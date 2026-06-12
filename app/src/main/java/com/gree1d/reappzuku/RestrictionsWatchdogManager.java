package com.gree1d.reappzuku;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import java.util.Set;

public class RestrictionsWatchdogManager {

    private static final String TAG = "RestrictionsWatchdog";
    private static final long WATCHDOG_INTERVAL_MS = 35 * 60 * 1000L; // 35 minutes

    private final Context context;
    private final Handler handler;
    private final BackgroundAppManager appManager;
    private final ShellManager shellManager;
    private final RestrictionsScheduler scheduler;

    private boolean running = false;

    private final Runnable watchdogRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            runCheck();
            handler.postDelayed(this, WATCHDOG_INTERVAL_MS);
        }
    };

    public RestrictionsWatchdogManager(Context context, Handler handler,
            BackgroundAppManager appManager, ShellManager shellManager,
            RestrictionsScheduler scheduler) {
        this.context      = context;
        this.handler      = handler;
        this.appManager   = appManager;
        this.shellManager = shellManager;
        this.scheduler    = scheduler;
    }

    public void startIfNeeded() {
        if (running) return;
        if (!appManager.supportsBackgroundRestriction()) return;
        if (!shellManager.hasAnyShellPermission()) return;
        if (appManager.getBackgroundRestrictedApps().isEmpty()) {
            Log.d(TAG, "No restricted apps, watchdog not started");
            return;
        }
        running = true;
        handler.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL_MS);
        Log.d(TAG, "Watchdog started, interval=" + (WATCHDOG_INTERVAL_MS / 60000) + " min");
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(watchdogRunnable);
        Log.d(TAG, "Watchdog stopped");
    }


    private void runCheck() {
        if (!appManager.supportsBackgroundRestriction()
                || !shellManager.hasAnyShellPermission()) {
            return;
        }

        Set<String> desired = appManager.sanitizeBackgroundRestrictionTargets(
                appManager.getBackgroundRestrictedApps());

        if (desired.isEmpty()) {
            stop();
            Log.d(TAG, "Watchdog stopped — no more restricted apps");
            return;
        }

        appManager.checkAndRepairRestrictions(desired, scheduler);
        checkAndRepairBuckets(desired);
    }

    private boolean isMediumLikeManual(String packageName) {
        int mask = appManager.getManualOpsMask(packageName);
        int mediumMask = 0;
        for (int i = 0; i < BackgroundAppManager.ALL_OPS.length; i++) {
            for (String medOp : BackgroundAppManager.MEDIUM_OPS) {
                if (BackgroundAppManager.ALL_OPS[i].equals(medOp)) {
                    mediumMask |= (1 << i);
                    break;
                }
            }
        }
        int overlap = Integer.bitCount(mask & mediumMask);
        return overlap >= 3;
    }

    private boolean isAppForeground(String packageName) {
        android.app.ActivityManager am =
                (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        for (android.app.ActivityManager.RunningAppProcessInfo info : am.getRunningAppProcesses()) {
            if (info.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                    && java.util.Arrays.asList(info.pkgList).contains(packageName)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAppForegroundService(String packageName) {
        android.app.ActivityManager am =
                (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        for (android.app.ActivityManager.RunningAppProcessInfo info : am.getRunningAppProcesses()) {
            if (info.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE
                    && java.util.Arrays.asList(info.pkgList).contains(packageName)) {
                return true;
            }
        }
        return false;
    }

    private void checkAndRepairBuckets(Set<String> desired) {
        Set<String> hardSet   = appManager.getHardRestrictedApps();
        Set<String> mediumSet = appManager.getMediumRestrictedApps();
        Set<String> manualSet = appManager.getManualRestrictedApps();

        for (String pkg : desired) {
            if (scheduler != null
                    && scheduler.isProtected(pkg, RestrictionsScheduler.PROTECT_BG_RESTRICTIONS)) {
                continue;
            }

            int required;
            if (hardSet.contains(pkg)) {
                if (isAppForeground(pkg)) {
                    Log.d(TAG, "watchdog bucket SKIP (foreground): " + pkg);
                    continue;
                }
                required = 45;
            } else if (mediumSet.contains(pkg)) {
                if (isAppForeground(pkg) || isAppForegroundService(pkg)) {
                    Log.d(TAG, "watchdog bucket SKIP (foreground/fgs): " + pkg);
                    continue;
                }
                required = 40;
            } else if (manualSet.contains(pkg)) {
                if (isAppForeground(pkg)) {
                    Log.d(TAG, "watchdog bucket SKIP (foreground): " + pkg);
                    continue;
                }
                required = appManager.getManualBucket(pkg);
                if (required == 40 && isMediumLikeManual(pkg) && isAppForegroundService(pkg)) {
                    Log.d(TAG, "watchdog bucket SKIP (fgs, medium-like manual): " + pkg);
                    continue;
                }
            } else {
                continue;
            }
            if (required == 0) continue;

            String out = shellManager.runShellCommandAndGetFullOutput(
                    "am get-standby-bucket " + pkg);
            if (out == null || out.trim().isEmpty()) continue;

            int current;
            try {
                current = Integer.parseInt(out.trim());
            } catch (NumberFormatException e) {
                Log.w(TAG, "bucket parse error: " + pkg + " out=" + out.trim());
                continue;
            }

            if (current == required) continue;

            Log.w(TAG, "watchdog bucket drift: " + pkg
                    + " current=" + current + " required=" + required);
            boolean ok = shellManager.runShellCommandForResult(
                    "am set-standby-bucket " + pkg + " " + required).succeeded();
            BackgroundRestrictionLog.log(context, pkg, "watchdog-bucket",
                    ok ? "ok" : "failed",
                    "was=" + current + " set=" + required);
        }
    }
}
