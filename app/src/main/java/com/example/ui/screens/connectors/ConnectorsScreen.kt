package com.example.ui.screens.connectors

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "اتصال به ابزارها و ارتقای خودکار",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "پلاگین‌ها را همین‌جا روشن و خاموش کن. محل وارد کردن کلیدها: «تنظیمات و کلیدها».",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Floating widget
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(Icons.Default.AutoAwesome, "ویجت شناور و مکالمه زنده")

                Text(
                    text = "راهنمای رنگ گوی: سبز = در حال شنیدن صحبت شما، قرمز = در حال تفکر، آبی = در حال پاسخ دادن.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    StateDot(Color(0xFF22C55E), "شنیدن")
                    StateDot(Color(0xFFEF4444), "تفکر")
                    StateDot(Color(0xFF3B82F6), "پاسخ")
                }

                Text(
                    text = "نکته: با نگه‌داشتن گوی، ویجت مخفی می‌شود و با یک ضربه دوباره ظاهر می‌گردد.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("مکالمه پیوسته صوتی (دست‌ها آزاد)", style = MaterialTheme.typography.bodyMedium)
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
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                            )
                        } else if (state.overlayRunning) {
                            FokoFloatingOverlayService.stop(context)
                            viewModel.setOverlayRunning(false)
                            Toast.makeText(context, "ویجت شناور خاموش شد", Toast.LENGTH_SHORT).show()
                        } else {
                            FokoFloatingOverlayService.start(context)
                            viewModel.setOverlayRunning(true)
                            Toast.makeText(context, "ویجت شناور روشن شد", Toast.LENGTH_SHORT).show()
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

        // GitHub
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(Icons.Default.Hub, "گیت‌هاب — پلاگین ذخیره، دانش و کامپایل")
                Text(
                    text = "توکن، نام مخزن و شاخه را در «تنظیمات و کلیدها» وارد کن. این‌جا فقط پلاگین را روشن یا خاموش می‌کنی.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (state.githubToken.isNotBlank()) "کلید تنظیم شده ✓" else "کلید در تنظیمات وارد نشده",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (state.githubToken.isNotBlank()) PersianTurquoise
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = state.githubToken.isNotBlank(),
                        onCheckedChange = { viewModel.setGitHubEnabled(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = PersianTurquoise)
                    )
                }
                StatusLine(state.githubStatus)
            }
        }

        // Hugging Face — connected from the chat page, so only a status check lives here.
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(Icons.Default.Cloud, "هاگینگ‌فیس — پلاگین مدل‌ها و ابزارها")
                Text(
                    text = "توکن Hugging Face را در «تنظیمات و کلیدها» وارد کن. این‌جا فقط پلاگین را روشن یا خاموش می‌کنی.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (state.hfToken.isNotBlank()) "کلید تنظیم شده ✓" else "کلید در تنظیمات وارد نشده",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (state.hfToken.isNotBlank()) PersianTurquoise
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = state.hfToken.isNotBlank(),
                        onCheckedChange = { viewModel.setHuggingFaceEnabled(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = PersianTurquoise)
                    )
                }
                StatusLine(state.hfStatus)
            }
        }

        // Self upgrade
        // Voice: pick a Persian voice, rename it, test it here.
        VoiceSettingsCard(context = context)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(Icons.Default.Refresh, "ارتقای خودکار (Self-Upgrade)")
                Text(
                    text = "وقتی فوکو دانش یا ابزاری را نداشته باشد: خودش در وب جستجو می‌کند، یک پوشه دانش داخل مخزن گیت‌هاب تو می‌سازد و اطلاعات را داخلش بارگذاری می‌کند. اگر پروژه‌ای پیدا کند که برای خودِ اپلیکیشن مفید باشد، آن را در مخزن می‌گذارد، به پروژه وصل می‌کند و با گیت‌هاب کامپایلش می‌کند؛ بعد نتیجه کامپایل و لینک خروجی را به تو گزارش می‌دهد که «اپلیکیشن جدید آماده شد».",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
private fun StateDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
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
