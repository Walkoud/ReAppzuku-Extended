[English](./README.md) | **Русский** | [简体中文](./README_ZH.md) | [Espanol](./README_ES.md)

---

![Logo](https://github.com/gree1d/ReAppzuku/blob/main/docs/images/logo.png)
<p align="center">
<img src="https://img.shields.io/github/v/release/gree1d/ReAppzuku?label=Release&" alt="Latest Release">
<img src="https://img.shields.io/github/downloads/gree1d/ReAppzuku/total?label=Downloads&color=a855f7" alt="Downloads">
<img src="https://img.shields.io/badge/License-GPLv3-64748b.svg" alt="License">
<img src="https://img.shields.io/badge/Android-6.0%2B-f97316.svg" alt="Android">
<img src="https://img.shields.io/badge/Root-Supported-brightgreen.svg"/>
<img src="https://img.shields.io/badge/Shizuku-Supported-brightgreen.svg"/>
</p>

ReAppzuku — форк Appzuku (Shappky) с расширенным контролем над фоновой активностью приложений на Android.

Мониторинг и остановка приложений, которые потребляют RAM, разряжают батарею и нагружают CPU в фоне.\
Ручная принудительная остановка одним нажатием, периодический Kill по таймеру и гибкие фоновые ограничения для выбранных приложений.\
\
Требуется Root или Shizuku.

## ⚙️ Возможности

* **Умная автоматизация:**
  * Периодический Auto-Kill: интервалы от 10 секунд до 5 минут.
  * Kill при блокировке экрана: принудительная остановка фоновых процессов сразу после выключения экрана.
  * Порог RAM: Kill срабатывает только при превышении заданного лимита (75%–100%).
  * Kill при аппаратных событиях/запуске приложения: Kill срабатывает когда происходит выбранноые аппаратные события или когда происходит запуск целевого приложения, с возможностью очистки ОЗУ при его запуске.
  * Пресеты Auto-Kill: настраивайте и планируйте поведение Auto-Kill в определенное время.
* **Ручное управление**:
  * Главный экран: список всех активных фоновых процессов с расходом RAM, выбор и остановка пачкой.
  * Быстрые плитки: «Остановить приложение» убивает текущее приложение на переднем плане; «Остановить фоновые» запускает Auto-Kill с вашими списками.
  * Виджет на рабочем столе: отображение текущей загруженности ОЗУ и статистики Auto-Kill за последние 12ч.
  * Ярлык приложения: долгое нажатие на иконку мгновенно убивает текущее приложение на переднем плане.
* **Фоновые ограничения** (Android 11+):
  * Мягкий режим: блокирует автозапуск на уровне ОС — приложение продолжает работать, если вы его открыли, но само по себе не запустится.
  * Средний режим: частичное ограничение фоновой активности приложения.
  * Жёсткий режим: немедленно завершает процесс при сворачивании, не даёт оставаться в памяти ни секунды.
  * Ручной режим: вручную выберите и примените необходимые ограничения для приложения.
* **Планировщик ограничений:** настройте временное окно для снятия ограничений с опциональным запуском компонента по активации.
* **Режим сна:** полная заморозка выбранных приложений по таймеру бездействия (5–60 мин), автоматическая разморозка при разблокировке экрана.
* **App Triggers:** глубокая диагностика реальных причин фоновой активности — фоновые сервисы, sticky-сервисы, wakelocks, будильники, JobScheduler, сетевые соединения, boot-ресиверы и ещё 54 фактора (зависит от версии Android).
* **Аналитика и логи:**
  * Лог Auto-Kill за последние 12 часов: убийства, перезапуски, освобождённая RAM по каждому приложению.
  * Рейтинг нарушителей по потреблению RAM и частоте перезапусков (12ч / 24ч / 7 дней / всё время).
  * Лог фоновых ограничений: применено, ошибка, не применено — до 200 записей.
  * Графики потребления ресурсов (RAM, CPU, батарея) за периоды 2, 6, 12 и 24 часа.
* **Гибкие списки:** Белый список (исключения из Auto-Kill), Чёрный список (цели Auto-Kill), Скрытые приложения (полностью исключены из списка и Auto-Kill).
* **Резервное копирование:** экспорт и импорт всех настроек в JSON-файл — белый список, чёрный список, скрытые приложения, ограничения, режим сна и параметры автоматизации.

## 🛠 Требования

| Компонент | Требование |
|---|---|
| Android | 6.0+ (фоновые ограничения требуют 11+) |
| Доступ | Root или Shizuku |

## 🚀 Быстрый старт

* **Настройте доступ:** установите и активируйте [Shizuku](https://github.com/thedjchi/Shizuku) или предоставьте root.
* **Фоновая работа:** отключите оптимизацию батареи для ReAppzuku и закрепите в Недавних — иначе система может убить сам сервис управления.
* **Выберите стратегию:** Белый список + периодический Kill для максимальной экономии, или только Чёрный список для точечного контроля конкретных приложений.

## 🛡 Безопасность

ReAppzuku автоматически защищает критичные системные процессы — Google Play Services, System UI, текущую клавиатуру, текущий лаунчер, телефонию, Bluetooth, NFC и сам Shizuku. OEM-приложения производителей (Xiaomi Security Center, Samsung Device Care, OPPO Phone Manager и др.) также защищены.

## 🎨 Внешний вид

* Системная, светлая, тёмная и AMOLED темы.
* Настраиваемые цветовые акценты: индиго, малиновый, лесной зелёный, янтарный и другие.

## 🌐 Перевод

Переводы приветствуются!\
Помочь с локализацией можно так:
* Отправьте **Pull Request** с изменениями в `/values/strings.xml`, `README.md`, `HELP.md`.
* Откройте **Issue** и приложите `/values/strings.xml`, `README.md`, `HELP.md` (упакуйте в `.zip`).

## 🖼️ Скриншоты

<p align="center">
  <a href="https://raw.githubusercontent.com/gree1d/ReAppzuku/refs/heads/main/docs/images/screenshot1.jpg">
    <img src="https://raw.githubusercontent.com/gree1d/ReAppzuku/refs/heads/main/docs/images/screenshot1.jpg" width="100" alt="Screenshot 1">
  </a>
  <a href="https://raw.githubusercontent.com/gree1d/ReAppzuku/refs/heads/main/docs/images/screenshot2.jpg">
    <img src="https://raw.githubusercontent.com/gree1d/ReAppzuku/refs/heads/main/docs/images/screenshot2.jpg" width="100" alt="Screenshot 2">
  </a>
  <a href="https://raw.githubusercontent.com/gree1d/ReAppzuku/refs/heads/main/docs/images/screenshot3.jpg">
    <img src="https://raw.githubusercontent.com/gree1d/ReAppzuku/refs/heads/main/docs/images/screenshot3.jpg" width="100" alt="Screenshot 3">
  </a>
</p>

## Лицензия

ReAppzuku распространяется под лицензией [GNU General Public License v3.0](LICENSE).

## Благодарности

Форк проекта [northmendo/Appzuku](https://github.com/northmendo/Appzuku).
<br><br>  
>![Claude](https://img.shields.io/badge/Claude-D97757?logo=claude&logoColor=fff)
![Google Gemini](https://img.shields.io/badge/Google%20Gemini-886FBF?logo=googlegemini&logoColor=fff)
![Grok / xAI](https://img.shields.io/badge/Grok-000000?logo=xai&logoColor=white)
> ReAppzuku был создан с использованием vibecoding — подхода, при котором значительная часть кода была сгенерирована с помощью ИИ (LLM).
