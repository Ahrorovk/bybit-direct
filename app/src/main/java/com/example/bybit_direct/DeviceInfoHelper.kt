package com.example.bybit_direct

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.annotation.RequiresPermission
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.view.WindowManager
import java.util.UUID
import android.content.SharedPreferences
import android.util.Log

/**
 * Утилита для получения информации об устройстве для Matomo аналитики
 */
object DeviceInfoHelper {
    private const val PREFS_NAME = "matomo_prefs"
    private const val KEY_USER_ID = "user_id"

    /**
     * Получает разрешение экрана
     */
    fun getScreenResolution(context: Context): Pair<Int, Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        return Pair(displayMetrics.widthPixels, displayMetrics.heightPixels)
    }

    /**
     * Получает модель устройства
     */
    fun getDeviceModel(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }
    }

    /**
     * Получает версию Android
     */
    fun getAndroidVersion(): String {
        return Build.VERSION.RELEASE // Например: "16" для Android 16
    }

    /**
     * Генерирует правильный User-Agent на основе реальных данных устройства
     * Matomo определяет модель устройства и версию Android из User-Agent
     */
    fun generateUserAgent(context: Context): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val androidVersion = Build.VERSION.RELEASE
        val sdkVersion = Build.VERSION.SDK_INT
        
        // Получаем код модели для User-Agent
        // Для Samsung S25 Ultra обычно это SM-S928B или похожий код
        val modelCode = when {
            // Если модель уже содержит код (например, "SM-S928B")
            model.matches(Regex("^[A-Z]{2}-[A-Z0-9]+$")) -> model
            // Если модель содержит пробелы, убираем их
            model.contains(" ") -> model.replace(" ", "")
            // Иначе используем модель как есть
            else -> model
        }
        
        // Определяем версию Chrome на основе Android версии (более новая версия для Android 16)
        val chromeVersion = when {
            sdkVersion >= 35 -> "131.0.0.0" // Android 15+ использует Chrome 131+
            sdkVersion >= 34 -> "130.0.0.0" // Android 14
            else -> "131.0.0.0" // По умолчанию актуальная версия
        }
        
        // Генерируем User-Agent на основе реальных данных устройства
        // Формат: Mozilla/5.0 (Linux; Android {версия}; {код_модели}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/{версия} Mobile Safari/537.36
        val userAgent = "Mozilla/5.0 (Linux; Android $androidVersion; $modelCode) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeVersion Mobile Safari/537.36"
        
        Log.d("DeviceInfoHelper", "Generated User-Agent: $userAgent")
        Log.d("DeviceInfoHelper", "Device: $manufacturer $model, Android: $androidVersion (SDK $sdkVersion)")
        
        return userAgent
    }

    /**
     * Получает тип соединения (Wi-Fi, 4G, 5G и т.д.)
     */
    fun getConnectionType(context: Context): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return "Unknown"
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "Unknown"
            
            return when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    // Определяем тип мобильного соединения через TelephonyManager
                    getMobileNetworkType(context)
                }
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Unknown"
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            return when (networkInfo?.type) {
                ConnectivityManager.TYPE_WIFI -> "Wi-Fi"
                ConnectivityManager.TYPE_MOBILE -> {
                    // Для старых версий Android также определяем тип мобильной сети
                    getMobileNetworkType(context)
                }
                ConnectivityManager.TYPE_ETHERNET -> "Ethernet"
                else -> "Unknown"
            }
        }
    }

    /**
     * Определяет тип мобильного соединения (2G, 3G, 4G, 5G)
     * Требует разрешение READ_PHONE_STATE для точного определения типа сети
     */
    @RequiresPermission(android.Manifest.permission.READ_PHONE_STATE)
    private fun getMobileNetworkType(context: Context): String {
        return try {
            // Проверяем разрешение на Android 6.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) 
                    != PackageManager.PERMISSION_GRANTED) {
                    return "Mobile" // Возвращаем общий тип, если нет разрешения
                }
            }
            
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ (API 30+)
                when (telephonyManager.dataNetworkType) {
                    TelephonyManager.NETWORK_TYPE_NR -> "5G"
                    TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                    TelephonyManager.NETWORK_TYPE_HSDPA,
                    TelephonyManager.NETWORK_TYPE_HSUPA,
                    TelephonyManager.NETWORK_TYPE_HSPA,
                    TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"
                    TelephonyManager.NETWORK_TYPE_EDGE,
                    TelephonyManager.NETWORK_TYPE_GPRS,
                    TelephonyManager.NETWORK_TYPE_CDMA,
                    TelephonyManager.NETWORK_TYPE_1xRTT,
                    TelephonyManager.NETWORK_TYPE_IDEN -> "2G"
                    else -> "Mobile"
                }
            } else {
                // Android 10 и ниже
                @Suppress("DEPRECATION")
                when (telephonyManager.networkType) {
                    TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                    TelephonyManager.NETWORK_TYPE_HSDPA,
                    TelephonyManager.NETWORK_TYPE_HSUPA,
                    TelephonyManager.NETWORK_TYPE_HSPA,
                    TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"
                    TelephonyManager.NETWORK_TYPE_EDGE,
                    TelephonyManager.NETWORK_TYPE_GPRS,
                    TelephonyManager.NETWORK_TYPE_CDMA,
                    TelephonyManager.NETWORK_TYPE_1xRTT,
                    TelephonyManager.NETWORK_TYPE_IDEN -> "2G"
                    else -> "Mobile"
                }
            }
        } catch (e: SecurityException) {
            // Если нет доступа к TelephonyManager из-за отсутствия разрешения
            "Mobile"
        } catch (e: Exception) {
            // Если произошла другая ошибка
            "Mobile"
        }
    }

    /**
     * Получает или создает User ID
     */
    fun getUserId(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var userId = prefs.getString(KEY_USER_ID, null)
        
        if (userId == null) {
            userId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_USER_ID, userId).apply()
        }
        
        return userId
    }

    /**
     * Получает версию приложения
     * Берет versionName из build.gradle.kts (defaultConfig.versionName)
     */
    fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionName = packageInfo.versionName ?: "1.0"
            val versionCode = packageInfo.versionCode
            
            // Логируем для проверки
            Log.d("DeviceInfoHelper", "App Version: $versionName (code: $versionCode)")
            
            // Возвращаем версию в формате "1.0" или "1.0.0"
            versionName
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w("DeviceInfoHelper", "Could not get app version, using default: 1.0")
            "1.0"
        }
    }

    /**
     * Создает объект DeviceData для Matomo
     */
    fun createDeviceData(context: Context, userAgent: String): MatomoTagManagerIntegration.DeviceData {
        val (width, height) = getScreenResolution(context)
        Log.e("TAG","MATOMOOO->${MatomoTagManagerIntegration.DeviceData(
            screenWidth = width,
            screenHeight = height,
            userId = getUserId(context),
            deviceModel = getDeviceModel(),
            connectionType = getConnectionType(context),
            userAgent = userAgent
        )}")
        return MatomoTagManagerIntegration.DeviceData(
            screenWidth = width,
            screenHeight = height,
            userId = getUserId(context),
            deviceModel = getDeviceModel(),
            connectionType = getConnectionType(context),
            userAgent = userAgent
        )
    }
}

