package com.example

import android.app.Application
import com.example.data.local.CrashReporter
import com.example.data.offline.KnowledgeBase
import com.example.data.offline.OfflineEngine
import com.example.data.offline.OfflineSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * نقطهٔ شروع برنامه.
 *
 * دو کار این‌جا انجام می‌شود که پیش‌تر در هیچ‌کدام از صفحه‌ها جای درستی نداشت:
 *   ۱) ثبت خطاها از همان لحظهٔ اول — پیش‌تر اگر خطایی پیش از باز شدن صفحهٔ چت
 *      رخ می‌داد، هیچ‌جا ثبت نمی‌شد.
 *   ۲) دادن دسترسی موتور آفلاین به تنظیمات و پایگاه دانش، یک‌بار برای همیشه.
 */
class FokoApplication : Application() {
    /** کارهای پس‌زمینهٔ سبک برنامه (نباید رشتهٔ اصلی را قفل کند). */
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // ۱) ثبت خطاها از همان لحظهٔ اول — پیش‌تر خطاهای پیش از باز شدن صفحهٔ چت
        //    هیچ‌جا ثبت نمی‌شدند.
        runCatching { CrashReporter.install(this) }

        // ۲) در اختیار گذاشتن تنظیمات و دانش به موتور آفلاین.
        OfflineEngine.attach(this)

        // ۲.۵) نصب‌های قبلی پنجرهٔ متنِ تنگ (۲۰۴۸) داشتند که باعث بسته شدن اپ
        //      هنگام پاسخ‌دادن می‌شد؛ یک‌بار بزرگش می‌کنیم.
        runCatching { OfflineSettings.migrate(this) }

        // ۳) نصب پایگاه دانشِ همراهِ اپ. کاربر روی گوشی نمی‌توانست فایل ZIP را دانلود
        //    کند؛ حالا اسناد از پیش داخل برنامه هستند و هیچ دانلودی لازم نیست.
        //    اگر خودش چیزی اضافه کرده باشد، دست‌نخورده می‌ماند.
        background.launch {
            runCatching { KnowledgeBase.installBundled(this@FokoApplication) }
        }
    }
}
