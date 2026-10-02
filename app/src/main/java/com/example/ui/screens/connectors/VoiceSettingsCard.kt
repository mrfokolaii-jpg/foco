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
import java.util.Locale

private const val TEST_SENTENCE = "سلام، من فوکو هستم. صدای من را تست کن، اگر دوست نداشتی یکی دیگر انتخاب کن."

/**
 * Voice picker for the spoken replies: choose a Persian voice, rename it, tune pitch/speed
 * and hear it right here.
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

    DisposableEffect(Unit) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val t = tts
                if (t != null) {
                    try {
                        t.language = Locale("fa", "IR")
                        t.setPitch(store.getTtsPitch())
                        t.setSpeechRate(store.getTtsRate())
                        voices = t.voices?.toList().orEmpty()
                            .filter { it.locale.language == "fa" || it.locale.language == "ar" }
                            .sortedBy { it.name }
                    } catch (_: Exception) {
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

    fun applyVoice(name: String) {
        store.setTtsVoice(name)
        selected = name
        try {
            val t = engine ?: return
            voices.firstOrNull { it.name == name }?.let { t.voice = it }
            t.setPitch(pitch)
            t.setSpeechRate(rate)
        } catch (_: Exception) {
        }
    }

    fun preview(name: String) {
        val t = engine ?: return
        try {
            t.stop()
            voices.firstOrNull { it.name == name }?.let { t.voice = it }
            t.setPitch(pitch)
            t.setSpeechRate(rate)
            t.speak(com.example.util.SpeechText.forSpeech(TEST_SENTENCE), TextToSpeech.QUEUE_FLUSH, null, "voice_test")
        } catch (_: Exception) {
        }
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
                text = if (ready) {
                    if (voices.isEmpty())
                        "روی این گوشی هیچ صدای فارسی نصب نیست. از تنظیمات اندروید یک موتور صدا با زبان فارسی نصب کن."
                    else
                        "صدای فارسی نصب‌شده روی گوشی: ${voices.size} مورد. یکی را انتخاب کن، اسمش را عوض کن و همان‌جا تست بگیر."
                } else {
                    "در حال آماده‌سازی موتور صدا..."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val visible = if (showAll) voices else voices.take(4)
            visible.forEach { voice ->
                val isSelected = selected == voice.name
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSelected) PersianTurquoise.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { applyVoice(voice.name) }
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
                            text = voice.locale.displayName + " · " + voice.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

            if (voices.size > 4) {
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

            Text("زیر و بمی صدا: ${"%.2f".format(pitch)}", style = MaterialTheme.typography.labelSmall)
            Slider(
                value = pitch,
                onValueChange = {
                    pitch = it
                    store.setTtsPitch(it)
                    try {
                        engine?.setPitch(it)
                    } catch (_: Exception) {
                    }
                },
                valueRange = 0.5f..1.6f
            )

            Text("سرعت گفتار: ${"%.2f".format(rate)}", style = MaterialTheme.typography.labelSmall)
            Slider(
                value = rate,
                onValueChange = {
                    rate = it
                    store.setTtsRate(it)
                    try {
                        engine?.setSpeechRate(it)
                    } catch (_: Exception) {
                    }
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
                    text = "این صدا در ویجت شناور، دکمه پخش پاسخ در چت و مکالمه زنده استفاده می‌شود.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
