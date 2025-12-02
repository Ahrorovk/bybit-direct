package com.example.bybit_direct

import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.web.AccompanistWebChromeClient
import com.google.accompanist.web.AccompanistWebViewClient
import com.google.accompanist.web.WebView
import com.google.accompanist.web.rememberWebViewNavigator
import com.google.accompanist.web.rememberWebViewState

@Composable
fun HybridWebView(url: String, userAgent: String) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // 2. Добавляем наблюдатель для отслеживания событий жизненного цикла
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // Когда приложение переходит в фоновый режим (или activity/fragment останавливается)
                Lifecycle.Event.ON_STOP -> {
                    // !!! ПРИНУДИТЕЛЬНОЕ СОХРАНЕНИЕ КУКИ НА ДИСК !!!
                    CookieManager.getInstance().flush()
                    // Начиная с Android 11 (API 30), нужно использовать flush
                }
                else -> {}
            }
        }

        // Регистрируем наблюдателя
        lifecycleOwner.lifecycle.addObserver(observer)

        // Удаляем наблюдателя, когда компонент покидает композицию
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val state = rememberWebViewState(url)
    val navigator = rememberWebViewNavigator()

    val client = remember {
        object : AccompanistWebViewClient() {
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                handler?.proceed()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url.toString()
                val context = view?.context ?: return false

                if (url.startsWith("tg://") || url.startsWith("tg:") ||
                    url.startsWith("intent://") || url.startsWith("mailto:") ||
                    url.startsWith("tel:")) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                    }
                    return true
                }

                if (url.contains("vk.com") || url.contains("ok.ru") ||
                    url.contains("facebook.com") || url.contains("instagram.com") ||
                    url.contains("twitter.com") || url.contains("youtube.com")) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    return true
                }

                return false
            }
        }
    }
    WebView(
        state = state,
        navigator = navigator,
        onCreated = { webView ->
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false

                useWideViewPort = true
                loadWithOverviewMode = true
                allowContentAccess = true

                userAgentString = userAgent

                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
            }
        },
        client = client
    )
}
