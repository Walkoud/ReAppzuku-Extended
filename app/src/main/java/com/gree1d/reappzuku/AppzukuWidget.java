package com.gree1d.reappzuku;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.widget.RemoteViews;

import com.gree1d.reappzuku.db.AppDatabase;
import com.gree1d.reappzuku.db.AppStats;
import com.gree1d.reappzuku.db.AppStatsDao;

import java.io.RandomAccessFile;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static com.gree1d.reappzuku.AppConstants.STATS_HISTORY_DURATION_MS;

public class AppzukuWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    public static void updateAllWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, AppzukuWidget.class);
        int[] ids = manager.getAppWidgetIds(component);
        if (ids == null || ids.length == 0) return;
        for (int id : ids) {
            updateAppWidget(context, manager, id);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        new Thread(() -> {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

            // --- RAM ---
            long totalRamMb = 0;
            long usedRamMb = 0;
            try (RandomAccessFile reader = new RandomAccessFile("/proc/meminfo", "r")) {
                long totalKb = Long.parseLong(reader.readLine().replaceAll("\\D+", ""));
                reader.readLine(); // MemFree — пропускаем
                long availKb = Long.parseLong(reader.readLine().replaceAll("\\D+", ""));
                totalRamMb = totalKb / 1024;
                usedRamMb  = (totalKb - availKb) / 1024;
            } catch (Exception ignored) {}

            if (totalRamMb > 0) {
                int progress = (int) (usedRamMb * 100 / totalRamMb);
                views.setProgressBar(R.id.widget_ram_progress, 100, progress, false);
                views.setTextViewText(R.id.widget_ram_label,
                        usedRamMb + " / " + totalRamMb + " MB");
            } else {
                views.setProgressBar(R.id.widget_ram_progress, 100, 0, false);
                views.setTextViewText(R.id.widget_ram_label, "RAM: —");
            }

            // --- Kill stats (12h) ---
            int totalKills = 0;
            long totalRecoveredKb = 0;
            long lastKillTime = 0;
            try {
                AppStatsDao dao = AppDatabase.getInstance(context).appStatsDao();
                long since = System.currentTimeMillis() - STATS_HISTORY_DURATION_MS;
                List<AppStats> statsList = dao.getAllStatsSince(since);
                for (AppStats s : statsList) {
                    totalKills      += s.killCount;
                    totalRecoveredKb += s.totalRecoveredKb;
                    if (s.lastKillTime > lastKillTime) lastKillTime = s.lastKillTime;
                }
            } catch (Exception ignored) {}

            views.setTextViewText(R.id.widget_kills_text,
                    context.getString(R.string.widget_kills, totalKills));

            views.setTextViewText(R.id.widget_freed_text,
                    formatRecoveredSize(context, totalRecoveredKb));

            if (lastKillTime > 0) {
                DateFormat fmt = android.text.format.DateFormat.getTimeFormat(context);
                views.setTextViewText(R.id.widget_last_kill_text, fmt.format(new Date(lastKillTime)));
            } else {
                views.setTextViewText(R.id.widget_last_kill_text,
                        context.getString(R.string.widget_no_kills));
            }

            appWidgetManager.updateAppWidget(appWidgetId, views);
        }).start();
    }

    private static String formatRecoveredSize(Context context, long kb) {
        if (kb <= 0)   return context.getString(R.string.widget_freed, "0 MB");
        if (kb < 1024) return context.getString(R.string.widget_freed,
                String.format(Locale.US, "%d KB", kb));
        if (kb < 1024 * 1024) return context.getString(R.string.widget_freed,
                String.format(Locale.US, "%.1f MB", kb / 1024f));
        return context.getString(R.string.widget_freed,
                String.format(Locale.US, "%.2f GB", kb / (1024f * 1024f)));
    }
}
