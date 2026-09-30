package com.example.data.connectors

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists the credentials and target settings for every external connector.
 * All values live in one SharedPreferences file so the connectors screen and the
 * background overlay service read the exact same state.
 */
class ConnectorStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- GitHub -------------------------------------------------------------
    fun githubToken(): String = prefs.getString(K_GH_TOKEN, "").orEmpty().trim()
    fun githubOwner(): String = prefs.getString(K_GH_OWNER, DEFAULT_GH_OWNER).orEmpty().trim()
    fun githubRepo(): String = prefs.getString(K_GH_REPO, DEFAULT_GH_REPO).orEmpty().trim()
    fun githubBranch(): String = prefs.getString(K_GH_BRANCH, DEFAULT_GH_BRANCH).orEmpty().trim()
    fun setGitHub(token: String, owner: String, repo: String, branch: String) = prefs.edit()
        .putString(K_GH_TOKEN, token.trim())
        .putString(K_GH_OWNER, owner.trim().ifBlank { DEFAULT_GH_OWNER })
        .putString(K_GH_REPO, repo.trim().ifBlank { DEFAULT_GH_REPO })
        .putString(K_GH_BRANCH, branch.trim().ifBlank { DEFAULT_GH_BRANCH })
        .apply()

    // ---- Hugging Face -------------------------------------------------------
    fun hfToken(): String = prefs.getString(K_HF_TOKEN, "").orEmpty().trim()
    fun setHfToken(token: String) = prefs.edit().putString(K_HF_TOKEN, token.trim()).apply()

    // ---- Cloudflare ---------------------------------------------------------
    fun cloudflareToken(): String = prefs.getString(K_CF_TOKEN, "").orEmpty().trim()
    fun cloudflareAccountId(): String = prefs.getString(K_CF_ACCOUNT_ID, "").orEmpty().trim()
    fun setCloudflare(token: String, accountId: String = "") = prefs.edit()
        .putString(K_CF_TOKEN, token.trim())
        .putString(K_CF_ACCOUNT_ID, accountId.trim())
        .apply()

    // ---- Google Drive -------------------------------------------------------
    fun driveClientId(): String = prefs.getString(K_DRIVE_CLIENT_ID, "").orEmpty().trim()
    fun driveClientSecret(): String = prefs.getString(K_DRIVE_CLIENT_SECRET, "").orEmpty().trim()
    fun driveRefreshToken(): String = prefs.getString(K_DRIVE_REFRESH, "").orEmpty().trim()
    fun driveAccessToken(): String = prefs.getString(K_DRIVE_ACCESS, "").orEmpty().trim()
    fun setDrive(clientId: String, clientSecret: String, refreshToken: String, accessToken: String) =
        prefs.edit()
            .putString(K_DRIVE_CLIENT_ID, clientId.trim())
            .putString(K_DRIVE_CLIENT_SECRET, clientSecret.trim())
            .putString(K_DRIVE_REFRESH, refreshToken.trim())
            .putString(K_DRIVE_ACCESS, accessToken.trim())
            .apply()

    fun setDriveAccessToken(token: String) =
        prefs.edit().putString(K_DRIVE_ACCESS, token.trim()).apply()

    // ---- Overlay / behaviour preferences -----------------------------------
    fun isAutoUpgradeEnabled(): Boolean = prefs.getBoolean(K_AUTO_UPGRADE, true)
    fun setAutoUpgradeEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(K_AUTO_UPGRADE, enabled).apply()

    fun isContinuousVoiceEnabled(): Boolean = prefs.getBoolean(K_CONTINUOUS_VOICE, true)
    fun setContinuousVoiceEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(K_CONTINUOUS_VOICE, enabled).apply()

    fun isOverlayEnabled(): Boolean = prefs.getBoolean(K_OVERLAY_ENABLED, false)
    fun setOverlayEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(K_OVERLAY_ENABLED, enabled).apply()

    fun snapshot(): ConnectorSnapshot = ConnectorSnapshot(
        githubConfigured = githubToken().isNotBlank(),
        huggingFaceConfigured = hfToken().isNotBlank(),
        cloudflareConfigured = cloudflareToken().isNotBlank(),
        driveConfigured = driveAccessToken().isNotBlank() || driveRefreshToken().isNotBlank(),
        autoUpgradeEnabled = isAutoUpgradeEnabled(),
        continuousVoiceEnabled = isContinuousVoiceEnabled()
    )

    companion object {
        private const val PREFS = "foko_connectors"
        private const val K_GH_TOKEN = "github_token"
        private const val K_GH_OWNER = "github_owner"
        private const val K_GH_REPO = "github_repo"
        private const val K_GH_BRANCH = "github_branch"
        private const val K_HF_TOKEN = "hf_token"
        private const val K_CF_TOKEN = "cf_token"
        private const val K_CF_ACCOUNT_ID = "cf_account_id"
        private const val K_DRIVE_CLIENT_ID = "drive_client_id"
        private const val K_DRIVE_CLIENT_SECRET = "drive_client_secret"
        private const val K_DRIVE_REFRESH = "drive_refresh"
        private const val K_DRIVE_ACCESS = "drive_access"
        private const val K_AUTO_UPGRADE = "auto_upgrade"
        private const val K_CONTINUOUS_VOICE = "continuous_voice"
        private const val K_OVERLAY_ENABLED = "overlay_enabled"

        const val DEFAULT_GH_OWNER = "mrfokolaii-jpg"
        const val DEFAULT_GH_REPO = "foco"
        const val DEFAULT_GH_BRANCH = "main"
    }
}

data class ConnectorSnapshot(
    val githubConfigured: Boolean = false,
    val huggingFaceConfigured: Boolean = false,
    val cloudflareConfigured: Boolean = false,
    val driveConfigured: Boolean = false,
    val autoUpgradeEnabled: Boolean = true,
    val continuousVoiceEnabled: Boolean = true
)
