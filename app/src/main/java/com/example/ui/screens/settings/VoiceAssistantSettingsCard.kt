package com.example.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.connectors.ConnectorStore
import com.example.data.local.ApiKeyStore
import com.example.data.local.VoicePrefs
import com.example.service.FokoFloatingOverlayService
import com.example.voice.NaturalTts
import com.example.voice.VoskEngine
import kotlinx.coroutines.launch

/**
 * تنظیمات دستیار صوتی: کلمه بیداری، تشخیص آفلاین، صدای طبیعی، قطع کردن وسط حرف،
 * پاسخ استریم، ابزارهای بومی، توضیح زنده صفحه و پراکسی.
 */
@Composable
fun VoiceAssistantSettingsCard() {
    val context = LocalContext.current
    val prefs = remember { VoicePrefs(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var wake by remember { mutableStateOf(prefs.wakeWordEnabled) }
    var offline by remember { mutableStateOf(prefs.offlineSttEnabled) }
    var natural by remember { mutableStateOf(prefs.naturalVoiceEnabled) }
    var voiceName by remember { mutableStateOf(prefs.naturalVoiceName) }
    var barge by remember { mutableStateOf(prefs.bargeInEnabled) }
    var stream by remember { mutableStateOf(prefs.streamingEnabled) }
    var nativeTools by remember { mutableStateOf(prefs.nativeToolsEnabled) }
    var live by remember { mutableStateOf(prefs.liveExplainEnabled) }
    var proxyOn by remember { mutableStateOf(prefs.proxyEnabled) }
    var proxyType by remember { mutableStateOf(prefs.proxyType) }
    var proxyHost by remember { mutableStateOf(prefs.proxyHost) }
    var proxyPort by remember { mutableStateOf(prefs.proxyPort.toString()) }

    var modelReady by remember { mutableStateOf(VoskEngine.isModelReady(context)) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }

    fun applyToService() {
        if (ConnectorStore(context).isOverlayEnabled()) FokoFloatingOverlayService.refreshSettings(context.applicationContext)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("دستیار صوتی و شبکه", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // مدل آفلاین
            Text(
                if (modelReady) "مدل گفتار آفلاین فارسی نصب است."
                else "برای کلمه بیداری و تشخیص آفلاین، مدل فارسی (حدود ۵۰ مگابایت) را یک بار دانلود کن.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (downloading) {
                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text("در حال دانلود: $progress٪", style = MaterialTheme.typography.bodySmall)
            } else if (!modelReady) {
                Button(onClick = {
                    downloading = true
                    scope.launch {
                        val r = VoskEngine.downloadModel(context.applicationContext) { progress = it }
                        downloading = false
                        modelReady = VoskEngine.isModelReady(context)
                        Toast.makeText(
                            context,
                            if (r.isSuccess && modelReady) "مدل آفلاین نصب شد" else "دانلود ناموفق: ${r.exceptionOrNull()?.localizedMessage ?: "خطا"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }) { Text("دانلود مدل آفلاین فارسی") }
            }

            SettingSwitch("بیدار شدن با گفتن «فوکو»", "بدون زدن دکمه، با شنیدن اسمش گوش می‌دهد (دستیار شناور باید روشن باشد).", wake) {
                if (it && !modelReady) {
                    Toast.makeText(context, "اول مدل آفلاین را دانلود کن", Toast.LENGTH_SHORT).show()
                } else { wake = it; prefs.wakeWordEnabled = it; applyToService() }
            }
            SettingSwitch("تشخیص گفتار آفلاین", "بدون سرویس گوگل و بدون اینترنت؛ دقتش کمی کمتر است.", offline) {
                if (it && !modelReady) {
                    Toast.makeText(context, "اول مدل آفلاین را دانلود کن", Toast.LENGTH_SHORT).show()
                } else { offline = it; prefs.offlineSttEnabled = it }
            }
            SettingSwitch("صدای طبیعی فارسی", "با کلید جمنای یا OpenAI؛ اگر نشد صدای گوشی استفاده می‌شود.", natural) {
                natural = it; prefs.naturalVoiceEnabled = it
                if (it && !NaturalTts.isAvailable(ApiKeyStore(context))) {
                    Toast.makeText(context, "برای صدای طبیعی، کلید جمنای یا OpenAI را در بخش کلیدها وارد کن", Toast.LENGTH_LONG).show()
                }
            }
            if (natural) {
                Text("صدای جمنای:", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Kore", "Puck", "Charon", "Aoede").forEach { v ->
                        FilterChip(selected = voiceName == v, onClick = { voiceName = v; prefs.naturalVoiceName = v }, label = { Text(v) })
                    }
                }
            }
            SettingSwitch("قطع کردن وسط حرف", "وقتی فوکو حرف می‌زند، با شروع صحبت تو ساکت می‌شود. زدن روی دایره هم قطعش می‌کند.", barge) {
                barge = it; prefs.bargeInEnabled = it
            }
            SettingSwitch("جواب سریع (جمله به جمله)", "جواب کلمه به کلمه می‌رسد و جمله اول همان لحظه خوانده می‌شود.", stream) {
                stream = it; prefs.streamingEnabled = it
            }
            SettingSwitch("ابزارهای بومی مدل", "اجرای ابزارها با سیستم رسمی مدل؛ اگر مدلی پشتیبانی نکند خودکار به روش قبلی برمی‌گردد.", nativeTools) {
                nativeTools = it; prefs.nativeToolsEnabled = it
            }
            SettingSwitch("توضیح زنده صفحه", "هر بار صفحه عوض شد، کوتاه توضیح می‌دهد یا ترجمه می‌کند (دسترسی‌پذیری لازم است).", live) {
                live = it; prefs.liveExplainEnabled = it; applyToService()
            }

            Spacer(Modifier.height(4.dp))
            SettingSwitch("پراکسی برای اتصال به مدل‌ها", "مثلاً پراکسی برنامه فیلترشکن روی همین گوشی.", proxyOn) {
                proxyOn = it; prefs.proxyEnabled = it
            }
            if (proxyOn) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("SOCKS", "HTTP").forEach { t ->
                        FilterChip(selected = proxyType == t, onClick = { proxyType = t; prefs.proxyType = t }, label = { Text(t) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = proxyHost,
                        onValueChange = { proxyHost = it; prefs.proxyHost = it },
                        label = { Text("آدرس") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = proxyPort,
                        onValueChange = { v ->
                            proxyPort = v.filter { it.isDigit() }.take(5)
                            proxyPort.toIntOrNull()?.let { prefs.proxyPort = it }
                        },
                        label = { Text("پورت") },
                        singleLine = true,
                        modifier = Modifier.width(110.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
