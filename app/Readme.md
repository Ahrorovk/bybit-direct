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
    <string name="app_name">My WebApp</string>
    <string name="admin_url">https://linkapp.tdsdomain.ru/webview-admin/app.php/</string>
</resources>
```

---

## Файл: app/src/main/java/com/example/myapp/MainActivity.kt

```kotlin
package com.example.myapp

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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
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
    private val projectParam by lazy { getString(R.string.app_name) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(modifier = Modifier.fillMaxSize()) {
                LauncherScreen(adminUrl, projectParam)
            }
        }
    }
}
// Основной экран загрузки и отображения WebView
@Composable
fun LauncherScreen(adminUrl: String, projectParam: String) {
    var loading by remember { mutableStateOf(true) }
    var targetUrl by remember { mutableStateOf<String?>(null) }
    var customUA by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(adminUrl, projectParam) {
        loading = true
        errorMsg = null
        val urlWithParams = buildAdminRequestUrl(adminUrl, projectParam)

        try {
            Log.e("LauncherScreen", "Fetching admin URL: $urlWithParams")
            val raw = fetchJsonNoCache(urlWithParams)
            val parts = raw.split("***")
            Log.e("LauncherScreen", "Got response: $raw")

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
        loading -> Surface(modifier = Modifier.fillMaxSize()) { CircularProgressIndicator(modifier = Modifier.fillMaxSize()) }
        errorMsg != null -> Surface(modifier = Modifier.fillMaxSize()) { Text(text = errorMsg ?: "Unknown error") }
        targetUrl != null -> {
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
                    if (!customUA.isNullOrBlank()) settings.userAgentString = customUA
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            request?.url?.let { view?.loadUrl(it.toString()) }
                            return true
                        }
                    }
                    loadUrl(targetUrl!!)
                }
            }, update = { webView ->
                if (!customUA.isNullOrBlank()) webView.settings.userAgentString = customUA
                webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
                webView.clearCache(true)
            })
        }
        else -> Surface(modifier = Modifier.fillMaxSize()) { Text(text = "No URL to open") }
    }
}
// Добавляет параметр appname к URL админки
private fun buildAdminRequestUrl(base: String, projectParam: String): String {
    val encodedProject = URLEncoder.encode(projectParam, "UTF-8")
    val sep = if (base.contains("?")) "&" else "?"
    return "$base${sep}appname=$encodedProject"
}
// Выполняет HTTP GET без кэширования и возвращает тело ответа как строку
suspend fun fetchJsonNoCache(urlStr: String): String = withContext(Dispatchers.IO) {
    val url = URL(urlStr)
    val conn = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 10_000
        instanceFollowRedirects = true
        useCaches = false
        requestMethod = "GET"
        setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
        setRequestProperty("Pragma", "no-cache")
        setRequestProperty("Expires", "0")
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
