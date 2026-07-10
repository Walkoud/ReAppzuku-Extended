[English](./README.md) | [Русский](./README_RU.md) | [简体中文](./README_ZH.md) | [Espanol](./README_ES.md) | **Українська** | [Deutsch](./README_DE.md)

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

ReAppzuku — форк Appzuku (Shappky) з розширеним контролем над фоновою активністю застосунків на Android.

Моніторинг та зупинка застосунків, які споживають RAM, розряджають батарею і навантажують CPU у фоні.\
Ручна примусова зупинка одним натисканням, періодичний Kill за таймером і гнучкі фонові обмеження для вибраних застосунків.\
\
Потрібен Root або Shizuku.

## ⚙️ Можливості

* **Розумна автоматизація:**
  * Періодичний Auto-Kill: інтервали від 10 секунд до 5 хвилин.
  * Kill у разі блокування екрана: примусова зупинка фонових процесів одразу після вимкнення екрана.
  * Поріг RAM: Kill спрацьовує лише у разі перевищення заданого ліміту (75%–100%).
  * Kill при апаратних подіях/запуску застосунку: Kill спрацьовує, коли відбуваються вибрані апаратні події або коли запускається цільовий додаток, з можливістю очищення ОЗП під час його запуску цільового застосунку, з можливістю очищення ОЗП при його запуску.
  * Пресети Auto-Kill: налаштовуйте та плануйте поведінку Auto-Kill у визначений час.
* **Ручне керування**:
  * Головний екран: список усіх активних фонових процесів з витратою RAM, вибір і пакетна зупинка.
  * Швидкі плитки: «Зупинити додаток» вбиває поточний додаток на передньому плані; «Зупинити фонові» запускає Auto-Kill з вашими списками.
  * Віджет на робочому столі: відображення поточного завантаження ОЗП та статистики Auto-Kill за останні 12 годин
  * Ярлик застосунку: довге натискання на іконку миттєво вбиває поточний додаток на передньому плані.
* **Фонові обмеження** (Android 11+):
  * М'який режим: блокує автозапуск на рівні ОС — додаток продовжує працювати, якщо ви його відкрили, але сам по собі не запуститься.
  * Середній режим: часткове обмеження фонової активності застосунку.
  * Жорсткий режим: негайно завершує процес під час згортання, не дає залишатися в пам'яті жодної секунди.
  * Ручний режим: вручну оберіть і застосуйте необхідні обмеження для застосунку.
* **Планувальник обмежень:** налаштуйте часове вікно для зняття обмежень з опціональним запуском компонента під час активації.
* **Режим сну:** повне заморожування вибраних застосунків за таймером бездіяльності (5–60 хв), автоматичне розморожування після розблокування екрана.
* **App Triggers:** глибока діагностика реальних причин фонової активності — фонові сервіси, sticky-сервіси, wakelocks, будильники, JobScheduler, мережеві з'єднання, boot-ресивери та ще 54 фактори (залежно від версії Android).
* **Аналітика та логи:**
  * Лог Auto-Kill за останні 12 годин: примусові завершення, перезапуски, звільнена RAM для кожного застосунку.
  * Рейтинг порушників за споживанням RAM та частотою перезапусків (12 годин / 24 годин / 7 днів / весь час).
  * Лог фонових обмежень: застосовано, помилка, не застосовано — до 200 записів.
  * Графіки споживання ресурсів (RAM, CPU, батарея) за періоди 2, 6, 12 та 24 години.
* **Гнучкі списки:** Білий список (винятки з Auto-Kill), Чорний список (цілі Auto-Kill), Приховані застосунки (повністю виключені зі списку та Auto-Kill).
* **Резервне копіювання:** експорт та імпорт усіх налаштувань у JSON-файл — білий список, чорний список, приховані застосунки, обмеження, режим сну та параметри автоматизації.

## 🛠 Вимоги

| Компонент | Вимога |
|---|---|
| Android | 6.0+ (фонові обмеження потребують 11+) |
| Доступ | Root або Shizuku |

## 🚀 Швидкий старт

* **Налаштуйте доступ:** встановіть та активуйте [Shizuku](https://github.com/thedjchi/Shizuku) або надайте root.
* **Фонова робота:** вимкніть оптимізацію батареї для ReAppzuku та закріпіть у Нещодавніх — інакше система може вбити сам сервіс керування.
* **Оберіть стратегію:** Білий список + періодичний Kill для максимальної економії, або лише Чорний список для точкового контролю конкретних застосунків.

## 🛡 Безпека

ReAppzuku автоматично захищає критичні системні процеси — Google Play Services, System UI, поточну клавіатуру, поточний лаунчер, телефонію, Bluetooth, NFC та сам Shizuku. OEM-застосунки виробників (Xiaomi Security Center, Samsung Device Care, OPPO Phone Manager та ін.) також захищені.

## 🎨 Зовнішній вигляд

* Системна, світла, темна та AMOLED теми.
* Налаштовувані кольорові акценти: індиго, малиновий, лісовий зелений, бурштиновий та інші.

## 🌐 Переклад

Переклади вітаються!\
Допомогти з локалізацією можна так:
* Надішліть **Pull Request** зі змінами до `/values/strings.xml`, `README.md`, `HELP.md`.
* Відкрийте **Issue** і додайте `/values/strings.xml`, `README.md`, `HELP.md` (упакуйте у `.zip`).

## 🖼️ Знімки екрана

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

## Ліцензія

ReAppzuku розповсюджується під ліцензією [GNU General Public License v3.0](LICENSE).

## Подяки

Форк проєкту [northmendo/Appzuku](https://github.com/northmendo/Appzuku).
<br><br>  
>![Claude](https://img.shields.io/badge/Claude-D97757?logo=claude&logoColor=fff)
![Google Gemini](https://img.shields.io/badge/Google%20Gemini-886FBF?logo=googlegemini&logoColor=fff)
![Grok / xAI](https://img.shields.io/badge/Grok-000000?logo=xai&logoColor=white)
> ReAppzuku було створено з використанням vibecoding — підходу, за якого значну частину коду було згенеровано за допомогою ШІ (LLM).
