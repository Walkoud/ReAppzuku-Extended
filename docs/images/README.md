**English** | [Русский](https://github.com/gree1d/ReAppzuku/blob/main/docs/README_RU.md)

---

![Logo](https://github.com/gree1d/ReAppzuku/blob/main/docs/images/logo.png)
<p align="center">
<img src="https://img.shields.io/github/v/release/gree1d/ReAppzuku?label=Release&logo=github" alt="Latest Release">
<img src="https://img.shields.io/github/downloads/gree1d/ReAppzuku/total?label=Downloads&logo=github&color=a855f7" alt="Downloads">
<img src="https://img.shields.io/badge/License-GPLv3-64748b.svg" alt="License">
<img src="https://img.shields.io/badge/Android-6.0%2B-f97316.svg" alt="Android">
<img src="https://img.shields.io/badge/Root-Supported-brightgreen.svg"/>
<img src="https://img.shields.io/badge/Shizuku-Supported-brightgreen.svg"/>
</p>

ReAppzuku is a fork of Appzuku (Shappky) with enhanced control over background activity of Android apps.

Monitor and stop apps that consume RAM, drain battery, and load CPU in background.\
One-tap manual force-stop, periodic Kill via a timer, and flexible background restrictions for selected apps.\
\
Root or Shizuku privileges are required.

## ⚙️ Features

* **Smart automation:**
  * Periodic Auto-Kill: intervals from 10 seconds to 5 minutes.
  * Kill on screen lock: force-stop background processes immediately after screen turns off.
  * RAM threshold: Kill triggers only when RAM usage reaches a set limit (75%–100%).
* **Manual controls** *(no background service required)*:
  * Main screen: view all active background processes with RAM usage, select and kill in bulk.
  * Quick Tiles: "Stop app" kills current foreground app; "Stop background apps" runs Auto-Kill with your lists.
  * Home screen widget: one tap runs Auto-Kill and shows current RAM state.
  * App shortcut: long-press app icon to kill current foreground app instantly.
* **Background restrictions** (Android 11+):
  * Soft mode: blocks auto-start at OS level — app keeps running if you opened it, but won't wake up on its own.
  * Hard mode: immediately terminates process when minimized, prevents it from staying in memory even for a second.
  * Manual mode: manually select and apply required restrictions to app.
* **Restriction Scheduler:** set a time window to temporarily lift restrictions, with optional component launch on activation.
* **Sleep Mode:** full freeze of selected apps after a set inactivity timer (5–60 min), automatic unfreeze on screen unlock.
* **App Triggers:** deep diagnostic tool analyzing real causes of background activity — foreground services, sticky services, wakelocks, alarms, job scheduler, network connections, boot receivers, and 32 more factors.
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

## 🛡 Safety

ReAppzuku automatically protects critical system processes — Google Play Services, System UI, current keyboard, current launcher, telephony, Bluetooth, NFC, and Shizuku itself. OEM-specific system apps (Xiaomi Security Center, Samsung Device Care, OPPO Phone Manager, etc.) are also protected.

## 🎨 Customization

* System, light, dark, and AMOLED themes.
* Configurable color accents: Indigo, Crimson, Forest Green, Amber, and more.

## 🌐 Translation

Translations are welcome!\
To help localize app:
* Submit a **Pull Request** with changes to `/values/strings.xml`.
* Open an **Issue** and attach your `/values/strings.xml` (pack into `.zip` first), or paste XML directly into a comment.

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

Forked from [northmendo/Appzuku](https://github.com/northmendo/Appzuku).
