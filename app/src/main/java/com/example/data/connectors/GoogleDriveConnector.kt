package com.example.data.connectors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Google Drive v3 client.
 *
 * Auth model: the user supplies an OAuth client id + secret + refresh token (or a
 * short-lived access token). FOKO exchanges the refresh token for an access token
 * automatically and caches it back into [ConnectorStore].
 */
object GoogleDriveConnector {

    private const val DRIVE = "https://www.googleapis.com/drive/v3"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    /** Returns a usable access token, refreshing it when only a refresh token is stored. */
    suspend fun ensureAccessToken(store: ConnectorStore): ConnectorResult<String> =
        withContext(Dispatchers.IO) {
            val existing = store.driveAccessToken()
            if (existing.isNotBlank()) return@withContext ConnectorResult.Ok(existing)

            val refresh = store.driveRefreshToken()
            val clientId = store.driveClientId()
            val clientSecret = store.driveClientSecret()
            if (refresh.isBlank() || clientId.isBlank() || clientSecret.isBlank()) {
                return@withContext ConnectorResult.Err(
                    "برای گوگل درایو، شناسه کلاینت، رمز کلاینت و Refresh Token لازم است."
                )
            }
            try {
                val form = okhttp3.FormBody.Builder()
                    .add("client_id", clientId)
                    .add("client_secret", clientSecret)
                    .add("refresh_token", refresh)
                    .add("grant_type", "refresh_token")
                    .build()
                val res = client.newCall(
                    Request.Builder()
                        .url("https://oauth2.googleapis.com/token")
                        .post(form)
                        .build()
                ).execute()
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    return@withContext ConnectorResult.Err("دریافت توکن گوگل ناموفق بود (${res.code}).", res.code)
                }
                val token = JSONObject(body).optString("access_token")
                if (token.isBlank()) return@withContext ConnectorResult.Err("توکن گوگل خالی برگشت.")
                store.setDriveAccessToken(token)
                ConnectorResult.Ok(token)
            } catch (e: Exception) {
                ConnectorResult.Err("اتصال به گوگل برقرار نشد: ${e.localizedMessage}")
            }
        }

    suspend fun about(token: String): ConnectorResult<String> = withContext(Dispatchers.IO) {
        try {
            val res = client.newCall(
                Request.Builder()
                    .url("$DRIVE/about?fields=user(displayName,emailAddress)")
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            ).execute()
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) return@withContext ConnectorResult.Err(errorFor(res.code), res.code)
            val user = JSONObject(body).optJSONObject("user")
            ConnectorResult.Ok(user?.optString("emailAddress").orEmpty().ifBlank { user?.optString("displayName").orEmpty() })
        } catch (e: Exception) {
            ConnectorResult.Err("اتصال به گوگل درایو برقرار نشد: ${e.localizedMessage}")
        }
    }

    suspend fun listFiles(token: String, query: String = ""): ConnectorResult<List<RemoteDriveFile>> =
        withContext(Dispatchers.IO) {
            try {
                val q = if (query.isBlank()) "trashed=false"
                else "trashed=false and name contains '${query.replace("'", "")}'"
                val url = "$DRIVE/files?pageSize=30&orderBy=modifiedTime desc" +
                    "&fields=files(id,name,mimeType,size)&q=${URLEncoder.encode(q, "UTF-8")}"
                val res = client.newCall(
                    Request.Builder().url(url).addHeader("Authorization", "Bearer $token").build()
                ).execute()
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful) return@withContext ConnectorResult.Err(errorFor(res.code), res.code)
                val files = JSONObject(body).optJSONArray("files") ?: JSONArray()
                val out = mutableListOf<RemoteDriveFile>()
                for (i in 0 until files.length()) {
                    val o = files.getJSONObject(i)
                    out.add(
                        RemoteDriveFile(
                            id = o.optString("id"),
                            name = o.optString("name"),
                            mimeType = o.optString("mimeType"),
                            sizeBytes = o.optString("size").toLongOrNull() ?: 0L
                        )
                    )
                }
                ConnectorResult.Ok(out)
            } catch (e: Exception) {
                ConnectorResult.Err("دریافت فایل‌های درایو ناموفق بود: ${e.localizedMessage}")
            }
        }

    suspend fun downloadFile(token: String, fileId: String): ConnectorResult<String> =
        withContext(Dispatchers.IO) {
            try {
                val res = client.newCall(
                    Request.Builder()
                        .url("$DRIVE/files/$fileId?alt=media")
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                ).execute()
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful) return@withContext ConnectorResult.Err(errorFor(res.code), res.code)
                ConnectorResult.Ok(body)
            } catch (e: Exception) {
                ConnectorResult.Err("دانلود از درایو ناموفق بود: ${e.localizedMessage}")
            }
        }

    suspend fun uploadTextFile(
        token: String,
        name: String,
        content: String,
        mimeType: String = "text/plain"
    ): ConnectorResult<String> = withContext(Dispatchers.IO) {
        try {
            val metadata = JSONObject().apply { put("name", name) }
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addPart(metadata.toString().toRequestBody(JSON))
                .addPart(content.toRequestBody(mimeType.toMediaType()))
                .build()
            val res = client.newCall(
                Request.Builder()
                    .url("$UPLOAD/files?uploadType=multipart&fields=id,name,webViewLink")
                    .addHeader("Authorization", "Bearer $token")
                    .post(body)
                    .build()
            ).execute()
            val resBody = res.body?.string().orEmpty()
            if (!res.isSuccessful) return@withContext ConnectorResult.Err(errorFor(res.code), res.code)
            val o = JSONObject(resBody)
            ConnectorResult.Ok(o.optString("webViewLink").ifBlank { o.optString("id") })
        } catch (e: Exception) {
            ConnectorResult.Err("بارگذاری در درایو ناموفق بود: ${e.localizedMessage}")
        }
    }

    private fun errorFor(code: Int): String = when (code) {
        401 -> "توکن گوگل منقضی شده است. یک توکن تازه ثبت کنید یا Refresh Token بدهید."
        403 -> "دسترسی گوگل درایو رد شد (۴۰۳). محدوده دسترسی (scope) را بررسی کنید."
        404 -> "فایل مورد نظر در گوگل درایو پیدا نشد."
        in 500..599 -> "سرور گوگل موقتاً پاسخگو نیست."
        else -> "خطای گوگل درایو ($code)."
    }
}
