package com.gree1d.reappzuku;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.gree1d.reappzuku.db.AppDatabase;
import com.gree1d.reappzuku.db.ResourceSnapshot;
import com.gree1d.reappzuku.db.ResourceSnapshotDao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages collection and aggregation of per-app resource usage snapshots.
 *
 * Data sources:
 *  - Battery:  dumpsys batterystats --charged --checkin   (pwi uid lines → mAh per UID)
 *  - RAM:      dumpsys procstats --hours N                (avg PSS per package)
 *  - CPU:      batterystats per-app cpu= fields           (cumulative ms since charge)
 *
 * Snapshot strategy:
 *  Every 30–60 minutes a full snapshot is saved to Room (ResourceSnapshot table).
 *  Period queries (2h / 6h / 12h / 24h) diff the two closest snapshots to that window.
 *
 *  NOTE: batterystats --charged resets on every charge cycle.
 *  batteryMah is a CUMULATIVE value since last charge — diffing works correctly as long
 *  as no charge event occurs between two snapshots. If the device was charged between
 *  snapshots the delta goes negative; we clamp it to 0 in that case.
 */
public class BatteryStatsManager {

    private static final String TAG = "BatteryStatsManager";

    /** Minimum interval between snapshots (10 min). Prevents duplicate writes. */
    private static final long MIN_SNAPSHOT_INTERVAL_MS = 10 * 60 * 1000L;

    /** Percentage threshold — apps at or below this share are grouped into "Others". */
    public static final float OTHERS_THRESHOLD_PCT = 5.0f;

    /** Minimum number of top apps always shown as individual slices (even if < threshold). */
    public static final int MIN_TOP_SLICES = 3;

    // ──────────────────────────────────────────────────────────────────────────
    // Regex patterns
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Matches a pwi uid line from --checkin output.
     *
     * Real format (verified on Android 12–14):
     *   9,<uid>,l,pwi,uid,<mAh>,1,...
     *
     * Fields (0-based, comma-split):
     *   [0] version      → "9"
     *   [1] uid          → numeric app UID  (THIS is the UID we need)
     *   [2] "l"
     *   [3] "pwi"
     *   [4] "uid"        → literal token distinguishing per-uid rows
     *   [5] mAh          → battery drain value
     *   [6] "1"          → present flag
     *   [7] foreground mAh (may be 0)
     *
     * We detect the line by the literal pattern "^9,\d+,l,pwi,uid," and then
     * extract uid from parts[1] and mAh from parts[5].
     */
    private static final Pattern PWI_UID_LINE = Pattern.compile("^9,\\d+,l,pwi,uid,");

    /**
     * Matches a cpu line from --checkin output.
     *
     * Format:  9,<uid>,l,cpu,<user_ms>,<system_ms>,0
     *
     * Fields (0-based, comma-split):
     *   [1] uid
     *   [4] user CPU ms (cumulative since last charge)
     *   [5] system CPU ms (cumulative since last charge)
     *
     * cpuTimeMs = parts[4] + parts[5]
     */
    private static final Pattern CPU_UID_LINE = Pattern.compile("^9,\\d+,l,cpu,");

    /**
     * Matches package stat lines from procstats.
     * Example:  "  * com.example.app / u0a123 / v456:"
     */
    private static final Pattern PROCSTATS_PKG =
            Pattern.compile("^\\s{2}\\*\\s([\\w.]+)\\s*/\\su\\d+a(\\d+)");

    /**
     * Extracts the average PSS value from a procstats TOTAL line.
     *
     * Supports both dot and comma as decimal separator to handle all system locales.
     * Samsung/One UI with Russian locale outputs commas: "2,8MB-9,9MB-..."
     * Stock Android / English locale outputs dots: "2.8MB-9.9MB-..."
     *
     * Real format:
     *   TOTAL: 100% (346MB-346MB-346MB/161MB-161MB-161MB/288MB-288MB-288MB over 1)
     *   Fields: PSS min-avg-max / USS min-avg-max / RSS min-avg-max
     *
     * We want the PSS average (second number in the first triplet).
     * Pattern captures the three PSS values; group(2) = avg PSS in MB.
     */
    private static final Pattern PROCSTATS_PSS =
            Pattern.compile(
                "(\\d+(?:[.,]\\d+)?)MB-(\\d+(?:[.,]\\d+)?)MB-(\\d+(?:[.,]\\d+)?)MB"
            );

    // ──────────────────────────────────────────────────────────────────────────
    // Fields
    // ──────────────────────────────────────────────────────────────────────────

    private final Context context;
    private final Handler handler;
    private final ExecutorService executor;
    private final ShellManager shellManager;
    private volatile ResourceSnapshotDao dao;

    /**
     * Battery design capacity in mAh, read once and cached.
     * -1 means not yet initialized.
     */
    private volatile double cachedCapacityMah = -1;

    /**
     * Number of CPU cores, read once and cached.
     * 0 means not yet initialized.
     */
    private volatile int cachedCpuCoreCount = 0;

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    public BatteryStatsManager(@NonNull Context context,
                               @NonNull Handler handler,
                               @NonNull ExecutorService executor,
                               @NonNull ShellManager shellManager) {
        this.context      = context.getApplicationContext();
        this.handler      = handler;
        this.executor     = executor;
        this.shellManager = shellManager;
    }

    /**
     * Convenience constructor for ShappkyService where Handler/Executor are managed externally.
     */
    public BatteryStatsManager(@NonNull Context context,
                               @NonNull ShellManager shellManager) {
        this.context      = context.getApplicationContext();
        this.handler      = new Handler(Looper.getMainLooper());
        this.executor     = java.util.concurrent.Executors.newSingleThreadExecutor();
        this.shellManager = shellManager;
    }

    /** Lazy initializer — always called from executor thread, never from main thread. */
    private ResourceSnapshotDao getDao() {
        if (dao == null) {
            dao = AppDatabase.getInstance(context).resourceSnapshotDao();
        }
        return dao;
    }

    /** Public data container for one app's resource stats over a period. */
    public static class AppResourceStats {
        public final String packageName;
        public final String appName;
        /** Battery drain estimate in mAh over the period. */
        public final double batteryMah;
        /** Actual CPU usage over the period as % of wall-clock time (0–100+). */
        public final double cpuPct;
        /** Average RAM in MB (PSS) over the period. */
        public final double ramMb;
        /** Peak RAM in MB (max PSS snapshot) over the period. */
        public final double peakRamMb;
        /** Whether this app is ReAppzuku itself. */
        public final boolean isSelf;

        public AppResourceStats(String packageName, String appName,
                                double batteryMah, double cpuPct, double ramMb,
                                double peakRamMb, boolean isSelf) {
            this.packageName = packageName;
            this.appName     = appName;
            this.batteryMah  = batteryMah;
            this.cpuPct      = cpuPct;
            this.ramMb       = ramMb;
            this.peakRamMb   = peakRamMb;
            this.isSelf      = isSelf;
        }
    }

    /** Result of a period query, ready for the chart. */
    public static class PeriodStats {
        public final List<AppResourceStats> sorted;
        public final boolean hasData;
        public final double actualHours;
        public final String dataHint;
        /**
         * True when data exists but covers less than half of the requested period.
         * The UI should show an "Incomplete data" warning instead of hiding the chart.
         */
        public final boolean isPartialData;

        PeriodStats(List<AppResourceStats> sorted, boolean hasData,
                    double actualHours, String dataHint, boolean isPartialData) {
            this.sorted        = sorted;
            this.hasData       = hasData;
            this.actualHours   = actualHours;
            this.dataHint      = dataHint;
            this.isPartialData = isPartialData;
        }
    }

    /** Async snapshot trigger. Posts onComplete to the main thread when done. */
    public void takeSnapshotAsync(@Nullable Runnable onComplete) {
        executor.execute(() -> {
            takeSnapshotBlocking();
            if (onComplete != null) handler.post(onComplete);
        });
    }

    /**
     * Returns aggregated stats for the given period in hours (2 / 6 / 12 / 24).
     * Callback is invoked on the main thread.
     */
    public void getStatsForPeriodAsync(int hours, @NonNull StatsCallback callback) {
        executor.execute(() -> {
            PeriodStats result = getStatsForPeriodBlocking(hours);
            handler.post(() -> callback.onResult(result));
        });
    }

    /**
     * Returns per-hour breakdown for a single app (for the detail graph).
     * Callback is invoked on the main thread.
     */
    public interface StatsCallback  { void onResult(PeriodStats stats); }
    public interface HourlyCallback { void onResult(HourlyResult result); }

    /** Activity level for a 30-minute slot. */
    public enum ActivityLevel {
        NONE,    // no data / app not running
        LOW,     // below average vs all apps
        MEDIUM,  // around average
        HIGH     // significantly above average
    }

    /** One 30-minute slot on the activity chart. */
    public static class ActivitySlice {
        public final long slotTimestamp; // slot start time in ms — formatted by the UI layer
        public final ActivityLevel level;
        public final double cpuPercent;
        public final double ramMb;

        ActivitySlice(long slotTimestamp, ActivityLevel level, double cpuPercent, double ramMb) {
            this.slotTimestamp = slotTimestamp;
            this.level         = level;
            this.cpuPercent    = cpuPercent;
            this.ramMb         = ramMb;
        }
    }

    /** Aggregated min/avg/max stats for the full period (used in detail screen). */
    public static class HourlyPeriodStats {
        public final double minBatteryMah, avgBatteryMah, maxBatteryMah;
        public final double minCpuPct,     avgCpuPct,     maxCpuPct;
        public final double minRamMb,      avgRamMb,      maxRamMb;

        HourlyPeriodStats(double minBat, double avgBat, double maxBat,
                    double minCpu, double avgCpu, double maxCpu,
                    double minRam, double avgRam, double maxRam) {
            minBatteryMah = minBat; avgBatteryMah = avgBat; maxBatteryMah = maxBat;
            minCpuPct     = minCpu; avgCpuPct     = avgCpu; maxCpuPct     = maxCpu;
            minRamMb      = minRam; avgRamMb      = avgRam; maxRamMb      = maxRam;
        }
    }

    /** Result of a per-app hourly query, including partial-data metadata. */
    public static class HourlyResult {
        public final List<ActivitySlice> slices;
        public final HourlyPeriodStats stats;
        public final boolean isPartialData;

        HourlyResult(List<ActivitySlice> slices, HourlyPeriodStats stats, boolean isPartialData) {
            this.slices        = slices;
            this.stats         = stats;
            this.isPartialData = isPartialData;
        }

        /** Convenience: empty result with no data. */
        static HourlyResult empty(boolean isPartialData) {
            return new HourlyResult(new ArrayList<>(), null, isPartialData);
        }
    }

    // Keep HourlyPoint for backward compat — no longer used by UI but may exist elsewhere
    /** @deprecated Use ActivitySlice + PeriodStats instead. */
    @Deprecated
    public static class HourlyPoint {
        public final String hourLabel;
        public final double batteryMah;
        public final double cpuPercent;
        public final double ramMb;

        HourlyPoint(String hourLabel, double batteryMah, double cpuPercent, double ramMb) {
            this.hourLabel  = hourLabel;
            this.batteryMah = batteryMah;
            this.cpuPercent = cpuPercent;
            this.ramMb      = ramMb;
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private — snapshot collection
    // ──────────────────────────────────────────────────────────────────────────

    @WorkerThread
    private void takeSnapshotBlocking() {
        long now = System.currentTimeMillis();

        try {
            // Throttle: skip if last snapshot was too recent
            ResourceSnapshot last = getDao().getLatestSnapshot();
            if (last != null && (now - last.timestamp) < MIN_SNAPSHOT_INTERVAL_MS) {
                Log.d(TAG, "Snapshot skipped — too soon after last one");
                return;
            }

            // 1. Battery (mAh) + CPU time (ms) — single batterystats --checkin call.
            //    Each source is isolated: failure of one does not abort the others.
            Map<String, Double> batteryMahByPkg = new HashMap<>();
            Map<String, Long>   cpuMsByPkg      = new HashMap<>();
            try {
                collectCheckinStats(batteryMahByPkg, cpuMsByPkg);
            } catch (Exception e) {
                Log.e(TAG, "collectCheckinStats failed, battery/cpu data will be empty", e);
            }

            // 2. RAM (PSS MB) from procstats, with fallback to meminfo.
            Map<String, Double> ramMbByPkg = new HashMap<>();
            try {
                collectProcStatsRam(24, ramMbByPkg);
                if (ramMbByPkg.isEmpty()) {
                    Log.d(TAG, "procstats returned no RAM data, trying meminfo fallback");
                    collectMeminfoRam(ramMbByPkg);
                }
            } catch (Exception e) {
                Log.e(TAG, "RAM collection failed, trying meminfo fallback", e);
                try {
                    collectMeminfoRam(ramMbByPkg);
                } catch (Exception e2) {
                    Log.e(TAG, "meminfo fallback also failed, RAM data will be empty", e2);
                }
            }

            // 3. System-wide CPU baseline from /proc/stat (no root required).
            long[] jiffies = readProcStatJiffies(); // [totalJiffies, activeJiffies]

            // 4. Battery level (0-100) — no root required.
            android.os.BatteryManager bm =
                    (android.os.BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            int batteryLevel = bm != null
                    ? bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    : 50;

            // 5. Merge into one snapshot row per package
            java.util.Set<String> allPkgs = new java.util.HashSet<>();
            allPkgs.addAll(batteryMahByPkg.keySet());
            allPkgs.addAll(ramMbByPkg.keySet());
            allPkgs.addAll(cpuMsByPkg.keySet());

            // Pre-compute batch-wide sum of raw pwi values so hourly charts can
            // normalize per-app batteryMah → real mAh without extra DB queries.
            double totalRawPwiBatch = 0;
            for (double v : batteryMahByPkg.values()) totalRawPwiBatch += v;

            for (String pkg : allPkgs) {
                ResourceSnapshot snap = new ResourceSnapshot();
                snap.timestamp        = now;
                snap.packageName      = pkg;
                snap.batteryMah       = getOrZero(batteryMahByPkg, pkg);
                snap.ramMb            = getOrZero(ramMbByPkg, pkg);
                snap.cpuTimeMs        = cpuMsByPkg.containsKey(pkg) ? cpuMsByPkg.get(pkg) : 0L;
                snap.totalCpuJiffies  = jiffies[0];
                snap.activeCpuJiffies = jiffies[1];
                snap.batteryLevelPct  = batteryLevel;
                snap.totalRawPwiBatch = totalRawPwiBatch;
                getDao().insert(snap);
            }

            // Prune old snapshots (keep 24 hours)
            getDao().deleteOlderThan(now - 24 * 3600_000L);

            Log.d(TAG, "Snapshot saved: " + allPkgs.size() + " apps"
                    + "  battery=" + batteryMahByPkg.size()
                    + "  ram=" + ramMbByPkg.size()
                    + "  cpu=" + cpuMsByPkg.size());

        } catch (Exception e) {
            Log.e(TAG, "takeSnapshotBlocking: unexpected error, snapshot aborted", e);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private — battery capacity & CPU core count (cached, no root required)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns battery design capacity in mAh, reading and caching it on first call.
     *
     * Priority:
     *   1. /sys/class/power_supply/battery/charge_full_design  (µAh → /1000)
     *   2. /sys/class/power_supply/battery/charge_full         (µAh → /1000)
     *   3. dumpsys batterystats | grep Capacity  ("Capacity: 5100, ...")
     *   4. BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER (µAh → /1000, real-time)
     *   5. 4000 mAh fallback
     *
     * Note: on MIUI/HyperOS the sysfs path returns Permission denied,
     * so the dumpsys fallback is the real primary source on those devices.
     * On some Huawei/Honor devices dumpsys is also blocked — BatteryManager API
     * is the last resort before the hardcoded fallback.
     */
    @WorkerThread
    public double getBatteryCapacityMah() {
        if (cachedCapacityMah > 0) return cachedCapacityMah;

        // 1 & 2: sysfs (works on stock Android, may be denied on MIUI/HyperOS)
        String[] sysPaths = {
            "/sys/class/power_supply/battery/charge_full_design",
            "/sys/class/power_supply/battery/charge_full"
        };
        for (String path : sysPaths) {
            try {
                String line = shellManager.runCommandAndGetOutput("cat " + path);
                if (line != null && !line.isEmpty()) {
                    long uah = Long.parseLong(line.trim());
                    if (uah > 100_000) { // sanity: >100 mAh
                        cachedCapacityMah = uah / 1000.0;
                        Log.d(TAG, "Battery capacity from " + path + ": " + cachedCapacityMah + " mAh");
                        return cachedCapacityMah;
                    }
                }
            } catch (Exception ignored) {}
        }

        // 3: dumpsys batterystats — "Capacity: 5100, Computed drain: ..."
        try {
            String output = shellManager.runCommandAndGetOutput(
                    "dumpsys batterystats | grep -m1 'Capacity:'");
            if (output != null) {
                java.util.regex.Matcher m =
                        java.util.regex.Pattern.compile("Capacity:\\s*(\\d+)").matcher(output);
                if (m.find()) {
                    double cap = parseLocaleDouble(m.group(1));
                    if (cap > 100) {
                        cachedCapacityMah = cap;
                        Log.d(TAG, "Battery capacity from dumpsys: " + cachedCapacityMah + " mAh");
                        return cachedCapacityMah;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "dumpsys batterystats capacity read failed", e);
        }

        // 4: BatteryManager API — CHARGE_COUNTER gives current charge in µAh.
        //    Not design capacity, but better than hardcoded 4000 when above sources fail.
        //    Only use if value is plausible (> 500 mAh).
        try {
            android.os.BatteryManager bm =
                    (android.os.BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                int chargeUah = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                if (chargeUah > 500_000) { // > 500 mAh in µAh
                    // charge_counter is current charge, not design capacity.
                    // Estimate design capacity assuming current level.
                    int levelPct = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
                    if (levelPct > 5 && levelPct <= 100) {
                        double estimatedCapacity = (chargeUah / 1000.0) / (levelPct / 100.0);
                        if (estimatedCapacity > 500 && estimatedCapacity < 30_000) {
                            cachedCapacityMah = estimatedCapacity;
                            Log.d(TAG, "Battery capacity estimated from BatteryManager: "
                                    + cachedCapacityMah + " mAh (level=" + levelPct + "%)");
                            return cachedCapacityMah;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "BatteryManager capacity read failed", e);
        }

        Log.w(TAG, "Could not read battery capacity, using 4000 mAh fallback");
        cachedCapacityMah = 4000.0;
        return cachedCapacityMah;
    }

    /**
     * Returns the number of CPU cores, reading and caching on first call.
     *
     * Reads /sys/devices/system/cpu/present which contains "0-N" (N+1 cores)
     * or "0" (1 core). Falls back to Runtime.availableProcessors().
     */
    @WorkerThread
    private int getCpuCoreCount() {
        if (cachedCpuCoreCount > 0) return cachedCpuCoreCount;
        try {
            String output = shellManager.runCommandAndGetOutput(
                    "cat /sys/devices/system/cpu/present");
            if (output != null && !output.isEmpty()) {
                String[] parts = output.trim().split("-");
                cachedCpuCoreCount = parts.length == 2
                        ? Integer.parseInt(parts[1]) + 1
                        : 1;
                Log.d(TAG, "CPU cores: " + cachedCpuCoreCount);
                return cachedCpuCoreCount;
            }
        } catch (Exception e) {
            Log.w(TAG, "readCpuCoreCount failed", e);
        }
        cachedCpuCoreCount = Runtime.getRuntime().availableProcessors();
        return cachedCpuCoreCount;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private — checkin stats parsing (battery + CPU in one call)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Parses both battery mAh and CPU time from a single
     * "dumpsys batterystats --charged --checkin" call.
     *
     * pwi uid line  →  battery mAh per UID:
     *   9,<uid>,l,pwi,uid,<mAh>,1,<fg_mAh>,0
     *   uid=parts[1], mAh=parts[5]
     *
     * cpu line  →  cumulative CPU ms per UID:
     *   9,<uid>,l,cpu,<user_ms>,<system_ms>,0
     *   uid=parts[1], cpuMs=parts[4]+parts[5]
     *
     * Both maps are populated with per-package values (UID split equally
     * across all packages sharing that UID).
     *
     * UID filtering:
     *   UIDs >= 100000 belong to secondary users / Work Profiles (e.g. Samsung Knox
     *   user 150 → UIDs 15000000+). Calling getPackagesForUid() on these throws
     *   SecurityException without INTERACT_ACROSS_USERS permission. We skip them —
     *   they are not relevant for the primary user's statistics.
     *
     * Fallback:
     *   If --checkin returns empty output (Huawei/Honor block this flag), we fall
     *   back to parsing the human-readable "dumpsys batterystats --charged" format.
     */
    @WorkerThread
    private void collectCheckinStats(@NonNull Map<String, Double> batteryMahOut,
                                     @NonNull Map<String, Long> cpuMsOut) {
        String cmd = "dumpsys batterystats --charged --checkin";
        String output = shellManager.runCommandAndGetOutput(cmd);

        // Huawei/Honor and some other OEMs block --checkin or return empty output.
        // Detect by checking if any pwi/cpu lines are present.
        boolean hasCheckinData = output != null && (output.contains(",l,pwi,") || output.contains(",l,cpu,"));

        if (!hasCheckinData) {
            Log.d(TAG, "--checkin returned no usable data, trying human-readable fallback");
            collectCheckinStatsFallback(batteryMahOut, cpuMsOut);
            return;
        }

        Map<Integer, Double> uidToMah   = new HashMap<>();
        Map<Integer, Long>   uidToCpuMs = new HashMap<>();

        for (String line : output.split("\n")) {
            if (PWI_UID_LINE.matcher(line).find()) {
                // 9,<uid>,l,pwi,uid,<mAh>,...
                try {
                    String[] parts = line.split(",");
                    if (parts.length < 6) continue;
                    int uid    = Integer.parseInt(parts[1].trim());
                    // Skip UIDs from secondary users / Work Profiles (Samsung Knox etc.)
                    // Primary user UIDs are always < 100000.
                    if (uid >= 100_000) continue;
                    double mah = parseLocaleDouble(parts[5].trim());
                    if (mah > 0) uidToMah.put(uid, mah);
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    Log.w(TAG, "pwi parse error: " + line, e);
                }
            } else if (CPU_UID_LINE.matcher(line).find()) {
                // 9,<uid>,l,cpu,<user_ms>,<system_ms>,0
                try {
                    String[] parts = line.split(",");
                    if (parts.length < 6) continue;
                    int uid = Integer.parseInt(parts[1].trim());
                    if (uid >= 100_000) continue;
                    long userMs   = parseLocaleDoubleToLong(parts[4].trim());
                    long systemMs = parseLocaleDoubleToLong(parts[5].trim());
                    long total    = userMs + systemMs;
                    if (total > 0) uidToCpuMs.put(uid, total);
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    Log.w(TAG, "cpu parse error: " + line, e);
                }
            }
        }

        mapUidsToPkgs(uidToMah, uidToCpuMs, batteryMahOut, cpuMsOut);
    }

    /**
     * Fallback battery/CPU parser for devices where --checkin is blocked (Huawei/Honor).
     *
     * Parses human-readable "dumpsys batterystats --charged" output.
     * Looks for lines like:
     *   Uid u0a123:
     *     ...
     *     Wifi Running: ...  (battery drain proxy — not mAh directly)
     *
     * Since human-readable format doesn't give per-app mAh directly, we extract
     * CPU time from "u0a<N>: ... cpu=<user>ms usr + <sys>ms krn" lines and leave
     * battery at 0 (will show CPU chart only, battery chart will be empty).
     *
     * Pattern: "    u0a<N>:" header, then "cpu=Xms usr + Yms krn" on next lines.
     */
    private static final Pattern HR_UID_HEADER =
            Pattern.compile("^\\s+Uid\\s+u0a(\\d+):");
    private static final Pattern HR_CPU_LINE =
            Pattern.compile("cpu=(\\d+(?:[.,]\\d+)?)ms\\s+usr\\s*\\+\\s*(\\d+(?:[.,]\\d+)?)ms\\s+krn");

    @WorkerThread
    private void collectCheckinStatsFallback(@NonNull Map<String, Double> batteryMahOut,
                                             @NonNull Map<String, Long> cpuMsOut) {
        String output = shellManager.runCommandAndGetOutput("dumpsys batterystats --charged");
        if (output == null || output.isEmpty()) {
            Log.w(TAG, "batterystats fallback also returned empty output");
            return;
        }

        Map<Integer, Long> uidToCpuMs = new HashMap<>();
        int currentAppUid = -1;

        for (String line : output.split("\n")) {
            Matcher uidMatcher = HR_UID_HEADER.matcher(line);
            if (uidMatcher.find()) {
                try {
                    currentAppUid = Integer.parseInt(uidMatcher.group(1)) + 10000; // u0a123 → uid 10123
                } catch (NumberFormatException e) {
                    currentAppUid = -1;
                }
                continue;
            }
            if (currentAppUid < 0) continue;

            Matcher cpuMatcher = HR_CPU_LINE.matcher(line);
            if (cpuMatcher.find()) {
                long userMs   = parseLocaleDoubleToLong(cpuMatcher.group(1));
                long systemMs = parseLocaleDoubleToLong(cpuMatcher.group(2));
                long total    = userMs + systemMs;
                if (total > 0) uidToCpuMs.merge(currentAppUid, total, Long::sum);
            }
        }

        Log.d(TAG, "batterystats fallback: parsed cpu for " + uidToCpuMs.size() + " UIDs");
        mapUidsToPkgs(new HashMap<>(), uidToCpuMs, batteryMahOut, cpuMsOut);
    }

    /**
     * Maps UID→value maps to package name maps via PackageManager.
     *
     * Handles SecurityException per-entry — on devices with Work Profiles,
     * some UIDs may still throw even after the >= 100000 filter (e.g. isolated
     * processes, vendor UIDs). We log and skip them gracefully.
     */
    @WorkerThread
    private void mapUidsToPkgs(@NonNull Map<Integer, Double> uidToMah,
                                @NonNull Map<Integer, Long> uidToCpuMs,
                                @NonNull Map<String, Double> batteryMahOut,
                                @NonNull Map<String, Long> cpuMsOut) {
        android.content.pm.PackageManager pm = context.getPackageManager();

        for (Map.Entry<Integer, Double> e : uidToMah.entrySet()) {
            String[] pkgs;
            try {
                pkgs = pm.getPackagesForUid(e.getKey());
            } catch (SecurityException ex) {
                Log.d(TAG, "getPackagesForUid(" + e.getKey() + ") denied: " + ex.getMessage());
                continue;
            }
            if (pkgs == null || pkgs.length == 0) continue;
            double share = e.getValue() / pkgs.length;
            for (String pkg : pkgs) batteryMahOut.merge(pkg, share, Double::sum);
        }

        for (Map.Entry<Integer, Long> e : uidToCpuMs.entrySet()) {
            String[] pkgs;
            try {
                pkgs = pm.getPackagesForUid(e.getKey());
            } catch (SecurityException ex) {
                Log.d(TAG, "getPackagesForUid(" + e.getKey() + ") denied: " + ex.getMessage());
                continue;
            }
            if (pkgs == null || pkgs.length == 0) continue;
            long share = e.getValue() / pkgs.length;
            for (String pkg : pkgs) cpuMsOut.merge(pkg, share, Long::sum);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private — procstats RAM parsing
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Parses average PSS RAM from "dumpsys procstats --hours N".
     *
     * Handles both dot and comma decimal separators (locale-dependent output).
     * Samsung One UI with Russian/European locale outputs commas instead of dots.
     *
     * Real TOTAL line format:
     *   TOTAL: 100% (346MB-346MB-346MB/161MB-161MB-161MB/288MB-288MB-288MB over 1)
     *   Structure: PSS(min-avg-max) / USS(min-avg-max) / RSS(min-avg-max)
     *
     * We take PSS avg (second value in the first triplet).
     * If there are multiple TOTAL lines per package (different process states),
     * we keep the maximum avg PSS across states.
     */
    @WorkerThread
    private void collectProcStatsRam(int hours, Map<String, Double> ramMbOut) {
        String cmd = "dumpsys procstats --hours " + hours;
        String output = shellManager.runCommandAndGetOutput(cmd);
        if (output == null || output.isEmpty()) return;

        String currentPkg = null;
        for (String line : output.split("\n")) {
            Matcher pkgMatcher = PROCSTATS_PKG.matcher(line);
            if (pkgMatcher.find()) {
                currentPkg = pkgMatcher.group(1);
                continue;
            }
            if (currentPkg == null) continue;

            // TOTAL line with PSS triplet
            if (!line.contains("TOTAL")) continue;
            Matcher pssMatcher = PROCSTATS_PSS.matcher(line);
            if (pssMatcher.find()) {
                try {
                    // group(2) = avg PSS — normalize locale before parsing
                    double avgPssMb = parseLocaleDouble(pssMatcher.group(2));
                    // Keep max across multiple state rows for the same package
                    ramMbOut.merge(currentPkg, avgPssMb, Math::max);
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    /**
     * Fallback RAM collection via "dumpsys meminfo" when procstats is unavailable.
     *
     * Used on Huawei/Honor where procstats may return empty output, and as a
     * general fallback on any device where procstats yields no package data.
     *
     * Parses lines like:
     *   12345 kB: com.example.app (pid 6789 / activities)
     *
     * PSS is the first numeric value on these lines (in kB → converted to MB).
     * This gives a point-in-time snapshot rather than historical average,
     * but it is far better than showing nothing.
     */
    private static final Pattern MEMINFO_PKG =
            Pattern.compile("^\\s*(\\d+)\\s+kB:\\s+([\\w.:]+)\\s+\\(pid");

    @WorkerThread
    private void collectMeminfoRam(@NonNull Map<String, Double> ramMbOut) {
        String output = shellManager.runCommandAndGetOutput("dumpsys meminfo -a");
        if (output == null || output.isEmpty()) return;

        int parsedCount = 0;
        for (String line : output.split("\n")) {
            Matcher m = MEMINFO_PKG.matcher(line);
            if (!m.find()) continue;
            try {
                double pssKb = parseLocaleDouble(m.group(1));
                String pkg   = m.group(2);
                if (pkg == null || pkg.isEmpty() || pssKb <= 0) continue;
                // Strip process suffix (e.g. com.example.app:service → com.example.app)
                int colon = pkg.indexOf(':');
                if (colon > 0) pkg = pkg.substring(0, colon);
                double pssMb = pssKb / 1024.0;
                ramMbOut.merge(pkg, pssMb, Math::max);
                parsedCount++;
            } catch (Exception ignored) {}
        }
        Log.d(TAG, "meminfo fallback: parsed RAM for " + parsedCount + " entries → "
                + ramMbOut.size() + " packages");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private — /proc/stat CPU baseline (no root required)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Reads the first "cpu" line from /proc/stat and returns cumulative jiffies.
     *
     * /proc/stat first line format:
     *   cpu  user nice system idle iowait irq softirq steal guest guest_nice
     *
     * All values are in USER_HZ units (jiffies). On Android, USER_HZ = 100,
     * so 1 jiffy = 10 ms.  The values are cumulative since boot, summed across
     * ALL cores.
     *
     * Returns long[2]:
     *   [0] totalJiffies  = sum of all fields
     *   [1] activeJiffies = totalJiffies - idle - iowait
     *
     * On failure returns {0, 0} — callers should treat 0 as "no data".
     */
    @WorkerThread
    @NonNull
    private long[] readProcStatJiffies() {
        try {
            String output = shellManager.runCommandAndGetOutput("cat /proc/stat");
            if (output == null || output.isEmpty()) return new long[]{0, 0};

            // First line: "cpu  user nice system idle iowait irq softirq steal guest guest_nice"
            String line = output.split("\n")[0];
            if (!line.startsWith("cpu ")) return new long[]{0, 0};

            String[] parts = line.trim().split("\\s+");
            // parts[0] = "cpu", parts[1..] = jiffie fields
            if (parts.length < 5) return new long[]{0, 0};

            long total  = 0;
            long idle   = 0;
            long iowait = 0;
            for (int i = 1; i < parts.length; i++) {
                long v = Long.parseLong(parts[i]);
                total += v;
                if (i == 4) idle   = v; // field index 4 = idle
                if (i == 5) iowait = v; // field index 5 = iowait
            }
            long active = total - idle - iowait;
            return new long[]{total, active};
        } catch (Exception e) {
            Log.w(TAG, "readProcStatJiffies failed", e);
            return new long[]{0, 0};
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private — period aggregation
    // ──────────────────────────────────────────────────────────────────────────

    @WorkerThread
    @NonNull
    private PeriodStats getStatsForPeriodBlocking(int hours) {
        long now    = System.currentTimeMillis();
        long target = now - (long) hours * 3600_000L;

        ResourceSnapshot current = getDao().getLatestSnapshot();

        if (current == null) {
            return new PeriodStats(Collections.emptyList(), false, 0,
                    context.getString(R.string.stats_no_data_hint_no_snapshot), false);
        }

        // Find the snapshot closest to the requested period start (target).
        ResourceSnapshot previous = getDao().getClosestSnapshotBefore(target);

        if (previous == null) {
            // No snapshot at or before target — try the oldest one we have.
            // Only use it if it's close enough to target (within 10% of the period).
            ResourceSnapshot oldest = getDao().getOldestSnapshot();
            if (oldest != null && oldest.timestamp < current.timestamp) {
                double oldestHoursFromTarget =
                        (oldest.timestamp - target) / 3600_000.0; // positive = newer than target
                if (hours == 2 || oldestHoursFromTarget <= hours * 0.1) {
                    previous = oldest;
                }
            }
        }

        if (previous == null || previous.timestamp >= current.timestamp) {
            return new PeriodStats(Collections.emptyList(), false, 0,
                    context.getString(R.string.stats_no_data_hint_no_history), false);
        }

        double actualHours = (current.timestamp - previous.timestamp) / 3600_000.0;
        if (actualHours < 0.08) {
            return new PeriodStats(Collections.emptyList(), false, 0,
                    context.getString(R.string.stats_no_data_hint_too_close), false);
        }

        // For periods > 2h: data must cover at least 90% of the requested window.
        if (hours > 2 && actualHours < hours * 0.9) {
            return new PeriodStats(Collections.emptyList(), false, 0,
                    context.getString(R.string.stats_no_data_hint_no_history), false);
        }

        // 2h period: flag as partial if actual coverage is less than the full requested window.
        boolean isPartialData = (hours == 2) && (actualHours < hours * 0.9);

        // Load all snapshots in the window, ordered by (packageName, timestamp)
        List<ResourceSnapshot> windowSnaps =
                getDao().getSnapshotsBetween(previous.timestamp, current.timestamp);

        Map<String, double[]> perPkg     = new HashMap<>();
        Map<String, ResourceSnapshot> prevByPkg = new HashMap<>();

        for (ResourceSnapshot snap : windowSnaps) {
            String pkg = snap.packageName;
            if (!prevByPkg.containsKey(pkg)) {
                prevByPkg.put(pkg, snap);
                perPkg.put(pkg, new double[]{ 0, snap.ramMb, 0, snap.ramMb, 1 });
            } else {
                ResourceSnapshot prev = prevByPkg.get(pkg);
                double dBat = snap.batteryMah >= prev.batteryMah
                        ? snap.batteryMah - prev.batteryMah
                        : snap.batteryMah;
                double dCpu = Math.max(0, snap.cpuTimeMs - prev.cpuTimeMs);

                double[] acc = perPkg.get(pkg);
                acc[0] += dBat;
                acc[1] += snap.ramMb;
                acc[2] += dCpu;
                acc[3]  = Math.max(acc[3], snap.ramMb);
                acc[4] += 1;
                prevByPkg.put(pkg, snap);
            }
        }

        android.content.pm.PackageManager pm = context.getPackageManager();
        List<AppResourceStats> result = new ArrayList<>();

        double totalRawPwi = 0;
        for (double[] v : perPkg.values()) totalRawPwi += v[0];

        java.util.TreeMap<Long, Integer> levelByTime = new java.util.TreeMap<>();
        for (ResourceSnapshot s : windowSnaps) {
            if (s.batteryLevelPct > 0) {
                levelByTime.put(s.timestamp, s.batteryLevelPct);
            }
        }

        double drainMah = 0;
        boolean batteryNormalizationValid = false;
        long dTotalJiffies = current.totalCpuJiffies - previous.totalCpuJiffies;

        if (totalRawPwi > 0 && levelByTime.size() >= 2) {
            double capacityMah = getBatteryCapacityMah();
            double totalDrainPct = 0;
            Integer prevLevel = null;
            for (Integer lvl : levelByTime.values()) {
                if (prevLevel != null && lvl < prevLevel) {
                    totalDrainPct += prevLevel - lvl;
                }
                prevLevel = lvl;
            }
            if (totalDrainPct > 0) {
                drainMah = totalDrainPct / 100.0 * capacityMah;
                batteryNormalizationValid = true;
            }
        }

        double cpuDenominatorMs = dTotalJiffies > 0
                ? (dTotalJiffies * 10.0)
                : actualHours * 3600_000.0;

        for (Map.Entry<String, double[]> e : perPkg.entrySet()) {
            String pkg = e.getKey();
            double[] v = e.getValue();
            String name = resolveAppName(pm, pkg);

            double avgRamMb  = v[4] > 0 ? v[1] / v[4] : 0;
            double peakRamMb = v[3];
            double batteryMah = batteryNormalizationValid && totalRawPwi > 0
                    ? (v[0] / totalRawPwi) * drainMah
                    : 0;
            double cpuPct = Math.min(100.0,
                    cpuDenominatorMs > 0 ? v[2] / cpuDenominatorMs * 100.0 : 0);

            result.add(new AppResourceStats(pkg, name, batteryMah, cpuPct, avgRamMb, peakRamMb,
                    pkg.equals(context.getPackageName())));
        }

        result.sort((a, b) -> Double.compare(b.batteryMah, a.batteryMah));
        return new PeriodStats(result, true, actualHours, null, isPartialData);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private — hourly breakdown
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Extended async entry point that accepts all-apps CPU and RAM totals for the period.
     */
    public void getHourlyStatsAsync(String packageName, int hours,
                                    double totalAllAppsCpuPct, double totalAllAppsRamMb,
                                    @NonNull HourlyCallback callback) {
        executor.execute(() -> {
            HourlyResult result = getHourlyStatsBlocking(
                    packageName, hours, totalAllAppsCpuPct, totalAllAppsRamMb);
            handler.post(() -> callback.onResult(result));
        });
    }

    @WorkerThread
    @NonNull
    private HourlyResult getHourlyStatsBlocking(String packageName, int hours,
                                                 double totalAllAppsCpuPct,
                                                 double totalAllAppsRamMb) {
        long now = System.currentTimeMillis();

        java.util.Calendar endCal = java.util.Calendar.getInstance();
        endCal.setTimeInMillis(now);
        endCal.set(java.util.Calendar.MINUTE, 0);
        endCal.set(java.util.Calendar.SECOND, 0);
        endCal.set(java.util.Calendar.MILLISECOND, 0);
        long endAligned   = endCal.getTimeInMillis();
        long startAligned = endAligned - (long) hours * 3600_000L;

        List<ResourceSnapshot> snaps =
                getDao().getSnapshotsForPackageBetween(packageName, startAligned, endAligned);

        ResourceSnapshot oldestForPkg = getDao().getOldestSnapshotForPackage(packageName);
        long oldestPkgTime = oldestForPkg != null ? oldestForPkg.timestamp : endAligned;
        double pkgAgeHours = (endAligned - oldestPkgTime) / 3600_000.0;
        boolean isPartialData = (hours == 2) && (pkgAgeHours < hours * 0.9);

        if (snaps.size() < 2) return HourlyResult.empty(isPartialData);
        if (hours > 2 && pkgAgeHours < hours * 0.9) return HourlyResult.empty(false);

        double periodTotalDrainPct = 0, periodTotalBatRaw = 0, periodTotalCpuMs = 0;
        double periodTotalRawPwiBatch = 0;
        int    periodBatchCount = 0;
        {
            Integer prevLevel = null;
            for (int i = 0; i < snaps.size(); i++) {
                ResourceSnapshot s = snaps.get(i);
                if (s.batteryLevelPct > 0) {
                    if (prevLevel != null && s.batteryLevelPct < prevLevel)
                        periodTotalDrainPct += prevLevel - s.batteryLevelPct;
                    prevLevel = s.batteryLevelPct;
                }
                if (i > 0) {
                    ResourceSnapshot p = snaps.get(i - 1);
                    double dBat = s.batteryMah >= p.batteryMah
                            ? s.batteryMah - p.batteryMah : s.batteryMah;
                    periodTotalBatRaw += dBat;
                    periodTotalCpuMs  += Math.max(0, s.cpuTimeMs - p.cpuTimeMs);
                }
                if (s.totalRawPwiBatch > 0) { periodTotalRawPwiBatch += s.totalRawPwiBatch; periodBatchCount++; }
            }
        }
        double periodDrainMah  = periodTotalDrainPct / 100.0 * getBatteryCapacityMah();
        double avgPeriodBatch  = periodBatchCount > 0 ? periodTotalRawPwiBatch / periodBatchCount : 0;
        double periodAppMah    = (periodTotalDrainPct > 0 && avgPeriodBatch > 0 && periodTotalBatRaw > 0)
                ? (periodTotalBatRaw / avgPeriodBatch) * periodDrainMah : 0;
        boolean batValid       = periodAppMah > 0 && periodTotalCpuMs > 0;

        int numSlots = hours * 2;
        double avgAllCpuPerSlot = totalAllAppsCpuPct > 0 ? totalAllAppsCpuPct / numSlots : 0;
        double avgAllRamPerSlot = totalAllAppsRamMb  > 0 ? totalAllAppsRamMb  / numSlots : 0;

        final long SLOT_MS = 30 * 60 * 1000L;
        List<ActivitySlice> slices = new ArrayList<>();

        List<Double> slotBatList = new ArrayList<>();
        List<Double> slotCpuList = new ArrayList<>();
        List<Double> slotRamList = new ArrayList<>();

        for (int s = 0; s < numSlots; s++) {
            long slotStart = startAligned + (long) s * SLOT_MS;
            long slotEnd   = slotStart + SLOT_MS;

            double slotCpuMs = 0, slotRamSum = 0;
            int    slotRamCount = 0;
            long   slotJiffies = 0, slotFirstTs = -1, slotLastTs = -1;

            for (int i = 1; i < snaps.size(); i++) {
                ResourceSnapshot prev = snaps.get(i - 1);
                ResourceSnapshot curr = snaps.get(i);
                if (curr.timestamp <= slotStart || curr.timestamp > slotEnd) continue;

                slotCpuMs    += Math.max(0, curr.cpuTimeMs - prev.cpuTimeMs);
                slotJiffies  += Math.max(0, curr.totalCpuJiffies - prev.totalCpuJiffies);
                slotRamSum   += curr.ramMb;
                slotRamCount++;
                if (slotFirstTs < 0) slotFirstTs = prev.timestamp;
                slotLastTs = curr.timestamp;
            }

            double slotBat = 0;
            if (batValid && slotCpuMs > 0)
                slotBat = (slotCpuMs / periodTotalCpuMs) * periodAppMah;

            double slotCpuDenMs = slotJiffies > 0 ? slotJiffies * 10.0
                    : (slotFirstTs >= 0 ? (double)(slotLastTs - slotFirstTs) : 0);
            double slotCpu = (slotRamCount > 0 && slotCpuDenMs > 0)
                    ? Math.min(100.0, slotCpuMs / slotCpuDenMs * 100.0) : 0;

            double slotRam = slotRamCount > 0 ? slotRamSum / slotRamCount : 0;

            ActivityLevel level;
            if (slotRamCount == 0) {
                level = ActivityLevel.NONE;
            } else {
                double cpuRatio = avgAllCpuPerSlot > 0 ? slotCpu / avgAllCpuPerSlot : 0;
                double ramRatio = avgAllRamPerSlot > 0 ? slotRam / avgAllRamPerSlot : 0;
                double score = cpuRatio * 0.6 + ramRatio * 0.4;
                if (score <= 0.0)       level = ActivityLevel.NONE;
                else if (score < 0.5)   level = ActivityLevel.LOW;
                else if (score < 1.5)   level = ActivityLevel.MEDIUM;
                else                    level = ActivityLevel.HIGH;
            }

            slices.add(new ActivitySlice(slotStart, level, slotCpu, slotRam));
            if (slotRamCount > 0) {
                slotBatList.add(slotBat);
                slotCpuList.add(slotCpu);
                slotRamList.add(slotRam);
            }
        }

        slices.add(new ActivitySlice(endAligned, ActivityLevel.NONE, 0, 0));

        HourlyPeriodStats periodStats = null;
        if (!slotBatList.isEmpty()) {
            double minBat = Double.MAX_VALUE, maxBat = 0, sumBat = 0;
            double minCpu = Double.MAX_VALUE, maxCpu = 0, sumCpu = 0;
            double minRam = Double.MAX_VALUE, maxRam = 0, sumRam = 0;
            int n = slotBatList.size();
            for (int i = 0; i < n; i++) {
                double bat = slotBatList.get(i), cpu = slotCpuList.get(i), ram = slotRamList.get(i);
                minBat = Math.min(minBat, bat); maxBat = Math.max(maxBat, bat); sumBat += bat;
                minCpu = Math.min(minCpu, cpu); maxCpu = Math.max(maxCpu, cpu); sumCpu += cpu;
                minRam = Math.min(minRam, ram); maxRam = Math.max(maxRam, ram); sumRam += ram;
            }
            periodStats = new HourlyPeriodStats(
                    minBat, sumBat / n, maxBat,
                    minCpu, sumCpu / n, maxCpu,
                    minRam, sumRam / n, maxRam);
        }

        return new HourlyResult(slices, periodStats, isPartialData);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Utility helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Parses a double from a string that may use either dot or comma as decimal separator.
     *
     * This is necessary because dumpsys output is locale-dependent:
     * - English locale → "2.8MB"
     * - Russian/European locale (Samsung One UI, etc.) → "2,8MB"
     *
     * @param s the string to parse (e.g. "2,8" or "2.8")
     * @return parsed double value, or 0.0 on failure
     */
    private static double parseLocaleDouble(@Nullable String s) {
        if (s == null || s.isEmpty()) return 0.0;
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Parses a long from a string that may contain a decimal separator (locale-dependent).
     * Truncates fractional part — safe for millisecond values from dumpsys.
     *
     * @param s the string to parse (e.g. "12345" or "12345,0")
     * @return parsed long value (integer part only), or 0 on failure
     */
    private static long parseLocaleDoubleToLong(@Nullable String s) {
        if (s == null || s.isEmpty()) return 0L;
        try {
            // Normalize and truncate to integer part
            String normalized = s.trim().replace(',', '.');
            return (long) Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static double getOrZero(Map<String, Double> map, String key) {
        Double v = map.get(key);
        return v != null ? v : 0.0;
    }

    private static String resolveAppName(android.content.pm.PackageManager pm, String pkg) {
        try {
            return pm.getApplicationLabel(
                    pm.getApplicationInfo(pkg, 0)).toString();
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }
}
