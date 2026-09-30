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
    val snapshot: ApiKeySnapshot = ApiKeySnapshot()
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
            snapshot = keyStore.snapshot()
        )
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

        // Google / GitHub Account Integration Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = PersianTurquoise)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "حساب گوگل و یکپارچه‌سازی دسترسی‌ها",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text("جیمیل متصل به کلیدها (Gmail)") },
                    trailingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = PersianTurquoise) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                val connectorSnapshot = ConnectorStore(context).snapshot()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AccountBadge("GitHub", if (connectorSnapshot.githubConfigured) "متصل" else "قطع", connectorSnapshot.githubConfigured, modifier = Modifier.weight(1f))
                    AccountBadge("HuggingFace", if (connectorSnapshot.huggingFaceConfigured) "متصل" else "قطع", connectorSnapshot.huggingFaceConfigured, modifier = Modifier.weight(1f))
                    AccountBadge("Google Drive", if (connectorSnapshot.driveConfigured) "متصل" else "قطع", connectorSnapshot.driveConfigured, modifier = Modifier.weight(1f))
                    AccountBadge("Gmail", "خارجی", false, modifier = Modifier.weight(1f))
                }
                Text(
                    text = "برای اتصال واقعی ابزارها به تب «ابزارها» بروید.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

        // Hugging Face
        ApiKeyField(
            title = "توکن Hugging Face (کیوون Qwen 2.5, DeepSeek)",
            hint = "huggingface.co/settings/tokens",
            value = hfKey,
            onValueChange = { hfKey = it }
        )

        // xAI Grok
        ApiKeyField(
            title = "کلید xAI Grok (گروک رسمی console.x.ai)",
            hint = "console.x.ai/team/... -> API Keys",
            value = grokKey,
            onValueChange = { grokKey = it }
        )

        // OpenRouter
        ApiKeyField(
            title = "کلید OpenRouter (مدل‌های رایگان و باز)",
            hint = "openrouter.ai/keys",
            value = orKey,
            onValueChange = { orKey = it }
        )

        // Groq
        ApiKeyField(
            title = "کلید Groq (Llama 3.3 سریع)",
            hint = "console.groq.com",
            value = groqKey,
            onValueChange = { groqKey = it }
        )

        // Google Gemini
        ApiKeyField(
            title = "کلید Google Gemini (اختیاری)",
            hint = "aistudio.google.com",
            value = gemKey,
            onValueChange = { gemKey = it }
        )

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
