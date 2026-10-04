package com.example.data.offline

import android.content.Context
import android.net.Uri
import com.example.data.remote.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

/** یک تکه از یک سند؛ کوچک‌ترین واحدی که جستجو روی آن انجام می‌شود. */
data class KnowledgeChunk(
    val title: String,
    val category: String,
    val text: String,
    val score: Int = 0
)

/** خلاصهٔ پایگاه دانش برای نمایش در تنظیمات. */
data class KnowledgeStats(
    val documents: Int = 0,
    val chunks: Int = 0,
    val bytes: Long = 0
)

/**
 * پایگاه دانش محلی — بدون اینترنت.
 *
 * فایل ZIP کاربر باز می‌شود، همهٔ فایل‌های متنی داخلش خوانده و به قطعه‌های کوچک
 * تقسیم می‌شوند، و یک فهرست (`index.json`) ساخته می‌شود. موقع پرسیدن سؤال، فقط
 * قطعه‌های مربوط به همان سؤال پیدا و به مدل داده می‌شوند (روش RAG).
 *
 * چرا لازم است: مدل ۱.۵ میلیاردی به‌تنهایی دانش تخصصی ندارد. به‌جای بزرگ و کند کردن
 * مدل، دانش را کنارش می‌گذاریم و هر بار فقط بخش مربوطه را جلوی چشمش می‌گیریم.
 */
object KnowledgeBase {

    private const val INDEX = "index.json"
    /** سقف متن هر سند؛ از پر شدن حافظه و کند شدن فهرست‌سازی جلوگیری می‌کند. */
    private const val MAX_DOC_CHARS = 400_000
    private const val CHUNK_CHARS = 900
    private const val MIN_CHUNK_CHARS = 120

    private var cacheStamp: Long = -1
    private var cache: List<KnowledgeChunk> = emptyList()

    private fun dir(context: Context): File =
        File(context.filesDir, "knowledge").apply { if (!exists()) mkdirs() }

    private fun indexFile(context: Context) = File(dir(context), INDEX)

    fun stats(context: Context): KnowledgeStats {
        val chunks = loadChunks(context)
        val file = indexFile(context)
        return KnowledgeStats(
            documents = chunks.map { it.title }.distinct().size,
            chunks = chunks.size,
            bytes = if (file.exists()) file.length() else 0
        )
    }

    fun isReady(context: Context): Boolean = loadChunks(context).isNotEmpty()

    /**
     * خواندن ZIP انتخابی کاربر و ساخت فهرست.
     *
     * فقط فایل‌های `.md`، `.txt` و `.json` خوانده می‌شوند؛ تصویر و PDF کنار گذاشته
     * می‌شوند چون مدل متنی نمی‌تواند آن‌ها را بخواند.
     */
    suspend fun importZip(
        context: Context,
        uri: Uri,
        onProgress: (Int) -> Unit = {}
    ): ApiResult<KnowledgeStats> = withContext(Dispatchers.IO) {
        try {
            val documents = mutableListOf<Triple<String, String, String>>() // دسته، عنوان، متن
            var read = 0

            context.contentResolver.openInputStream(uri).use { input ->
                if (input == null) return@withContext ApiResult.Error("فایل زیپ قابل خواندن نبود.")
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (!entry.isDirectory && isTextFile(name)) {
                            val text = readEntry(zip)
                            if (text.isNotBlank()) {
                                val path = name.substringBeforeLast('/', "")
                                val title = entry.name.substringAfterLast('/')
                                    .substringBeforeLast('.')
                                documents += Triple(path.ifBlank { "عمومی" }, title, text)
                            }
                        }
                        read++
                        onProgress((read % 100))
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }

            if (documents.isEmpty()) {
                return@withContext ApiResult.Error(
                    "داخل این زیپ هیچ فایل متنی (.md، .txt یا .json) پیدا نشد."
                )
            }

            val chunks = buildChunks(documents)
            writeIndex(context, chunks)
            cacheStamp = -1   // فهرست حافظه باطل شود تا از فایل تازه خوانده شود
            ApiResult.Success(stats(context))
        } catch (error: Exception) {
            ApiResult.Error("خواندن پایگاه دانش انجام نشد: ${error.localizedMessage ?: "خطای نامشخص"}")
        }
    }

    private fun isTextFile(name: String): Boolean {
        val lower = name.lowercase()
        if (lower.startsWith("__macosx/") || lower.contains("/._")) return false
        return lower.endsWith(".md") || lower.endsWith(".txt") || lower.endsWith(".json")
    }

    /** یک ورودی زیپ را به متن تبدیل می‌کند (با سقف طول). */
    private fun readEntry(zip: ZipInputStream): String {
        val out = StringBuilder()
        val buffer = ByteArray(8192)
        var count = zip.read(buffer)
        while (count > 0 && out.length < MAX_DOC_CHARS) {
            out.append(String(buffer, 0, count, Charsets.UTF_8))
            count = zip.read(buffer)
        }
        return out.toString()
    }

    /**
     * عنوان نمایشی سند.
     *
     * اگر نام فایل انگلیسی باشد (که برای سازگاری زیپ بهتر است)، عنوان فارسی از
     * نخستین سرتیتر `#` داخل خود فایل خوانده می‌شود.
     */
    private fun displayTitle(fileName: String, text: String): String {
        val looksEnglish = fileName.all { it.code < 128 }
        if (!looksEnglish) return fileName
        val heading = text.lineSequence()
            .firstOrNull { it.trimStart().startsWith("# ") }
            ?.trimStart()
            ?.removePrefix("#")
            ?.trim()
            .orEmpty()
        return heading.ifBlank { fileName }
    }

    /** هر سند به قطعه‌های ~۹۰۰ حرفی، ترجیحاً روی سرتیترها بریده می‌شود. */
    private fun buildChunks(documents: List<Triple<String, String, String>>): List<KnowledgeChunk> {
        val chunks = mutableListOf<KnowledgeChunk>()
        documents.forEach { (category, fileName, text) ->
            val title = displayTitle(fileName, text)
            val normalized = text.replace("\r\n", "\n")
            val pieces = mutableListOf<String>()
            val current = StringBuilder()
            normalized.split('\n').forEach { line ->
                val isHeading = line.trimStart().startsWith("#")
                if ((isHeading && current.length > MIN_CHUNK_CHARS) || current.length > CHUNK_CHARS) {
                    pieces += current.toString()
                    current.clear()
                }
                current.append(line).append('\n')
            }
            if (current.isNotBlank()) pieces += current.toString()

            pieces.map { it.trim() }
                .filter { it.length >= MIN_CHUNK_CHARS }
                .forEach { chunks += KnowledgeChunk(title = title, category = category, text = it) }
        }
        return chunks
    }

    private fun writeIndex(context: Context, chunks: List<KnowledgeChunk>) {
        val array = JSONArray()
        chunks.forEach { chunk ->
            array.put(
                JSONObject()
                    .put("t", chunk.title)
                    .put("c", chunk.category)
                    .put("x", chunk.text)
            )
        }
        indexFile(context).writeText(array.toString(), Charsets.UTF_8)
    }

    private fun loadChunks(context: Context): List<KnowledgeChunk> {
        val file = indexFile(context)
        if (!file.exists()) return emptyList()
        val stamp = file.lastModified()
        if (stamp == cacheStamp && cache.isNotEmpty()) return cache
        return try {
            val array = JSONArray(file.readText(Charsets.UTF_8))
            val list = (0 until array.length()).map { index ->
                val obj = array.optJSONObject(index) ?: JSONObject()
                KnowledgeChunk(
                    title = obj.optString("t"),
                    category = obj.optString("c"),
                    text = obj.optString("x")
                )
            }
            cache = list
            cacheStamp = stamp
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear(context: Context) {
        indexFile(context).delete()
        cache = emptyList()
        cacheStamp = -1
    }

    /**
     * جستجوی محلی: کدام قطعه‌ها به این سؤال مربوط‌اند؟
     *
     * نمره‌دهی ساده و قابل‌فهم است: هر کلمهٔ سؤال که در قطعه باشد یک امتیاز می‌گیرد،
     * و اگر در عنوان سند باشد سه امتیاز. نیازی به اینترنت و نیازی به مدل embedding
     * نیست — روی گوشی در چند میلی‌ثانیه انجام می‌شود.
     */
    fun search(context: Context, query: String, limit: Int = 3): List<KnowledgeChunk> {
        val chunks = loadChunks(context)
        if (chunks.isEmpty() || query.isBlank()) return emptyList()

        val terms = tokenize(query).distinct()
        if (terms.isEmpty()) return emptyList()

        return chunks.mapNotNull { chunk ->
            val body = normalize(chunk.text)
            val head = normalize(chunk.title + " " + chunk.category)
            var score = 0
            terms.forEach { term ->
                var hits = 0
                var from = body.indexOf(term)
                while (from >= 0 && hits < 12) {
                    hits++
                    from = body.indexOf(term, from + term.length)
                }
                if (hits > 0) score += hits
                if (head.contains(term)) score += 3
            }
            if (score > 0) chunk.copy(score = score) else null
        }
            .sortedByDescending { it.score }
            .take(limit)
    }

    /**
     * متن آماده برای دادن به مدل. کوتاه نگه داشته می‌شود چون مدل آفلاین پنجرهٔ
     * کوچکی دارد و متن زیاد جای سؤال کاربر را تنگ می‌کند.
     */
    fun contextFor(context: Context, query: String, maxChars: Int = 1400): String {
        val hits = search(context, query)
        if (hits.isEmpty()) return ""
        val builder = StringBuilder()
        hits.forEach { hit ->
            val room = maxChars - builder.length
            if (room < 200) return@forEach
            builder.append("[").append(hit.category).append(" › ").append(hit.title).append("]\n")
            builder.append(hit.text.take(room - 120).trim()).append("\n\n")
        }
        return builder.toString().trim()
    }

    /** یکسان‌سازی حروف عربی/فارسی که شکل‌های متفاوتی دارند. */
    private fun normalize(text: String): String = text
        .replace('\u064A', '\u06CC')   // ي → ی
        .replace('\u0643', '\u06A9')   // ك → ک
        .replace('\u0629', '\u0647')   // ة → ه
        .replace('\u064B', ' ').replace('\u064C', ' ').replace('\u064D', ' ')
        .replace('\u064E', ' ').replace('\u064F', ' ').replace('\u0650', ' ')
        .replace('\u0651', ' ').replace('\u0652', ' ')
        .replace('\u200C', ' ')        // نیم‌فاصله
        .lowercase()

    /** کلمات بی‌ارزش (و خیلی کوتاه) حذف می‌شوند تا نتیجه دقیق‌تر باشد. */
    private fun tokenize(query: String): List<String> {
        val stop = setOf(
            "و", "در", "به", "از", "که", "این", "را", "با", "است", "برای", "چی",
            "چه", "چطور", "کدام", "یک", "های", "ها", "می", "شود", "کن", "کنم",
            "the", "a", "an", "of", "to", "is", "are", "how", "what", "and"
        )
        return normalize(query)
            .split(Regex("[^\\p{L}\\p{N}_+#.]+"))
            .map { it.trim('.', '_', '+', '#') }
            .filter { it.length >= 3 && it !in stop }
    }
}
