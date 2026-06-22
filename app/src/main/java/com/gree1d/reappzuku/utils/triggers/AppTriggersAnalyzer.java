package com.gree1d.reappzuku.utils.triggers;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.gree1d.reappzuku.R;
import com.gree1d.reappzuku.core.ShellManager;
import com.gree1d.reappzuku.core.AppDebugManager;
import com.gree1d.reappzuku.core.AppDebugManager.Category;
import com.gree1d.reappzuku.utils.triggers.analyzers.ProcessAnalyzer;
import com.gree1d.reappzuku.utils.triggers.analyzers.PowerAnalyzer;
import com.gree1d.reappzuku.utils.triggers.analyzers.SensorsLocationAnalyzer;
import com.gree1d.reappzuku.utils.triggers.analyzers.MediaAnalyzer;
import com.gree1d.reappzuku.utils.triggers.analyzers.NetworkAnalyzer;
import com.gree1d.reappzuku.utils.triggers.analyzers.ComponentsAnalyzer;
import com.gree1d.reappzuku.utils.triggers.analyzers.DozeOpsAnalyzer;
import com.gree1d.reappzuku.utils.triggers.analyzers.SchedulingAnalyzer;
import com.gree1d.reappzuku.utils.triggers.analyzers.MiscAnalyzer;

// AnalysisType — pass to analyze(packageName, types) for targeted analysis.
// Full list of available values:
//
//   PROCESS_STATE                  — process state (proc state, oom adj)
//   SERVICES_AND_BINDINGS          — running services and bindings (FGS, sticky, bound)
//   FG_NOTIFICATION                — foreground service notification
//   FGS_START_BLOCKED              — FGS start attempts blocked by the system
//   WAKELOCKS                      — CPU held via wakelock
//   NETWORK_ACTIVITY               — network activity (background data transfer)
//   NETWORK_POLICY                 — network policy (background access allowed/blocked)
//   SENSORS                        — sensor usage
//   LOCATION_REQUESTS              — active location requests
//   BACKGROUND_LOCATION_PERMISSION — background location permission granted
//   AUDIO_FOCUS                    — audio focus acquisition
//   BLUETOOTH                      — Bluetooth activity (scanning, connections)
//   BLUETOOTH_PERMISSIONS          — Bluetooth permissions
//   ALARMS                         — alarms (including WAKEUP types)
//   EXCESSIVE_WAKEUPS              — excessive device wakeups
//   JOBS                           — background JobScheduler tasks
//   PENDING_INTENTS                — registered PendingIntents
//   CONTENT_OBSERVERS              — ContentObserver subscriptions
//   FCM_REGISTRATION               — Firebase Cloud Messaging registration
//   APP_OPS                        — AppOps operations (system resource access)
//   CHAIN_LAUNCH                   — chain launch (one app waking another)
//   BROADCAST_RECEIVERS            — registered BroadcastReceivers
//   BOOT_RECEIVERS                 — BOOT_COMPLETED receivers
//   CONTENT_PROVIDERS              — registered ContentProviders
//   SYNC_ADAPTERS                  — background sync (SyncAdapter)
//   DOZE_EXEMPTION                 — Doze mode exemption
//   STANDBY_BUCKET                 — app standby bucket (priority tier)
//   BATTERY_STATS                  — cumulative power consumption (since last charge)
//   BROADCAST_EFFICIENCY           — broadcast handling efficiency
//   MULTIPLE_PROCESSES             — multiple active processes for the app
//   ACCESSIBILITY_AND_IME          — Accessibility Service or IME usage
//   DEVICE_ADMIN                   — device administrator rights
//   USAGE_STATS                    — app usage statistics

public class AppTriggersAnalyzer {

    public enum AnalysisType {
        PROCESS_STATE,
        SERVICES_AND_BINDINGS,
        FG_NOTIFICATION,
        WAKELOCKS,
        NETWORK_ACTIVITY,
        NETWORK_POLICY,
        SENSORS,
        LOCATION_REQUESTS,
        BACKGROUND_LOCATION_PERMISSION,
        AUDIO_FOCUS,
        BLUETOOTH,
        BLUETOOTH_PERMISSIONS,
        FGS_START_BLOCKED,
        ALARMS,
        JOBS,
        PENDING_INTENTS,
        EXCESSIVE_WAKEUPS,
        CONTENT_OBSERVERS,
        FCM_REGISTRATION,
        APP_OPS,
        CHAIN_LAUNCH,
        BROADCAST_RECEIVERS,
        BOOT_RECEIVERS,
        CONTENT_PROVIDERS,
        SYNC_ADAPTERS,
        DOZE_EXEMPTION,
        STANDBY_BUCKET,
        BATTERY_STATS,
        BROADCAST_EFFICIENCY,
        MULTIPLE_PROCESSES,
        ACCESSIBILITY_AND_IME,
        DEVICE_ADMIN,
        USAGE_STATS
    }

    private static final String FILE_NAME = "AppTriggersAnalyzer";

    public static final class TriggerInfo {

        public enum Group { ACTIVE_NOW, CAN_WAKE, OTHER }

        public enum Severity { HIGH, MEDIUM, LOW, INFO }

        public final Group    group;
        public final String   category;
        public final String   detail;
        public final String   explanation;
        public final Severity severity;

        public TriggerInfo(Group group, String category, String detail,
                           String explanation, Severity severity) {
            this.group       = group;
            this.category    = category;
            this.detail      = detail;
            this.explanation = explanation;
            this.severity    = severity;
        }

        public TriggerInfo(String category, String detail,
                           String explanation, Severity severity) {
            this(Group.OTHER, category, detail, explanation, severity);
        }
    }

    public static final class AlarmEntry {
    
        public final String  type;
        public final String  tag;
        public final long    fireDiffMs;
        public final long    intervalMs;
        public final boolean exact;
        public final boolean whileIdle;
        public final boolean isWakeup;
        public boolean pendingBroadcast;
        public boolean quotaExceeded;
    
        public AlarmEntry(String type, String tag, long fireDiffMs, long intervalMs,
                   boolean exact, boolean whileIdle, boolean isWakeup) {
            this.type       = type;
            this.tag        = tag;
            this.fireDiffMs = fireDiffMs;
            this.intervalMs = intervalMs;
            this.exact      = exact;
            this.whileIdle  = whileIdle;
            this.isWakeup   = isWakeup;
        }
    
        public boolean isClockAlarm() {
            return tag != null && (tag.contains("AlarmClock") || tag.contains("ALARM_CLOCK"));
        }
    }

    public static final class AlarmDumpsysParser {

        private static final Pattern TYPE_LINE_PAT = Pattern.compile(
                "^\\s*(RTC_WAKEUP|RTC|ELAPSED_WAKEUP|ELAPSED)\\s+#\\d+");
        private static final Pattern TAG_PAT        = Pattern.compile("\\btag=(\\S+)");
        private static final Pattern WINDOW_PAT     = Pattern.compile("\\bwindow=(-?\\d+)");
        private static final Pattern INTERVAL_PAT   = Pattern.compile("\\binterval=(\\d+)");
        private static final Pattern FLAGS_PAT      = Pattern.compile("\\bflgs=0x([0-9a-fA-F]+)");
        private static final Pattern ELAPSED_HR_PAT = Pattern.compile(
                "whenElapsed=\\+((?:(\\d+)h)?(?:(\\d+)m)?(\\d+)s)");
        private static final Pattern ELAPSED_MS_PAT = Pattern.compile("whenElapsed=(\\d+)");
        private static final Pattern WHEN_UNIX_PAT  = Pattern.compile("\\bwhen=(\\d{10,13})");
        private static final Pattern TOP_ENTRY_PAT  = Pattern.compile(
                "(\\S+)\\s+running,\\s*(\\d+)\\s+wakeups?,\\s*(\\d+)\\s+alarms?:\\s*\\d+:([\\w.]+)\\s+tag=(\\S+)");

        public List<AlarmEntry> parseEntries(String output, String packageName) {
            List<AlarmEntry> result = new ArrayList<>();
            String       curType     = null;
            boolean      curIsWakeup = false;
            List<String> block       = new ArrayList<>();

            for (String line : output.split("\n")) {
                Matcher mType = TYPE_LINE_PAT.matcher(line);
                if (mType.find()) {
                    if (curType != null) {
                        AlarmEntry e = parseBlock(block, curType, curIsWakeup, packageName);
                        if (e != null) result.add(e);
                    }
                    curType     = mType.group(1);
                    curIsWakeup = curType.contains("WAKEUP");
                    block.clear();
                } else if (curType != null) {
                    block.add(line);
                }
            }
            if (curType != null) {
                AlarmEntry e = parseBlock(block, curType, curIsWakeup, packageName);
                if (e != null) result.add(e);
            }
            return result;
        }

        private AlarmEntry parseBlock(List<String> lines, String type,
                                      boolean isWakeup, String packageName) {
            String  tag       = null;
            long    fireDiff  = Long.MAX_VALUE;
            long    interval  = 0;
            boolean exact     = false;
            boolean whileIdle = false;
            long    nowMs     = System.currentTimeMillis();
            long    bootMs    = nowMs - SystemClock.elapsedRealtime();

            for (String line : lines) {
                String t = line.trim();
                if (tag == null) {
                    Matcher m = TAG_PAT.matcher(t);
                    if (m.find()) tag = m.group(1);
                }
                if (fireDiff == Long.MAX_VALUE) fireDiff = parseFireDiff(t, nowMs, bootMs);
                Matcher mWin = WINDOW_PAT.matcher(t);
                if (mWin.find()) exact = Long.parseLong(mWin.group(1)) < 0;
                if (interval == 0) {
                    Matcher mIv = INTERVAL_PAT.matcher(t);
                    if (mIv.find()) interval = Long.parseLong(mIv.group(1));
                }
                if (!whileIdle) {
                    Matcher mFlg = FLAGS_PAT.matcher(t);
                    if (mFlg.find()) whileIdle = (Long.parseLong(mFlg.group(1), 16) & 0xCL) != 0;
                    if (t.contains("ALLOW_WHILE_IDLE") || t.contains("allowWhileIdle=true"))
                        whileIdle = true;
                }
            }
            if (tag == null || !tag.contains(packageName)) return null;
            AlarmEntry entry = new AlarmEntry(type, tag, fireDiff, interval, exact, whileIdle, isWakeup);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                for (String line : lines) {
                    String t = line.trim();
                    if (t.contains("pendingBroadcast=true") || t.contains("deferredBroadcast=true"))
                        entry.pendingBroadcast = true;
                    if (t.contains("QUOTA_EXCEEDED") || t.contains("quotaExceeded=true"))
                        entry.quotaExceeded = true;
                }
            }
            return entry;
        }

        private long parseFireDiff(String line, long nowMs, long bootMs) {
            Matcher mHr = ELAPSED_HR_PAT.matcher(line);
            if (mHr.find()) {
                long ms = 0;
                if (mHr.group(2) != null) ms += Long.parseLong(mHr.group(2)) * 3_600_000L;
                if (mHr.group(3) != null) ms += Long.parseLong(mHr.group(3)) * 60_000L;
                ms += Long.parseLong(mHr.group(4)) * 1_000L;
                return ms > 0 ? ms : Long.MAX_VALUE;
            }
            Matcher mUnix = WHEN_UNIX_PAT.matcher(line);
            if (mUnix.find()) {
                long diff = Long.parseLong(mUnix.group(1)) - nowMs;
                return diff > 0 ? diff : Long.MAX_VALUE;
            }
            Matcher mMs = ELAPSED_MS_PAT.matcher(line);
            if (mMs.find()) {
                long diff = bootMs + Long.parseLong(mMs.group(1)) - nowMs;
                return diff > 0 ? diff : Long.MAX_VALUE;
            }
            return Long.MAX_VALUE;
        }

        public List<String> parseTopAlarms(String output, String packageName) {
            List<String> result = new ArrayList<>();
            boolean inTop = false;
            for (String line : output.split("\n")) {
                String t = line.trim();
                if (t.startsWith("Top Alarms:") || t.startsWith("Top alarm senders:")) {
                    inTop = true; continue;
                }
                if (inTop && (t.isEmpty() || (t.endsWith(":") && !t.startsWith("+")))) {
                    inTop = false; continue;
                }
                if (!inTop) continue;
                Matcher m = TOP_ENTRY_PAT.matcher(t);
                if (!m.find() || !m.group(4).equals(packageName)) continue;
                String shortTag = m.group(5)
                        .replaceAll("^\\*[^*]+\\*/", "")
                        .replaceAll(".*\\.([^.]+)$", "$1");
                result.add(shortTag + ":" + m.group(2) + "×wakeup/" + m.group(1));
                if (result.size() >= 3) break;
            }
            return result;
        }
    }

    private final ShellManager           shellManager;
    private final Context                context;

    public final int apiLevel;

    public static final int API_CMD_APPOPS        = Build.VERSION_CODES.O;
    public static final int API_STANDBY_BUCKET    = Build.VERSION_CODES.P;
    public static final int API_PROCESS_FREEZER   = Build.VERSION_CODES.R;
    public static final int API_FGS_BG_BLOCKED    = Build.VERSION_CODES.S;
    public static final int API_RECEIVER_EXPORTED = Build.VERSION_CODES.TIRAMISU;
    public static final int API_BAL_PRIVILEGES    = Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
    public static final int API_MEDIA_PROCESSING  = Build.VERSION_CODES.VANILLA_ICE_CREAM;

    public static final Pattern DOZE_STATE_PAT      = Pattern.compile("mState=([A-Z_]+)");
    public static final Pattern LIGHT_DOZE_PAT      = Pattern.compile("mLightState=([A-Z_]+)");

    String cachedUid = null;

    public ShellManager getShellManager() { return shellManager; }
    public Context       getContext()      { return context; }
    public String        getCachedUid()    { return cachedUid; }

    private final ProcessAnalyzer processAnalyzer;
    private final PowerAnalyzer powerAnalyzer;
    private final SensorsLocationAnalyzer sensorsLocationAnalyzer;
    private final MediaAnalyzer mediaAnalyzer;
    private final NetworkAnalyzer networkAnalyzer;
    private final ComponentsAnalyzer componentsAnalyzer;
    private final DozeOpsAnalyzer dozeOpsAnalyzer;
    private final SchedulingAnalyzer schedulingAnalyzer;
    private final MiscAnalyzer miscAnalyzer;

    public AppTriggersAnalyzer(Context context, ShellManager shellManager) {
        this.context      = context.getApplicationContext();
        this.shellManager = shellManager;
        this.apiLevel     = Build.VERSION.SDK_INT;

        this.processAnalyzer         = new ProcessAnalyzer(this);
        this.powerAnalyzer           = new PowerAnalyzer(this);
        this.sensorsLocationAnalyzer = new SensorsLocationAnalyzer(this);
        this.mediaAnalyzer           = new MediaAnalyzer(this);
        this.networkAnalyzer         = new NetworkAnalyzer(this);
        this.componentsAnalyzer      = new ComponentsAnalyzer(this);
        this.dozeOpsAnalyzer         = new DozeOpsAnalyzer(this);
        this.schedulingAnalyzer      = new SchedulingAnalyzer(this, dozeOpsAnalyzer);
        this.miscAnalyzer            = new MiscAnalyzer(this, componentsAnalyzer);

        AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": initialized, apiLevel=" + this.apiLevel);
    }

    public List<TriggerInfo> analyze(String packageName) {
        return analyze(packageName, EnumSet.allOf(AnalysisType.class));
    }

    public List<TriggerInfo> analyze(String packageName, EnumSet<AnalysisType> types) {
        AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": analyze: start pkg=" + packageName + " types=" + types.size());

        cachedUid = resolveUid(packageName);

        List<TriggerInfo> results = new ArrayList<>();

        if (types.contains(AnalysisType.PROCESS_STATE))
            safeAdd(results, "ProcessState",              () -> processAnalyzer.analyzeProcessState(packageName));
        if (types.contains(AnalysisType.SERVICES_AND_BINDINGS))
            safeAdd(results, "ServicesAndBindings",       () -> processAnalyzer.analyzeServicesAndBindings(packageName));
        if (types.contains(AnalysisType.FG_NOTIFICATION))
            safeAdd(results, "FgNotification",            () -> processAnalyzer.analyzeFgNotification(packageName));
        if (types.contains(AnalysisType.WAKELOCKS))
            safeAdd(results, "Wakelocks",                 () -> powerAnalyzer.analyzeWakelocks(packageName));
        if (types.contains(AnalysisType.NETWORK_ACTIVITY))
            safeAdd(results, "NetworkActivity",           () -> networkAnalyzer.analyzeNetworkActivity(packageName));
        if (types.contains(AnalysisType.SENSORS))
            safeAdd(results, "Sensors",                   () -> sensorsLocationAnalyzer.analyzeSensors(packageName));
        if (types.contains(AnalysisType.LOCATION_REQUESTS))
            safeAdd(results, "LocationRequests",          () -> sensorsLocationAnalyzer.analyzeLocationRequests(packageName));
        if (types.contains(AnalysisType.AUDIO_FOCUS))
            safeAdd(results, "AudioFocus",                () -> mediaAnalyzer.analyzeAudioFocus(packageName));
        if (types.contains(AnalysisType.BLUETOOTH))
            safeAdd(results, "Bluetooth",                 () -> mediaAnalyzer.analyzeBluetooth(packageName));
        if (types.contains(AnalysisType.FGS_START_BLOCKED))
            safeAdd(results, "FgsStartBlocked",           () -> processAnalyzer.analyzeFgsStartBlocked(packageName));
        if (types.contains(AnalysisType.NETWORK_POLICY))
            safeAdd(results, "NetworkPolicy",             () -> networkAnalyzer.analyzeNetworkPolicy(packageName));
        if (types.contains(AnalysisType.BACKGROUND_LOCATION_PERMISSION))
            safeAdd(results, "BackgroundLocationPerm",    () -> sensorsLocationAnalyzer.analyzeBackgroundLocationPermission(packageName));
        if (types.contains(AnalysisType.BLUETOOTH_PERMISSIONS))
            safeAdd(results, "BluetoothPermissions",      () -> mediaAnalyzer.analyzeBluetoothPermissions(packageName));

        if (types.contains(AnalysisType.ALARMS))
            safeAdd(results, "Alarms",                    () -> schedulingAnalyzer.analyzeAlarms(packageName));
        if (types.contains(AnalysisType.JOBS))
            safeAdd(results, "Jobs",                      () -> schedulingAnalyzer.analyzeJobs(packageName));
        if (types.contains(AnalysisType.PENDING_INTENTS))
            safeAdd(results, "PendingIntents",            () -> schedulingAnalyzer.analyzePendingIntents(packageName));
        if (types.contains(AnalysisType.EXCESSIVE_WAKEUPS))
            safeAdd(results, "ExcessiveWakeups",          () -> powerAnalyzer.analyzeExcessiveWakeups(packageName));
        if (types.contains(AnalysisType.CONTENT_OBSERVERS))
            safeAdd(results, "ContentObservers",          () -> componentsAnalyzer.analyzeContentObservers(packageName));
        if (types.contains(AnalysisType.FCM_REGISTRATION))
            safeAdd(results, "FcmRegistration",           () -> componentsAnalyzer.analyzeFcmRegistration(packageName));
        if (types.contains(AnalysisType.APP_OPS))
            safeAdd(results, "AppOps",                    () -> dozeOpsAnalyzer.analyzeAppOps(packageName));
        if (types.contains(AnalysisType.CHAIN_LAUNCH))
            safeAdd(results, "ChainLaunch",               () -> miscAnalyzer.analyzeChainLaunch(packageName));
        if (types.contains(AnalysisType.BROADCAST_RECEIVERS))
            safeAdd(results, "BroadcastReceivers",        () -> componentsAnalyzer.analyzeBroadcastReceivers(packageName));
        if (types.contains(AnalysisType.BOOT_RECEIVERS))
            safeAdd(results, "BootReceivers",             () -> componentsAnalyzer.analyzeBootReceivers(packageName));
        if (types.contains(AnalysisType.CONTENT_PROVIDERS))
            safeAdd(results, "ContentProviders",          () -> componentsAnalyzer.analyzeContentProviders(packageName));
        if (types.contains(AnalysisType.SYNC_ADAPTERS))
            safeAdd(results, "SyncAdapters",              () -> componentsAnalyzer.analyzeSyncAdapters(packageName));
        if (types.contains(AnalysisType.DOZE_EXEMPTION))
            safeAdd(results, "DozeExemption",             () -> dozeOpsAnalyzer.analyzeDozeExemption(packageName));
        if (types.contains(AnalysisType.STANDBY_BUCKET))
            safeAdd(results, "StandbyBucket",             () -> dozeOpsAnalyzer.analyzeStandbyBucket(packageName));
        if (types.contains(AnalysisType.BATTERY_STATS))
            safeAdd(results, "BatteryStats",              () -> powerAnalyzer.analyzeBatteryStats(packageName));
        if (types.contains(AnalysisType.BROADCAST_EFFICIENCY))
            safeAdd(results, "BroadcastEfficiency",       () -> miscAnalyzer.analyzeBroadcastEfficiency(packageName));
        if (types.contains(AnalysisType.MULTIPLE_PROCESSES))
            safeAdd(results, "MultipleProcesses",         () -> miscAnalyzer.analyzeMultipleProcesses(packageName));
        if (types.contains(AnalysisType.ACCESSIBILITY_AND_IME))
            safeAdd(results, "AccessibilityAndIme",       () -> miscAnalyzer.analyzeAccessibilityAndIme(packageName));
        if (types.contains(AnalysisType.DEVICE_ADMIN))
            safeAdd(results, "DeviceAdmin",               () -> miscAnalyzer.analyzeDeviceAdmin(packageName));
        if (types.contains(AnalysisType.USAGE_STATS))
            safeAdd(results, "UsageStats",                () -> miscAnalyzer.analyzeUsageStats(packageName));

        if (results.isEmpty()) {
            results.add(new TriggerInfo(
                    context.getString(R.string.triggers_none_title),
                    context.getString(R.string.triggers_none_detail),
                    context.getString(R.string.triggers_none_explanation),
                    TriggerInfo.Severity.INFO));
        }

        AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": analyze: finished pkg=" + packageName + " totalTriggers=" + results.size());

        return results;
    }

    public enum AppStatus {
        ACTIVE,
        BACKGROUND,
        BACKGROUND_SERVICE,
        CACHED_WITH_SERVICE,
        CACHED_RECENT,
        CACHED_IDLE
    }

    public AppStatus resolveAppStatus(String packageName) {
        AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": resolveAppStatus: start pkg=" + packageName);
        try {
            String psOutput = shellManager.runShellCommandAndGetFullOutput(
                    "ps -eo pid,name | grep " + packageName);
            AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": resolveAppStatus: ps output=" + (psOutput != null ? psOutput.trim() : "null"));

            if (psOutput == null || psOutput.trim().isEmpty()) {
                AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": resolveAppStatus: result=null (not running)");
                return null;
            }

            String pid = null;
            for (String line : psOutput.trim().split("\n")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 2) continue;
                if (parts[1].equals(packageName)) { pid = parts[0]; break; }
                if (pid == null && parts[1].startsWith(packageName)) pid = parts[0];
            }

            if (pid == null) {
                AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": resolveAppStatus: result=null (pid not parsed)");
                return null;
            }

            String adjStr = shellManager.runShellCommandAndGetFullOutput(
                    "cat /proc/" + pid + "/oom_score_adj");
            AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": resolveAppStatus: pid=" + pid + " oom_score_adj=" + (adjStr != null ? adjStr.trim() : "null"));

            if (adjStr == null || adjStr.trim().isEmpty()) {
                AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": resolveAppStatus: result=null (oom_score_adj unreadable)");
                return null;
            }

            int adj = Integer.parseInt(adjStr.trim());

            if (adj <= 224) {
                AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": resolveAppStatus: result=ACTIVE (adj=" + adj + ")");
                return AppStatus.ACTIVE;
            }

            if (adj <= 499) {
                boolean hasFgs = hasActiveService(packageName);
                AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": resolveAppStatus: adj=" + adj + " hasFgs=" + hasFgs);
                return hasFgs ? AppStatus.BACKGROUND_SERVICE : AppStatus.BACKGROUND;
            }

            boolean hasService = hasActiveService(packageName);
            if (hasService) {
                AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": resolveAppStatus: result=CACHED_WITH_SERVICE (adj=" + adj + ")");
                return AppStatus.CACHED_WITH_SERVICE;
            }

            if (adj < 920) {
                AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": resolveAppStatus: result=CACHED_RECENT (adj=" + adj + ")");
                return AppStatus.CACHED_RECENT;
            }

            AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": resolveAppStatus: result=CACHED_IDLE (adj=" + adj + ")");
            return AppStatus.CACHED_IDLE;

        } catch (NumberFormatException e) {
            AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": resolveAppStatus: oom_score_adj parse error", e);
            return null;
        } catch (Exception e) {
            AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": resolveAppStatus failed", e);
            return null;
        }
    }

    private boolean hasActiveService(String packageName) {
        try {
            String out = shellManager.runShellCommandAndGetFullOutput(
                    "dumpsys activity services " + packageName);
            if (out == null) return false;
            return out.contains("ServiceRecord") && out.contains(packageName);
        } catch (Exception e) {
            AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": hasActiveService failed", e);
            return false;
        }
    }

    private interface Analyzer { List<TriggerInfo> run() throws Exception; }

    private void safeAdd(List<TriggerInfo> out, String name, Analyzer a) {
        try {
            List<TriggerInfo> partial = a.run();
            if (partial != null && !partial.isEmpty()) {
                out.addAll(partial);
                AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": " + name + " - OK (" + partial.size() + " trigger(s): "
                        + summarizeTriggers(partial) + ")");
            } else {
                AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": " + name + " - OK (no triggers)");
            }
        } catch (Exception e) {
            AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": " + name + " - ERROR", e);
        }
    }

    private String summarizeTriggers(List<TriggerInfo> list) {
        StringBuilder sb = new StringBuilder();
        for (TriggerInfo t : list) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(t.category);
        }
        return sb.toString();
    }

    public String resolveUid(String packageName) {
        try {
            String out = shellManager.runShellCommandAndGetFullOutput(
                    "dumpsys package " + packageName);
            if (out != null) {
                Matcher m = Pattern.compile("(?:userId|appId|\\buid)=(\\d{4,6})").matcher(out);
                if (m.find()) return m.group(1);
            }
        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": resolveUid/dumpsys failed", e); }

        try {
            String pmOut = shellManager.runShellCommandAndGetFullOutput(
                    "pm list packages -U | grep " + packageName);
            if (pmOut != null) {
                Matcher m = Pattern.compile("uid:(\\d+)").matcher(pmOut);
                if (m.find()) return m.group(1);
            }
        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": resolveUid/pm fallback failed", e); }

        return null;
    }

    public String resolveAppName(String pkg) {
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(pkg, 0);
            return context.getPackageManager().getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException e) {
            AppDebugManager.w(Category.TRIGGERS, FILE_NAME + ": resolveAppName: package not found pkg=" + pkg);
            return pkg;
        }
    }

    public String bucketValueToName(int v) {
        if (v <= 10) return "ACTIVE";
        if (v <= 20) return "WORKING_SET";
        if (v <= 30) return "FREQUENT";
        if (v <= 40) return "RARE";
        if (v <= 45) return "RESTRICTED";
        return "NEVER";
    }

    public String shortenAction(String action) {
        if (action.startsWith("android.intent.action.")) return action.substring(22);
        if (action.startsWith("android.net.conn."))      return action.substring(17);
        if (action.startsWith("android.net."))           return action.substring(12);
        if (action.startsWith("com.android."))           return action.substring(12);
        return action;
    }

    public String formatInterval(long ms) {
        long sec = ms / 1000;
        if (sec < 60)   return context.getString(R.string.triggers_alarms_interval_sec,  (int) sec);
        if (sec < 3600) return context.getString(R.string.triggers_alarms_interval_min,  (int)(sec/60));
        return             context.getString(R.string.triggers_alarms_interval_hour, (int)(sec/3600));
    }

    public String formatDuration(long ms) {
        long sec = ms / 1000;
        if (sec < 60)   return sec + context.getString(R.string.time_unit_sec);
        if (sec < 3600) return (sec/60) + context.getString(R.string.time_unit_min);
        return               (sec/3600) + context.getString(R.string.time_unit_hour);
    }

    public String formatBytes(long bytes) {
        if (bytes < 1024)            return bytes + " B";
        if (bytes < 1024*1024)       return (bytes/1024) + " KB";
        if (bytes < 1024L*1024*1024) return (bytes/(1024*1024)) + " MB";
        return (bytes/(1024L*1024*1024)) + " GB";
    }

    public static boolean anyContains(List<String> list, String... tokens) {
        for (String s : list) for (String t : tokens) if (s.contains(t)) return true;
        return false;
    }

    public static String trimTo(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    public int calculateAggressionScore(List<TriggerInfo> triggers) {
        int score = 0;
        for (TriggerInfo t : triggers) {
            score += getTriggerScore(t);
        }
        return Math.min(score, 100);
    }

    private int getTriggerScore(TriggerInfo t) {
        switch (t.group) {
            case ACTIVE_NOW: return 6;
            case CAN_WAKE:   return 5;
            case OTHER:      return getOtherScore(t);
            default:         return 0;
        }
    }

    private int getOtherScore(TriggerInfo t) {
        if (t.severity == TriggerInfo.Severity.INFO) return 0;

        String cat    = t.category != null ? t.category : "";
        String detail = t.detail   != null ? t.detail   : "";

        if (cat.equals(context.getString(R.string.triggers_cat_proc_state))) {
            return 0;
        }

        if (cat.contains("throttled") || cat.contains("cancelled")) {
            return 0;
        }

        if (cat.equals(context.getString(R.string.triggers_cat_provider))) {
            return 0;
        }

        if (cat.equals(context.getString(R.string.triggers_cat_chain_launch))
                && (detail.contains("blocked") || detail.contains("BAL_BLOCKED"))) {
            return 0;
        }

        if (cat.equals(context.getString(R.string.triggers_cat_fg_service))
                && detail.contains("blocked")) {
            return 0;
        }

        if (cat.equals(context.getString(R.string.triggers_cat_bucket))) {
            if (t.severity == TriggerInfo.Severity.HIGH)   return 4;
            if (t.severity == TriggerInfo.Severity.MEDIUM) return 3;
            return 1;
        }

        switch (t.severity) {
            case HIGH:   return 4;
            case MEDIUM: return 3;
            case LOW:    return 1;
            default:     return 0;
        }
    }
}
