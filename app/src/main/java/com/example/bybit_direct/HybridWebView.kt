package com.example.bybit_direct

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.util.Log
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.web.AccompanistWebViewClient
import com.google.accompanist.web.WebView
import com.google.accompanist.web.rememberWebViewNavigator
import com.google.accompanist.web.rememberWebViewState

@SuppressLint("JavascriptInterface", "SetJavaScriptEnabled")
@Composable
fun HybridWebView(url: String, userAgent: String) {
    val context = LocalContext.current
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

    // Инициализация Matomo Tag Manager
    val matomoIntegration = remember {
        val matomoUrl = context.getString(R.string.matomo_url)
        val containerId = context.getString(R.string.matomo_container_id)
        val siteId = context.getString(R.string.matomo_site_id)
        val appVersion = DeviceInfoHelper.getAppVersion(context)
        
        MatomoTagManagerIntegration(
            context = context,
            matomoUrl = matomoUrl,
            containerId = containerId,
            siteId = siteId,
            appVersion = appVersion
        )
    }

    val client = remember {
        object : AccompanistWebViewClient() {
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                handler?.proceed()
            }
            
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): android.webkit.WebResourceResponse? {
                val url = request?.url?.toString() ?: ""
                // Логируем только запросы к Matomo (НЕ инжектируем здесь - это замедляет!)
                if (url.contains("index.php") || url.contains("container_")) {
                    Log.d("HybridWebView", "🔵 Matomo Request intercepted: $url")
                }
                // ВАЖНО: Не блокируем запросы, позволяем загружать все ресурсы
                return super.shouldInterceptRequest(view, request)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                val url = request?.url?.toString() ?: ""
                // Логируем ошибки загрузки ресурсов (включая изображения)
                if (url.contains(".jpg") || url.contains(".jpeg") || url.contains(".png") || 
                    url.contains(".gif") || url.contains(".webp") || url.contains(".svg") ||
                    url.contains("logo") || url.contains("image")) {
                    Log.e("HybridWebView", "❌ Error loading image: $url, Error: ${error?.description}")
                } else {
                    Log.e("HybridWebView", "❌ Error loading resource: $url, Error: ${error?.description}")
                }
            }
            
            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                val url = request?.url?.toString() ?: ""
                // Логируем HTTP ошибки загрузки ресурсов
                if (url.contains(".jpg") || url.contains(".jpeg") || url.contains(".png") || 
                    url.contains(".gif") || url.contains(".webp") || url.contains(".svg") ||
                    url.contains("logo") || url.contains("image")) {
                    Log.e("HybridWebView", "❌ HTTP Error loading image: $url, Status: ${errorResponse?.statusCode}")
                } else {
                    Log.e("HybridWebView", "❌ HTTP Error loading resource: $url, Status: ${errorResponse?.statusCode}")
                }
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?){
                super.onPageStarted(view, url, favicon)
                view.let { webView ->
                    val deviceData = DeviceInfoHelper.createDeviceData(context, userAgent)
                    matomoIntegration.injectIntoWebView(webView, deviceData)
                    Log.d("HybridWebView", "Matomo Tag Manager injection started for URL: $url")
                }
            }

            override fun onPageFinished(view: WebView, pageUrl: String?) {
                super.onPageFinished(view, pageUrl)
                // Повторно инжектируем на случай, если первая инъекция не сработала
                val webView = view
                view.let { webView ->
                    val deviceData = DeviceInfoHelper.createDeviceData(context, userAgent)
                    matomoIntegration.injectIntoWebView(webView, deviceData)
                    Log.d("HybridWebView", "Matomo Tag Manager injection started for URL: $url")
                }
                // Проверяем, загружен ли контейнер, и если нет - инжектируем снова
                webView.evaluateJavascript("""
                    if (typeof window._mtm === 'undefined' || !window._mtm.loaded) {
                        console.log('Matomo контейнер не загружен, повторная инъекция...');
                        true;
                    } else {
                        false;
                    }
                """.trimIndent()) { needsInjection ->
                    if (needsInjection == "true") {
                        val deviceData = DeviceInfoHelper.createDeviceData(context, userAgent)
                        matomoIntegration.injectIntoWebView(webView, deviceData)
                        Log.d("HybridWebView", "Matomo Tag Manager re-injected for URL: $pageUrl")
                    } else {
                        Log.d("HybridWebView", "Matomo Tag Manager already loaded for URL: $pageUrl")
                        // Контейнер уже загружен, триггерим PageView для текущей страницы
                        webView.evaluateJavascript("""
                            (function() {
                                var currentUrl = window.location.href;
                                var currentTitle = document.title;
                                
                                // Триггерим PageView для Tag Manager
                                if (window._mtm && Array.isArray(window._mtm)) {
                                    window._mtm.push({
                                        'event': 'mtm.PageView',
                                        'mtm.pageUrl': currentUrl,
                                        'mtm.pageTitle': currentTitle
                                    });
                                    console.log('Matomo Tag Manager: PageView для страницы:', currentUrl);
                                }
                                
                                // Также вызываем trackPageView для Matomo
                                if (typeof window._paq !== 'undefined') {
                                    window._paq.push(['setCustomUrl', currentUrl]);
                                    window._paq.push(['setDocumentTitle', currentTitle]);
                                    window._paq.push(['trackPageView']);
                                }
                            })();
                        """.trimIndent(), null)
                    }
                }
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
                
                // ВАЖНО: Настройки для загрузки изображений и ресурсов
                loadsImagesAutomatically = true  // Автоматическая загрузка изображений
                blockNetworkLoads = false        // Разрешить загрузку ресурсов по сети
                blockNetworkImage = false        // Не блокировать изображения
                
                // Разрешить смешанный контент (HTTP ресурсы на HTTPS странице)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                
                // Кеширование для улучшения загрузки
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                
                // Дополнительные настройки для загрузки ресурсов
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = true
                
                // Разрешить доступ к файлам из file:// URL (для локальных ресурсов)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                    allowFileAccessFromFileURLs = true
                    allowUniversalAccessFromFileURLs = true
                }

                userAgentString = userAgent

                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
            }
            
            // Добавляем JavaScript интерфейс для Matomo
            webView.addJavascriptInterface(matomoIntegration, "MatomoAndroid")
        },
        client = client
    )
}
