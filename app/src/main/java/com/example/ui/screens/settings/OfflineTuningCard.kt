package com.example.ui.screens.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.offline.OfflineEngine
import com.example.data.offline.OfflineSettings
import com.example.data.offline.OfflineTuning
import com.example.ui.theme.PersianTurquoise

/**
 * تنظیمات سرعت، کیفیت و گرمای مدل آفلاین.
 *
 * مهم‌ترین نکته‌ای که این کارت حل می‌کند: روی گوشی، «کند» و «داغ» یک ریشه دارند.
 * اگر همهٔ هسته‌ها (حتی هسته‌های ضعیف) تا آخر کار کنند، مدل منتظرِ کندترین هسته
 * می‌ماند: هم دیرتر جواب می‌دهد و هم گوشی را بیشتر گرم می‌کند. کم کردن «تعداد رشته»
 * و «پنجرهٔ متن» معمولاً هر دو مشکل را با هم کمتر می‌کند.
 */
@Composable
fun OfflineTuningCard() {
    val context = LocalContext.current
    var tuning by remember { mutableStateOf(OfflineSettings.load(context)) }
    var saved by remember { mutableStateOf("") }

    val recommendedThreads = remember { OfflineSettings.recommendedThreads() }
    val cores = remember { Runtime.getRuntime().availableProcessors() }

    fun update(newTuning: OfflineTuning) {
        OfflineSettings.save(context, newTuning)
        tuning = OfflineSettings.load(context)
        saved = "✓ ذخیره شد — از پیام بعدی اعمال می‌شود"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Speed, contentDescription = null, tint = PersianTurquoise)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "تنظیمات سرعت و گرمای گوشی",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "گوشی تو $cores هسته دارد. هرچه «رشته» کمتر باشد، گوشی خنک‌تر می‌ماند؛ " +
                    "و هرچه «پنجرهٔ متن» کمتر باشد، پاسخ سریع‌تر می‌آید.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ---- تعداد رشته ----
            ChoiceRow(
                label = "تعداد رشتهٔ پردازشی (کمتر = خنک‌تر)",
                values = OfflineTuning.THREAD_CHOICES,
                selected = tuning.threads,
                labelOf = { if (it == recommendedThreads) "$it (پیشنهادی)" else "$it" },
                onSelect = { update(tuning.copy(threads = it)) }
            )

            // ---- پنجرهٔ متن ----
            ChoiceRow(
                label = "پنجرهٔ متن — چند توکن به یاد بیاورد (کمتر = سریع‌تر)",
                values = OfflineTuning.CONTEXT_CHOICES,
                selected = tuning.contextSize,
                labelOf = { it.toString() },
                onSelect = { update(tuning.copy(contextSize = it)) }
            )

            // ---- حداکثر طول پاسخ ----
            ChoiceRow(
                label = "حداکثر طول پاسخ (اگر جواب نیمه‌کاره ماند، ببرش بالا)",
                values = OfflineTuning.MAX_TOKEN_CHOICES,
                selected = tuning.maxTokens,
                labelOf = { it.toString() },
                onSelect = { update(tuning.copy(maxTokens = it)) }
            )

            // ---- دقت و خلاقیت ----
            SliderRow(
                label = "دقت در برابر خلاقیت (temperature)",
                value = tuning.temperature,
                range = 0f..1.2f,
                hint = "کم = دقیق‌تر و منطقی‌تر · زیاد = متنوع‌تر",
                onChange = { update(tuning.copy(temperature = it)) }
            )

            SliderRow(
                label = "تنوع واژه‌ها (top_p)",
                value = tuning.topP,
                range = 0.1f..1f,
                hint = "۰.۹ برای بیشتر کارها خوب است",
                onChange = { update(tuning.copy(topP = it)) }
            )

            ChoiceRow(
                label = "تعداد گزینه‌های هر کلمه (top_k)",
                values = listOf(10, 20, 40, 80),
                selected = tuning.topK,
                labelOf = { it.toString() },
                onSelect = { update(tuning.copy(topK = it)) }
            )

            // ---- GPU ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "استفاده از GPU (Vulkan)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "موتور فعلی فقط CPU است. خودِ کتابخانه اعلام کرده که " +
                            "اجرای مدل روی GPU فقط در نسخهٔ Pro فعال می‌شود، پس این کلید " +
                            "در این نسخه کار نمی‌کند و خاموش است.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = false,
                    onCheckedChange = null,
                    enabled = false,
                    colors = SwitchDefaults.colors(checkedTrackColor = PersianTurquoise)
                )
            }

            // هشدار گرمای گوشی — صادقانه، چون کاربر از داغ شدن شکایت داشت.
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "GPU هم گوشی را گرم می‌کند؛ تفاوت واقعی GPU در سرعت است، نه در خنکی. " +
                        "برای خنک‌تر شدن، «تعداد رشته» را ۳ یا ۴ بگذار و بین پاسخ‌ها کمی صبر کن.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TextButton(
                onClick = {
                    tuning = OfflineSettings.reset(context)
                    saved = "✓ به مقدار پیشنهادی برگشت"
                }
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("بازگشت به تنظیمات پیشنهادی", fontSize = 12.sp)
            }

            if (saved.isNotBlank()) {
                Text(
                    text = saved,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981)
                )
            }
        }
    }
}

/** یک ردیف انتخاب از بین چند عدد (مثل تعداد رشته یا پنجرهٔ متن). */
@Composable
private fun ChoiceRow(
    label: String,
    values: List<Int>,
    selected: Int,
    labelOf: (Int) -> String,
    onSelect: (Int) -> Unit
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            values.forEach { value ->
                val isSelected = value == selected
                if (isSelected) {
                    Button(
                        onClick = { },
                        modifier = Modifier.height(38.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PersianTurquoise)
                    ) {
                        Text(labelOf(value), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSelect(value) },
                        modifier = Modifier.height(38.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(labelOf(value), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/** یک ردیف لغزنده برای تنظیم عددی پیوسته. */
@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    hint: String,
    onChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "%.2f".format(value),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = PersianTurquoise
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
