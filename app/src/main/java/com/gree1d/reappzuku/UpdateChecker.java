package com.gree1d.reappzuku;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles GitHub release update checking for ReAppzuku.
 *
 * Auto-check: once per day via {@link #checkForUpdatesAuto(Context)}.
 *   - Silently skipped if no internet.
 *   - Posts a notification if a new version is found.
 *
 * Manual check: triggered by the "Check for updates" button.
 *   - Shows "No internet connection" if offline.
 *   - Shows a changelog dialog with "Close" / "Download" if a new version is found.
 *   - Shows "You're up to date" toast if already on latest.
 */
public class UpdateChecker {

    private static final String TAG = "UpdateChecker";

    // ── GitHub config ──────────────────────────────────────────────────────────
    private static final String GITHUB_API_URL =
            "https://api.github.com/repos/gree1d/ReAppzuku/releases/latest";
    private static final String RELEASES_URL =
            "https://github.com/gree1d/ReAppzuku/releases";

    // ── Notification ───────────────────────────────────────────────────────────
    private static final String CHANNEL_ID   = "reappzuku_updates";
    private static final int    NOTIF_ID     = 9001;

    // ── Prefs for auto-check throttle ──────────────────────────────────────────
    private static final String PREFS_NAME         = "update_checker_prefs";
    private static final String KEY_LAST_CHECK_MS  = "last_check_ms";
    private static final long   CHECK_INTERVAL_MS  = 24 * 60 * 60 * 1000L; // 1 day

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Perform a background auto-check (once per 24 h, silently skipped if offline).
     * Call from e.g. {@code ShappkyService.onCreate()} or a WorkManager task.
     */
    public static void checkForUpdatesAuto(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastCheck = prefs.getLong(KEY_LAST_CHECK_MS, 0);
        if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) {
            Log.d(TAG, "Auto-check skipped: within throttle window");
            return;
        }

        if (!isConnected(context)) {
            Log.d(TAG, "Auto-check skipped: no internet");
            return; // silent fallback
        }

        prefs.edit().putLong(KEY_LAST_CHECK_MS, System.currentTimeMillis()).apply();

        ExecutorService exec = Executors.newSingleThreadExecutor();
        exec.execute(() -> {
            ReleaseInfo info = fetchLatestRelease();
            if (info == null) return;

            String currentVersion = getAppVersion(context);
            if (isNewer(info.tagName, currentVersion)) {
                Log.i(TAG, "Auto-check: new version found: " + info.tagName);
                postUpdateNotification(context, info);
            } else {
                Log.d(TAG, "Auto-check: already up to date (" + currentVersion + ")");
            }
        });
    }

    /**
     * Perform a manual check triggered by a UI button.
     * Must be called from the main thread; {@code context} should be an Activity.
     */
    public static void checkForUpdatesManual(Context context) {
        if (!isConnected(context)) {
            showToast(context, "No internet");
            return;
        }

        ExecutorService exec = Executors.newSingleThreadExecutor();
        Handler main = new Handler(Looper.getMainLooper());

        exec.execute(() -> {
            ReleaseInfo info = fetchLatestRelease();
            String currentVersion = getAppVersion(context);

            main.post(() -> {
                if (info == null) {
                    showToast(context, context.getString(R.string.update_check_failed));
                    return;
                }
                if (isNewer(info.tagName, currentVersion)) {
                    showUpdateDialog(context, info);
                } else {
                    showToast(context, context.getString(R.string.update_up_to_date, currentVersion));
                }
            });
        });
    }

    // ── Internal helpers ───────────────────────────────────────────────────────

    /** Fetches the latest GitHub release via the REST API. Returns null on error. */
    private static ReleaseInfo fetchLatestRelease() {
        try {
            URL url = new URL(GITHUB_API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);

            int code = conn.getResponseCode();
            if (code != 200) {
                Log.w(TAG, "GitHub API returned HTTP " + code);
                return null;
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            JSONObject json = new JSONObject(sb.toString());
            String tagName = json.optString("tag_name", "").replaceFirst("^v", "");
            String body    = json.optString("body", "");
            String htmlUrl = json.optString("html_url", RELEASES_URL);

            // Collect APK download URL from assets (first .apk asset if present)
            String downloadUrl = htmlUrl;
            JSONArray assets = json.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    String name = asset.optString("name", "");
                    if (name.endsWith(".apk")) {
                        downloadUrl = asset.optString("browser_download_url", htmlUrl);
                        break;
                    }
                }
            }

            return new ReleaseInfo(tagName, body.trim(), downloadUrl, htmlUrl);

        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch release info", e);
            return null;
        }
    }

    /**
     * Compares two semantic version strings (e.g. "1.5.0" > "1.4.0").
     * Returns true if {@code remote} is strictly newer than {@code local}.
     */
    static boolean isNewer(String remote, String local) {
        if (remote == null || remote.isEmpty()) return false;
        try {
            int[] r = parseVersion(remote.replaceFirst("^v", ""));
            int[] l = parseVersion(local.replaceFirst("^v", ""));
            for (int i = 0; i < Math.max(r.length, l.length); i++) {
                int rv = i < r.length ? r[i] : 0;
                int lv = i < l.length ? l[i] : 0;
                if (rv > lv) return true;
                if (rv < lv) return false;
            }
            return false; // equal
        } catch (Exception e) {
            Log.w(TAG, "Version parse failed: remote=" + remote + " local=" + local);
            return false;
        }
    }

    private static int[] parseVersion(String v) {
        String[] parts = v.split("\\.");
        int[] nums = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            nums[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
        }
        return nums;
    }

    private static String getAppVersion(Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "0.0.0";
        }
    }

    private static boolean isConnected(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    private static void showToast(Context context, String message) {
        new Handler(Looper.getMainLooper()).post(() ->
                android.widget.Toast.makeText(context, message,
                        android.widget.Toast.LENGTH_SHORT).show());
    }

    // ── Notification ───────────────────────────────────────────────────────────

    private static void postUpdateNotification(Context context, ReleaseInfo info) {
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "App Updates",
                    NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Notifications about new ReAppzuku releases");
            nm.createNotificationChannel(ch);
        }

        Intent openIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl));
        PendingIntent pi = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_shappky)
                .setContentTitle("ReAppzuku " + info.tagName + " available")
                .setContentText("Tap to download the latest release")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(info.changelog.isEmpty()
                                ? "Tap to download the latest release"
                                : info.changelog))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        // Fallback: on Android 13+ silently skip if POST_NOTIFICATIONS was not granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "POST_NOTIFICATIONS not granted, skipping update notification");
                return;
            }
        }

        nm.notify(NOTIF_ID, builder.build());
    }

    // ── Update dialog (manual check) ───────────────────────────────────────────

    private static void showUpdateDialog(Context context, ReleaseInfo info) {
        String message = info.changelog.isEmpty()
                ? "Version " + info.tagName + " is available."
                : "Version " + info.tagName + " is available.\n\n" + info.changelog;

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.update_dialog_title))
                .setMessage(message)
                .setPositiveButton(context.getString(R.string.update_dialog_download), (d, w) -> {
                    d.dismiss();
                    try {
                        context.startActivity(new Intent(
                                Intent.ACTION_VIEW, Uri.parse(info.releasePageUrl)));
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to open release page URL", e);
                    }
                })
                .setNegativeButton(context.getString(R.string.update_dialog_close), null)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(
                            ContextCompat.getColor(context, R.color.background_primary)));
        }
        dialog.show();

        int btnColor = ContextCompat.getColor(context, R.color.dialog_button_text);
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(btnColor);
        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(btnColor);
    }

    // ── Data class ─────────────────────────────────────────────────────────────

    private static class ReleaseInfo {
        final String tagName;
        final String changelog;
        final String downloadUrl;    // direct APK asset URL (fallback: release page)
        final String releasePageUrl; // always the HTML release page

        ReleaseInfo(String tagName, String changelog, String downloadUrl, String releasePageUrl) {
            this.tagName        = tagName;
            this.changelog      = changelog;
            this.downloadUrl    = downloadUrl;
            this.releasePageUrl = releasePageUrl;
        }
    }
}
