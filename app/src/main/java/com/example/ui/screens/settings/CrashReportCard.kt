package com.example.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CrashReporter
import com.example.ui.theme.PersianTurquoise

/**
 * گزارش خطا.
 *
 * هدفش این است که دیگر لازم نباشد کاربر از صفحهٔ خطای اندروید عکس مبهم بگیرد:
 * متن کامل خطا این‌جا خوانده و با یک دکمه کپی می‌شود.
 */
@Composable
fun CrashReportCard() {
    val context = LocalContext.current
    var report by remember { mutableStateOf(CrashReporter.lastReport(context)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Build, contentDescription = null, tint = PersianTurquoise)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "گزارش خطا",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            if (report.isBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "هیچ خطایی ثبت نشده است.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "اگر اپ بسته شد یا پیامی فرستاده نشد، متن زیر را کپی کن و بفرست — " +
                        "دقیقاً می‌گوید چه چیزی خراب شده است.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = report.take(4000),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Start
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        copyToClipboard(context, report)
                        Toast.makeText(context, "گزارش کپی شد", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("کپی گزارش", color = PersianTurquoise, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = {
                        CrashReporter.clear(context)
                        report = ""
                    }) {
                        Text("پاک کردن", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("گزارش خطای فوکو", text))
    }
}
