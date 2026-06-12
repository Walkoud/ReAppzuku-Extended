# 📖 ReAppzuku — FAQ

> Complete guide to configuring and using ReAppzuku

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
  - [App Stability](#️-app-stability)
  - [Auto-Kill Settings](#-auto-kill-settings)
  - [Advanced Tools](#-advanced-tools)
  - [About](#ℹ️-about)
- [Statistics & Logs](#-statistics--logs)
- [Protected Apps](#protected-apps)
- [FAQ](#faq)

---

## What is ReAppzuku?

**ReAppzuku** is utility for background process diagnostics and management. It offers wide selection of restriction scenarios for every app.

`Why even need ReAppzuku if modern Android handles app control well on its own?` — yes, it does, but not perfectly. OS developers actively improve and modernize system mechanisms for process management. Meanwhile, numerous loopholes allow apps to remain active in background. These range from harmless receivers to aggressive Alarms, Wakelocks, and other retention mechanisms. Ultimately, they prevent devices from entering deep standby mode, overload CPU/RAM, and gladly drain battery power.

---

## Requirements

| Requirement | Description |
|---|---|
| **Android** | 6.0 or higher. Background restrictions only available on Android 11+ |
| **Root** or **Shizuku** | One of two is required |

### Root vs Shizuku

- **Root** — preferred mode, used automatically if available
- **Shizuku** — root-free alternative. Installed from Play Store, requires initial setup via ADB or MIUI/HyperOS developer mode

> Current operating mode is always shown in **Settings → Information → Operating Mode**

---

## Background Survival Setup

For ReAppzuku to run reliably without being killed by system, configure permissions correctly. Steps depend on your firmware.

---

### Battery Optimization (all firmwares)

Most important step. If not disabled, system will periodically kill ReAppzuku.

**Settings → Apps → ReAppzuku → Battery → Unrestricted**

Or via system dialog:

**Settings → Battery → Battery Optimization → All Apps → ReAppzuku → Don't Optimize**

---

### Pin in Recents (all firmwares)

Open recent apps (square button or swipe from bottom), find ReAppzuku, and tap **lock icon** 🔒. This prevents it from being unloaded when you clear recents.

---

### MIUI / HyperOS (Xiaomi, Redmi, POCO)

<details>
<summary>Expand instructions</summary>

**Autostart:**

Settings → Apps → Manage Apps → ReAppzuku → Autostart → Enable

**Background activity:**

Settings → Apps → Manage Apps → ReAppzuku → Battery Saver → No restrictions

**Lock in recents:**

Recents → long-press ReAppzuku card → tap lock icon

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

After setup:
1. Enable **Background Service** in ReAppzuku settings
2. Lock screen for 10–15 minutes
3. Unlock and open ReAppzuku — service should still be active
4. If service stopped — repeat steps for your firmware

> 💡 Device-specific instructions: [dontkillmyapp.com](https://dontkillmyapp.com)

---

## Quick Start

1. Install and open ReAppzuku
2. Grant root access or set up Shizuku
3. Main screen shows active background apps
4. Select apps and tap **Kill** — or set up automation

---

## Manual Control

### Quick Tiles

Added to notification shade:

| Tile | Action |
|---|---|
| **Kill App** | Kills current foreground app |
| **Kill Background Apps** | Runs Auto-Kill with your whitelist/blacklist settings |

### Widget

Home screen widget — shows Auto-Kill stats for last 12 hours and current RAM load.

### Shortcut

Static shortcut via long-pressing app icon — kills current foreground app.

---

## Main

Main screen shows all active background apps with real-time RAM and CPU usage. Top section shows overall stats: number of active apps and current RAM load.

### Toolbar

Three buttons in toolbar:

- 🔍 **Search** — filter list by app name or package
- 🔽 **Sort** — configure display order
- ☑️ **Select All** — select all apps for Kill in one tap

**Sort**

List can be sorted by:
- **Default** — user apps first, then system apps
- **RAM Usage: High → Low / Low → High**
- **CPU Load: High → Low / Low → High**
- **Name A → Z** / **Name Z → A** — alphabetical

You can also toggle display of system and persistent apps.

**Scan**
Performs scan of current load on system from all active apps in list. Load categories:
- CPU hold
- Network hold
- Foreground Service hold
- Waking device (prevents sleep mode)
- Sensor hold
- GPS hold

> ℹ️ Scan does not work on Persistent and Protected apps, even if they appear in active apps list.

> 💡 Keep in mind that more active apps displayed (e.g. system apps display is enabled), longer scan will take.

### App Actions

Tapping app in list opens quick action menu:

- **App Info** — opens standard system app info
- **App Triggers** — detailed analysis of background activity causes (see below)
- **Uninstall** — removes app from device (unavailable for system apps)
- **Add to...** — quickly add to one of these lists:
  - Whitelist
  - Blacklist
  - Hidden
  - Background Restriction (Soft)

### App Triggers

Triggers is a deep diagnostic tool that analyzes **real reasons** for an app's background activity at system level. Instead of guesswork — precise technical facts: what's keeping app in memory, how often it wakes up, and whether it has active network connections right now.

Analyzes **62 independent factors (42 main and 20 additional ones depending Android version)** via system commands in real time.

---

**App Status (active / background / cached)**\
Determined by process priority in Linux kernel combined with active service detection. This is the same value Android uses to decide which processes to terminate when memory is low.

- **Active** — app is in foreground or holds system resources (service, alarm, etc.)
- **Background • active service** — running in background with an active Foreground Service.
- **Background** — running silently, but system considers it necessary.
- **Cached • holds service** — process is in cache but keeps an active service running.
- **Cached • recently used** — process is in cache, was used recently.
- **Cached • inactive** — process is alive, but Android is ready to terminate it at any moment.

---

**Aggression Score**\
Evaluated on a 100-point scale based on triggers.
- Active triggers: + **6 points** each.
- Can wake app at any time: + **5 points** each.
- Other triggers: **0–4 points** depending on importance. Some are informational only and don't affect score.

> 💡 What you can do based on aggression score:
> - 0–40 — system can handle this on its own. No urgent need for restrictions.
> - 41–65 — medium level. Auto-Kill or Soft ,type of Background Restrictions may be enough.
> - 66+ — ideal candidate for Auto-Kill, Hard or Manual type of Background Restrictions, or Sleep Mode.

> ℹ️ This note is provided for informational purposes only and should not be treated as a recommendation. Decide whether to apply restrictions to an app based on factors such as:
> - app’s behavior.
> - its triggers and aggression score.
> - current status assigned by system.
> - device resource usage (battery, RAM, CPU).

---

#### Trigger types:

**Actual**

App is consuming resources **right now**.

- **Foreground Service**. 
App launched a background service with a persistent notification. Most reliable way to avoid being killed — Android won't touch such processes while notification is visible. Shows service type: media playback, location, phone call, connected device, etc.

- **FG Notification Channel**.
Supplements foreground service info: shows notification channel importance. URGENT or HIGH importance shows as a pop-up banner — extremely hard for system to suppress, making force-stop nearly impossible.

- **Sticky Service**.
Service declared `START_STICKY` — Android automatically restarts it after kill. App can't be permanently stopped without disabling it.

- **Held by Bindings**.
One or more processes hold an active binding to this app's service. While binding is held, Android can't kill process. Google Play Services (GMS) is a common culprit — holds push connections and account sync bindings.

- **WakeLock**.
App explicitly asked system to "stay awake". `PARTIAL_WAKE_LOCK` — CPU running with screen off; `FULL_WAKE_LOCK` — screen stays on too. Shows lock tag, type, and hold duration. Directly drains battery while held.

- **Network Activity**.
App has active background network activity. Open TCP connections indicate ongoing data exchange — typical for messengers, push clients, and real-time sync apps. Only traffic exceeding 10 KB is counted, along with `ESTABLISHED` connections.

- **Sensors**.
App is actively polling hardware sensors: accelerometer, gyroscope, barometer, GPS, heart rate monitor, and others. Continuous sensor use drains battery even with screen off. Shows sensor names and polling rate where available.

- **Location**.
App is requesting location data. Shows accuracy level (HIGH_ACCURACY, BALANCED, LOW_POWER), whether background or foreground, and minimum update interval. Background high-accuracy is most resource-intensive.

- **Audio Focus**.
App holds audio focus — exclusively (GAIN) or temporarily (duck/transient). Process stays alive until focus is released. Shows stream type: MUSIC, VOICE_CALL, ALARM, etc.

- **Media Session**.
App has active `MediaSession`. Shows playback state (PLAYING, PAUSED, BUFFERING, etc.) and session tag. Unclosed paused session is a common reason media apps stay in memory.

- **BLE Scan**.
App is doing a Bluetooth Low Energy scan. BLE scanning acquires a wake lock internally and keeps process running in background. `LOW_LATENCY` mode is most power-hungry.

- **GATT Connection**.
App has active Bluetooth GATT connection to a peripheral. Connection is maintained by system and keeps process alive for its duration.

- **AppOps**.
AppOps operations indicating recent app activity:
  - **WAKE_LOCK** — app acquired a WakeLock via AppOps — CPU was kept awake on its behalf.
  - **ACTIVITY_RECOGNITION** — app is using Activity Recognition API and periodically receives motion updates in background (walking, running, in vehicle, etc.).

<details>
<summary>Android 15+ triggers</summary>

- **FGS Timeout Exceeded**.
Android 15: service of type `dataSync` or `mediaProcessing` exceeded 6-hour limit. System should have triggered `onTimeout()` and stopped it.

- **FGS Near Timeout**.
Android 15: service has less than 30 minutes left of 6-hour limit (`dataSync` / `mediaProcessing`).

</details>

<details>
<summary>Android 13 and below triggers</summary>

- **WakeLock (WorkSource attribution)**.
Android 10–13: wakelock is held by a system process but attributed to this app via WorkSource. App is real initiator of wakeup, even though lock is formally held by system.

- **Kernel Wakelock**.
App is holding a kernel-level wakelock (`/sys/power/wake_lock`). Extremely rare — indicates a non-standard driver or system component.

- **ACCESS_BACKGROUND_LOCATION**.
Android 11–13: app has permission to receive location data from background at any time, even when not actively used. Requires separate user approval.

</details>

---

**Can Wake Up at Any Time**\
System **may start or resume** app without any user action.

- **Alarms**.
Analyzes active `AlarmManager` alarms. Wake-up alarms (`RTC_WAKEUP`) pull device out of sleep even with screen off. Interval under 2 minutes is high severity. `AllowWhileIdle` alarms fire even in Doze mode. Shows alarm tags, intervals, and time until next trigger.

- **Jobs / WorkManager**.
App has registered jobs in `JobScheduler`. WorkManager tasks, sync jobs, and periodic operations are registered here and wake app on schedule. Shows job constraints (network type, charging required, idle mode) and stop reasons from recent history.

- **PendingIntent**.
App holds registered `PendingIntent`s. System or other apps can activate them at any moment — via notification, AlarmManager, or system event — starting process. Shows breakdown by type: Activity, Service, Broadcast.

- **Excessive Wakeups**.
Total device wakeups caused by this app since last charge. High numbers indicate aggressive background activity that prevents deep CPU sleep. Broken down by alarms, jobs, GCM/FCM, and broadcasts.

- **Content Observers**.
App registered `ContentObserver`s for content URIs (contacts, media, settings, calendar, etc.). Any change to those URIs wakes app to deliver callback.

- **Push Notifications (FCM)**.
App is registered for Firebase Cloud Messaging (FCM). Google Play Services can wake it at any time when a push arrives, regardless of battery optimization settings.

- **Dynamic Receivers**.
App registered `BroadcastReceiver`s dynamically at runtime. Unlike static manifest receivers, they're active while process is alive and react to system events in real time.

- **AppOps**.
AppOps operations granting background execution rights:
  - **RUN_IN_BACKGROUND** — system battery policy explicitly allows this app to run in background. Won't be suspended when screen is off.
  - **RUN_ANY_IN_BACKGROUND** — app is fully excluded from battery optimization — unrestricted background execution with no system limitations.
  - **USE_FULL_SCREEN_INTENT** — permission to display notifications over lock screen. Android 14+: allowed only for alarm/call apps. Presence in third-party app is an anomaly.
  - **RUN_USER_INITIATED_JOBS** — permission to run long user-initiated tasks. Can execute while screen is locked.
  - **USER_INTERACTION** — app recently received explicit user interaction signal, which may have triggered a background launch.

<details>
<summary>Android 14+ triggers</summary>

- **Jobs (sysfs fallback)**.
Android 14+: job state retrieved via `cmd jobscheduler get-job-state` when primary method (`dumpsys jobscheduler`) is unavailable. Shows status: running, pending, or stopped.

</details>

<details>
<summary>Android 13 and below triggers</summary>

- **SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM**.
Android 12–13: app has permission for exact alarms that fire at a specified time regardless of Doze mode and battery saving. `USE_EXACT_ALARM` is a broader right granted only to alarm clock and calendar apps.

</details>

---

**Other Triggers**\
Passive factors that affect background behavior but don't indicate current activity directly.

- **Chain Launch**.
Identifies who launched this process and how. Direct call — another app explicitly started it via service or activity. Broadcast — started by a broadcast from third-party app. Shows sender name and triggering action.

- **Broadcast Receivers**.
Lists all system events app subscribed to in manifest: network changes, charger connection, timezone changes, screen on/off, and others. `BOOT` and `CONNECTIVITY` subscriptions are flagged as potentially aggressive.

- **Boot Autostart**.
App is registered for system boot events. `BOOT_COMPLETED` — launches after storage is unlocked. `LOCKED_BOOT_COMPLETED` — launches before lock screen appears (before PIN/password entry) — especially aggressive autostart.

- **App Standby Bucket**.
App's priority rank in system: `ACTIVE` → `WORKING_SET` → `FREQUENT` → `RARE` → `RESTRICTED` → `NEVER`. Higher status = fewer background restrictions. `RESTRICTED` and `NEVER` mean system already throttled app. Shows bucket history where available.

- **Doze Exempt**.
App is on Doze whitelist. Such apps don't sleep with device and retain unrestricted network and alarm access at any time. Manufacturer entries can't be revoked by user.

- **Battery Usage History**.
Stats since last battery reset: wakelock holds, alarm wakeups, job and sync launches. Supplements current snapshot with longer-term data.

- **Broadcast Efficiency**.
Shows how many broadcasts were delivered to app and how many required a cold start. High percentage = system regularly kills and restarts it.

- **Multiple Processes**.
App runs in more than one OS process. Sub-processes (`:sync`, `:remote`, `:push`, etc.) can stay alive independently and may not die when main process stops.

- **Accessibility Service**.
App is registered as active Accessibility Service. System keeps it running at all times while enabled, regardless of battery optimization.

- **Input Method (IME)**.
App is currently selected input method (keyboard). System keeps active IME alive as long as it's selected.

- **Device Administrator**.
App is active Device Administrator, Device Owner, or Profile Owner. Has elevated privileges — system protects it from force-stop via standard battery restriction mechanisms.

- **Sync Adapter**.
App has Sync Adapter registered with system. Android periodically launches it to sync account data, even when app isn't running.

- **Background Start**.
App was recently active but not in foreground — sign of a hidden background wakeup triggered by alarm, job, push, or chain launch. Detected by comparing `lastTimeUsed` and `lastTimeForeground` from `dumpsys usagestats`.

- **AppOps**
  - **START_FOREGROUND (blocked)** — system has blocked right to launch Foreground Service. App is trying to operate in background but is restricted.
  - **MANAGE_MEDIA** — manages media sessions of other applications. Associated with `mediaProcessing` FGS type on Android 15.
  
- **Wakelocks History**.
Shows history of last 5 **WAKELOCK** held by app. If app holds wakelock too long, that's bad sign.

<details>
<summary>Android 14+ triggers</summary>

- **Chain Launch (BAL privilege)**.
Android 14+: app received a `BackgroundStartPrivilege` token to launch from background. Usually granted by system for high-priority FCM, exact alarms, or PendingIntent from a visible app.

- **Boot Autostart (FGS restriction)**.
Android 14+: `BOOT_COMPLETED` receiver can't start FGS of type MICROPHONE or PHONE_CALL. App is attempting to bypass this restriction at boot.

- **Doze Exempt (fallback)**.
Android 14+: battery optimization exemption detected via `cmd appops get RUN_ANY_IN_BACKGROUND=allow`. App can run in background without Doze/App Standby restrictions.

- **StandbyBucket Restricted Effects**.
Android 14+: system confirmed RESTRICTED bucket via appops. Jobs and Alarms are blocked — app can't launch itself independently.

</details>

<details>
<summary>Android 13 and below triggers</summary>

- **Chain Launch (BAL blocked)**.
Android 13 and below: system blocked attempt to start Activity or FGS from background without valid exemption. App tried to start but was denied.

- **Process Frozen**.
Android 11–13: process is frozen by system via cgroup freezer — execution is paused, but not killed. Unfreezes automatically upon access.

- **FGS Start Blocked**.
Android 12–13: attempt to start Foreground Service from background without allowed exemption. Service didn't start.

- **Network Blocked (Data Saver)**.
Android 10–13: user enabled Data Saver or restricted background network access. App can't use network in background on mobile data.

- **Background Network Allowed (Data Saver)**.
Android 10–13: app is whitelisted in Data Saver settings — unrestricted network access in background.

- **BT Permissions (BLUETOOTH_SCAN / BLUETOOTH_CONNECT)**.
Android 12–13: app has permissions for Bluetooth scanning and/or connection. Can initiate scanning upon receiving a broadcast.

- **Dynamic Receivers (exported=true)**.
Android 13: dynamically registered receiver with `exported=true` is accessible to other apps and can receive broadcasts from any sender.

- **Doze State Fallback**.
Android 11–13: device is in Deep Doze or Light Doze. Wakelocks, network, jobs, and alarms (except ALLOW_WHILE_IDLE) are blocked for apps without doze exemption.

</details>

> 💡 For Root users: [Blocker](https://github.com/lihenggui/blocker) pairs very well with ReAppzuku. Together they give you a new level of app control.

---

## Settings

### 🔵 Information

**ReAppzuku Access Mode**\
Shows current access mode: **Root**, **Shizuku**, or **No Access**. Read-only.

**Help**\
Link to this FAQ.

---

### 🎨 Appearance

**App Theme**\
Choose a theme: system default, light, dark, or AMOLED.

**Accent Color**\
Choose accent color: indigo, crimson, forest green, amber, and other shades.

**Notifications**\
Configure notification behavior. Critical notifications cover background service status and permission errors.

---

### ⚙️ App stability

**Background Service**\
Main automation toggle. Starts persistent ReAppzuku background process. Required for most of app's features to work, including collecting statistics.

---

### 🎯 Auto-Kill Settings

**Periodic Auto-Kill**\
Automatically kills apps at set interval while background service runs.

**Auto-Kill Interval:**

| Interval | Description |
|---|---|
| 10 seconds | Maximum aggressive cleanup |
| **18 seconds** | Default |
| 30 seconds | Moderate cleanup |
| 1 minute | Light cleanup |
| 5 minutes | Minimal intervention |

**Kill on Screen Off**\
Runs Kill moment screen locks. Useful for cleaning up every time you put your phone down.

**Kill at RAM Load**\
Additional condition — Kill only fires **if** RAM exceeds selected threshold. Applies to both periodic Kill and screen-off Kill.

| Threshold | Description |
|---|---|
| 75% | Early cleanup |
| **80%** | Default |
| 85–95% | Cleanup only when memory is genuinely low |
| 100% | Critical situations only |

**Auto-Kill Type**\
Only relevant if ReAppzuku conflicts with your firmware. If you notice unusual behavior in other apps, try switching to `am kill`.

**Auto-Kill Mode**\
Determines **which** apps get targeted by Auto-Kill.

- **🛡️ Whitelist** — kills all background apps **except** those on whitelist. Use for maximum cleanup.

- **🎯 Blacklist (default)** — kills **only** apps on blacklist. Use to stop specific apps without touching everything else.

**Whitelist / Blacklist**\
App list for selected mode. One of two lists is shown depending on mode.

**Advanced Conditions**\
Expand Auto-Kill with extra triggers — for cases where regular schedule is not enough.

- **Hardware Events**. 
Auto-Kill launches automatically on selected events: headphone or USB connect/disconnect, charging state change, WiFi, mobile network, Bluetooth, GPS or hotspot. After event, 10-second pause is held — so parasitic apps have time to start and get cleaned up.

- **App Launch**. 
Auto-Kill triggers right when selected target apps are opened — useful on budget devices to free RAM and CPU before launching heavy games or programs. Target apps themselves do not get killed.  
  - **Clear Cache**. Additionally clears cache of all apps, except Protected, Persistent and other target apps.
> ℹ️ **App Launch** function requires special permission in "Accessibility" settings. This feature can also slightly increase battery usage by ReAppzuku itself.


**Auto-Kill Presets**\
Save your own set of Auto-Kill settings that activates automatically at a specific time of day and replaces the current settings for the duration of its active window. When the window ends, the original settings are restored automatically.
**2 presets** are available. Each can be configured independently: its own name, its own active time range, its own Auto-Kill rules, its own app lists, and its own additional scenarios.
> ℹ️ While active, presets ignore the immunity granted to apps by the Restrictions Scheduler. This is done to avoid confusion in the settings.

- **Enable preset**.
The master switch. If disabled, the preset **will not activate** on schedule, even if its time window starts. If the preset is currently active and this switch is turned off, it deactivates immediately and the original settings are restored.

- **Preset name**.
A custom name, up to 30 characters. Shown in the preset picker dialog in the main settings. If the preset is currently active, an **"Active"** badge appears next to its name.

- **Active time**.
A "From — To" range, displayed using the device's time format (12/24-hour). Ranges that cross midnight are supported (e.g. 22:00 – 06:00).
> ℹ️ The two presets cannot overlap in their active time. If you try to save a preset with an overlapping range, a warning will show the conflicting preset's time range — adjust one of the presets' times to resolve it.

- **App list source**
Choose between:
  - **Use current whitelist / blacklist** — the preset always uses the live whitelist/blacklist from the main settings at the moment it activates
  - **Use preset's own list** — the preset has its own independent whitelist/blacklist, edited separately and unaffected by changes to the main settings

- **Auto-Kill management and Advanced Conditions**.
A standard Auto-Kill settings block, same as in the regular app settings. All these settings are described in [Auto-Kill Settings](#-auto-kill-settings)

- **Save preset**.
Applies all changes: saves the settings, reschedules the activation/deactivation alarms, and immediately activates or deactivates the preset if needed (if the changes affect the current time window).

- **Import/Export JSON file**.
Save preset to JSON file or restore it from a backup file. To apply changes, click "Save" button.

- **Reset preset**.
Resets all current settings on screen back to their default values (taken from the app's main settings). **Changes are not applied** until "Save" is pressed — you can simply leave the screen without saving, and the reset will not affect the already-saved preset.

**RAM Kill Shortcut**\
Adds small 1x1 shortcut to home screen. Tapping shortcut launches Auto-Kill according to current list (black or white). Additionally performs RAM cleanup from active apps, but excludes whitelist, protected and persistent system apps.

---

### 🔧 Advanced Tools

**Background Restrictions**
> Available on **Android 11+** only

Uses Android's `appops` to **block an app from running in background at OS level**. Deeper than regular Kill.

| | Regular Kill | Background Restrictions |
|---|---|---|
| How it works | Force-stops process | Prevents Android from starting process in background |
| Can restart | ✅ Yes | ❌ No |
| Persists after reboot | ❌ No | ✅ Yes |
| Requires Android 11+ | ❌ No | ✅ Yes |

**Restriction types:**

- **Soft** (RUN_ANY_IN_BACKGROUND ignore)\
Blocks autostart at a stricter level than standard activity settings.\
**How it works**: If you open app and switch away — it keeps running (while in recents). But on its own (overnight or in background) it won't wake up until you open it.

- **Medium**\
Restricts some background activity.
**How it works:**\
Blocks service launches, job scheduler and alarms. App works normally while open, but enters standby as soon as you leave it (minimize).\
**Commands used:**\
`RUN_ANY_IN_BACKGROUND ignore`\
`RUN_IN_BACKGROUND ignore`\
`ALARM_WAKEUP ignore`\
`START_FOREGROUND_SERVICES_FROM_BACKGROUND ignore`\
`Standby Bucket: Rare`

- **Hard**\
Blocks any background activity.\
**How it works:**\
Once app is minimized or switched away from — system kills it immediately. App cannot keep itself in memory without direct user interaction (even if visible in recents). Use Hard restriction with caution, as it may completely deprive app of background operations (file downloads, media playback, long-running internal tasks).\
**Commands used:**\
`RUN_ANY_IN_BACKGROUND ignore`\
`RUN_IN_BACKGROUND ignore`\
`START_FOREGROUND ignore`\
`START_FOREGROUND_SERVICES_FROM_BACKGROUND ignore`\
`WAKE_LOCK ignore`\
`ALARM_WAKEUP ignore`\
`RECEIVE_BOOT_COMPLETED ignore`\
`INTERACT_ACROSS_PROFILES ignore`\
`Battery optimization whitelist removal`\
`Standby Bucket: Restricted`

- **Manual**\
You choose which restrictions to apply.\
**How it works**: ReAppzuku applies only restrictions you select.

> 💡 App Standby Bucket resets upon user interaction with target app. System does not always restore it back. ReAppzuku will automatically restore app Bucket on next restriction integrity check cycle.

---

**Available restrictions:**

- **RUN_ANY_IN_BACKGROUND**\
Prevents app from starting background processes or services without explicit user interaction. Primary and broadest restriction — used in **Soft** mode.\
**Blocks:** background service starts, sync, deferred tasks (JobScheduler, WorkManager).\
**Does not block:** foreground services (with notification), already-running processes.

- **RUN_IN_BACKGROUND**\
More targeted background execution restriction. Blocks service starts via `startService()` when app is in background.\
**Blocks:** background services started by app itself without user involvement.\
**Does not block:** foreground services, alarm-triggered tasks, broadcast receivers.

- **START_FOREGROUND**\
Prevents app from promoting service to foreground (persistent notification). Without this, app can't show "running in background" notification or hold process alive.\
**Blocks:** calls to `startForeground()` — app can't create sticky notification or keep service alive.\
**Does not block:** regular app notifications, background tasks via JobScheduler.

- **START_FOREGROUND_SERVICES_FROM_BACKGROUND**\
Prevents starting foreground service when app is in background. Added in Android 12 on top of `START_FOREGROUND`.\
**Blocks:** attempts to start foreground service while app isn't visible on screen.\
**Does not block:** foreground services started while app is in foreground.

- **WAKE_LOCK**\
Prevents app from keeping CPU active with screen off. Without wake lock, system can sleep CPU and stop background operations.\
**Blocks:** CPU hold via `PowerManager.WakeLock` — app can't prevent phone from sleeping.\
**Does not block:** app running while screen is on.

- **ALARM_WAKEUP**\
Prevents app from waking device via exact timers (`AlarmManager.setExactAndAllowWhileIdle` and equivalents). Without this, alarms can't wake phone from deep sleep.\
**Blocks:** exact alarm tasks that wake device — app can't schedule forced wakeup by timer.\
**Does not block:** inexact timers, JobScheduler tasks.

- **RECEIVE_BOOT_COMPLETED**\
Prevents app from receiving `BOOT_COMPLETED` after reboot — mechanism most apps use to add themselves to autostart.\
**Blocks:** autostart on system boot.\
**Does not block:** manually launching app after reboot.

- **INTERACT_ACROSS_PROFILES**\
Prevents app from interacting with other work profiles. Primarily relevant on enterprise devices.\
**Blocks:** cross-profile calls and data transfer between primary and work profiles.\
**Does not block:** app operating within single profile.

- **Standby Bucket: Rare**\
Marked by system as rarely used. Blocks app at system level:
  - Background network. Network available only during rare system maintenance windows.
  - JobScheduler. Regular jobs and Expedited Jobs limited to 10 minutes per day.
  - AlarmManager. Inexact alarms are deferred. Limit — 1 trigger per hour.
  - Push (FCM). High-Priority push quota is reduced. Exceeded pushes are delayed.

- **Standby Bucket: Restricted**\
Marked by system as long-unused or anomalous app that consumed excessive CPU and battery. Includes all Rare restrictions, but enforces them more strictly. Additionally restricts at system level:
  - Charging exemption removed. When device is plugged in, restrictions for all buckets (including Rare) are fully lifted. However for Restricted, JobScheduler launch limits remain active even while charging.
  - Job frequency hard cap. Strictly limits scheduling granularity — app is allowed to launch background job exactly 1 time per day.
  - Boot behavior. Starting from Android 13, if app is in Restricted bucket, system completely blocks delivery of `BOOT_COMPLETED` and `LOCKED_BOOT_COMPLETED` broadcasts. App cannot start on OS boot until user opens it manually.
  - Forced termination of active services. If running app is moved by system into Restricted bucket while in background (e.g. due to detected abnormal power consumption), OS automatically removes and terminates all its active Foreground Services.
  - Network access during maintenance windows. During Doze Mode, system periodically opens maintenance windows. Apps with Restricted bucket are denied network access even during these system windows.
  - Expedited Jobs limit cut. Limit for Expedited Jobs is halved — down to 5 minutes per day.

---

### Restriction types comparison

| Restriction | Soft | Medium | Hard | Manual |
|---|:---:|:---:|:---:|:---:|
| RUN_ANY_IN_BACKGROUND | ✓ | ✓ | ✓ | optional |
| RUN_IN_BACKGROUND | — | ✓ | ✓ | optional |
| START_FOREGROUND | — | — | ✓ | optional |
| START_FOREGROUND_SERVICES_FROM_BACKGROUND | — | ✓ | ✓ | optional |
| WAKE_LOCK | — | — | ✓ | optional |
| ALARM_WAKEUP | — | ✓ | ✓ | optional |
| RECEIVE_BOOT_COMPLETED | — | — | ✓ | optional |
| INTERACT_ACROSS_PROFILES | — | — | ✓ | optional |
| Standby Bucket | — | Rare | Restricted | optional |

---

List statuses:
- **Saved in ReAppzuku** — saved, but system status is unknown (insufficient permissions)
- **Saved in ReAppzuku, but not applied** — saved, but Android hasn't applied restriction
- **Restricted, not by ReAppzuku** — restricted by Android or another app

**Background Restrictions WatchDog**\
ReAppzuku periodically checks integrity of Background Restrictions applied to apps. If system resets any restrictions — restores them.\
For **Soft and Medium** (and Manual, if chosen restrictions are equivalent to Soft/Medium) — restrictions are restored only if app is not active on screen and does not hold `IMPORTANCE_FOREGROUND_SERVICE`.\
In all other cases restrictions are restored only if app is not currently active on screen (not being used).

**Re-apply Background Restrictions**\
Manually re-applies all saved restrictions. After reboot this happens **automatically** when background service starts.

---

**Restriction Scheduler**\
Schedule when restrictions should be lifted and restored for specific apps.
> Only apps with an active **Background Restriction** (Soft / Hard / Manual) appear here.
> Apps with scheduled entry show 🕐 icon with scheduled time.

Tap app to open scheduler configuration:

**Protect from**\
Select which restrictions app will be temporarily exempted from.

**Time window**\
Set start time (restrictions lifted) and end time (restrictions restored).
App is force-stopped before restrictions are restored.

**Set Bucket: Active**
When you remove restrictions from an app, its App Standby Bucket gets forced to Active. This lets the app spin up its services on its own.

**On activation**\
Action to take when restrictions are lifted:
- **None** — no additional action.
- **Launch component** — opens app's component picker (Activity, Service, Receiver, etc.).

> Scheduled entries are limited to 15 apps to protect ReAppzuku itself.

---

**Sleep Mode**\
Completely **freezes** selected apps when device is idle. Unlike background restrictions — app simply can't start; fully suspended by system.

How it works:
1. Screen turns off → timer starts
2. Timer expires → selected apps are frozen
3. Screen turns on and unlocks → apps unfreeze automatically

> ⚠️ Only works with user-installed apps — system apps can't be frozen (Android limitation)

> ℹ️ Enabling Sleep Mode restarts ReAppzuku — required for proper initialization

> 💡 If target app was on home screen, its icon may disappear or move after Sleep Mode activates. Expected Android behavior with pm disable/enable.

**Sleep Mode App List**\
Select apps to freeze in Sleep Mode.

**Freeze Timer**\
Idle period after which freezing triggers: **5 to 60 minutes** (default: 60 minutes).

---

**Clear Cache for All Apps**\
Runs `pm trim-caches` — clears cache of all apps at once.

**Hidden Apps**\
Apps here don't appear on main screen and are never touched by Auto-Kill. Useful for service processes you don't need to see.

**Backup & Restore**\
Export and import all settings as JSON. Covers whitelist, blacklist, hidden apps, background restrictions, Sleep Mode, and all automation settings.

---

### ℹ️ About

**Source Code**\
Link to GitHub repository.

**Check for Updates**\
Manually checks GitHub for a new release and shows it if found.
Automatic update checks run once a day.

---

### 📊 Statistics & Logs

Statistics & Logs are available as a **separate screen** with detailed data for all tracked apps.

**ReAppzuku Consumption**\
Top of screen shows **ReAppzuku's own resource usage** — RAM, CPU, and battery — so you can assess its impact on device.

**Resource Usage Charts**\
Interactive charts of RAM, CPU, and battery usage across tracked apps. Switch between chart types with **arrows**.

| Period | Description |
|---|---|
| 2 hours | Last 2 hours |
| 6 hours | Last 6 hours |
| 12 hours | Last 12 hours |
| 24 hours | Last 24 hours |

> 💡 Tap an **app in chart legend** to open its **personal activity graph**

**Auto-Kill Log**\
Shows activity for last **12 hours**: Auto-Kill count, restarts, RAM freed, and last event time per app.

> 💡 Apps restarting more than 3 times are good candidates for Background Restrictions.

**Top Offenders**\
Ranks apps by combined score (kills + restarts + RAM usage). Filter by: 12 hours / 24 hours / 7 days / all time.

> 💡 Score shows how aggressively app interferes with background management.\
>
> `Score = kills × 1 + restarts × 2 + freed RAM × 0.01`
>
> • Kill (+1) — app was force-stopped.\
> • Restart (+2) — app relaunched after being stopped; worth double because it's active resistance.\
> • RAM — every 100 MB of freed memory adds +1 point; usually a small contribution.\

> ℹ️ Freed RAM is counted only if app isn't found running at next Auto-Kill cycle. If it restarts, it reclaims same RAM — net gain 0%.

**Background Restrictions Log**\
Detailed log of background restriction operations. Stored in cache, 200 entries max.

| Status | Meaning |
|---|---|
| `Sent` | Command executed successfully (may not have been applied by system) |
| `Applied` | Restriction confirmed by system (100% result) |
| `NOT APPLIED` | Command executed, but system didn't apply change |
| `ERROR` | Command failed with an error |
| `Skipped` | Operation not performed (no permissions, Android < 11, etc.) |
| `Verification unavailable` | Could not query actual state from system |
| `Removed from whitelist` | App removed from battery optimization exceptions |
| `Restored to whitelist` | App restored to battery optimization exceptions |

> 💡 Tapping entry in Background Restrictions Log opens log details. There you can see which AppOps didn't apply or got reset. Also can check if app Standby Bucket changed.

**Sleep Mode Log**\
Logs date and time of freeze/unfreeze for target apps.

**Scheduler Log**\
Contains records of Restriction Scheduler activity. Each entry shows:
- Date and time restrictions were lifted/restored.
- How successfully restrictions were restored (OK / PARTIAL / FAILED).
- Type of forced stop applied (based on Auto-Kill settings).
- Which app component was running when restriction was lifted.

---

## Protected Apps

These apps are **never affected** by Auto-Kill or other restrictions, regardless of settings:

**Android Core & Google**
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

**Shizuku**
- Shizuku (both variants: `rikka.shizuku.common` and `moe.shizuku.privileged.api`)

**Root Managers**
- Magisk
- KernelSU
- KernelSU Next
- APatch
- SukiSU / SukiSU Ultra

**Manufacturer System Apps**
| Manufacturer | Protected Apps |
|---|---|
| **Xiaomi / MIUI / HyperOS** | Security Center, home launcher, wallpaper, camera, system protection, core services, PowerKeeper |
| **Samsung (One UI)** | Device Care, device protection, One UI Home, phone interface, telephony server |
| **Oppo / Realme / OnePlus (ColorOS)** | Phone Manager, system launcher, smart assistant |
| **Vivo / iQOO (Funtouch / OriginOS)** | iManager, Vivo launcher |
| **Huawei / Honor (EMUI / MagicOS)** | System Optimizer, Huawei Home, Honor System Manager |

**Dynamically Determined**
- Current keyboard (detected automatically at runtime)
- Current launcher (detected automatically at runtime)

---

## FAQ

**❓ An app restarts immediately after Kill — what should I do?**

Add it to **Background Restrictions** — prevents Android from restarting it in background at OS level.

---

**❓ Background restrictions are lost after a reboot**

Enable **Background Service** — it automatically restores all saved restrictions after reboot.

---

**❓ Which mode should I choose — whitelist or blacklist?**

Whitelist — stop everything except what matters. Blacklist — stop only specific apps and leave everything else alone.

---

**❓ Is background service required for manual Kill?**

No. Manual Kill from main screen, quick tiles, widget, and shortcut all work without background service.

---

**❓ Is it safe to stop system apps?**

No. Stopping or restricting system apps can cause instability, freezes, notification loss, and boot loops. ReAppzuku warns you before affecting system apps.

---

**❓ Sleep Mode vs Background Restrictions — what's difference?**

Background Restrictions prevent app from **launching** in background, but it stays installed and visible. Sleep Mode completely **freezes** it at system level — as if disabled — until screen is unlocked.

---

**❓ Shizuku stopped working after a reboot**

Shizuku requires re-activation after every reboot (unless using wireless ADB mode). Open Shizuku and restart service.

---

**❓ An app simply cannot be killed — what should I do?**

Open app menu and select **Triggers**. It'll show exactly what's keeping process alive: foreground service, wakelock, sticky service, or binding from another app. Depending on trigger — apply **Background Restrictions** (soft, hard, or manual).

---

**❓ Sleep Mode vs Hard Restriction — what's difference?**

Both aggressively limit background activity, but differently. Sleep Mode **freezes** app when screen is off and unfreezes on unlock — follows screen schedule. Hard Restriction is **always on**: app can't survive in background even when screen is on and you've switched away. For overnight freezing — Sleep Mode. For chronically aggressive apps — Hard Restriction.

---

**❓ Why change Kill Type from force-stop to am kill?**

`am force-stop` is a hard stop — kills all processes and clears app state. `am kill` is softer — terminates only background processes without touching foreground. Only switch if you notice issues in other apps or firmware conflicts — on some devices `force-stop` is too aggressive.
