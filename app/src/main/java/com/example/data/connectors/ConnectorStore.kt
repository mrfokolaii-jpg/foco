package com.example.data.connectors

import android.content.Context
import android.content.SharedPreferences

class ConnectorStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun githubToken(): String = prefs.getString(K_GH_TOKEN, "").orEmpty().trim()
    fun githubOwner(): String = prefs.getString(K_GH_OWNER, DEFAULT_GH_OWNER).orEmpty().trim()
    fun githubRepo(): String = prefs.getString(K_GH_REPO, DEFAULT_GH_REPO).orEmpty().trim()
    fun githubBranch(): String = prefs.getString(K_GH_BRANCH, DEFAULT_GH_BRANCH).orEmpty().trim()
    fun githubClientId(): String = prefs.getString(K_GH_CLIENT_ID, "").orEmpty().trim()

    fun setGitHub(token: String, owner: String, repo: String, branch: String) = prefs.edit()
        .putString(K_GH_TOKEN, token.trim())
        .putString(K_GH_OWNER, owner.trim().ifBlank { DEFAULT_GH_OWNER })
        .putString(K_GH_REPO, repo.trim().ifBlank { DEFAULT_GH_REPO })
        .putString(K_GH_BRANCH, branch.trim().ifBlank { DEFAULT_GH_BRANCH })
        .apply()

    fun setGitHubToken(token: String) = prefs.edit().putString(K_GH_TOKEN, token.trim()).apply()
    fun setGitHubClientId(clientId: String) = prefs.edit().putString(K_GH_CLIENT_ID, clientId.trim()).apply()

    fun hfToken(): String = prefs.getString(K_HF_TOKEN, "").orEmpty().trim()
    fun setHfToken(token: String) = prefs.edit().putString(K_HF_TOKEN, token.trim()).apply()

    fun cloudflareToken(): String = prefs.getString(K_CF_TOKEN, "").orEmpty().trim()
    fun cloudflareAccountId(): String = prefs.getString(K_CF_ACCOUNT_ID, "").orEmpty().trim()
    fun setCloudflare(token: String, accountId: String = "") = prefs.edit()
        .putString(K_CF_TOKEN, token.trim())
        .putString(K_CF_ACCOUNT_ID, accountId.trim())
        .apply()

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
        githubConfigured = githubOwner().isNotBlank() && githubRepo().isNotBlank(),
        githubTokenPresent = githubToken().isNotBlank(),
        huggingFaceConfigured = hfToken().isNotBlank(),
        cloudflareConfigured = cloudflareToken().isNotBlank(),
        autoUpgradeEnabled = isAutoUpgradeEnabled(),
        continuousVoiceEnabled = isContinuousVoiceEnabled()
    )

    companion object {
        private const val PREFS = "foko_connectors"
        private const val K_GH_TOKEN = "github_token"
        private const val K_GH_OWNER = "github_owner"
        private const val K_GH_REPO = "github_repo"
        private const val K_GH_BRANCH = "github_branch"
        private const val K_GH_CLIENT_ID = "github_client_id"
        private const val K_HF_TOKEN = "hf_token"
        private const val K_CF_TOKEN = "cf_token"
        private const val K_CF_ACCOUNT_ID = "cf_account_id"
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
    val githubTokenPresent: Boolean = false,
    val huggingFaceConfigured: Boolean = false,
    val cloudflareConfigured: Boolean = false,
    val autoUpgradeEnabled: Boolean = true,
    val continuousVoiceEnabled: Boolean = true
)
