package com.example.ui.screens.connectors

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.PersianTurquoise
import kotlinx.coroutines.delay

/**
 * A connector the user can log into from inside the app.
 *
 * When the user ticks the checkbox next to GitHub / Hugging Face, FOKO opens the real
 * provider page in a built-in browser. The user signs in once with their normal account,
 * and FOKO watches the page for the freshly created access token, grabs it and stores it —
 * so the connection completes without the user copy-pasting anything.
 */
data class ConnectorAuthTarget(
    val id: String,
    val emoji: String,
    val title: String,
    val url: String,
    /** JavaScript regular expression used to recognise the token on the page. */
    val tokenRegex: String,
    val hint: String
) {
    companion object {
        val GITHUB = ConnectorAuthTarget(
            id = "github",
            emoji = "🐙",
            title = "GitHub",
            url = "https://github.com/settings/tokens/new?scopes=repo,workflow&description=FOKO%20AI",
            tokenRegex = "(ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})",
            hint = "با حساب گیت‌هاب وارد شو، بعد دکمه «Generate token» را بزن. فوکو توکن را خودکار برمی‌دارد و وصل می‌شود."
        )

        val HUGGING_FACE = ConnectorAuthTarget(
            id = "huggingface",
            emoji = "🤗",
            title = "Hugging Face",
            url = "https://huggingface.co/settings/tokens/new?tokenType=write&name=FOKO",
            tokenRegex = "(hf_[A-Za-z0-9]{20,})",
            hint = "وارد حساب Hugging Face شو و یک توکن با دسترسی Write بساز. فوکو آن را خودکار برمی‌دارد و وصل می‌شود."
        )

        fun byId(id: String): ConnectorAuthTarget? = when (id) {
            GITHUB.id -> GITHUB
            HUGGING_FACE.id -> HUGGING_FACE
            else -> null
        }
    }
}

/** Result of a successful in-app login. */
data class ConnectorAuthResult(
    val target: ConnectorAuthTarget,
    val token: String
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ConnectorAuthDialog(
    target: ConnectorAuthTarget,
    onDismiss: () -> Unit,
    onTokenDetected: (String) -> Unit
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var status by remember { mutableStateOf("در حال باز کردن صفحه ورود…") }
    var manualToken by remember { mutableStateOf("") }
    var captured by remember { mutableStateOf(false) }

    val extractScript = remember(target) { buildTokenExtractScript(target) }

    // Watch the page: as soon as a provider token shows up, hand it to the caller.
    LaunchedEffect(webView) {
        val wv = webView ?: return@LaunchedEffect
        while (true) {
            delay(1200)
            if (captured) continue
            try {
                wv.evaluateJavascript(extractScript) { raw ->
                    val token = parseJsString(raw)
                    if (token.isNotBlank() && !captured) {
                        captured = true
                        status = "توکن پیدا شد؛ در حال اتصال…"
                        onTokenDetected(token)
                    }
                }
            } catch (_: Exception) {
                // The page may be mid-navigation — just keep polling.
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                webView?.stopLoading()
                webView?.destroy()
            } catch (_: Exception) {
            }
            webView = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ---- Header ----------------------------------------------------------
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = target.emoji, fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "اتصال به ${target.title}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = status,
                                style = MaterialTheme.typography.labelSmall,
                                color = PersianTurquoise
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "بستن")
                        }
                    }
                }

                // ---- Hint ------------------------------------------------------------
                Text(
                    text = target.hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )

                // ---- Built-in browser ------------------------------------------------
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.White)
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(
                                        view: WebView?,
                                        url: String?,
                                        favicon: Bitmap?
                                    ) {
                                        if (!captured) status = "در حال بارگذاری…"
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        if (!captured) status = "وارد حساب شو و توکن بساز — فوکو خودکار وصل می‌شود"
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean = false
                                }

                                loadUrl(target.url)
                                webView = this
                            }
                        },
                        update = { }
                    )

                    if (captured) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier.padding(18.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = PersianTurquoise
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("در حال اتصال…", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // ---- Manual fallback -------------------------------------------------
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "اگر اتصال خودکار انجام نشد، توکن را دستی بچسبان:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = manualToken,
                                onValueChange = { manualToken = it },
                                label = { Text("توکن ${target.title}") },
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = PersianTurquoise,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (manualToken.isNotBlank()) {
                                        captured = true
                                        onTokenDetected(manualToken.trim())
                                    }
                                },
                                enabled = manualToken.isNotBlank(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PersianTurquoise)
                            ) {
                                Text("اتصال", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    captured = false
                                    status = "در حال بارگذاری مجدد…"
                                    webView?.reload()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "بارگذاری مجدد",
                                    tint = PersianTurquoise,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "بارگذاری مجدد صفحه ورود",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Builds the JavaScript that scans the loaded page for a provider access token. */
private fun buildTokenExtractScript(target: ConnectorAuthTarget): String {
    val pattern = target.tokenRegex
    return """
        (function () {
          try {
            var pat = /$pattern/;
            var chunks = [];
            var fields = document.querySelectorAll('input, textarea');
            for (var i = 0; i < fields.length; i++) {
              var v = fields[i].value;
              if (v) { chunks.push(v); }
            }
            var blocks = document.querySelectorAll('code, pre, samp, kbd');
            for (var j = 0; j < blocks.length; j++) {
              var t = blocks[j].textContent;
              if (t) { chunks.push(t); }
            }
            if (document.body && document.body.innerText) {
              chunks.push(document.body.innerText);
            }
            var m = chunks.join('\n').match(pat);
            return m ? m[0] : '';
          } catch (e) {
            return '';
          }
        })();
    """.trimIndent()
}

/** `evaluateJavascript` returns a JSON-encoded value — unwrap the plain string. */
private fun parseJsString(raw: String?): String {
    if (raw.isNullOrBlank() || raw == "null") return ""
    val trimmed = raw.trim()
    return if (trimmed.length >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
        trimmed.substring(1, trimmed.length - 1)
            .replace("\\\"", "\"")
            .replace("\\/", "/")
    } else {
        trimmed
    }
}
