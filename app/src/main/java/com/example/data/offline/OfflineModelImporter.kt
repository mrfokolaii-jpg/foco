package com.example.data.offline

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import com.example.data.remote.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * آوردن مدل هوش مصنوعی از حافظهٔ گوشی به داخل اپ.
 *
 * سه راه پشتیبانی می‌شود، چون هر گوشی و هر کاربری یک‌جور فایل را پیدا می‌کند:
 *   ۱) فایل ZIP → خودمان GGUF را از داخلش بیرون می‌کشیم.
 *   ۲) فایل GGUF مستقیم → کپی می‌شود.
 *   ۳) مسیری که کاربر خودش روی حافظه درآورده → بدون کپی، همان‌جا استفاده می‌شود
 *      (برای مدل‌های چند گیگابایتی، دوباره‌نویسی فایل وقت و فضا تلف می‌کند).
 */
object OfflineModelImporter {

    /** هر فایل GGUF با این چهار بایت شروع می‌شود؛ راه مطمئن تشخیص فایل سالم. */
    private const val GGUF_MAGIC = "GGUF"

    private const val CHUNK = 1 shl 20   // 1 مگابایت

    /** پوشهٔ داخلی خود اپ که همیشه قابل خواندن است. */
    fun modelsDir(context: Context): File =
        File(context.filesDir, "models").apply { if (!exists()) mkdirs() }

    /**
     * فایل واقعاً GGUF است؟ فقط پسوند کافی نیست؛ سرِ فایل را می‌خوانیم. مدل ناقص یا
     * اشتباه، هنگام بارگذاری باعث خطای مبهم می‌شود و کاربر فکر می‌کند اپ خراب است.
     */
    fun isGgufFile(file: File): Boolean = try {
        if (!file.isFile || file.length() < 8) false
        else FileInputStreamHeader(file) == GGUF_MAGIC
    } catch (_: Exception) {
        false
    }

    private fun FileInputStreamHeader(file: File): String {
        val bytes = ByteArray(4)
        file.inputStream().use { stream ->
            if (stream.read(bytes) != 4) return ""
        }
        return String(bytes, Charsets.US_ASCII)
    }

    /** نام نمایشی یک Uri (اسم فایل در حافظه). */
    fun displayName(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    val name = cursor.getString(index)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "model.gguf"
    }

    /**
     * آوردن مدل از یک فایل انتخاب‌شده (ZIP یا GGUF).
     *
     * ZIP ها گاهی چند فایل دارند؛ بزرگ‌ترین `*.gguf` انتخاب می‌شود، چون مدل اصلی
     * همان است و بقیه معمولاً توکنایزر یا فایل‌های جانبی‌اند.
     *
     * @param onProgress درصد پیشرفت (۰ تا ۱۰۰) — برای نمایش به کاربر.
     */
    suspend fun importFromUri(
        context: Context,
        uri: Uri,
        onProgress: (Int) -> Unit = {}
    ): ApiResult<File> = withContext(Dispatchers.IO) {
        try {
            val name = displayName(context, uri)
            val targetDir = modelsDir(context)

            if (name.lowercase().endsWith(".zip")) {
                extractGgufFromZip(context, uri, targetDir, onProgress)
            } else {
                copyUriToFile(context, uri, File(targetDir, sanitize(name)), onProgress)
            }
        } catch (error: Exception) {
            ApiResult.Error("خواندن فایل انجام نشد: ${error.localizedMessage ?: "خطای نامشخص"}")
        }
    }

    private fun copyUriToFile(
        context: Context,
        uri: Uri,
        target: File,
        onProgress: (Int) -> Unit
    ): ApiResult<File> {
        val total = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.length ?: -1L
        }.getOrDefault(-1L)

        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return ApiResult.Error("فایل قابل خواندن نبود.")
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(CHUNK)
                var copied = 0L
                var read = input.read(buffer)
                while (read > 0) {
                    output.write(buffer, 0, read)
                    copied += read
                    if (total > 0) onProgress(((copied * 100) / total).toInt().coerceIn(0, 100))
                    read = input.read(buffer)
                }
            }
        }

        if (!isGgufFile(target)) {
            target.delete()
            return ApiResult.Error(
                "این فایل یک مدل GGUF نیست. مدل باید با چهار حرف GGUF شروع شود (پسوند .gguf)."
            )
        }
        return ApiResult.Success(target)
    }

    private fun extractGgufFromZip(
        context: Context,
        uri: Uri,
        targetDir: File,
        onProgress: (Int) -> Unit
    ): ApiResult<File> {
        // مرحلهٔ ۱: پیدا کردن بزرگ‌ترین فایل GGUF داخل زیپ، بدون بیرون کشیدن آن.
        var bestName: String? = null
        var bestSize = -1L

        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return ApiResult.Error("فایل زیپ قابل خواندن نبود.")
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    if (!entry.isDirectory && entryName.lowercase().endsWith(".gguf")) {
                        if (entry.size > bestSize) {
                            bestSize = entry.size
                            bestName = entryName
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        val chosen = bestName
            ?: return ApiResult.Error(
                "داخل این زیپ هیچ فایل .gguf پیدا نشد. زیپ باید خودِ فایل مدل را داشته باشد."
            )

        if (bestSize > 0 && bestSize > targetDir.usableSpace) {
            val needGb = "%.1f".format(bestSize / (1024.0 * 1024.0 * 1024.0))
            val freeGb = "%.1f".format(targetDir.usableSpace / (1024.0 * 1024.0 * 1024.0))
            return ApiResult.Error(
                "فضای آزاد کافی نیست. مدل $needGb گیگابایت است ولی $freeGb گیگابایت فضا داری.\n" +
                    "یا فضا خالی کن، یا از دکمهٔ «اسکن حافظه» استفاده کن تا مدل بدون کپی خوانده شود."
            )
        }

        // مرحلهٔ ۲: بیرون کشیدن همان فایل، با نمایش درصد پیشرفت.
        val target = File(targetDir, sanitize(File(chosen).name))
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return ApiResult.Error("فایل زیپ قابل خواندن نبود.")
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name == chosen) {
                        FileOutputStream(target).use { output ->
                            val buffer = ByteArray(CHUNK)
                            var copied = 0L
                            var read = zip.read(buffer)
                            while (read > 0) {
                                output.write(buffer, 0, read)
                                copied += read
                                if (bestSize > 0) {
                                    onProgress(((copied * 100) / bestSize).toInt().coerceIn(0, 100))
                                }
                                read = zip.read(buffer)
                            }
                        }
                        break
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        if (!target.exists() || target.length() == 0L) {
            return ApiResult.Error("بیرون کشیدن مدل از زیپ کامل نشد. دوباره امتحان کن.")
        }
        if (!isGgufFile(target)) {
            target.delete()
            return ApiResult.Error("فایل بیرون‌کشیده‌شده یک مدل GGUF سالم نیست.")
        }
        onProgress(100)
        return ApiResult.Success(target)
    }

    /** نام فایل را برای حافظهٔ اندروید بی‌خطر می‌کند. */
    private fun sanitize(name: String): String {
        val clean = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (clean.lowercase().endsWith(".gguf")) clean else "$clean.gguf"
    }

    // ---- پیدا کردن مدل‌هایی که کاربر از قبل روی حافظه دارد --------------------

    /**
     * دسترسی «همهٔ فایل‌ها» داده شده است؟
     * اندروید ۱۱ به بالا نیاز به این اجازه دارد تا پوشهٔ Download و ... خوانده شود.
     */
    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
        } else {
            true
        }

    /**
     * پوشه‌های معمولی‌ای که کاربر مدل را در آن‌ها می‌گذارد.
     * کاربر گفت «فایل را از حالت زیپ بیرون می‌آورم»؛ پس باید پوشه‌ها را واقعاً بگردیم.
     */
    private fun candidateRoots(context: Context): List<File> {
        val list = mutableListOf<File>()
        runCatching { Environment.getExternalStorageDirectory() }.getOrNull()?.let { root ->
            list += File(root, "Download")
            list += File(root, "Downloads")
            list += File(root, "Documents")
            list += File(root, "FokoModels")
            list += File(root, "Models")
            list += File(root, "Android/media")
        }
        context.getExternalFilesDir(null)?.let { list += it }
        list += modelsDir(context)
        return list.filter { it.isDirectory }
    }

    /**
     * حافظه را برای فایل‌های `.gguf` می‌گردد (دو سطح عمق؛ گشتن کل حافظه بسیار کند است).
     * بزرگ‌ترین فایل‌ها اول می‌آیند، چون مدل اصلی همان است.
     */
    suspend fun scanForModels(context: Context): List<File> = withContext(Dispatchers.IO) {
        val found = mutableListOf<File>()
        candidateRoots(context).forEach { root ->
            walk(root, depth = 0) { file ->
                if (file.name.lowercase().endsWith(".gguf") && file.length() > 8) found += file
            }
        }
        found.distinctBy { it.absolutePath }
            .sortedByDescending { it.length() }
            .take(40)
    }

    private fun walk(dir: File, depth: Int, onFile: (File) -> Unit) {
        if (depth > 2) return
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        children.forEach { child ->
            if (child.isDirectory) walk(child, depth + 1, onFile) else onFile(child)
        }
    }

    /** حجم خوانا برای نمایش. */
    fun readableSize(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1.0) {
            "%.2f گیگابایت".format(gb)
        } else {
            "%.0f مگابایت".format(bytes / (1024.0 * 1024.0))
        }
    }

    /**
     * هشدار صادقانه دربارهٔ مدل‌های بزرگ: روی پردازندهٔ گوشی، مدل بزرگ‌تر از حافظهٔ آزاد
     * بارگذاری نمی‌شود؛ بهتر است کاربر پیش‌تر بداند تا بعد از چند دقیقه انتظار.
     */
    fun sizeWarning(bytes: Long): String? {
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return when {
            gb >= 5.0 -> "این مدل ${readableSize(bytes)} است و تقریباً مطمئناً روی این گوشی باز نمی‌شود. نسخهٔ ۱ تا ۳ میلیارد پارامتری Q4 بگیر."
            gb >= 2.5 -> "این مدل ${readableSize(bytes)} است؛ روی گوشی با ۸ گیگابایت حافظه ممکن است حافظه تمام شود. اگر باز نشد، نسخهٔ کوچک‌تر بگیر."
            else -> null
        }
    }
}
