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
import com.example.service.FokoAccessibilityService
import com.example.service.FokoFloatingOverlayService
import com.example.ui.theme.PersianTurquoise
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val huggingFace: String = "",
    val xaiGrok: String = "",
    val openRouter: String = "",
    val groq: String = "",
    val gemini: String = "",
    val userEmail: String = "mrfokolaii@gmail.com",
    val accessibilityEnabled: Boolean = false,
    val snapshot: ApiKeySnapshot = ApiKeySnapshot(),
    // 9Router — the self-hosted router that rotates free models for us
    val nineRouterBaseUrl: String = ApiKeyStore.DEFAULT_9ROUTER_BASE_URL,
    val nineRouterKey: String = "",
    val nineRouterModel: String = "",
    val nineRouterEnabled: Boolean = false,
    // Manual provider: any site, its own URL, key and model
    val customLabel: String = "",
    val customBaseUrl: String = "",
    val customKey: String = "",
    val customModel: String = ""
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val keyStore = ApiKeyStore(application)
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadKeys()
    }

    fun loadKeys() {
        _uiState.value = SettingsUiState(
            huggingFace = keyStore.getHuggingFaceKey(),
            xaiGrok = keyStore.getXaiGrokKey(),
            openRouter = keyStore.getOpenRouterKey(),
            groq = keyStore.getGroqKey(),
            gemini = keyStore.getGeminiOverride(),
            userEmail = keyStore.getUserEmail(),
            accessibilityEnabled = keyStore.isAccessibilityEnabled(),
            snapshot = keyStore.snapshot(),
            nineRouterBaseUrl = keyStore.getNineRouterBaseUrl(),
            nineRouterKey = keyStore.getNineRouterKey(),
            nineRouterModel = keyStore.getNineRouterModel(),
            nineRouterEnabled = keyStore.isNineRouterEnabled(),
            customLabel = keyStore.getCustomLabel(),
            customBaseUrl = keyStore.getCustomBaseUrl(),
            customKey = keyStore.getCustomKey(),
            customModel = keyStore.getCustomModel()
        )
    }

    fun saveNineRouter(baseUrl: String, key: String, model: String, enabled: Boolean) {
        viewModelScope.launch {
            keyStore.setNineRouter(baseUrl, key, model)
            keyStore.setNineRouterEnabled(enabled)
            loadKeys()
        }
    }

    fun setNineRouterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            keyStore.setNineRouterEnabled(enabled)
            loadKeys()
        }
    }

    fun saveCustom(label: String, baseUrl: String, key: String, model: String) {
        viewModelScope.launch {
            keyStore.setCustom(label, baseUrl, key, model)
            loadKeys()
        }
    }

    /** Wipes every key except the Google Gemini one. */
    fun clearOtherKeys() {
        viewModelScope.launch {
            keyStore.setHuggingFaceKey("")
            keyStore.setXaiGrokKey("")
            keyStore.setOpenRouterKey("")
            keyStore.setGroqKey("")
            loadKeys()
        }
    }

    fun saveKeys(
        hf: String,
        grok: String,
        or: String,
        groq: String,
        gem: String,
        email: String,
        accessibility: Boolean
    ) {
        viewModelScope.launch {
            keyStore.setHuggingFaceKey(hf)
            keyStore.setXaiGrokKey(grok)
            keyStore.setOpenRouterKey(or)
            keyStore.setGroqKey(groq)
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
    var grokKey by remember(uiState.xaiGrok) { mutableStateOf(uiState.xaiGrok) }
    var orKey by remember(uiState.openRouter) { mutableStateOf(uiState.openRouter) }
    var groqKey by remember(uiState.groq) { mutableStateOf(uiState.groq) }
    var gemKey by remember(uiState.gemini) { mutableStateOf(uiState.gemini) }
    var emailInput by remember(uiState.userEmail) { mutableStateOf(uiState.userEmail) }
    var nineRouterBase by remember(uiState.nineRouterBaseUrl) { mutableStateOf(uiState.nineRouterBaseUrl) }
    var nineRouterKey by remember(uiState.nineRouterKey) { mutableStateOf(uiState.nineRouterKey) }
    var nineRouterModel by remember(uiState.nineRouterModel) { mutableStateOf(uiState.nineRouterModel) }
    var customLabel by remember(uiState.customLabel) { mutableStateOf(uiState.customLabel) }
    var customBase by remember(uiState.customBaseUrl) { mutableStateOf(uiState.customBaseUrl) }
    var customKey by remember(uiState.customKey) { mutableStateOf(uiState.customKey) }
    var customModel by remember(uiState.customModel) { mutableStateOf(uiState.customModel) }
    var accessibilityState by remember(uiState.accessibilityEnabled) { mutableStateOf(uiState.accessibilityEnabled) }
    var showAccessibilityHelpDialog by remember { mutableStateOf(false) }

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

                ProviderStatusRow(
                    title = "هوش مصنوعی رایگان داخلی فوکو",
                    subtitle = "پاسخگویی سریع بدون نیاز به کلید (Pollinations Free)",
                    isActive = true
                )

                ProviderStatusRow(
                    title = "Hugging Face (کیوون Qwen 2.5, DeepSeek R1)",
                    subtitle = if (uiState.snapshot.huggingFaceConfigured) "فعال و آماده گفتگو" else "کلید وارد نشده",
                    isActive = uiState.snapshot.huggingFaceConfigured
                )

                ProviderStatusRow(
                    title = "xAI Grok (گروک رسمی console.x.ai)",
                    subtitle = if (uiState.snapshot.xaiGrokConfigured) "فعال و متصل به xAI" else "کلید وارد نشده",
                    isActive = uiState.snapshot.xaiGrokConfigured
                )

                ProviderStatusRow(
                    title = "OpenRouter (مدل‌های رایگان و متنوع)",
                    subtitle = if (uiState.snapshot.openRouterConfigured) "فعال و آماده ۴ مدل" else "کلید وارد نشده",
                    isActive = uiState.snapshot.openRouterConfigured
                )

                ProviderStatusRow(
                    title = "Groq Cloud (Llama 3.3 سریع)",
                    subtitle = if (uiState.snapshot.groqConfigured) "فعال و آماده" else "کلید وارد نشده",
                    isActive = uiState.snapshot.groqConfigured
                )

                ProviderStatusRow(
                    title = "Google Gemini (جمینای چندرسانه‌ای)",
                    subtitle = if (uiState.snapshot.geminiConfigured) "فعال" else "کلید وارد نشده",
                    isActive = uiState.snapshot.geminiConfigured
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
                                viewModel.saveKeys(hfKey, grokKey, orKey, groqKey, gemKey, emailInput, false)
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
            TextButton(onClick = { viewModel.clearOtherKeys() }) {
                Text("پاک کردن کلیدهای دیگر (Hugging Face / xAI / OpenRouter / Groq)", color = MaterialTheme.colorScheme.error)
            }
        }

        // Save Button
        Button(
            onClick = {
                viewModel.saveKeys(
                    hf = hfKey,
                    grok = grokKey,
                    or = orKey,
                    groq = groqKey,
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
        // 9Router — the GitHub project that rotates free models for us.
        // Point FOKO at a running 9Router server (its OpenAI-compatible API).
        // ---------------------------------------------------------------
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Layers, contentDescription = null, tint = PersianTurquoise)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "9Router — مسیریاب خودکار مدل‌های رایگان",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "9Router (github.com/decolua/9router) یک سرور است که چند سرویس رایگان را پشت یک آدرس جمع می‌کند؛ هر مدل به سقف خورد، خودش مدل بعدی را فعال می‌کند. آن را روی کامپیوتر یا سرور خودت اجرا کن و آدرسش را اینجا بده.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = nineRouterBase,
                    onValueChange = { nineRouterBase = it },
                    label = { Text("آدرس سرور 9Router") },
                    placeholder = { Text(ApiKeyStore.DEFAULT_9ROUTER_BASE_URL) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ApiKeyField(
                    title = "کلید API 9Router (اگر روی سرورت گذاشته‌ای)",
                    hint = "Dashboard → API Keys",
                    value = nineRouterKey,
                    onValueChange = { nineRouterKey = it }
                )
                OutlinedTextField(
                    value = nineRouterModel,
                    onValueChange = { nineRouterModel = it },
                    label = { Text("مدل یا نام Combo (مثلاً free-combo)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("اتصال فعال باشد", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = uiState.nineRouterEnabled,
                        onCheckedChange = { viewModel.setNineRouterEnabled(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = PersianTurquoise)
                    )
                }
                Button(
                    onClick = {
                        viewModel.saveNineRouter(nineRouterBase, nineRouterKey, nineRouterModel, true)
                        Toast.makeText(context, "9Router ذخیره شد و به ربات وصل شد", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PersianTurquoise)
                ) {
                    Text("ذخیره و اتصال به ربات", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ---------------------------------------------------------------
        // Manual provider: any site that gave you a key, with its own
        // base URL and model, typed by hand.
        // ---------------------------------------------------------------
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = PersianTurquoise)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "افزودن سایت دلخواه (کلید دستی)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "اگر سایتی به تو کلید داده، نامش و آدرس API و نام مدلش را دستی اینجا بنویس؛ فوکو با همان کلید به آن سایت وصل می‌شود.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = customLabel,
                    onValueChange = { customLabel = it },
                    label = { Text("نام سایت") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = customBase,
                    onValueChange = { customBase = it },
                    label = { Text("آدرس API (Base URL)") },
                    placeholder = { Text("https://example.com/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ApiKeyField(
                    title = "کلید API",
                    hint = "کلید همان سایت",
                    value = customKey,
                    onValueChange = { customKey = it }
                )
                OutlinedTextField(
                    value = customModel,
                    onValueChange = { customModel = it },
                    label = { Text("نام مدل") },
                    placeholder = { Text("gpt-4o-mini") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Button(
                    onClick = {
                        viewModel.saveCustom(customLabel, customBase, customKey, customModel)
                        Toast.makeText(context, "سایت دلخواه ذخیره و به ربات وصل شد", Toast.LENGTH_SHORT).show()
                    },
                    enabled = customBase.isNotBlank() && customModel.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PersianTurquoise)
                ) {
                    Text("ذخیره و اتصال به ربات", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

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
