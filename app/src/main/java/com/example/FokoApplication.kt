package com.example

import android.app.Application
import com.example.data.local.CrashReporter
import com.example.data.offline.OfflineEngine

/**
 * نقطهٔ شروع برنامه.
 *
 * دو کار این‌جا انجام می‌شود که پیش‌تر در هیچ‌کدام از صفحه‌ها جای درستی نداشت:
 *   ۱) ثبت خطاها از همان لحظهٔ اول — پیش‌تر اگر خطایی پیش از باز شدن صفحهٔ چت
 *      رخ می‌داد، هیچ‌جا ثبت نمی‌شد.
 *   ۲) دادن دسترسی موتور آفلاین به تنظیمات و پایگاه دانش، یک‌بار برای همیشه.
 */
class FokoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        OfflineEngine.attach(this)
    }
}
