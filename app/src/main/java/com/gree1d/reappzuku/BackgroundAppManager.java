package com.gree1d.reappzuku;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.gree1d.reappzuku.PreferenceKeys.*;
import static com.gree1d.reappzuku.AppConstants.*;

public class BackgroundAppManager {
    private static final String TAG = "BackgroundAppManager";
    private static final String BACKGROUND_RESTRICTION_OP = "RUN_ANY_IN_BACKGROUND";
    private static final String BG_RUN_RESTRICTION_OP = "RUN_IN_BACKGROUND";
    private static final String FOREGROUND_RESTRICTION_OP = "START_FOREGROUND";
    private static final String FGS_FROM_BG_RESTRICTION_OP = "START_FOREGROUND_SERVICES_FROM_BACKGROUND";
    private static final String BOOT_RESTRICTION_OP = "RECEIVE_BOOT_COMPLETED";
    private static final String WAKE_LOCK_RESTRICTION_OP = "WAKE_LOCK";
    private static final String ALARM_RESTRICTION_OP = "ALARM_WAKEUP";
    private static final Pattern PACKAGE_NAME_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+");
    private static final String FORCE_STOP_COMMAND_PREFIX = "am force-stop ";

    private final Context context;
    private final Handler handler;
    private final ExecutorService executor;
    private final ShellManager shellManager;
    private final List<AppModel> currentAppsList = new ArrayList<>();
    private boolean showSystemApps = false;
    private boolean showPersistentApps = false;
    private SharedPreferences sharedpreferences;
    private RestrictionsScheduler scheduler;

    public BackgroundAppManager(Context context, Handler handler, ExecutorService executor,
            ShellManager shellManager) {
        this.context = context;
        this.handler = handler;
        this.executor = executor;
        this.shellManager = shellManager;
        this.sharedpreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public void setScheduler(RestrictionsScheduler scheduler) {
        this.scheduler = scheduler;
    }

    private String runPs(String psCommand) {
        return shellManager.runShellCommandAndGetFullOutput(psCommand);
    }

    public boolean supportsBackgroundRestriction() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    }

    public boolean canApplyBackgroundRestrictionNow() {
        return supportsBackgroundRestriction() && shellManager.hasAnyShellPermission();
    }

    // --- Load apps ---

    public void loadBackgroundApps(Consumer<List<AppModel>> callback) {
        executor.execute(() -> {
            List<AppModel> result = new ArrayList<>();
            PackageManager packageManager = context.getPackageManager();
            Set<String> runningPackagesFromPs = new HashSet<>();
            Set<String> hiddenApps = getHiddenApps();
            Set<String> whitelistedApps = getWhitelistedApps();
            Set<String> desiredBackgroundRestrictedApps = getBackgroundRestrictedApps();
            BackgroundRestrictionState backgroundRestrictionState = getBackgroundRestrictionState();

            if (shellManager.hasAnyShellPermission()) {
                String command = "ps -A -o rss,name | grep '\\.' | grep -v '[-:@]'";
                try {
                    String fullOutput = runPs(command);
                    if (fullOutput != null) {
                        try (BufferedReader reader = new BufferedReader(new StringReader(fullOutput))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                String[] parts = line.trim().split("\\s+");
                                if (parts.length >= 2) {
                                    String packageName = parts[1].trim();
                                    String appRam = parts[0].trim();
                                    if (!packageName.isEmpty() && packageName.contains(".")
                                            && !packageName.startsWith("ERROR:")) {
                                        try {
                                            packageManager.getApplicationInfo(packageName, 0);
                                            runningPackagesFromPs.add(packageName + ":" + appRam);
                                        } catch (PackageManager.NameNotFoundException ignored) {
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        handler.post(() -> Toast
                                .makeText(context, context.getString(R.string.toast_failed_get_running_apps), Toast.LENGTH_SHORT).show());
                    }
                } catch (Exception e) {
                    handler.post(() -> Toast
                            .makeText(context, context.getString(R.string.toast_error_getting_running_apps, e.getMessage()), Toast.LENGTH_SHORT)
                            .show());
                }
            }

            for (String packageEntry : runningPackagesFromPs) {
                String[] parts = packageEntry.split(":");
                String packageName = parts[0];
                long ramUsage = 0;
                try {
                    ramUsage = parts.length > 1 ? Long.parseLong(parts[1]) : 0;
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Failed to parse RAM value for " + packageName, e);
                }

                try {
                    if (hiddenApps.contains(packageName)) {
                        continue;
                    }

                    boolean isProtected = ProtectedApps.isProtected(context, packageName);
                    ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);

                    boolean isPersistentApp = (appInfo.flags & ApplicationInfo.FLAG_PERSISTENT) != 0;
                    boolean isSystemApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;

                    if (!showSystemApps && isSystemApp || !showPersistentApps && isPersistentApp) {
                        continue;
                    }

                    AppModel appModel = new AppModel(
                            packageManager.getApplicationLabel(appInfo).toString(),
                            packageName,
                            formatMemorySize(ramUsage),
                            ramUsage,
                            packageManager.getApplicationIcon(appInfo),
                            isSystemApp,
                            isPersistentApp,
                            isProtected);

                    appModel.setWhitelisted(whitelistedApps.contains(packageName));
                    applyBackgroundRestrictionState(appModel, desiredBackgroundRestrictedApps, backgroundRestrictionState);
                    result.add(appModel);
                } catch (PackageManager.NameNotFoundException ignored) {
                }
            }

            sortAppList(result, SORT_MODE_DEFAULT);

            handler.post(() -> {
                currentAppsList.clear();
                currentAppsList.addAll(result);
                if (callback != null) {
                    callback.accept(new ArrayList<>(result));
                }
            });
        });
    }

    public void loadBackgroundRestrictionApps(Consumer<List<AppModel>> callback) {
        executor.execute(() -> {
            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            Set<String> desiredPackages = getBackgroundRestrictedApps();
            BackgroundRestrictionState state = getBackgroundRestrictionState();
            List<AppModel> result = new ArrayList<>();
            for (ApplicationInfo appInfo : packages) {
                String packageName = appInfo.packageName;
                if (packageName.equals(context.getPackageName())) {
                    continue;
                }

                AppModel model = new AppModel(
                        pm.getApplicationLabel(appInfo).toString(),
                        packageName,
                        "-",
                        0,
                        pm.getApplicationIcon(appInfo),
                        (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0,
                        (appInfo.flags & ApplicationInfo.FLAG_PERSISTENT) != 0,
                        ProtectedApps.isProtected(context, packageName));
                applyBackgroundRestrictionState(model, desiredPackages, state);
                result.add(model);
            }

            Collections.sort(result, (a, b) -> a.getAppName().compareToIgnoreCase(b.getAppName()));
            handler.post(() -> callback.accept(result));
        });
    }

    public void loadAllApps(Consumer<List<AppModel>> callback) {
        executor.execute(() -> {
            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            List<AppModel> allApps = new ArrayList<>();
            for (ApplicationInfo appInfo : packages) {
                if (appInfo.packageName.equals(context.getPackageName())) {
                    continue;
                }
                boolean isSystem = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                boolean isPersistent = (appInfo.flags & ApplicationInfo.FLAG_PERSISTENT) != 0;
                allApps.add(new AppModel(
                        pm.getApplicationLabel(appInfo).toString(),
                        appInfo.packageName,
                        "-",
                        0,
                        pm.getApplicationIcon(appInfo),
                        isSystem,
                        isPersistent,
                        ProtectedApps.isProtected(context, appInfo.packageName)));
            }
            Collections.sort(allApps, (a1, a2) -> a1.getAppName().compareToIgnoreCase(a2.getAppName()));
            handler.post(() -> callback.accept(allApps));
        });
    }

    public void updateRunningState(List<AppModel> apps, Runnable onComplete) {
        if (!shellManager.hasAnyShellPermission()) {
            if (onComplete != null) handler.post(onComplete);
            return;
        }

        executor.execute(() -> {
            String psOutput = runPs("ps -A -o rss,name | grep '\\.' | grep -v '[-:@]'");

            Map<String, Long> runningMap = new HashMap<>();
            if (psOutput != null) {
                try (BufferedReader reader = new BufferedReader(new StringReader(psOutput))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length >= 2) {
                            String packageName = parts[1].trim();
                            try {
                                long ramUsage = Long.parseLong(parts[0].trim());
                                runningMap.put(packageName, ramUsage);
                            } catch (NumberFormatException e) {
                                Log.w(TAG, "Failed to parse RAM value for " + packageName, e);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            for (AppModel app : apps) {
                if (runningMap.containsKey(app.getPackageName())) {
                    long ram = runningMap.get(app.getPackageName());
                    app.setAppRamBytes(ram);
                    app.setAppRam(formatMemorySize(ram));
                } else {
                    app.setAppRamBytes(0);
                    app.setAppRam("-");
                }
            }

            if (onComplete != null) handler.post(onComplete);
        });
    }

    public void sortAppList(List<AppModel> apps, int sortMode) {
        if (apps == null || apps.isEmpty()) {
            return;
        }

        switch (sortMode) {
            case SORT_MODE_RAM_DESC:
                Collections.sort(apps, (a1, a2) -> Long.compare(a2.getAppRamBytes(), a1.getAppRamBytes()));
                break;
            case SORT_MODE_RAM_ASC:
                Collections.sort(apps, (a1, a2) -> Long.compare(a1.getAppRamBytes(), a2.getAppRamBytes()));
                break;
            case SORT_MODE_NAME_ASC:
                Collections.sort(apps, (a1, a2) -> a1.getAppName().compareToIgnoreCase(a2.getAppName()));
                break;
            case SORT_MODE_NAME_DESC:
                Collections.sort(apps, (a1, a2) -> a2.getAppName().compareToIgnoreCase(a1.getAppName()));
                break;
            case SORT_MODE_DEFAULT:
            default:
                Collections.sort(apps,
                        Comparator.comparing(AppModel::isSystemApp)
                                .thenComparing(AppModel::isPersistentApp)
                                .thenComparing(a -> a.getAppName().toLowerCase()));
                break;
        }
    }

    public void clearCaches(Runnable onComplete) {
        if (!shellManager.hasAnyShellPermission()) {
            if (onComplete != null)
                handler.post(onComplete);
            return;
        }

        executor.execute(() -> {
            shellManager.runShellCommand("pm trim-caches 4096G", () -> {
                Toast.makeText(context, context.getString(R.string.toast_caches_cleared), Toast.LENGTH_SHORT).show();
                if (onComplete != null)
                    onComplete.run();
            }, () -> {
                Toast.makeText(context, context.getString(R.string.toast_failed_clear_caches), Toast.LENGTH_SHORT).show();
                if (onComplete != null)
                    onComplete.run();
            });
        });
    }

    public void setShowSystemApps(boolean show) {
        this.showSystemApps = show;
    }

    public void setShowPersistentApps(boolean show) {
        this.showPersistentApps = show;
    }

    // --- Background Restriction ---

    public void setBackgroundRestricted(String packageName, boolean restricted, Runnable onComplete) {
        Set<String> targetPackages = getBackgroundRestrictedApps();
        if (restricted) {
            targetPackages.add(packageName);
        } else {
            targetPackages.remove(packageName);
        }
        applyBackgroundRestriction(targetPackages, onComplete);
    }

    public void applyBackgroundRestriction(Set<String> targetPackages, Runnable onComplete) {
        applyBackgroundRestriction(targetPackages, null, onComplete);
    }

    /**
     * Applies background restrictions.
     * @param targetPackages  full set of packages that should be restricted
     * @param hardPackages    subset of targetPackages that should use HARD restriction (START_FOREGROUND ignore).
     *                        Pass null to preserve existing hard/soft assignments from preferences.
     * @param onComplete      called on main thread when done
     */
    public void applyBackgroundRestriction(Set<String> targetPackages, Set<String> hardPackages, Runnable onComplete) {
        Set<String> desiredPackages = sanitizeBackgroundRestrictionTargets(targetPackages);
        saveBackgroundRestrictedApps(desiredPackages);

        if (hardPackages != null) {
            Set<String> sanitizedHard = new HashSet<>(hardPackages);
            sanitizedHard.retainAll(desiredPackages);
            saveHardRestrictedApps(sanitizedHard);
        } else {
            Set<String> existingHard = getHardRestrictedApps();
            existingHard.retainAll(desiredPackages);
            saveHardRestrictedApps(existingHard);
        }

        if (!supportsBackgroundRestriction()) {
            BackgroundRestrictionLog.log(context, null, "apply", "skipped", "Android 11+ required");
            Toast.makeText(context, context.getString(R.string.toast_bg_restriction_requires_android11), Toast.LENGTH_SHORT).show();
            if (onComplete != null) {
                handler.post(onComplete);
            }
            return;
        }
        if (!shellManager.hasAnyShellPermission()) {
            BackgroundRestrictionLog.log(context, null, "apply", "failed", "No Root or Shizuku permission available");
            shellManager.checkShellPermissions();
            if (onComplete != null) {
                handler.post(onComplete);
            }
            return;
        }

        executor.execute(() -> {
            Set<String> currentPackages = getActualBackgroundRestrictedApps();
            Set<String> hardSet = getHardRestrictedApps();

            Set<String> packagesToAllow = new HashSet<>(currentPackages);
            packagesToAllow.removeAll(desiredPackages);

            Set<String> packagesToRestrict = new HashSet<>(desiredPackages);
            packagesToRestrict.removeAll(currentPackages);

            Set<String> packagesToUpdate = new HashSet<>(desiredPackages);
            packagesToUpdate.retainAll(currentPackages);

            boolean success = true;

            for (String packageName : packagesToAllow) {
                ShellManager.ShellResult r1 = shellManager
                        .runShellCommandForResult(buildBackgroundRestrictionCommand(packageName, "allow"));
                ShellManager.ShellResult r2 = shellManager
                        .runShellCommandForResult(buildHardRestrictionCommand(packageName, "allow"));
                applyHardExtraOps(packageName, "allow");
                shellManager.runShellCommandForResult(buildBootRestrictionCommand(packageName, "allow"));
                restoreBatteryWhitelist(packageName);
                if (!r1.succeeded()) success = false;
                logRestrictionResult(packageName, "allow", r1, null);
            }

            for (String packageName : packagesToRestrict) {
                boolean isHard = hardSet.contains(packageName);
                ShellManager.ShellResult restrictResult = isHard
                        ? shellManager.runShellCommandForResult(buildHardRestrictionCommand(packageName, "ignore"))
                        : shellManager.runShellCommandForResult(buildBackgroundRestrictionCommand(packageName, "ignore"));
                if (!restrictResult.succeeded()) {
                    success = false;
                    logRestrictionResult(packageName, isHard ? "restrict-hard" : "restrict-soft", restrictResult, null);
                    continue;
                }
                if (isHard) {
                    applyHardExtraOps(packageName, "ignore");
                    shellManager.runShellCommandForResult(buildBootRestrictionCommand(packageName, "ignore"));
                    applyBatteryWhitelistRemoval(packageName);
                }
                ShellManager.ShellResult forceStopResult = shellManager.runShellCommandForResult(FORCE_STOP_COMMAND_PREFIX + packageName);
                if (!forceStopResult.succeeded()) success = false;
                logRestrictionResult(packageName, isHard ? "restrict-hard" : "restrict-soft", restrictResult, forceStopResult);
            }

            for (String packageName : packagesToUpdate) {
                boolean isHard = hardSet.contains(packageName);
                if (isHard) {
                    shellManager.runShellCommandForResult(buildBackgroundRestrictionCommand(packageName, "allow"));
                    shellManager.runShellCommandForResult(buildHardRestrictionCommand(packageName, "ignore"));
                    applyHardExtraOps(packageName, "ignore");
                    shellManager.runShellCommandForResult(buildBootRestrictionCommand(packageName, "ignore"));
                    applyBatteryWhitelistRemoval(packageName);
                } else {
                    shellManager.runShellCommandForResult(buildHardRestrictionCommand(packageName, "allow"));
                    applyHardExtraOps(packageName, "allow");
                    shellManager.runShellCommandForResult(buildBootRestrictionCommand(packageName, "allow"));
                    restoreBatteryWhitelist(packageName);
                    shellManager.runShellCommandForResult(buildBackgroundRestrictionCommand(packageName, "ignore"));
                }
            }

            BackgroundRestrictionState actualState = getBackgroundRestrictionState();
            for (String packageName : packagesToAllow) {
                logRestrictionVerification(packageName, "allow", actualState, false);
            }
            for (String packageName : packagesToRestrict) {
                logRestrictionVerification(packageName, "restrict", actualState, true);
            }

            final boolean finalSuccess = success;
            handler.post(() -> {
                Toast.makeText(context,
                        finalSuccess ? context.getString(R.string.bg_manager_restriction_applied)
                                : context.getString(R.string.toast_bg_restriction_changes_partial),
                        Toast.LENGTH_SHORT).show();
                if (onComplete != null) {
                    onComplete.run();
                }
            });
        });
    }

    public void reapplySavedBackgroundRestrictions(Runnable onComplete) {
        Set<String> desired = sanitizeBackgroundRestrictionTargets(getBackgroundRestrictedApps());
        Set<String> hard = getHardRestrictedApps();

        if (desired.isEmpty()) {
            BackgroundRestrictionLog.log(context, null, "reapply", "skipped", "No saved restrictions");
            if (onComplete != null) handler.post(onComplete);
            return;
        }
        if (!supportsBackgroundRestriction()) {
            BackgroundRestrictionLog.log(context, null, "reapply", "skipped", "Android 11+ required");
            if (onComplete != null) handler.post(onComplete);
            return;
        }
        if (!shellManager.hasAnyShellPermission()) {
            BackgroundRestrictionLog.log(context, null, "reapply", "failed", "No shell permission");
            if (onComplete != null) handler.post(onComplete);
            return;
        }

        executor.execute(() -> {
            boolean success = true;
            for (String packageName : desired) {
                if (scheduler != null && scheduler.isProtected(packageName, RestrictionsScheduler.PROTECT_BG_RESTRICTIONS)) {
                    Log.d(TAG, "reapply SKIP (temp protected): " + packageName);
                    continue;
                }
                boolean isHard = hard.contains(packageName);

                ShellManager.ShellResult result = isHard
                        ? shellManager.runShellCommandForResult(buildHardRestrictionCommand(packageName, "ignore"))
                        : shellManager.runShellCommandForResult(buildBackgroundRestrictionCommand(packageName, "ignore"));
                if (!result.succeeded()) success = false;
                logRestrictionResult(packageName, isHard ? "reapply-hard" : "reapply-soft", result, null);

                if (isHard) {
                    applyHardExtraOps(packageName, "ignore");
                    shellManager.runShellCommandForResult(buildBootRestrictionCommand(packageName, "ignore"));
                    applyBatteryWhitelistRemoval(packageName);
                }

                BackgroundRestrictionState actualState = getBackgroundRestrictionState();
                logRestrictionVerification(packageName, isHard ? "reapply-hard" : "reapply-soft",
                        actualState, true);
            }

            final boolean finalSuccess = success;
            handler.post(() -> {
                Toast.makeText(context,
                        finalSuccess ? context.getString(R.string.bg_manager_restrictions_reapplied)
                                : context.getString(R.string.bg_manager_restrictions_reapply_partial),
                        Toast.LENGTH_SHORT).show();
                if (onComplete != null) onComplete.run();
            });
        });
    }

    public void setRestrictionType(String packageName, boolean hard) {
        Set<String> hardSet = getHardRestrictedApps();
        if (hard) {
            hardSet.add(packageName);
        } else {
            hardSet.remove(packageName);
        }
        saveHardRestrictedApps(hardSet);
    }

    // --- Shell command builders ---

    private String buildBackgroundRestrictionCommand(String packageName, String mode) {
        return "cmd appops set --user current " + packageName + " " + BACKGROUND_RESTRICTION_OP + " " + mode;
    }

    private String buildHardRestrictionCommand(String packageName, String mode) {
        return "cmd appops set --user current " + packageName + " " + FOREGROUND_RESTRICTION_OP + " " + mode;
    }

    private String buildBootRestrictionCommand(String packageName, String mode) {
        return "cmd appops set --user current " + packageName + " " + BOOT_RESTRICTION_OP + " " + mode;
    }

    private void applyHardExtraOps(String packageName, String mode) {
        shellManager.runShellCommandForResult(
                "cmd appops set --user current " + packageName + " " + BACKGROUND_RESTRICTION_OP + " " + mode);
        shellManager.runShellCommandForResult(
                "cmd appops set --user current " + packageName + " " + BG_RUN_RESTRICTION_OP + " " + mode);
        shellManager.runShellCommandForResult(
                "cmd appops set --user current " + packageName + " " + FGS_FROM_BG_RESTRICTION_OP + " " + mode);
        shellManager.runShellCommandForResult(
                "cmd appops set --user current " + packageName + " " + WAKE_LOCK_RESTRICTION_OP + " " + mode);
        shellManager.runShellCommandForResult(
                "cmd appops set --user current " + packageName + " " + ALARM_RESTRICTION_OP + " " + mode);
    }

    // --- Battery whitelist ---

    private boolean isInBatteryWhitelist(String packageName) {
        String output = shellManager.runShellCommandAndGetFullOutput("dumpsys deviceidle whitelist");
        if (output == null) return false;
        for (String line : output.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("user,") && trimmed.contains("," + packageName + ",")) {
                return true;
            }
        }
        return false;
    }

    private void applyBatteryWhitelistRemoval(String packageName) {
        if (!isInBatteryWhitelist(packageName)) return;
        ShellManager.ShellResult result = shellManager.runShellCommandForResult(
                "cmd deviceidle whitelist -" + packageName);
        if (result.succeeded()) {
            Set<String> removed = getBatteryWhitelistRemoved();
            removed.add(packageName);
            saveBatteryWhitelistRemoved(removed);
            BackgroundRestrictionLog.log(context, packageName, "restrict-hard",
                    "battery-whitelist-removed", "removed from deviceidle whitelist");
        }
    }

    private void restoreBatteryWhitelist(String packageName) {
        Set<String> removed = getBatteryWhitelistRemoved();
        if (!removed.contains(packageName)) return;
        shellManager.runShellCommandForResult("cmd deviceidle whitelist +" + packageName);
        removed.remove(packageName);
        saveBatteryWhitelistRemoved(removed);
        BackgroundRestrictionLog.log(context, packageName, "allow",
                "battery-whitelist-restored", "restored to deviceidle whitelist");
    }

    // --- BackgroundRestrictionState ---

    private Set<String> getActualBackgroundRestrictedApps() {
        return getBackgroundRestrictionState().restrictedPackages;
    }

    private BackgroundRestrictionState getBackgroundRestrictionState() {
        Set<String> fallbackPackages = getBackgroundRestrictedApps();
        if (!supportsBackgroundRestriction() || !shellManager.hasAnyShellPermission()) {
            return new BackgroundRestrictionState(fallbackPackages, false);
        }

        Set<String> restrictedPackages = new HashSet<>();
        boolean querySucceeded = false;

        String ignoreOutput = shellManager.runShellCommandAndGetFullOutput(
                "cmd appops query-op --user current " + BACKGROUND_RESTRICTION_OP + " ignore");
        if (ignoreOutput != null) {
            querySucceeded = true;
            mergeBackgroundRestrictedPackages(restrictedPackages, ignoreOutput);
        }

        String denyOutput = shellManager.runShellCommandAndGetFullOutput(
                "cmd appops query-op --user current " + BACKGROUND_RESTRICTION_OP + " deny");
        if (denyOutput != null) {
            querySucceeded = true;
            mergeBackgroundRestrictedPackages(restrictedPackages, denyOutput);
        }

        String hardIgnoreOutput = shellManager.runShellCommandAndGetFullOutput(
                "cmd appops query-op --user current " + FOREGROUND_RESTRICTION_OP + " ignore");
        if (hardIgnoreOutput != null) {
            querySucceeded = true;
            mergeBackgroundRestrictedPackages(restrictedPackages, hardIgnoreOutput);
        }

        String hardDenyOutput = shellManager.runShellCommandAndGetFullOutput(
                "cmd appops query-op --user current " + FOREGROUND_RESTRICTION_OP + " deny");
        if (hardDenyOutput != null) {
            querySucceeded = true;
            mergeBackgroundRestrictedPackages(restrictedPackages, hardDenyOutput);
        }

        return new BackgroundRestrictionState(querySucceeded ? restrictedPackages : fallbackPackages, querySucceeded);
    }

    private void mergeBackgroundRestrictedPackages(Set<String> packages, String output) {
        if (output == null || output.trim().isEmpty()) {
            return;
        }
        Matcher matcher = PACKAGE_NAME_PATTERN.matcher(output);
        while (matcher.find()) {
            packages.add(matcher.group());
        }
    }

    private void applyBackgroundRestrictionState(AppModel model, Set<String> desiredPackages,
            BackgroundRestrictionState actualState) {
        boolean desired = desiredPackages.contains(model.getPackageName());
        model.setBackgroundRestrictionDesired(desired);
        model.setBackgroundRestrictionActualKnown(actualState.querySucceeded);
        if (actualState.querySucceeded) {
            model.setBackgroundRestrictionActual(actualState.restrictedPackages.contains(model.getPackageName()));
        } else {
            model.setBackgroundRestrictionActual(desired);
        }
    }

    private Set<String> sanitizeBackgroundRestrictionTargets(Set<String> targetPackages) {
        Set<String> desiredPackages = new HashSet<>();
        if (targetPackages == null) {
            return desiredPackages;
        }
        for (String packageName : targetPackages) {
            if (packageName != null && !packageName.isEmpty() && !packageName.equals(context.getPackageName())) {
                desiredPackages.add(packageName);
            }
        }
        return desiredPackages;
    }

    // --- Logging ---

    private void logRestrictionResult(String packageName, String action,
            ShellManager.ShellResult appOpsResult, ShellManager.ShellResult forceStopResult) {
        StringBuilder detail = new StringBuilder()
                .append("appops=")
                .append(formatShellOutcome(appOpsResult));
        if (forceStopResult != null) {
            detail.append(" force-stop=").append(formatShellOutcome(forceStopResult));
        }
        String outcome = appOpsResult != null && appOpsResult.succeeded()
                && (forceStopResult == null || forceStopResult.succeeded())
                        ? "ok"
                        : "failed";
        BackgroundRestrictionLog.log(context, packageName, action, outcome, detail.toString());
    }

    private void logRestrictionVerification(String packageName, String action, BackgroundRestrictionState state,
            boolean desiredRestricted) {
        if (!state.querySucceeded) {
            BackgroundRestrictionLog.log(context, packageName, action, "verify-unavailable", "AppOps query failed");
            return;
        }
        boolean isRestricted = state.restrictedPackages.contains(packageName);
        BackgroundRestrictionLog.log(context, packageName, action,
                desiredRestricted == isRestricted ? "verified" : "verify-failed",
                "expected=" + (desiredRestricted ? "restricted" : "allowed")
                        + " actual=" + (isRestricted ? "restricted" : "allowed"));
    }

    private String formatShellOutcome(ShellManager.ShellResult result) {
        if (result == null) {
            return "n/a";
        }
        StringBuilder detail = new StringBuilder(result.succeeded() ? "ok" : "failed");
        if (!result.output().isEmpty()) {
            detail.append("[").append(result.output()).append("]");
        } else if (!result.succeeded() && result.exitCode() >= 0) {
            detail.append("[exit=").append(result.exitCode()).append("]");
        }
        return detail.toString();
    }

    public String getBackgroundRestrictionLogText() {
        return BackgroundRestrictionLog.readDisplayText(context);
    }

    public void clearBackgroundRestrictionLog() {
        BackgroundRestrictionLog.clear(context);
    }

    // --- Inner classes ---

    private static final class BackgroundRestrictionState {
        private final Set<String> restrictedPackages;
        private final boolean querySucceeded;

        private BackgroundRestrictionState(Set<String> restrictedPackages, boolean querySucceeded) {
            this.restrictedPackages = restrictedPackages;
            this.querySucceeded = querySucceeded;
        }
    }

    // --- Preferences ---

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

    public Set<String> getBackgroundRestrictedApps() {
        return new HashSet<>(sharedpreferences.getStringSet(KEY_AUTOSTART_DISABLED_APPS, new HashSet<>()));
    }

    public void saveBackgroundRestrictedApps(Set<String> packageNames) {
        sharedpreferences.edit().putStringSet(KEY_AUTOSTART_DISABLED_APPS, new HashSet<>(packageNames)).apply();
    }

    public Set<String> getHardRestrictedApps() {
        return new HashSet<>(sharedpreferences.getStringSet(KEY_HARD_RESTRICTION_APPS, new HashSet<>()));
    }

    public void saveHardRestrictedApps(Set<String> packageNames) {
        sharedpreferences.edit().putStringSet(KEY_HARD_RESTRICTION_APPS, new HashSet<>(packageNames)).apply();
    }

    public boolean isHardRestricted(String packageName) {
        return getHardRestrictedApps().contains(packageName);
    }

    public Set<String> getBatteryWhitelistRemoved() {
        return new HashSet<>(sharedpreferences.getStringSet(KEY_BATTERY_WHITELIST_REMOVED, new HashSet<>()));
    }

    public void saveBatteryWhitelistRemoved(Set<String> packages) {
        sharedpreferences.edit().putStringSet(KEY_BATTERY_WHITELIST_REMOVED, new HashSet<>(packages)).apply();
    }

    // --- Shared utility ---

    public String formatMemorySize(long kb) {
        if (kb < 1024)
            return kb + " KB";
        else if (kb < 1024 * 1024)
            return String.format(java.util.Locale.US, "%.2f MB", kb / 1024f);
        else
            return String.format(java.util.Locale.US, "%.2f GB", kb / (1024f * 1024f));
    }

    public List<AppModel> getCurrentAppsList() {
        return currentAppsList;
    }
}
