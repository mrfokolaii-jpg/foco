package com.example.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.documentfile.provider.DocumentFile
import com.example.data.local.ApiKeyStore
import com.example.data.offline.OfflineEngine
import com.example.data.offline.OfflineModelImporter
import com.example.data.remote.ApiResult
import com.example.ui.theme.PersianTurquoise
import kotlinx.coroutines.launch
import java.io.File

/**
 * کارت «هوش مصنوعی آفلاین».
 *
 * سه راه برای آوردن مدل، چون هر کاربری فایل را یک‌جور روی گوشی دارد:
 *   ۱) فایل ZIP → خودمان GGUF را از داخلش بیرون می‌کشیم و کپی می‌کنیم.
 *   ۲) فایل GGUF مستقیم → کپی می‌شود.
 *   ۳) مدلی که از قبل روی حافظه هست → بدون کپی، همان‌جا استفاده می‌شود
 *      (برای مدل چند گیگابایتی، دوباره‌نویسی فایل وقت و فضا تلف می‌کند).
 */
@Composable
fun OfflineModelCard(onChanged: () -> Unit) {
    val context = LocalContext.current
    // موتور باید تنظیمات سرعت و پایگاه دانش را بشناسد، حتی اگر کاربر از این صفحه
    // وارد شود (مسیر چت هم جداگانه attach می‌کند).
    remember(context) { OfflineEngine.attach(context.applicationContext); true }
    val store = remember { ApiKeyStore(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var modelPath by remember { mutableStateOf(store.getOfflineModelPath()) }
    var modelName by remember { mutableStateOf(store.getOfflineModelName()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(-1) }
    var scanned by remember { mutableStateOf<List<File>>(emptyList()) }
    var testInput by remember { mutableStateOf("سلام، خودت را در یک جمله معرفی کن.") }
    var testOutput by remember { mutableStateOf("") }

    val deviceOk = remember { OfflineEngine.isDeviceSupported() }
    val currentFile = remember(modelPath) { if (modelPath.isBlank()) null else File(modelPath) }

    fun adopt(path: String, name: String) {
        store.setOfflineModel(path, name)
        modelPath = path
        modelName = name
        onChanged()
    }

    // ---- انتخاب فایل (ZIP یا GGUF) ----
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            progress = 0
            status = "در حال خواندن فایل..."
            when (val result = OfflineModelImporter.importFromUri(context, uri) { progress = it }) {
                is ApiResult.Success -> {
                    adopt(result.data.absolutePath, result.data.name)
                    status = "✓ مدل اضافه شد: ${result.data.name} (${OfflineModelImporter.readableSize(result.data.length())})"
                }
                is ApiResult.Error -> status = "✗ ${result.message}"
            }
            progress = -1
            busy = false
        }
    }

    // مدل‌هایی که داخل پوشهٔ انتخابی پیدا شدند (باید کپی شوند، چون Uri هستند).
    var folderChoices by remember { mutableStateOf<List<Pair<String, Uri>>>(emptyList()) }

    // ---- انتخاب پوشه و پیدا کردن مدل داخلش ----
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            status = "در حال گشتن داخل پوشه..."
            val root = DocumentFile.fromTreeUri(context, uri)
            val found = root?.let { findGgufs(it, 0) }.orEmpty()
            when {
                found.isEmpty() -> status = "✗ داخل این پوشه هیچ فایل .gguf پیدا نشد."
                found.size == 1 -> {
                    val doc = found.first()
                    val name = doc.name ?: "model.gguf"
                    status = "پیدا شد: $name — در حال کپی..."
                    when (val result = OfflineModelImporter.importFromUri(context, doc.uri) { progress = it }) {
                        is ApiResult.Success -> {
                            adopt(result.data.absolutePath, result.data.name)
                            status = "✓ مدل اضافه شد: ${result.data.name}"
                        }
                        is ApiResult.Error -> status = "✗ ${result.message}"
                    }
                }
                else -> {
                    // چند مدل پیدا شد؛ همه را نشان می‌دهیم تا کاربر یکی را انتخاب کند.
                    status = "${found.size} فایل GGUF پیدا شد — روی هرکدام بزن تا اضافه شود:"
                    scanned = emptyList()
                    folderChoices = found.map { Pair(it.name ?: "model.gguf", it.uri) }
                }
            }
            progress = -1
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
                Icon(Icons.Default.Storage, contentDescription = null, tint = PersianTurquoise)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "هوش مصنوعی آفلاین (روی خودِ گوشی)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "یک فایل مدل GGUF (مثل DeepSeek یا Qwen) بده تا روی خود گوشی و بدون اینترنت کار کند.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!deviceOk) {
                WarningLine("این گوشی arm64 نیست؛ اجرای مدل روی خودِ گوشی پشتیبانی نمی‌شود.")
            }

            // ---- وضعیت مدل فعلی ----
            if (currentFile != null && currentFile.exists()) {
                Text(
                    text = "مدل فعلی: $modelName",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${OfflineModelImporter.readableSize(currentFile.length())}\n${currentFile.absolutePath}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OfflineModelImporter.sizeWarning(currentFile.length())?.let { WarningLine(it) }
                if (OfflineEngine.isLoaded() && OfflineEngine.loadedModelPath() == modelPath) {
                    Text(
                        text = "✓ روی گوشی بارگذاری شده و آماده پاسخ است",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                }
            } else {
                Text(
                    text = "هنوز مدلی اضافه نشده است.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---- راه ۱: فایل ZIP یا GGUF ----
            Button(
                onClick = { filePicker.launch(arrayOf("*/*")) },
                enabled = !busy && deviceOk,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PersianTurquoise)
            ) {
                Text("۱) انتخاب فایل ZIP یا GGUF", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            // ---- راه ۲: پوشه ----
            OutlinedButton(
                onClick = { folderPicker.launch(null) },
                enabled = !busy && deviceOk,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("۲) انتخاب پوشه (مدل را خودش پیدا می‌کند)")
            }

            // ---- راه ۳: مدل‌هایی که از قبل روی حافظه هستند ----
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !OfflineModelImporter.hasAllFilesAccess()) {
                OutlinedButton(
                    onClick = {
                        runCatching {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                .setData(Uri.parse("package:${context.packageName}"))
                            context.startActivity(intent)
                        }.onFailure {
                            runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                        }
                        Toast.makeText(
                            context,
                            "گزینهٔ «دسترسی به همهٔ فایل‌ها» را برای فوکو روشن کن و برگرد",
                            Toast.LENGTH_LONG
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("۳) دادن دسترسی به حافظه (برای خواندن مدل‌های موجود)")
                }
            }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        busy = true
                        status = "در حال گشتن در حافظه..."
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                            !Environment.isExternalStorageManager()
                        ) {
                            status = "✗ اول دسترسی «همهٔ فایل‌ها» را از دکمهٔ بالا روشن کن."
                        } else {
                            val files = OfflineModelImporter.scanForModels(context)
                            scanned = files
                            status = if (files.isEmpty()) {
                                "✗ هیچ فایل .gguf روی حافظه پیدا نشد. اگر مدل داخل ZIP است، از راه ۱ استفاده کن."
                            } else {
                                "${files.size} فایل GGUF پیدا شد — روی هرکدام بزن:"
                            }
                        }
                        busy = false
                    }
                },
                enabled = !busy && deviceOk,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("اسکن حافظه و پیدا کردن مدل")
            }

            // مدل‌های پیدا‌شده روی حافظه — بدون کپی، همان‌جا استفاده می‌شوند.
            scanned.forEach { file ->
                TextButton(
                    onClick = {
                        adopt(file.absolutePath, file.name)
                        status = "✓ انتخاب شد: ${file.name} (${OfflineModelImporter.readableSize(file.length())})"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${file.name}  (${OfflineModelImporter.readableSize(file.length())})",
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }

            // مدل‌های داخل پوشه (کپی می‌شوند)
            folderChoices.forEach { (name, uri) ->
                TextButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            status = "در حال کپی $name ..."
                            when (val result = OfflineModelImporter.importFromUri(context, uri) { progress = it }) {
                                is ApiResult.Success -> {
                                    adopt(result.data.absolutePath, result.data.name)
                                    status = "✓ مدل اضافه شد: ${result.data.name}"
                                    folderChoices = emptyList()
                                }
                                is ApiResult.Error -> status = "✗ ${result.message}"
                            }
                            progress = -1
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(name, fontSize = 12.sp, maxLines = 1)
                }
            }

            if (progress in 0..100) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = PersianTurquoise
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("$progress٪", style = MaterialTheme.typography.labelSmall)
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

            // ---- فعال‌سازی روی گوشی ----
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            status = "در حال بارگذاری مدل روی گوشی... (ممکن است چند دقیقه طول بکشد)"
                            when (val result = OfflineEngine.loadModel(modelPath)) {
                                is ApiResult.Success -> status = "✓ ${result.data}"
                                is ApiResult.Error -> status = "✗ ${result.message}"
                            }
                            busy = false
                        }
                    },
                    enabled = !busy && deviceOk && currentFile?.exists() == true,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PersianTurquoise)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("فعال‌سازی روی گوشی", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = {
                        OfflineEngine.release()
                        status = "حافظهٔ مدل آزاد شد."
                    },
                    enabled = !busy,
                    modifier = Modifier.height(46.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("آزاد کردن", fontSize = 12.sp)
                }
            }

            // ---- حذف مدل ----
            if (modelPath.isNotBlank()) {
                TextButton(onClick = {
                    OfflineEngine.release()
                    store.clearOfflineModel()
                    modelPath = ""
                    modelName = ""
                    status = "مدل از تنظیمات حذف شد."
                    onChanged()
                }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("حذف مدل از تنظیمات", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }

            // ---- تست واقعی و آفلاین ----
            Text(
                text = "تست آفلاین (این پیام واقعاً روی گوشی پردازش می‌شود):",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = testInput,
                onValueChange = { testInput = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                maxLines = 2
            )
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        testOutput = ""
                        status = "در حال فکر کردن روی گوشی... (بدون اینترنت)"
                        val history = listOf(
                            com.example.data.model.ChatMessage(
                                sender = com.example.data.model.MessageSender.USER,
                                text = testInput
                            )
                        )
                        when (
                            val result = OfflineEngine.chat(
                                modelPath = modelPath,
                                conversationHistory = history,
                                systemInstructionText = "تو یک دستیار فارسی‌زبان هستی. کوتاه و مفید جواب بده."
                            )
                        ) {
                            is ApiResult.Success -> {
                                testOutput = result.data.replyText
                                status = "✓ پاسخ آمد (${result.data.modelUsed})"
                            }
                            is ApiResult.Error -> status = "✗ ${result.message}"
                        }
                        busy = false
                    }
                },
                enabled = !busy && deviceOk && currentFile?.exists() == true,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("ارسال پیام تست به مدل آفلاین", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            if (testOutput.isNotBlank()) {
                Text(
                    text = testOutput,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (status.isNotBlank() && status.startsWith("✓")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "وضعیت: آماده",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                }
            }
        }
    }
}

@Composable
private fun WarningLine(message: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

/** همهٔ فایل‌های `.gguf` داخل یک پوشه (تا دو سطح عمق). */
private fun findGgufs(dir: DocumentFile, depth: Int): List<DocumentFile> {
    if (depth > 2) return emptyList()
    val out = mutableListOf<DocumentFile>()
    dir.listFiles().forEach { child ->
        if (child.isDirectory) {
            out += findGgufs(child, depth + 1)
        } else if ((child.name ?: "").lowercase().endsWith(".gguf")) {
            out += child
        }
    }
    return out
}
