package com.example.ui.screens.connectors

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.ApiKeyStore
import com.example.ui.theme.PersianTurquoise
import com.example.util.FokoVoiceCtl
import com.example.util.SpeechText

private const val TEST_SENTENCE = "سلام، من فوکو هستم. صدای من را تست کن، اگر دوست نداشتی یکی دیگر انتخاب کن."

/**
 * انتخاب صدای پاسخ‌های گفتاری: صدا را انتخاب کن، اسمش را عوض کن، زیر‌و‌بمی و سرعت را
 * تنظیم کن و همان‌جا بشنو.
 *
 * نکته‌ی مهم: با هر انتخاب، صدا بلافاصله در چت و ویجت شناور هم عوض می‌شود
 * (به‌کمک `FokoVoiceCtl`) — دیگر نیازی به بستن و باز‌کردن اپ نیست.
 */
@Composable
fun VoiceSettingsCard(context: Context) {

    val store = remember { ApiKeyStore(context) }
    var engine by remember { mutableStateOf<TextToSpeech?>(null) }
    var ready by remember { mutableStateOf(false) }
    var voices by remember { mutableStateOf<List<Voice>>(emptyList()) }
    var showAll by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(store.getTtsVoice()) }
    var labels by remember { mutableStateOf(store.getVoiceLabels()) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var pitch by remember { mutableStateOf(store.getTtsPitch()) }
    var rate by remember { mutableStateOf(store.getTtsRate()) }

    val persianVoices = voices.filter { FokoVoiceCtl.isPersian(it) }

    DisposableEffect(Unit) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val t = tts
                if (t != null) {
                    SpeechText.init(context.applicationContext)
                    FokoVoiceCtl.applySettings(context.applicationContext, t)
                    val list = FokoVoiceCtl.voicesOf(t)
                    voices = list
                    if (selected.isBlank()) {
                        selected = try {
                            t.voice?.name.orEmpty()
                        } catch (_: Exception) {
                            ""
                        }
                    }
                    ready = true
                }
            }
        }
        engine = tts
        onDispose {
            try {
                tts?.stop()
                tts?.shutdown()
            } catch (_: Exception) {
            }
        }
    }

    fun labelFor(v: Voice): String = labels[v.name]?.takeIf { it.isNotBlank() } ?: v.name

    /** صدا را روی موتور تست می‌گذارد تا پیش‌نمایش درست شنیده شود. */
    fun setOnEngine(name: String) {
        try {
            val t = engine ?: return
            voices.firstOrNull { it.name == name }?.let { t.voice = it }
            t.setPitch(pitch)
            t.setSpeechRate(rate)
        } catch (_: Exception) {
        }
    }

    /** انتخاب صدا: ذخیره + خبر‌کردن چت و ویجت + شنیدن فوری. */
    fun applyVoice(name: String) {
        selected = name
        setOnEngine(name)
        FokoVoiceCtl.persistAndNotify(context.applicationContext, name, null, null)
    }

    fun preview(name: String) {
        val t = engine ?: return
        try {
            t.stop()
        } catch (_: Exception) {
        }
        setOnEngine(name)
        FokoVoiceCtl.speak(t, TEST_SENTENCE, "voice_test")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = PersianTurquoise)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "صدا — انتخاب، تغییر نام و تست",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = if (!ready) {
                    "در حال آماده‌سازی موتور صدا..."
                } else if (persianVoices.isEmpty()) {
                    "روی این گوشی هیچ صدای فارسی نصب نیست. دکمه‌ی زیر را بزن و از صفحه‌ی تنظیمات صدای اندروید، بسته‌ی زبان فارسی را نصب کن."
                } else {
                    "صدای فارسی آماده: ${persianVoices.size} مورد (از مجموع ${voices.size} صدای گوشی). روی هر صدا بزن تا هم انتخاب شود و هم همان لحظه بشنوی."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (ready && persianVoices.isEmpty()) {
                Button(
                    onClick = { FokoVoiceCtl.openSystemTtsSettings(context) },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PersianTurquoise)
                ) {
                    Text("نصب / فعال‌سازی صدای فارسی", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            val visible = if (showAll) voices else voices.take(6)
            visible.forEach { voice ->
                val isSelected = selected == voice.name
                val isPersian = FokoVoiceCtl.isPersian(voice)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSelected) PersianTurquoise.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            applyVoice(voice.name)
                            preview(voice.name)
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = labelFor(voice) + if (isSelected) "  ✓" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        Text(
                            text = (if (isPersian) "فارسی · " else "") + voice.locale.displayName + " · " + voice.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isPersian) PersianTurquoise else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { preview(voice.name) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "تست صدا", tint = PersianTurquoise)
                    }
                    IconButton(onClick = {
                        renameTarget = voice.name
                        renameText = labels[voice.name].orEmpty()
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "تغییر نام", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (voices.size > 6) {
                TextButton(onClick = { showAll = !showAll }) {
                    Text(if (showAll) "نمایش کمتر" else "نمایش همه صداها (${voices.size})", color = PersianTurquoise)
                }
            }

            renameTarget?.let { target ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("نام دلخواه این صدا") },
                        placeholder = { Text("مثلاً آقا امیر") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            store.setVoiceLabel(target, renameText)
                            labels = store.getVoiceLabels()
                            renameTarget = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PersianTurquoise),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("ذخیره", color = Color.Black, fontWeight = FontWeight.Bold) }
                }
            }

            Text("زیر و بمی صدا: ${
                "%.2f".format(pitch)
            }", style = MaterialTheme.typography.labelSmall)
            Slider(
                value = pitch,
                onValueChange = {
                    pitch = it
                    try {
                        engine?.setPitch(it)
                    } catch (_: Exception) {
                    }
                    FokoVoiceCtl.persistAndNotify(context.applicationContext, null, it, null)
                },
                valueRange = 0.5f..1.6f
            )

            Text("سرعت گفتار: ${
                "%.2f".format(rate)
            }", style = MaterialTheme.typography.labelSmall)
            Slider(
                value = rate,
                onValueChange = {
                    rate = it
                    try {
                        engine?.setSpeechRate(it)
                    } catch (_: Exception) {
                    }
                    FokoVoiceCtl.persistAndNotify(context.applicationContext, null, null, it)
                },
                valueRange = 0.6f..1.6f
            )

            Button(
                onClick = {
                    val target = selected.ifBlank { voices.firstOrNull()?.name.orEmpty() }
                    if (target.isNotBlank()) preview(target)
                },
                enabled = ready && voices.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PersianTurquoise)
            ) {
                Text("تست صدای انتخاب‌شده", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "این صدا در ویجت شناور، دکمه پخش پاسخ در چت و مکالمه زنده استفاده می‌شود. عددها، علامت‌ها و کلمه‌های انگلیسی پیش از خواندن، خودکار به فارسی برگردانده می‌شوند.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
