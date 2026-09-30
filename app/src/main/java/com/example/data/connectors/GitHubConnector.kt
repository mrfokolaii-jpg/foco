package com.example.data.connectors

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Thin, dependency-free GitHub REST client.
 * Supports the operations FOKO needs: identity check, repo listing, file read/write
 * (used by the self-upgrade / knowledge-harvest pipeline) and code search.
 */
object GitHubConnector {

    private const val API = "https://api.github.com"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    private fun request(path: String, token: String): Request.Builder =
        Request.Builder()
            .url("$API$path")
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("X-GitHub-Api-Version", "2022-11-28")
            .addHeader("User-Agent", "FOKO-AI/2.0")
            .apply { if (token.isNotBlank()) addHeader("Authorization", "Bearer $token") }

    private fun mapError(code: Int, body: String): String = when (code) {
        401 -> "توکن گیت‌هاب نامعتبر است یا منقضی شده (۴۰۱)."
        403 -> "دسترسی گیت‌هاب رد شد یا محدودیت نرخ فعال شد (۴۰۳)."
        404 -> "مخزن یا مسیر مورد نظر در گیت‌هاب پیدا نشد (۴۰۴)."
        409 -> "تداخل در گیت‌هاب؛ فایل بین دو درخواست تغییر کرده است (۴۰۹)."
        422 -> "داده ارسالی به گیت‌هاب نامعتبر بود (۴۲۲)."
        in 500..599 -> "سرور گیت‌هاب موقتاً پاسخگو نیست."
        else -> {
            val msg = try {
                JSONObject(body).optString("message")
            } catch (_: Exception) {
                ""
            }
            msg.ifBlank { "خطای گیت‌هاب ($code)" }
        }
    }

    suspend fun whoAmI(token: String): ConnectorResult<String> = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext ConnectorResult.Err("توکن گیت‌هاب وارد نشده است.")
        try {
            val res = client.newCall(request("/user", token).get().build()).execute()
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) return@withContext ConnectorResult.Err(mapError(res.code, body), res.code)
            val login = JSONObject(body).optString("login")
            ConnectorResult.Ok(login)
        } catch (e: Exception) {
            ConnectorResult.Err("اتصال به گیت‌هاب برقرار نشد: ${e.localizedMessage}")
        }
    }

    suspend fun listRepos(token: String, query: String = ""): ConnectorResult<List<RemoteRepo>> =
        withContext(Dispatchers.IO) {
            if (token.isBlank()) return@withContext ConnectorResult.Err("توکن گیت‌هاب وارد نشده است.")
            try {
                val res = client.newCall(
                    request("/user/repos?per_page=100&sort=updated&affiliation=owner,collaborator", token).get().build()
                ).execute()
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful) return@withContext ConnectorResult.Err(mapError(res.code, body), res.code)
                val arr = JSONArray(body)
                val out = mutableListOf<RemoteRepo>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val name = o.optString("full_name")
                    if (query.isBlank() || name.contains(query, ignoreCase = true)) {
                        out.add(
                            RemoteRepo(
                                fullName = name,
                                defaultBranch = o.optString("default_branch", "main"),
                                isPrivate = o.optBoolean("private"),
                                htmlUrl = o.optString("html_url")
                            )
                        )
                    }
                }
                ConnectorResult.Ok(out)
            } catch (e: Exception) {
                ConnectorResult.Err("دریافت فهرست مخازن ناموفق بود: ${e.localizedMessage}")
            }
        }

    suspend fun getFile(
        token: String,
        owner: String,
        repo: String,
        path: String,
        branch: String
    ): ConnectorResult<RemoteFile?> = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext ConnectorResult.Err("توکن گیت‌هاب وارد نشده است.")
        try {
            val encodedPath = path.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8") }
            val url = "$API/repos/$owner/$repo/contents/$encodedPath?ref=${URLEncoder.encode(branch, "UTF-8")}"
            val res = client.newCall(request("", token).url(url).get().build()).execute()
            if (res.code == 404) return@withContext ConnectorResult.Ok(null)
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) return@withContext ConnectorResult.Err(mapError(res.code, body), res.code)
            val o = JSONObject(body)
            val b64 = o.optString("content").replace("\n", "")
            val decoded = try {
                String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
            } catch (_: Exception) {
                ""
            }
            ConnectorResult.Ok(RemoteFile(path = o.optString("path", path), sha = o.optString("sha"), content = decoded))
        } catch (e: Exception) {
            ConnectorResult.Err("خواندن فایل از گیت‌هاب ناموفق بود: ${e.localizedMessage}")
        }
    }

    /** Creates or updates a file. Returns the commit URL on success. */
    suspend fun putFile(
        token: String,
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String,
        branch: String
    ): ConnectorResult<String> = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext ConnectorResult.Err("توکن گیت‌هاب وارد نشده است.")
        try {
            val existing = getFile(token, owner, repo, path, branch)
            val sha = (existing as? ConnectorResult.Ok)?.value?.sha
            val payload = JSONObject().apply {
                put("message", message)
                put("content", Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
                put("branch", branch)
                if (!sha.isNullOrBlank()) put("sha", sha)
            }
            val encodedPath = path.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8") }
            val res = client.newCall(
                request("/repos/$owner/$repo/contents/$encodedPath", token)
                    .put(payload.toString().toRequestBody(JSON))
                    .build()
            ).execute()
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) return@withContext ConnectorResult.Err(mapError(res.code, body), res.code)
            val commit = JSONObject(body).optJSONObject("commit")
            ConnectorResult.Ok(commit?.optString("html_url").orEmpty())
        } catch (e: Exception) {
            ConnectorResult.Err("ثبت فایل در گیت‌هاب ناموفق بود: ${e.localizedMessage}")
        }
    }

    suspend fun searchCode(token: String, query: String): ConnectorResult<List<String>> =
        withContext(Dispatchers.IO) {
            if (token.isBlank()) return@withContext ConnectorResult.Err("توکن گیت‌هاب وارد نشده است.")
            try {
                val res = client.newCall(
                    request("/search/code?q=${URLEncoder.encode(query, "UTF-8")}", token).get().build()
                ).execute()
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful) return@withContext ConnectorResult.Err(mapError(res.code, body), res.code)
                val items = JSONObject(body).optJSONArray("items") ?: JSONArray()
                val out = mutableListOf<String>()
                for (i in 0 until minOf(items.length(), 10)) {
                    val o = items.getJSONObject(i)
                    out.add("${o.optString("repository")} :: ${o.optString("path")}")
                }
                ConnectorResult.Ok(out)
            } catch (e: Exception) {
                ConnectorResult.Err("جستجوی کد ناموفق بود: ${e.localizedMessage}")
            }
        }
}
