package com.example.bybit_direct

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    private val projectParam by lazy { getString(R.string.app_name_admin) }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CookieManager.getInstance().setAcceptCookie(true)
        setContent {
            Surface(modifier = Modifier.fillMaxSize()) {
                LauncherScreen(adminUrl, projectParam)
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@SuppressLint("SetJavaScriptEnabled", "ContextCastToActivity")
@Composable
fun LauncherScreen(adminUrl: String, projectParam: String) {
    var hasError by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var targetUrl by remember { mutableStateOf<String?>(null) }
    var customUA by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
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

                customUA = ua.ifBlank { null }
                targetUrl = url
            } else {
                errorMsg = "Invalid response format from admin"
            }

        } catch (t: Throwable) {
            errorMsg = "Network error: ${t.message}"
            hasError = true
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

        errorMsg != null || hasError -> {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Проблемы с интернетом", modifier = Modifier.padding(bottom = 8.dp))
                    Text(
                        "Пожалуйста, проверьте интернет соединение или отключите VPN",
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Text("Попробовать снова")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Подпишитесь на телеграм канал чтобы отслеживать доступность сайта")
                }
            }
        }

        targetUrl != null -> {

            HybridWebView(
                url = targetUrl!!,
                customUA
                    ?: "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36"
            )

        }

        else -> {
            Surface(modifier = Modifier.fillMaxSize()) {
                Text(text = "No URL to open")
            }
        }
    }
}

fun buildAdminRequestUrl(base: String, projectParam: String): String {
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
