package com.example.data.local

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ثبت خطاها داخل خودِ اپ.
 *
 * چرا لازم شد: وقتی اپ با یک خطای داخلی بسته می‌شود، کاربر فقط یک صفحهٔ کوتاه یا
 * عکس بی‌کیفیت می‌بیند و نمی‌شود فهمید مشکل کجاست. این‌جا متن کامل خطا، همراه با
 * مدل گوشی و نسخهٔ اندروید، در حافظه ذخیره می‌شود تا در «تنظیمات › گزارش خطا»
 * خوانده و کپی شود.
 *
 * محدودیت صادقانه: کرش‌های بومی (خرابی داخل کد C++ مثل موتور آفلاین) فرصت اجرای
 * کد جاوا را نمی‌دهند و این‌جا ثبت نمی‌شوند. برای آن‌ها باید از گزارش خودِ اندروید
 * استفاده کرد.
 */
object CrashReporter {

    private const val DIR = "crash"
    private const val FILE_NAME = "last_crash.txt"

    private val lock = Any()

    /**
     * نصب گیرندهٔ خطا. از [com.example.FokoApplication] در شروع برنامه صدا زده می‌شود.
     *
     * پس از ثبت، به گیرندهٔ قبلی هم خبر می‌دهیم تا رفتار معمول اندروید
     * (نشان دادن پیام «برنامه متوقف شد») از بین نرود.
     */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(app, error, "بستهٔ برنامه (Uncaught)") }
            previous?.uncaughtException(thread, error)
        }
    }

    /** ثبت یک خطای گرفته‌شده (بدون بستن برنامه). */
    fun record(context: Context, error: Throwable, where: String) {
        synchronized(lock) {
            runCatching {
                val dir = File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }
                val text = buildString {
                    appendLine("زمان: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                    appendLine("محل: $where")
                    appendLine("اندروید: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendLine("گوشی: ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("معماری: ${Build.SUPPORTED_ABIS.joinToString()}")
                    appendLine()
                    appendLine(error.toString())
                    appendLine(error.stackTraceToString())
                    error.cause?.let { cause ->
                        appendLine()
                        appendLine("علت اصلی: $cause")
                        appendLine(cause.stackTraceToString())
                    }
                }
                File(dir, FILE_NAME).writeText(text, Charsets.UTF_8)
            }
        }
    }

    /** آخرین گزارش ثبت‌شده، یا متن خالی اگر خطایی ثبت نشده باشد. */
    fun lastReport(context: Context): String {
        val file = File(File(context.filesDir, DIR), FILE_NAME)
        if (!file.exists()) return ""
        return runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("")
    }

    fun clear(context: Context) {
        runCatching { File(File(context.filesDir, DIR), FILE_NAME).delete() }
    }
}
