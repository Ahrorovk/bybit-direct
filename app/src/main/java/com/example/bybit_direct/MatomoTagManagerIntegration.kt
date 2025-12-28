package com.example.bybit_direct

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

/**
 * Класс для интеграции Matomo Tag Manager в WebView
 * Обеспечивает инъекцию контейнера JavaScript и передачу данных аналитики
 */
class MatomoTagManagerIntegration(
    private val context: Context,
    private val matomoUrl: String,
    private val containerId: String,
    private val siteId: String,
    private val appVersion: String
) {
    companion object {
        private const val TAG = "MatomoTagManager"
    }

    /**
     * Генерирует JavaScript код для инъекции контейнера Matomo Tag Manager
     * Контейнер загружается в <head> для правильной работы
     */
    fun getContainerInjectionScript(): String {
        return """
            (function() {
                // ВАЖНО: Проверяем, не загружен ли уже контейнер
                // Если контейнер уже загружен, не загружаем его снова, но триггерим события
                if (window._mtm && window._mtm.loaded) {
                    console.log('Matomo Tag Manager уже загружен, триггерим события для новой страницы');
                    // Триггерим PageView для новой страницы, даже если контейнер уже загружен
                    if (window._mtm && Array.isArray(window._mtm)) {
                        window._mtm.push({
                            'event': 'mtm.PageView',
                            'mtm.pageUrl': window.location.href,
                            'mtm.pageTitle': document.title
                        });
                        console.log('Matomo Tag Manager: PageView для существующего контейнера:', window.location.href);
                    }
                    return;
                }
                
                // Создаем контейнер Matomo Tag Manager
                window._mtm = window._mtm || [];
                _mtm.push({'mtm.startTime': (new Date().getTime()), 'event': 'mtm.Start'});
                
                // Создаем скрипт для загрузки контейнера
                var script = document.createElement('script');
                script.type = 'text/javascript';
                script.async = true;
                script.defer = true;
                script.src = '$matomoUrl/js/container_$containerId.js';
                
                // Обработчик успешной загрузки контейнера
                script.onload = function() {
                    console.log('Matomo Tag Manager контейнер успешно загружен: $containerId');
                    window._mtm.loaded = true;
                    
                    // Проверяем, что _paq доступен для контейнера
                    if (typeof window._paq !== 'undefined') {
                        console.log('Matomo _paq доступен для контейнера, количество команд:', window._paq.length);
                    } else {
                        console.warn('Matomo _paq не найден после загрузки контейнера!');
                    }
                    
                    // ВАЖНО: Триггерим событие загрузки страницы для выполнения тегов
                    // Matomo Tag Manager выполняет теги при событии mtm.Start и других событиях
                    if (window._mtm && Array.isArray(window._mtm)) {
                        // Добавляем событие загрузки страницы для триггеров типа "Все страницы"
                        window._mtm.push({
                            'event': 'mtm.PageView',
                            'mtm.pageUrl': window.location.href,
                            'mtm.pageTitle': document.title
                        });
                        console.log('Matomo Tag Manager: событие PageView отправлено для выполнения тегов');
                    }
                    
                    // Триггерим событие загрузки для других скриптов
                    if (typeof window.dispatchEvent !== 'undefined') {
                        window.dispatchEvent(new Event('mtmLoaded'));
                    }
                    
                    // Проверяем через небольшую задержку, что контейнер работает
                    setTimeout(function() {
                        if (typeof window._paq !== 'undefined' && window._paq.length > 0) {
                            console.log('Matomo контейнер активен, _paq содержит', window._paq.length, 'команд');
                        } else {
                            console.warn('Matomo контейнер загружен, но _paq пуст или недоступен');
                        }
                        
                        // Проверяем, что контейнер может выполнять теги
                        if (typeof window._mtm !== 'undefined' && window._mtm.length > 0) {
                            console.log('Matomo Tag Manager готов к выполнению тегов, очередь событий:', window._mtm.length);
                        }
                    }, 1000);
                };
                
                // Обработчик ошибки загрузки
                script.onerror = function() {
                    console.error('Ошибка загрузки Matomo Tag Manager контейнера: $containerId');
                    console.error('URL контейнера: $matomoUrl/js/container_$containerId.js');
                };
                
                // Вставляем скрипт в <head> для правильной работы
                var head = document.head || document.getElementsByTagName('head')[0];
                if (head) {
                    head.appendChild(script);
                    console.log('Matomo Tag Manager контейнер добавлен в <head>: $containerId');
                } else {
                    // Если head еще не загружен, вставляем в начало body
                    var body = document.body || document.getElementsByTagName('body')[0];
                    if (body) {
                        body.insertBefore(script, body.firstChild);
                        console.log('Matomo Tag Manager контейнер добавлен в <body>: $containerId');
                    } else {
                        // Если и body нет, ждем загрузки DOM
                        if (document.readyState === 'loading') {
                            document.addEventListener('DOMContentLoaded', function() {
                                var h = document.head || document.getElementsByTagName('head')[0];
                                if (h) h.appendChild(script);
                            });
                        }
                    }
                }
            })();
        """.trimIndent()
    }

    /**
     * Генерирует JavaScript код для инициализации Matomo с кастомными данными
     */
    fun getMatomoInitScript(deviceData: DeviceData): String {
        val screenWidth = deviceData.screenWidth
        val screenHeight = deviceData.screenHeight
        val userId = deviceData.userId.escapeJs()
        val deviceModel = deviceData.deviceModel.escapeJs()
        val connectionType = deviceData.connectionType.escapeJs()
        val userAgent = deviceData.userAgent.escapeJs()
        
        return """
            (function() {
                // Инициализация Matomo с кастомными данными
                if (typeof window._paq === 'undefined') {
                    window._paq = window._paq || [];
                }
                
                // Устанавливаем URL трекера
                _paq.push(['setTrackerUrl', '$matomoUrl/index.php']);
                _paq.push(['setSiteId', '$siteId']);
                
                // Устанавливаем User ID
                ${if (userId.isNotBlank()) "_paq.push(['setUserId', '$userId']);" else ""}
                
                // Устанавливаем разрешение экрана
                _paq.push(['setCustomDimension', 1, '$screenWidth x $screenHeight']);
                
                // Устанавливаем модель устройства
                _paq.push(['setCustomDimension', 2, '$deviceModel']);
                
                // Устанавливаем тип соединения
                _paq.push(['setCustomDimension', 3, '$connectionType']);
                
                // Устанавливаем версию приложения
                _paq.push(['setCustomDimension', 4, '$appVersion']);
                
                // Устанавливаем User-Agent для правильного определения устройства в Matomo
                // ВАЖНО: Matomo определяет модель устройства и версию Android из User-Agent
                if (navigator.userAgent !== '$userAgent') {
                    try {
                        Object.defineProperty(navigator, 'userAgent', {
                            get: function() { return '$userAgent'; },
                            configurable: true
                        });
                        console.log('Matomo: User-Agent установлен:', '$userAgent');
                    } catch (e) {
                        console.warn('Matomo: Не удалось установить User-Agent:', e);
                    }
                }
                
                // Включаем отслеживание ссылок
                _paq.push(['enableLinkTracking']);
                
                // НЕ вызываем trackPageView здесь - Tag Manager сам управляет отслеживанием страниц
                // _paq.push(['trackPageView']);
                
                console.log('Matomo _paq инициализирован с кастомными данными');
            })();
        """.trimIndent()
    }
    
    /**
     * Экранирует строку для использования в JavaScript
     */
    private fun String.escapeJs(): String {
        return this
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    /**
     * Генерирует JavaScript код для отслеживания событий с поддержкой динамических элементов
     */
    fun getEventTrackingScript(): String {
        return """
            (function() {
                // Функция для отслеживания событий
                window.trackMatomoEvent = function(category, action, name, value) {
                    if (typeof window._paq !== 'undefined') {
                        window._paq.push(['trackEvent', category || '', action || '', name || '', value || 0]);
                        console.log('Matomo Event tracked:', category, action, name, value);
                    } else {
                        console.warn('Matomo не инициализирован');
                    }
                };
                
                // Отслеживание кликов по фильтрам и другим элементам
                function setupEventTracking() {
                    // Используем делегирование событий для поддержки динамических элементов (SPA/React/Vue)
                    document.addEventListener('click', function(event) {
                        var target = event.target;
                        
                        // Отслеживание кликов по фильтрам
                        if (target.matches && (
                            target.matches('[data-filter]') ||
                            target.matches('.filter') ||
                            target.matches('[class*="filter"]') ||
                            target.closest('[data-filter]') ||
                            target.closest('.filter')
                        )) {
                            var filterName = target.getAttribute('data-filter') || 
                                            target.getAttribute('data-name') || 
                                            target.textContent.trim() || 
                                            'unknown';
                            window.trackMatomoEvent('Filter', 'Click', filterName);
                        }
                        
                        // Отслеживание кликов по кнопкам поиска
                        if (target.matches && (
                            target.matches('button[type="submit"]') ||
                            target.matches('[data-action="search"]') ||
                            target.matches('.search-button') ||
                            target.closest('form') && target.type === 'submit'
                        )) {
                            var searchQuery = '';
                            var form = target.closest('form');
                            if (form) {
                                var input = form.querySelector('input[type="search"], input[name*="search"], input[placeholder*="поиск" i]');
                                if (input) {
                                    searchQuery = input.value || '';
                                }
                            }
                            window.trackMatomoEvent('Search', 'Click', searchQuery || 'search_button');
                        }
                        
                        // Отслеживание кликов по кнопке "О сайте" (специальное)
                        var elementText = target.textContent.trim().toLowerCase() || '';
                        if (elementText.indexOf('о сайте') !== -1 || elementText.indexOf('o sajte') !== -1) {
                            var fullText = target.textContent.trim() || target.getAttribute('aria-label') || 'О сайте';
                            window.trackMatomoEvent('Navigation', 'Click', fullText);
                            console.log('Matomo: Клик по кнопке "О сайте" отслежен');
                        }
                        
                        // Отслеживание кликов по кнопкам (общее)
                        if (target.matches && target.matches('button, [role="button"], .btn, [class*="button"], a[href*="o-sajte"], a[href*="about"]')) {
                            var buttonText = target.textContent.trim() || target.getAttribute('aria-label') || 'button';
                            var buttonId = target.id || target.getAttribute('data-id') || '';
                            // Не дублируем событие для "О сайте", если уже отследили выше
                            if (buttonText.toLowerCase().indexOf('о сайте') === -1 && buttonText.toLowerCase().indexOf('o sajte') === -1) {
                                window.trackMatomoEvent('Button', 'Click', buttonText + (buttonId && buttonId.length > 0 ? ' (' + buttonId + ')' : ''));
                            }
                        }
                    }, true); // Используем capture phase для перехвата всех событий
                    
                    // Отслеживание изменений в формах (для фильтров)
                    document.addEventListener('change', function(event) {
                        var target = event.target;
                        if (target.matches && (
                            target.matches('select') ||
                            target.matches('input[type="checkbox"]') ||
                            target.matches('input[type="radio"]')
                        )) {
                            var elementName = target.name || target.id || 'form_element';
                            var elementValue = target.value || (target.checked ? 'checked' : 'unchecked');
                            window.trackMatomoEvent('Form', 'Change', elementName, elementValue);
                        }
                    }, true);
                    
                    console.log('Event tracking настроен для динамических элементов');
                }
                
                // Запускаем настройку отслеживания событий
                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', setupEventTracking);
                } else {
                    setupEventTracking();
                }
                
                // Для SPA приложений (React, Vue) - перехватываем изменения роутера
                if (typeof window.history !== 'undefined') {
                    var originalPushState = window.history.pushState;
                    var originalReplaceState = window.history.replaceState;
                    
                    window.history.pushState = function() {
                        originalPushState.apply(window.history, arguments);
                        var currentUrl = window.location.href;
                        var currentTitle = document.title;
                        
                        if (typeof window._paq !== 'undefined') {
                            window._paq.push(['setCustomUrl', currentUrl]);
                            window._paq.push(['setDocumentTitle', currentTitle]);
                            window._paq.push(['trackPageView']);
                        }
                        
                        // Триггерим событие PageView для Tag Manager
                        if (window._mtm && Array.isArray(window._mtm)) {
                            window._mtm.push({
                                'event': 'mtm.PageView',
                                'mtm.pageUrl': currentUrl,
                                'mtm.pageTitle': currentTitle
                            });
                            console.log('Matomo Tag Manager: PageView для pushState:', currentUrl);
                        }
                        
                        // Перенастраиваем отслеживание для новых элементов
                        setTimeout(setupEventTracking, 100);
                    };
                    
                    window.history.replaceState = function() {
                        originalReplaceState.apply(window.history, arguments);
                        var currentUrl = window.location.href;
                        var currentTitle = document.title;
                        
                        if (typeof window._paq !== 'undefined') {
                            window._paq.push(['setCustomUrl', currentUrl]);
                            window._paq.push(['setDocumentTitle', currentTitle]);
                            window._paq.push(['trackPageView']);
                        }
                        
                        // Триггерим событие PageView для Tag Manager
                        if (window._mtm && Array.isArray(window._mtm)) {
                            window._mtm.push({
                                'event': 'mtm.PageView',
                                'mtm.pageUrl': currentUrl,
                                'mtm.pageTitle': currentTitle
                            });
                            console.log('Matomo Tag Manager: PageView для replaceState:', currentUrl);
                        }
                    };
                    
                    // Отслеживание popstate (назад/вперед)
                    window.addEventListener('popstate', function() {
                        var currentUrl = window.location.href;
                        var currentTitle = document.title;
                        
                        if (typeof window._paq !== 'undefined') {
                            window._paq.push(['setCustomUrl', currentUrl]);
                            window._paq.push(['setDocumentTitle', currentTitle]);
                            window._paq.push(['trackPageView']);
                        }
                        
                        // Триггерим событие PageView для Tag Manager
                        if (window._mtm && Array.isArray(window._mtm)) {
                            window._mtm.push({
                                'event': 'mtm.PageView',
                                'mtm.pageUrl': currentUrl,
                                'mtm.pageTitle': currentTitle
                            });
                            console.log('Matomo Tag Manager: PageView для popstate:', currentUrl);
                        }
                        
                        setTimeout(setupEventTracking, 100);
                    });
                }
            })();
        """.trimIndent()
    }

    /**
     * Генерирует полный скрипт для инъекции в WebView
     * Контейнер Tag Manager сам управляет отслеживанием, мы только передаем кастомные данные
     */
    fun getFullInjectionScript(deviceData: DeviceData): String {
        val screenWidth = deviceData.screenWidth
        val screenHeight = deviceData.screenHeight
        val userId = deviceData.userId.escapeJs()
        val deviceModel = deviceData.deviceModel.escapeJs()
        val connectionType = deviceData.connectionType.escapeJs()
        
        return """
            // Инициализируем _paq ДО загрузки контейнера, чтобы контейнер мог использовать эти настройки
            window._paq = window._paq || [];
            
            // Устанавливаем URL трекера и Site ID
            // ВАЖНО: Трекер Matomo - это публичный endpoint, не требует логина/пароля
            // URL указывается в strings.xml (matomo_url)
            // Site ID идентифицирует ваш сайт в Matomo
            _paq.push(['setTrackerUrl', '$matomoUrl/index.php']);
            _paq.push(['setSiteId', '$siteId']);
            
            // Устанавливаем User ID
            ${if (userId.isNotBlank()) "_paq.push(['setUserId', '$userId']);" else ""}
            
            // Устанавливаем кастомные измерения (Custom Dimensions)
            _paq.push(['setCustomDimension', 1, '$screenWidth x $screenHeight']);
            _paq.push(['setCustomDimension', 2, '$deviceModel']);
            _paq.push(['setCustomDimension', 3, '$connectionType']);
            _paq.push(['setCustomDimension', 4, '$appVersion']);
            
            // Включаем отслеживание ссылок
            _paq.push(['enableLinkTracking']);
            
            console.log('Matomo _paq инициализирован с кастомными данными:', {
                trackerUrl: '$matomoUrl/index.php',
                siteId: '$siteId',
                userId: '${if (userId.isNotBlank()) userId else "не установлен"}',
                customDimensions: {
                    screen: '$screenWidth x $screenHeight',      // Custom Dimension 1
                    device: '$deviceModel',                      // Custom Dimension 2
                    connection: '$connectionType',            // Custom Dimension 3
                    appVersion: '$appVersion'                    // Custom Dimension 4 - Версия приложения
                }
            });
            
            // ВАЖНО: Версия приложения передается через Custom Dimension 4
            // В Matomo: Настройки → Веб-сайты → Custom Dimensions → ID 4 = App Version
            
            // ВАЖНО: Перехватываем все запросы к Matomo для проверки
            // Перехватываем XMLHttpRequest
            (function() {
                var originalXHROpen = XMLHttpRequest.prototype.open;
                var originalXHRSend = XMLHttpRequest.prototype.send;
                
                XMLHttpRequest.prototype.open = function(method, url, async, user, password) {
                    this._url = url;
                    if (url && (url.indexOf('matomo.php') !== -1 || url.indexOf('index.php') !== -1)) {
                        console.log('🔵 Matomo Request OPEN:', method, url);
                    }
                    return originalXHROpen.apply(this, arguments);
                };
                
                XMLHttpRequest.prototype.send = function(data) {
                    if (this._url && (this._url.indexOf('matomo.php') !== -1 || this._url.indexOf('index.php') !== -1)) {
                        console.log('🔵 Matomo Request SEND to:', this._url);
                        console.log('🔵 Matomo Request Data:', data);
                        this.addEventListener('load', function() {
                            console.log('✅ Matomo Response Status:', this.status, 'URL:', this._url);
                            if (this.responseText) {
                                console.log('✅ Matomo Response:', this.responseText.substring(0, 200));
                            }
                        });
                        this.addEventListener('error', function() {
                            console.error('❌ Matomo Request Error:', this._url);
                        });
                    }
                    return originalXHRSend.apply(this, arguments);
                };
            })();
            
            // Перехватываем fetch запросы
            if (typeof window.fetch !== 'undefined') {
                var originalFetch = window.fetch;
                window.fetch = function(url, options) {
                    if (typeof url === 'string' && (url.indexOf('matomo.php') !== -1 || url.indexOf('index.php') !== -1)) {
                        console.log('🔵 Matomo Fetch Request to:', url);
                        console.log('🔵 Matomo Fetch Options:', options);
                        return originalFetch.apply(this, arguments).then(function(response) {
                            console.log('✅ Matomo Fetch Response:', response.status, 'URL:', url);
                            return response;
                        }).catch(function(error) {
                            console.error('❌ Matomo Fetch Error:', error, 'URL:', url);
                            throw error;
                        });
                    }
                    return originalFetch.apply(this, arguments);
                };
            }
            
            // Перехватываем отправку данных через _paq
            var originalPush = window._paq.push;
            window._paq.push = function() {
                var args = Array.prototype.slice.call(arguments);
                console.log('🔵 Matomo _paq.push called:', JSON.stringify(args));
                return originalPush.apply(this, arguments);
            };
            
            // ВАЖНО: После загрузки контейнера нужно триггерить события для выполнения тегов
            // Теги в Matomo Tag Manager выполняются при определенных событиях (триггерах)
            // Событие mtm.Start уже отправлено при инициализации контейнера
            // Дополнительно триггерим PageView для тегов с триггером "Все страницы"
            
            // Слушаем событие загрузки контейнера и триггерим PageView
            if (typeof window.addEventListener !== 'undefined') {
                window.addEventListener('mtmLoaded', function() {
                    setTimeout(function() {
                        // Триггерим событие PageView для выполнения тегов с триггером "Все страницы"
                        if (window._mtm && Array.isArray(window._mtm)) {
                            window._mtm.push({
                                'event': 'mtm.PageView',
                                'mtm.pageUrl': window.location.href,
                                'mtm.pageTitle': document.title
                            });
                            console.log('Matomo Tag Manager: PageView событие отправлено для выполнения тегов');
                        }
                        
                        // Также вызываем trackPageView для Matomo
                        if (typeof window._paq !== 'undefined') {
                            _paq.push(['trackPageView']);
                            console.log('Matomo trackPageView вызван после загрузки контейнера');
                        }
                    }, 500);
                });
            }
            
            // Загружаем контейнер Tag Manager
            ${getContainerInjectionScript()}
            
            // Настраиваем отслеживание событий после загрузки контейнера
            (function() {
                function setupEventTrackingWhenReady() {
                    // Проверяем, загружен ли контейнер
                    if (typeof window._mtm !== 'undefined' && window._mtm.loaded) {
                        ${getEventTrackingScript()}
                        console.log('Matomo Tag Manager полностью инициализирован с отслеживанием событий');
                    } else if (typeof window._mtm !== 'undefined' && window._mtm.length > 0) {
                        // Контейнер загружается, но еще не готов
                        setTimeout(setupEventTrackingWhenReady, 200);
                    } else {
                        // Повторяем попытку (максимум 15 раз = 3 секунды)
                        if (typeof setupEventTrackingWhenReady.attempts === 'undefined') {
                            setupEventTrackingWhenReady.attempts = 0;
                        }
                        setupEventTrackingWhenReady.attempts++;
                        if (setupEventTrackingWhenReady.attempts < 15) {
                            setTimeout(setupEventTrackingWhenReady, 200);
                        } else {
                            console.warn('Matomo Tag Manager контейнер не загрузился за 3 секунды');
                            // Все равно настраиваем отслеживание событий
                            ${getEventTrackingScript()}
                        }
                    }
                }
                
                // Слушаем событие загрузки контейнера
                if (typeof window.addEventListener !== 'undefined') {
                    window.addEventListener('mtmLoaded', function() {
                        setTimeout(setupEventTrackingWhenReady, 100);
                    });
                }
                
                // Запускаем настройку отслеживания событий
                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', function() {
                        setTimeout(setupEventTrackingWhenReady, 500);
                    });
                } else {
                    setTimeout(setupEventTrackingWhenReady, 500);
                }
            })();
            
            // ВАЖНО: Принудительно триггерим PageView при каждой загрузке страницы
            // Это гарантирует, что события отправляются даже если контейнер уже загружен
            (function() {
                function forceTriggerPageView() {
                    var currentUrl = window.location.href;
                    var currentTitle = document.title;
                    
                    // Триггерим PageView для Tag Manager (даже если контейнер уже загружен)
                    if (window._mtm && Array.isArray(window._mtm)) {
                        window._mtm.push({
                            'event': 'mtm.PageView',
                            'mtm.pageUrl': currentUrl,
                            'mtm.pageTitle': currentTitle
                        });
                        console.log('✅ Matomo Tag Manager: PageView ПРИНУДИТЕЛЬНО триггерен:', currentUrl);
                    } else {
                        console.warn('⚠️ Matomo Tag Manager: _mtm не найден для триггера PageView');
                    }
                    
                    // Также вызываем trackPageView для Matomo
                    if (typeof window._paq !== 'undefined') {
                        window._paq.push(['setCustomUrl', currentUrl]);
                        window._paq.push(['setDocumentTitle', currentTitle]);
                        window._paq.push(['trackPageView']);
                        console.log('✅ Matomo trackPageView ПРИНУДИТЕЛЬНО вызван для:', currentUrl);
                    } else {
                        console.warn('⚠️ Matomo: _paq не найден для trackPageView');
                    }
                }
                
                // Триггерим при загрузке страницы (с задержкой, чтобы контейнер успел загрузиться)
                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', function() {
                        setTimeout(forceTriggerPageView, 1500);
                    });
                } else {
                    setTimeout(forceTriggerPageView, 1500);
                }
                
                // Дополнительно: триггерим события для выполнения тегов при навигации в SPA
                var lastUrl = window.location.href;
                var urlCheckInterval = setInterval(function() {
                    if (window.location.href !== lastUrl) {
                        lastUrl = window.location.href;
                        console.log('🔄 URL изменился, триггерим PageView:', lastUrl);
                        forceTriggerPageView();
                    }
                }, 1000);
            })();
        """.trimIndent()
    }

    /**
     * Инжектирует скрипт в WebView
     */
    fun injectIntoWebView(webView: WebView, deviceData: DeviceData) {
        val script = getFullInjectionScript(deviceData)
        Log.d(TAG, "Injecting Matomo script with container: $containerId, siteId: $siteId, url: $matomoUrl")
        Log.d(TAG, "Device data: ${deviceData.deviceModel}, ${deviceData.connectionType}, ${deviceData.screenWidth}x${deviceData.screenHeight}")
        Log.d(TAG, "Tracker URL: $matomoUrl/matomo.php")
        Log.d(TAG, "Container URL: $matomoUrl/js/container_$containerId.js")
        webView.post {
            webView.evaluateJavascript(script) { result ->
                Log.d(TAG, "Matomo script injected: $result")
            }
            // Проверяем состояние через 2 секунды
            webView.postDelayed({
                webView.evaluateJavascript("""
                    (function() {
                        var status = {
                            _paq: typeof window._paq !== 'undefined' ? 'exists (' + window._paq.length + ' commands)' : 'not found',
                            _mtm: typeof window._mtm !== 'undefined' ? 'exists' : 'not found',
                            _mtm_loaded: (window._mtm && window._mtm.loaded) ? 'yes' : 'no',
                            trackerUrl: (window._paq && window._paq.length > 0) ? 'set' : 'not set',
                            matomoUrl: '$matomoUrl',
                            containerId: '$containerId',
                            siteId: '$siteId'
                        };
                        console.log('📊 Matomo Status Check:', JSON.stringify(status, null, 2));
                        return JSON.stringify(status);
                    })();
                """.trimIndent()) { result ->
                    Log.d(TAG, "Matomo status check result: $result")
                }
                
                // Проверяем, какие запросы были отправлены
                webView.evaluateJavascript("""
                    (function() {
                        console.log('📊 Проверка отправки данных на Matomo:');
                        console.log('📊 Tracker URL должен быть: $matomoUrl/matomo.php');
                        console.log('📊 Container URL должен быть: $matomoUrl/js/container_$containerId.js');
                        console.log('📊 Site ID: $siteId');
                        console.log('📊 Проверьте Network tab в DevTools для запросов к matomo.php');
                        return 'Check console for Matomo requests';
                    })();
                """.trimIndent(), null)
            }, 2000)
            
            // Проверяем через 5 секунд, были ли отправлены запросы
            webView.postDelayed({
                webView.evaluateJavascript("""
                    (function() {
                        console.log('📊 Финальная проверка отправки данных на Matomo:');
                        if (typeof window._paq !== 'undefined' && window._paq.length > 0) {
                            console.log('✅ _paq содержит', window._paq.length, 'команд для отправки');
                            console.log('✅ Данные должны отправляться на: $matomoUrl/matomo.php');
                        } else {
                            console.warn('⚠️ _paq пуст или не инициализирован');
                        }
                        return 'Final check completed';
                    })();
                """.trimIndent(), null)
            }, 5000)
        }
    }

    /**
     * JavaScript интерфейс для вызова из WebView
     */
    @JavascriptInterface
    fun trackEvent(category: String, action: String, name: String, value: String) {
        Log.d(TAG, "Track event: category=$category, action=$action, name=$name, value=$value")
    }

    /**
     * Данные устройства для передачи в Matomo
     */
    data class DeviceData(
        val screenWidth: Int,
        val screenHeight: Int,
        val userId: String,
        val deviceModel: String,
        val connectionType: String,
        val userAgent: String
    )
}

