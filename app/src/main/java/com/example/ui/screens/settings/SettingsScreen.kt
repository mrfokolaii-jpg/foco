package com.example.ui.screens.settings

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.connectors.ConnectorStore
import com.example.data.local.ApiKeySnapshot
import com.example.data.local.ApiKeyStore
import com.example.data.model.AiProviderKind
import com.example.data.remote.ApiResult
import com.example.data.remote.ProviderDiagnostics
import com.example.service.FokoAccessibilityService
import com.example.service.FokoFloatingOverlayService
import com.example.ui.theme.PersianTurquoise
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class SettingsUiState(
    val huggingFace: String = "",
    val gemini: String = "",
    val userEmail: String = "",
    val accessibilityEnabled: Boolean = false,
    val snapshot: ApiKeySnapshot = ApiKeySnapshot(),
    /** مدل آفلاینی که کاربر اضافه کرده — برای نمایش در وضعیت اتصال. */
    val offlineModelName: String = "",
    val offlineModelPath: String = ""
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val keyStore = ApiKeyStore(application)
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // کلیدهای سرویس‌هایی که دیگر در اپ نیستند (Groq، OpenRouter، xAI، 9Router و
        // کلیدهای دستی) یک‌بار برای همیشه از حافظه پاک می‌شوند.
        runCatching { keyStore.purgeRemovedProviders() }
        loadKeys()
    }

    fun loadKeys() {
        // کلید Hugging Face در صفحهٔ «پلاگین‌ها» ذخیره می‌شود؛ اگر آن‌جا وارد شده باشد
        // این‌جا هم استفاده می‌شود تا وضعیت اتصال «فعال» نشان داده شود و مدل‌های
        // Hugging Face واقعاً کار کنند.
        runCatching {
            val connectors = ConnectorStore(getApplication<Application>())
            val hfFromConnector = connectors.hfToken().trim()
            if (hfFromConnector.isNotBlank() && keyStore.getHuggingFaceKey().isBlank()) {
                keyStore.setHuggingFaceKey(hfFromConnector)
            }
        }

        _uiState.value = SettingsUiState(
            huggingFace = keyStore.getHuggingFaceKey(),
            gemini = keyStore.getGeminiKey(),
            userEmail = keyStore.getUserEmail(),
            accessibilityEnabled = keyStore.isAccessibilityEnabled(),
            snapshot = keyStore.snapshot(),
            offlineModelName = keyStore.getOfflineModelName(),
            offlineModelPath = keyStore.getOfflineModelPath()
        )
    }

    /** کلیدهای پلاگین (گیت‌هاب / هاگینگ‌فیس) را ذخیره می‌کند و فوراً در اختیار ربات می‌گذارد. */
    fun savePluginKeys(
        gitHubToken: String,
        owner: String,
        repo: String,
        branch: String,
        huggingFaceToken: String
    ) {
        viewModelScope.launch {
            val connectors = ConnectorStore(getApplication<Application>())
            connectors.setGitHub(gitHubToken, owner, repo, branch)
            connectors.setHfToken(huggingFaceToken)
            // همان کلید در مخزن کلیدهای هوش مصنوعی هم ذخیره می‌شود تا وضعیت اتصال
            // «فعال» شود و مدل‌های Hugging Face با همین کلید پاسخ دهند.
            if (huggingFaceToken.isNotBlank()) keyStore.setHuggingFaceKey(huggingFaceToken)
            loadKeys()
        }
    }

    fun saveKeys(
        hf: String,
        gem: String,
        email: String,
        accessibility: Boolean
    ) {
        viewModelScope.launch {
            keyStore.setHuggingFaceKey(hf)
            keyStore.setGeminiKey(gem)
            keyStore.setUserEmail(email)
            keyStore.setAccessibilityEnabled(accessibility)
            loadKeys()
        }
    }
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val systemAccessibilityEnabled = FokoAccessibilityService.isEnabledInSystem(context)

    var hfKey by remember(uiState.huggingFace) { mutableStateOf(uiState.huggingFace) }
    var gemKey by remember(uiState.gemini) { mutableStateOf(uiState.gemini) }
    var emailInput by remember(uiState.userEmail) { mutableStateOf(uiState.userEmail) }
    var accessibilityState by remember(uiState.accessibilityEnabled) { mutableStateOf(uiState.accessibilityEnabled) }
    var showAccessibilityHelpDialog by remember { mutableStateOf(false) }

    // کلیدهای پلاگین‌ها (گیت‌هاب و هاگینگ‌فیس) — این‌جا وارد می‌شوند و در صفحه‌ی
    // «ابزارها» فقط روشن/خاموش می‌گردند.
    val connectorStore = remember { ConnectorStore(context.applicationContext) }
    var githubToken by remember { mutableStateOf(connectorStore.githubToken()) }
    var githubOwner by remember { mutableStateOf(connectorStore.githubOwner()) }
    var githubRepo by remember { mutableStateOf(connectorStore.githubRepo()) }
    var githubBranch by remember { mutableStateOf(connectorStore.githubBranch()) }
    var hfToken by remember { mutableStateOf(connectorStore.hfToken()) }

    val scrollState = rememberScrollState()

    if (showAccessibilityHelpDialog) {
        AlertDialog(
            onDismissRequest = { showAccessibilityHelpDialog = false },
            title = {
                Text("راهنمای فعال‌سازی دسترسی‌پذیری", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "در صفحه تنظیمات گوشی شما:\n\n" +
                        "۱. روی گزینه «Downloaded apps» یا «برنامه‌های بارگیری‌شده» بزنید.\n" +
                        "۲. سپس سرویس «FOKO AI» را انتخاب کرده و تیک فعال‌سازی را روشن کنید.",
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAccessibilityHelpDialog = false
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PersianTurquoise)
                ) {
                    Text("رفتن به تنظیمات گوشی", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityHelpDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "تنظیمات، کلیدها و دسترسی‌ها",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "مدیریت هوش مصنوعی و یکپارچه‌سازی سیستم فوکو",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = {
                viewModel.loadKeys()
                Toast.makeText(context, "وضعیت کلیدها بروزرسانی شد", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.Refresh, contentDescription = "بروزرسانی", tint = PersianTurquoise)
            }
        }

        // Active Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "وضعیت اتصال هوش مصنوعی:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PersianTurquoise
                )

                // ---- تست واقعی همهٔ سرویس‌ها ----------------------------------
                // «کلید گذاشتم ولی کار نمی‌کند» با حدس حل نمی‌شود؛ این دکمه به هر
                // سرویس وصل می‌شود و پیام خودِ سرور را نشان می‌دهد.
                val scope = rememberCoroutineScope()
                var testing by remember { mutableStateOf(false) }
                var results by remember { mutableStateOf<List<ProviderCheck>>(emptyList()) }

                Button(
                    onClick = {
                        testing = true
                        results = emptyList()
                        scope.launch {
                            val store = ApiKeyStore(context)
                            val targets = buildList {
                                if (uiState.gemini.isNotBlank()) add(
                                    ProviderCheck("Google Gemini", AiProviderKind.GEMINI)
                                )
                                if (uiState.huggingFace.isNotBlank()) add(
                                    ProviderCheck("Hugging Face", AiProviderKind.HUGGINGFACE)
                                )
                                if (uiState.offlineModelPath.isNotBlank()) add(
                                    ProviderCheck(
                                        label = "مدل آفلاین",
                                        provider = AiProviderKind.OFFLINE,
                                        model = uiState.offlineModelPath
                                    )
                                )
                            }

                            val out = mutableListOf<ProviderCheck>()
                            targets.forEach { target ->
                                val outcome = try {
                                    ProviderDiagnostics.check(
                                        target.provider,
                                        target.key.ifBlank { store.keyFor(target.provider) },
                                        target.baseUrl,
                                        target.model
                                    )
                                } catch (error: Exception) {
                                    ApiResult.Error(error.localizedMessage ?: "خطای نامشخص")
                                }
                                out.add(
                                    target.copy(
                                        result = when (outcome) {
                                            is ApiResult.Success -> "✓ " + outcome.data
                                            is ApiResult.Error -> "✗ " + outcome.message
                                        },
                                        ok = outcome is ApiResult.Success
                                    )
                                )
                            }
                            results = out
                            testing = false
                        }
                    },
                    enabled = !testing,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PersianTurquoise)
                ) {
                    if (testing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.Black
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (testing) "در حال تست همهٔ سرویس‌ها..." else "تست همهٔ سرویس‌ها",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }

                results.forEach { check ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = check.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (check.ok) PersianTurquoise else MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = check.result,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // اگر هیچ‌کدام وصل نباشد صریح گفته می‌شود (هیچ هوش مصنوعی داخلی وجود ندارد).
                if (!uiState.snapshot.hasAny()) {
                    Text(
                        text = "هیچ هوش مصنوعی‌ای وصل نیست. کلید جمینای را وارد کن یا یک مدل آفلاین اضافه کن.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                ProviderStatusRow(
                    title = "Google Gemini (جمینای گوگل)",
                    subtitle = if (uiState.snapshot.geminiConfigured) "کلید ذخیره شد — فهرست مدل‌های همین کلید خوانده می‌شود" else "کلید وارد نشده",
                    isActive = uiState.snapshot.geminiConfigured
                )

                ProviderStatusRow(
                    title = "Hugging Face (روتر استنتاج)",
                    subtitle = if (uiState.snapshot.huggingFaceConfigured) "کلید ذخیره شد — فهرست مدل‌های همین کلید خوانده می‌شود" else "کلید وارد نشده",
                    isActive = uiState.snapshot.huggingFaceConfigured
                )

                // مدل آفلاین روی خود گوشی: نه اینترنت می‌خواهد، نه کلید.
                ProviderStatusRow(
                    title = "هوش مصنوعی آفلاین (روی خود گوشی)",
                    subtitle = when {
                        uiState.offlineModelPath.isBlank() -> "مدلی اضافه نشده"
                        File(uiState.offlineModelPath).exists() ->
                            "آماده — ${uiState.offlineModelName}"
                        else -> "فایل مدل پیدا نمی‌شود: ${uiState.offlineModelPath}"
                    },
                    isActive = uiState.snapshot.offlineConfigured
                )
            }
        }

        // Accessibility Service Switch Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "سرویس دسترسی‌پذیری و کنترل دستگاه (Accessibility)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "برای لمس خودکار، خواندن صفحه و اجرای فرامین صوتی گوشی",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = systemAccessibilityEnabled,
                        onCheckedChange = { newState ->
                            accessibilityState = newState
                            if (newState) {
                                showAccessibilityHelpDialog = true
                            } else {
                                viewModel.saveKeys(hfKey, gemKey, emailInput, false)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PersianTurquoise
                        )
                    )
                }
            }
        }

        // Floating Overlay Widget Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ویجت شناور خارج از برنامه (Floating Overlay)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "دسترسی صوتی و تعاملی سریع هنگام کار با سایر برنامه‌ها",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val canDraw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                context.startActivity(intent)
                            } else {
                                FokoFloatingOverlayService.start(context)
                                Toast.makeText(context, "ویجت شناور روی صفحه فعال شد", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (canDraw) PersianTurquoise else MaterialTheme.colorScheme.secondary)
                    ) {
                        Text(if (canDraw) "روشن کردن شناور" else "درخواست مجوز", fontSize = 12.sp, color = if (canDraw) Color.Black else Color.White)
                    }
                }
            }
        }

        // API Keys Inputs
        Text(
            text = "ورود کلیدهای اختصاصی شما:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Google Gemini — the only free key FOKO needs
        ApiKeyField(
            title = "کلید Google Gemini (رایگان)",
            hint = "aistudio.google.com",
            value = gemKey,
            onValueChange = { gemKey = it }
        )
        Text(
            text = "با همین کلید، مدل‌های Gemini در پنجره انتخاب مدل فعال می‌شوند.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (uiState.snapshot.huggingFaceConfigured || uiState.snapshot.xaiGrokConfigured ||
            uiState.snapshot.openRouterConfigured || uiState.snapshot.groqConfigured
        ) {
        }

        // Save Button
        Button(
            onClick = {
                viewModel.saveKeys(
                    hf = hfKey,
                    gem = gemKey,
                    email = emailInput,
                    accessibility = systemAccessibilityEnabled
                )
                Toast.makeText(context, "تمام کلیدها و دسترسی‌ها با موفقیت ذخیره شدند", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("ذخیره و فعال‌سازی کلیدها", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        // ---------------------------------------------------------------
        // کلید پلاگین‌ها — کلید گیت‌هاب و هاگینگ‌فیس این‌جا وارد می‌شود.
        // روشن/خاموش کردن پلاگین در صفحه‌ی «ابزارها» انجام می‌گیرد.
        // ---------------------------------------------------------------
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Hub, contentDescription = null, tint = PersianTurquoise)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "کلید پلاگین‌ها — گیت‌هاب و هاگینگ‌فیس",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "کلیدها را همین‌جا وارد کن و به ربات وصل کن. روشن و خاموش کردن پلاگین در صفحه‌ی «ابزارها» انجام می‌شود.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ApiKeyField(
                    title = "توکن گیت‌هاب (ghp_... یا github_pat_...)",
                    hint = "دسترسی repo و workflow",
                    value = githubToken,
                    onValueChange = { githubToken = it }
                )
                OutlinedTextField(
                    value = githubOwner,
                    onValueChange = { githubOwner = it },
                    label = { Text("مالک مخزن (owner)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = githubRepo,
                    onValueChange = { githubRepo = it },
                    label = { Text("نام مخزن (repo)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = githubBranch,
                    onValueChange = { githubBranch = it },
                    label = { Text("شاخه (branch)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ApiKeyField(
                    title = "توکن Hugging Face (hf_...)",
                    hint = "huggingface.co → Settings → Access Tokens",
                    value = hfToken,
                    onValueChange = { hfToken = it }
                )
                Button(
                    onClick = {
                        viewModel.savePluginKeys(
                            gitHubToken = githubToken,
                            owner = githubOwner,
                            repo = githubRepo,
                            branch = githubBranch,
                            huggingFaceToken = hfToken
                        )
                        Toast.makeText(context, "کلیدها ذخیره و به ربات وصل شدند", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PersianTurquoise)
                ) {
                    Text("ذخیره کلیدها و اتصال به ربات", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ---------------------------------------------------------------
        // Hand-wired providers: as many sites as you like, each with its
        // own base URL, key and model. All of them join the rotation.
        // ---------------------------------------------------------------
        // ---------------------------------------------------------------
        // هوش مصنوعی آفلاین: مدل GGUF از ZIP یا از حافظهٔ خود گوشی خوانده می‌شود
        // و بدون اینترنت و بدون کلید کار می‌کند.
        // ---------------------------------------------------------------
        OfflineModelCard(onChanged = { viewModel.loadKeys() })

        // Where FOKO is allowed to write: models, downloads and knowledge files.
        StorageSettingsCard(context = context)

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** یک سرویس برای «تست اتصال»، به‌همراه نتیجه‌ای که از سرور برگشته است. */
private data class ProviderCheck(
    val label: String,
    val provider: AiProviderKind,
    val baseUrl: String = "",
    val model: String = "",
    /** کلید مخصوص همین سرویس؛ خالی باشد از مخزن کلیدها خوانده می‌شود. */
    val key: String = "",
    val result: String = "",
    val ok: Boolean = false
)

@Composable
fun ProviderStatusRow(title: String, subtitle: String, isActive: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Close,
            contentDescription = null,
            tint = if (isActive) PersianTurquoise else MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun AccountBadge(name: String, status: String, isConnected: Boolean, modifier: Modifier = Modifier) {
    Surface(
        color = PersianTurquoise.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = PersianTurquoise, maxLines = 1)
            Text(
                status,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = if (isConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ApiKeyField(
    title: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(text = "دریافت از: $hint", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PersianTurquoise
                )
            )
        }
    }
}
