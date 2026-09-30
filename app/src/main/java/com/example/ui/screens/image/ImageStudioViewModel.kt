package com.example.ui.screens.image

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
import com.example.data.model.ImageGenerationConfig
import com.example.data.remote.ApiResult
import com.example.data.remote.GeminiApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.UUID

data class ImageStudioUiState(
    val prompt: String = "",
    val selectedResolution: String = "1K", // 512px, 1K, 2K, 4K
    val selectedAspectRatio: String = "1:1", // 1:1, 16:9, 9:16, 4:3, 3:4
    val selectedStyle: String = "",
    val baseImageBase64: String? = null,
    val baseImageUri: Uri? = null,
    val isEditMode: Boolean = false,
    val isGenerating: Boolean = false,
    val statusMessage: String? = null,
    val generatedImageBase64: String? = null,
    val generatedDescription: String? = null,
    val errorMessage: String? = null,
    val creationsHistory: List<CreationEntity> = emptyList()
)

class ImageStudioViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val creationDao = db.creationDao()

    private val _uiState = MutableStateFlow(ImageStudioUiState())
    val uiState: StateFlow<ImageStudioUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            creationDao.getCreationsByType("IMAGE").collectLatest { list ->
                _uiState.value = _uiState.value.copy(creationsHistory = list)
            }
        }
    }

    fun setPrompt(prompt: String) {
        _uiState.value = _uiState.value.copy(prompt = prompt)
    }

    fun setResolution(res: String) {
        _uiState.value = _uiState.value.copy(selectedResolution = res)
    }

    fun setAspectRatio(ratio: String) {
        _uiState.value = _uiState.value.copy(selectedAspectRatio = ratio)
    }

    fun setStyle(style: String) {
        _uiState.value = _uiState.value.copy(
            selectedStyle = if (_uiState.value.selectedStyle == style) "" else style
        )
    }

    fun setBaseImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                _uiState.value = _uiState.value.copy(
                    baseImageBase64 = base64,
                    baseImageUri = uri,
                    isEditMode = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "خطا در بارگذاری تصویر پایه: ${e.message}")
            }
        }
    }

    fun clearBaseImage() {
        _uiState.value = _uiState.value.copy(
            baseImageBase64 = null,
            baseImageUri = null,
            isEditMode = false
        )
    }

    fun generateImage() {
        val currentPrompt = _uiState.value.prompt.trim()
        if (currentPrompt.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "لطفاً توضیحات تصویر مورد نظر خود را به فارسی بنویسید.")
            return
        }

        val config = ImageGenerationConfig(
            modelId = if (_uiState.value.selectedResolution == "4K" || _uiState.value.selectedResolution == "2K")
                GeminiModelConstants.MODEL_IMAGE_PRO
            else
                GeminiModelConstants.MODEL_IMAGE_FLASH,
            prompt = currentPrompt,
            resolution = _uiState.value.selectedResolution,
            aspectRatio = _uiState.value.selectedAspectRatio,
            stylePrompt = _uiState.value.selectedStyle,
            baseImageBase64 = _uiState.value.baseImageBase64
        )

        _uiState.value = _uiState.value.copy(
            isGenerating = true,
            statusMessage = if (_uiState.value.isEditMode) "در حال اعمال تغییرات و ویرایش هوشمند تصویر..." else "در حال خلق تصویر با کیفیت ${_uiState.value.selectedResolution} و هوش مصنوعی...",
            errorMessage = null,
            generatedImageBase64 = null
        )

        viewModelScope.launch {
            val result = GeminiApiService.generateOrEditImage(config)
            when (result) {
                is ApiResult.Success -> {
                    val imgData = result.data.imageBase64
                    val desc = result.data.textDescription

                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        statusMessage = null,
                        generatedImageBase64 = imgData,
                        generatedDescription = desc
                    )

                    // Persist to creations DB
                    if (imgData != null) {
                        creationDao.insertCreation(
                            CreationEntity(
                                id = UUID.randomUUID().toString(),
                                type = "IMAGE",
                                title = if (currentPrompt.length > 25) currentPrompt.take(24) + "..." else currentPrompt,
                                prompt = currentPrompt,
                                mediaData = imgData,
                                modelUsed = result.data.modelUsed,
                                metadata = "کیفیت: ${_uiState.value.selectedResolution} | نسبت: ${_uiState.value.selectedAspectRatio}"
                            )
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        statusMessage = null,
                        errorMessage = result.message
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
