package com.example.ui.screens.media

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.CreationEntity
import com.example.data.model.GeminiModelConstants
import com.example.ui.theme.PersianCyan
import com.example.ui.theme.PersianGold
import com.example.ui.theme.PersianRose
import com.example.ui.theme.PersianTurquoise
import com.example.ui.theme.PersianViolet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaStudioScreen(
    viewModel: MediaStudioViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedMediaTab by remember { mutableIntStateOf(0) } // 0: Music (Lyria), 1: Video (Veo)

    val videoPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.setVideoBaseImage(context, it) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (selectedMediaTab == 0) Icons.Default.MusicNote else Icons.Default.Movie,
                        contentDescription = null,
                        tint = PersianTurquoise,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "استودیو چندرسانه‌ای هوش مصنوعی",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "آهنگسازی با مدل‌های پیشرفته لیریا (Lyria) و متحرک‌سازی تصاویر به ویدیو با وئو (Veo)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Top Sub Tabs
        item {
            TabRow(
                selectedTabIndex = selectedMediaTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = PersianTurquoise,
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedMediaTab == 0,
                    onClick = { selectedMediaTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ساخت موسیقی (Lyria)", fontWeight = FontWeight.Bold)
                        }
                    }
                )
                Tab(
                    selected = selectedMediaTab == 1,
                    onClick = { selectedMediaTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تولید ویدیو (Veo)", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        }

        // ================= MUSIC GENERATION SECTION (LYRIA) =================
        if (selectedMediaTab == 0) {
            // Model Selector: Clip vs Pro
            item {
                Column {
                    Text(
                        text = "نوع قطعه موسیقی و مدل لیریا:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.setMusicModel(GeminiModelConstants.MODEL_LYRIA_CLIP) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (uiState.selectedMusicModel == GeminiModelConstants.MODEL_LYRIA_CLIP)
                                    PersianTurquoise.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = if (uiState.selectedMusicModel == GeminiModelConstants.MODEL_LYRIA_CLIP)
                                androidx.compose.foundation.BorderStroke(1.5.dp, PersianTurquoise) else null
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "کلیپ کوتاه (تا ۳۰ ثانیه)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.selectedMusicModel == GeminiModelConstants.MODEL_LYRIA_CLIP) PersianTurquoise else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "lyria-3-clip-preview",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.setMusicModel(GeminiModelConstants.MODEL_LYRIA_PRO) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (uiState.selectedMusicModel == GeminiModelConstants.MODEL_LYRIA_PRO)
                                    PersianViolet.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = if (uiState.selectedMusicModel == GeminiModelConstants.MODEL_LYRIA_PRO)
                                androidx.compose.foundation.BorderStroke(1.5.dp, PersianViolet) else null
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "آهنگ کامل استودیویی",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.selectedMusicModel == GeminiModelConstants.MODEL_LYRIA_PRO) PersianViolet else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "lyria-3-pro-preview",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Genre Selector
            item {
                Column {
                    Text(
                        text = "دستگاه و سبک موسیقی:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val genres = listOf(
                        "سنتی اصیل ایرانی (سنتور و تار)",
                        "حماسی شاهنامه‌ای با دف و تنبور",
                        "بی‌کلام آرامش‌بخش و ذن",
                        "تلفیقی سنتی و الکترونیک مدرن",
                        "پیانو کلاسیک رمانتیک",
                        "ارکسترال سینمایی پرشور",
                        "پاپ ملایم با آکورد گیتار"
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(genres) { g ->
                            val isSelected = uiState.selectedGenre == g
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setGenre(g) },
                                label = { Text(g, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PersianTurquoise,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }
                }
            }

            // Music Prompt Input
            item {
                Column {
                    Text(
                        text = "شرح ملودی، حس‌وحال، یا شعر قطعه:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = uiState.musicPrompt,
                        onValueChange = { viewModel.setMusicPrompt(it) },
                        placeholder = {
                            Text("مثلاً: تک‌نوازی دلنشین سنتور در دستگاه همایون همراه با نوای باران و صدای طبیعت...")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .testTag("music_prompt_input"),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PersianTurquoise,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }

            // Music Suggestions
            item {
                Column {
                    Text(
                        text = "✨ نمونه ایده‌های موسیقی:",
                        style = MaterialTheme.typography.labelMedium,
                        color = PersianGold,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val musicSuggestions = listOf(
                        "تک‌نوازی پرشور تار و کمانچه با ریتم ۶/۸ سنتی",
                        "موسیقی متن حماسی برای صحنه نبرد با سازهای کوبه‌ای شرقی",
                        "ملودی آرامش‌بخش برای تمرکز و مطالعه با پیانو و سه‌تار"
                    )
                    musicSuggestions.forEach { s ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable { viewModel.setMusicPrompt(s) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = PersianGold, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(s, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // Generate Music Button
            item {
                Button(
                    onClick = { viewModel.generateMusic() },
                    enabled = !uiState.isGeneratingMusic && uiState.musicPrompt.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("generate_music_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PersianTurquoise,
                        contentColor = Color.Black
                    )
                ) {
                    if (uiState.isGeneratingMusic) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.Black, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(uiState.musicStatusMessage ?: "در حال آهنگسازی...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ساخت قطعه موسیقی با هوش مصنوعی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Music Error if any
            if (uiState.musicErrorMessage != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠️ " + uiState.musicErrorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            // Generated Music Player Card
            if (uiState.generatedMusicAudioBase64 != null) {
                item {
                    MusicPlayerCard(
                        title = uiState.musicPrompt,
                        genre = uiState.selectedGenre,
                        isPlaying = uiState.isMusicPlaying,
                        progress = uiState.musicPlaybackProgress,
                        durationSec = uiState.musicDurationSeconds,
                        description = uiState.generatedMusicDescription ?: "قطعه با هوش مصنوعی لیریا ساخته شد",
                        onPlayPauseToggle = { viewModel.toggleMusicPlayback() }
                    )
                }
            }
        }

        // ================= VIDEO GENERATION SECTION (VEO) =================
        if (selectedMediaTab == 1) {
            // Video base image uploader
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { videoPhotoPickerLauncher.launch("image/*") },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, PersianCyan.copy(alpha = 0.5f))
                ) {
                    if (uiState.videoBaseImageUri != null) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            AsyncImage(
                                model = uiState.videoBaseImageUri,
                                contentDescription = "تصویر جهت انیمیشن",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = { viewModel.clearVideoBaseImage() },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "حذف تصویر",
                                    tint = Color.White
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                tint = PersianCyan,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "بارگذاری عکس برای متحرک‌سازی به ویدیو (Veo)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = PersianCyan
                            )
                            Text(
                                text = "مدل veo-3.1-fast-generate-preview تصویر شما را به ویدیویی زنده تبدیل می‌کند",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Aspect Ratio for Video (16:9 or 9:16 as requested)
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AspectRatio, contentDescription = null, tint = PersianGold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "نسبت تصویر ویدیو (Aspect Ratio):",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf("16:9" to "۱۶:۹ (افقی سینمایی)", "9:16" to "۹:۱۶ (عمودی استوری و ریلز)").forEach { (ratio, label) ->
                            val isSelected = uiState.selectedVideoAspectRatio == ratio
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setVideoAspectRatio(ratio) },
                                label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PersianCyan,
                                    selectedLabelColor = Color.Black
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Motion Prompt Input
            item {
                Column {
                    Text(
                        text = "دستور حرکت و جلوه تصویری (به زبان فارسی):",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = uiState.videoPrompt,
                        onValueChange = { viewModel.setVideoPrompt(it) },
                        placeholder = {
                            Text("مثلاً: دوربین با زوم آرام به جلو حرکت کند و ذرات نورانی و برگ‌های پاییزی در هوا بچرخند...")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .testTag("video_prompt_input"),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PersianCyan,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }

            // Generate Video Button
            item {
                Button(
                    onClick = { viewModel.generateVideo() },
                    enabled = !uiState.isGeneratingVideo && (uiState.videoPrompt.isNotBlank() || uiState.videoBaseImageBase64 != null),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("generate_video_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PersianCyan,
                        contentColor = Color.Black
                    )
                ) {
                    if (uiState.isGeneratingVideo) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.Black, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(uiState.videoStatusMessage ?: "در حال تولید ویدیو با وئو...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Videocam, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("متحرک‌سازی و تولید ویدیو با Veo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Video Error if any
            if (uiState.videoErrorMessage != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠️ " + uiState.videoErrorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            // Video Result Preview
            if (uiState.generatedVideoResultText != null) {
                item {
                    VideoPlayerPreviewCard(
                        prompt = uiState.videoPrompt,
                        aspectRatio = uiState.selectedVideoAspectRatio,
                        isPlaying = uiState.isVideoPlaying,
                        baseImageUri = uiState.videoBaseImageUri,
                        onTogglePlay = { viewModel.toggleVideoPlayback() }
                    )
                }
            }
        }
    }
}

@Composable
fun MusicPlayerCard(
    title: String,
    genre: String,
    isPlaying: Boolean,
    progress: Float,
    durationSec: Int,
    description: String,
    onPlayPauseToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = androidx.compose.foundation.BorderStroke(2.dp, PersianTurquoise)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = PersianTurquoise)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "قطعه ساخته شده با لیریا",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PersianTurquoise
                    )
                }
                Surface(
                    color = PersianTurquoise.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = genre,
                        style = MaterialTheme.typography.labelSmall,
                        color = PersianTurquoise,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Animated Equalizer Visualizer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "waveform")
                for (i in 0 until 18) {
                    val animatedScale by infiniteTransition.animateFloat(
                        initialValue = 0.2f,
                        targetValue = if (isPlaying) (0.4f + (i % 5) * 0.15f) else 0.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 300 + (i * 40) % 500, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "bar_$i"
                    )
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(32.dp)
                            .scale(scaleX = 1f, scaleY = if (isPlaying) animatedScale else 0.2f)
                            .background(
                                if (i % 2 == 0) PersianTurquoise else PersianViolet,
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = PersianTurquoise,
                trackColor = MaterialTheme.colorScheme.surface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Player controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currentSeconds = (progress * durationSec).toInt()
                Text(
                    text = String.format("%02d:%02d / %02d:%02d", currentSeconds / 60, currentSeconds % 60, durationSec / 60, durationSec % 60),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                IconButton(
                    onClick = onPlayPauseToggle,
                    modifier = Modifier
                        .size(48.dp)
                        .background(PersianTurquoise, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "توقف" else "پخش",
                        tint = Color.Black
                    )
                }
            }
        }
    }
}

@Composable
fun VideoPlayerPreviewCard(
    prompt: String,
    aspectRatio: String,
    isPlaying: Boolean,
    baseImageUri: Uri?,
    onTogglePlay: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = androidx.compose.foundation.BorderStroke(2.dp, PersianCyan)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = PersianCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "پیش‌نمایش انیمیشن ویدیویی وئو",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PersianCyan
                    )
                }
                Surface(
                    color = PersianCyan.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "$aspectRatio • Veo Fast",
                        style = MaterialTheme.typography.labelSmall,
                        color = PersianCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Animated Video Canvas Simulation
            val isPortrait = aspectRatio == "9:16"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isPortrait) 320.dp else 200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (baseImageUri != null) {
                    AsyncImage(
                        model = baseImageUri,
                        contentDescription = "ویدیو متحرک",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = PersianCyan,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "فریم‌های متحرک شده ویدیویی",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                }

                // Play/Pause Overlay Button
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = PersianCyan,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = prompt.ifBlank { "انیمیشن سینمایی با وئو" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
