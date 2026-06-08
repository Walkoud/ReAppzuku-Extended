package com.gree1d.reappzuku;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import java.io.RandomAccessFile;
import java.util.Collections;
import java.util.Locale;

public class RamKillShortcutManager {

    private static final String TAG = "RamKillShortcutManager";
    private static final String SHORTCUT_ID = "ram_kill_shortcut";
    private static final int ICON_SIZE = 108;
    private static final int CORNER_RADIUS = 24;

    private final Context context;

    public RamKillShortcutManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public void requestPinShortcut() {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            Log.w(TAG, "Launcher does not support pinned shortcuts");
            return;
        }

        RamInfo info = readRamInfo();
        ShortcutInfoCompat shortcut = buildShortcut(info);

        Intent callbackIntent = new Intent(context, ShappkyService.class);
        callbackIntent.setAction("SHORTCUT_PINNED");
        PendingIntent callback = PendingIntent.getService(
                context, 0, callbackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        ShortcutManagerCompat.requestPinShortcut(context, shortcut, callback.getIntentSender());
    }

    public void updateShortcut() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return;

        ShortcutManager sm = context.getSystemService(ShortcutManager.class);
        if (sm == null) return;

        boolean isPinned = false;
        for (ShortcutInfo s : sm.getPinnedShortcuts()) {
            if (SHORTCUT_ID.equals(s.getId())) {
                isPinned = true;
                break;
            }
        }
        if (!isPinned) return;

        RamInfo info = readRamInfo();
        ShortcutInfoCompat shortcut = buildShortcut(info);
        ShortcutManagerCompat.updateShortcuts(context, Collections.singletonList(shortcut));
    }

    private ShortcutInfoCompat buildShortcut(RamInfo info) {
        Bitmap icon = buildIcon(info);

        Intent killIntent = new Intent(context, ShappkyService.class);
        killIntent.setAction("WIDGET_KILL");

        return new ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
                .setShortLabel(formatLabel(info))
                .setLongLabel(formatLabel(info))
                .setIcon(IconCompat.createWithBitmap(icon))
                .setIntent(killIntent)
                .build();
    }

    private Bitmap buildIcon(RamInfo info) {
        Bitmap bmp = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(resolveBackgroundColor());
        RectF rect = new RectF(0, 0, ICON_SIZE, ICON_SIZE);
        canvas.drawRoundRect(rect, CORNER_RADIUS, CORNER_RADIUS, bgPaint);

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(resolveTextColor());
        textPaint.setTextAlign(Paint.Align.CENTER);

        String percentText = info != null ? info.percent + "%" : "—";

        textPaint.setTextSize(32f);
        textPaint.setFakeBoldText(true);
        float cx = ICON_SIZE / 2f;
        float cy = ICON_SIZE / 2f - 6f;
        canvas.drawText(percentText, cx, cy - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint);

        return bmp;
    }

    private int resolveBackgroundColor() {
        int nightMode = context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
                ? Color.BLACK : Color.WHITE;
    }

    private int resolveTextColor() {
        int nightMode = context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
                ? Color.WHITE : Color.BLACK;
    }

    private String formatLabel(RamInfo info) {
        if (info == null) return "—";
        String unit = Locale.getDefault().getLanguage().equals("ru") ? "ГБ" : "GB";
        String used = String.format(Locale.getDefault(), "%.1f", info.usedKb / (1024f * 1024f));
        String total = String.format(Locale.getDefault(), "%.1f", info.totalKb / (1024f * 1024f));
        return used + "/" + total + " " + unit;
    }

    private RamInfo readRamInfo() {
        try (RandomAccessFile reader = new RandomAccessFile("/proc/meminfo", "r")) {
            long totalKb = 0;
            long availKb = 0;
            String line;
            int linesRead = 0;
            while (linesRead < 3 && (line = reader.readLine()) != null) {
                if (line.startsWith("MemTotal")) totalKb = parseMemValue(line);
                else if (line.startsWith("MemAvailable")) availKb = parseMemValue(line);
                linesRead++;
            }
            if (totalKb > 0) {
                long usedKb = totalKb - availKb;
                int percent = (int) (usedKb * 100 / totalKb);
                return new RamInfo(usedKb, totalKb, percent);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to read RAM info", e);
        }
        return null;
    }

    private long parseMemValue(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length >= 2) {
            try { return Long.parseLong(parts[1]); } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private static class RamInfo {
        final long usedKb;
        final long totalKb;
        final int percent;

        RamInfo(long usedKb, long totalKb, int percent) {
            this.usedKb = usedKb;
            this.totalKb = totalKb;
            this.percent = percent;
        }
    }
}
