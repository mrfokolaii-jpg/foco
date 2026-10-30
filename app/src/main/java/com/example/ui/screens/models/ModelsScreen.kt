package com.example.ui.screens.models

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.KeyOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.ApiKeySnapshot
import com.example.data.local.ApiKeyStore
import com.example.data.model.AiModelCatalog
import com.example.data.model.AiProviderKind
import com.example.data.model.CatalogModel
import com.example.data.model.ModelCapability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ModelsUiState(
    val query: String = "",
    val capability: ModelCapability? = null,
    val provider: AiProviderKind? = null,
    val keys: ApiKeySnapshot = ApiKeySnapshot(),
    val models: List<CatalogModel> = AiModelCatalog.all
)

class ModelsViewModel(application: Application) : AndroidViewModel(application) {
    private val keyStore = ApiKeyStore(application)
    private val _uiState = MutableStateFlow(ModelsUiState())
    val uiState: StateFlow<ModelsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            applyFilters(_uiState.value.query, _uiState.value.capability, _uiState.value.provider)
        }
    }

    fun onQuery(query: String) {
        applyFilters(query, _uiState.value.capability, _uiState.value.provider)
    }

    fun onCapability(cap: ModelCapability?) {
        applyFilters(_uiState.value.query, cap, _uiState.value.provider)
    }

    fun onProvider(provider: AiProviderKind?) {
        applyFilters(_uiState.value.query, _uiState.value.capability, provider)
    }

    private fun applyFilters(query: String, cap: ModelCapability?, provider: AiProviderKind?) {
        val keys = keyStore.snapshot()
        val filtered = AiModelCatalog.all.filter { model ->
            val q = query.trim()
            val matchesQuery = q.isBlank() ||
                model.title.contains(q, ignoreCase = true) ||
                model.apiModelId.contains(q, ignoreCase = true) ||
                model.provider.persianTitle.contains(q, ignoreCase = true)
            val matchesCap = cap == null || model.capability == cap
            val matchesProv = provider == null || model.provider == provider
            matchesQuery && matchesCap && matchesProv
        }
        _uiState.value = ModelsUiState(query, cap, provider, keys, filtered)
    }
}

@Composable
fun ModelsScreen(
    selectedModelId: String,
    onSelectModel: (CatalogModel) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ModelsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "کاتالوگ مدل‌های رایگان",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "هوش مصنوعی رایگان فوکو و Hugging Face",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.query,
            onValueChange = { viewModel.onQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("models_search"),
            placeholder = { Text("جستجوی مدل...", fontSize = 13.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "جستجو")
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = uiState.capability == null,
                    onClick = { viewModel.onCapability(null) },
                    label = { Text("همه") },
                    colors = FilterChipDefaults.filterChipColors()
                )
            }
            item {
                FilterChip(
                    selected = uiState.capability == ModelCapability.TEXT_CHAT,
                    onClick = { viewModel.onCapability(ModelCapability.TEXT_CHAT) },
                    label = { Text("گفتگو و متن") }
                )
            }
            item {
                FilterChip(
                    selected = uiState.capability == ModelCapability.VISION,
                    onClick = { viewModel.onCapability(ModelCapability.VISION) },
                    label = { Text("بینایی و تصویر") }
                )
            }
            item {
                FilterChip(
                    selected = uiState.capability == ModelCapability.IMAGE,
                    onClick = { viewModel.onCapability(ModelCapability.IMAGE) },
                    label = { Text("ساخت تصویر") }
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = uiState.provider == null,
                    onClick = { viewModel.onProvider(null) },
                    label = { Text("همه پروایدرها") }
                )
            }
            AiProviderKind.entries.forEach { kind ->
                item {
                    FilterChip(
                        selected = uiState.provider == kind,
                        onClick = { viewModel.onProvider(kind) },
                        label = { Text(kind.persianTitle) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(uiState.models, key = { it.id }) { model ->
                val configured = uiState.keys.isConfigured(model.provider)
                val selected = model.id == selectedModelId
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectModel(model) }
                        .then(
                            if (selected) Modifier.border(
                                1.5.dp,
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(16.dp)
                            ) else Modifier
                        )
                        .testTag("model_card_${model.id}"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = model.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = Color(model.provider.badgeColorHex),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = model.provider.persianTitle,
                                        color = if (model.provider == AiProviderKind.HUGGINGFACE)
                                            Color(0xFF1E1B4B) else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = model.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = model.badge,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                if (!configured) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "نیاز به کلید",
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }
                        Icon(
                            imageVector = if (configured) Icons.Default.CheckCircle else Icons.Default.KeyOff,
                            contentDescription = if (configured) "کلید تنظیم شده" else "کلید ندارد",
                            tint = if (configured) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
