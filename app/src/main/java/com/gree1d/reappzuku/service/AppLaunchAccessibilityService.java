package com.gree1d.reappzuku.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.gree1d.reappzuku.core.AppDebugManager;
import com.gree1d.reappzuku.core.AppDebugManager.Category;
import com.gree1d.reappzuku.core.ShellManager;
import com.gree1d.reappzuku.manager.AutoKillManager;
import com.gree1d.reappzuku.manager.BackgroundAppManager;
import com.gree1d.reappzuku.manager.PresetManager;
import com.gree1d.reappzuku.core.ProtectedApps;

import static com.gree1d.reappzuku.core.PreferenceKeys.*;
import static com.gree1d.reappzuku.core.AppConstants.*;

public class AppLaunchAccessibilityService extends AccessibilityService {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService executor;
    private ShellManager shellManager;
    private AutoKillManager autoKillManager;

    private String lastTriggeredPackage = null;
    private long lastTriggerTime = 0;
    private static final long MIN_TRIGGER_INTERVAL_MS = 5_000L;

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newSingleThreadExecutor();
        shellManager = new ShellManager(getApplicationContext(), handler, executor);
        BackgroundAppManager appManager = new BackgroundAppManager(
                getApplicationContext(), handler, executor, shellManager);
        autoKillManager = new AutoKillManager(
                getApplicationContext(), handler, executor, shellManager,
                appManager.getCurrentAppsList());
        AppDebugManager.d(Category.ADVANCED_CONDITIONS, "AppLaunchAccessibilityService: Service created, AutoKillManager initialized");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        info.notificationTimeout = 100;
        setServiceInfo(info);
        AppDebugManager.d(Category.ADVANCED_CONDITIONS, "AppLaunchAccessibilityService: AccessibilityService connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        CharSequence packageNameCs = event.getPackageName();
        if (packageNameCs == null) return;
        String packageName = packageNameCs.toString();

        if (packageName.equals(getPackageName())) return;

        SharedPreferences prefs = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);

        if (!prefs.getBoolean(KEY_APP_LAUNCH_TRIGGER_ENABLED, false)) return;
        if (!prefs.getBoolean(KEY_AUTO_KILL_ENABLED, false)) return;

        Set<String> targetPackages = prefs.getStringSet(KEY_APP_LAUNCH_TRIGGER_PACKAGES, new HashSet<>());
        if (!targetPackages.contains(packageName)) return;

        long now = System.currentTimeMillis();
        if (packageName.equals(lastTriggeredPackage) && (now - lastTriggerTime) < MIN_TRIGGER_INTERVAL_MS) {
            AppDebugManager.d(Category.ADVANCED_CONDITIONS, "AppLaunchAccessibilityService: Skipping repeated trigger for: " + packageName);
            return;
        }

        lastTriggeredPackage = packageName;
        lastTriggerTime = now;

        AppDebugManager.d(Category.ADVANCED_CONDITIONS, "AppLaunchAccessibilityService: Target app launched: " + packageName + " — triggering Auto-Kill");
        autoKillManager.performAutoKill(null, new HashSet<String>(targetPackages), resolveKillSource("App Launch Trigger"));

        if (prefs.getBoolean(KEY_APP_LAUNCH_CLEAR_CACHE, false)) {
            executor.execute(() -> trimMemoryForAll(targetPackages));
        }
    }

    private void trimMemoryForAll(Set<String> excludePackages) {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> installedApps;
        try {
            installedApps = pm.getInstalledApplications(0);
        } catch (Exception e) {
            AppDebugManager.e(Category.ADVANCED_CONDITIONS, "AppLaunchAccessibilityService: Failed to get installed apps", e);
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        Set<String> whitelistedApps = prefs.getStringSet(KEY_WHITELISTED_APPS, new HashSet<>());
        Set<String> hiddenApps = prefs.getStringSet(KEY_HIDDEN_APPS, new HashSet<>());

        for (ApplicationInfo app : installedApps) {
            String pkg = app.packageName;

            if (pkg.equals(getPackageName())) continue;
            if (excludePackages.contains(pkg)) continue;
            if (hiddenApps.contains(pkg)) continue;
            if (whitelistedApps.contains(pkg)) continue;
            if (ProtectedApps.isProtected(getApplicationContext(), pkg)) continue;
            if ((app.flags & ApplicationInfo.FLAG_PERSISTENT) != 0) continue;

            try {
                String pidOutput = shellManager.runShellCommandAndGetFullOutput("pidof " + pkg);
                if (pidOutput == null || pidOutput.trim().isEmpty()) continue;
                for (String pidStr : pidOutput.trim().split("\\s+")) {
                    pidStr = pidStr.trim();
                    if (pidStr.isEmpty()) continue;
                    shellManager.runShellCommandAndGetFullOutput(
                            "am send-trim-memory " + pidStr + " RUNNING_CRITICAL");
                    AppDebugManager.d(Category.ADVANCED_CONDITIONS, "AppLaunchAccessibilityService: Trim memory sent to " + pkg + " (pid " + pidStr + ")");
                }
            } catch (Exception e) {
                AppDebugManager.w(Category.ADVANCED_CONDITIONS, "AppLaunchAccessibilityService: Failed to trim memory for " + pkg + ": " + e.getMessage());
            }
        }
    }

    @Override
    public void onInterrupt() {
        AppDebugManager.d(Category.ADVANCED_CONDITIONS, "AppLaunchAccessibilityService: AccessibilityService interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AppDebugManager.d(Category.ADVANCED_CONDITIONS, "AppLaunchAccessibilityService: Service destroyed");
        if (executor != null) {
            executor.shutdown();
        }
    }

    private String resolveKillSource(String defaultSource) {
        SharedPreferences prefs = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        int activePreset = prefs.getInt(KEY_ACTIVE_PRESET, 0);
        if (activePreset != 0) {
            PresetManager pm = new PresetManager(this);
            return defaultSource + " · " + pm.getPresetName(activePreset);
        }
        return defaultSource;
    }
}
