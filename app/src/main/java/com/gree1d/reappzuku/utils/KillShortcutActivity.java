package com.gree1d.reappzuku.utils;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.widget.Toast;

import com.gree1d.reappzuku.core.AppDebugManager;
import com.gree1d.reappzuku.core.AppDebugManager.Category;
import com.gree1d.reappzuku.core.ShellManager;
import com.gree1d.reappzuku.manager.AutoKillManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;

public class KillShortcutActivity extends Activity {

    private static final String TAG = "KillShortcutActivity";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";

    private ShellManager shellManager;
    private AutoKillManager autoKillManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppDebugManager.d(Category.SHORTCUTS_WIDGETS, TAG + ": onCreate, action=" + (getIntent() != null ? getIntent().getAction() : "null"));

        String action = getIntent() != null ? getIntent().getAction() : null;
        if ("WIDGET_KILL".equals(action)) {
            AppDebugManager.d(Category.SHORTCUTS_WIDGETS, TAG + ": WIDGET_KILL received, proxying to ShappkyService");
            Intent service = new Intent(this, com.gree1d.reappzuku.service.ShappkyService.class);
            service.setAction("WIDGET_KILL");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(service);
            } else {
                startService(service);
            }
            finish();
            return;
        }

        shellManager = new ShellManager(this, handler, executor);

        if (!shellManager.hasAnyShellPermission()) {
            AppDebugManager.w(Category.SHORTCUTS_WIDGETS, TAG + ": no shell permission, aborting");
            Toast.makeText(getApplicationContext(), "Shizuku or Root permission required", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        autoKillManager = new AutoKillManager(this, handler, executor, shellManager, new ArrayList<>());

        executor.execute(() -> {
            String targetPackage = findKillablePackage();
            AppDebugManager.d(Category.SHORTCUTS_WIDGETS, TAG + ": findKillablePackage result=" + targetPackage);
            handler.post(() -> {
                if (targetPackage != null) {
                    AppDebugManager.d(Category.SHORTCUTS_WIDGETS, TAG + ": killing " + targetPackage);
                    autoKillManager.killApp(targetPackage, "Shortcut Kill", () -> finish());
                } else {
                    AppDebugManager.w(Category.SHORTCUTS_WIDGETS, TAG + ": no killable foreground app found");
                    Toast.makeText(getApplicationContext(), "No killable foreground app found", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
        });
    }

    private String findKillablePackage() {
        Set<String> launcherPackages = getLauncherPackages();

        String recentsOutput = shellManager.runShellCommandAndGetFullOutput("dumpsys activity recents");
        String targetPackage = findKillablePackageFromRecents(recentsOutput, launcherPackages);
        if (targetPackage != null) {
            AppDebugManager.d(Category.SHORTCUTS_WIDGETS, TAG + ": found target from recents: " + targetPackage);
            return targetPackage;
        }

        String activitiesOutput = shellManager.runShellCommandAndGetFullOutput(
                "dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity|ActivityRecord'");
        return findKillablePackageFromActivities(activitiesOutput, launcherPackages);
    }

    private Set<String> getLauncherPackages() {
        Set<String> launcherPackages = new HashSet<>();
        Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> homeResolvers = getPackageManager().queryIntentActivities(homeIntent, 0);
        for (ResolveInfo ri : homeResolvers) {
            if (ri.activityInfo != null) {
                launcherPackages.add(ri.activityInfo.packageName);
            }
        }
        launcherPackages.add(getPackageName());
        launcherPackages.add(SYSTEM_UI_PACKAGE);
        return launcherPackages;
    }

    private String findKillablePackageFromRecents(String output, Set<String> excludedPackages) {
        if (output == null || output.isEmpty()) return null;

        boolean inHomeBlock = false;
        for (String line : output.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("* Recent #")) {
                inHomeBlock = trimmed.contains("type=home");
                continue;
            }
            if (inHomeBlock) continue;

            String candidate = extractRecentPackage(trimmed);
            if (isKillablePackage(candidate, excludedPackages)) return candidate;
        }
        return null;
    }

    private String extractRecentPackage(String line) {
        if (line.startsWith("realActivity=") || line.startsWith("origActivity=") || line.startsWith("affinity=")) {
            return normalizePackageToken(line.substring(line.indexOf('=') + 1));
        }
        int cmpIndex = line.indexOf("cmp=");
        if (cmpIndex != -1) return normalizePackageToken(line.substring(cmpIndex + 4));
        int activityIndex = line.indexOf("A=");
        if (activityIndex != -1) return normalizePackageToken(line.substring(activityIndex + 2));
        return null;
    }

    private String findKillablePackageFromActivities(String output, Set<String> excludedPackages) {
        if (output == null || output.isEmpty()) return null;

        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        for (String line : output.split("\n")) {
            for (String part : line.trim().split("\\s+")) {
                if (!part.contains("/")) continue;
                String candidate = normalizePackageToken(part);
                if (candidate != null) candidates.add(candidate);
            }
        }
        for (String candidate : candidates) {
            if (isKillablePackage(candidate, excludedPackages)) return candidate;
        }
        return null;
    }

    private String normalizePackageToken(String token) {
        if (token == null || token.isEmpty()) return null;
        String normalized = token.trim();
        int spaceIdx = normalized.indexOf(' ');
        if (spaceIdx != -1) normalized = normalized.substring(0, spaceIdx);
        int braceIdx = normalized.indexOf('}');
        if (braceIdx != -1) normalized = normalized.substring(0, braceIdx);
        int slashIdx = normalized.indexOf('/');
        if (slashIdx != -1) normalized = normalized.substring(0, slashIdx);
        while (!normalized.isEmpty() && !Character.isLetterOrDigit(normalized.charAt(normalized.length() - 1))) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.contains(".") && Character.isLetter(normalized.charAt(0))) return normalized;
        return null;
    }

    private boolean isKillablePackage(String packageName, Set<String> excludedPackages) {
        return packageName != null && !excludedPackages.contains(packageName);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
