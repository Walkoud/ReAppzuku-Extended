package com.gree1d.reappzuku.ui;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.gree1d.reappzuku.R;
import com.gree1d.reappzuku.core.AppDebugManager;
import com.gree1d.reappzuku.core.AppDebugManager.Category;
import com.gree1d.reappzuku.manager.BackgroundAppManager;
import com.gree1d.reappzuku.manager.RestrictionsScheduler;
import com.gree1d.reappzuku.utils.BackgroundRestrictionLog;
import com.gree1d.reappzuku.utils.SleepModeLogManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static com.gree1d.reappzuku.core.AppConstants.*;
import static com.gree1d.reappzuku.core.PreferenceKeys.*;


class StatisticsActivityDialogs {

    private static final String FILE = "StatisticsActivityDialogs";
    private static final int TOP_OFFENDERS_LIMIT = 50;
    private static final long[] TOP_OFFENDER_FILTER_WINDOWS_MS = {
            STATS_HISTORY_DURATION_MS,
            24 * 60 * 60 * 1000L,
            7 * 24 * 60 * 60 * 1000L,
            -1L
    };


    private final StatisticsActivity activity;
    private final BackgroundAppManager appManager;
    private final String[] topOffenderFilterLabels;

    StatisticsActivityDialogs(StatisticsActivity activity,
                               BackgroundAppManager appManager,
                               String[] topOffenderFilterLabels) {
        this.activity = activity;
        this.appManager = appManager;
        this.topOffenderFilterLabels = topOffenderFilterLabels;
    }


    void showStatsDialog() {
        AppDebugManager.d(Category.STATISTICS_PAGE, FILE + ": showStatsDialog");
        SettingsListContent content = createSettingsListContent(
                activity.getString(R.string.stats_no_activity_12h), false);
        SettingsSurfaceAdapter adapter = new SettingsSurfaceAdapter();
        content.listView.setAdapter(adapter);
        content.listView.setEmptyView(content.emptyView);
        content.loading.setVisibility(View.VISIBLE);
        content.listView.setVisibility(View.GONE);
        content.summaryText.setText(activity.getString(R.string.stats_loading));

        AlertDialog dialog = createSettingsSurfaceDialog(
                activity.getString(R.string.settings_kill_history_title),
                activity.getString(R.string.stats_dialog_subtitle),
                content.rootView);
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, activity.getString(R.string.dialog_close), (d, w) -> d.dismiss());
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, activity.getString(R.string.settings_restriction_log_clear), (d, w) -> {});
        dialog.show();
        applyCustomAccentToDialogButtons(dialog);

        final List<KillHistoryEntry> sortedEntries = new ArrayList<>();

        Runnable reloadStats = () -> activity.executor.execute(() -> {
            long twelveHoursAgo = System.currentTimeMillis() - STATS_HISTORY_DURATION_MS;
            com.gree1d.reappzuku.db.AppStatsDao appStatsDao = com.gree1d.reappzuku.db.AppDatabase
                    .getInstance(activity).appStatsDao();
            java.util.List<com.gree1d.reappzuku.db.AppStatsAggregate> statsList =
                    appStatsDao.getAllStatsSince(twelveHoursAgo);
            AppDebugManager.d(Category.STATISTICS_PAGE, FILE + ": showStatsDialog loaded " + statsList.size()
                    + " stats rows since 12h ago");

            List<KillHistoryEntry> historyEntries = new ArrayList<>();
            int totalKills = 0;
            int totalRelaunches = 0;
            long totalRecoveredKb = 0;
            java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(activity);

            for (com.gree1d.reappzuku.db.AppStatsAggregate stats : statsList) {
                if (stats == null || stats.packageName == null) continue;
                if (stats.killCount <= 0 && stats.relaunchCount <= 0) continue;

                List<String> detailParts = new ArrayList<>();
                if (stats.killCount > 0) {
                    detailParts.add(activity.getString(R.string.stats_kill_detail, stats.killCount));
                }
                if (stats.relaunchCount > 0) {
                    String relaunchDetail = activity.getString(R.string.stats_relaunch_detail, stats.relaunchCount);
                    if (stats.lastRelaunchTime > 0) {
                        relaunchDetail += activity.getString(R.string.stats_last_relaunch_time,
                                timeFormat.format(new java.util.Date(stats.lastRelaunchTime)));
                    }
                    detailParts.add(relaunchDetail);
                }
                if (stats.totalRecoveredKb > 0) {
                    detailParts.add(activity.getString(R.string.stats_recovered_ram,
                            formatRecoveredSize(stats.totalRecoveredKb)));
                }

                long lastEventTime = Math.max(stats.lastKillTime, stats.lastRelaunchTime);
                String badge = lastEventTime > 0 ? timeFormat.format(new java.util.Date(lastEventTime)) : "";
                historyEntries.add(new KillHistoryEntry(
                        resolveAggregateAppName(stats, appStatsDao),
                        stats.packageName,
                        String.join(" | ", detailParts),
                        badge,
                        lastEventTime,
                        stats.lastKillSource));
                totalKills += stats.killCount;
                totalRelaunches += stats.relaunchCount;
                totalRecoveredKb += stats.totalRecoveredKb;
            }

            Collections.sort(historyEntries, (a, b) -> Long.compare(b.lastEventTime, a.lastEventTime));
            final List<KillHistoryEntry> loaded = new ArrayList<>(historyEntries);
            List<SettingsSurfaceRow> rows = buildKillHistoryRows(loaded);
            final String summary = activity.getString(R.string.stats_summary_12h,
                    rows.size(), totalKills, totalRelaunches, formatRecoveredSize(totalRecoveredKb));

            activity.handler.post(() -> {
                sortedEntries.clear();
                sortedEntries.addAll(loaded);
                adapter.setItems(rows);
                content.summaryText.setText(summary);
                content.loading.setVisibility(View.GONE);
                content.listView.setVisibility(View.VISIBLE);
                content.emptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
                content.listView.setOnItemClickListener((parent, view, position, id) -> {
                    if (position < sortedEntries.size()) {
                        showKillEntryDetails(sortedEntries.get(position));
                    }
                });
            });
        });

        reloadStats.run();

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            AppDebugManager.i(Category.STATISTICS_PAGE, FILE + ": kill history stats cleared by user");
            long sinceTime = System.currentTimeMillis() - STATS_HISTORY_DURATION_MS;
            activity.executor.execute(() -> {
                com.gree1d.reappzuku.db.AppDatabase.getInstance(activity).appStatsDao().deleteStatsSince(sinceTime);
                activity.handler.post(reloadStats);
            });
        });
    }

    void showTopOffendersDialog() {
        AppDebugManager.d(Category.STATISTICS_PAGE, FILE + ": showTopOffendersDialog");
        SettingsListContent content = createSettingsListContent(
                activity.getString(R.string.stats_top_offenders_empty), true);
        TextView summaryText = content.summaryText;
        ProgressBar loading = content.loading;
        ListView listView = content.listView;
        TextView emptyView = content.emptyView;

        SettingsSurfaceAdapter offendersAdapter = new SettingsSurfaceAdapter();
        listView.setAdapter(offendersAdapter);
        listView.setEmptyView(emptyView);

        ArrayAdapter<String> filterAdapter = new ArrayAdapter<>(
                activity, android.R.layout.simple_dropdown_item_1line, topOffenderFilterLabels);
        content.filterSpinner.setAdapter(filterAdapter);
        content.filterSpinner.setText(topOffenderFilterLabels[0], false);

        AlertDialog dialog = createSettingsSurfaceDialog(
                activity.getString(R.string.settings_top_offenders_title),
                activity.getString(R.string.stats_top_offenders_dialog_subtitle),
                content.rootView);
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, activity.getString(R.string.dialog_close), (d, w) -> d.dismiss());
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, activity.getString(R.string.stats_top_offenders_reset), (d, w) -> {});
        dialog.show();
        applyCustomAccentToDialogButtons(dialog);

        final int[] currentFilterIndex = {0};

        loadTopOffenders(0, offendersAdapter, summaryText, loading, listView, emptyView);

        content.filterSpinner.setOnItemClickListener((parent, view, position, id) -> {
            currentFilterIndex[0] = position;
            content.filterSpinner.setText(topOffenderFilterLabels[position], false);
            loadTopOffenders(position, offendersAdapter, summaryText, loading, listView, emptyView);
        });

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            AppDebugManager.i(Category.STATISTICS_PAGE, FILE + ": top offenders stats cleared by user");
            long windowMs = TOP_OFFENDER_FILTER_WINDOWS_MS[currentFilterIndex[0]];
            long sinceTime = windowMs > 0 ? System.currentTimeMillis() - windowMs : 0L;
            activity.executor.execute(() -> {
                com.gree1d.reappzuku.db.AppStatsDao dao =
                        com.gree1d.reappzuku.db.AppDatabase.getInstance(activity).appStatsDao();
                if (sinceTime > 0) dao.deleteStatsSince(sinceTime);
                else               dao.deleteAll();
                activity.handler.post(() -> loadTopOffenders(currentFilterIndex[0], offendersAdapter,
                        summaryText, loading, listView, emptyView));
            });
        });
    }

    void showBackgroundRestrictionLogDialog() {
        AppDebugManager.d(Category.STATISTICS_PAGE, FILE + ": showBackgroundRestrictionLogDialog");
        SettingsListContent content = createSettingsListContent(
                activity.getString(R.string.settings_restriction_log_empty), false);
        SettingsSurfaceAdapter adapter = new SettingsSurfaceAdapter();
        final List<BackgroundRestrictionLog.LogEntry> logEntryRef = new ArrayList<>();
        content.listView.setAdapter(adapter);
        content.listView.setEmptyView(content.emptyView);
        content.listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < logEntryRef.size()) {
                showRestrictionLogEntryDetails(logEntryRef.get(position));
            }
        });
        content.loading.setVisibility(View.VISIBLE);
        content.listView.setVisibility(View.GONE);
        content.summaryText.setText(activity.getString(R.string.stats_loading));

        AlertDialog dialog = createSettingsSurfaceDialog(
                activity.getString(R.string.settings_restriction_log_title),
                activity.getString(R.string.settings_restriction_log_dialog_subtitle),
                content.rootView);
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, activity.getString(R.string.dialog_close), (d, w) -> d.dismiss());
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, activity.getString(R.string.settings_restriction_log_clear), (d, w) -> {});
        dialog.show();
        applyCustomAccentToDialogButtons(dialog);

        Runnable reloadLog = () -> activity.executor.execute(() -> {
            List<BackgroundRestrictionLog.LogEntry> entries = BackgroundRestrictionLog.readEntries(activity);
            AppDebugManager.d(Category.STATISTICS_PAGE, FILE + ": restriction log loaded " + entries.size() + " entries");
            List<SettingsSurfaceRow> rows = buildRestrictionLogRows(entries);
            String summary = activity.getString(R.string.settings_restriction_log_summary, rows.size());
            activity.handler.post(() -> {
                logEntryRef.clear();
                logEntryRef.addAll(entries);
                adapter.setItems(rows);
                content.summaryText.setText(summary);
                content.loading.setVisibility(View.GONE);
                content.listView.setVisibility(View.VISIBLE);
                content.emptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
        reloadLog.run();

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            activity.executor.execute(() -> {
                AppDebugManager.i(Category.STATISTICS_PAGE, FILE + ": restriction log cleared by user");
                appManager.clearBackgroundRestrictionLog();
                activity.handler.post(reloadLog);
            });
        });
    }

    void showSleepModeLogDialog() {
        AppDebugManager.d(Category.STATISTICS_PAGE, FILE + ": showSleepModeLogDialog");
        SettingsListContent content = createSettingsListContent(
                activity.getString(R.string.log_sleep_mode_empty), false);
        SettingsSurfaceAdapter adapter = new SettingsSurfaceAdapter();
        content.listView.setAdapter(adapter);
        content.listView.setEmptyView(content.emptyView);
        content.listView.setOnItemClickListener((parent, view, position, id) -> {
            SettingsSurfaceRow row = adapter.getItem(position);
            if (row != null && row.packageName != null && row.packageName.contains(".")) {
                openAppInfo(row.packageName);
            }
        });
        content.loading.setVisibility(View.VISIBLE);
        content.listView.setVisibility(View.GONE);
        content.summaryText.setText(activity.getString(R.string.stats_loading));

        AlertDialog dialog = createSettingsSurfaceDialog(
                activity.getString(R.string.log_sleep_mode_title),
                activity.getString(R.string.log_sleep_mode_dialog_subtitle),
                content.rootView);
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, activity.getString(R.string.dialog_close), (d, w) -> d.dismiss());
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, activity.getString(R.string.settings_restriction_log_clear), (d, w) -> {});
        dialog.show();
        applyCustomAccentToDialogButtons(dialog);

        Runnable reloadLog = () -> activity.executor.execute(() -> {
            List<SleepModeLogManager.LogEntry> entries = SleepModeLogManager.readEntries(activity);
            AppDebugManager.d(Category.STATISTICS_PAGE, FILE + ": sleep mode log loaded " + entries.size() + " entries");
            List<SettingsSurfaceRow> rows = buildSleepModeLogRows(entries);
            String summary = activity.getString(R.string.settings_restriction_log_summary, rows.size());
            activity.handler.post(() -> {
                adapter.setItems(rows);
                content.summaryText.setText(summary);
                content.loading.setVisibility(View.GONE);
                content.listView.setVisibility(View.VISIBLE);
                content.emptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
        reloadLog.run();

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            activity.executor.execute(() -> {
                AppDebugManager.i(Category.STATISTICS_PAGE, FILE + ": sleep mode log cleared by user");
                SleepModeLogManager.clear(activity);
                activity.handler.post(reloadLog);
            });
        });
    }

    void showSchedulerLogDialog() {
        AppDebugManager.d(Category.STATISTICS_PAGE, FILE + ": showSchedulerLogDialog");
        SettingsListContent content = createSettingsListContent(
                activity.getString(R.string.log_scheduler_empty), false);
        SettingsSurfaceAdapter adapter = new SettingsSurfaceAdapter();
        content.listView.setAdapter(adapter);
        content.listView.setEmptyView(content.emptyView);
        content.listView.setOnItemClickListener((parent, view, position, id) -> {
            SettingsSurfaceRow row = adapter.getItem(position);
            if (row != null && row.packageName != null && row.packageName.contains(".")) {
                openAppInfo(row.packageName);
            }
        });
        content.loading.setVisibility(View.VISIBLE);
        content.listView.setVisibility(View.GONE);
        content.summaryText.setText(activity.getString(R.string.stats_loading));

        AlertDialog dialog = createSettingsSurfaceDialog(
                activity.getString(R.string.log_scheduler_title),
                activity.getString(R.string.log_scheduler_dialog_subtitle),
                content.rootView);
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, activity.getString(R.string.dialog_close), (d, w) -> d.dismiss());
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, activity.getString(R.string.settings_restriction_log_clear), (d, w) -> {});
        dialog.show();
        applyCustomAccentToDialogButtons(dialog);

        Runnable reloadLog = () -> activity.executor.execute(() -> {
            List<RestrictionsScheduler.SchedulerLog.Entry> entries =
                    RestrictionsScheduler.SchedulerLog.readEntries(activity);
            AppDebugManager.d(Category.STATISTICS_PAGE, FILE + ": scheduler log loaded " + entries.size() + " entries");
            List<SettingsSurfaceRow> rows = buildSchedulerLogRows(entries);
            String summary = activity.getString(R.string.settings_restriction_log_summary, rows.size());
            activity.handler.post(() -> {
                adapter.setItems(rows);
                content.summaryText.setText(summary);
                content.loading.setVisibility(View.GONE);
                content.listView.setVisibility(View.VISIBLE);
                content.emptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
        reloadLog.run();

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            activity.executor.execute(() -> {
                AppDebugManager.i(Category.STATISTICS_PAGE, FILE + ": scheduler log cleared by user");
                RestrictionsScheduler.SchedulerLog.clear(activity);
                activity.handler.post(reloadLog);
            });
        });
    }

    void showOthersDialog(List<com.gree1d.reappzuku.manager.CollectStatsManager.AppResourceStats> others,
                          StatisticsActivity.ChartMetric metric, double total) {
        StringBuilder sb = new StringBuilder();
        for (com.gree1d.reappzuku.manager.CollectStatsManager.AppResourceStats s : others) {
            sb.append(String.format(Locale.US, "• %s  %.1f%%\n",
                    s.appName, activity.metricValue(s, metric) / total * 100));
        }
        applyCustomAccentToDialogButtons(new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.chart_others_dialog_title))
                .setMessage(sb.toString().trim())
                .setPositiveButton(android.R.string.ok, null)
                .show());
    }


    private void showKillEntryDetails(KillHistoryEntry entry) {
        showKillDetailDialog(entry.appName, entry.packageName, STATS_HISTORY_DURATION_MS);
    }

    private void showKillDetailDialog(String appName, String packageName, long windowMs) {
        activity.executor.execute(() -> {
            long since = windowMs > 0 ? System.currentTimeMillis() - windowMs : 0L;
            com.gree1d.reappzuku.db.AppStatsDao dao =
                    com.gree1d.reappzuku.db.AppDatabase.getInstance(activity).appStatsDao();
            List<com.gree1d.reappzuku.db.AppStats> kills = dao.getKillsSince(packageName, since);

            java.text.DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(activity);
            java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(activity);

            activity.handler.post(() -> {
                float dp = activity.getResources().getDisplayMetrics().density;

                android.widget.ScrollView scrollView = new android.widget.ScrollView(activity);
                android.widget.LinearLayout container = new android.widget.LinearLayout(activity);
                container.setOrientation(android.widget.LinearLayout.VERTICAL);
                int padH = Math.round(16 * dp);
                int padV = Math.round(8 * dp);
                container.setPadding(padH, padV, padH, padV);
                scrollView.addView(container);

                List<com.gree1d.reappzuku.db.AppStats> validKills = new ArrayList<>();
                for (com.gree1d.reappzuku.db.AppStats kill : kills) {
                    if (kill.lastKillTime > 0) validKills.add(kill);
                }

                if (validKills.isEmpty()) {
                    TextView empty = new TextView(activity);
                    empty.setText("—");
                    empty.setTextSize(14f);
                    empty.setPadding(0, Math.round(8 * dp), 0, Math.round(8 * dp));
                    empty.setTextColor(ContextCompat.getColor(activity, R.color.text_primary));
                    container.addView(empty);
                } else {
                    int accentRaw = activity.prefs().getInt(KEY_ACCENT, ACCENT_SYSTEM);
                    int badgeColor = accentRaw == ACCENT_CUSTOM
                            ? activity.prefs().getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR)
                            : com.google.android.material.color.MaterialColors.getColor(
                                    container, com.google.android.material.R.attr.colorSecondary);

                    for (int i = 0; i < validKills.size(); i++) {
                        com.gree1d.reappzuku.db.AppStats kill = validKills.get(i);
                        java.util.Date d = new java.util.Date(kill.lastKillTime);
                        String source = kill.lastKillSource != null && !kill.lastKillSource.isEmpty()
                                ? kill.lastKillSource : "—";

                        android.widget.LinearLayout row = new android.widget.LinearLayout(activity);
                        row.setOrientation(android.widget.LinearLayout.VERTICAL);
                        android.widget.LinearLayout.LayoutParams rowLp =
                                new android.widget.LinearLayout.LayoutParams(
                                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                        rowLp.topMargin = Math.round(10 * dp);
                        rowLp.bottomMargin = Math.round(10 * dp);
                        row.setLayoutParams(rowLp);

                        android.widget.LinearLayout badgesRow = new android.widget.LinearLayout(activity);
                        badgesRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                        badgesRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
                        android.widget.LinearLayout.LayoutParams badgesLp =
                                new android.widget.LinearLayout.LayoutParams(
                                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                        badgesLp.bottomMargin = Math.round(5 * dp);
                        badgesRow.setLayoutParams(badgesLp);

                        badgesRow.addView(makeBadge(dateFormat.format(d), badgeColor, dp));
                        android.widget.Space space = new android.widget.Space(activity);
                        space.setLayoutParams(new android.widget.LinearLayout.LayoutParams(Math.round(6 * dp), 1));
                        badgesRow.addView(space);
                        badgesRow.addView(makeBadge(timeFormat.format(d), badgeColor, dp));

                        TextView sourceTv = new TextView(activity);
                        sourceTv.setText(source);
                        sourceTv.setTextSize(13f);
                        sourceTv.setTextColor(ContextCompat.getColor(activity, R.color.text_secondary));

                        row.addView(badgesRow);
                        row.addView(sourceTv);
                        container.addView(row);

                        if (i < validKills.size() - 1) {
                            View divider = new View(activity);
                            android.widget.LinearLayout.LayoutParams divLp =
                                    new android.widget.LinearLayout.LayoutParams(
                                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                            Math.round(1 * dp));
                            divider.setLayoutParams(divLp);
                            divider.setBackgroundColor(
                                    androidx.core.graphics.ColorUtils.setAlphaComponent(
                                            ContextCompat.getColor(activity, R.color.text_primary), 30));
                            container.addView(divider);
                        }
                    }
                }

                applyCustomAccentToDialogButtons(new MaterialAlertDialogBuilder(activity)
                        .setTitle(appName)
                        .setView(scrollView)
                        .setNegativeButton(activity.getString(R.string.dialog_close), (d, w) -> d.dismiss())
                        .show());
            });
        });
    }

    private void showRestrictionLogEntryDetails(BackgroundRestrictionLog.LogEntry entry) {
        StringBuilder body = new StringBuilder();
        body.append(entry.timestamp).append("\n\n");

        String actionLabel  = humanizeLogAction(entry.action);
        String outcomeLabel = humanizeLogOutcome(entry.outcome);
        body.append(activity.getString(R.string.log_detail_action)).append(": ").append(actionLabel).append("\n");
        body.append(activity.getString(R.string.log_detail_outcome)).append(": ").append(outcomeLabel).append("\n");

        String detail = entry.detail != null ? entry.detail : "";

        boolean isWatchdogBucket = "watchdog-bucket".equals(entry.action);
        boolean isWatchdog       = "watchdog".equals(entry.action);

        if (isWatchdogBucket) {
            body.append("\n");
            String was = extractDetailValue(detail, "was");
            String set = extractDetailValue(detail, "set");
            if (was != null) body.append(activity.getString(R.string.log_detail_bucket_was)).append(": ").append(bucketBucketName(was)).append("\n");
            if (set != null) body.append(activity.getString(R.string.log_detail_bucket_set)).append(": ").append(bucketBucketName(set)).append("\n");
            body.append(activity.getString(R.string.log_detail_bucket_restored)).append(": ")
                    .append("ok".equals(entry.outcome)
                            ? activity.getString(R.string.log_outcome_ok)
                            : activity.getString(R.string.log_outcome_failed))
                    .append("\n");
        } else if (isWatchdog) {
            body.append("\n");
            String missing  = extractDetailValue(detail, "missing");
            String repaired = extractDetailValue(detail, "repaired");
            if (missing  != null) body.append(activity.getString(R.string.log_detail_ops_missing)).append(": ").append(missing).append("\n");
            if (repaired != null) body.append(activity.getString(R.string.log_detail_ops_repaired)).append(": ").append(repaired).append("\n");
            List<String> repairedOps = extractOpsList(detail, "repairedOps");
            if (!repairedOps.isEmpty()) {
                body.append("\n");
                for (String op : repairedOps) body.append("  ✓ ").append(op).append("\n");
            }
            List<String> failedOps = extractOpsList(detail, "failedOps");
            if (!failedOps.isEmpty()) {
                if (repairedOps.isEmpty()) body.append("\n");
                body.append(activity.getString(R.string.log_detail_ops_failed)).append(":\n");
                for (String op : failedOps) body.append("  ✗ ").append(op).append("\n");
            }
        } else {
            boolean isSoftAction = "reapply-soft".equals(entry.action) || "restrict-soft".equals(entry.action) || "restrict".equals(entry.action);
            boolean hasOpsInfo   = !isSoftAction && detail.contains("appops=");
            if (hasOpsInfo) {
                body.append("\n");
                List<String> failedOps = extractOpsList(detail, "failedOps");
                boolean allOk     = detail.contains("appops=ok(");
                boolean allFailed = detail.contains("appops=failed(0/");
                boolean isManual  = "reapply-manual".equals(entry.action) || "restrict-manual".equals(entry.action);
                body.append(activity.getString(R.string.log_detail_appops)).append(":\n");
                if (isManual) {
                    int manualMask = appManager.getManualOpsMask(entry.packageName);
                    if (manualMask != 0) {
                        for (int i = 0; i < BackgroundAppManager.ALL_OPS.length; i++) {
                            if ((manualMask & (1 << i)) == 0) continue;
                            String op     = BackgroundAppManager.ALL_OPS[i];
                            boolean failed = failedOps.contains(op);
                            if (allOk)     body.append("  ✓ ").append(op).append("\n");
                            else if (allFailed) body.append("  ✗ ").append(op).append("\n");
                            else           body.append(failed ? "  ✗ " : "  ✓ ").append(op).append("\n");
                        }
                    } else {
                        for (String op : failedOps) body.append("  ✗ ").append(op).append("\n");
                        String appopsRaw = extractDetailValue(detail, "appops");
                        if (appopsRaw != null && !appopsRaw.isEmpty())
                            body.append("  ").append(appopsRaw).append("\n");
                    }
                } else {
                    String[] opsForAction;
                    switch (entry.action != null ? entry.action : "") {
                        case "reapply-medium":
                        case "restrict-medium": opsForAction = BackgroundAppManager.MEDIUM_OPS; break;
                        default:                opsForAction = BackgroundAppManager.ALL_OPS;    break;
                    }
                    for (String op : opsForAction) {
                        if (allOk)          body.append("  ✓ ").append(op).append("\n");
                        else if (allFailed) body.append("  ✗ ").append(op).append("\n");
                        else {
                            boolean failed = failedOps.contains(op);
                            body.append(failed ? "  ✗ " : "  ✓ ").append(op).append("\n");
                        }
                    }
                }
            }
            String forceStop = extractDetailValue(detail, "force-stop");
            String bucketVal = extractDetailValue(detail, "bucket");
            if (bucketVal  != null) body.append("\n").append(activity.getString(R.string.log_detail_bucket_set)).append(": ").append(bucketBucketName(bucketVal)).append("\n");
            if (forceStop  != null) body.append(activity.getString(R.string.log_detail_force_stop)).append(": ").append(forceStop).append("\n");
        }

        android.widget.ScrollView scrollView = new android.widget.ScrollView(activity);
        TextView textView = new TextView(activity);
        int pad = (int)(16 * activity.getResources().getDisplayMetrics().density);
        textView.setPadding(pad, pad, pad, pad);
        textView.setTextSize(13f);
        textView.setTypeface(android.graphics.Typeface.MONOSPACE);
        textView.setText(body.toString().trim());
        scrollView.addView(textView);

        String title = (entry.packageName == null || entry.packageName.equals("-"))
                ? humanizeLogAction(entry.action) : entry.packageName;

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setView(scrollView)
                .setNegativeButton(activity.getString(R.string.dialog_close), (d, w) -> d.dismiss());

        if (entry.packageName != null && entry.packageName.contains(".")) {
            builder.setPositiveButton(activity.getString(R.string.settings_open_app_info),
                    (d, w) -> openAppInfo(entry.packageName));
        }

        applyCustomAccentToDialogButtons(builder.show());
    }

    private void loadTopOffenders(int filterIndex, SettingsSurfaceAdapter adapter, TextView summaryText,
                                   ProgressBar loading, ListView listView, TextView emptyView) {
        if (filterIndex < 0 || filterIndex >= TOP_OFFENDER_FILTER_WINDOWS_MS.length) filterIndex = 0;

        final int selectedFilterIndex = filterIndex;
        loading.setVisibility(View.VISIBLE);
        listView.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        summaryText.setText(activity.getString(R.string.stats_loading));

        activity.executor.execute(() -> {
            long windowMs = TOP_OFFENDER_FILTER_WINDOWS_MS[selectedFilterIndex];
            com.gree1d.reappzuku.db.AppStatsDao appStatsDao =
                    com.gree1d.reappzuku.db.AppDatabase.getInstance(activity).appStatsDao();
            List<com.gree1d.reappzuku.db.AppStatsAggregate> stats;
            if (windowMs > 0) {
                stats = appStatsDao.getAllStatsSince(System.currentTimeMillis() - windowMs);
            } else {
                stats = appStatsDao.getAllStats();
            }

            List<TopOffender> offenders = buildTopOffenders(stats, appStatsDao);
            AppDebugManager.d(Category.STATISTICS_PAGE, FILE + ": loadTopOffenders filter=" + selectedFilterIndex
                    + " loaded " + offenders.size() + " offenders");

            int totalKills = 0, totalRelaunches = 0;
            long totalRecoveredKb = 0;
            for (TopOffender o : offenders) {
                totalKills      += o.killCount;
                totalRelaunches += o.relaunchCount;
                totalRecoveredKb += o.recoveredKb;
            }

            String summary = activity.getString(R.string.stats_top_offenders_summary,
                    topOffenderFilterLabels[selectedFilterIndex],
                    offenders.size(), totalKills, totalRelaunches,
                    formatRecoveredSize(totalRecoveredKb));

            final List<TopOffender> finalOffenders = offenders;
            activity.handler.post(() -> {
                if (activity.isFinishing() ||
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed())) return;
                adapter.setItems(buildTopOffenderRows(finalOffenders));
                summaryText.setText(summary);
                loading.setVisibility(View.GONE);
                listView.setVisibility(View.VISIBLE);
                emptyView.setVisibility(finalOffenders.isEmpty() ? View.VISIBLE : View.GONE);
                listView.setOnItemClickListener((parent, view, position, id) -> {
                    if (position < finalOffenders.size()) {
                        TopOffender o = finalOffenders.get(position);
                        showKillDetailDialog(o.appName, o.packageName, windowMs);
                    }
                });
            });
        });
    }

    private List<TopOffender> buildTopOffenders(List<com.gree1d.reappzuku.db.AppStatsAggregate> statsList,
                                                 com.gree1d.reappzuku.db.AppStatsDao appStatsDao) {
        List<TopOffender> offenders = new ArrayList<>();
        for (com.gree1d.reappzuku.db.AppStatsAggregate stats : statsList) {
            if (stats == null || stats.packageName == null) continue;
            if (stats.killCount <= 0 && stats.relaunchCount <= 0 && stats.totalRecoveredKb <= 0) continue;
            String appName = resolveAggregateAppName(stats, appStatsDao);
            double score = (stats.killCount * 1.0) + (stats.relaunchCount * 2.0) + (stats.totalRecoveredKb / 102400.0);
            offenders.add(new TopOffender(appName, stats.packageName, stats.killCount,
                    stats.relaunchCount, stats.totalRecoveredKb, score));
        }
        Collections.sort(offenders, (a, b) -> {
            int c = Double.compare(b.score, a.score);
            if (c != 0) return c;
            c = Integer.compare(b.killCount, a.killCount);
            if (c != 0) return c;
            c = Integer.compare(b.relaunchCount, a.relaunchCount);
            if (c != 0) return c;
            return Long.compare(b.recoveredKb, a.recoveredKb);
        });
        return offenders.size() > TOP_OFFENDERS_LIMIT
                ? new ArrayList<>(offenders.subList(0, TOP_OFFENDERS_LIMIT))
                : offenders;
    }


    private List<SettingsSurfaceRow> buildTopOffenderRows(List<TopOffender> offenders) {
        List<SettingsSurfaceRow> rows = new ArrayList<>();
        for (int i = 0; i < offenders.size(); i++) {
            TopOffender o = offenders.get(i);
            rows.add(new SettingsSurfaceRow("#" + (i + 1), o.appName, o.packageName,
                    activity.getString(R.string.stats_offender_metrics, o.killCount, o.relaunchCount,
                            formatRecoveredSize(o.recoveredKb)),
                    activity.getString(R.string.stats_offender_score,
                            String.format(Locale.US, "%.1f", o.score)),
                    o.packageName));
        }
        return rows;
    }

    private List<SettingsSurfaceRow> buildKillHistoryRows(List<KillHistoryEntry> entries) {
        List<SettingsSurfaceRow> rows = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            KillHistoryEntry e = entries.get(i);
            rows.add(new SettingsSurfaceRow("#" + (i + 1), e.appName, e.packageName, e.detail, e.badge, e.packageName));
        }
        return rows;
    }

    private List<SettingsSurfaceRow> buildRestrictionLogRows(List<BackgroundRestrictionLog.LogEntry> logEntries) {
        List<SettingsSurfaceRow> rows = new ArrayList<>();
        for (int i = 0; i < logEntries.size(); i++) {
            BackgroundRestrictionLog.LogEntry entry = logEntries.get(i);
            String title = (entry.packageName == null || entry.packageName.equals("-"))
                    ? humanizeLogAction(entry.action) : entry.packageName;
            String subtitle = entry.timestamp;
            if (entry.action != null && !entry.action.trim().isEmpty()) {
                subtitle = subtitle.isEmpty() ? humanizeLogAction(entry.action)
                        : subtitle + " | " + humanizeLogAction(entry.action);
            }
            String detail = humanizeLogOutcome(entry.outcome);
            String extraDetail = buildRestrictionLogRowDetail(entry);
            if (!extraDetail.isEmpty()) {
                detail = detail.isEmpty() ? extraDetail : detail + "  |  " + extraDetail;
            }
            rows.add(new SettingsSurfaceRow("#" + (i + 1), title, subtitle, detail,
                    resolveRestrictionTypeBadge(entry.action), entry.packageName));
        }
        return rows;
    }

    private String buildRestrictionLogRowDetail(BackgroundRestrictionLog.LogEntry entry) {
        String raw    = entry.detail != null ? entry.detail : "";
        if (raw.isEmpty()) return "";
        String action = entry.action != null ? entry.action : "";
        if ("watchdog".equals(action)) {
            String repaired = extractDetailValue(raw, "repaired");
            return repaired != null ? "repaired=" + repaired : "";
        }
        if ("watchdog-bucket".equals(action)) {
            String was = extractDetailValue(raw, "was");
            String set = extractDetailValue(raw, "set");
            if (was != null && set != null) return "was=" + was + " → " + set;
            return "";
        }
        if (raw.contains("appops=")) {
            String forceStop = extractDetailValue(raw, "force-stop");
            return forceStop != null ? "force-stop=" + forceStop : "";
        }
        return raw;
    }

    private List<SettingsSurfaceRow> buildSleepModeLogRows(List<SleepModeLogManager.LogEntry> logEntries) {
        List<SettingsSurfaceRow> rows = new ArrayList<>();
        for (int i = 0; i < logEntries.size(); i++) {
            SleepModeLogManager.LogEntry entry = logEntries.get(i);
            String title = (entry.packageName == null || entry.packageName.equals("-"))
                    ? humanizeLogAction(entry.action) : entry.packageName;
            String typeTag = sleepModeTypeTag(entry);
            String subtitle = entry.timestamp;
            if (!typeTag.isEmpty()) {
                subtitle = subtitle.isEmpty() ? typeTag : subtitle + " | " + typeTag;
            }
            String detail = humanizeLogOutcome(entry.outcome);
            rows.add(new SettingsSurfaceRow("#" + (i + 1), title, subtitle, detail,
                    resolveSleepModeLogBadge(entry.action), entry.packageName));
        }
        return rows;
    }

    private String sleepModeTypeTag(SleepModeLogManager.LogEntry entry) {
        String type   = entry.freezeType;
        String method = entry.method;
        if (type == null || type.equals("-")) return "";
        String typeLabel = "permanent".equals(type) ? "perm" : "temp";
        if (method == null || method.equals("-")) return typeLabel;
        return typeLabel + " • " + method;
    }

    private List<SettingsSurfaceRow> buildSchedulerLogRows(List<RestrictionsScheduler.SchedulerLog.Entry> logEntries) {
        List<SettingsSurfaceRow> rows = new ArrayList<>();
        for (int i = 0; i < logEntries.size(); i++) {
            RestrictionsScheduler.SchedulerLog.Entry entry = logEntries.get(i);
            String title = (entry.packageName == null || entry.packageName.equals("-"))
                    ? humanizeLogAction(entry.action) : entry.packageName;
            String subtitle = entry.timestamp;
            if (entry.action != null && !entry.action.trim().isEmpty()) {
                subtitle = subtitle.isEmpty() ? humanizeLogAction(entry.action)
                        : subtitle + " | " + humanizeLogAction(entry.action);
            }
            String detail = humanizeLogOutcome(entry.outcome);
            if (entry.detail != null && !entry.detail.trim().isEmpty()) {
                detail = detail.isEmpty() ? entry.detail : detail + "  |  " + entry.detail;
            }
            rows.add(new SettingsSurfaceRow("#" + (i + 1), title, subtitle, detail,
                    resolveSchedulerLogBadge(entry.action), entry.packageName));
        }
        return rows;
    }

    private AlertDialog createSettingsSurfaceDialog(String title, String subtitle, View contentView) {
        View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_settings_surface, null);
        TextView subtitleView = dialogView.findViewById(R.id.dialog_surface_subtitle);
        FrameLayout contentContainer = dialogView.findViewById(R.id.dialog_surface_content);
        subtitleView.setText(subtitle);
        subtitleView.setVisibility(subtitle == null || subtitle.trim().isEmpty() ? View.GONE : View.VISIBLE);
        contentContainer.addView(contentView);

        int screenHeight;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.view.WindowMetrics metrics = activity.getWindowManager().getCurrentWindowMetrics();
            Insets insets = WindowInsetsCompat.toWindowInsetsCompat(metrics.getWindowInsets())
                    .getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            screenHeight = metrics.getBounds().height() - insets.top - insets.bottom;
        } else {
            DisplayMetrics dm = new DisplayMetrics();
            activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
            screenHeight = dm.heightPixels;
        }
        contentContainer.getLayoutParams().height = (int)(screenHeight * 0.55);

        ListView lv = contentView.findViewById(R.id.top_offenders_list);
        if (lv != null) {
            lv.getLayoutParams().height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
            View footer = new View(activity);
            footer.setLayoutParams(new android.widget.AbsListView.LayoutParams(
                    android.widget.AbsListView.LayoutParams.MATCH_PARENT,
                    Math.round(activity.getResources().getDisplayMetrics().density)));
            footer.setBackgroundColor(ContextCompat.getColor(activity, R.color.divider_color));
            lv.addFooterView(footer, null, false);
        }

        return new MaterialAlertDialogBuilder(activity).setTitle(title).setView(dialogView).create();
    }

    private SettingsListContent createSettingsListContent(String emptyText, boolean showFilter) {
        View contentView = activity.getLayoutInflater().inflate(R.layout.dialog_top_offenders, null);
        AutoCompleteTextView filterSpinner = contentView.findViewById(R.id.top_offenders_filter);
        TextView summaryText  = contentView.findViewById(R.id.top_offenders_summary);
        ProgressBar loading   = contentView.findViewById(R.id.top_offenders_loading);
        ListView listView     = contentView.findViewById(R.id.top_offenders_list);
        TextView emptyView    = contentView.findViewById(R.id.top_offenders_empty);
        View filterLayout     = contentView.findViewById(R.id.top_offenders_filter_layout);
        filterLayout.setVisibility(showFilter ? View.VISIBLE : View.GONE);
        emptyView.setText(emptyText);
        return new SettingsListContent(contentView, filterSpinner, summaryText, loading, listView, emptyView);
    }

    private void bindOptionalText(TextView view, String text) {
        if (text == null || text.trim().isEmpty()) {
            view.setVisibility(View.GONE);
            view.setText("");
            return;
        }
        view.setVisibility(View.VISIBLE);
        view.setText(text);
    }

    private TextView makeBadge(String text, int badgeColor, float dp) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextSize(12f);
        tv.setTextColor(badgeColor);
        int padH = Math.round(8 * dp);
        int padV = Math.round(3 * dp);
        tv.setPadding(padH, padV, padH, padV);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(6 * dp);
        bg.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(badgeColor, 30));
        bg.setStroke(Math.round(dp), androidx.core.graphics.ColorUtils.setAlphaComponent(badgeColor, 70));
        tv.setBackground(bg);
        return tv;
    }

    private void openAppInfo(String packageName) {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + packageName));
            activity.startActivity(intent);
        } catch (Exception e) {
            AppDebugManager.e(Category.STATISTICS_PAGE, FILE + ": openAppInfo failed for " + packageName, e);
            Toast.makeText(activity, activity.getString(R.string.settings_open_app_info_error), Toast.LENGTH_SHORT).show();
        }
    }

    void applyCustomAccentToDialogButtons(AlertDialog dialog) {
        int accent = activity.prefs().getInt(KEY_ACCENT, ACCENT_SYSTEM);
        if (accent != ACCENT_CUSTOM) return;
        int nightMode = activity.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        int color = nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
                ? Color.WHITE : Color.BLACK;
        for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
            android.widget.Button btn = dialog.getButton(which);
            if (btn != null) btn.setTextColor(color);
        }
    }

    private String resolveAggregateAppName(com.gree1d.reappzuku.db.AppStatsAggregate stats,
                                            com.gree1d.reappzuku.db.AppStatsDao appStatsDao) {
        if (stats.appName != null && !stats.appName.trim().isEmpty()) return stats.appName;
        try {
            android.content.pm.ApplicationInfo appInfo =
                    activity.getPackageManager().getApplicationInfo(stats.packageName, 0);
            CharSequence label = activity.getPackageManager().getApplicationLabel(appInfo);
            if (label != null) {
                String name = label.toString();
                activity.executor.execute(() -> appStatsDao.updateAppName(stats.packageName, name));
                stats.appName = name;
                return name;
            }
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            AppDebugManager.w(Category.STATISTICS_PAGE, FILE + ": resolveAggregateAppName package not found: "
                    + stats.packageName, e);
        }
        return stats.packageName;
    }


    private String resolveRestrictionTypeBadge(String action) {
        if (action == null) return "";
        switch (action.trim().toLowerCase()) {
            case "restrict-hard":   case "reapply-hard":   return activity.getString(R.string.restriction_badge_hard);
            case "restrict-medium": case "reapply-medium": return activity.getString(R.string.restriction_badge_medium);
            case "restrict-soft":   case "reapply-soft":   case "restrict": return activity.getString(R.string.restriction_badge_soft);
            case "restrict-manual": case "reapply-manual": return activity.getString(R.string.restriction_badge_manual);
            case "allow":                                  return activity.getString(R.string.restriction_badge_removed);
            default:                                       return "";
        }
    }

    private String resolveSleepModeLogBadge(String action) {
        if (action == null) return "";
        switch (action.trim().toLowerCase()) {
            case "freeze":   return activity.getString(R.string.log_badge_freeze);
            case "unfreeze": return activity.getString(R.string.log_badge_unfreeze);
            default:         return "";
        }
    }

    private String resolveSchedulerLogBadge(String action) {
        if (action == null) return "";
        switch (action.trim().toLowerCase()) {
            case "lift":    return activity.getString(R.string.log_badge_lift);
            case "restore": return activity.getString(R.string.log_badge_restore);
            default:        return "";
        }
    }

    private String humanizeLogAction(String action) {
        if (action == null || action.trim().isEmpty()) return activity.getString(R.string.log_action_event);
        String n = action.trim().replace('-', ' ').replace('_', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    private String humanizeLogOutcome(String outcome) {
        if (outcome == null || outcome.trim().isEmpty()) return "";
        String trimmed = outcome.trim();
        String suffix = "";
        int parenIdx = trimmed.indexOf(" (");
        if (parenIdx > 0 && trimmed.endsWith(")")) {
            suffix  = trimmed.substring(parenIdx);
            trimmed = trimmed.substring(0, parenIdx);
        }
        String localized;
        switch (trimmed.toLowerCase()) {
            case "ok":                         localized = activity.getString(R.string.log_outcome_ok);                        break;
            case "verified":                   localized = activity.getString(R.string.log_outcome_verified);                  break;
            case "failed":                     localized = activity.getString(R.string.log_outcome_failed);                    break;
            case "skipped":                    localized = activity.getString(R.string.log_outcome_skipped);                   break;
            case "verify-failed":              localized = activity.getString(R.string.log_outcome_verify_failed);             break;
            case "verify-unavailable":         localized = activity.getString(R.string.log_outcome_verify_unavailable);        break;
            case "battery-whitelist-removed":  localized = activity.getString(R.string.log_outcome_battery_whitelist_removed); break;
            case "battery-whitelist-restored": localized = activity.getString(R.string.log_outcome_battery_whitelist_restored); break;
            default:
                String n = trimmed.replace('-', ' ').replace('_', ' ');
                localized = n.toUpperCase(Locale.US);
        }
        return localized + suffix;
    }

    private String extractDetailValue(String detail, String key) {
        if (detail == null || key == null) return null;
        String prefix = key + "=";
        int start = detail.indexOf(prefix);
        if (start < 0) return null;
        start += prefix.length();
        int end = detail.indexOf(' ', start);
        return end < 0 ? detail.substring(start) : detail.substring(start, end);
    }

    private List<String> extractOpsList(String detail, String key) {
        List<String> result = new ArrayList<>();
        if (detail == null) return result;
        String prefix = key + "=[";
        int start = detail.indexOf(prefix);
        if (start < 0) return result;
        start += prefix.length();
        int end = detail.indexOf(']', start);
        if (end < 0) return result;
        String inner = detail.substring(start, end).trim();
        if (inner.isEmpty()) return result;
        for (String op : inner.split(",")) {
            String trimmed = op.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    private String bucketBucketName(String value) {
        switch (value) {
            case "40": return "RARE (40)";
            case "45": return "RESTRICTED (45)";
            case "30": return "WORKING_SET (30)";
            case "20": return "FREQUENT (20)";
            case "10": return "ACTIVE (10)";
            default:   return value;
        }
    }

    private String formatRecoveredSize(long kb) {
        if (kb < 1024)          return activity.getString(R.string.unit_kb, kb);
        if (kb < 1024 * 1024)   return activity.getString(R.string.unit_mb_precise, kb / 1024f);
        return                         activity.getString(R.string.unit_gb_precise, kb / (1024f * 1024f));
    }


    static class TopOffender {
        final String appName, packageName;
        final int killCount, relaunchCount;
        final long recoveredKb;
        final double score;

        TopOffender(String appName, String packageName, int killCount, int relaunchCount,
                    long recoveredKb, double score) {
            this.appName = appName; this.packageName = packageName;
            this.killCount = killCount; this.relaunchCount = relaunchCount;
            this.recoveredKb = recoveredKb; this.score = score;
        }
    }

    static class KillHistoryEntry {
        final String appName, packageName, detail, badge, lastKillSource;
        final long lastEventTime;

        KillHistoryEntry(String appName, String packageName, String detail, String badge,
                         long lastEventTime, String lastKillSource) {
            this.appName = appName; this.packageName = packageName;
            this.detail = detail; this.badge = badge;
            this.lastEventTime = lastEventTime; this.lastKillSource = lastKillSource;
        }
    }

    static class SettingsSurfaceRow {
        final String leadingText, title, subtitle, detail, badge, packageName;

        SettingsSurfaceRow(String leadingText, String title, String subtitle,
                           String detail, String badge, String packageName) {
            this.leadingText = leadingText; this.title = title; this.subtitle = subtitle;
            this.detail = detail; this.badge = badge; this.packageName = packageName;
        }
    }

    static class SettingsListContent {
        final View rootView;
        final AutoCompleteTextView filterSpinner;
        final TextView summaryText;
        final ProgressBar loading;
        final ListView listView;
        final TextView emptyView;

        SettingsListContent(View rootView, AutoCompleteTextView filterSpinner, TextView summaryText,
                            ProgressBar loading, ListView listView, TextView emptyView) {
            this.rootView = rootView; this.filterSpinner = filterSpinner;
            this.summaryText = summaryText; this.loading = loading;
            this.listView = listView; this.emptyView = emptyView;
        }
    }

    class SettingsSurfaceAdapter extends BaseAdapter {
        private final List<SettingsSurfaceRow> items = new ArrayList<>();
        private final LayoutInflater inflater = LayoutInflater.from(activity);

        void setItems(List<SettingsSurfaceRow> newItems) {
            items.clear();
            if (newItems != null) items.addAll(newItems);
            notifyDataSetChanged();
        }

        @Override public int getCount()                { return items.size(); }
        @Override public SettingsSurfaceRow getItem(int pos) {
            return (pos >= 0 && pos < items.size()) ? items.get(pos) : null;
        }
        @Override public long getItemId(int pos)       { return pos; }

        @Override
        public View getView(int position, View convertView, android.view.ViewGroup parent) {
            View view = convertView != null
                    ? convertView
                    : inflater.inflate(R.layout.item_top_offender, parent, false);
            SettingsSurfaceRow item = getItem(position);
            if (item == null) return view;
            bindOptionalText((TextView) view.findViewById(R.id.offender_rank),    item.leadingText);
            ((TextView) view.findViewById(R.id.offender_name)).setText(item.title);
            bindOptionalText((TextView) view.findViewById(R.id.offender_package), item.subtitle);
            bindOptionalText((TextView) view.findViewById(R.id.offender_metrics), item.detail);
            bindOptionalText((TextView) view.findViewById(R.id.offender_score),   item.badge);

            int accent = activity.prefs().getInt(KEY_ACCENT, ACCENT_SYSTEM);
            TextView rank  = view.findViewById(R.id.offender_rank);
            TextView score = view.findViewById(R.id.offender_score);
            if (accent == ACCENT_CUSTOM) {
                int color = activity.prefs().getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR);
                if (rank  != null) rank.setTextColor(color);
                if (score != null) score.setTextColor(color);
            }
            if (score != null) {
                if (item.badge != null && !item.badge.trim().isEmpty()) {
                    int badgeColor = accent == ACCENT_CUSTOM
                            ? activity.prefs().getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR)
                            : com.google.android.material.color.MaterialColors.getColor(
                                    score, com.google.android.material.R.attr.colorSecondary);
                    android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                    bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                    bg.setCornerRadius(6 * score.getResources().getDisplayMetrics().density);
                    bg.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(badgeColor, 40));
                    bg.setStroke(Math.round(score.getResources().getDisplayMetrics().density),
                            androidx.core.graphics.ColorUtils.setAlphaComponent(badgeColor, 80));
                    score.setBackground(bg);
                } else {
                    score.setBackground(null);
                }
            }
            return view;
        }
    }
}
