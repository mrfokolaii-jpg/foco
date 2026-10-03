package com.example.ui.screens.connectors

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.connectors.ConnectorResult
import com.example.data.connectors.ConnectorStore
import com.example.data.connectors.GitHubConnector
import com.example.data.connectors.GoogleDriveConnector
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
    val driveClientId: String = "",
    val driveClientSecret: String = "",
    val driveRefreshToken: String = "",
    val driveAccessToken: String = "",
    val autoUpgrade: Boolean = true,
    val continuousVoice: Boolean = true,
    val overlayRunning: Boolean = false,
    val githubStatus: String? = null,
    val hfStatus: String? = null,
    val driveStatus: String? = null,
    val busy: Boolean = false,
    val lastMessage: String? = null
)

class ConnectorsViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ConnectorStore(application)
    private val _uiState = MutableStateFlow(ConnectorsUiState())
    val uiState: StateFlow<ConnectorsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = _uiState.value.copy(
            githubToken = store.githubToken(),
            githubOwner = store.githubOwner(),
            githubRepo = store.githubRepo(),
            githubBranch = store.githubBranch(),
            hfToken = store.hfToken(),
            driveClientId = store.driveClientId(),
            driveClientSecret = store.driveClientSecret(),
            driveRefreshToken = store.driveRefreshToken(),
            driveAccessToken = store.driveAccessToken(),
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

    /**
     * کلید گیت‌هاب در صفحه‌ی تنظیمات وارد می‌شود؛ اینجا فقط پلاگین روشن/خاموش می‌شود.
     * روشن‌کردن یعنی همان کلیدِ ذخیره‌شده فوراً به ربات وصل شود.
     */
    fun setGitHubEnabled(enabled: Boolean) {
        if (enabled) {
            load()
            val s = _uiState.value
            if (s.githubToken.isNotBlank()) {
                testGitHub()
            } else {
                _uiState.value = s.copy(githubStatus = "اول کلید گیت‌هاب را در تنظیمات وارد کن.")
            }
        } else {
            store.setGitHub("", store.githubOwner(), store.githubRepo(), store.githubBranch())
            load()
            _uiState.value = _uiState.value.copy(githubStatus = "پلاگین گیت‌هاب خاموش شد.")
        }
    }

    /** همان قاعده برای Hugging Face: کلید در تنظیمات، روشن/خاموش اینجا. */
    fun setHuggingFaceEnabled(enabled: Boolean) {
        if (enabled) {
            load()
            val s = _uiState.value
            if (s.hfToken.isNotBlank()) {
                testHuggingFace()
            } else {
                _uiState.value = s.copy(hfStatus = "اول کلید Hugging Face را در تنظیمات وارد کن.")
            }
        } else {
            store.setHfToken("")
            load()
            _uiState.value = _uiState.value.copy(hfStatus = "پلاگین Hugging Face خاموش شد.")
        }
    }

    fun saveDrive() {
        val s = _uiState.value
        store.setDrive(s.driveClientId, s.driveClientSecret, s.driveRefreshToken, s.driveAccessToken)
        _uiState.value = s.copy(lastMessage = "تنظیمات گوگل درایو ذخیره شد.")
    }

    fun testGitHub() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true, githubStatus = "در حال بررسی...")
            val result = GitHubConnector.whoAmI(_uiState.value.githubToken)
            _uiState.value = _uiState.value.copy(
                busy = false,
                githubStatus = when (result) {
                    is ConnectorResult.Ok -> "متصل ✓  (کاربر: ${result.value})"
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

    fun testDrive() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true, driveStatus = "در حال بررسی...")
            val token = GoogleDriveConnector.ensureAccessToken(store)
            val status = when (token) {
                is ConnectorResult.Ok -> when (val about = GoogleDriveConnector.about(token.value)) {
                    is ConnectorResult.Ok -> "متصل ✓  (${about.value})"
                    is ConnectorResult.Err -> about.message
                }
                is ConnectorResult.Err -> token.message
            }
            load()
            _uiState.value = _uiState.value.copy(busy = false, driveStatus = status)
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
