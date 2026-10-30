package com.example.ui.screens.connectors

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.connectors.ConnectorResult
import com.example.data.connectors.ConnectorStore
import com.example.data.connectors.GitHubConnector
import com.example.data.connectors.HuggingFaceConnector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ConnectorsUiState(
    val githubToken: String = "",
    val githubOwner: String = ConnectorStore.DEFAULT_GH_OWNER,
    val githubRepo: String = ConnectorStore.DEFAULT_GH_REPO,
    val githubBranch: String = ConnectorStore.DEFAULT_GH_BRANCH,
    val hfToken: String = "",
    val autoUpgrade: Boolean = true,
    val continuousVoice: Boolean = true,
    val overlayRunning: Boolean = false,
    val githubStatus: String? = null,
    val hfStatus: String? = null,
    val busy: Boolean = false,
    val lastMessage: String? = null
)

class ConnectorsViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ConnectorStore(application)
    private val _uiState = MutableStateFlow(ConnectorsUiState())
    val uiState: StateFlow<ConnectorsUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        _uiState.value = _uiState.value.copy(
            githubToken = store.githubToken(),
            githubOwner = store.githubOwner(),
            githubRepo = store.githubRepo(),
            githubBranch = store.githubBranch(),
            hfToken = store.hfToken(),
            autoUpgrade = store.isAutoUpgradeEnabled(),
            continuousVoice = store.isContinuousVoiceEnabled(),
            overlayRunning = store.isOverlayEnabled()
        )
    }

    fun update(transform: (ConnectorsUiState) -> ConnectorsUiState) {
        _uiState.value = transform(_uiState.value)
    }

    fun saveGitHub() {
        val s = _uiState.value
        store.setGitHub(s.githubToken, s.githubOwner, s.githubRepo, s.githubBranch)
        _uiState.value = s.copy(lastMessage = "تنظیمات گیت‌هاب ذخیره شد.")
    }

    fun saveHuggingFace() {
        store.setHfToken(_uiState.value.hfToken)
        _uiState.value = _uiState.value.copy(lastMessage = "توکن Hugging Face ذخیره شد.")
    }

    fun testGitHub() {
        viewModelScope.launch {
            val s = _uiState.value
            _uiState.value = s.copy(busy = true, githubStatus = "در حال بررسی...")
            // Public repo: no token needed. Private / identity: token used if present.
            val result = GitHubConnector.checkRepo(s.githubToken, s.githubOwner, s.githubRepo)
            _uiState.value = _uiState.value.copy(
                busy = false,
                githubStatus = when (result) {
                    is ConnectorResult.Ok -> "متصل ✓  ${result.value}"
                    is ConnectorResult.Err -> result.message
                }
            )
        }
    }

    fun testHuggingFace() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true, hfStatus = "در حال بررسی...")
            val result = HuggingFaceConnector.whoAmI(_uiState.value.hfToken)
            _uiState.value = _uiState.value.copy(
                busy = false,
                hfStatus = when (result) {
                    is ConnectorResult.Ok -> "متصل ✓  (${result.value})"
                    is ConnectorResult.Err -> result.message
                }
            )
        }
    }

    fun setAutoUpgrade(enabled: Boolean) {
        store.setAutoUpgradeEnabled(enabled)
        _uiState.value = _uiState.value.copy(autoUpgrade = enabled)
    }

    fun setContinuousVoice(enabled: Boolean) {
        store.setContinuousVoiceEnabled(enabled)
        _uiState.value = _uiState.value.copy(continuousVoice = enabled)
    }

    fun setOverlayRunning(running: Boolean) {
        store.setOverlayEnabled(running)
        _uiState.value = _uiState.value.copy(overlayRunning = running)
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(lastMessage = null)
    }
}
