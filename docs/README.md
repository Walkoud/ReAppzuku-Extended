**English** | [Русский](./README_RU.md) | [简体中文](./README_ZH.md) | [Espanol](./README_ES.md) | [Українська](./README_UK.md) | [Deutsch](./README_DE.md) | [Français](./README_FR.md)

---

![Logo](https://github.com/Walkoud/ReAppzuku-Extended/blob/main/docs/images/logo.png)
<p align="center">
<img src="https://img.shields.io/github/v/release/Walkoud/ReAppzuku-Extended?label=Release&" alt="Latest Release">
<img src="https://img.shields.io/github/downloads/Walkoud/ReAppzuku-Extended/total?label=Downloads&color=a855f7" alt="Downloads">
<img src="https://img.shields.io/badge/License-GPLv3-64748b.svg" alt="License">
<img src="https://img.shields.io/badge/Android-6.0%2B-f97316.svg" alt="Android">
<img src="https://img.shields.io/badge/Root-Supported-brightgreen.svg"/>
<img src="https://img.shields.io/badge/Shizuku-Supported-brightgreen.svg"/>
</p>

ReAppzuku Extended is a fork of [ReAppzuku](https://github.com/gree1d/ReAppzuku) with additional features for enhanced control over background activity of Android apps.

Monitor and stop apps that consume RAM, drain battery, and load CPU in background.\
One-tap manual force-stop, periodic Kill via a timer, and flexible background restrictions for selected apps.\
\
Root or Shizuku privileges are required.

## ✨ Extended Features

* **Select All Filtered:** Select or deselect all visible apps in any dialog with one tap — Whitelist, Blacklist, Background Restrictions, Sleep Mode, Hidden apps, etc.
* **Enhanced Sort & Filters:**
  * Filter by selection state: show only selected or unselected apps.
  * Filter by restriction type (Soft / Medium / Hard / Manual) in Background Restrictions dialogs.
  * Batch set restriction type for multiple selected apps at once.
* **Auto Install Template:** Automatically apply background restriction, sleep mode, whitelist, or blacklist to newly installed apps. Configure once in Settings → Advanced Tools → App Install Template.
* **Android 14+ & HyperOS Support:** Package detection via both broadcast receiver and active polling fallback for reliable operation on OEMs that block standard events.
* **Extended Backup:** Custom standby buckets, whitelist removal flags, and sleep mode suspend method are now preserved during backup and restore.

## ⚙️ Features

* **Smart automation:**
  * Periodic Auto-Kill: intervals from 10 seconds to 5 minutes.
  * Kill on screen lock: force-stop background processes immediately after screen turns off.
  * RAM threshold: Kill triggers only when RAM usage reaches a set limit (75%–100%).
  * Kill on hardware events/launch app: Kill is triggered by selected hardware events or when target application is launched, with option to additionally clear RAM.
  * Auto-Kill presets: Customize and schedule Auto-Kill behavior at specific times. 
* **Manual controls:**
  * Main screen: view all active background processes with RAM usage, select and kill in bulk.
  * Quick Tiles: "Stop app" kills current foreground app; "Stop background apps" runs Auto-Kill with your lists.
  * Home screen widget: displays current RAM usage and Auto-Kill statistics for last 12 hours. 
  * App shortcut: long-press app icon to kill current foreground app instantly.
* **Background restrictions** (Android 11+):
  * Soft mode: blocks auto-start at OS level — app keeps running if you opened it, but won't wake up on its own.
  *  Medium mode: partial restriction background app activity.
  * Hard mode: immediately terminates process when minimized, prevents it from staying in memory even for a second.
  * Manual mode: manually select and apply required restrictions to app.
* **Restriction Scheduler:** set a time window to temporarily lift restrictions, with optional component launch on activation.
* **Sleep Mode:** full freeze of selected apps after a set inactivity timer (5–60 min), automatic unfreeze on screen unlock.
* **App Triggers:** deep diagnostic tool analyzing real causes of background activity — foreground services, sticky services, wakelocks, alarms, job scheduler, network connections, boot receivers, and 54 more factors (Depends on Android version).
* **Analytics & Logs:**
  * Auto-Kill log for last 12 hours: kills, restarts, freed RAM per app.
  * Top offenders ranking by RAM consumption and restart frequency (12h / 24h / 7d / all time).
  * Background restriction log: applied, error, not applied — up to 200 entries.
  * Resource usage charts (RAM, CPU, battery) for periods of 2, 6, 12, and 24 hours.
* **Flexible lists:** Whitelist (Auto-Kill exclusions), Blacklist (Auto-Kill targets), Hidden apps (excluded from list and Auto-Kill entirely).
* **Backup & Restore:** export and import all settings to a JSON file — whitelist, blacklist, hidden apps, restrictions, Sleep Mode, and automation parameters.

## 🛠 Requirements

| Component | Requirement |
|---|---|
| Android | 6.0+ (Background restrictions require 11+) |
| Access | Root or Shizuku |

## 🚀 Quick Start

* **Set up access:** install and activate [Shizuku](https://github.com/thedjchi/Shizuku), or grant root.
* **Background operation:** disable battery optimization for ReAppzuku and pin it in Recents — otherwise system may kill management service itself.
* **Choose your strategy:** Whitelist + periodic Kill for maximum savings, or Blacklist-only for targeted control of specific apps.

## ⭐ Best Use (my preference)

This is my personal recommended setup for maximum battery savings with minimal trade-offs.

### Setup Steps

1. **Enable Background Service** — Go to Settings → Automation → toggle ON "Background Service". This ensures ReAppzuku can apply and maintain restrictions even after you leave the settings.

2. **Enable App Install Template** — Settings → Advanced Tools → App Install Template → toggle ON, then:
   - Check **Background restriction** → set to **HARD**
   - Check **Notify when applied** — this sends a notification each time a new app is installed and the template is applied to it, so you know exactly what happened and can adjust if needed.
   - Leave the rest unchecked (Sleep mode, Whitelist, Blacklist).

3. **Bulk-set HARD mode on all user apps:**
   - Go to **Background Restrictions** → tap **Sort** → select **User** to show only your installed apps.
   - Tap **Select All** (top-right) to select every visible app.
   - Tap **Type** (top-right, appears when apps are selected) → choose **Hard** → Confirm.
   - All your user apps are now in HARD mode.

4. **Exceptions — apps that need notifications or have widgets:**
   - In the same Background Restrictions screen, **uncheck** or switch to **Soft** mode for:
     - Messaging apps (WhatsApp, Signal, Telegram, Discord) — Soft keeps FCM notifications instant.
     - Apps with widgets on your home screen (widgets may not update in HARD mode).
     - Any app where you want to keep receiving push notifications.

### Restriction Modes Reference

| Mode | What it does technically (via Android/Shizuku) | Why use it? | Impact on Notifications | Example target apps |
|---|---|---|---|---|
| 🟢 SOFT | • Disables only RUN_ANY_IN_BACKGROUND.<br>• Leaves the app passively in RAM. | • Prevents the app from auto-launching.<br>• Preserves maximum fluidity (no reload/logo when switching back). | Instant (100% functional via Google FCM servers). | WhatsApp, Signal, Discord, Gmail, Uber. |
| 🟡 MEDIUM | • Disables 6 key AppOps (ACCESS_NOTIFICATIONS, GET_USAGE_STATS, etc.).<br>• Sets app to Standby Bucket RARE. | • Blocks tracking and spying (app can no longer see your other activity).<br>• Still allows music playback or GPS in background while in use. | Blocked or heavily delayed (only delivered in batches). | Instagram, X (Twitter), Spotify, YouTube, Chrome/Firefox. |
| 🔴 HARD | • Disables all 11 available AppOps (including WAKE_LOCK, START_FOREGROUND, SCHEDULE_EXACT_ALARM).<br>• Standby Bucket RESTRICTED.<br>• One initial force-stop on activation. | • Maximum battery protection.<br>• Completely cripples the app as soon as it goes to background.<br>• No more parasitic processes overnight or while screen is off. | Completely blocked (app is silent until you open it). | TikTok, AliExpress, Temu, Shein, Mobile Games, Banking apps. |

## 🛡 Safety

ReAppzuku automatically protects critical system processes — Google Play Services, System UI, current keyboard, current launcher, telephony, Bluetooth, NFC, and Shizuku itself. OEM-specific system apps (Xiaomi Security Center, Samsung Device Care, OPPO Phone Manager, etc.) are also protected.

## 🎨 Customization

* System, light, dark, and AMOLED themes.
* Configurable color accents: Indigo, Crimson, Forest Green, Amber, and more.

## 🌐 Translation

Translations are welcome!\
To help localize app:
* Submit a **Pull Request** with changes to `/values/strings.xml`, `README.md`, `HELP.md`.
* Open an **Issue** and attach your `/values/strings.xml`, `README.md`, `HELP.md` (pack into `.zip` first).\

You can use AI to translate files, then check and correct any errors/mistakes. Claude and Gemini work well with technical texts (in my opinion and experience)

## 🖼️ Screenshots

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

## License

ReAppzuku is licensed under [GNU General Public License v3.0](LICENSE).

## Credits

Forked from [gree1d/ReAppzuku](https://github.com/gree1d/ReAppzuku), originally forked from [northmendo/Appzuku](https://github.com/northmendo/Appzuku).
<br><br>
>![Claude](https://img.shields.io/badge/Claude-D97757?logo=claude&logoColor=fff)
![Google Gemini](https://img.shields.io/badge/Google%20Gemini-886FBF?logo=googlegemini&logoColor=fff)
![Grok / xAI](https://img.shields.io/badge/Grok-000000?logo=xai&logoColor=white)
> ReAppzuku was built using vibecoding — an approach where a significant part of code was generated with help of AI (LLM).
