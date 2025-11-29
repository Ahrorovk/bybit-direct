package com.example.bybit_direct

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@SuppressLint("SetJavaScriptEnabled")
class MainActivity : ComponentActivity() {

    private val adminUrl by lazy { getString(R.string.admin_url) }
    private val projectParam by lazy { getString(R.string.app_name) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Осторожно: операции сети выполняются в корутинах (IO)
        setContent {
            Surface(modifier = Modifier.fillMaxSize()) {
                LauncherScreen(
                    adminUrl = adminUrl,
                    projectParam = projectParam
                )
            }
        }
    }
}

@Composable
fun LauncherScreen(adminUrl: String, projectParam: String) {
    var loading by remember { mutableStateOf(true) }
    var targetUrl by remember { mutableStateOf<String?>(null) }
    var customUA by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(key1 = adminUrl, key2 = projectParam) {
        loading = true
        errorMsg = null

        val urlWithParams = buildAdminRequestUrl(adminUrl, projectParam)

        try {
            Log.e("LauncherScreen", "Fetching admin URL: $urlWithParams")
            val raw = fetchJsonNoCache(urlWithParams)
            val parts = raw.split("***")
            Log.e("LauncherScreen", "Fetching admin URL: $urlWithParams \n got response: $raw")

            if (parts.size == 2) {
                val ua = parts[0].trim()
                val url = parts[1].trim()

                customUA = if (ua.isBlank()) null else ua
                targetUrl = url
            } else {
                errorMsg = "Invalid response format from admin"
            }

        } catch (t: Throwable) {
            errorMsg = "Network error: ${t.message}"
        } finally {
            loading = false
        }
    }


    when {
        loading -> {
            // Простая загрузка
            Surface(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.fillMaxSize())
            }
        }
        errorMsg != null -> {
            Surface(modifier = Modifier.fillMaxSize()) {
                Text(text = errorMsg ?: "Unknown error")
            }
        }
        targetUrl != null -> {
            // Встраиваем WebView
            AndroidView(factory = { context ->
                WebView(context).apply {

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.setSupportMultipleWindows(true)
                    WebView.setWebContentsDebuggingEnabled(true)

                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    // Установим custom UA если пришёл
                    if (!customUA.isNullOrBlank()) {
                        settings.userAgentString = customUA
                    }
                    // WebViewClient чтобы обрабатывать редиректы внутри WebView
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            // Все ссылки открываем внутри WebView (поддержка редиректов)
                            request?.url?.let { view?.loadUrl(it.toString()) }
                            return true
                        }
                    }
                    // Загружаем целевой URL
                    loadUrl(targetUrl!!)
                }
            }, update = { webView ->
                // Если recompose с новым UA/URL — применим
                if (!customUA.isNullOrBlank()) {
                    webView.settings.userAgentString = customUA
                }
                webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
                // Не кэшируем вебview
                webView.clearCache(true)
            })
        }
        else -> {
            Surface(modifier = Modifier.fillMaxSize()) {
                Text(text = "No URL to open")
            }
        }
    }
}

private fun buildAdminRequestUrl(base: String, projectParam: String): String {
    val encodedProject = URLEncoder.encode(projectParam, "UTF-8")
    val sep = if (base.contains("?")) "&" else "?"
    return "$base${sep}appname=$encodedProject"
}


suspend fun fetchJsonNoCache(urlStr: String): String = withContext(Dispatchers.IO) {
    val url = URL(urlStr)
    val conn = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 10_000
        instanceFollowRedirects = true // следовать редиректам при запросе админки
        useCaches = false
        requestMethod = "GET"
        setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
        setRequestProperty("Pragma", "no-cache")
        setRequestProperty("Expires", "0")
        // Можно отправить заголовок с project, но мы уже передали в GET
    }

    try {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { br ->
            val sb = StringBuilder()
            var line: String? = br.readLine()
            while (line != null) {
                sb.append(line)
                line = br.readLine()
            }
            sb.toString()
        }
    } finally {
        conn.disconnect()
    }
}