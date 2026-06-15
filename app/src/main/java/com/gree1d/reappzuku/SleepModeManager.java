package com.gree1d.reappzuku;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import static com.gree1d.reappzuku.PreferenceKeys.*;
import com.gree1d.reappzuku.SleepModeLogManager;

public class SleepModeManager {
    private static final String TAG = "SleepModeManager";

    public enum FreezeType {
        TIMER,
        PERMANENT
    }

    private final Context context;
    private final Handler handler;
    private final ExecutorService executor;
    private final ShellManager shellManager;
    private final SharedPreferences sharedpreferences;
    private RestrictionsScheduler scheduler;
    private final Set<String> systemPackages = new HashSet<>();

    public SleepModeManager(Context context, Handler handler, ExecutorService executor,
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

    public Set<String> getSleepModeApps() {
        return new HashSet<>(sharedpreferences.getStringSet(KEY_SLEEP_MODE_APPS, new HashSet<>()));
    }

    public Set<String> getPermanentFreezeApps() {
        return new HashSet<>(sharedpreferences.getStringSet(KEY_SLEEP_MODE_APPS_PERMANENT, new HashSet<>()));
    }

    public FreezeType getFreezeType(String packageName) {
        if (getPermanentFreezeApps().contains(packageName)) return FreezeType.PERMANENT;
        if (getSleepModeApps().contains(packageName)) return FreezeType.TIMER;
        return null;
    }

    public Set<String> getFrozenTimerApps() {
        return new HashSet<>(sharedpreferences.getStringSet(KEY_SLEEP_MODE_APPS_FROZEN, new HashSet<>()));
    }

    private void markFrozen(String packageName) {
        Set<String> frozen = getFrozenTimerApps();
        frozen.add(packageName);
        sharedpreferences.edit().putStringSet(KEY_SLEEP_MODE_APPS_FROZEN, frozen).apply();
    }

    private void markUnfrozen(String packageName) {
        Set<String> frozen = getFrozenTimerApps();
        frozen.remove(packageName);
        sharedpreferences.edit().putStringSet(KEY_SLEEP_MODE_APPS_FROZEN, frozen).apply();
    }

    private boolean freezeApp(String packageName) {
        if (systemPackages.contains(packageName)) {
            return shellManager.suspendSystemApp(packageName);
        }
        return shellManager.runShellCommandBlocking("pm disable-user --user 0 " + packageName);
    }

    private boolean unfreezeApp(String packageName) {
        if (systemPackages.contains(packageName)) {
            return shellManager.unsuspendSystemApp(packageName);
        }
        return shellManager.runShellCommandBlocking("pm enable " + packageName);
    }

    public void saveSleepModeApps(Set<String> timerPackages, Set<String> permanentPackages,
            Runnable onComplete) {
        Set<String> previousPermanent = getPermanentFreezeApps();

        Set<String> previousTimer = getSleepModeApps();

        sharedpreferences.edit()
                .putStringSet(KEY_SLEEP_MODE_APPS, new HashSet<>(timerPackages))
                .putStringSet(KEY_SLEEP_MODE_APPS_PERMANENT, new HashSet<>(permanentPackages))
                .apply();

        Set<String> removedFromTimer = new HashSet<>(previousTimer);
        removedFromTimer.removeAll(timerPackages);
        if (!removedFromTimer.isEmpty()) {
            Set<String> frozen = getFrozenTimerApps();
            frozen.removeAll(removedFromTimer);
            sharedpreferences.edit().putStringSet(KEY_SLEEP_MODE_APPS_FROZEN, frozen).apply();
        }

        Set<String> toFreeze = new HashSet<>(permanentPackages);
        toFreeze.removeAll(previousPermanent);

        Set<String> toUnfreeze = new HashSet<>(previousPermanent);
        toUnfreeze.removeAll(permanentPackages);

        if (toFreeze.isEmpty() && toUnfreeze.isEmpty()) {
            if (onComplete != null) handler.post(onComplete);
            return;
        }

        executor.execute(() -> {
            for (String packageName : toFreeze) {
                boolean ok = freezeApp(packageName);
                SleepModeLogManager.logFreeze(context, packageName, ok);
            }
            for (String packageName : toUnfreeze) {
                boolean ok = unfreezeApp(packageName);
                SleepModeLogManager.logUnfreeze(context, packageName, ok);
            }
            if (onComplete != null) handler.post(onComplete);
        });
    }

    public void saveSleepModeApps(Set<String> packages) {
        sharedpreferences.edit().putStringSet(KEY_SLEEP_MODE_APPS, new HashSet<>(packages)).apply();
    }

    public boolean isSleepModeEnabled() {
        return sharedpreferences.getBoolean(KEY_SLEEP_MODE_ENABLED, false);
    }

    public void setSleepModeEnabled(boolean enabled) {
        sharedpreferences.edit().putBoolean(KEY_SLEEP_MODE_ENABLED, enabled).apply();
    }

    public void loadSleepModeApps(Consumer<List<AppModel>> callback) {
        executor.execute(() -> {
            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            Set<String> timerApps = getSleepModeApps();
            Set<String> permanentApps = getPermanentFreezeApps();
            List<AppModel> result = new ArrayList<>();
            systemPackages.clear();
            for (ApplicationInfo appInfo : packages) {
                if (appInfo.packageName.equals(context.getPackageName())) continue;
                if (ProtectedApps.isProtected(context, appInfo.packageName)) continue;
                if ((appInfo.flags & ApplicationInfo.FLAG_PERSISTENT) != 0) continue;
                if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    systemPackages.add(appInfo.packageName);
                }
                boolean isSystem = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                AppModel model = new AppModel(
                        pm.getApplicationLabel(appInfo).toString(),
                        appInfo.packageName,
                        "-",
                        0,
                        pm.getApplicationIcon(appInfo),
                        isSystem,
                        (appInfo.flags & ApplicationInfo.FLAG_PERSISTENT) != 0,
                        ProtectedApps.isProtected(context, appInfo.packageName));
                boolean selected = timerApps.contains(appInfo.packageName)
                        || permanentApps.contains(appInfo.packageName);
                model.setSelected(selected);
                if (permanentApps.contains(appInfo.packageName)) {
                    model.setFreezeType(FreezeType.PERMANENT);
                } else if (timerApps.contains(appInfo.packageName)) {
                    model.setFreezeType(FreezeType.TIMER);
                }
                result.add(model);
            }
            handler.post(() -> callback.accept(result));
        });
    }

    public void freezeBackgroundRestrictedApps(Runnable onComplete) {
        Set<String> packages = getSleepModeApps();
        if (packages.isEmpty()) {
            if (onComplete != null) handler.post(onComplete);
            return;
        }
        executor.execute(() -> {
            Set<String> alreadyFrozen = getFrozenTimerApps();
            for (String packageName : packages) {
                if (alreadyFrozen.contains(packageName)) continue;
                if (scheduler != null && scheduler.isProtected(packageName, RestrictionsScheduler.PROTECT_SLEEP_MODE)) continue;
                boolean ok = freezeApp(packageName);
                if (ok) markFrozen(packageName);
                SleepModeLogManager.logFreeze(context, packageName, ok);
            }
            if (onComplete != null) handler.post(onComplete);
        });
    }

    public void unfreezeBackgroundRestrictedApps(Runnable onComplete) {
        Set<String> packages = getSleepModeApps();
        if (packages.isEmpty()) {
            if (onComplete != null) handler.post(onComplete);
            return;
        }
        executor.execute(() -> {
            for (String packageName : packages) {
                boolean ok = unfreezeApp(packageName);
                if (ok) markUnfrozen(packageName);
                SleepModeLogManager.logUnfreeze(context, packageName, ok);
            }
            if (onComplete != null) handler.post(onComplete);
        });
    }
}
