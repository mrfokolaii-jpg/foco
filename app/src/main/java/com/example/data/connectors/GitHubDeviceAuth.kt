package com.example.data.connectors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * GitHub OAuth Device Authorization Grant.
 * User does not paste a token; they approve in the browser once.
 *
 * Requires a GitHub OAuth App Client ID (create at
 * https://github.com/settings/applications/new — enable Device Flow).
 */
object GitHubDeviceAuth {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val intervalSec: Int,
        val expiresInSec: Int
    )

    suspend fun requestDeviceCode(clientId: String, scope: String = "repo"): ConnectorResult<DeviceCode> =
        withContext(Dispatchers.IO) {
            if (clientId.isBlank()) {
                return@withContext ConnectorResult.Err(
                    "Client ID گیت‌هاب تنظیم نشده. یک OAuth App بساز و Client ID را وارد کن."
                )
            }
            try {
                val body = FormBody.Builder()
                    .add("client_id", clientId)
                    .add("scope", scope)
                    .build()
                val res = client.newCall(
                    Request.Builder()
                        .url("https://github.com/login/device/code")
                        .header("Accept", "application/json")
                        .post(body)
                        .build()
                ).execute()
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    return@withContext ConnectorResult.Err("درخواست کد دستگاه ناموفق (${res.code}): $text")
                }
                val o = JSONObject(text)
                ConnectorResult.Ok(
                    DeviceCode(
                        deviceCode = o.getString("device_code"),
                        userCode = o.getString("user_code"),
                        verificationUri = o.optString("verification_uri", "https://github.com/login/device"),
                        intervalSec = o.optInt("interval", 5),
                        expiresInSec = o.optInt("expires_in", 900)
                    )
                )
            } catch (e: Exception) {
                ConnectorResult.Err("خطا در شروع اتصال گیت‌هاب: ${e.localizedMessage}")
            }
        }

    /**
     * Poll until the user finishes authorization in the browser, or timeout.
     */
    suspend fun pollForToken(
        clientId: String,
        deviceCode: String,
        intervalSec: Int,
        maxAttempts: Int = 60,
        onWaiting: suspend (attempt: Int) -> Unit = {}
    ): ConnectorResult<String> = withContext(Dispatchers.IO) {
        var interval = intervalSec.coerceAtLeast(5)
        repeat(maxAttempts) { attempt ->
            onWaiting(attempt + 1)
            delay(interval * 1000L)
            try {
                val body = FormBody.Builder()
                    .add("client_id", clientId)
                    .add("device_code", deviceCode)
                    .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                    .build()
                val res = client.newCall(
                    Request.Builder()
                        .url("https://github.com/login/oauth/access_token")
                        .header("Accept", "application/json")
                        .post(body)
                        .build()
                ).execute()
                val text = res.body?.string().orEmpty()
                val o = JSONObject(text)
                when {
                    o.has("access_token") -> {
                        val token = o.getString("access_token")
                        return@withContext ConnectorResult.Ok(token)
                    }
                    o.optString("error") == "authorization_pending" -> { /* keep polling */ }
                    o.optString("error") == "slow_down" -> { interval += 5 }
                    o.optString("error") == "expired_token" ->
                        return@withContext ConnectorResult.Err("کد منقضی شد. دوباره تلاش کن.")
                    o.optString("error") == "access_denied" ->
                        return@withContext ConnectorResult.Err("دسترسی توسط کاربر رد شد.")
                    else -> {
                        val err = o.optString("error_description", o.optString("error", text))
                        if (err.isNotBlank() && !err.contains("pending"))
                            return@withContext ConnectorResult.Err(err)
                    }
                }
            } catch (e: Exception) {
                // transient network — keep trying
            }
        }
        ConnectorResult.Err("زمان اتصال تمام شد. دوباره تلاش کن.")
    }
}
