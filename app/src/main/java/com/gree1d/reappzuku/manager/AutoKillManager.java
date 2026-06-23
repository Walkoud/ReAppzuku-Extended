package com.gree1d.reappzuku.manager;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.os.Handler;
import com.gree1d.reappzuku.core.AppDebugManager;
import com.gree1d.reappzuku.core.AppDebugManager.Category;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import com.gree1d.reappzuku.core.ShellManager;
import com.gree1d.reappzuku.utils.AppModel;
import com.gree1d.reappzuku.R;
import com.gree1d.reappzuku.service.ShappkyService;
import com.gree1d.reappzuku.core.ProtectedApps;

import static com.gree1d.reappzuku.core.PreferenceKeys.*;
import static com.gree1d.reappzuku.core.AppConstants.*;

public class AutoKillManager {
    private static final String TAG = "AutoKillManager";
  
    private static final int STATS_LIMIT = 15_000;

    private final Context context;
    private final Handler handler;
    private final ExecutorService executor;
    private final ShellManager shellManager;
    private final SharedPreferences sharedpreferences;
    private final List<AppModel> currentAppsList;
    private RestrictionsScheduler scheduler;

    public AutoKillManager(Context context, Handler handler, ExecutorService executor,
            ShellManager shellManager, List<AppModel> currentAppsList) {
        this.context = context;
        this.handler = handler;
        this.executor = executor;
        this.shellManager = shellManager;
        this.currentAppsList = currentAppsList;
        this.sharedpreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public void setScheduler(RestrictionsScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void performAutoKill(Runnable onComplete, String source) {
        performAutoKill(onComplete, null, source);
    }

    public void performAutoKill(Runnable onComplete, Set<String> extraWhitelist, String source) {
        performAutoKillWithResult(onComplete, extraWhitelist, null, source);
    }

    public void performAutoKillWithResult(Runnable onComplete, Set<String> extraWhitelist,
            java.util.function.BiConsumer<Integer, Long> onResult, String source) {
        executor.execute(() -> {
            if (!shellManager.resolveAnyShellPermission()) {
                if (onComplete != null)
                    handler.post(onComplete);
                return;
            }

            Set<String> hiddenApps = getHiddenApps();
            Set<String> whitelistedApps = getWhitelistedApps();
            Set<String> blacklistedApps = getBlacklistedApps();
            int killMode = getKillMode();

            AppDebugManager.d(Category.AUTO_KILL_BASE, "AutoKillManager: === performAutoKill start ===");
            AppDebugManager.d(Category.AUTO_KILL_BASE, "AutoKillManager: killMode=" + (killMode == 1 ? "BLACKLIST" : "WHITELIST"));
            AppDebugManager.d(Category.AUTO_KILL_BASE, "AutoKillManager: whitelistedApps=" + whitelistedApps);
            AppDebugManager.d(Category.AUTO_KILL_BASE, "AutoKillManager: blacklistedApps=" + blacklistedApps);
            AppDebugManager.d(Category.AUTO_KILL_BASE, "AutoKillManager: hiddenApps=" + hiddenApps);

            String dumpOutput = shellManager.runShellCommandAndGetFullOutput("dumpsys activity activities");
            AppDebugManager.d(Category.AUTO_KILL_BASE, "AutoKillManager: dumpsys output length: " + (dumpOutput == null ? "null" : dumpOutput.length()));
            if (dumpOutput == null) {
                AppDebugManager.w(Category.AUTO_KILL_BASE, "AutoKillManager: dumpsys returned null — aborting kill");
                if (onComplete != null)
                    handler.post(onComplete);
                return;
            }

            String psOutput = shellManager.runShellCommandAndGetFullOutput(
                    "ps -A -o rss,name | grep '\\.'");
            AppDebugManager.d(Category.AUTO_KILL_BASE, "AutoKillManager: ps output length: " + (psOutput == null ? "null" : psOutput.trim().length()));
            if (psOutput == null || psOutput.trim().isEmpty()) {
                AppDebugManager.w(Category.AUTO_KILL_BASE, "AutoKillManager: ps returned null/empty — aborting kill");
                if (onComplete != null)
                    handler.post(onComplete);
                return;
            }

            Set<String> runningPackages = new HashSet<>();
            Map<String, Long> psRssMap = new HashMap<>();
            PackageManager pm = context.getPackageManager();
            try (BufferedReader reader = new BufferedReader(new StringReader(psOutput))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.trim().split("\\s+", 2);
                    if (parts.length < 2) continue;
                    String rssStr = parts[0].trim();
                    String fullName = parts[1].trim();
                    String packageName = fullName.contains(":")
                            ? fullName.substring(0, fullName.indexOf(":"))
                            : fullName;
                    if (packageName.isEmpty() || !packageName.contains(".")) continue;
                    try {
                        pm.getApplicationInfo(packageName, 0);
                        runningPackages.add(packageName);
                        try {
                            long rssKb = Long.parseLong(rssStr);
                            psRssMap.put(packageName, rssKb);
                        } catch (NumberFormatException ignored) {
                        }
                    } catch (PackageManager.NameNotFoundException ignored) {
                    }
                }
            } catch (IOException ignored) {
            }

            killOrphanShellProcesses(null);

            boolean presetActive = new PresetManager(context).getActivePresetNumber() != 0;

            List<String> toKill = runningPackages.stream()
                    .filter(pkg -> {
                        try {
                            if (hiddenApps.contains(pkg)) {
                                AppDebugManager.d(Category.AUTO_KILL_BASE, "AutoKillManager: SKIP (hidden): " + pkg);
                                return false;
                            }
                            if (ProtectedApps.isProtected(context, pkg)) {
                                AppDebugManager.d(Category.AUTO_KILL_BASE, "AutoKillManager: SKIP (protected): " + pkg);
                                return false;
                            }
                            if (extraWhitelist != null && extraWhitelist.contains(pkg)) {
                                AppDebugManager.d(Category.AUTO_KILL_BASE, "AutoKillManager: SKIP (extra whitelist): " + pkg);
                                return false;
                            }
                            if (!presetActive && scheduler != null && scheduler.isProtected(pkg, RestrictionsScheduler.PROTECT_AUTO_KILL)) {
                                AppDebugManager.d(Category.AUTO_KILL_BASE, "AutoKillManager: SKIP (temp protected): " + pkg);
                                return false;
                            }
                            if (containsPackage(dumpOutput, pkg)) {
                                AppDebugManager.d(Category.AUTO_KILL_BASE, "AutoKillManager: SKIP (foreground): " + pkg);
                                return false;
                            }
                            if (killMode == 1) {
                                boolean inBlacklist = blacklistedApps.contains(pkg);
                                AppDebugManager.d(Category.AUTO_KILL_BASE, (inBlacklist ? "AutoKillManager: KILL (blacklist): " : "AutoKillManager: SKIP (not in blacklist): ") + pkg);
                                return inBlacklist;
                            } else {
                                if (whitelistedApps.contains(pkg)) {
                                    AppDebugManager.d(Category.AUTO_KILL_BASE, "AutoKillManager: SKIP (whitelisted): " + pkg);
                                    return false;
                                }
                                ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0);
                                boolean persistent = (appInfo.flags & ApplicationInfo.FLAG_PERSISTENT) != 0;
                                AppDebugManager.d(Category.AUTO_KILL_BASE, (persistent ? "AutoKillManager: SKIP (persistent): " : "AutoKillManager: KILL (whitelist mode): ") + pkg);
                                return !persistent;
                            }
                        } catch (PackageManager.NameNotFoundException e) {
                            AppDebugManager.d(Category.AUTO_KILL_BASE, "AutoKillManager: SKIP (not found): " + pkg);
                            return false;
                        }
                    })
                    .collect(Collectors.toList());

            AppDebugManager.d(Category.AUTO_KILL_BASE, "AutoKillManager: toKill list (" + toKill.size() + "): " + toKill);

            Map<String, Long> pendingRss = loadPendingRss();
            Map<String, Long> confirmedFreedKb = new HashMap<>();
            for (Map.Entry<String, Long> entry : pendingRss.entrySet()) {
                String pkg = entry.getKey();
                if (!psRssMap.containsKey(pkg)) {
                    confirmedFreedKb.put(pkg, entry.getValue());
                    AppDebugManager.d(Category.AUTO_KILL_BASE, "AutoKillManager: Confirmed freed RAM for " + pkg + ": " + entry.getValue() + " KB");
                } else {
                    AppDebugManager.d(Category.AUTO_KILL_BASE, "AutoKillManager: Skipped RAM (relaunched): " + pkg);
                }
            }
            if (!confirmedFreedKb.isEmpty()) {
                recordConfirmedRam(confirmedFreedKb);
            }

            if (!toKill.isEmpty()) {
                recordSuccessfulKills(toKill, null, source);

                Map<String, Long> newPendingRss = new HashMap<>();
                for (String pkg : toKill) {
                    long rssKb = psRssMap.getOrDefault(pkg, 0L);
                    if (rssKb > 0) {
                        newPendingRss.put(pkg, rssKb);
                        AppDebugManager.d(Category.AUTO_KILL_BASE, "AutoKillManager: Pending RSS for " + pkg + ": " + rssKb + " KB");
                    }
                }
                savePendingRss(newPendingRss);

                String killCommand = toKill.stream()
                        .map(this::buildKillCommand)
                        .collect(Collectors.joining("; "));

                shellManager.runShellCommandAndGetFullOutput(killCommand);

                sendKillNotification(toKill.size());

                long totalRssKb = 0;
                for (String pkg : toKill) {
                    totalRssKb += psRssMap.getOrDefault(pkg, 0L);
                }
                final long finalTotalRssKb = totalRssKb;
                final int finalKillCount = toKill.size();
                if (onResult != null) {
                    handler.post(() -> onResult.accept(finalKillCount, finalTotalRssKb));
                }

                try {
                    Thread.sleep(RELAUNCH_CHECK_DELAY_MS);
                } catch (InterruptedException ignored) {
                }
                com.gree1d.reappzuku.db.AppDatabase db = com.gree1d.reappzuku.db.AppDatabase.getInstance(context);
                checkRelaunches(toKill, db);
            } else {
                savePendingRss(new HashMap<>());
                if (onResult != null) {
                    handler.post(() -> onResult.accept(0, 0L));
                }
            }

            if (onComplete != null)
                handler.post(onComplete);
        });
    }

    private void checkRelaunches(List<String> recentlyKilled, com.gree1d.reappzuku.db.AppDatabase db) {
        String psOutput = shellManager.runShellCommandAndGetFullOutput("ps -A -o name | grep '\\.'");
        if (psOutput == null)
            return;

        long now = System.currentTimeMillis();
        try (BufferedReader reader = new BufferedReader(new StringReader(psOutput))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String pkg = line.trim();
                if (pkg.contains(":")) {
                    pkg = pkg.split(":")[0];
                }
                if (recentlyKilled.contains(pkg)) {
                    db.appStatsDao().incrementRelaunch(pkg, now);
                }
            }
        } catch (IOException ignored) {
        }
    }

    public void killPackages(List<String> packageNames, Runnable onComplete) {
        if (!shellManager.hasAnyShellPermission()) {
            shellManager.checkShellPermissions();
            if (onComplete != null) {
                handler.post(onComplete);
            }
            return;
        }

        if (packageNames == null || packageNames.isEmpty()) {
            if (onComplete != null) {
                handler.post(onComplete);
            }
            return;
        }
        long totalKb = 0;
        Map<String, Long> recoveredKbByPackage = new HashMap<>();
        for (String pkg : packageNames) {
            long appRamKb = 0;
            for (AppModel app : currentAppsList) {
                if (app.getPackageName().equals(pkg)) {
                    appRamKb = app.getAppRamBytes();
                    break;
                }
            }
            if (appRamKb > 0) {
                recoveredKbByPackage.put(pkg, appRamKb);
                totalKb += appRamKb;
            }
        }

        String command = packageNames.stream()
                .map(this::buildKillCommand)
                .collect(Collectors.joining("; "));

        final long finalTotalKb = totalKb;
        final List<String> packagesToLog = new ArrayList<>(packageNames);
        final Map<String, Long> recoveredToLog = new HashMap<>(recoveredKbByPackage);
        shellManager.runShellCommand(command, () -> {
            executor.execute(() -> {
                recordSuccessfulKills(packagesToLog, recoveredToLog, "Manual Kill");
                killOrphanShellProcesses(new HashSet<>(packagesToLog));
            });
            Toast.makeText(context, context.getString(R.string.toast_free_up, formatMemorySize(finalTotalKb)), Toast.LENGTH_LONG).show();
            if (onComplete != null) {
                onComplete.run();
            }
        }, () -> {
            Toast.makeText(context, context.getString(R.string.bg_manager_kill_failed), Toast.LENGTH_SHORT).show();
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    public void killApp(String packageName, Runnable onComplete) {
        if (!shellManager.hasAnyShellPermission()) {
            shellManager.checkShellPermissions();
            if (onComplete != null) {
                handler.post(onComplete);
            }
            return;
        }
        if (packageName == null || packageName.isEmpty()) {
            if (onComplete != null) {
                handler.post(onComplete);
            }
            return;
        }
        long appRamBytes = 0;
        for (AppModel app : currentAppsList) {
            if (app.getPackageName().equals(packageName)) {
                appRamBytes = app.getAppRamBytes();
                break;
            }
        }
        final String packageToKill = packageName;
        final Map<String, Long> recoveredKbByPackage = new HashMap<>();
        if (appRamBytes > 0) {
            recoveredKbByPackage.put(packageToKill, appRamBytes);
        }
        final long finalAppRamBytes = appRamBytes;
        shellManager.runShellCommand(buildKillCommand(packageToKill), () -> {
            executor.execute(() -> {
                recordSuccessfulKills(Collections.singletonList(packageToKill), recoveredKbByPackage, "Manual Kill");
                killOrphanShellProcesses(Collections.singleton(packageToKill));
            });
            if (finalAppRamBytes > 0) {
                Toast.makeText(context, context.getString(R.string.toast_free_up, formatMemorySize(finalAppRamBytes)), Toast.LENGTH_LONG).show();
            }
            if (onComplete != null) {
                onComplete.run();
            }
        }, () -> {
            Toast.makeText(context, context.getString(R.string.toast_failed_stop_app, packageName), Toast.LENGTH_SHORT).show();
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    public void uninstallPackage(String packageName, Runnable onComplete) {
        if (!shellManager.hasAnyShellPermission()) {
            shellManager.checkShellPermissions();
            if (onComplete != null) {
                handler.post(onComplete);
            }
            return;
        }
        if (packageName == null || packageName.isEmpty()) {
            if (onComplete != null) {
                handler.post(onComplete);
            }
            return;
        }

        String command = "pm uninstall " + packageName;
        shellManager.runShellCommand(command, () -> {
            Toast.makeText(context, context.getString(R.string.toast_uninstall_sent, packageName), Toast.LENGTH_SHORT).show();
            if (onComplete != null) {
                onComplete.run();
            }
        }, () -> {
            Toast.makeText(context, context.getString(R.string.toast_failed_uninstall, packageName), Toast.LENGTH_SHORT).show();
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    private String buildKillCommand(String packageName) {
        String cmd = (getAutoKillType() == 1 ? "am kill " : "am force-stop ") + packageName;
        AppDebugManager.d(Category.AUTO_KILL_BASE, "AutoKillManager: buildKillCommand: " + cmd + " (type=" + getAutoKillType() + ")");
        return cmd;
    }

    private void sendKillNotification(int count) {
        ShappkyService.updateNotification(context,
                context.getString(R.string.bg_manager_auto_kill_active),
                context.getString(R.string.bg_manager_stopped_apps, count));
    }

    public void recordQuickTileKill(String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        recordSuccessfulKills(Collections.singletonList(packageName), null, "Quick Tile");
    }

    private void recordSuccessfulKills(List<String> packageNames,
            Map<String, Long> recoveredKbByPackage, String source) {
        if (packageNames == null || packageNames.isEmpty()) {
            return;
        }

        com.gree1d.reappzuku.db.AppStatsDao appStatsDao =
                com.gree1d.reappzuku.db.AppDatabase.getInstance(context).appStatsDao();
        PackageManager packageManager = context.getPackageManager();
        long now = System.currentTimeMillis();

        Set<String> uniquePackages = new HashSet<>(packageNames);
        int newEntries = uniquePackages.size();

        int currentCount = appStatsDao.getCount();
        int excess = (currentCount + newEntries) - STATS_LIMIT;
        if (excess > 0) {
            appStatsDao.deleteOldestStats(excess);
            AppDebugManager.d(Category.AUTO_KILL_BASE,
                    "AutoKillManager: DB limit reached, deleted " + excess + " oldest records");
        }

        for (String packageName : uniquePackages) {
            if (packageName == null || packageName.isEmpty()) {
                continue;
            }

            String appName = resolveInstalledAppName(packageManager, packageName);

            if (appName != null && !appName.trim().isEmpty()) {
                appStatsDao.updateAppName(packageName, appName);
            }

            com.gree1d.reappzuku.db.AppStats stats =
                    new com.gree1d.reappzuku.db.AppStats(packageName);
            stats.appName = appName;
            stats.lastKillTime = now;
            stats.lastKillSource = source;
            stats.relaunchCount = 0;
            stats.lastRelaunchTime = 0;

            long recoveredKb = recoveredKbByPackage != null
                    ? recoveredKbByPackage.getOrDefault(packageName, 0L)
                    : 0L;
            stats.totalRecoveredKb = recoveredKb;

            appStatsDao.insert(stats);

            AppDebugManager.d(Category.AUTO_KILL_BASE,
                    "AutoKillManager: Inserted kill record for " + packageName
                    + " recoveredKb=" + recoveredKb + " source=" + source);
        }
    }

    private void recordConfirmedRam(Map<String, Long> confirmedFreedKb) {
        if (confirmedFreedKb == null || confirmedFreedKb.isEmpty()) return;
        com.gree1d.reappzuku.db.AppStatsDao appStatsDao =
                com.gree1d.reappzuku.db.AppDatabase.getInstance(context).appStatsDao();
        for (Map.Entry<String, Long> entry : confirmedFreedKb.entrySet()) {
            appStatsDao.addRecoveredKb(entry.getKey(), entry.getValue());
            AppDebugManager.d(Category.AUTO_KILL_BASE,
                    "AutoKillManager: Confirmed RAM added for " + entry.getKey()
                    + ": " + entry.getValue() + " KB");
        }
    }

    private String resolveInstalledAppName(PackageManager packageManager, String packageName) {
        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            CharSequence label = packageManager.getApplicationLabel(appInfo);
            if (label != null) {
                return label.toString();
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return null;
    }

    private void killOrphanShellProcesses(Set<String> targetPackages) {
        String psOutput = shellManager.runShellCommandAndGetFullOutput(
                "ps -A -o user,pid,ppid,name | grep '\\.'");
        if (psOutput == null || psOutput.trim().isEmpty()) return;

        Map<String, String> appProcessPids = new HashMap<>();
        Map<String, List<String>> orphanCandidatePids = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new StringReader(psOutput))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 4) continue;
                String user = parts[0].trim();
                String pid = parts[1].trim();
                String ppid = parts[2].trim();
                String fullName = parts[3].trim();
                String packageName = fullName.contains(":")
                        ? fullName.substring(0, fullName.indexOf(":"))
                        : fullName;
                if (packageName.isEmpty() || !packageName.contains(".")) continue;
                if (targetPackages != null && !targetPackages.contains(packageName)) continue;
                if (user.equals("shell") && ppid.equals("1") && fullName.contains(":")) {
                    orphanCandidatePids.computeIfAbsent(packageName, k -> new ArrayList<>()).add(pid);
                } else if (!user.equals("shell")) {
                    appProcessPids.put(packageName, pid);
                }
            }
        } catch (IOException ignored) {
        }

        List<String> toKill = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : orphanCandidatePids.entrySet()) {
            if (!appProcessPids.containsKey(entry.getKey())) {
                toKill.addAll(entry.getValue());
                AppDebugManager.d(Category.AUTO_KILL_BASE, "AutoKillManager: Orphan shell PIDs for " + entry.getKey() + ": " + entry.getValue());
            }
        }
        if (!toKill.isEmpty()) {
            shellManager.runShellCommandAndGetFullOutput("kill -9 " + String.join(" ", toKill));
            AppDebugManager.d(Category.AUTO_KILL_BASE, "AutoKillManager: Killed orphan shell PIDs: " + toKill);
        }
    }

    private static boolean containsPackage(String output, String packageName) {
        if (output == null || packageName == null) return false;

        int idx = output.indexOf(packageName);
        if (idx == -1) {
            AppDebugManager.d(Category.AUTO_KILL_BASE, "AutoKillManager: containsPackage: NOT FOUND in dumpsys: " + packageName);
            return false;
        }

        while (idx != -1) {
            int end = idx + packageName.length();
            boolean endOk = end >= output.length()
                    || !Character.isLetterOrDigit(output.charAt(end)) && output.charAt(end) != '.';
            boolean startOk = idx == 0
                    || !Character.isLetterOrDigit(output.charAt(idx - 1)) && output.charAt(idx - 1) != '.';
            if (startOk && endOk) {
                int from = Math.max(0, idx - 40);
                int to = Math.min(output.length(), end + 40);
                AppDebugManager.d(Category.AUTO_KILL_BASE, "AutoKillManager: containsPackage: FOUND " + packageName
                        + " | context: [" + output.substring(from, to).replace("\n", "↵") + "]");
                return true;
            }
            idx = output.indexOf(packageName, idx + 1);
        }

        AppDebugManager.d(Category.AUTO_KILL_BASE, "AutoKillManager: containsPackage: found as substring but boundaries failed: " + packageName);
        return false;
    }

    private String formatMemorySize(long kb) {
        if (kb < 1024)
            return kb + " KB";
        else if (kb < 1024 * 1024)
            return String.format(java.util.Locale.US, "%.2f MB", kb / 1024f);
        else
            return String.format(java.util.Locale.US, "%.2f GB", kb / (1024f * 1024f));
    }

    private Map<String, Long> loadPendingRss() {
        Map<String, Long> result = new HashMap<>();
        String json = sharedpreferences.getString(KEY_AUTO_KILL_PENDING_RSS, null);
        if (json == null) return result;
        try {
            JSONObject obj = new JSONObject(json);
            java.util.Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String pkg = keys.next();
                result.put(pkg, obj.getLong(pkg));
            }
        } catch (JSONException ignored) {
        }
        return result;
    }

    private void savePendingRss(Map<String, Long> pendingRss) {
        JSONObject obj = new JSONObject();
        try {
            for (Map.Entry<String, Long> entry : pendingRss.entrySet()) {
                obj.put(entry.getKey(), entry.getValue());
            }
        } catch (JSONException ignored) {
        }
        sharedpreferences.edit().putString(KEY_AUTO_KILL_PENDING_RSS, obj.toString()).apply();
    }

    public void clearPendingRss() {
        sharedpreferences.edit().remove(KEY_AUTO_KILL_PENDING_RSS).apply();
    }

    public Set<String> getHiddenApps() {
        return new HashSet<>(sharedpreferences.getStringSet(KEY_HIDDEN_APPS, new HashSet<>()));
    }

    public void saveHiddenApps(Set<String> hiddenApps) {
        sharedpreferences.edit().putStringSet(KEY_HIDDEN_APPS, new HashSet<>(hiddenApps)).apply();
    }

    public Set<String> getWhitelistedApps() {
        return new HashSet<>(sharedpreferences.getStringSet(KEY_WHITELISTED_APPS, new HashSet<>()));
    }

    public void saveWhitelistedApps(Set<String> whitelistedApps) {
        sharedpreferences.edit().putStringSet(KEY_WHITELISTED_APPS, new HashSet<>(whitelistedApps)).apply();
    }

    public boolean toggleWhitelist(String packageName) {
        Set<String> whitelisted = getWhitelistedApps();
        boolean isNowWhitelisted;
        if (whitelisted.contains(packageName)) {
            whitelisted.remove(packageName);
            isNowWhitelisted = false;
        } else {
            whitelisted.add(packageName);
            isNowWhitelisted = true;
        }
        saveWhitelistedApps(whitelisted);
        return isNowWhitelisted;
    }

    public Set<String> getBlacklistedApps() {
        return new HashSet<>(sharedpreferences.getStringSet(KEY_BLACKLISTED_APPS, new HashSet<>()));
    }

    public void saveBlacklistedApps(Set<String> apps) {
        sharedpreferences.edit().putStringSet(KEY_BLACKLISTED_APPS, new HashSet<>(apps)).apply();
    }

    public int getKillMode() {
        return sharedpreferences.getInt(KEY_KILL_MODE, 1);
    }

    public void setKillMode(int mode) {
        sharedpreferences.edit().putInt(KEY_KILL_MODE, mode).apply();
    }

    public int getAutoKillType() {
        return sharedpreferences.getInt(KEY_AUTO_KILL_TYPE, 0);
    }

    public void setAutoKillType(int type) {
        sharedpreferences.edit().putInt(KEY_AUTO_KILL_TYPE, type).apply();
    }
}
