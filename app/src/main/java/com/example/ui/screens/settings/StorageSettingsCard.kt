package com.example.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.FokoStorage
import com.example.ui.theme.PersianTurquoise

/**
 * Where FOKO is allowed to write. The user picks any folder on the phone and FOKO remembers it;
 * offline models, downloads and knowledge files all land there.
 */
@Composable
fun StorageSettingsCard(context: Context) {

    val storage = remember { FokoStorage(context.applicationContext) }
    var locationLabel by remember { mutableStateOf(storage.locationLabel()) }
    var usedLabel by remember { mutableStateOf(FokoStorage.formatSize(storage.totalUsedBytes())) }
    var fileCount by remember { mutableStateOf(storage.listFiles().size) }
    var maxDownload by remember { mutableStateOf(storage.maxDownloadBytes) }

    fun refresh() {
        locationLabel = storage.locationLabel()
        usedLabel = FokoStorage.formatSize(storage.totalUsedBytes())
        fileCount = storage.listFiles().size
        maxDownload = storage.maxDownloadBytes
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Some providers refuse persistable grants; the folder still works this session.
            }
            storage.rememberPickedFolder(uri)
            refresh()
            Toast.makeText(context, "محل ذخیره ثبت شد", Toast.LENGTH_SHORT).show()
        }
    }

    val sizeOptions = listOf(
        10L * 1024 * 1024 to "۱۰ مگ",
        50L * 1024 * 1024 to "۵۰ مگ",
        200L * 1024 * 1024 to "۲۰۰ مگ",
        1024L * 1024 * 1024 to "۱ گیگ"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = PersianTurquoise)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "محل ذخیرهٔ فایل‌ها",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "مدل‌های آفلاین، فایل‌های دانلودی و دانشی که ذخیره می‌شود همه در این پوشه می‌روند. " +
                    "خودت تعیین می‌کنی کجا باشد.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = locationLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = PersianTurquoise
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { folderPicker.launch(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = PersianTurquoise),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("انتخاب پوشه", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                if (storage.hasPickedFolder()) {
                    OutlinedButton(
                        onClick = {
                            storage.clearPickedFolder()
                            refresh()
                            Toast.makeText(context, "برگشت به حافظهٔ خود برنامه", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("پیش‌فرض")
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "فضای استفاده‌شده: $usedLabel  •  تعداد فایل: $fileCount",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "حداکثر حجم هر دانلود: ${FokoStorage.formatSize(maxDownload)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                sizeOptions.forEach { (bytes, label) ->
                    val selected = maxDownload == bytes
                    OutlinedButton(
                        onClick = {
                            storage.maxDownloadBytes = bytes
                            refresh()
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = label,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) PersianTurquoise else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    val removed = storage.clearAll()
                    refresh()
                    Toast.makeText(context, "$removed فایل پاک شد", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("پاک کردن فایل‌های فوکو")
            }
        }
    }
}
