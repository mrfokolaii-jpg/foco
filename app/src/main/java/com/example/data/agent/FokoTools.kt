package com.example.data.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.example.data.connectors.ConnectorResult
import com.example.data.connectors.ConnectorStore
import com.example.data.connectors.GitHubConnector
import com.example.data.connectors.GoogleDriveConnector
import com.example.data.connectors.HuggingFaceConnector
import com.example.data.remote.PageReader
import com.example.data.remote.WebSearchProvider
import com.example.service.FokoAccessibilityService
import com.example.util.DeviceActionDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class ToolSpec(
    val name: String,
    val description: String,
    val args: String
)

data class ToolOutcome(
    val ok: Boolean,
    val text: String
)

/**
 * The complete capability surface exposed to the language model.
 *
 * Every capability the user asked for (web search, browser reading, screen vision,
 * device control, file download/save, GitHub, Hugging Face, Google Drive, self-upgrade
 * knowledge harvesting and long term memory) is implemented here as one callable tool.
 */
object FokoTools {

    val specs: List<ToolSpec> = listOf(
        ToolSpec("web_search", "جستجوی زنده در وب و برگرداندن عنوان/لینک/خلاصه نتایج.", "query: متن جستجو"),
        ToolSpec("read_web_page", "خواندن متن کامل یک صفحه وب (قابلیت مرورگر).", "url: آدرس صفحه"),
        ToolSpec("read_screen", "خواندن متنی که همین حالا روی صفحه گوشی دیده می‌شود (بینایی صفحه).", "بدون آرگومان"),
        ToolSpec("open_app", "باز کردن یک اپلیکیشن روی گوشی با نام آن.", "name: نام برنامه مثل تلگرام"),
        ToolSpec("device_action", "اجرای فرمان دستگاه: اسکرول، برگشت، هوم، آلارم، تنظیمات، تماس و ... .", "command: دستور"),
        ToolSpec("save_file", "ذخیره محتوا در حافظه گوشی.", "name: نام فایل, content: متن"),
        ToolSpec("list_saved_files", "فهرست فایل‌های ذخیره‌شده فوکو.", "بدون آرگومان"),
        ToolSpec("read_saved_file", "خواندن یک فایل ذخیره‌شده.", "name: نام فایل"),
        ToolSpec("github_whoami", "بررسی اتصال حساب گیت‌هاب.", "بدون آرگومان"),
        ToolSpec("github_list_repos", "فهرست مخازن گیت‌هاب کاربر.", "query: فیلتر اختیاری"),
        ToolSpec("github_read_file", "خواندن یک فایل از مخزن گیت‌هاب.", "path: مسیر فایل, repo: اختیاری"),
        ToolSpec("github_write_file", "ساخت یا بروزرسانی فایل در مخزن گیت‌هاب (بارگذاری اطلاعات).", "path: مسیر, content: محتوا, message: پیام کامیت"),
        ToolSpec("github_search_code", "جستجوی کد در گیت‌هاب.", "query: عبارت"),
        ToolSpec("hf_whoami", "بررسی اتصال حساب Hugging Face.", "بدون آرگومان"),
        ToolSpec("hf_search_models", "جستجوی مدل در Hugging Face.", "query: نام یا موضوع"),
        ToolSpec("drive_list", "فهرست فایل‌های گوگل درایو.", "query: جستجوی اختیاری"),
        ToolSpec("drive_upload", "بارگذاری فایل متنی در گوگل درایو.", "name: نام, content: محتوا"),
        ToolSpec("drive_download", "دانلود فایل از گوگل درایو.", "file_id: شناسه فایل"),
        ToolSpec(
            "harvest_knowledge",
            "وقتی دانش کافی برای انجام کاری نداری: در وب جستجو کن، مطلب را جمع‌بندی کن، در حافظه ذخیره کن و در گیت‌هاب کاربر بارگذاری کن تا توانایی‌هایت ارتقا یابد.",
            "topic: موضوعی که نمی‌دانی, summary: خلاصه‌ای که خودت نوشتی (اختیاری)"
        ),
        ToolSpec("remember", "ذخیره یک نکته در حافظه بلندمدت فوکو.", "title: عنوان, content: متن"),
        ToolSpec("recall", "یادآوری از حافظه بلندمدت فوکو.", "query: عبارت"),
        ToolSpec("list_capabilities", "فهرست تمام توانایی‌ها و ابزارهای متصل.", "بدون آرگومان")
    )

    fun describeForPrompt(): String = buildString {
        appendLine("ابزارهای در دسترس تو:")
        specs.forEach { appendLine("- ${it.name}(${it.args}) → ${it.description}") }
    }.trim()

    suspend fun execute(context: Context, name: String, args: JSONObject): ToolOutcome {
        val store = ConnectorStore(context)
        return try {
            when (name) {
                "web_search" -> webSearch(args.optString("query"))
                "read_web_page" -> ToolOutcome(true, PageReader.read(args.optString("url")))
                "read_screen" -> readScreen()
                "open_app" -> ToolOutcome(true, openApp(context, args.optString("name")))
                "device_action" -> ToolOutcome(true, deviceAction(context, args.optString("command")))
                "save_file" -> saveFile(context, args.optString("name"), args.optString("content"))
                "list_saved_files" -> ToolOutcome(true, listSavedFiles(context))
                "read_saved_file" -> readSavedFile(context, args.optString("name"))
                "github_whoami" -> {
                    val user = GitHubConnector.whoAmI(store.githubToken())
                    if (user is ConnectorResult.Ok) ToolOutcome(true, "متصل به گیت‌هاب با نام کاربری ${user.value}.")
                    else ToolOutcome(false, (user as ConnectorResult.Err).message)
                }
                "github_list_repos" -> {
                    when (val r = GitHubConnector.listRepos(store.githubToken(), args.optString("query"))) {
                        is ConnectorResult.Ok -> ToolOutcome(true, r.value.joinToString("\n") { "${it.fullName} (${if (it.isPrivate) "خصوصی" else "عمومی"})" })
                        is ConnectorResult.Err -> ToolOutcome(false, r.message)
                    }
                }
                "github_read_file" -> {
                    val repo = args.optString("repo").ifBlank { store.githubRepo() }
                    val owner = args.optString("owner").ifBlank { store.githubOwner() }
                    when (val r = GitHubConnector.getFile(store.githubToken(), owner, repo, args.optString("path"), store.githubBranch())) {
                        is ConnectorResult.Ok -> if (r.value == null) ToolOutcome(false, "فایل پیدا نشد.") else ToolOutcome(true, r.value.content.take(6000))
                        is ConnectorResult.Err -> ToolOutcome(false, r.message)
                    }
                }
                "github_write_file" -> {
                    val repo = args.optString("repo").ifBlank { store.githubRepo() }
                    val owner = args.optString("owner").ifBlank { store.githubOwner() }
                    val message = args.optString("message").ifBlank { "FOKO: بروزرسانی ${args.optString("path")}" }
                    when (
                        val r = GitHubConnector.putFile(
                            store.githubToken(), owner, repo, args.optString("path"),
                            args.optString("content"), message, store.githubBranch()
                        )
                    ) {
                        is ConnectorResult.Ok -> ToolOutcome(true, "در گیت‌هاب ثبت شد: ${r.value}")
                        is ConnectorResult.Err -> ToolOutcome(false, r.message)
                    }
                }
                "github_search_code" -> {
                    when (val r = GitHubConnector.searchCode(store.githubToken(), args.optString("query"))) {
                        is ConnectorResult.Ok -> ToolOutcome(true, if (r.value.isEmpty()) "نتیجه‌ای یافت نشد." else r.value.joinToString("\n"))
                        is ConnectorResult.Err -> ToolOutcome(false, r.message)
                    }
                }
                "hf_whoami" -> {
                    when (val r = HuggingFaceConnector.whoAmI(store.hfToken())) {
                        is ConnectorResult.Ok -> ToolOutcome(true, "متصل به Hugging Face با نام ${r.value}.")
                        is ConnectorResult.Err -> ToolOutcome(false, r.message)
                    }
                }
                "hf_search_models" -> {
                    when (val r = HuggingFaceConnector.searchModels(args.optString("query"), store.hfToken())) {
                        is ConnectorResult.Ok -> ToolOutcome(true, if (r.value.isEmpty()) "مدلی یافت نشد." else r.value.joinToString("\n") { "${it.id} — دانلود ${it.downloads}" })
                        is ConnectorResult.Err -> ToolOutcome(false, r.message)
                    }
                }
                "drive_list" -> {
                    val token = GoogleDriveConnector.ensureAccessToken(store)
                    if (token is ConnectorResult.Err) return ToolOutcome(false, token.message)
                    when (val r = GoogleDriveConnector.listFiles((token as ConnectorResult.Ok).value, args.optString("query"))) {
                        is ConnectorResult.Ok -> ToolOutcome(true, if (r.value.isEmpty()) "فایلی یافت نشد." else r.value.joinToString("\n") { "${it.name} [${it.id}]" })
                        is ConnectorResult.Err -> ToolOutcome(false, r.message)
                    }
                }
                "drive_upload" -> {
                    val token = GoogleDriveConnector.ensureAccessToken(store)
                    if (token is ConnectorResult.Err) return ToolOutcome(false, token.message)
                    when (
                        val r = GoogleDriveConnector.uploadTextFile(
                            (token as ConnectorResult.Ok).value,
                            args.optString("name").ifBlank { "foko-note.txt" },
                            args.optString("content")
                        )
                    ) {
                        is ConnectorResult.Ok -> ToolOutcome(true, "در گوگل درایو بارگذاری شد: ${r.value}")
                        is ConnectorResult.Err -> ToolOutcome(false, r.message)
                    }
                }
                "drive_download" -> {
                    val token = GoogleDriveConnector.ensureAccessToken(store)
                    if (token is ConnectorResult.Err) return ToolOutcome(false, token.message)
                    when (val r = GoogleDriveConnector.downloadFile((token as ConnectorResult.Ok).value, args.optString("file_id"))) {
                        is ConnectorResult.Ok -> ToolOutcome(true, r.value.take(6000))
                        is ConnectorResult.Err -> ToolOutcome(false, r.message)
                    }
                }
                "harvest_knowledge" -> harvest(context, store, args.optString("topic"), args.optString("summary"))
                "remember" -> {
                    FokoMemory.add(context, "note", args.optString("title").ifBlank { "یادداشت" }, args.optString("content"))
                    ToolOutcome(true, "در حافظه فوکو ذخیره شد.")
                }
                "recall" -> {
                    val hits = FokoMemory.search(context, args.optString("query"), 5)
                    ToolOutcome(true, if (hits.isEmpty()) "چیزی در حافظه پیدا نشد." else hits.joinToString("\n") { "- ${it.title}: ${it.content.take(400)}" })
                }
                "list_capabilities" -> ToolOutcome(true, describeForPrompt())
                else -> ToolOutcome(false, "ابزار ناشناخته: $name")
            }
        } catch (e: Exception) {
            ToolOutcome(false, "اجرای ابزار $name با خطا مواجه شد: ${e.localizedMessage}")
        }
    }

    private suspend fun webSearch(query: String): ToolOutcome {
        if (query.isBlank()) return ToolOutcome(false, "عبارت جستجو خالی است.")
        val hits = WebSearchProvider.search(query, 6)
        if (hits.isEmpty()) return ToolOutcome(false, "نتیجه‌ای در وب پیدا نشد.")
        return ToolOutcome(true, hits.joinToString("\n\n") { hit ->
            buildString {
                appendLine("عنوان: ${hit.title}")
                if (hit.url.isNotBlank()) appendLine("لینک: ${hit.url}")
                if (hit.snippet.isNotBlank()) appendLine("خلاصه: ${hit.snippet}")
            }.trim()
        })
    }

    private fun readScreen(): ToolOutcome {
        if (!FokoAccessibilityService.isRunning()) {
            return ToolOutcome(false, "سرویس دسترسی‌پذیری فعال نیست؛ برای بینایی صفحه آن را در تنظیمات فوکو روشن کنید.")
        }
        val text = FokoAccessibilityService.getScreenContentHierarchy()
        return ToolOutcome(true, "محتوای صفحه جاری:\n$text")
    }

    private fun openApp(context: Context, name: String): String {
        if (name.isBlank()) return "نام برنامه مشخص نشد."
        val packages = mapOf(
            "تلگرام" to "org.telegram.messenger", "telegram" to "org.telegram.messenger",
            "واتساپ" to "com.whatsapp", "whatsapp" to "com.whatsapp",
            "اینستاگرام" to "com.instagram.android", "instagram" to "com.instagram.android",
            "یوتیوب" to "com.google.android.youtube", "youtube" to "com.google.android.youtube",
            "جیمیل" to "com.google.android.gm", "gmail" to "com.google.android.gm",
            "گوگل درایو" to "com.google.android.apps.docs", "drive" to "com.google.android.apps.docs",
            "کروم" to "com.android.chrome", "chrome" to "com.android.chrome",
            "دوربین" to "com.android.camera", "camera" to "com.android.camera",
            "تنظیمات" to "com.android.settings", "settings" to "com.android.settings"
        )
        val pkg = packages.entries.firstOrNull { name.contains(it.key, ignoreCase = true) }?.value
        if (pkg != null) {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return "برنامه «$name» باز شد."
            }
        }
        val fallback = DeviceActionDispatcher.executeIfIntent(context, name)
        return fallback ?: "برنامه «$name» روی دستگاه پیدا نشد."
    }

    private fun deviceAction(context: Context, command: String): String {
        if (command.isBlank()) return "دستور خالی است."
        return DeviceActionDispatcher.executeIfIntent(context, command)
            ?: "برای این دستور اقدام مستقیمی تعریف نشده است؛ می‌توانم با ابزارهای دیگر کمکت کنم."
    }

    fun fokoStorageDir(context: Context): File {
        val publicDir = File(Environment.getExternalStorageDirectory(), "FOKO")
        return try {
            if (!publicDir.exists()) publicDir.mkdirs()
            if (publicDir.canWrite()) publicDir else File(context.getExternalFilesDir(null), "FOKO").apply { mkdirs() }
        } catch (_: Exception) {
            File(context.getExternalFilesDir(null), "FOKO").apply { mkdirs() }
        }
    }

    private suspend fun saveFile(context: Context, name: String, content: String): ToolOutcome =
        withContext(Dispatchers.IO) {
            if (content.isBlank()) return@withContext ToolOutcome(false, "محتوای فایل خالی است.")
            val safeName = name.ifBlank { "foko-${System.currentTimeMillis()}.txt" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            return@withContext try {
                val file = File(fokoStorageDir(context), safeName)
                file.writeText(content)
                FokoMemory.add(context, "file", safeName, "مسیر: ${file.absolutePath}")
                ToolOutcome(true, "فایل ذخیره شد: ${file.absolutePath}")
            } catch (e: Exception) {
                ToolOutcome(false, "ذخیره فایل ناموفق بود: ${e.localizedMessage}")
            }
        }

    private fun listSavedFiles(context: Context): String {
        val files = fokoStorageDir(context).listFiles()?.take(40) ?: emptyList()
        if (files.isEmpty()) return "هنوز فایلی ذخیره نشده است."
        return files.joinToString("\n") { "${it.name} (${it.length()} بایت)" }
    }

    private suspend fun readSavedFile(context: Context, name: String): ToolOutcome = withContext(Dispatchers.IO) {
        val file = File(fokoStorageDir(context), name)
        if (!file.exists()) return@withContext ToolOutcome(false, "فایلی با این نام پیدا نشد.")
        try {
            ToolOutcome(true, file.readText().take(6000))
        } catch (e: Exception) {
            ToolOutcome(false, "خواندن فایل ناموفق بود: ${e.localizedMessage}")
        }
    }

    private suspend fun harvest(
        context: Context,
        store: ConnectorStore,
        topic: String,
        providedSummary: String
    ): ToolOutcome {
        if (topic.isBlank()) return ToolOutcome(false, "موضوع برای یادگیری مشخص نشد.")
        val hits = WebSearchProvider.search(topic, 5)
        val findings = StringBuilder()
        if (hits.isNotEmpty()) {
            hits.forEach { hit ->
                findings.appendLine("### ${hit.title}")
                if (hit.url.isNotBlank()) findings.appendLine("منبع: ${hit.url}")
                if (hit.snippet.isNotBlank()) findings.appendLine(hit.snippet)
                findings.appendLine()
            }
            val firstUrl = hits.firstOrNull { it.url.isNotBlank() }?.url
            if (firstUrl != null) {
                findings.appendLine("### متن کامل منبع اصلی")
                findings.appendLine(PageReader.read(firstUrl, 4000))
            }
        }

        val summary = providedSummary.ifBlank {
            if (hits.isEmpty()) "دانش ساخت‌یافته‌ای پیدا نشد." else hits.take(3).joinToString("\n") { "- ${it.title}: ${it.snippet.take(200)}" }
        }

        val markdown = buildString {
            appendLine("# $topic")
            appendLine()
            appendLine("> این سند به‌صورت خودکار توسط FOKO AI ساخته و در مخزن کاربر بارگذاری شد.")
            appendLine("> زمان: ${java.util.Date()}")
            appendLine()
            appendLine("## خلاصه")
            appendLine(summary)
            appendLine()
            if (findings.isNotBlank()) {
                appendLine("## یافته‌های وب")
                appendLine(findings.toString().trim())
            }
        }

        FokoMemory.add(context, "knowledge", topic, summary)

        if (!store.isAutoUpgradeEnabled()) {
            return ToolOutcome(true, "دانش در حافظه فوکو ذخیره شد (ارتقای خودکار گیت‌هاب خاموش است).\n$summary")
        }
        if (store.githubToken().isBlank()) {
            return ToolOutcome(true, "دانش در حافظه فوکو ذخیره شد، اما برای بارگذاری در گیت‌هاب ابتدا توکن را در بخش «ابزارها» وارد کن.\n$summary")
        }

        val slug = slugFor(topic)
        val path = "knowledge/$slug.md"
        val put = GitHubConnector.putFile(
            store.githubToken(), store.githubOwner(), store.githubRepo(),
            path, markdown, "FOKO self-upgrade: افزودن دانش «$topic»", store.githubBranch()
        )
        return when (put) {
            is ConnectorResult.Ok ->
                ToolOutcome(true, "دانش جدید در گیت‌هاب بارگذاری شد و توانایی‌های فوکو ارتقا یافت: ${put.value}\n\n$summary")
            is ConnectorResult.Err ->
                ToolOutcome(true, "دانش در حافظه فوکو ذخیره شد اما بارگذاری در گیت‌هاب ناموفق بود: ${put.message}\n$summary")
        }
    }

    private fun slugFor(topic: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(topic.toByteArray())
        val hash = digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }.take(10)
        val ascii = topic.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return if (ascii.isNotBlank()) "$ascii-$hash" else "topic-$hash"
    }

    fun downloadToDevice(context: Context, url: String, fileName: String): ToolOutcome {
        return try {
            val safe = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val file = File(fokoStorageDir(context), safe)
            val connection = java.net.URL(url).openConnection()
            connection.connectTimeout = 20000
            connection.readTimeout = 30000
            connection.getInputStream().use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            ToolOutcome(true, "دانلود شد: ${file.absolutePath}")
        } catch (e: Exception) {
            ToolOutcome(false, "دانلود ناموفق بود: ${e.localizedMessage}")
        }
    }
}
