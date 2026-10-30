package com.example.ui.screens.settings

import android.app.Application
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.connectors.ConnectorStore
import com.example.data.local.ApiKeySnapshot
import com.example.data.local.ApiKeyStore
import com.example.service.FokoAccessibilityService
import com.example.ui.theme.PersianTurquoise
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val apiKey: String = "",
    val baseUrl: String = ApiKeyStore.DEFAULT_BASE_URL,
    val modelName: String = ApiKeyStore.DEFAULT_MODEL,
    val apiKey2: String = "",
    val baseUrl2: String = ApiKeyStore.DEFAULT_BASE_URL2,
    val modelName2: String = ApiKeyStore.DEFAULT_MODEL2,
    val userEmail: String = "mrfokolaii@gmail.com",
    val accessibilityEnabled: Boolean = false,
    val snapshot: ApiKeySnapshot = ApiKeySnapshot()
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val keyStore = ApiKeyStore(application)
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init { loadKeys() }

    fun loadKeys() {
        _uiState.value = SettingsUiState(
            apiKey = keyStore.getHuggingFaceKey(),
            baseUrl = keyStore.getBaseUrl(),
            modelName = keyStore.getModelName(),
            apiKey2 = keyStore.getApiKey2(),
            baseUrl2 = keyStore.getBaseUrl2(),
            modelName2 = keyStore.getModelName2(),
            userEmail = keyStore.getUserEmail(),
            accessibilityEnabled = keyStore.isAccessibilityEnabled(),
            snapshot = keyStore.snapshot()
        )
    }

    fun saveAll(
        apiKey: String,
        baseUrl: String,
        modelName: String,
        apiKey2: String,
        baseUrl2: String,
        modelName2: String,
        email: String,
        accessibility: Boolean
    ) {
        viewModelScope.launch {
            keyStore.setHuggingFaceKey(apiKey)
            keyStore.setBaseUrl(baseUrl)
            keyStore.setModelName(modelName)
            keyStore.setApiKey2(apiKey2)
            keyStore.setBaseUrl2(baseUrl2)
            keyStore.setModelName2(modelName2)
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

    var apiKey by remember(uiState.apiKey) { mutableStateOf(uiState.apiKey) }
    var baseUrl by remember(uiState.baseUrl) { mutableStateOf(uiState.baseUrl) }
    var modelName by remember(uiState.modelName) { mutableStateOf(uiState.modelName) }
    var apiKey2 by remember(uiState.apiKey2) { mutableStateOf(uiState.apiKey2) }
    var baseUrl2 by remember(uiState.baseUrl2) { mutableStateOf(uiState.baseUrl2) }
    var modelName2 by remember(uiState.modelName2) { mutableStateOf(uiState.modelName2) }
    var emailInput by remember(uiState.userEmail) { mutableStateOf(uiState.userEmail) }
    var accessibilityState by remember(uiState.accessibilityEnabled) { mutableStateOf(uiState.accessibilityEnabled) }
    var showAccessibilityHelpDialog by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }
    var showApiKey2 by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    fun saveEverything(accessibility: Boolean = accessibilityState) {
        viewModel.saveAll(apiKey, baseUrl, modelName, apiKey2, baseUrl2, modelName2, emailInput, accessibility)
        Toast.makeText(context, "تنظیمات ذخیره شد", Toast.LENGTH_SHORT).show()
    }

    if (showAccessibilityHelpDialog) {
        AlertDialog(
            onDismissRequest = { showAccessibilityHelpDialog = false },
            title = { Text("راهنمای فعال‌سازی دسترسی‌پذیری", fontWeight = FontWeight.Bold) },
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
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PersianTurquoise)
                ) { Text("رفتن به تنظیمات گوشی", color = Color.Black, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityHelpDialog = false }) { Text("انصراف") }
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "تنظیمات، کلیدها و دسترسی‌ها",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
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

        // وضعیت مدل‌های فعال
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "مدل‌های فعال بر اساس کلیدها:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PersianTurquoise
                )
                if (uiState.snapshot.huggingFaceConfigured) {
                    ProviderStatusRow(
                        title = uiState.snapshot.modelName,
                        subtitle = "کلید ۱ · ${uiState.snapshot.baseUrl}",
                        isActive = true
                    )
                }
                if (uiState.snapshot.apiKey2Configured) {
                    ProviderStatusRow(
                        title = uiState.snapshot.modelName2,
                        subtitle = "کلید ۲ · ${uiState.snapshot.baseUrl2}",
                        isActive = true
                    )
                }
                if (!uiState.snapshot.huggingFaceConfigured && !uiState.snapshot.apiKey2Configured) {
                    Text(
                        text = "هیچ کلیدی ثبت نشده. با وارد کردن کلید، مدل مربوطه اینجا نمایش داده می‌شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // کلید ۱
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("کلید API ۱ (اصلی)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("کلید API") },
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Text(if (showApiKey) "مخفی" else "نمایش", fontSize = 12.sp)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("لینک سایت / Base URL") },
                    singleLine = true,
                    placeholder = { Text(ApiKeyStore.DEFAULT_BASE_URL) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("مدل (Model)") },
                    singleLine = true,
                    placeholder = { Text(ApiKeyStore.DEFAULT_MODEL) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                if (apiKey.isNotBlank()) {
                    Text(
                        text = "با این کلید فعال می‌شود: $modelName",
                        style = MaterialTheme.typography.labelMedium,
                        color = PersianTurquoise
                    )
                }
                Button(
                    onClick = { saveEverything() },
                    colors = ButtonDefaults.buttonColors(containerColor = PersianTurquoise),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ذخیره کلید ۱", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        // کلید ۲
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("کلید API ۲ (اضافی)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = apiKey2,
                    onValueChange = { apiKey2 = it },
                    label = { Text("کلید API") },
                    singleLine = true,
                    visualTransformation = if (showApiKey2) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey2 = !showApiKey2 }) {
                            Text(if (showApiKey2) "مخفی" else "نمایش", fontSize = 12.sp)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = baseUrl2,
                    onValueChange = { baseUrl2 = it },
                    label = { Text("لینک سایت / Base URL") },
                    singleLine = true,
                    placeholder = { Text(ApiKeyStore.DEFAULT_BASE_URL2) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = modelName2,
                    onValueChange = { modelName2 = it },
                    label = { Text("مدل (Model)") },
                    singleLine = true,
                    placeholder = { Text(ApiKeyStore.DEFAULT_MODEL2) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                if (apiKey2.isNotBlank()) {
                    Text(
                        text = "با این کلید فعال می‌شود: $modelName2",
                        style = MaterialTheme.typography.labelMedium,
                        color = PersianTurquoise
                    )
                }
                Button(
                    onClick = { saveEverything() },
                    colors = ButtonDefaults.buttonColors(containerColor = PersianTurquoise),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ذخیره کلید ۲", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        // حساب
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = PersianTurquoise)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("حساب و اتصالات", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text("جیمیل (اختیاری)") },
                    trailingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = PersianTurquoise) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                val connectorSnapshot = ConnectorStore(context).snapshot()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccountBadge("GitHub", if (connectorSnapshot.githubConfigured) "متصل" else "قطع", connectorSnapshot.githubConfigured, Modifier.weight(1f))
                    AccountBadge("HuggingFace", if (connectorSnapshot.huggingFaceConfigured) "متصل" else "قطع", connectorSnapshot.huggingFaceConfigured, Modifier.weight(1f))
                }
            }
        }

        // Accessibility
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
                        Text("سرویس دسترسی‌پذیری (Accessibility)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("لمس خودکار، خواندن صفحه و فرامین صوتی", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = systemAccessibilityEnabled,
                        onCheckedChange = { newState ->
                            accessibilityState = newState
                            if (newState) showAccessibilityHelpDialog = true
                            else saveEverything(false)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PersianTurquoise)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ProviderStatusRow(title: String, subtitle: String, isActive: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Close,
            contentDescription = null,
            tint = if (isActive) PersianTurquoise else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AccountBadge(title: String, status: String, active: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (active) PersianTurquoise.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(text = status, fontSize = 10.sp, color = if (active) PersianTurquoise else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
