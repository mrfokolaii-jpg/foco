package com.example.ui.screens.connectors

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.service.FokoFloatingOverlayService
import com.example.ui.theme.PersianTurquoise

@Composable
fun ConnectorsScreen(
    modifier: Modifier = Modifier,
    viewModel: ConnectorsViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val scroll = rememberScrollState()

    // وقتی کد دستگاه آماده شد، صفحه تأیید را باز کن
    LaunchedEffect(state.deviceVerificationUri, state.deviceUserCode) {
        val uri = state.deviceVerificationUri
        if (!uri.isNullOrBlank() && !state.deviceUserCode.isNullOrBlank()) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
            } catch (_: Exception) { }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "اتصال به ابزارها",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // Overlay
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(Icons.Default.AutoAwesome, "ویجت شناور")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("مکالمه پیوسته صوتی", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = state.continuousVoice,
                        onCheckedChange = { viewModel.setContinuousVoice(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = PersianTurquoise)
                    )
                }
                val canDraw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
                Button(
                    onClick = {
                        if (!canDraw) {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                    .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                            )
                        } else if (state.overlayRunning) {
                            FokoFloatingOverlayService.stop(context)
                            viewModel.setOverlayRunning(false)
                        } else {
                            FokoFloatingOverlayService.start(context)
                            viewModel.setOverlayRunning(true)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.overlayRunning) MaterialTheme.colorScheme.error else PersianTurquoise
                    )
                ) {
                    Text(
                        when {
                            !canDraw -> "اعطای مجوز نمایش روی برنامه‌ها"
                            state.overlayRunning -> "خاموش کردن ویجت شناور"
                            else -> "روشن کردن ویجت شناور"
                        },
                        fontWeight = FontWeight.Bold,
                        color = if (state.overlayRunning) Color.White else Color.Black
                    )
                }
            }
        }

        // GitHub — simple for end users
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(Icons.Default.Hub, "GitHub")
                Text(
                    text = "یک بار وارد شو تا اپ بتواند روی مخازن بخواند و بنویسد.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = { viewModel.startGitHubDeviceLogin() },
                    enabled = !state.deviceAuthInProgress,
                    colors = ButtonDefaults.buttonColors(containerColor = PersianTurquoise),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (state.deviceAuthInProgress) "در حال اتصال..." else "ورود به GitHub",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!state.deviceUserCode.isNullOrBlank()) {
                    Text(
                        text = "کد تأیید: ${state.deviceUserCode}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PersianTurquoise
                    )
                    Text(
                        text = "این کد را در صفحه گیت‌هاب وارد کن و اجازه بده.",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Button(
                        onClick = {
                            state.deviceVerificationUri?.let {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("باز کردن صفحه تأیید") }
                }

                OutlinedTextField(
                    value = state.githubOwner,
                    onValueChange = { viewModel.update { s -> s.copy(githubOwner = it) } },
                    label = { Text("مالک مخزن (owner)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = state.githubRepo,
                    onValueChange = { viewModel.update { s -> s.copy(githubRepo = it) } },
                    label = { Text("نام مخزن (repo)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = state.githubBranch,
                    onValueChange = { viewModel.update { s -> s.copy(githubBranch = it) } },
                    label = { Text("شاخه (branch)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Button(
                    onClick = { viewModel.saveGitHub(); viewModel.testGitHub() },
                    colors = ButtonDefaults.buttonColors(containerColor = PersianTurquoise),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("ذخیره و بررسی", color = Color.Black, fontWeight = FontWeight.Bold) }

                StatusLine(state.githubStatus)
            }
        }

        // Hugging Face
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(Icons.Default.Cloud, "Hugging Face")
                SecretField("توکن Hugging Face", state.hfToken) {
                    viewModel.update { s -> s.copy(hfToken = it) }
                }
                Button(
                    onClick = { viewModel.saveHuggingFace(); viewModel.testHuggingFace() },
                    colors = ButtonDefaults.buttonColors(containerColor = PersianTurquoise),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("ذخیره و بررسی", color = Color.Black, fontWeight = FontWeight.Bold) }
                StatusLine(state.hfStatus)
            }
        }

        // Self upgrade
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(Icons.Default.Refresh, "ارتقای خودکار")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("فعال باشد", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = state.autoUpgrade,
                        onCheckedChange = { viewModel.setAutoUpgrade(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = PersianTurquoise)
                    )
                }
            }
        }

        state.lastMessage?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = PersianTurquoise)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = PersianTurquoise, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusLine(status: String?) {
    if (status.isNullOrBlank()) return
    Text(
        text = status,
        style = MaterialTheme.typography.labelSmall,
        color = if (status.contains("✓")) Color(0xFF10B981) else MaterialTheme.colorScheme.error
    )
}

@Composable
private fun SecretField(label: String, value: String, onValueChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = null
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PersianTurquoise)
    )
}
