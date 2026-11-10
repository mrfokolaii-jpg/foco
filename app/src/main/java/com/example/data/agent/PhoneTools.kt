package com.example.data.agent

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.service.FokoAccessibilityService
import com.example.service.FokoNotificationListener
import com.example.service.ReminderReceiver
import com.example.util.PersianDate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * ابزارهای کنترل گوشی: زدن، تایپ، اسکرول، اعلان‌ها، یادآور، زنگ و تقویم.
 */
object PhoneTools {

    private fun needAccessibility(): ToolOutcome? =
        if (!FokoAccessibilityService.isRunning())
            ToolOutcome(false, "سرویس دسترسی‌پذیری فوکو خاموش است؛ به کاربر بگو آن را در «تنظیمات و دسترسی‌ها» روشن کند.")
        else null

    fun tapText(text: String): ToolOutcome {
        needAccessibility()?.let { return it }
        if (text.isBlank()) return ToolOutcome(false, "متن دکمه مشخص نشده است.")
        return if (FokoAccessibilityService.clickNodeWithText(text))
            ToolOutcome(true, "روی «$text» زده شد. برای دیدن نتیجه read_screen را اجرا کن.")
        else ToolOutcome(false, "چیزی با متن «$text» روی صفحه پیدا نشد. اول read_screen را اجرا کن و متن دقیق را ببین.")
    }

    fun tapXY(x: Double, y: Double): ToolOutcome {
        needAccessibility()?.let { return it }
        return if (FokoAccessibilityService.clickCoordinates(x.toFloat(), y.toFloat()))
            ToolOutcome(true, "روی نقطه ($x، $y) زده شد.") else ToolOutcome(false, "زدن روی صفحه ناموفق بود.")
    }

    fun typeText(text: String, submit: Boolean): ToolOutcome {
        needAccessibility()?.let { return it }
        return if (FokoAccessibilityService.typeText(text, submit))
            ToolOutcome(true, "متن تایپ شد: $text")
        else ToolOutcome(false, "کادر نوشتنی روی صفحه پیدا نشد. اول روی کادر پیام بزن (tap_text) و دوباره امتحان کن.")
    }

    fun scroll(direction: String): ToolOutcome {
        needAccessibility()?.let { return it }
        val ok = if (direction.contains("up", true) || direction.contains("بالا")) FokoAccessibilityService.performScrollUp()
        else FokoAccessibilityService.performScrollDown()
        return if (ok) ToolOutcome(true, "اسکرول انجام شد.") else ToolOutcome(false, "در این صفحه اسکرول ممکن نبود.")
    }

    fun press(key: String): ToolOutcome {
        needAccessibility()?.let { return it }
        val k = key.lowercase()
        val ok = when {
            k.contains("back") || k.contains("برگشت") -> FokoAccessibilityService.performBack()
            k.contains("home") || k.contains("خانه") -> FokoAccessibilityService.performHome()
            k.contains("recent") || k.contains("اخیر") -> FokoAccessibilityService.performRecents()
            k.contains("notif") || k.contains("اعلان") -> FokoAccessibilityService.performNotifications()
            else -> return ToolOutcome(false, "کلید ناشناخته: $key (back, home, recents, notifications)")
        }
        return if (ok) ToolOutcome(true, "کلید $key زده شد.") else ToolOutcome(false, "اجرای کلید ناموفق بود.")
    }

    fun readNotifications(count: Int): ToolOutcome {
        if (!FokoNotificationListener.isConnected()) {
            return ToolOutcome(false, "دسترسی خواندن اعلان‌ها داده نشده است؛ به کاربر بگو در «تنظیمات و دسترسی‌ها» دسترسی اعلان‌ها را بدهد.")
        }
        val list = FokoNotificationListener.latest().take(count.coerceIn(1, 20))
        if (list.isEmpty()) return ToolOutcome(true, "اعلان تازه‌ای نیست.")
        val fmt = SimpleDateFormat("HH:mm", Locale.US)
        val text = list.mapIndexed { i, n ->
            val reply = if (FokoNotificationListener.canReply(n.key)) " [قابل جواب]" else ""
            "${i + 1}. ${FokoNotificationListener.appLabel(n.packageName)} — ${n.title}: ${n.text} (${fmt.format(n.postTime)})$reply"
        }.joinToString("\n")
        return ToolOutcome(true, "اعلان‌های اخیر (شماره برای جواب دادن):\n$text")
    }

    fun replyNotification(context: Context, index: Int, text: String): ToolOutcome {
        if (text.isBlank()) return ToolOutcome(false, "متن جواب خالی است.")
        val list = FokoNotificationListener.latest()
        val target = list.getOrNull(index - 1) ?: return ToolOutcome(false, "اعلان شماره $index پیدا نشد؛ اول read_notifications را اجرا کن.")
        return if (FokoNotificationListener.reply(context, target.key, text))
            ToolOutcome(true, "جواب «$text» برای ${target.title} فرستاده شد.")
        else ToolOutcome(false, "این اعلان دکمه جواب ندارد؛ می‌توانی برنامه را باز کنی و با tap_text و type_text جواب بدهی.")
    }

    /** زمان را از minutes یا at (yyyy-MM-dd HH:mm میلادی، یا HH:mm برای امروز/فردا) می‌خواند. */
    private fun resolveTime(minutes: Int, at: String): Long? {
        if (minutes > 0) return System.currentTimeMillis() + minutes * 60_000L
        val a = PersianDate.normalizeDigits(at.trim())
        if (a.isBlank()) return null
        val tz = TimeZone.getDefault()
        listOf("yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm").forEach { pattern ->
            try {
                val f = SimpleDateFormat(pattern, Locale.US).apply { timeZone = tz; isLenient = false }
                val d = f.parse(a)
                if (d != null) {
                    // اگر سال شمسی داده شده بود (۱۴xx) تبدیل کن
                    val cal = Calendar.getInstance().apply { time = d }
                    if (cal.get(Calendar.YEAR) in 1300..1500) {
                        val parts = a.split(' ')[0].split('-', '/').map { it.toInt() }
                        val g = PersianDate.toGregorian(parts[0], parts[1], parts[2])
                        val hm = a.split(' ')[1].split(':').map { it.toInt() }
                        return Calendar.getInstance().apply {
                            set(g[0], g[1] - 1, g[2], hm[0], hm[1], 0); set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    }
                    return d.time
                }
            } catch (_: Exception) {}
        }
        Regex("^(\\d{1,2}):(\\d{2})$").find(a)?.let { m ->
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, m.groupValues[1].toInt()); set(Calendar.MINUTE, m.groupValues[2].toInt())
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
            return cal.timeInMillis
        }
        return null
    }

    fun setReminder(context: Context, text: String, minutes: Int, at: String): ToolOutcome {
        val time = resolveTime(minutes, at) ?: return ToolOutcome(false, "زمان یادآور مشخص نیست. minutes یا at را به شکل 2026-11-12 08:00 بده.")
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val id = (time / 1000 % Int.MAX_VALUE).toInt()
        val pi = PendingIntent.getBroadcast(
            context, id,
            Intent(context, ReminderReceiver::class.java).putExtra(ReminderReceiver.EXTRA_TEXT, text.ifBlank { "یادآور فوکو" }),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
        else am.set(AlarmManager.RTC_WAKEUP, time, pi)
        return ToolOutcome(true, "یادآور «$text» برای ${PersianDate.describe(time)} تنظیم شد." +
            if (!exact) " (اجازه زنگ دقیق داده نشده؛ ممکن است چند دقیقه دیرتر بیاید)" else "")
    }

    fun setAlarm(context: Context, hour: Int, minute: Int, label: String): ToolOutcome {
        if (hour !in 0..23 || minute !in 0..59) return ToolOutcome(false, "ساعت نامعتبر است.")
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "زنگ فوکو" })
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolOutcome(true, "زنگ ساعت %02d:%02d تنظیم شد.".format(hour, minute))
        } catch (e: Exception) {
            ToolOutcome(false, "برنامه ساعت گوشی پیدا نشد: ${e.localizedMessage}")
        }
    }

    fun addCalendarEvent(context: Context, title: String, start: String, durationMinutes: Int): ToolOutcome {
        val begin = resolveTime(0, start) ?: return ToolOutcome(false, "زمان شروع رویداد را به شکل 2026-11-12 08:00 بده.")
        val end = begin + durationMinutes.coerceAtLeast(15) * 60_000L
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val calId = context.contentResolver.query(
                    CalendarContract.Calendars.CONTENT_URI,
                    arrayOf(CalendarContract.Calendars._ID),
                    "${CalendarContract.Calendars.VISIBLE}=1 AND ${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL}>=${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}",
                    null, "${CalendarContract.Calendars.IS_PRIMARY} DESC"
                )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
                if (calId != null) {
                    val values = ContentValues().apply {
                        put(CalendarContract.Events.CALENDAR_ID, calId)
                        put(CalendarContract.Events.TITLE, title)
                        put(CalendarContract.Events.DTSTART, begin)
                        put(CalendarContract.Events.DTEND, end)
                        put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                    }
                    context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                    return ToolOutcome(true, "رویداد «$title» برای ${PersianDate.describe(begin)} به تقویم اضافه شد.")
                }
            } catch (_: Exception) {}
        }
        return try {
            context.startActivity(Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            ToolOutcome(true, "صفحه افزودن رویداد در تقویم باز شد؛ کاربر باید ذخیره را بزند.")
        } catch (e: Exception) {
            ToolOutcome(false, "تقویم در دسترس نیست: ${e.localizedMessage}")
        }
    }

    /** پیدا کردن و باز کردن هر برنامه نصب‌شده با نام نمایشی آن. */
    fun findAndOpenApp(context: Context, name: String): String? {
        val pm = context.packageManager
        val q = name.trim().lowercase()
        if (q.isBlank()) return null
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = try { pm.queryIntentActivities(launcher, 0) } catch (_: Exception) { emptyList() }
        val match = apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == q }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) || q.contains(it.loadLabel(pm).toString().lowercase()) }
            ?: return null
        val intent = pm.getLaunchIntentForPackage(match.activityInfo.packageName) ?: return null
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return "برنامه «${match.loadLabel(pm)}» باز شد."
    }
}
