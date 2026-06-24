package com.gree1d.reappzuku.manager;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.gree1d.reappzuku.R;
import com.gree1d.reappzuku.core.AppDebugManager;
import com.gree1d.reappzuku.core.AppDebugManager.Category;
import com.gree1d.reappzuku.service.UpdateCheckWorker;
import static com.gree1d.reappzuku.core.AppConstants.*;
import static com.gree1d.reappzuku.core.PreferenceKeys.*;

import io.noties.markwon.Markwon;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class UpdateChecker {

    private static final String FILE_NAME = "UpdateChecker";

    static final String GITHUB_API_URL =
            "https://api.github.com/repos/gree1d/ReAppzuku/releases/latest";
    private static final String RELEASES_URL =
            "https://github.com/gree1d/ReAppzuku/releases";

    private static final String CHANNEL_ID = "reappzuku_updates";
    private static final int    NOTIF_ID   = 9001;

    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS    = 8_000;

    private static final String WORK_NAME           = "update_check_periodic";
    private static final long   CHECK_INTERVAL_HOURS = 12;
    private static final long   RETRY_BACKOFF_MINS   = 5;

    public static void schedulePeriodicCheck(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                UpdateCheckWorker.class,
                CHECK_INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(
                        BackoffPolicy.LINEAR,
                        RETRY_BACKOFF_MINS, TimeUnit.MINUTES)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request);

        AppDebugManager.d(Category.UTILS, FILE_NAME + ": Periodic update check scheduled (interval=" +
                CHECK_INTERVAL_HOURS + "h, backoff=" + RETRY_BACKOFF_MINS + "m)");
    }

    public static void checkForUpdatesManual(Context context, SharedPreferences prefs) {
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
                    showUpdateDialog(context, info, prefs);
                } else {
                    showToast(context, context.getString(R.string.update_up_to_date, currentVersion));
                }
            });
        });
    }

    public static ReleaseInfo fetchLatestRelease() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(GITHUB_API_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);

            int code = conn.getResponseCode();
            if (code != 200) {
                AppDebugManager.w(Category.UTILS, FILE_NAME + ": GitHub API returned HTTP " + code);
                return null;
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            JSONObject json = new JSONObject(sb.toString());
            String tagName  = json.optString("tag_name", "").replaceFirst("^v", "");
            String body     = json.optString("body", "");
            String htmlUrl  = json.optString("html_url", RELEASES_URL);

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

        } catch (UnknownHostException e) {
            AppDebugManager.w(Category.UTILS, FILE_NAME + ": No route to GitHub (DNS failed): " + e.getMessage());
            return null;
        } catch (SocketTimeoutException e) {
            AppDebugManager.w(Category.UTILS, FILE_NAME + ": GitHub API timed out: " + e.getMessage());
            return null;
        } catch (IOException e) {
            AppDebugManager.w(Category.UTILS, FILE_NAME + ": Network I/O error fetching release: " + e.getMessage());
            return null;
        } catch (Exception e) {
            AppDebugManager.e(Category.UTILS, FILE_NAME + ": Unexpected error fetching release info", e);
            return null;
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Exception ignored) {}
            }
        }
    }

    public static boolean isNewer(String remote, String local) {
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
            return false;
        } catch (Exception e) {
            AppDebugManager.w(Category.UTILS, FILE_NAME + ": Version parse failed: remote=" + remote + " local=" + local);
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

    public static String getAppVersion(Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            AppDebugManager.w(Category.UTILS, FILE_NAME + ": getAppVersion: own package not found, using 0.0.0 fallback", e);
            return "0.0.0";
        }
    }

    public static void postUpdateNotification(Context context, ReleaseInfo info) {
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                AppDebugManager.w(Category.UTILS, FILE_NAME + ": POST_NOTIFICATIONS not granted, skipping update notification");
                return;
            }
        }

        nm.notify(NOTIF_ID, builder.build());
    }

    private static void showUpdateDialog(Context context, ReleaseInfo info, SharedPreferences prefs) {
        String bodyMd = info.changelog.isEmpty()
                ? "Version " + info.tagName + " is available."
                : "Version " + info.tagName + " is available.\n\n" + stripGitHubAlerts(info.changelog);

        Markwon markwon = Markwon.create(context);
        TextView messageView = new TextView(context);
        int px16 = (int) (16 * context.getResources().getDisplayMetrics().density);
        int px8  = (int) (8  * context.getResources().getDisplayMetrics().density);
        messageView.setPadding(px16, px8, px16, px8);
        messageView.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        try {
            markwon.setMarkdown(messageView, bodyMd);
        } catch (Exception e) {
            AppDebugManager.w(Category.UTILS, FILE_NAME + ": Markwon rendering failed, falling back to plain text", e);
            messageView.setText(bodyMd);
        }

        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(messageView);

        int accent = prefs.getInt(KEY_ACCENT, ACCENT_SYSTEM);
        boolean isCustomAccent = accent == ACCENT_CUSTOM;
        int onColor = prefs.getInt(KEY_ACCENT_ON_COLOR, ACCENT_ON_WHITE);
        int btnColor = isCustomAccent
                ? ((onColor == ACCENT_ON_BLACK) ? android.graphics.Color.BLACK : android.graphics.Color.WHITE)
                : ContextCompat.getColor(context, R.color.dialog_button_text);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.update_dialog_title))
                .setView(scrollView)
                .setPositiveButton(context.getString(R.string.update_dialog_download), (d, w) -> {
                    d.dismiss();
                    try {
                        context.startActivity(new Intent(
                                Intent.ACTION_VIEW, Uri.parse(info.releasePageUrl)));
                    } catch (Exception e) {
                        AppDebugManager.e(Category.UTILS, FILE_NAME + ": Failed to open release page URL", e);
                    }
                })
                .setNegativeButton(context.getString(R.string.update_dialog_close), null)
                .create();

        dialog.show();

        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(btnColor);
        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(btnColor);
    }

    private static void showToast(Context context, String message) {
        new Handler(Looper.getMainLooper()).post(() ->
                android.widget.Toast.makeText(context, message,
                        android.widget.Toast.LENGTH_SHORT).show());
    }

    private static String stripGitHubAlerts(String markdown) {
        if (markdown == null) return "";
        return markdown
                .replaceAll("(?m)^> \\[!(NOTE|WARNING|TIP|IMPORTANT|CAUTION)][ \\t]*\\r?\\n?", "")
                .replaceAll("(?m)^> ?", "")
                .replaceAll("\\n{3,}", "\\n\\n")
                .trim();
    }

    public static class ReleaseInfo {
        public final String tagName;
        public final String changelog;
        public final String downloadUrl;
        public final String releasePageUrl;

        ReleaseInfo(String tagName, String changelog, String downloadUrl, String releasePageUrl) {
            this.tagName        = tagName;
            this.changelog      = changelog;
            this.downloadUrl    = downloadUrl;
            this.releasePageUrl = releasePageUrl;
        }
    }
}
