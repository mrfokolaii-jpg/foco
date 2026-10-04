package com.example.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.offline.KnowledgeBase
import com.example.data.offline.OfflineSettings
import com.example.data.remote.ApiResult
import com.example.ui.theme.PersianTurquoise
import kotlinx.coroutines.launch

/**
 * پایگاه دانش محلی.
 *
 * یک فایل ZIP حاوی فایل‌های متنی (`.md` / `.txt`) را می‌گیرد، داخل گوشی باز می‌کند و
 * موقع پرسیدن سؤال فقط بخش مربوط به همان سؤال را به مدل آفلاین می‌دهد. نتیجه: مدل
 * ۱.۵ میلیاردی هم می‌تواند دربارهٔ موضوعات تخصصی جواب درست بدهد، بدون اینترنت.
 *
 * با یک مثال واقعی هم می‌شود امتحان کرد: در کادر جستجو یک کلمه بنویس و ببین اپ
 * کدام بخش‌ها را پیدا می‌کند — همان‌ها به مدل داده می‌شوند.
 */
@Composable
fun KnowledgeBaseCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var stats by remember { mutableStateOf(KnowledgeBase.stats(context)) }
    var enabled by remember { mutableStateOf(OfflineSettings.load(context).knowledgeEnabled) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(KnowledgeBase.search(context, "")) }

    val zipPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            status = "در حال خواندن و فهرست‌سازی پایگاه دانش..."
            when (val result = KnowledgeBase.importZip(context, uri)) {
                is ApiResult.Success -> {
                    stats = result.data
                    status = "✓ پایگاه دانش آماده شد: ${result.data.documents} سند و " +
                        "${result.data.chunks} قطعه"
                }
                is ApiResult.Error -> status = "✗ ${result.message}"
            }
            busy = false
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Menu, contentDescription = null, tint = PersianTurquoise)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "پایگاه دانش (دانش تخصصی، بدون اینترنت)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "یک فایل ZIP از فایل‌های متنی بده. اپ فقط بخش مربوط به سؤال تو را " +
                    "پیدا می‌کند و به مدل آفلاین می‌دهد — بدون اینترنت و بدون بزرگ کردن مدل.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = if (stats.chunks > 0) {
                    "وضعیت: ${stats.documents} سند · ${stats.chunks} قطعه · ${"%.1f".format(stats.bytes / 1024.0 / 1024.0)} مگابایت"
                } else {
                    "وضعیت: خالی — هنوز فایلی اضافه نشده"
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = { zipPicker.launch(arrayOf("*/*")) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PersianTurquoise)
            ) {
                Text("انتخاب فایل ZIP پایگاه دانش", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "استفاده از پایگاه دانش در پاسخ‌های آفلاین",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = { on ->
                        val updated = OfflineSettings.load(context).copy(knowledgeEnabled = on)
                        OfflineSettings.save(context, updated)
                        enabled = on
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = PersianTurquoise)
                )
            }

            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = PersianTurquoise
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("کمی صبر کن...", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (status.isNotBlank()) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (status.startsWith("✓")) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                )
            }

            // ---- آزمون واقعی جستجو ----
            if (stats.chunks > 0) {
                Text(
                    text = "امتحان کن: بنویس «سئو» یا «ترید» و ببین چه چیزی پیدا می‌شود:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        results = KnowledgeBase.search(context, it, limit = 3)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    placeholder = { Text("مثلاً: طراحی سایت") }
                )
                results.forEach { hit ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "• ${hit.title}  (${hit.category}) — امتیاز ${hit.score}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PersianTurquoise
                        )
                        Text(
                            text = hit.text.take(160).replace('\n', ' ') + "…",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TextButton(onClick = {
                    KnowledgeBase.clear(context)
                    stats = KnowledgeBase.stats(context)
                    results = emptyList()
                    query = ""
                    status = "پایگاه دانش پاک شد."
                }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("پاک کردن پایگاه دانش", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }

            OutlinedButton(
                onClick = {
                    query = "طراحی سایت"
                    results = KnowledgeBase.search(context, query, limit = 3)
                },
                enabled = stats.chunks > 0,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("جستجوی نمونه", fontSize = 12.sp)
            }
        }
    }
}
