# 📖 ReAppzuku — FAQ

> Complete guide to configuring and using the app

---

## Table of Contents

- [What is ReAppzuku?](#what-is-reappzuku)
- [Requirements](#requirements)
- [Background operation setup](#background-operation-setup)
- [Quick start](#quick-start)
- [Manual control](#manual-control)
- [Main](#main)
  - [Toolbar](#toolbar)
  - [App triggers](#app-triggers)
- [Settings](#settings)
  - [Information](#-information)
  - [Appearance](#-appearance)
  - [Automation](#️-automation)
  - [Auto-Kill settings](#-auto-kill-settings)
  - [Additional tools](#-additional-tools)
  - [About](#ℹ️-about)
- [Statistics and logs](#-statistics-and-logs)
- [Protected apps](#protected-apps)
- [FAQ](#faq)

---

## What is ReAppzuku?

**ReAppzuku** is an app for managing background processes on Android. It force-stops unnecessary background apps, freeing up RAM and extending battery life.

---

## Requirements

| Requirement | Description |
|---|---|
| **Android** | 6.0 and above. Background restrictions are only available on Android 11+ |
| **Root** or **Shizuku** | One of the two is required for features to work |

### Root vs Shizuku

- **Root** — preferred mode, used automatically if available
- **Shizuku** — a root-free alternative. Installed from the Play Store, requires initial setup via ADB or MIUI/HyperOS developer mode

> The current operating mode is always shown in **Settings → Information → Operating mode**

---

## Background operation setup

For ReAppzuku to run stably and not be killed by the system, you need to configure permissions correctly. The required steps depend on your firmware.

---

### Battery optimization (all firmwares)

The most important step. If not disabled, the system will periodically kill ReAppzuku.

**Settings → Apps → ReAppzuku → Battery → Unrestricted**

Or via the system dialog:
**Settings → Battery → Battery optimization → All apps → ReAppzuku → Don't optimize**

---

### Pinning in Recents (all firmwares)

Open the Recents screen (square button or swipe from the bottom), find the ReAppzuku card, and tap the **lock icon** 🔒. This prevents the app from being unloaded when Recents is cleared.

---

### MIUI / HyperOS (Xiaomi, Redmi, POCO)

<details>
<summary>Expand instructions</summary>

**Autostart:**
Settings → Apps → Manage apps → ReAppzuku → Autostart → Enable

**Background activity:**
Settings → Apps → Manage apps → ReAppzuku → Battery saver → No restrictions

**Lock in Recents:**
Recents screen → long-press the ReAppzuku card → tap the lock icon

**Additional (MIUI 12+):**
Settings → Apps → Manage apps → ReAppzuku → Other permissions → Run in background → Allow

</details>

---

### One UI (Samsung)

<details>
<summary>Expand instructions</summary>

**Allow background activity:**
Settings → Device care → Battery → Background usage limits → Sleeping apps → make sure ReAppzuku is not in the list

**Disable Adaptive battery:**
Settings → Device care → Battery → More battery settings → Adaptive battery → Off (optional, if issues persist)

**Autostart:**
Settings → Apps → ReAppzuku → Battery → Unrestricted

</details>

---

### ColorOS / OxygenOS (OPPO, OnePlus, Realme)

<details>
<summary>Expand instructions</summary>

**Autostart:**
Settings → App management → ReAppzuku → Autostart → Enable

**Background activity:**
Settings → App management → ReAppzuku → Battery saver → No restrictions

**Additional:**
Settings → Battery → Battery optimization → ReAppzuku → Don't optimize

</details>

---

### Flyme (Meizu)

<details>
<summary>Expand instructions</summary>

**Autostart:**
Settings → Permissions → Autostart → ReAppzuku → Enable

**Background activity:**
Settings → Permissions → Background execution → ReAppzuku → Enable

**App security:**
Security Center → Permission manager → ReAppzuku → enable all permissions

</details>

---

### OriginOS / Funtouch OS (Vivo)

<details>
<summary>Expand instructions</summary>

**Autostart:**
Settings → Apps → App management → ReAppzuku → Permissions → Autostart → Enable

**Background activity:**
Settings → Apps → App management → ReAppzuku → Power consumption → High background performance

</details>

---

### MagicOS (Honor)

<details>
<summary>Expand instructions</summary>

**Autostart:**
Settings → Apps → App launch → ReAppzuku → Manual → Autostart, Background activity

**Battery:**
Settings → Battery → App launch → ReAppzuku → No restrictions

</details>

---

### How to verify everything is configured correctly

After setup:
1. Enable the **Background service** in ReAppzuku settings
2. Lock the screen for 10–15 minutes
3. Unlock and open ReAppzuku — the service should be active
4. If the service stopped — repeat the steps for your firmware

> 💡 Up-to-date instructions for specific devices can also be found at [dontkillmyapp.com](https://dontkillmyapp.com)

---

## Quick start

1. Install and open ReAppzuku
2. Grant Root access or set up Shizuku
3. The main screen will show a list of active background apps
4. Select apps and tap the **Kill** button — or configure automation

---

## Manual control

### Quick Tiles

Added to the notification shade:

| Tile | Action |
|---|---|
| **Stop app** | Kills the current foreground app |
| **Stop background apps** | Runs Auto-Kill with your whitelist/blacklist settings |

### Widget

A home screen widget — one tap runs Auto-Kill and shows the current RAM state.

### Shortcut

A static shortcut via long-press on the app icon — kills the current foreground app.

---

## Main

The main screen shows a list of all active background apps with real-time RAM and CPU usage. At the top — overall stats: number of active apps and current RAM load.

### Toolbar

Three buttons in the top toolbar:

| Button | Action |
|---|---|
| 🔍 Search | Filter the list by app name or package |
| 🔽 Sort | Configure the display order |
| ☑️ Select all | Select all apps for Kill in one tap |

### Sorting

The list can be sorted by:
- **Default** — user apps first, then system apps
- **RAM usage: High → Low / Low → High**
- **CPU usage: High → Low / Low → High**
- **Name A → Z** / **Name Z → A** — alphabetical order

You can also toggle visibility of system apps and persistent apps.

### App actions

Tapping an app in the list opens a quick actions menu:

- **App info** — opens the standard system app info page
- **App triggers** — detailed analysis of background activity causes (see below)
- **Uninstall** — remove the app from the device (unavailable for system apps)
- **Add to...** — quickly add to one of the lists:
  - Whitelist
  - Blacklist
  - Hidden
  - Background restriction

### App triggers

Triggers is a deep diagnostic tool that analyzes the **real causes** of an app's background activity at the system level. Instead of guessing, you see precise technical facts: what is keeping the app alive, how often it wakes up, and whether it has active network connections right now.

Analysis runs across **40 independent factors** using system commands and parsing their output in real time.

---

#### App status (active / background / cached)
Determined by process priority in Linux kernel (oom_score_adj). This is same value Android uses to decide which processes to kill when memory is low. 
- **Active** — means app is in the foreground or holds a system resource (service, alarm, etc.). 
- **Background** — means it is running silently but considered necessary by the system.
- **Cached** — means the process is alive but Android may kill it at any moment.

---

**Triggers types:**

#### Actual

These triggers mean the app is consuming resources **at this very moment**.

- **Chain Launch**
Identifies who started this process and by what mechanism. Two methods are detected:\
**Direct call** — another app explicitly started this process via a service or activity (`callingPackage` / `clientPackage`).\
**Broadcast** — the app was started by a broadcast sent from a third-party app. Shows the sender's name and the action, e.g. `CONNECTIVITY_CHANGE`.

- **Foreground Service**
Detects active foreground services — those that hold a persistent notification and cannot be killed by the system. Shows the specific service class name, its type (media playback, location, connected device, etc.), and whether it can be stopped along with the task.

- **Sticky Service**
Services declared with `START_STICKY` that Android restarts automatically after being killed. This is why some apps come back instantly — Android itself brings them back.

- **Service Bindings**
Identifies which other apps hold an active binder connection to this one. For example, Google Play Services or push notification services may keep the app in memory via binding. Shows a list of "holders" with app names.

- **WakeLock**
Detects active sleep locks — `PARTIAL_WAKE_LOCK` (CPU stays on, screen off) and `FULL_WAKE_LOCK` (screen stays on too). Shows the lock tag, type, and how long it has been held.

- **Network Activity**
Reads `/proc/net/tcp` and `/proc/net/tcp6` directly to find active TCP connections with `ESTABLISHED` status. Also shows total traffic (inbound and outbound) via `dumpsys netstats` — only traffic above 10 KB is reported. An active connection means the app is communicating with a server right now.

- **Sensors**
Detects active hardware sensor subscriptions: accelerometer, gyroscope, barometer, GPS, heart rate monitor, and others. Shows sensor names and polling frequency where available.

- **Location Requests**
Identifies active location requests: accuracy level (HIGH_ACCURACY, BALANCED, LOW_POWER), whether the request is foreground or background, and the minimum update interval. Background high-accuracy location is the most battery-intensive case.

- **Audio Focus**
Detects whether the app is holding audio focus — either exclusively (GAIN) or transiently (duck/transient). Also checks for an active `MediaSession`: shows its playback state (PLAYING, PAUSED, BUFFERING, etc.) and session tag. An unreleased paused session is a common reason a media app stays in memory.

- **BLE Scan / GATT Connection**
Detects active Bluetooth Low Energy scanning and open GATT connections to peripheral devices. BLE scanning in `LOW_LATENCY` mode prevents the CPU from sleeping. An active GATT connection keeps the process alive for the duration of the connection.

- **WAKE_LOCK**
The app acquired a CPU wakelock via AppOps — the processor was kept awake on its behalf. Shows how long ago the lock was last acquired.

- **START_FOREGROUND**
The app started a foreground service recently. Shows how long ago it was launched.

- **ACTIVITY_RECOGNITION**
The app is subscribed to motion updates via the Activity Recognition API — it periodically receives movement data (walking, running, in vehicle, etc.) in the background. Shows how long ago the last update was received.

---

#### Can Wake Up App at Any Time

These triggers mean the system **can launch or resume** the app without any user action.

- **Alarms**
Analyzes active `AlarmManager` alarms: whether any are wakeup alarms (wake the device from sleep), how frequently they fire (under 2 minutes is high severity), and whether exact alarms (`setExact`) are used. Also detects `ALLOW_WHILE_IDLE` alarms that fire even in Doze mode.

- **Jobs / WorkManager**
Checks the `JobScheduler` queue for active and pending jobs. WorkManager tasks, sync jobs, and periodic operations are all registered here — they are what wakes the app on a schedule. Shows job constraints (network type, charging required, idle required) and stop reasons from recent history.

- **PendingIntents**
Shows registered pending intents by type: Activity, Service, and Broadcast. A PendingIntent means the system can launch the app at any moment — triggered by a notification, alarm, or external event. Also detects alarm- and push-related intents specifically.

- **Excessive Wakeups**
A historical summary from `dumpsys batterystats` of how many times the app actually woke the device: broken down by alarms, jobs, GCM/FCM messages, and broadcasts. High counts here confirm that scheduled triggers are firing frequently in practice.

- **Content Observers**
Detects registered `ContentObserver` subscriptions. When the observed URI changes (contacts, media library, settings, calendar, etc.) the system delivers a callback — waking the app if it is not running. The more observers registered, the more potential wake-up sources exist.

- **Push Notifications (FCM)**
Detects Firebase Cloud Messaging registration in the app's manifest: a `FirebaseMessagingService` or FCM broadcast receiver. Google Play Services can wake this app at any time when a push message arrives, regardless of battery optimization settings.

- **RUN_IN_BACKGROUND**
The system battery policy explicitly permits this app to run in the background. It will not be suspended when the screen is off.

- **RUN_ANY_IN_BACKGROUND**
The app is fully exempted from battery optimization — it has unrestricted background execution with no system-imposed limits.

- **SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM**
The app has permission to schedule exact alarms that fire at a precise time regardless of Doze mode or battery saver.

- **USER_INTERACTION**
The app received an explicit user interaction signal recently, which may have triggered a background launch. Shows how long ago it occurred.

---

#### Other

Passive factors that influence background behavior but do not indicate immediate activity.

- **Broadcast Receivers**
Lists all system events the app is subscribed to in its manifest: network changes, charger connection, timezone change, screen on/off, and others. Subscriptions to `BOOT` and `CONNECTIVITY` events are flagged as potentially aggressive.

- **Boot Receivers**
Checks whether the app is registered for system startup events: `BOOT_COMPLETED` (after full boot) and `LOCKED_BOOT_COMPLETED` (before the screen is even unlocked). The latter is a sign of particularly aggressive auto-start behavior.

- **App Standby Bucket**
Shows the system's priority ranking for the app: `ACTIVE` → `WORKING_SET` → `FREQUENT` → `RARE` → `RESTRICTED` → `NEVER`. The higher the status, the fewer background restrictions the system applies. Shows bucket change history where available.

- **Doze Exemption**
Determines whether the app is on the system's Doze whitelist. Such apps do not sleep with the device and retain unrestricted access to the network and alarms even at night.

- **Multiple Processes**
Detects whether the app is running in more than one OS process. Sub-processes (`:sync`, `:remote`, `:push`, etc.) can remain alive independently and are not always stopped when the main process is killed.

- **Accessibility Service / IME**
Checks whether the app is registered as an active Accessibility Service or is the currently selected input method (keyboard). Both are kept permanently alive by the system as long as they are enabled — battery optimization does not apply to them.

- **Device Administrator**
Checks whether the app holds Device Administrator, Device Owner, or Profile Owner rights. Apps with these privileges are protected from force-stop and uninstall through standard means.

- **Content Providers**
Detects registered content providers. If other apps are actively accessing a provider, the system keeps the host process alive to serve their requests.

- **Sync Adapters**
Identifies sync adapters — the mechanism for periodic server synchronization. Shows the account type. Active sync adapters are scheduled by the system independently of the app's own alarm and job logic.

- **Battery Stats History**
A historical summary from `dumpsys batterystats`: wakelock count and total held time, alarm wakeups, jobs, and syncs since the last charge reset. Complements current snapshot data with longer-term context.

- **Background Start**
Detects a hidden background wake by cross-referencing two timestamps from `dumpsys usagestats`: `lastTimeUsed` (any activity) and `lastTimeForeground` (last time the user actually opened the app). If the app was active within the last 10 minutes but has not been in the foreground for significantly longer, it was woken in the background without any user interaction. Also shows total foreground time for context.

> 💡 If an app comes back after Kill — Triggers will show you why. Recommended use **Background restrictions** → **Soft/Hard/Manual restriction** depending on triggers.\

> 🔷 For root users: You can also use very useful [Blocker](https://github.com/lihenggui/blocker) with ReAppzuku. The ReAppzuku+Blocker combination will give you a new level of app control.

---

## Settings

### 🔵 Information

#### ReAppzuku operating mode

Shows the current mode of access to system commands: **Root**, **Shizuku**, or **No access**. Informational only, not editable.

#### Help

Link to this FAQ.

---

### 🎨 Appearance

#### App theme

Choose a theme: system, light, dark, or AMOLED.

#### Color accent

Choose the app's color accent: indigo, crimson, forest green, amber, and other shades.

#### Notifications

Customize app notifications. Important notifications include background service notifications and permission error notifications. 

---

### ⚙️ Automation

> ⚠️ All options in this section require the **Background service** to be enabled

#### Background service

The main automation toggle. Starts a persistent background process for ReAppzuku. Without it, periodic Auto-Kill and Auto-Kill on screen lock will not work.

#### Periodic Auto-Kill

Automatically performs Kill (force-stop) of apps on an interval while the background service is running.

#### Auto-Kill interval

| Interval | Description |
|---|---|
| 10 seconds | Maximum aggressive cleanup |
| **18 seconds** | Default |
| 30 seconds | Moderate cleanup |
| 1 minute | Soft cleanup |
| 5 minutes | Minimal intervention |

#### Kill on screen off

Performs Kill (force-stop) at the moment the screen locks. Useful for cleaning up every time you put your phone down.

#### Kill on RAM load

An additional condition — Kill triggers **only if** RAM usage exceeds the selected threshold. Applies to both periodic Kill and screen-off Kill.

| Threshold | Description |
|---|---|
| 75% | Early cleanup |
| **80%** | Default |
| 85–95% | Cleanup only when memory is genuinely low |
| 100% | Only in critical situations |

---

### 🎯 Auto-Kill settings

#### Auto-Kill mode

Determines **which** apps Auto-Kill affects.

**🛡️ Whitelist** — kills all background apps **except** those added to the whitelist. Use this for maximum cleanup.

**🎯 Blacklist (default)** — kills **only** apps from the blacklist. Use this to selectively stop specific apps without touching others.

#### Auto-Kill type
This setting is intended for situations where your firmware conflicts with the app. If you notice strange behavior with your firmware, change the type to 'am kill'.

#### White / Black list

The list of apps for the selected mode. Depending on the mode, one of the two lists is displayed.

---

### 🔧 Additional tools

#### Background restrictions

> Available only on **Android 11+**

Uses Android's system mechanism (`appops`) to **block an app from running in the background at the OS level**. A deeper tool than a regular Kill.

| | Regular Kill | Background restrictions |
|---|---|---|
| How it works | Force-stops the process | Prevents Android from launching the process in the background |
| Can restart | ✅ Yes | ❌ No |
| Persists after reboot | ❌ No | ✅ Yes |
| Requires Android 11+ | ❌ No | ✅ Yes |

**Types of restrictions**
- Soft (RUN_ANY_IN_BACKGROUND ignore)\
Blocks the app from auto-starting at a stricter level than standard activity settings.\
**How it works**: If you opened an app and minimized it, it will continue running (while in Recents). But on its own (at night or in the background) it will not wake up until you open it.

- Hard (Soft restriction + START_FOREGROUND ignore + RECEIVE_BOOT_COMPLETED ignore + Removal from battery optimization whitelist)\
Blocks any background activity for the app.\
**How it works**: As soon as you minimize the app or switch to another one, the system immediately "kills" it. It cannot keep itself in memory for a single second without your direct attention (even if it appears in Recents). Be careful with the Hard restriction, as it may completely prevent an app from performing any background operations (downloading files, playing media, long-running internal tasks).

- **Manual**
You choose which restrictions to apply to the app yourself.\
**How it works**: ReAppzuku will only apply the restrictions you select. Read below for details on each restriction.

---
**Available restrictions**:

- **RUN_ANY_IN_BACKGROUND**
Prevents the app from starting any background processes or services without explicit user interaction. This is the broadest restriction — the one used in **Soft** mode.\
**Blocks:** background service launches, sync operations, deferred tasks (JobScheduler, WorkManager).\
**Does not block:** foreground services (with notification), already running processes.


- **RUN_IN_BACKGROUND**
A more targeted background execution restriction. Blocks service launches via `startService()` while the app is in the background.\
**Blocks:** background services started by the app without user involvement.\
**Does not block:** foreground services, alarm-triggered tasks, broadcast receivers.


- **START_FOREGROUND**
Prevents the app from promoting a service to foreground state (with a persistent notifiдcation). Without this, the app cannot show a "running in background" notification and keep its process alive.\
**Blocks:** calls to `startForeground()` — the app cannot create a sticky notification or keep its service running.\
**Does not block:** regular app notifications, background tasks via JobScheduler.


- **START_FOREGROUND_SERVICES_FROM_BACKGROUND**
Prevents launching a foreground service while the app itself is in the background. Introduced in Android 12 as a separate restriction on top of `START_FOREGROUND`.\
**Blocks:** attempts to start a foreground service when the app is not actively on screen.\
**Does not block:** foreground services launched while the app is in the foreground.\


- **WAKE_LOCK**
Prevents the app from keeping the CPU active when the screen is off. Without a wake lock, the system can put the CPU to sleep and stop the app's background operations.\
**Blocks:** CPU retention via `PowerManager.WakeLock` — the app cannot prevent the phone from sleeping.\
**Does not block:** app operation while the screen is on.


- **ALARM_WAKEUP**
Prevents the app from waking the device using exact alarms (`AlarmManager.setExactAndAllowWhileIdle` and similar). Without this, the app's alarms cannot wake the phone from deep sleep.\
**Blocks:** exact alarm tasks that wake the device — the app cannot schedule a forced wakeup by timer.\
**Does not block:** inexact timers, JobScheduler tasks.\


- **RECEIVE_BOOT_COMPLETED**
Prevents the app from receiving the system `BOOT_COMPLETED` broadcast after the device restarts. This is the mechanism most apps use to add themselves to autostart.\
**Blocks:** automatic app launch on system boot.\
**Does not block:** manually launching the app after a reboot.\


- **INTERACT_ACROSS_PROFILES**
Prevents the app from interacting with other work profiles (e.g. Android work profile or multiple accounts). Primarily relevant on enterprise devices.\
**Blocks:** cross-profile calls and data transfer between the primary and work profiles.\
**Does not block:** app operation within a single profile.

---

## Mode Comparison

| Restriction | Soft | Hard | Manual |
|---|:---:|:---:|:---:|
| RUN_ANY_IN_BACKGROUND | ✓ | ✓ | selectable |
| RUN_IN_BACKGROUND | — | ✓ | selectable |
| START_FOREGROUND | — | ✓ | selectable |
| START_FOREGROUND_SERVICES_FROM_BACKGROUND | — | ✓ | selectable |
| WAKE_LOCK | — | ✓ | selectable |
| ALARM_WAKEUP | — | ✓ | selectable |
| RECEIVE_BOOT_COMPLETED | — | ✓ | selectable |
| INTERACT_ACROSS_PROFILES | — | ✓ | selectable |

---

Statuses in the list:
- **Saved in ReAppzuku** — saved, but the status from the system is unknown (no permission)
- **Saved in ReAppzuku, but not applied** — saved, but Android has not applied the restriction
- **Restricted, but not by ReAppzuku** — restricted by Android or another app

> 👀 ReAppzuku periodically checks integrity of Background Restrictions imposed on apps. If the system resets any restrictions, it restores them.

#### Re-apply background restrictions

Manually re-applies all saved restrictions. After a reboot this happens **automatically** when the background service starts.

#### Restriction Scheduler
Schedule restriction actions for your applications.

> Only apps with an active **Background Restriction** (soft or hard) are shown in the list.
> Already scheduled entries are indicated by a 🕐 icon next to the app, including the scheduled time.

Tap an app to open its scheduler settings:

**Protect from**
Select the restrictions the app will be temporarily protected from.

**Time window**
Set the start time (restrictions lifted) and end time (restrictions restored).
The app will be force-stopped before restrictions are restored.

**On activation**
Action to perform when restrictions are lifted. Available options:

- **None** — no additional actions.
- **Launch component** — opens the app's component picker (Activity, Service, Receiver, etc.).

> Number of available schedule plans is limited to 15 applications for security of ReAppzuku itself.

#### Sleep Mode

Completely **freezes** selected apps when the device is idle. Unlike background restrictions — the app simply cannot start, it is fully disabled by the system.

How it works:
1. Screen turns off → a timer starts
2. Timer expires → selected apps are frozen
3. Screen turns on and is unlocked → apps are unfrozen automatically

> ⚠️ Only works with user apps — system apps cannot be frozen (limitation of modern Android)

> ⚠️ When Sleep Mode is enabled, the app restarts — this is necessary for correct initialization

#### Sleep Mode app list

Choose which apps will be frozen in Sleep Mode.

#### Freeze timer

The inactivity period after which freezing triggers: from **5 to 60 minutes** (default 60 minutes).

#### Clear cache for all apps

Runs the system command `pm trim-caches` — clears the cache of all apps at once.

#### Hidden apps

Apps in this list are not shown in the main list and are never affected by Auto-Kill. Useful for service processes you don't need to see.

#### Backup and restore

Export and import all settings to a JSON file. Saves the whitelist, blacklist, hidden apps, background restrictions, Sleep Mode, and all automation parameters.

---

### ℹ️ About

#### Source code

Link to the GitHub repository.

#### Check for updates

Manually queries Github repository and displays information about a new release if one is found.
Updates are also checked automatically once a day.

---

### 📊 Statistics and Logs

Statistics and Logs are now available as a **separate screen** with detailed usage data for all monitored apps.

#### App consumption

At the top of the screen, ReAppzuku's **own resource usage** is shown — RAM, CPU, and battery — so you can always assess the app's footprint on your device.

#### Resource usage charts

Interactive charts showing RAM, CPU, and battery consumption across all tracked apps. Switch between chart types using the **arrow buttons**.

| Period | Available |
|---|---|
| 2 hours | Last 2 hours |
| 6 hours | Last 6 hours |
| 12 hours | Last 12 hours |
| 24 hours | Last 24 hours |

> 💡 Tap on any **app in the chart legend** to open its **personal activity graph**

#### Auto-Kill log

Shows activity over the last **12 hours**: number of Auto-Kills, restarts, freed RAM, and the time of the last event for each app.

> 💡 Apps that restart more than 3 times are good candidates for Background restrictions.

#### Top offenders

App ranking by combined score (Kills + restarts + RAM volume). Filterable by: 12 hours / 24 hours / 7 days / all time.

> 💡 Score shows how aggressively an app resists background restrictions.
>
> `Score = kills × 1 + relaunches × 2 + freed RAM × 0.01`
>
> • Kill (+1) — the app was force-stopped.\
> • Relaunch (+2) — the app restarted after being killed; counts double because it actively fights back.\
> • RAM — every 100 MB of freed memory adds +1 point; usually a minor factor.

#### Background Restrictions log

Detailed log of background restriction operations. Stored in cache, maximum 200 entries.

| Status | Meaning |
|---|---|
| `Sent` | Command executed successfully (may not be applied by the system) |
| `Applied` | Restriction confirmed by the system (100% result) |
| `NOT APPLIED` | Command executed, but the system did not apply the change |
| `ERROR` | Command finished with an error |
| `Skipped` | Operation was not performed (no permission, Android < 11, etc.) |
| `Verification unavailable` | Could not query the actual state from the system |
| `Removed from whitelist` | App removed from battery optimization exceptions |
| `Restored to whitelist` | App restored to battery optimization exceptions |

---

## Protected apps

The following apps are **never affected** by Auto-Kill and other restrictions regardless of settings:

### Core Android & Google
- **ReAppzuku** (itself)
- Google Play Services & Google Services Framework
- System UI
- Android Settings
- Phone / Dialer, Contacts, SMS Service, Telecom Server
- Bluetooth
- External storage & Media Module
- Package Installer & Permission Controller (AOSP and Google variants)
- Gboard (Google Keyboard)
- ADB/Shell service
- Android Keychain (TLS/VPN/Wi-Fi)
- Settings, Telephony & SMS/MMS providers
- NFC
- Network Stack, Tethering stack, DNS resolver, VPN dialogs

### Shizuku
- Shizuku (both `rikka.shizuku.common` and `moe.shizuku.privileged.api`)

### Root managers
- Magisk
- KernelSU
- KernelSU Next
- APatch
- SukiSU / SukiSU Ultra

### OEM system apps (per manufacturer)
| Manufacturer | Protected apps |
|---|---|
| **Xiaomi / MIUI / HyperOS** | Security Center, Home launcher, Wallpaper, Camera, Security Guard, Core Services, PowerKeeper |
| **Samsung (One UI)** | Device Care, Device Security, One UI Home, Phone UI, Telecom Server |
| **Oppo / Realme / OnePlus (ColorOS)** | Phone Manager, System Launcher, Smart Assistant |
| **Vivo / iQOO (Funtouch / OriginOS)** | iManager, Vivo Launcher |
| **Huawei / Honor (EMUI / MagicOS)** | System Optimizer, Huawei Home, Honor System Manager |

### Dynamically detected
- Current keyboard (detected automatically at runtime)
- Current launcher (detected automatically at runtime)

---

## FAQ

**❓ An app restarts immediately after Kill — what should I do?**

Add it to **Background restrictions**. This will prevent Android from restarting it in the background at the system level.

---

**❓ Background restrictions are lost after a reboot**

Enable the **Background service** — when it starts after a reboot, it automatically restores all saved restrictions.

---

**❓ Which mode should I choose — whitelist or blacklist?**

Whitelist — if you want to stop everything except what's important. Blacklist — if you want to stop only specific apps without touching the rest.

---

**❓ Is the background service needed for manual Kill?**

No. Manual Kill from the main screen, quick tiles, widget, and shortcut all work without the background service.

---

**❓ Is it safe to stop system apps?**

No. Stopping or restricting system apps can cause device instability, freezes, lost notifications, and boot loops. ReAppzuku warns you when you attempt to affect system apps.

---

**❓ What is the difference between Sleep Mode and Background restrictions?**

Background restrictions — the app cannot **start** in the background, but remains installed and visible. Sleep Mode — the app is completely **frozen** by the system, as if disabled, until the screen is unlocked.

---

**❓ Shizuku stopped working after a reboot**

Shizuku requires re-activation after every reboot (unless wireless mode is used). Open the Shizuku app and start the service again.

---

**❓ The app won't stop no matter what — what should I do?**

Open the app's menu on the main screen and tap **Triggers**. It will show exactly what's keeping the process alive: a foreground service, wakelock, sticky service, or a binding from another app. Based on the trigger, apply **Background Restrictions** — soft, hard, or manual with the specific options you need.

---

**❓ What's the difference between Sleep Mode and Hard Restriction?**

Both aggressively limit background activity, but in different ways. Sleep Mode **freezes** the app when the screen is off and unfreezes it on unlock — it follows the screen state. Hard Restriction is **always active**: the app can't live in the background even when the screen is on and you've just switched away. Use Sleep Mode for overnight freezing. Use Hard Restriction for chronically aggressive apps.

---

**❓ Why would I change the Kill Type from force-stop to am kill?**

`am force-stop` is a hard stop — kills all processes and clears the app's state. `am kill` is softer — only terminates background processes without touching foreground ones. Switch only if you notice odd behaviour in other apps or firmware conflicts after using `force-stop` — on some devices it's too aggressive.
