package com.example.ui.screens.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.ApiKeyStore
import com.example.data.model.CustomProvider
import com.example.data.model.ProviderPresets
import com.example.data.remote.OpenAiCompatProvider
import com.example.ui.theme.PersianTurquoise
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Manages an unlimited list of hand-wired OpenAI-compatible providers.
 * Every enabled entry joins the auto-rotation chain, so when one service hits its
 * limit FOKO simply moves on to the next one.
 */
@Composable
fun CustomProvidersCard(context: Context, onChanged: () -> Unit) {

    val store = remember { ApiKeyStore(context) }
    val scope = rememberCoroutineScope()

    var providers by remember { mutableStateOf(store.getCustomProviders()) }
    var adding by remember { mutableStateOf(false) }
    var loadingModels by remember { mutableStateOf(false) }
    var fetchedModels by remember { mutableStateOf<List<String>>(emptyList()) }

    // Draft for the "add new" form
    var draftLabel by remember { mutableStateOf("") }
    var draftBase by remember { mutableStateOf("") }
    var draftKey by remember { mutableStateOf("") }
    var draftModel by remember { mutableStateOf("") }

    fun persist(list: List<CustomProvider>) {
        store.setCustomProviders(list)
        providers = list
        onChanged()
    }

    fun resetDraft() {
        draftLabel = ""
        draftBase = ""
        draftKey = ""
        draftModel = ""
        fetchedModels = emptyList()
        adding = false
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Code, contentDescription = null, tint = PersianTurquoise)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "سرویس‌های دستی من (${providers.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "هر تعداد سرویس رایگان که خواستی اینجا اضافه کن. همه‌شان پشت سر هم قرار می‌گیرند و در حالت Auto، وقتی یکی به سقف خورد فوکو خودش می‌رود سراغ بعدی.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ---- Saved providers -------------------------------------------
            providers.forEach { provider ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = provider.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = provider.model.ifBlank { "مدل انتخاب نشده" },
                            style = MaterialTheme.typography.labelSmall,
                            color = PersianTurquoise
                        )
                        Text(
                            text = provider.baseUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = provider.enabled,
                        onCheckedChange = { on ->
                            persist(providers.map { if (it.id == provider.id) it.copy(enabled = on) else it })
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = PersianTurquoise)
                    )
                    IconButton(onClick = {
                        persist(providers.filterNot { it.id == provider.id })
                        Toast.makeText(context, "«${provider.label}» حذف شد", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "حذف",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // ---- Add form ---------------------------------------------------
            if (!adding) {
                Button(
                    onClick = { adding = true },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PersianTurquoise)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("افزودن سرویس جدید", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    text = "۱) یکی از سرویس‌های آماده را بزن تا آدرسش پر شود، یا خودت آدرس را بنویس:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Preset list — fills the name and base URL, nothing is guessed.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 190.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProviderPresets.ALL.forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    draftLabel = preset.label
                                    draftBase = preset.baseUrl
                                    fetchedModels = emptyList()
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Column {
                                Text(
                                    text = preset.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = preset.note,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "۲) کلید را بچسبان و اسم مدل را واکشی کن:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = draftLabel,
                    onValueChange = { draftLabel = it },
                    label = { Text("نام سرویس") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = draftBase,
                    onValueChange = { draftBase = it },
                    label = { Text("آدرس API (Base URL)") },
                    placeholder = { Text("https://api.groq.com/openai/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = draftKey,
                    onValueChange = { draftKey = it },
                    label = { Text("کلید API (اگر سرویس کلید نمی‌خواهد، خالی بگذار)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Button(
                    onClick = {
                        loadingModels = true
                        scope.launch {
                            val models = OpenAiCompatProvider.listModels(draftBase, draftKey)
                            fetchedModels = models
                            loadingModels = false
                            if (models.isEmpty()) {
                                Toast.makeText(
                                    context,
                                    "لیست مدل‌ها گرفته نشد؛ اسم مدل را دستی بنویس",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else if (draftModel.isBlank()) {
                                draftModel = models.first()
                            }
                        }
                    },
                    enabled = draftBase.isNotBlank() && !loadingModels,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    if (loadingModels) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(18.dp).height(18.dp),
                            strokeWidth = 2.dp,
                            color = PersianTurquoise
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = PersianTurquoise)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (fetchedModels.isEmpty()) "واکشی لیست مدل‌ها از سرویس"
                        else "واکشی مجدد (${fetchedModels.size} مدل)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (fetchedModels.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 170.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        fetchedModels.forEach { model ->
                            val chosen = model == draftModel
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (chosen) PersianTurquoise.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { draftModel = model }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = model + if (chosen) "  ✓" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = draftModel,
                    onValueChange = { draftModel = it },
                    label = { Text("نام مدل") },
                    placeholder = { Text("از لیست بالا انتخاب کن یا دستی بنویس") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            store.upsertCustomProvider(
                                CustomProvider(
                                    id = UUID.randomUUID().toString(),
                                    label = draftLabel.ifBlank { "سرویس دستی" },
                                    baseUrl = draftBase.trim(),
                                    apiKey = draftKey.trim(),
                                    model = draftModel.trim()
                                )
                            )
                            providers = store.getCustomProviders()
                            onChanged()
                            Toast.makeText(context, "سرویس اضافه و به ربات وصل شد", Toast.LENGTH_SHORT).show()
                            resetDraft()
                        },
                        enabled = draftBase.isNotBlank() && draftModel.isNotBlank(),
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PersianTurquoise)
                    ) {
                        Text("ذخیره و اتصال", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { resetDraft() }) {
                        Text("انصراف", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "نکته: سرویس‌هایی مثل Kilo Code و OVHcloud کلید نمی‌خواهند — فیلد کلید را خالی بگذار.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
