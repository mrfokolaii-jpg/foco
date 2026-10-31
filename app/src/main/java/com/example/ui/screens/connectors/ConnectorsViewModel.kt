package com.example.ui.screens.connectors

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.connectors.ConnectorResult
import com.example.data.connectors.ConnectorStore
import com.example.data.connectors.GitHubConnector
import com.example.data.connectors.GitHubDeviceAuth
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
    val githubClientId: String = ConnectorStore.DEFAULT_GH_CLIENT_ID,
    val hfToken: String = "",
    val autoUpgrade: Boolean = true,
    val continuousVoice: Boolean = true,
    val overlayRunning: Boolean = false,
    val githubStatus: String? = null,
    val hfStatus: String? = null,
    val deviceUserCode: String? = null,
    val deviceVerificationUri: String? = null,
    val deviceAuthInProgress: Boolean = false,
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
            githubClientId = store.githubClientId(),
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
            val result = GitHubConnector.checkRepo(s.githubToken, s.githubOwner, s.githubRepo)
            _uiState.value = _uiState.value.copy(
                busy = false,
                githubStatus = when (result) {
                    is ConnectorResult.Ok -> {
                        val writeHint = if (s.githubToken.isBlank()) " — فقط خواندن" else " — خواندن و نوشتن"
                        "متصل ✓  ${result.value}$writeHint"
                    }
                    is ConnectorResult.Err -> result.message
                }
            )
        }
    }

    fun startGitHubDeviceLogin() {
        viewModelScope.launch {
            val clientId = store.githubClientId().ifBlank { ConnectorStore.DEFAULT_GH_CLIENT_ID }
            _uiState.value = _uiState.value.copy(
                deviceAuthInProgress = true,
                githubStatus = "در حال دریافت کد دستگاه...",
                deviceUserCode = null,
                deviceVerificationUri = null
            )
            when (val codeResult = GitHubDeviceAuth.requestDeviceCode(clientId, scope = "repo")) {
                is ConnectorResult.Err -> {
                    _uiState.value = _uiState.value.copy(
                        deviceAuthInProgress = false,
                        githubStatus = codeResult.message
                    )
                }
                is ConnectorResult.Ok -> {
                    val dc = codeResult.value
                    _uiState.value = _uiState.value.copy(
                        deviceUserCode = dc.userCode,
                        deviceVerificationUri = dc.verificationUri,
                        githubStatus = "کد را در مرورگر وارد کن: ${dc.userCode}"
                    )
                    val tokenResult = GitHubDeviceAuth.pollForToken(
                        clientId = clientId,
                        deviceCode = dc.deviceCode,
                        intervalSec = dc.intervalSec
                    ) { attempt ->
                        _uiState.value = _uiState.value.copy(
                            githubStatus = "منتظر تأیید در مرورگر... ($attempt)"
                        )
                    }
                    when (tokenResult) {
                        is ConnectorResult.Ok -> {
                            store.setGitHubToken(tokenResult.value)
                            val s = _uiState.value
                            store.setGitHub(tokenResult.value, s.githubOwner, s.githubRepo, s.githubBranch)
                            _uiState.value = _uiState.value.copy(
                                githubToken = tokenResult.value,
                                deviceAuthInProgress = false,
                                deviceUserCode = null,
                                deviceVerificationUri = null,
                                githubStatus = "متصل ✓  توکن با دسترسی repo دریافت شد",
                                lastMessage = "ورود گیت‌هاب موفق بود."
                            )
                        }
                        is ConnectorResult.Err -> {
                            _uiState.value = _uiState.value.copy(
                                deviceAuthInProgress = false,
                                deviceUserCode = null,
                                deviceVerificationUri = null,
                                githubStatus = tokenResult.message
                            )
                        }
                    }
                }
            }
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
