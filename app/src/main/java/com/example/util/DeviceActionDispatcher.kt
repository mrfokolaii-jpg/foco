package com.example.util

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import com.example.service.FokoAccessibilityService

object DeviceActionDispatcher {

    fun executeIfIntent(context: Context, text: String): String? {
        val lower = text.trim().lowercase()

        // Screen Awareness & Reading (JARVIS-1 Core)
        if (lower.contains("صفحه رو بخون") || lower.contains("متن صفحه") || lower.contains("صفحه چی نوشته") || lower.contains("دیدن صفحه")) {
            val screenText = FokoAccessibilityService.getScreenContentHierarchy()
            return "محتوای خوانده‌شده از صفحه جاری:\n$screenText"
        }

        // Screen Scroll
        if (lower.contains("برو پایین") || lower.contains("اسکرول پایین") || lower.contains("پایین تر")) {
            if (FokoAccessibilityService.performScrollDown()) return "صفحه به سمت پایین اسکرول شد."
        }
        if (lower.contains("برو بالا") || lower.contains("اسکرول بالا") || lower.contains("بالاتر")) {
            if (FokoAccessibilityService.performScrollUp()) return "صفحه به سمت بالا اسکرول شد."
        }

        // Global Navigation Actions (Accessibility)
        if (lower == "برگرد" || lower == "برو عقب" || lower.contains("دکمه برگشت")) {
            if (FokoAccessibilityService.performBack()) return "فرمان بازگشت (Back) اجرا شد."
        }
        if (lower.contains("صفحه اصلی") || lower.contains("برو هوم") || lower == "هوم") {
            if (FokoAccessibilityService.performHome()) return "به صفحه اصلی (Home) هدایت شدید."
        }
        if (lower.contains("برنامه های باز") || lower.contains("برنامه های اخیر") || lower.contains("recent apps")) {
            if (FokoAccessibilityService.performRecents()) return "لیست برنامه‌های اخیر باز شد."
        }
        if (lower.contains("اعلان") || lower.contains("نوتیفیکیشن")) {
            if (FokoAccessibilityService.performNotifications()) return "پنل اعلان‌ها باز شد."
        }

        // Set Alarm Clock
        if (lower.contains("آلارم") || lower.contains("ساعت رو کوک کن") || lower.contains("بیدارم کن")) {
            return try {
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_MESSAGE, "آلارم فوکو (FOKO AI)")
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                "در حال تنظیم و باز کردن ساعت بیدارباش (Alarm)..."
            } catch (e: Exception) {
                "بخش آلارم ساعت باز شد."
            }
        }

        // Open SMS / Messages
        if (lower.contains("پیامک") || lower.contains("پیام ها") || lower.contains("اس ام اس") || lower.contains("sms")) {
            return try {
                val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(context)
                if (defaultSmsPackage != null) {
                    launchAppOrMarket(context, defaultSmsPackage, "پیامک‌ها")
                } else {
                    val intent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_APP_MESSAGING)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    "در حال باز کردن بخش پیامک‌ها..."
                }
            } catch (e: Exception) {
                "در حال باز کردن بخش پیامک‌های دستگاه..."
            }
        }

        // Search Web / Google
        if (lower.contains("سرچ کن") || lower.contains("جستجو کن") || lower.contains("گوگل سرچ") || lower.contains("برو داخل گوگل")) {
            val query = text.replace("سرچ کن", "")
                .replace("جستجو کن", "")
                .replace("برو داخل گوگل", "")
                .replace("در گوگل", "")
                .trim()
            return try {
                val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra("query", query)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                "در حال جستجو برای «$query» در گوگل..."
            } catch (e: Exception) {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(browserIntent)
                "در حال باز کردن نتایج جستجو در مرورگر..."
            }
        }

        // GitHub
        if (lower.contains("گیت هاب") || lower.contains("گیتهاب") || lower.contains("github")) {
            return try {
                val store = com.example.data.connectors.ConnectorStore(context)
                val url = "https://github.com/${store.githubOwner()}/${store.githubRepo()}"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                "در حال باز کردن مخزن پروژه شما در گیت‌هاب..."
            } catch (e: Exception) {
                null
            }
        }

        // Open Telegram
        if (lower.contains("تلگرام") || lower.contains("telegram")) {
            return launchAppOrMarket(context, "org.telegram.messenger", "تلگرام")
        }

        // Open WhatsApp
        if (lower.contains("واتساپ") || lower.contains("whatsapp")) {
            return launchAppOrMarket(context, "com.whatsapp", "واتساپ")
        }

        // Open Instagram
        if (lower.contains("اینستاگرام") || lower.contains("instagram")) {
            return launchAppOrMarket(context, "com.instagram.android", "اینستاگرام")
        }

        // Open YouTube
        if (lower.contains("یوتیوب") || lower.contains("youtube")) {
            return launchAppOrMarket(context, "com.google.android.youtube", "یوتیوب")
        }

        // Open Gmail
        if (lower.contains("جیمیل") || lower.contains("gmail") || lower.contains("ایمیل")) {
            return launchAppOrMarket(context, "com.google.android.gm", "جیمیل")
        }

        // Open Google Drive
        if (lower.contains("گوگل درایو") || lower.contains("google drive") || lower.contains("درایو")) {
            return launchAppOrMarket(context, "com.google.android.apps.docs", "گوگل درایو")
        }

        // Open Camera
        if (lower.contains("دوربین") || lower.contains("عکس بگیر") || lower.contains("camera")) {
            return try {
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                "در حال باز کردن دوربین دستگاه..."
            } catch (e: Exception) {
                null
            }
        }

        // Open Phone / Dialer / Contacts
        if (lower.contains("تلفن") || lower.contains("تماس") || lower.contains("شماره گیر") || lower.contains("دفترچه تلفن")) {
            return try {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                "در حال باز کردن شماره‌گیر تلفن..."
            } catch (e: Exception) {
                null
            }
        }

        // Open Device Settings
        if (lower.contains("تنظیمات گوشی") || lower.contains("تنظیمات دستگاه") || lower.contains("settings")) {
            return try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                "در حال باز کردن تنظیمات دستگاه..."
            } catch (e: Exception) {
                null
            }
        }

        // Volume adjustments
        if (lower.contains("صدای گوشی") || lower.contains("ولوم")) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                if (lower.contains("کم") || lower.contains("پایین")) {
                    audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    return "صدای دستگاه کاهش یافت."
                } else if (lower.contains("زیاد") || lower.contains("بالا")) {
                    audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    return "صدای دستگاه افزایش یافت."
                }
            }
        }

        // Open Chrome / Browser
        if (lower.contains("مرورگر") || lower.contains("کروم") || lower.contains("chrome") || lower.contains("اینترنت")) {
            return try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                "در حال باز کردن مرورگر اینترنت..."
            } catch (e: Exception) {
                null
            }
        }

        return null
    }

    private fun launchAppOrMarket(context: Context, packageName: String, appName: String): String {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(launchIntent)
                "در حال باز کردن اپلیکیشن $appName..."
            } else {
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(marketIntent)
                "اپلیکیشن $appName نصب نیست؛ صفحه نصب باز شد."
            }
        } catch (e: Exception) {
            "امکان اجرای مستقیم $appName فراهم نشد."
        }
    }
}
