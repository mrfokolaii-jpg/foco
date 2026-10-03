package com.example.ui.screens.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ApiKeyStore
import com.example.data.model.AiModelCatalog
import com.example.data.model.AiProviderKind
import com.example.data.model.CatalogModel
import com.example.data.remote.ApiResult
import com.example.data.remote.GeminiModels
import com.example.data.remote.GroqProvider
import com.example.data.remote.HuggingFaceProvider
import com.example.data.remote.OpenAiCompatProvider
import com.example.data.remote.OpenRouterProvider
import com.example.data.remote.XaiGrokProvider
import com.example.ui.theme.PersianTurquoise

/** چقدر از هر سرویس نشان داده شود (کاربر ۴۰ تا ۵۰ مدل می‌خواست). */
private const val MODELS_PER_PROVIDER = 60

/**
 * پنجرهٔ انتخاب مدل.
 *
 * قاعدهٔ اصلی: فهرست هر سرویس **با کلید خودِ کاربر از خودِ آن سرویس** خوانده می‌شود.
 * هیچ نام مدلی از پیش حدس زده نمی‌شود، چون هر کلید به بخش متفاوتی از مدل‌ها دسترسی
 * دارد؛ همین باعث می‌شد کاربر روی مدلی بزند و پیام «این مدل پشتیبانی نمی‌شود» بگیرد.
 *
 * اگر خواندن فهرست شکست بخورد، متن خطای واقعی سرور همان‌جا نشان داده می‌شود
 * (۴۰۱ کلید غلط، ۴۰۲ اعتبار تمام‌شده، ۴۰۳ دسترسی بسته) تا کاربر بداند مشکل کجاست.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectorSheet(
    selectedModelId: String,
    onSelect: (CatalogModel) -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { ApiKeyStore(context.applicationContext) }
    val snapshot = remember { store.snapshot() }

    var loading by remember { mutableStateOf(true) }
    var live by remember { mutableStateOf<Map<AiProviderKind, List<CatalogModel>>>(emptyMap()) }
    var errors by remember { mutableStateOf<Map<AiProviderKind, String>>(emptyMap()) }

    LaunchedEffect(Unit) {
        loading = true
        val found = mutableMapOf<AiProviderKind, List<CatalogModel>>()
        val failed = mutableMapOf<AiProviderKind, String>()

        suspend fun loadOpenAiCompat(provider: AiProviderKind, base: String, key: String) {
            if (key.isBlank()) return
            when (val result = OpenAiCompatProvider.listModelsDetailed(base, key)) {
                is ApiResult.Success -> found[provider] = result.data
                    .take(MODELS_PER_PROVIDER)
                    .map { AiModelCatalog.live(provider, it) }
                is ApiResult.Error -> failed[provider] = result.message
            }
        }

        if (snapshot.geminiConfigured) {
            when (val result = GeminiModels.listDetailed(store.getGeminiKey())) {
                is ApiResult.Success -> found[AiProviderKind.GEMINI] = result.data
                    .take(MODELS_PER_PROVIDER)
                    .map { AiModelCatalog.gemini(it) }
                is ApiResult.Error -> failed[AiProviderKind.GEMINI] = result.message
            }
        }
        loadOpenAiCompat(AiProviderKind.HUGGINGFACE, HuggingFaceProvider.BASE_URL, store.getHuggingFaceKey())
        loadOpenAiCompat(AiProviderKind.OPENROUTER, OpenRouterProvider.BASE_URL, store.getOpenRouterKey())
        loadOpenAiCompat(AiProviderKind.GROQ, GroqProvider.BASE_URL, store.getGroqKey())
        loadOpenAiCompat(AiProviderKind.XAI_GROK, XaiGrokProvider.BASE_URL, store.getXaiGrokKey())

        live = found
        errors = failed
        loading = false
    }

    val customModels = remember { AiModelCatalog.customModels(store.getCustomProviders()) }
    val nineRouterModel = remember {
        if (snapshot.nineRouterConfigured) AiModelCatalog.nineRouter(store.getNineRouterModel()) else null
    }

    val configuredProviders = listOf(
        AiProviderKind.GEMINI,
        AiProviderKind.HUGGINGFACE,
        AiProviderKind.OPENROUTER,
        AiProviderKind.GROQ,
        AiProviderKind.XAI_GROK
    ).filter { snapshot.isConfigured(it) }

    val nothingAtAll = configuredProviders.isEmpty() &&
        customModels.isEmpty() &&
        nineRouterModel == null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // فهرست می‌تواند ۴۰ تا ۵۰ مورد باشد؛ بدون اسکرول بقیه دیده نمی‌شد.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "انتخاب هوش مصنوعی (Select Model):",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = PersianTurquoise
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "در حال خواندن فهرست مدل‌ها با کلید خودت...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            nineRouterModel?.let { model ->
                SectionHeader("9Router", 1)
                ModelOptionCard(
                    title = model.title,
                    tag = model.badge,
                    isSelected = selectedModelId == model.id,
                    onClick = { onSelect(model) }
                )
            }

            configuredProviders.forEach { provider ->
                val models = live[provider].orEmpty()
                val error = errors[provider]

                // اگر فهرست زنده نیامد، مدل‌های پیش‌فرض همان سرویس نشان داده می‌شوند
                // تا دست‌کم چیزی برای انتخاب باشد.
                val fallback = remember(provider) { AiModelCatalog.modelsForProvider(provider) }
                val shown = if (models.isNotEmpty()) models else fallback

                SectionHeader(provider.persianTitle, shown.size)
                error?.let { ErrorLine(it) }
                shown.forEach { model ->
                    ModelOptionCard(
                        title = model.title,
                        tag = model.badge,
                        isSelected = selectedModelId == model.id,
                        onClick = { onSelect(model) }
                    )
                }
            }

            if (customModels.isNotEmpty()) {
                SectionHeader("سرویس‌های دستی تو", customModels.size)
                customModels.forEach { model ->
                    ModelOptionCard(
                        title = model.title,
                        tag = model.badge,
                        isSelected = selectedModelId == model.id,
                        onClick = { onSelect(model) }
                    )
                }
            }

            if (nothingAtAll && !loading) {
                Text(
                    text = "هیچ سرویسی وصل نیست. کلید جمینای یا یک سرویس دستی اضافه کن.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PersianTurquoise)
                ) {
                    Text("رفتن به تنظیمات و وارد کردن کلید", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "$title ($count مدل):",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = PersianTurquoise
    )
}

/** متن خطای واقعی سرور — برای اینکه کاربر بداند چرا فهرست نیامد. */
@Composable
private fun ErrorLine(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** یک ردیف کوتاه برای هر مدل — فقط نام مدل و برچسب سرویس. */
@Composable
fun ModelOptionCard(
    title: String,
    tag: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) PersianTurquoise.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) BorderStroke(1.5.dp, PersianTurquoise) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) PersianTurquoise else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            if (isSelected) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "انتخاب شده",
                    tint = PersianTurquoise,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
