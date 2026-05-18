# 📖 ReAppzuku — FAQ

> Complete guide to configuring and using the app

---

## Table of Contents

- [What is ReAppzuku?](#what-is-reappzuku)
- [Requirements](#requirements)
- [Background Survival Setup](#background-survival-setup)
- [Quick Start](#quick-start)
- [Manual Control](#manual-control)
- [Main](#main)
  - [Toolbar](#toolbar)
  - [App Triggers](#app-triggers)
- [Settings](#settings)
  - [Information](#-information)
  - [Appearance](#-appearance)
  - [Automation](#️-automation)
  - [Auto-Kill Settings](#-auto-kill-settings)
  - [Advanced Tools](#-advanced-tools)
  - [About](#ℹ️-about)
- [Statistics & Logs](#-statistics--logs)
- [Protected Apps](#protected-apps)
- [FAQ](#faq)

---

## What is ReAppzuku?

**ReAppzuku** is a background process manager for Android. It force-stops unnecessary background apps to free up RAM and extend battery life.

---

## Requirements

| Requirement | Details |
|---|---|
| **Android** | 6.0 or higher. Background restrictions are only available on Android 11+ |
| **Root** or **Shizuku** | One of the two is required for the app to function |

### Root vs Shizuku

- **Root** — preferred mode, used automatically if available
- **Shizuku** — root-free alternative. Installed from the Play Store, requires initial setup via ADB or MIUI/HyperOS developer mode

> The current operating mode is always shown in **Settings → Information → Operating Mode**

---

## Background Survival Setup

For ReAppzuku to run reliably without being killed by the system, you need to configure the right permissions. The exact steps depend on your firmware.

---

### Battery Optimization (all firmwares)

The most important step. If not disabled, the system will periodically kill ReAppzuku.

**Settings → Apps → ReAppzuku → Battery → Unrestricted**

Or via the system dialog:
**Settings → Battery → Battery Optimization → All Apps → ReAppzuku → Don't Optimize**

---

### Pin in Recents (all firmwares)

Open the recent apps screen (square button or swipe from the bottom), find the ReAppzuku card, and tap the **lock icon** 🔒. This prevents the app from being unloaded when you clear recents.

---

### MIUI / HyperOS (Xiaomi, Redmi, POCO)

<details>
<summary>Expand instructions</summary>

**Autostart:**
Settings → Apps → Manage Apps → ReAppzuku → Autostart → Enable

**Background activity:**
Settings → Apps → Manage Apps → ReAppzuku → Battery Saver → No restrictions

**Lock in recents:**
Recents → long-press the ReAppzuku card → tap the lock icon

**Additional (MIUI 12+):**
Settings → Apps → Manage Apps → ReAppzuku → Other permissions → Run in background → Allow

</details>

---

### One UI (Samsung)

<details>
<summary>Expand instructions</summary>

**Allow background activity:**
Settings → Device Care → Battery → Background usage limits → Sleeping apps → make sure ReAppzuku is not listed

**Disable Adaptive Battery:**
Settings → Device Care → Battery → More battery settings → Adaptive Battery → Off (optional, if issues persist)

**Autostart:**
Settings → Apps → ReAppzuku → Battery → Unrestricted

</details>

---

### ColorOS / OxygenOS (OPPO, OnePlus, Realme)

<details>
<summary>Expand instructions</summary>

**Autostart:**
Settings → App Management → ReAppzuku → Autostart → Enable

**Background activity:**
Settings → App Management → ReAppzuku → Battery Saver → Don't restrict

**Additional:**
Settings → Battery → Battery Optimization → ReAppzuku → Don't Optimize

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
Security Center → Permission Manager → ReAppzuku → enable all permissions

</details>

---

### OriginOS / Funtouch OS (Vivo)

<details>
<summary>Expand instructions</summary>

**Autostart:**
Settings → Apps → Manage Apps → ReAppzuku → Permissions → Autostart → Enable

**Background activity:**
Settings → Apps → Manage Apps → ReAppzuku → Power Consumption → High Background Performance

</details>

---

### MagicOS (Honor)

<details>
<summary>Expand instructions</summary>

**Autostart:**
Settings → Apps → App Launch → ReAppzuku → Manual → Autostart, Background Activity

**Battery:**
Settings → Battery → App Launch → ReAppzuku → Don't restrict

</details>

---

### How to Verify Everything is Set Up Correctly

After completing the setup:
1. Enable the **Background Service** in ReAppzuku settings
2. Lock the screen for 10–15 minutes
3. Unlock and open ReAppzuku — the service should still be active
4. If the service stopped — repeat the steps for your firmware

> 💡 Device-specific instructions can also be found at [dontkillmyapp.com](https://dontkillmyapp.com)

---

## Quick Start

1. Install and open ReAppzuku
2. Grant Root access or set up Shizuku
3. The main screen will show a list of active background apps
4. Select apps and tap **Kill** — or configure automation

---

## Manual Control

### Quick Tiles

Added to the notification shade:

| Tile | Action |
|---|---|
| **Kill App** | Kills the current foreground app |
| **Kill Background Apps** | Runs Auto-Kill with your whitelist/blacklist settings |

### Widget

Home screen widget - shows Auto-Kill statistics for the last 12 hours and current RAM load.

### Shortcut

A static shortcut via long-pressing the app icon — kills the current foreground app.

---

## Main

The main screen shows a list of all active background apps with real-time RAM and CPU usage. The top section displays overall stats: the number of active apps and current RAM load.

### Toolbar

Three buttons in the top toolbar:

| Button | Action |
|---|---|
| 🔍 Search | Filter the list by app name or package |
| 🔽 Sort | Configure the display order |
| ☑️ Select All | Select all apps for Kill in one tap |

### Sort

The list can be sorted by:
- **Default** — user apps first, then system apps
- **RAM Usage: High → Low / Low → High**
- **CPU Load: High → Low / Low → High**
- **Name A → Z** / **Name Z → A** — alphabetical order

You can also toggle the display of system and persistent apps.

### App Actions

Tapping an app in the list opens a quick action menu:

- **App Info** — opens the standard system app page
- **App Triggers** — detailed analysis of background activity causes (see below)
- **Uninstall** — removes the app from the device (unavailable for system apps)
- **Add to...** — quickly add to one of the lists:
  - Whitelist
  - Blacklist
  - Hidden
  - Background Restriction (Soft)

### App Triggers

Triggers is a deep diagnostic tool that analyzes the **real reasons** for an app's background activity at the system level. Instead of guesswork — precise technical facts: what is keeping the app in memory, how often it wakes up, and whether it has active network connections right now.

The analysis covers **40 independent factors** via system commands, sorting their output in real time.

---

#### App Status (active / background / cached)
Determined by the process priority in the Linux kernel (oom_score_adj) — the same value Android uses to decide which processes to terminate when memory is low.
- **Active** — the app is in the foreground or holds a system resource (service, alarm, etc.).
- **Background** — running silently, but the system considers it necessary.
- **Cached** — the process is alive, but Android may terminate it at any moment.

---

**Trigger types:**

#### Actual

These triggers indicate that the app is consuming resources **right now**.

- **Chain Launch**
Identifies who launched this process and how. Two mechanisms are detected:\
  - **Direct call** — another app explicitly started this process via a service or activity (`callingPackage` / `clientPackage`).\
  - **Broadcast** — the app was launched by a broadcast from a third-party app. Shows the sender name and action, e.g. `CONNECTIVITY_CHANGE`.

- **Foreground Service**
Detects active foreground services — those that hold a persistent notification and cannot be killed by the system. Shows the service class name, its type (media playback, location, connected device, etc.), and whether it can be stopped along with the task.

- **FG Notification Channel**
Supplements foreground service info: shows the notification channel importance. A notification with URGENT or HIGH importance displays as a pop-up banner — extremely difficult for the system to suppress, making force-stopping the app nearly impossible.

- **Sticky Service**
Services declared with `START_STICKY`, which Android automatically restarts after killing. This is why some apps come back instantly — Android itself brings them back.

- **Held by bindings**
Identifies which other apps hold an active binder connection to this one. For example, Google Play Services or push notification services may keep an app in memory via binding. Shows a list of "holders" with app names.

- **WakeLock**
Detects active wake locks — `PARTIAL_WAKE_LOCK` (CPU running, screen off) and `FULL_WAKE_LOCK` (screen stays on too). Shows the lock tag, type, and hold duration.

- **Network activity**
Analyses app's network activity via `dumpsys netstats` and `dumpsys connectivity`. Shows total traffic (inbound and outbound) — only traffic exceeding 10 KB is counted — as well as active TCP connections with `ESTABLISHED` status. An active connection means the app is communicating with a server right now

- **Sensors**
Detects active subscriptions to hardware sensors: accelerometer, gyroscope, barometer, GPS, heart rate monitor, and others. Shows sensor names and polling rate where available.

- **Location**
Identifies active location requests: accuracy level (HIGH_ACCURACY, BALANCED, LOW_POWER), whether the request is background or active, and the minimum update interval. Background high-accuracy location is the most resource-intensive case.

- **Audio Focus**
Determines whether the app holds audio focus — exclusively (GAIN) or temporarily (duck/transient). Also reports the stream type (MUSIC, VOICE_CALL, ALARM, etc.).

- **Media Session**
Checks for an active `MediaSession`: shows its playback state (PLAYING, PAUSED, BUFFERING, etc.) and session tag. An unclosed paused session is a common reason why a media app stays in memory.

- **BLE Scan**
Detects active Bluetooth Low Energy scanning. BLE scanning internally holds a wake lock and prevents the process from terminating. `LOW_LATENCY` mode is the most power-hungry.

- **GATT Connection**
Detects open connections to Bluetooth peripheral devices via the GATT protocol. An active connection keeps the process alive for its entire duration.

- **AppOps**
  - **WAKE_LOCK** — The app has acquired a CPU wake lock via AppOps — the processor was kept active on its behalf. Shows how long ago the last acquisition occurred.
  - **START_FOREGROUND** — The app recently started a foreground service. Shows how long ago that happened.
  - **ACTIVITY_RECOGNITION** — The app is subscribed to motion updates via the Activity Recognition API — periodically receives data about physical activity (walking, running, in a vehicle, etc.) in the background. Shows how long ago the last update occurred.

---

#### Can wake up app at any time

These triggers indicate that the system **may start or resume** the app without any user action.

- **Alarms**
Analyzes active `AlarmManager` alarms: whether any are wakeup alarms (that wake the device from sleep), how frequently they fire (under 2 minutes — high severity), and whether exact alarms (`setExact`) are used. Also detects `AllowWhileIdle` alarms that fire even in Doze mode.

- **Jobs / WorkManager**
Checks the `JobScheduler` queue for active and pending jobs. WorkManager tasks, sync jobs, and periodic operations are all registered here and are what wakes the app on a schedule. Shows job constraints (network type, charging required, idle mode) and stop reasons from recent history.

- **PendingIntent**
Shows registered pending intents by type: Activity, Service, and Broadcast. A PendingIntent means the system can launch the app at any moment — via a notification, alarm, or external event. Intents linked to alarms and push notifications are highlighted separately.

- **Excessive Wakeups**
A historical summary from `dumpsys batterystats` of how many times the app actually woke the device: broken down by alarms, jobs, GCM/FCM messages, and broadcasts. High counts confirm that scheduled triggers fire frequently in practice.

- **Content Observers**
Detects registered `ContentObserver` subscriptions. When a tracked URI changes (contacts, media library, settings, calendar, etc.), the system delivers a callback — waking the app if it isn't running.

- **Push Notifications (FCM)**
Detects Firebase Cloud Messaging registration in the app's manifest: `FirebaseMessagingService` or an FCM broadcast receiver. Google Play Services can wake this app at any moment upon receiving a push message, regardless of battery optimization settings.

- **AppOps**
  - **RUN_IN_BACKGROUND** — The system battery policy explicitly allows this app to run in the background. It will not be suspended when the screen is off.
  - **RUN_ANY_IN_BACKGROUND** — The app is fully excluded from battery optimization — it has unrestricted background access with no system limitations.
  - **SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM** — The app has permission for exact alarms that fire at a specified time regardless of Doze mode and battery saving.
  - **USER_INTERACTION** — The app recently received a signal indicating an explicit user interaction, which may have triggered a background launch. Shows how long ago this occurred.

---

#### Others triggers

Passive factors that affect background behavior but don't indicate current activity.

- **Broadcast Receivers**
Lists all system events the app is subscribed to in its manifest: network changes, charger connection, timezone changes, screen on/off, and others. `BOOT` and `CONNECTIVITY` subscriptions are flagged as potentially aggressive.

- **Boot autostart**
Checks whether the app is registered for system boot events: `BOOT_COMPLETED` (after full boot) and `LOCKED_BOOT_COMPLETED` (before the screen is unlocked). The latter is a sign of especially aggressive autostart — the app launches before the PIN/password is entered.

- **App Standby Bucket**
Shows the app's priority rank in the system: `ACTIVE` → `WORKING_SET` → `FREQUENT` → `RARE` → `RESTRICTED` → `NEVER`. The higher the status, the fewer background restrictions the system applies. Shows the bucket history where available.

- **Doze Exempt**
Determines whether the app is on the Doze whitelist. Such apps don't sleep with the device and retain unrestricted network and alarm access even at night.

- **Multiple Processes**
Detects whether the app runs across multiple OS processes. Child processes (`:sync`, `:remote`, `:push`, etc.) can stay alive independently and may not terminate when the main process is stopped.

- **Accessibility Service**
Checks whether the app is registered as an active Accessibility Service. Such services are constantly kept alive by the system while enabled — battery optimization does not apply to them.

- **Input Method (IME)**
Checks whether the app is the currently selected input method (keyboard). The system constantly keeps the active IME process alive while it is selected by the user.

- **Device Administrator**
Checks whether the app has Device Administrator, Device Owner, or Profile Owner rights. Apps with such privileges are protected from force-stop and removal by standard means.

- **Content Provider**
Detects registered content providers. If other apps are actively querying the provider, the system keeps the host process alive to handle their requests.

- **Sync Adapter**
Identifies sync adapters — a mechanism for periodic server synchronization. Active adapters are scheduled by the system independently of the app's own alarm and job logic.

- **Broadcast Efficiency**
Shows how many broadcasts were delivered to the app and how many required a cold process start. A high percentage of cold starts means the system regularly kills and restarts the app.

- **Battery usage history**
Stats since the last battery reset: number and total duration of wakelock holds, alarm wakeups, job and sync launches. Supplements the current snapshot with data over a longer period.

- **Background Start**
Identifies hidden background wakeups by comparing two timestamps from `dumpsys usagestats`: `lastTimeUsed` (any activity) and `lastTimeForeground` (the last time the user opened the app). If the app was active within the last 10 minutes but hasn't been in the foreground for significantly longer — it was woken in the background without user interaction. Also shows total foreground time for context.

> 💡 If an app keeps coming back after Kill — Triggers will show the reason. It is recommended to use **Background Restrictions** → **Soft/Hard/Manual restriction** depending on the triggers.\

> 🔷 For users with Root: the app [Blocker](https://github.com/lihenggui/blocker) pairs very well with ReAppzuku. The `ReAppzuku + Blocker` combination gives you a new level of app control.

---

## Settings

### 🔵 Information

#### ReAppzuku Access Mode

Shows the current system command access mode: **Root**, **Shizuku**, or **No Access**. Read-only.

#### Help

Link to this FAQ.

---

### 🎨 Appearance

#### App Theme

Choose a theme: system default, light, dark, or AMOLED.

#### Accent Color

Choose the app's accent color: indigo, crimson, forest green, amber, and other shades.

#### Notifications

Configure how the app sends notifications. Critical notifications include background service status and permission errors.

---

### ⚙️ Automation

> ⚠️ All features in this section require the **Background Service** to be enabled

#### Background Service

The main automation toggle. Starts a persistent ReAppzuku background process. Without it, periodic Auto-Kill and screen-lock Auto-Kill will not work.

#### Periodic Auto-Kill

Automatically kills (force-stops) apps at a set interval while the background service is running.

#### Auto-Kill Interval

| Interval | Description |
|---|---|
| 10 seconds | Maximum aggressive cleanup |
| **18 seconds** | Default |
| 30 seconds | Moderate cleanup |
| 1 minute | Light cleanup |
| 5 minutes | Minimal intervention |

#### Kill on Screen Off

Runs Kill (force-stop) the moment the screen is locked. Useful for cleaning up every time you put your phone down.

#### Kill at RAM Load

An additional condition — Kill only fires **if** RAM usage exceeds the selected threshold. Applies to both periodic Kill and screen-off Kill.

| Threshold | Description |
|---|---|
| 75% | Early cleanup |
| **80%** | Default |
| 85–95% | Cleanup only when memory is genuinely low |
| 100% | Critical situations only |

---

### 🎯 Auto-Kill Settings

#### Auto-Kill Mode

Determines **which** apps Auto-Kill targets.

**🛡️ Whitelist** — kills all background apps **except** those added to the whitelist. Use this for maximum cleanup.

**🎯 Blacklist (default)** — kills **only** apps on the blacklist. Use this to stop specific apps without touching everything else.

#### Auto-Kill Type

This setting is only relevant if the app conflicts with your firmware. If you notice unusual behavior in other apps, try switching the type to `am kill`.

#### Whitelist / Blacklist

The app list for the selected mode. Depending on the mode, one of the two lists is shown.

---

### 🔧 Advanced Tools

#### Background Restrictions

> Available on **Android 11+** only

Uses Android's system mechanism (`appops`) to **prohibit an app from running in the background at the OS level**. A deeper tool than a regular Kill.

| | Regular Kill | Background Restrictions |
|---|---|---|
| How it works | Force-stops the process | Prevents Android from starting the process in the background |
| Can restart | ✅ Yes | ❌ No |
| Persists after reboot | ❌ No | ✅ Yes |
| Requires Android 11+ | ❌ No | ✅ Yes |

**Restriction types:**
- **Soft** (RUN_ANY_IN_BACKGROUND ignore)\
Blocks autostart at a stricter level than standard activity settings.\
**How it works**: If you open the app and switch away — it keeps running (while in recents). But on its own (overnight or in the background) it won't wake up until you open it.

- **Hard** (Soft restriction + START_FOREGROUND ignore + RECEIVE_BOOT_COMPLETED ignore + INTERACT_ACROSS_PROFILES ignore + removal from battery optimization whitelist)\
Blocks any form of background life.\
**How it works**: As soon as you switch away from the app, the system immediately kills it. It cannot hold itself in memory for a single second without your direct attention — even if it appears in recents. Use Hard restriction with caution, as it can fully strip an app of any background operations (file downloads, media playback, long-running internal tasks).

- **Manual**\
You choose which restrictions to apply.\
**How it works**: ReAppzuku applies only the restrictions you select. See details on each restriction below.

---
**Available restrictions:**

- **RUN_ANY_IN_BACKGROUND**
Prevents the app from starting any background processes or services without explicit user interaction. This is the primary and broadest restriction — it is what is used in **Soft** mode.\
**Blocks:** background service starts, sync, deferred tasks (JobScheduler, WorkManager).\
**Does not block:** foreground services (with a notification), already-running processes.

- **RUN_IN_BACKGROUND**
A more targeted background execution restriction. Blocks service starts via `startService()` when the app is in the background.\
**Blocks:** background services started by the app itself without user involvement.\
**Does not block:** foreground services, alarm-triggered tasks, broadcast receivers.

- **START_FOREGROUND**
Prevents the app from promoting a service to foreground (with a persistent notification). Without this permission the app cannot show a "running in background" notification and hold the process alive.\
**Blocks:** calls to `startForeground()` — the app cannot create a sticky notification or keep the service alive.\
**Does not block:** regular app notifications, background tasks via JobScheduler.

- **START_FOREGROUND_SERVICES_FROM_BACKGROUND**
Prevents starting a foreground service when the app itself is in the background. Added in Android 12 as a separate restriction on top of `START_FOREGROUND`.\
**Blocks:** attempts by the app to start a foreground service while not visible on screen.\
**Does not block:** foreground services started while the app is in the foreground.

- **WAKE_LOCK**
Prevents the app from keeping the CPU active when the screen is off. Without a wake lock, the system can put the CPU to sleep and stop the app's background operations.\
**Blocks:** CPU hold via `PowerManager.WakeLock` — the app cannot prevent the phone from sleeping.\
**Does not block:** the app running while the screen is on.

- **ALARM_WAKEUP**
Prevents the app from waking the device via exact timers (`AlarmManager.setExactAndAllowWhileIdle` and equivalents). Without this permission, the app's alarms cannot wake the phone from deep sleep.\
**Blocks:** exact alarm tasks that wake the device — the app cannot schedule a forced wakeup by timer.\
**Does not block:** inexact timers, JobScheduler tasks.

- **RECEIVE_BOOT_COMPLETED**
Prevents the app from receiving the system `BOOT_COMPLETED` broadcast after a reboot. This is the mechanism most apps use to add themselves to autostart.\
**Blocks:** autostart of the app on system boot.\
**Does not block:** manually launching the app after a reboot.

- **INTERACT_ACROSS_PROFILES**
Prevents the app from interacting with other work profiles (e.g., Android work profile or multiple accounts). Primarily relevant on enterprise devices.\
**Blocks:** cross-profile calls and data transfer between the primary and work profiles.\
**Does not block:** the app operating within a single profile.

---

## Restriction types comparison

| Restriction | Soft | Hard | Manual |
|---|:---:|:---:|:---:|
| RUN_ANY_IN_BACKGROUND | ✓ | ✓ | optional |
| RUN_IN_BACKGROUND | — | ✓ | optional |
| START_FOREGROUND | — | ✓ | optional |
| START_FOREGROUND_SERVICES_FROM_BACKGROUND | — | ✓ | optional |
| WAKE_LOCK | — | ✓ | optional |
| ALARM_WAKEUP | — | ✓ | optional |
| RECEIVE_BOOT_COMPLETED | — | ✓ | optional |
| INTERACT_ACROSS_PROFILES | — | ✓ | optional |

---

List statuses:
- **Saved in ReAppzuku** — saved, but the system status is unknown (insufficient permissions)
- **Saved in ReAppzuku, but not applied** — saved, but Android has not applied the restriction
- **Restricted, but not by ReAppzuku** — restricted by Android or another app

> 👀 ReAppzuku periodically checks the integrity of Background Restrictions applied to apps. If the system "resets" any restrictions — they are restored automatically.

#### Re-apply Background Restrictions

Manually re-applies all saved restrictions. After a reboot this happens **automatically** when the background service starts.

#### Restriction Scheduler

Schedule when restrictions should be lifted and restored for specific apps.

> Only apps with an active **Background Restriction** (soft or hard) appear in the list.
> Apps with a scheduled entry show a 🕐 icon next to them, including the scheduled time.

Tapping an app opens the scheduler configuration:

**Protect from**
Select the restrictions the app will be temporarily exempted from.

**Time window**
Specify the start time (restrictions lifted) and end time (restrictions restored).
The app is force-stopped before restrictions are restored.

**On activation**
Action to take when the app's restrictions are lifted. Available options:

- **None** — no additional action.
- **Launch component** — opens the app's component picker (Activity, Service, Receiver, etc.).

> The number of scheduled entries is limited to 15 apps to protect ReAppzuku itself.

#### Sleep Mode

Completely **freezes** selected apps when the device is idle. Unlike background restrictions — the app simply cannot start; it is fully suspended by the system.

How it works:
1. Screen turns off → a timer starts
2. Timer expires → selected apps are frozen
3. Screen turns on and is unlocked → apps are unfrozen automatically

> ⚠️ Only works with user-installed apps — system apps cannot be frozen (limitation of modern Android)

> ℹ️ Enabling Sleep Mode restarts ReAppzuku — this is required for proper initialization

> 💡 If target app was on home screen, his icon may disappear or move after using Sleep Mode. This is a specific Android behavior when using pm disable/enable commands.

#### Sleep Mode App List

Select which apps will be frozen in Sleep Mode.

#### Freeze Timer

The idle period after which freezing triggers: from **5 to 60 minutes** (default: 60 minutes).

#### Clear Cache for All Apps

Runs the system command `pm trim-caches` — clears the cache of all apps at once.

#### Hidden Apps

Apps in this list do not appear in the main list and are never touched by Auto-Kill. Useful for service processes you don't need to see.

#### Backup & Restore

Export and import all settings to a JSON file. Includes whitelist, blacklist, hidden apps, background restrictions, Sleep Mode, and all automation settings.

---

### ℹ️ About

#### Source Code

Link to the GitHub repository.

#### Check for Updates

Makes a manual request to the GitHub repository and displays information about a new release if one is found.
Automatic update checks also run once per day.

---

### 📊 Statistics & Logs

Statistics & Logs are now available as a **separate screen** with detailed data for all tracked apps.

#### ReAppzuku Consumption

The top of the screen shows **ReAppzuku's own resource usage** — RAM, CPU, and battery — so you can always assess the app's impact on the device.

#### Resource Usage Charts

Interactive charts of RAM, CPU, and battery usage across all tracked apps. Switch between chart types using the **arrows**.

| Period | Description |
|---|---|
| 2 hours | Last 2 hours |
| 6 hours | Last 6 hours |
| 12 hours | Last 12 hours |
| 24 hours | Last 24 hours |

> 💡 Tap an **app in the chart legend** to open its **personal activity graph**

#### Auto-Kill Log

Shows activity for the last **12 hours**: Auto-Kill count, restarts, RAM freed, and last event time for each app.

> 💡 Apps that restart more than 3 times are good candidates for Background Restrictions.

#### Top Offenders

A ranking of apps by combined score (kills + restarts + RAM usage). Filterable by: 12 hours / 24 hours / 7 days / all time.

> 💡 The score shows how aggressively an app interferes with background management.\
>
> `Score = kills × 1 + restarts × 2 + freed RAM × 0.01`
>
> • Kill (+1) — the app was force-stopped.\
> • Restart (+2) — the app relaunched after being stopped; worth double because it's active resistance.\
> • RAM — every 100 MB of freed memory adds +1 point; usually a small contribution.\

> ℹ️ Freed RAM is only counted if the app is not found running again at the next Auto-Kill cycle. This calculation is accurate because if the app restarts, it reclaims the same RAM (net gain: 0%).

#### Background Restrictions Log

A detailed log of background restriction operations. Stored in cache, maximum 200 entries.

| Status | Meaning |
|---|---|
| `Sent` | Command executed successfully (may not have been applied by the system) |
| `Applied` | Restriction confirmed by the system (100% result) |
| `NOT APPLIED` | Command executed, but the system did not apply the change |
| `ERROR` | Command failed with an error |
| `Skipped` | Operation not performed (no permissions, Android < 11, etc.) |
| `Verification unavailable` | Could not query the actual state from the system |
| `Removed from whitelist` | App removed from battery optimization exceptions |
| `Restored to whitelist` | App restored to battery optimization exceptions |

#### Sleep Mode Log
Allows you to monitor sleep mode operation. It logs the date and time of freezing and defrosting target applications. 

#### Scheduler Log 
Contains records of Restrictions Scheduler's operation. In each record, you can see the following parameters:
- The date and time the restrictions were lifted/restored.
- How successfully the restrictions were restored (OK / PARTIAL / FAILED).
- The type of forced stop applied, depending on the Auto-Kill settings.
- Which application component (which you selected when configuring the scheduler) was running when the restriction was lifted.

---

## Protected Apps

The following apps are **never affected** by Auto-Kill or other restrictions, regardless of settings:

### Android Core & Google
- **ReAppzuku** (the app itself)
- Google Play Services and Google Services Framework
- System UI
- Android Settings
- Phone / Dialer, Contacts, SMS service, Telephony server
- Bluetooth
- External storage and media module
- Package installer and permission controller (AOSP and Google variants)
- Gboard (Google keyboard)
- ADB/Shell service
- Android Keychain (TLS/VPN/Wi-Fi)
- Settings, telephony, and SMS/MMS providers
- NFC
- Network stack, tethering stack, DNS resolver, VPN dialogs

### Shizuku
- Shizuku (both variants: `rikka.shizuku.common` and `moe.shizuku.privileged.api`)

### Root Managers
- Magisk
- KernelSU
- KernelSU Next
- APatch
- SukiSU / SukiSU Ultra

### Manufacturer System Apps
| Manufacturer | Protected Apps |
|---|---|
| **Xiaomi / MIUI / HyperOS** | Security Center, home launcher, wallpaper, camera, system protection, core services, PowerKeeper |
| **Samsung (One UI)** | Device Care, device protection, One UI Home, phone interface, telephony server |
| **Oppo / Realme / OnePlus (ColorOS)** | Phone Manager, system launcher, smart assistant |
| **Vivo / iQOO (Funtouch / OriginOS)** | iManager, Vivo launcher |
| **Huawei / Honor (EMUI / MagicOS)** | System Optimizer, Huawei Home, Honor System Manager |

### Dynamically Determined
- Current keyboard (detected automatically at runtime)
- Current launcher (detected automatically at runtime)

---

## FAQ

**❓ An app restarts immediately after Kill — what should I do?**

Add it to **Background Restrictions**. This prevents Android from restarting it in the background at the OS level.

---

**❓ Background restrictions are lost after a reboot**

Enable the **Background Service** — on startup after a reboot it automatically restores all saved restrictions.

---

**❓ Which mode should I choose — whitelist or blacklist?**

Whitelist — if you want to stop everything except what matters. Blacklist — if you want to stop only specific apps and leave everything else alone.

---

**❓ Is the background service required for manual Kill?**

No. Manual Kill from the main screen, quick tiles, the widget, and the shortcut all work without the background service.

---

**❓ Is it safe to stop system apps?**

No. Stopping or restricting system apps can cause device instability, freezes, notification loss, and boot loops. ReAppzuku warns you when you attempt to affect system apps.

---

**❓ What is the difference between Sleep Mode and Background Restrictions?**

Background Restrictions prevent an app from **launching** in the background, but it remains installed and visible. Sleep Mode completely **freezes** the app at the system level — as if it were disabled — until the screen is unlocked.

---

**❓ Shizuku stopped working after a reboot**

Shizuku requires re-activation after every reboot (unless wireless mode is used). Open the Shizuku app and restart the service.

---

**❓ An app simply cannot be killed — what should I do?**

Open the app's menu on the main screen and select **Triggers**. It will show exactly what is keeping the process alive: a foreground service, wakelock, sticky service, or a binding from another app. Depending on the trigger — apply **Background Restrictions** (soft, hard, or manual with the appropriate options).

---

**❓ What is the difference between Sleep Mode and Hard Restriction?**

Both aggressively limit background activity, but in different ways. Sleep Mode **freezes** the app when the screen is off and unfreezes it on unlock — it follows the screen schedule. Hard Restriction acts **constantly**: the app cannot live in the background even when the screen is on and you've switched to something else. For overnight freezing — Sleep Mode. For chronically aggressive apps — Hard Restriction.

---

**❓ Why would I change the Kill Type from force-stop to am kill?**

`am force-stop` is a hard stop — kills all processes and clears the app's state. `am kill` is softer — terminates only background processes without touching the foreground. Only switch if you notice unusual behavior in other apps or firmware conflicts after using `force-stop` — on some devices it is too aggressive.
