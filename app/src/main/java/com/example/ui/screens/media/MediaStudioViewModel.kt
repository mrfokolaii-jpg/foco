package com.example.ui.screens.media

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.CreationEntity
import com.example.data.model.GeminiModelConstants
import com.example.data.model.MusicGenerationConfig
import com.example.data.model.VideoGenerationConfig
import com.example.data.remote.ApiResult
import com.example.data.remote.GeminiApiService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.UUID

data class MediaStudioUiState(
    // Music Generation State
    val musicPrompt: String = "",
    val selectedMusicModel: String = GeminiModelConstants.MODEL_LYRIA_CLIP, // lyria-3-clip-preview or lyria-3-pro-preview
    val selectedGenre: String = "سنتی اصیل ایرانی (سنتور و تار)",
    val musicDurationSeconds: Int = 30,
    val isGeneratingMusic: Boolean = false,
    val musicStatusMessage: String? = null,
    val generatedMusicAudioBase64: String? = null,
    val generatedMusicDescription: String? = null,
    val musicErrorMessage: String? = null,
    val isMusicPlaying: Boolean = false,
    val musicPlaybackProgress: Float = 0f,

    // Video Generation State (Veo)
    val videoPrompt: String = "",
    val selectedVideoAspectRatio: String = "16:9", // 16:9 or 9:16
    val videoBaseImageBase64: String? = null,
    val videoBaseImageUri: Uri? = null,
    val isGeneratingVideo: Boolean = false,
    val videoStatusMessage: String? = null,
    val generatedVideoResultText: String? = null,
    val videoErrorMessage: String? = null,
    val isVideoPlaying: Boolean = false,

    // History
    val mediaCreations: List<CreationEntity> = emptyList()
)

class MediaStudioViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val creationDao = db.creationDao()

    private val _uiState = MutableStateFlow(MediaStudioUiState())
    val uiState: StateFlow<MediaStudioUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            creationDao.getAllCreations().collectLatest { list ->
                val mediaOnly = list.filter { it.type == "MUSIC" || it.type == "VIDEO" }
                _uiState.value = _uiState.value.copy(mediaCreations = mediaOnly)
            }
        }
    }

    fun setMusicPrompt(prompt: String) {
        _uiState.value = _uiState.value.copy(musicPrompt = prompt)
    }

    fun setMusicModel(model: String) {
        _uiState.value = _uiState.value.copy(
            selectedMusicModel = model,
            musicDurationSeconds = if (model == GeminiModelConstants.MODEL_LYRIA_CLIP) 30 else 90
        )
    }

    fun setGenre(genre: String) {
        _uiState.value = _uiState.value.copy(selectedGenre = genre)
    }

    fun setMusicDuration(duration: Int) {
        _uiState.value = _uiState.value.copy(musicDurationSeconds = duration)
    }

    fun toggleMusicPlayback() {
        val newPlaying = !_uiState.value.isMusicPlaying
        _uiState.value = _uiState.value.copy(isMusicPlaying = newPlaying)

        if (newPlaying) {
            viewModelScope.launch {
                while (_uiState.value.isMusicPlaying) {
                    delay(300)
                    val nextProgress = _uiState.value.musicPlaybackProgress + 0.02f
                    if (nextProgress >= 1f) {
                        _uiState.value = _uiState.value.copy(isMusicPlaying = false, musicPlaybackProgress = 0f)
                        break
                    } else {
                        _uiState.value = _uiState.value.copy(musicPlaybackProgress = nextProgress)
                    }
                }
            }
        }
    }

    fun generateMusic() {
        val prompt = _uiState.value.musicPrompt.trim()
        if (prompt.isBlank()) {
            _uiState.value = _uiState.value.copy(musicErrorMessage = "لطفاً موضوع، حس و حال یا متن شعر موسیقی را به فارسی بنویسید.")
            return
        }

        val config = MusicGenerationConfig(
            modelId = _uiState.value.selectedMusicModel,
            prompt = prompt,
            durationSeconds = _uiState.value.musicDurationSeconds,
            genre = _uiState.value.selectedGenre
        )

        _uiState.value = _uiState.value.copy(
            isGeneratingMusic = true,
            musicStatusMessage = "در حال آهنگسازی و تولید صدا با هوش مصنوعی لیریا (${if (config.modelId == GeminiModelConstants.MODEL_LYRIA_CLIP) "کلیپ ۳۰ ثانیه‌ای" else "آهنگ کامل"})...",
            musicErrorMessage = null,
            generatedMusicAudioBase64 = null
        )

        viewModelScope.launch {
            val result = GeminiApiService.generateMusic(config)
            when (result) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isGeneratingMusic = false,
                        musicStatusMessage = null,
                        generatedMusicAudioBase64 = result.data.audioBase64 ?: "simulated_audio_stream",
                        generatedMusicDescription = result.data.description,
                        isMusicPlaying = true,
                        musicPlaybackProgress = 0f
                    )

                    // Persist creation
                    creationDao.insertCreation(
                        CreationEntity(
                            id = UUID.randomUUID().toString(),
                            type = "MUSIC",
                            title = if (prompt.length > 25) prompt.take(24) + "..." else prompt,
                            prompt = prompt,
                            mediaData = result.data.audioBase64,
                            modelUsed = result.data.modelUsed,
                            metadata = "سبک: ${_uiState.value.selectedGenre} | مدت: ${_uiState.value.musicDurationSeconds} ثانیه"
                        )
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isGeneratingMusic = false,
                        musicStatusMessage = null,
                        musicErrorMessage = result.message
                    )
                }
            }
        }
    }

    // Video Functions
    fun setVideoPrompt(prompt: String) {
        _uiState.value = _uiState.value.copy(videoPrompt = prompt)
    }

    fun setVideoAspectRatio(aspectRatio: String) {
        _uiState.value = _uiState.value.copy(selectedVideoAspectRatio = aspectRatio)
    }

    fun setVideoBaseImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                _uiState.value = _uiState.value.copy(
                    videoBaseImageBase64 = base64,
                    videoBaseImageUri = uri
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(videoErrorMessage = "خطا در بارگذاری تصویر برای انیمیشن: ${e.message}")
            }
        }
    }

    fun clearVideoBaseImage() {
        _uiState.value = _uiState.value.copy(
            videoBaseImageBase64 = null,
            videoBaseImageUri = null
        )
    }

    fun toggleVideoPlayback() {
        _uiState.value = _uiState.value.copy(isVideoPlaying = !_uiState.value.isVideoPlaying)
    }

    fun generateVideo() {
        val prompt = _uiState.value.videoPrompt.trim()
        if (prompt.isBlank() && _uiState.value.videoBaseImageBase64 == null) {
            _uiState.value = _uiState.value.copy(videoErrorMessage = "لطفاً توضیحات حرکت صحنه یا تصویر پایه را وارد نمایید.")
            return
        }

        val config = VideoGenerationConfig(
            modelId = GeminiModelConstants.MODEL_VEO_FAST,
            prompt = prompt.ifBlank { "متحرک‌سازی روان و سینمایی تصویر" },
            aspectRatio = _uiState.value.selectedVideoAspectRatio,
            baseImageBase64 = _uiState.value.videoBaseImageBase64
        )

        _uiState.value = _uiState.value.copy(
            isGeneratingVideo = true,
            videoStatusMessage = "در حال متحرک‌سازی و تولید فریم‌های ویدیویی با مدل وئو (${config.aspectRatio})...",
            videoErrorMessage = null,
            generatedVideoResultText = null
        )

        viewModelScope.launch {
            val result = GeminiApiService.generateVideo(config)
            when (result) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isGeneratingVideo = false,
                        videoStatusMessage = null,
                        generatedVideoResultText = result.data.description,
                        isVideoPlaying = true
                    )

                    creationDao.insertCreation(
                        CreationEntity(
                            id = UUID.randomUUID().toString(),
                            type = "VIDEO",
                            title = if (prompt.length > 25) prompt.take(24) + "..." else "ویدیوی انیمیشن",
                            prompt = prompt,
                            mediaData = _uiState.value.videoBaseImageBase64,
                            modelUsed = result.data.modelUsed,
                            metadata = "نسبت: ${_uiState.value.selectedVideoAspectRatio} | مدل: Veo Fast"
                        )
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isGeneratingVideo = false,
                        videoStatusMessage = null,
                        videoErrorMessage = result.message
                    )
                }
            }
        }
    }

    fun deleteCreation(id: String) {
        viewModelScope.launch {
            creationDao.deleteCreation(id)
        }
    }
}
