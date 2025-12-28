# Быстрый старт - Matomo Tag Manager в WebView

## Что нужно сделать перед запуском

### 1. Настройка в `strings.xml`

Откройте `app/src/main/res/values/strings.xml` и убедитесь, что указаны правильные значения:

```xml
<string name="matomo_url">https://matomo.private-analytic-tools.com</string>
<string name="matomo_container_id">HNSAYDRE</string>  <!-- Замените на ваш Container ID -->
<string name="matomo_site_id">2</string>  <!-- Замените на ваш Site ID -->
```

**Важно**: 
- `matomo_container_id` - это ID контейнера из Matomo Tag Manager (можно найти в URL или в настройках контейнера)
- `matomo_site_id` - это ID сайта в Matomo (обычно виден в URL или в настройках сайта)

### 2. Настройка админки для проекта "athletic"

В админке (`admin_url`) нужно настроить ответ для проекта с названием **"athletic"**.

Админка должна возвращать ответ в формате:
```
UserAgent *** URL
```

Например:
```
Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 *** https://athletic-store.ru
```

**Важно**: В `strings.xml` измените `app_name_admin` на `athletic`:

```xml
<string name="app_name_admin">athletic</string>
```

### 3. Настройка в Matomo Tag Manager

1. Войдите в Matomo: `https://matomo.private-analytic-tools.com`
2. Выберите сайт `athletic-store.ru`
3. Выберите или создайте контейнер
4. Скопируйте **Container ID** (например: `HNSAYDRE`)
5. Вставьте его в `strings.xml` в поле `matomo_container_id`

### 4. Создание Custom Dimensions в Matomo

1. Перейдите в **Настройки** → **Веб-сайты** → `athletic-store.ru`
2. **Custom Dimensions** → **Добавить новое измерение**
3. Создайте 4 измерения:

| ID | Имя | Область |
|----|-----|---------|
| 1 | Screen Resolution | Visit |
| 2 | Device Model | Visit |
| 3 | Connection Type | Visit |
| 4 | App Version | Visit |

### 5. Создание тегов для отслеживания событий

#### Тег для кликов по фильтрам:

1. **Теги** → **Создать новый тег**
2. Тип: **Matomo Аналитика**
3. Настройки:
   - Имя: `Matomo Аналитика - клик по фильтру`
   - Тип отслеживания: `Событие`
   - Категория: `Filter`
   - Действие: `Click`
   - Имя: `{{Event Name}}`

4. **Триггер**: Создать новый
   - Тип: `Клик на любой элемент`
   - Условие: Элемент содержит `data-filter` ИЛИ класс содержит `filter`

5. **Переменная** (для получения названия фильтра):
   - Тип: `JavaScript переменная`
   - Код:
   ```javascript
   function() {
     var element = {{Click Element}};
     return element.getAttribute('data-filter') || 
            element.getAttribute('data-name') || 
            element.textContent.trim() || 
            'unknown';
   }
   ```

#### Тег для кликов по кнопке поиска:

1. Создайте тег типа **Matomo Аналитика**
2. Настройки:
   - Имя: `Matomo Аналитика - клик по кнопке поиска`
   - Тип: `Событие`
   - Категория: `Search`
   - Действие: `Click`
   - Имя: `search_button`

3. **Триггер**: Клик на `button[type="submit"]` или элемент с `data-action="search"`

### 6. Публикация контейнера

После настройки всех тегов:
1. Нажмите **Опубликовать** в левом меню
2. Выберите версию
3. Опубликуйте изменения

## Тестирование

1. Соберите и запустите приложение
2. Откройте сайт в WebView
3. В Matomo перейдите в **Tag Manager** → **Предпросмотр / Отладка**
4. Выполните действия в приложении (клики по фильтрам, кнопкам)
5. Проверьте срабатывание тегов в режиме отладки

## Проверка данных

1. **В реальном времени**: Matomo → Отчеты → В реальном времени
2. **Custom Dimensions**: Отчеты → Посетители → Custom Dimensions
3. **События**: Отчеты → События

## Что передается автоматически

✅ URL страниц и заголовки  
✅ Реферер  
✅ User-Agent  
✅ Cookies  
✅ Разрешение экрана (Custom Dimension 1)  
✅ Модель устройства (Custom Dimension 2)  
✅ Тип соединения (Custom Dimension 3)  
✅ Версия приложения (Custom Dimension 4)  
✅ User ID (уникальный ID пользователя)  

## Отслеживание событий

Автоматически отслеживаются:
- ✅ Клики по фильтрам
- ✅ Клики по кнопкам поиска
- ✅ Клики по всем кнопкам
- ✅ Изменения в формах
- ✅ Переходы между страницами в SPA

## Ручное отслеживание из JavaScript

Если нужно отследить кастомное событие:

```javascript
window.trackMatomoEvent('Category', 'Action', 'Name', 'Value');
```

Пример:
```javascript
window.trackMatomoEvent('Ecommerce', 'Click', 'Buy Button', 'product_123');
```

## Устранение проблем

### Контейнер не загружается
- Проверьте правильность `matomo_url` и `matomo_container_id`
- Проверьте доступность Matomo сервера
- Проверьте консоль WebView на ошибки

### События не отслеживаются
- Убедитесь, что теги опубликованы
- Проверьте условия триггеров
- Используйте режим отладки в Matomo Tag Manager

---

**Подробная инструкция**: См. `MATOMO_INTEGRATION_INSTRUCTIONS.md`







