package com.gree1d.reappzuku;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.gree1d.reappzuku.PreferenceKeys.*;
import static com.gree1d.reappzuku.AppConstants.*;

public class AppLaunchAccessibilityService extends AccessibilityService {

    private static final String TAG = "AppLaunchA11yService";

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
        Log.d(TAG, "AccessibilityService connected");
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
            Log.d(TAG, "Skipping repeated trigger for: " + packageName);
            return;
        }

        lastTriggeredPackage = packageName;
        lastTriggerTime = now;

        Log.d(TAG, "Target app launched: " + packageName + " — triggering Auto-Kill");
        autoKillManager.performAutoKill(null);
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "AccessibilityService interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
    }
}
