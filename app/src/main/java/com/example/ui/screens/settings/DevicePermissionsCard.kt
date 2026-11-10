package com.example.ui.screens.settings

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.service.FokoAccessibilityService
import com.example.ui.theme.PersianTurquoise

/** Every runtime (dangerous) permission Android lets a normal app ask for on this device. */
fun fokoRuntimePermissions(): List<String> {
    val list = mutableListOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.BODY_SENSORS
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) list += Manifest.permission.ACTIVITY_RECOGNITION
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        list += Manifest.permission.BLUETOOTH_CONNECT
        list += Manifest.permission.BLUETOOTH_SCAN
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        list += Manifest.permission.POST_NOTIFICATIONS
        list += Manifest.permission.READ_MEDIA_IMAGES
        list += Manifest.permission.READ_MEDIA_VIDEO
        list += Manifest.permission.READ_MEDIA_AUDIO
        list += Manifest.permission.NEARBY_WIFI_DEVICES
    } else {
        list += Manifest.permission.READ_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) list += Manifest.permission.WRITE_EXTERNAL_STORAGE
    }
    return list
}

private data class SpecialAccess(
    val title: String,
    val granted: (Context) -> Boolean,
    val open: (Context) -> Unit
)

private fun Context.openSafely(vararg intents: Intent) {
    for (i in intents) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) { }
    }
    Toast.makeText(this, "این صفحه روی گوشی شما پیدا نشد", Toast.LENGTH_SHORT).show()
}

private fun appDetails(ctx: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))

@Suppress("DEPRECATION")
private fun AppOpsManager.unsafeCheckOpNoThrowCompat(pkg: String): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), pkg)
    else
        checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), pkg)

private fun usageAccessGranted(ctx: Context): Boolean = try {
    val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    ops.unsafeCheckOpNoThrowCompat(ctx.packageName) == AppOpsManager.MODE_ALLOWED
} catch (_: Exception) { false }

private fun specialAccessList(): List<SpecialAccess> {
    val list = mutableListOf(
        SpecialAccess(
            "نمایش روی برنامه‌های دیگر (ویجت شناور)",
            { Settings.canDrawOverlays(it) },
            { it.openSafely(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${it.packageName}")), appDetails(it)) }
        ),
        SpecialAccess(
            "دسترسی‌پذیری (کنترل کامل صفحه و برنامه‌ها)",
            { FokoAccessibilityService.isEnabledInSystem(it) },
            { it.openSafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        ),
        SpecialAccess(
            "خواندن اعلان‌ها",
            { NotificationManagerCompat.getEnabledListenerPackages(it).contains(it.packageName) },
            { it.openSafely(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        ),
        SpecialAccess(
            "حالت مزاحم نشوید",
            { (it.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted },
            { it.openSafely(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
        ),
        SpecialAccess(
            "تغییر تنظیمات سیستم (روشنایی، صدا...)",
            { Settings.System.canWrite(it) },
            { it.openSafely(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${it.packageName}")), appDetails(it)) }
        ),
        SpecialAccess(
            "آمار استفاده از برنامه‌ها",
            { usageAccessGranted(it) },
            { it.openSafely(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        ),
        SpecialAccess(
            "اجرا در پس‌زمینه بدون محدودیت باتری",
            { (it.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(it.packageName) },
            {
                @Suppress("BatteryLife")
                val req = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${it.packageName}"))
                it.openSafely(req, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        )
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        list += SpecialAccess(
            "دسترسی به همه فایل‌ها",
            { Environment.isExternalStorageManager() },
            {
                it.openSafely(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${it.packageName}")),
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                )
            }
        )
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        list += SpecialAccess(
            "یادآور و آلارم دقیق",
            { (it.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms() },
            { it.openSafely(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${it.packageName}")), appDetails(it)) }
        )
    }
    return list
}

@Composable
fun DevicePermissionsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }

    // Re-check every time the user comes back from a system settings page
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val runtimePerms = remember { fokoRuntimePermissions() }
    val specials = remember { specialAccessList() }

    val missingRuntime = remember(refreshTick) {
        runtimePerms.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    }
    val grantedRuntime = runtimePerms.size - missingRuntime.size

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        refreshTick++
        val denied = result.count { !it.value }
        if (denied > 0) {
            Toast.makeText(
                context,
                "$denied مجوز داده نشد. اگر خاکستری است، از اطلاعات برنامه ⋮ «Allow restricted settings» را بزنید",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "دسترسی کامل فوکو به گوشی",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = PersianTurquoise
            )
            Text(
                "مجوزهای عادی: $grantedRuntime از ${runtimePerms.size} فعال است",
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = {
                    if (missingRuntime.isEmpty()) {
                        Toast.makeText(context, "همه مجوزهای عادی فعال است", Toast.LENGTH_SHORT).show()
                    } else {
                        launcher.launch(missingRuntime.toTypedArray())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PersianTurquoise)
            ) {
                Text("درخواست همه مجوزها یکجا", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Text(
                "دسترسی‌های ویژه (هرکدام صفحه تنظیمات خودش را دارد):",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
            specials.forEach { item ->
                val ok = remember(refreshTick, item.title) { try { item.granted(context) } catch (_: Exception) { false } }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = if (ok) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (ok) PersianTurquoise else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(item.title, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { item.open(context) }) { Text(if (ok) "فعال" else "فعال‌سازی") }
                }
            }

            Text(
                "اندروید ۱۳ به بالا: برای برنامه‌ای که خارج از Play نصب شده، دسترسی‌پذیری و پیامک/تماس قفل است. " +
                    "اول در اطلاعات برنامه، منوی ⋮ بالا را بزنید و «Allow restricted settings» را فعال کنید.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { context.openSafely(appDetails(context)) }) {
                Text("باز کردن اطلاعات برنامه")
            }
        }
    }
}
