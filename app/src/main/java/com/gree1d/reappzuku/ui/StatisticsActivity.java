package com.gree1d.reappzuku.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.AutoCompleteTextView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.gree1d.reappzuku.databinding.ActivityStatisticsBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.gree1d.reappzuku.core.ShellManager;
import com.gree1d.reappzuku.manager.BackgroundAppManager;
import com.gree1d.reappzuku.manager.BatteryStatsManager;
import com.gree1d.reappzuku.utils.SleepModeLogManager;
import com.gree1d.reappzuku.manager.RestrictionsScheduler;
import com.gree1d.reappzuku.utils.BackgroundRestrictionLog;
import com.gree1d.reappzuku.core.BaseActivity;
import com.gree1d.reappzuku.service.ShappkyService;
import com.gree1d.reappzuku.R;

import static com.gree1d.reappzuku.core.PreferenceKeys.*;
import static com.gree1d.reappzuku.core.AppConstants.*;

public class StatisticsActivity extends BaseActivity {

    private static final String TAG = "StatisticsActivity";
    private static final int TOP_OFFENDERS_LIMIT = 50;
    private static final long[] TOP_OFFENDER_FILTER_WINDOWS_MS = {
            STATS_HISTORY_DURATION_MS,
            24 * 60 * 60 * 1000L,
            7 * 24 * 60 * 60 * 1000L,
            -1L
    };

    private static final int[] CHART_PERIODS_HOURS = { 2, 6, 12, 24 };


    private double batteryCapacityMah = 4000.0;


    private static final int CHART_BATTERY = 0;
    private static final int CHART_CPU     = 1;
    private static final int CHART_RAM     = 2;
    private static final int CHART_COUNT   = 3;


    private static final int[] SLICE_PALETTE = {
        0xFFE53935,
        0xFF1E88E5,
        0xFF43A047,
        0xFFFB8C00,
        0xFF8E24AA,
        0xFF00ACC1,
        0xFFFFB300,
        0xFF00897B,
        0xFFF06292,
        0xFF6D4C41,
        0xFF3949AB,
        0xFF7CB342,
        0xFFBDBDBD,
    };

    private String[] topOffenderFilterLabels;
    private String[] chartPeriodLabels;
    private int selectedPeriodIdx = 0;
    private int currentChartIdx = CHART_BATTERY;


    private List<BatteryStatsManager.AppResourceStats> currentSorted = null;
    private double currentTotalHours = 0;

    private ActivityStatisticsBinding binding;
    private ShellManager shellManager;
    private BackgroundAppManager appManager;
    private BatteryStatsManager batteryStatsManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newCachedThreadPool();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStatisticsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        topOffenderFilterLabels = getResources().getStringArray(R.array.settings_top_offender_filter_labels);
        chartPeriodLabels = getResources().getStringArray(R.array.chart_period_labels);

        shellManager        = new ShellManager(this.getApplicationContext(), handler, executor);
        appManager          = new BackgroundAppManager(this.getApplicationContext(), handler, executor, shellManager);
        batteryStatsManager = new BatteryStatsManager(this.getApplicationContext(), handler, executor, shellManager);

        setupToolbar();
        setupBottomNavigation();
        setupPeriodTabs();
        setupChartPager();
        setupListeners();

        batteryCapacityMah = batteryStatsManager.getBatteryCapacityMah();
        batteryStatsManager.takeSnapshotAsync(() -> loadCharts(CHART_PERIODS_HOURS[selectedPeriodIdx]));
    }


    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
        if (accent == ACCENT_CUSTOM) {
            int customColor = sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR);
            int onColor = sharedPreferences.getInt(KEY_ACCENT_ON_COLOR, ACCENT_ON_WHITE) == ACCENT_ON_BLACK
                    ? Color.BLACK : Color.WHITE;
            binding.toolbar.setBackgroundColor(customColor);
            binding.toolbar.setTitleTextColor(onColor);
            if (binding.toolbar.getNavigationIcon() != null)
                androidx.core.graphics.drawable.DrawableCompat.setTint(
                        binding.toolbar.getNavigationIcon(), onColor);
        } else if (accent == ACCENT_SYSTEM) {
            binding.toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.toolbar_navy));
            binding.toolbar.setTitleTextColor(Color.WHITE);
        } else {
            boolean isLightAccent = (accent == ACCENT_APRICOT || accent == ACCENT_SKY ||
                    accent == ACCENT_PAPAYA || accent == ACCENT_LAVENDER ||
                    accent == ACCENT_MINT || accent == ACCENT_PEACH ||
                    accent == ACCENT_POWDER || accent == ACCENT_FOG);
            binding.toolbar.setTitleTextColor(isLightAccent ? Color.BLACK : Color.WHITE);
        }
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.navIconMain.setSelected(false);
        binding.bottomNavigation.navIconSettings.setSelected(false);
        binding.bottomNavigation.navIconStatistics.setSelected(true);
        binding.bottomNavigation.navBtnMain.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            finish();
        });
        binding.bottomNavigation.navBtnSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
            finish();
        });
        binding.bottomNavigation.navBtnStatistics.setOnClickListener(v -> {});
        applyNavBarInsets(binding.bottomNavigation.getRoot());
    }

    private void setupPeriodTabs() {
        com.google.android.material.tabs.TabLayout tabs = binding.tabPeriodSelector;
        for (String label : chartPeriodLabels) tabs.addTab(tabs.newTab().setText(label));
        tabs.selectTab(tabs.getTabAt(selectedPeriodIdx));

        int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
        if (accent == ACCENT_CUSTOM) {
            int color = sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR);
            applyCustomAccentToTabLayout(tabs, color);
        }

        tabs.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                selectedPeriodIdx = tab.getPosition();
                loadCharts(CHART_PERIODS_HOURS[selectedPeriodIdx]);
            }
            @Override public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
            @Override public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
        });
    }

    private void setupChartPager() {
        binding.btnChartPrev.setOnClickListener(v -> navigateChart(-1));
        binding.btnChartNext.setOnClickListener(v -> navigateChart(+1));
        updateChartPagerUI();
    }


    @SuppressWarnings("unchecked")
    private List<PieEntry>[] chartEntries   = new List[CHART_COUNT];
    @SuppressWarnings("unchecked")
    private List<BatteryStatsManager.AppResourceStats>[] chartSortedByMetric = new List[CHART_COUNT];
    @SuppressWarnings("unchecked")
    private List<BatteryStatsManager.AppResourceStats>[] chartOthers = new List[CHART_COUNT];
    @SuppressWarnings("unchecked")
    private List<Integer>[] chartColors     = new List[CHART_COUNT];
    private ChartMetric[]   chartMetrics    = { ChartMetric.BATTERY, ChartMetric.CPU, ChartMetric.RAM };
    private double[]        chartTotals     = new double[CHART_COUNT];

    private void navigateChart(int direction) {
        currentChartIdx = (currentChartIdx + direction + CHART_COUNT) % CHART_COUNT;
        updateChartPagerUI();
        if (currentSorted != null) {
            showActiveChart(currentSorted, currentTotalHours);
        }

        if (chartEntries[currentChartIdx] != null) {
            buildChartLegend(
                    chartEntries[currentChartIdx],
                    chartSortedByMetric[currentChartIdx],
                    chartOthers[currentChartIdx],
                    chartColors[currentChartIdx],
                    chartMetrics[currentChartIdx],
                    chartTotals[currentChartIdx]);
        }
    }

    private void updateChartPagerUI() {
        switch (currentChartIdx) {
            case CHART_BATTERY:
                binding.tvChartTitle.setText(getString(R.string.chart_title_battery));
                break;
            case CHART_CPU:
                binding.tvChartTitle.setText(getString(R.string.chart_title_cpu));
                break;
            case CHART_RAM:
                binding.tvChartTitle.setText(getString(R.string.chart_title_ram));
                break;
        }
    }

    private void setupListeners() {
        binding.layoutStats.setOnClickListener(v -> showStatsDialog());
        binding.layoutTopOffenders.setOnClickListener(v -> showTopOffendersDialog());
        binding.layoutRestrictionLog.setVisibility(
                appManager.supportsBackgroundRestriction() ? View.VISIBLE : View.GONE);
        binding.layoutRestrictionLog.setOnClickListener(v -> showBackgroundRestrictionLogDialog());
        binding.layoutSleepModeLog.setOnClickListener(v -> showSleepModeLogDialog());
        binding.layoutSchedulerLog.setOnClickListener(v -> showSchedulerLogDialog());
    }


    private void loadCharts(int hours) {
        if (binding == null) return;


        if (!ShappkyService.isRunning()) {
            showChartsLoading(false);
            binding.cardNoData.setVisibility(View.VISIBLE);
            binding.tvNoDataHint.setText(getString(R.string.stats_service_inactive_hint));
            binding.cardChartsPager.setVisibility(View.GONE);
            binding.tvPartialDataWarning.setVisibility(View.GONE);
            return;
        }

        showChartsLoading(true);
        batteryStatsManager.getStatsForPeriodAsync(hours, periodStats -> {
            if (binding == null) return;
            showChartsLoading(false);
            if (!periodStats.hasData) {
                binding.cardNoData.setVisibility(View.VISIBLE);
                binding.tvNoDataHint.setText(periodStats.dataHint);
                binding.cardChartsPager.setVisibility(View.GONE);
                binding.tvPartialDataWarning.setVisibility(View.GONE);
                return;
            }
            binding.cardNoData.setVisibility(View.GONE);
            binding.cardChartsPager.setVisibility(View.VISIBLE);


            if (periodStats.isPartialData) {
                binding.tvPartialDataWarning.setText(
                        getString(R.string.stats_partial_data_warning));
                binding.tvPartialDataWarning.setVisibility(View.VISIBLE);
            } else {
                binding.tvPartialDataWarning.setVisibility(View.GONE);
            }

            List<BatteryStatsManager.AppResourceStats> sorted = periodStats.sorted;
            currentSorted = sorted;
            currentTotalHours = periodStats.actualHours;


            BatteryStatsManager.AppResourceStats selfStats = null;
            for (BatteryStatsManager.AppResourceStats s : sorted) {
                if (s.isSelf) { selfStats = s; break; }
            }


            buildPieChart(binding.chartBattery, binding.layoutBatteryOthers,
                    sorted, ChartMetric.BATTERY, CHART_BATTERY);
            buildPieChart(binding.chartCpu, binding.layoutCpuOthers,
                    sorted, ChartMetric.CPU, CHART_CPU);
            buildPieChart(binding.chartRam, binding.layoutRamOthers,
                    sorted, ChartMetric.RAM, CHART_RAM);


            if (selfStats != null) {
                double totalBat = chartTotals[CHART_BATTERY];
                double totalCpu = chartTotals[CHART_CPU];
                double selfBatPct = totalBat > 0 ? (selfStats.batteryMah / totalBat) * 100.0 : 0;
                double selfCpuPct = totalCpu > 0 ? (selfStats.cpuPct / totalCpu) * 100.0 : 0;
                binding.tvSelfBat.setText(String.format(Locale.US, "%.1f%%", selfBatPct));
                binding.tvSelfCpu.setText(String.format(Locale.US, "%.1f%%", selfCpuPct));
                binding.tvSelfRam.setText(formatRamMb(selfStats.ramMb));
                binding.layoutSelfOverhead.setVisibility(View.VISIBLE);
            } else {
                binding.layoutSelfOverhead.setVisibility(View.GONE);
            }


            showActiveChart(sorted, periodStats.actualHours);
        });
    }

    private void showActiveChart(List<BatteryStatsManager.AppResourceStats> sorted, double actualHours) {
        if (binding == null) return;
        binding.chartBattery.setVisibility(currentChartIdx == CHART_BATTERY ? View.VISIBLE : View.GONE);
        binding.chartCpu.setVisibility(currentChartIdx == CHART_CPU     ? View.VISIBLE : View.GONE);
        binding.chartRam.setVisibility(currentChartIdx == CHART_RAM     ? View.VISIBLE : View.GONE);

        double totalBat = 0, totalCpu = 0, totalRam = 0;
        for (BatteryStatsManager.AppResourceStats s : sorted) {
            totalBat += s.batteryMah;
            totalCpu += s.cpuPct;
            totalRam += s.ramMb;
        }

        String centerText;
        switch (currentChartIdx) {
            case CHART_BATTERY:
                centerText = getString(R.string.stats_chart_total_battery, totalBat);
                break;
            case CHART_CPU:
                centerText = String.format(Locale.US, "%.1f%%", Math.min(100.0, totalCpu));
                break;
            case CHART_RAM:
                centerText = formatRamMb(totalRam);
                break;
            default:
                centerText = "";
        }
        binding.tvChartCenterValue.setText(centerText);
        // Keep hidden tv_chart_total in sync for any legacy references
        binding.tvChartTotal.setText(centerText);
    }

    private void showChartsLoading(boolean loading) {
        if (binding == null) return;
        binding.layoutChartsLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
    }


    private enum ChartMetric { BATTERY, CPU, RAM }

    private void buildPieChart(PieChart chart,
                                android.view.ViewGroup othersContainer,
                                List<BatteryStatsManager.AppResourceStats> sorted,
                                ChartMetric metric,
                                int chartIdx) {
        double total = 0;
        for (BatteryStatsManager.AppResourceStats s : sorted) total += metricValue(s, metric);
        if (total <= 0) return;

        List<BatteryStatsManager.AppResourceStats> byCurrent = new ArrayList<>(sorted);
        byCurrent.sort((a, b) -> Double.compare(metricValue(b, metric), metricValue(a, metric)));

        List<PieEntry> entries = new ArrayList<>();
        List<BatteryStatsManager.AppResourceStats> othersList = new ArrayList<>();
        double othersValue = 0;

        for (int i = 0; i < byCurrent.size(); i++) {
            BatteryStatsManager.AppResourceStats s = byCurrent.get(i);
            double val = metricValue(s, metric);
            float pct = (float)(val / total * 100);
            boolean forceShow = i < BatteryStatsManager.MIN_TOP_SLICES;
            if (forceShow || pct > BatteryStatsManager.OTHERS_THRESHOLD_PCT) {

                entries.add(new PieEntry((float) val, "", s.packageName));
            } else {
                othersValue += val;
                othersList.add(s);
            }
        }
        if (othersValue > 0) {

            entries.add(new PieEntry((float) othersValue, "", "__others__"));
        }

        List<Integer> colors = buildMultiColors(entries.size());
        final double finalTotal = total;

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setSliceSpace(0f);
        dataSet.setSelectionShift(0f);
        dataSet.setValueTextSize(0f);

        PieData data = new PieData(dataSet);
        chart.setData(data);
        chart.setRenderer(new PieChartRender(chart, chart.getAnimator(), chart.getViewPortHandler()));
        chart.setDrawHoleEnabled(true);
        chart.setHoleColor(Color.TRANSPARENT);
        chart.setHoleRadius(62f);
        chart.setTransparentCircleRadius(0f);
        chart.setDrawCenterText(false);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setRotationEnabled(false);

        chart.setExtraOffsets(0f, 0f, 0f, 0f);

        chart.setHighlightPerTapEnabled(false);
        chart.setOnChartValueSelectedListener(null);
        chart.animateY(800, Easing.EaseInOutQuad);

        chart.invalidate();

        if (othersContainer != null) othersContainer.setVisibility(View.GONE);


        chartEntries[chartIdx]        = entries;
        chartSortedByMetric[chartIdx] = byCurrent;
        chartOthers[chartIdx]         = othersList;
        chartColors[chartIdx]         = colors;
        chartTotals[chartIdx]         = total;


        if (chartIdx == currentChartIdx) {
            buildChartLegend(entries, byCurrent, othersList, colors, metric, total);

            chart.getViewTreeObserver().addOnGlobalLayoutListener(
                    new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                @Override public void onGlobalLayout() {
                    chart.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    com.github.mikephil.charting.utils.MPPointF center = chart.getCenterCircleBox();
                    float radius = chart.getRadius();

                    float circleTopPx = center.y - radius;
                    android.widget.LinearLayout.LayoutParams lp =
                            (android.widget.LinearLayout.LayoutParams)
                                    binding.scrollChartLegend.getLayoutParams();
                    lp.topMargin = Math.max(0, Math.round(circleTopPx));
                    binding.scrollChartLegend.setLayoutParams(lp);
                }
            });
        }
    }


    private void buildChartLegend(
            List<PieEntry> entries,
            List<BatteryStatsManager.AppResourceStats> byCurrent,
            List<BatteryStatsManager.AppResourceStats> othersList,
            List<Integer> colors,
            ChartMetric metric,
            double total) {

        android.widget.LinearLayout legend = binding.layoutChartLegend;
        if (legend == null) return;
        legend.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(this);
        int dotSizePx = (int)(10 * getResources().getDisplayMetrics().density);
        int marginEndPx = (int)(7 * getResources().getDisplayMetrics().density);
        int rowMarginBottomPx = (int)(7 * getResources().getDisplayMetrics().density);

        for (int i = 0; i < entries.size(); i++) {
            PieEntry pe = entries.get(i);
            int color = colors.get(i);
            float pct = total > 0 ? (float)(pe.getValue() / total * 100) : 0f;


            String name;
            final String pkg;
            Object tag = pe.getData();
            final boolean isOthers = "__others__".equals(tag);
            if (isOthers) {
                name = getString(R.string.chart_others_label);
                pkg  = "__others__";
            } else {
                pkg = tag != null ? tag.toString() : "";
                BatteryStatsManager.AppResourceStats found = findByPkg(byCurrent, pkg);
                name = (found != null && found.appName != null) ? found.appName : pkg;
            }
            final String finalName = name;
            final List<BatteryStatsManager.AppResourceStats> finalOthers = othersList;
            final double finalTotal = total;


            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            android.widget.LinearLayout.LayoutParams rowLp =
                    new android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = rowMarginBottomPx;
            row.setLayoutParams(rowLp);
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setBackground(obtainStyledAttributes(
                    new int[]{android.R.attr.selectableItemBackground})
                    .getDrawable(0));
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> {
                if (isOthers) {
                    showOthersDialog(finalOthers, metric, finalTotal);
                } else {
                    openAppDetail(pkg, finalName);
                }
            });


            android.widget.TextView dot = new android.widget.TextView(this);
            android.widget.LinearLayout.LayoutParams dotLp =
                    new android.widget.LinearLayout.LayoutParams(dotSizePx, dotSizePx);
            dotLp.rightMargin = marginEndPx;
            dot.setLayoutParams(dotLp);
            android.graphics.drawable.GradientDrawable circle =
                    new android.graphics.drawable.GradientDrawable();
            circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            circle.setColor(color);
            dot.setBackground(circle);


            android.widget.TextView label = new android.widget.TextView(this);
            label.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            label.setText(String.format(Locale.US, "%.1f%% — %s", pct, name));
            label.setTextSize(15f);
            label.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            label.setMaxLines(2);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);

            row.addView(dot);
            row.addView(label);
            legend.addView(row);
        }
    }

    private List<Integer> buildMultiColors(int count) {
        List<Integer> colors = new ArrayList<>();

        int paletteSize = SLICE_PALETTE.length - 1;
        for (int i = 0; i < count; i++) {
            if (i == count - 1 && count > 1) {

                colors.add(SLICE_PALETTE[SLICE_PALETTE.length - 1]);
            } else {
                colors.add(SLICE_PALETTE[i % paletteSize]);
            }
        }
        return colors;
    }

    private void showOthersDialog(List<BatteryStatsManager.AppResourceStats> others,
                                   ChartMetric metric, double total) {
        StringBuilder sb = new StringBuilder();
        for (BatteryStatsManager.AppResourceStats s : others) {
            sb.append(String.format(Locale.US, "• %s  %.1f%%\n",
                    s.appName, metricValue(s, metric) / total * 100));
        }
        applyCustomAccentToDialogButtons(new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.chart_others_dialog_title))
                .setMessage(sb.toString().trim())
                .setPositiveButton(android.R.string.ok, null)
                .show());
    }

    private void openAppDetail(String packageName, String appName) {
        double totalCpuPct = 0, totalRamMb = 0;
        if (currentSorted != null) {
            for (BatteryStatsManager.AppResourceStats s : currentSorted) {
                totalCpuPct += s.cpuPct;
                totalRamMb  += s.ramMb;
            }
        }
        Intent intent = new Intent(this, AppResourceDetailActivity.class);
        intent.putExtra(AppResourceDetailActivity.EXTRA_PACKAGE_NAME, packageName);
        intent.putExtra(AppResourceDetailActivity.EXTRA_APP_NAME, appName);
        intent.putExtra(AppResourceDetailActivity.EXTRA_TOTAL_CPU_PCT, totalCpuPct);
        intent.putExtra(AppResourceDetailActivity.EXTRA_TOTAL_RAM_MB, totalRamMb);
        intent.putExtra(AppResourceDetailActivity.EXTRA_PERIOD_IDX, selectedPeriodIdx);
        startActivity(intent);
    }

    private double metricValue(BatteryStatsManager.AppResourceStats s, ChartMetric m) {
        if (s == null) return 0;
        switch (m) {
            case BATTERY: return s.batteryMah;
            case CPU:     return s.cpuPct;
            case RAM:     return s.ramMb;
            default:      return 0;
        }
    }

    private BatteryStatsManager.AppResourceStats findByPkg(
            List<BatteryStatsManager.AppResourceStats> list, String pkg) {
        for (BatteryStatsManager.AppResourceStats s : list) {
            if (s.packageName.equals(pkg)) return s;
        }
        return null;
    }


    private void showStatsDialog() {
        executor.execute(() -> {
            long twelveHoursAgo = System.currentTimeMillis() - STATS_HISTORY_DURATION_MS;
            com.gree1d.reappzuku.db.AppStatsDao appStatsDao = com.gree1d.reappzuku.db.AppDatabase
                    .getInstance(this).appStatsDao();
            java.util.List<com.gree1d.reappzuku.db.AppStats> statsList = appStatsDao.getAllStatsSince(twelveHoursAgo);

            List<KillHistoryEntry> historyEntries = new ArrayList<>();
            int totalKills = 0;
            int totalRelaunches = 0;
            long totalRecoveredKb = 0;
            java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);

            for (com.gree1d.reappzuku.db.AppStats stats : statsList) {
                if (stats == null || stats.packageName == null) continue;
                if (stats.killCount <= 0 && stats.relaunchCount <= 0) continue;

                List<String> detailParts = new ArrayList<>();
                if (stats.killCount > 0) {
                    String killDetail = getString(R.string.stats_kill_detail, stats.killCount);
                    detailParts.add(killDetail);
                }
                if (stats.relaunchCount > 0) {
                    String relaunchDetail = getString(R.string.stats_relaunch_detail, stats.relaunchCount);
                    if (stats.lastRelaunchTime > 0) {
                        relaunchDetail += getString(R.string.stats_last_relaunch_time,
                                timeFormat.format(new java.util.Date(stats.lastRelaunchTime)));
                    }
                    detailParts.add(relaunchDetail);
                }
                if (stats.totalRecoveredKb > 0) {
                    detailParts.add(getString(R.string.stats_recovered_ram, formatRecoveredSize(stats.totalRecoveredKb)));
                }

                long lastEventTime = Math.max(stats.lastKillTime, stats.lastRelaunchTime);
                String badge = lastEventTime > 0 ? timeFormat.format(new java.util.Date(lastEventTime)) : "";
                historyEntries.add(new KillHistoryEntry(
                        resolveStatsAppName(stats, appStatsDao),
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
            final List<KillHistoryEntry> sortedEntries = new ArrayList<>(historyEntries);
            List<SettingsSurfaceRow> rows = buildKillHistoryRows(sortedEntries);
            String summary = getString(R.string.stats_summary_12h,
                    rows.size(), totalKills, totalRelaunches, formatRecoveredSize(totalRecoveredKb));

            handler.post(() -> {
                SettingsListContent content = createSettingsListContent(
                        getString(R.string.stats_no_activity_12h), false);
                SettingsSurfaceAdapter adapter = new SettingsSurfaceAdapter();
                adapter.setItems(rows);
                content.listView.setAdapter(adapter);
                content.listView.setEmptyView(content.emptyView);
                content.listView.setOnItemClickListener((parent, view, position, id) -> {
                    if (position < sortedEntries.size()) {
                        showKillEntryDetails(sortedEntries.get(position));
                    }
                });
                content.summaryText.setText(summary);
                content.loading.setVisibility(View.GONE);
                content.listView.setVisibility(View.VISIBLE);
                content.emptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);

                AlertDialog dialog = createSettingsSurfaceDialog(
                        getString(R.string.settings_kill_history_title),
                        getString(R.string.stats_dialog_subtitle),
                        content.rootView);
                dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.dialog_close), (d, w) -> d.dismiss());
                dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.settings_restriction_log_clear), (d, w) -> {});
                dialog.show();
                applyCustomAccentToDialogButtons(dialog);
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                    executor.execute(() ->
                            com.gree1d.reappzuku.db.AppDatabase.getInstance(this).appStatsDao().deleteAll());
                });
            });
        });
    }

    private void showTopOffendersDialog() {
        SettingsListContent content = createSettingsListContent(
                getString(R.string.stats_top_offenders_empty), true);
        TextView summaryText = content.summaryText;
        ProgressBar loading = content.loading;
        ListView listView = content.listView;
        TextView emptyView = content.emptyView;

        SettingsSurfaceAdapter offendersAdapter = new SettingsSurfaceAdapter();
        listView.setAdapter(offendersAdapter);
        listView.setEmptyView(emptyView);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            SettingsSurfaceRow row = offendersAdapter.getItem(position);
            if (row != null && row.packageName != null && !row.packageName.isEmpty()) {
                openAppInfo(row.packageName);
            }
        });

        ArrayAdapter<String> filterAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, topOffenderFilterLabels);
        content.filterSpinner.setAdapter(filterAdapter);
        content.filterSpinner.setText(topOffenderFilterLabels[0], false);

        AlertDialog dialog = createSettingsSurfaceDialog(
                getString(R.string.settings_top_offenders_title),
                getString(R.string.stats_top_offenders_dialog_subtitle),
                content.rootView);
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.dialog_close), (d, w) -> d.dismiss());
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.stats_top_offenders_reset), (d, w) -> {});
        dialog.show();
        applyCustomAccentToDialogButtons(dialog);

        loadTopOffenders(0, offendersAdapter, summaryText, loading, listView, emptyView);

        content.filterSpinner.setOnItemClickListener((parent, view, position, id) -> {
            content.filterSpinner.setText(topOffenderFilterLabels[position], false);
            loadTopOffenders(position, offendersAdapter, summaryText, loading, listView, emptyView);
        });

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            executor.execute(() ->
                    com.gree1d.reappzuku.db.AppDatabase.getInstance(this).appStatsDao().deleteAll());
        });
    }

    private void loadTopOffenders(int filterIndex, SettingsSurfaceAdapter adapter, TextView summaryText,
                                  ProgressBar loading, ListView listView, TextView emptyView) {
        if (filterIndex < 0 || filterIndex >= TOP_OFFENDER_FILTER_WINDOWS_MS.length) filterIndex = 0;

        final int selectedFilterIndex = filterIndex;
        loading.setVisibility(View.VISIBLE);
        listView.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        summaryText.setText(getString(R.string.stats_loading));

        executor.execute(() -> {
            long windowMs = TOP_OFFENDER_FILTER_WINDOWS_MS[selectedFilterIndex];
            com.gree1d.reappzuku.db.AppStatsDao appStatsDao =
                    com.gree1d.reappzuku.db.AppDatabase.getInstance(this).appStatsDao();
            List<com.gree1d.reappzuku.db.AppStats> stats;
            if (windowMs > 0) {
                long since = System.currentTimeMillis() - windowMs;
                stats = appStatsDao.getAllStatsSince(since);
            } else {
                stats = appStatsDao.getAllStats();
            }

            List<TopOffender> offenders = buildTopOffenders(stats, appStatsDao);

            int totalKills = 0;
            int totalRelaunches = 0;
            long totalRecoveredKb = 0;
            for (TopOffender offender : offenders) {
                totalKills += offender.killCount;
                totalRelaunches += offender.relaunchCount;
                totalRecoveredKb += offender.recoveredKb;
            }

            String summary = getString(R.string.stats_top_offenders_summary,
                    topOffenderFilterLabels[selectedFilterIndex],
                    offenders.size(), totalKills, totalRelaunches,
                    formatRecoveredSize(totalRecoveredKb));

            handler.post(() -> {
                if (isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) return;
                adapter.setItems(buildTopOffenderRows(offenders));
                summaryText.setText(summary);
                loading.setVisibility(View.GONE);
                listView.setVisibility(View.VISIBLE);
                emptyView.setVisibility(offenders.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private List<TopOffender> buildTopOffenders(List<com.gree1d.reappzuku.db.AppStats> statsList,
                                                 com.gree1d.reappzuku.db.AppStatsDao appStatsDao) {
        List<TopOffender> offenders = new ArrayList<>();
        for (com.gree1d.reappzuku.db.AppStats stats : statsList) {
            if (stats == null || stats.packageName == null) continue;
            if (stats.killCount <= 0 && stats.relaunchCount <= 0 && stats.totalRecoveredKb <= 0) continue;

            String appName = resolveStatsAppName(stats, appStatsDao);
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

    private void showBackgroundRestrictionLogDialog() {
        SettingsListContent content = createSettingsListContent(
                getString(R.string.settings_restriction_log_empty), false);
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
        content.summaryText.setText(getString(R.string.stats_loading));

        AlertDialog dialog = createSettingsSurfaceDialog(
                getString(R.string.settings_restriction_log_title),
                getString(R.string.settings_restriction_log_dialog_subtitle),
                content.rootView);
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.dialog_close), (d, w) -> d.dismiss());
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.settings_restriction_log_clear), (d, w) -> {});
        dialog.show();
        applyCustomAccentToDialogButtons(dialog);

        Runnable reloadLog = () -> executor.execute(() -> {
            List<BackgroundRestrictionLog.LogEntry> entries = BackgroundRestrictionLog.readEntries(this);
            List<SettingsSurfaceRow> rows = buildRestrictionLogRows(entries);
            String summary = getString(R.string.settings_restriction_log_summary, rows.size());
            handler.post(() -> {
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
            appManager.clearBackgroundRestrictionLog();
            reloadLog.run();
        });
    }

    private void showKillEntryDetails(KillHistoryEntry entry) {
        java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
        String killTime = entry.lastEventTime > 0
                ? timeFormat.format(new java.util.Date(entry.lastEventTime)) : "—";
        String source = entry.lastKillSource != null ? entry.lastKillSource : "—";

        String message = entry.appName + "\n"
                + entry.packageName + "\n"
                + killTime + " · " + source;

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setMessage(message)
                .setNegativeButton(getString(R.string.dialog_close), (d, w) -> d.dismiss());

        applyCustomAccentToDialogButtons(builder.show());
    }

    private void showRestrictionLogEntryDetails(BackgroundRestrictionLog.LogEntry entry) {
        StringBuilder body = new StringBuilder();
        body.append(entry.timestamp).append("\n\n");

        String actionLabel = humanizeLogAction(entry.action);
        String outcomeLabel = humanizeLogOutcome(entry.outcome);
        body.append(getString(R.string.log_detail_action)).append(": ").append(actionLabel).append("\n");
        body.append(getString(R.string.log_detail_outcome)).append(": ").append(outcomeLabel).append("\n");

        String detail = entry.detail != null ? entry.detail : "";

        boolean isWatchdogBucket = "watchdog-bucket".equals(entry.action);
        boolean isWatchdog = "watchdog".equals(entry.action);

        if (isWatchdogBucket) {
            body.append("\n");
            String was = extractDetailValue(detail, "was");
            String set = extractDetailValue(detail, "set");
            if (was != null) body.append(getString(R.string.log_detail_bucket_was)).append(": ").append(bucketBucketName(was)).append("\n");
            if (set != null) body.append(getString(R.string.log_detail_bucket_set)).append(": ").append(bucketBucketName(set)).append("\n");
            body.append(getString(R.string.log_detail_bucket_restored)).append(": ")
                    .append("ok".equals(entry.outcome) ? getString(R.string.log_outcome_ok) : getString(R.string.log_outcome_failed))
                    .append("\n");
        } else if (isWatchdog) {
            body.append("\n");
            String missing = extractDetailValue(detail, "missing");
            String repaired = extractDetailValue(detail, "repaired");
            if (missing != null) body.append(getString(R.string.log_detail_ops_missing)).append(": ").append(missing).append("\n");
            if (repaired != null) body.append(getString(R.string.log_detail_ops_repaired)).append(": ").append(repaired).append("\n");
            List<String> repairedOps = extractOpsList(detail, "repairedOps");
            if (!repairedOps.isEmpty()) {
                body.append("\n");
                for (String op : repairedOps) {
                    body.append("  ✓ ").append(op).append("\n");
                }
            }
            List<String> failedOps = extractOpsList(detail, "failedOps");
            if (!failedOps.isEmpty()) {
                if (repairedOps.isEmpty()) body.append("\n");
                body.append(getString(R.string.log_detail_ops_failed)).append(":\n");
                for (String op : failedOps) {
                    body.append("  ✗ ").append(op).append("\n");
                }
            }
        } else {
            boolean isSoftAction = "reapply-soft".equals(entry.action) || "restrict-soft".equals(entry.action) || "restrict".equals(entry.action);
            boolean hasOpsInfo = !isSoftAction && detail.contains("appops=");
            if (hasOpsInfo) {
                body.append("\n");
                List<String> failedOps = extractOpsList(detail, "failedOps");
                boolean allOk     = detail.contains("appops=ok(");
                boolean allFailed = detail.contains("appops=failed(0/");
                boolean isManual  = "reapply-manual".equals(entry.action) || "restrict-manual".equals(entry.action);
                body.append(getString(R.string.log_detail_appops)).append(":\n");
                if (isManual) {
                    int manualMask = appManager.getManualOpsMask(entry.packageName);
                    if (manualMask != 0) {
                        for (int i = 0; i < BackgroundAppManager.ALL_OPS.length; i++) {
                            if ((manualMask & (1 << i)) == 0) continue;
                            String op = BackgroundAppManager.ALL_OPS[i];
                            boolean failed = failedOps.contains(op);
                            if (allOk) {
                                body.append("  ✓ ").append(op).append("\n");
                            } else if (allFailed) {
                                body.append("  ✗ ").append(op).append("\n");
                            } else {
                                body.append(failed ? "  ✗ " : "  ✓ ").append(op).append("\n");
                            }
                        }
                    } else {
                        for (String op : failedOps) body.append("  ✗ ").append(op).append("\n");
                        String appopsRaw = extractDetailValue(detail, "appops");
                        if (appopsRaw != null && !appopsRaw.isEmpty()) body.append("  ").append(appopsRaw).append("\n");
                    }
                } else {
                    String[] opsForAction;
                    switch (entry.action != null ? entry.action : "") {
                        case "reapply-medium":
                        case "restrict-medium":
                            opsForAction = BackgroundAppManager.MEDIUM_OPS;
                            break;
                        default:
                            opsForAction = BackgroundAppManager.ALL_OPS;
                            break;
                    }
                    for (String op : opsForAction) {
                        if (allOk) {
                            body.append("  ✓ ").append(op).append("\n");
                        } else if (allFailed) {
                            body.append("  ✗ ").append(op).append("\n");
                        } else {
                            boolean failed = failedOps.contains(op);
                            body.append(failed ? "  ✗ " : "  ✓ ").append(op).append("\n");
                        }
                    }
                }
            }
            String forceStop = extractDetailValue(detail, "force-stop");
            String bucketVal = extractDetailValue(detail, "bucket");
            if (bucketVal != null) {
                body.append("\n").append(getString(R.string.log_detail_bucket_set)).append(": ").append(bucketBucketName(bucketVal)).append("\n");
            }
            if (forceStop != null) {
                body.append(getString(R.string.log_detail_force_stop)).append(": ").append(forceStop).append("\n");
            }
        }

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        TextView textView = new TextView(this);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        textView.setPadding(pad, pad, pad, pad);
        textView.setTextSize(13f);
        textView.setTypeface(android.graphics.Typeface.MONOSPACE);
        textView.setText(body.toString().trim());
        scrollView.addView(textView);

        String title = (entry.packageName == null || entry.packageName.equals("-"))
                ? humanizeLogAction(entry.action) : entry.packageName;

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(scrollView)
                .setNegativeButton(getString(R.string.dialog_close), (d, w) -> d.dismiss());

        if (entry.packageName != null && entry.packageName.contains(".")) {
            builder.setPositiveButton(getString(R.string.settings_open_app_info), (d, w) -> openAppInfo(entry.packageName));
        }

        applyCustomAccentToDialogButtons(builder.show());
    }

    private String extractDetailValue(String detail, String key) {
        if (detail == null) return null;
        if (key == null) return null;
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

    private void showSleepModeLogDialog() {
        SettingsListContent content = createSettingsListContent(
                getString(R.string.log_sleep_mode_empty), false);
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
        content.summaryText.setText(getString(R.string.stats_loading));

        AlertDialog dialog = createSettingsSurfaceDialog(
                getString(R.string.log_sleep_mode_title),
                getString(R.string.log_sleep_mode_dialog_subtitle),
                content.rootView);
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.dialog_close), (d, w) -> d.dismiss());
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.settings_restriction_log_clear), (d, w) -> {});
        dialog.show();
        applyCustomAccentToDialogButtons(dialog);

        Runnable reloadLog = () -> executor.execute(() -> {
            List<SettingsSurfaceRow> rows = buildSleepModeLogRows(SleepModeLogManager.readEntries(this));
            String summary = getString(R.string.settings_restriction_log_summary, rows.size());
            handler.post(() -> {
                adapter.setItems(rows);
                content.summaryText.setText(summary);
                content.loading.setVisibility(View.GONE);
                content.listView.setVisibility(View.VISIBLE);
                content.emptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
        reloadLog.run();

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            SleepModeLogManager.clear(this);
            reloadLog.run();
        });
    }

    private void showSchedulerLogDialog() {
        SettingsListContent content = createSettingsListContent(
                getString(R.string.log_scheduler_empty), false);
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
        content.summaryText.setText(getString(R.string.stats_loading));

        AlertDialog dialog = createSettingsSurfaceDialog(
                getString(R.string.log_scheduler_title),
                getString(R.string.log_scheduler_dialog_subtitle),
                content.rootView);
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.dialog_close), (d, w) -> d.dismiss());
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.settings_restriction_log_clear), (d, w) -> {});
        dialog.show();
        applyCustomAccentToDialogButtons(dialog);

        Runnable reloadLog = () -> executor.execute(() -> {
            List<SettingsSurfaceRow> rows = buildSchedulerLogRows(RestrictionsScheduler.SchedulerLog.readEntries(this));
            String summary = getString(R.string.settings_restriction_log_summary, rows.size());
            handler.post(() -> {
                adapter.setItems(rows);
                content.summaryText.setText(summary);
                content.loading.setVisibility(View.GONE);
                content.listView.setVisibility(View.VISIBLE);
                content.emptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
        reloadLog.run();

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            executor.execute(() -> {
                RestrictionsScheduler.SchedulerLog.clear(this);
                handler.post(reloadLog);
            });
        });
    }


    private static class TopOffender {
        final String appName, packageName;
        final int killCount, relaunchCount;
        final long recoveredKb;
        final double score;
        TopOffender(String appName, String packageName, int killCount, int relaunchCount, long recoveredKb, double score) {
            this.appName = appName; this.packageName = packageName;
            this.killCount = killCount; this.relaunchCount = relaunchCount;
            this.recoveredKb = recoveredKb; this.score = score;
        }
    }

    private static class KillHistoryEntry {
        final String appName, packageName, detail, badge, lastKillSource;
        final long lastEventTime;
        KillHistoryEntry(String appName, String packageName, String detail, String badge, long lastEventTime, String lastKillSource) {
            this.appName = appName; this.packageName = packageName;
            this.detail = detail; this.badge = badge; this.lastEventTime = lastEventTime;
            this.lastKillSource = lastKillSource;
        }
    }

    static class SettingsSurfaceRow {
        final String leadingText, title, subtitle, detail, badge, packageName;
        SettingsSurfaceRow(String leadingText, String title, String subtitle, String detail, String badge, String packageName) {
            this.leadingText = leadingText; this.title = title; this.subtitle = subtitle;
            this.detail = detail; this.badge = badge; this.packageName = packageName;
        }
    }

    private static class SettingsListContent {
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

    private class SettingsSurfaceAdapter extends BaseAdapter {
        private final List<SettingsSurfaceRow> items = new ArrayList<>();
        private final LayoutInflater inflater = LayoutInflater.from(StatisticsActivity.this);

        void setItems(List<SettingsSurfaceRow> newItems) {
            items.clear();
            if (newItems != null) items.addAll(newItems);
            notifyDataSetChanged();
        }

        @Override public int getCount() { return items.size(); }
        @Override public SettingsSurfaceRow getItem(int pos) { return (pos >= 0 && pos < items.size()) ? items.get(pos) : null; }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int position, View convertView, android.view.ViewGroup parent) {
            View view = convertView != null ? convertView : inflater.inflate(R.layout.item_top_offender, parent, false);
            SettingsSurfaceRow item = getItem(position);
            if (item == null) return view;
            bindOptionalText((TextView) view.findViewById(R.id.offender_rank), item.leadingText);
            ((TextView) view.findViewById(R.id.offender_name)).setText(item.title);
            bindOptionalText((TextView) view.findViewById(R.id.offender_package), item.subtitle);
            bindOptionalText((TextView) view.findViewById(R.id.offender_metrics), item.detail);
            bindOptionalText((TextView) view.findViewById(R.id.offender_score), item.badge);

            int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
            TextView rank  = view.findViewById(R.id.offender_rank);
            TextView score = view.findViewById(R.id.offender_score);
            if (accent == ACCENT_CUSTOM) {
                int color = sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR);
                if (rank  != null) rank.setTextColor(color);
                if (score != null) score.setTextColor(color);
            }
            if (score != null) {
                if (item.badge != null && !item.badge.trim().isEmpty()) {
                    int badgeColor = accent == ACCENT_CUSTOM
                            ? sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR)
                            : com.google.android.material.color.MaterialColors.getColor(score, com.google.android.material.R.attr.colorSecondary);
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


    private void bindOptionalText(TextView view, String text) {
        if (text == null || text.trim().isEmpty()) { view.setVisibility(View.GONE); view.setText(""); return; }
        view.setVisibility(View.VISIBLE);
        view.setText(text);
    }

    private androidx.appcompat.app.AlertDialog createSettingsSurfaceDialog(String title, String subtitle, View contentView) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_settings_surface, null);
        TextView subtitleView = dialogView.findViewById(R.id.dialog_surface_subtitle);
        FrameLayout contentContainer = dialogView.findViewById(R.id.dialog_surface_content);
        subtitleView.setText(subtitle);
        subtitleView.setVisibility(subtitle == null || subtitle.trim().isEmpty() ? View.GONE : View.VISIBLE);
        contentContainer.addView(contentView);

        int screenHeight;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.view.WindowMetrics metrics = getWindowManager().getCurrentWindowMetrics();
            Insets insets = WindowInsetsCompat.toWindowInsetsCompat(metrics.getWindowInsets())
                    .getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            screenHeight = metrics.getBounds().height() - insets.top - insets.bottom;
        } else {
            DisplayMetrics dm = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(dm);
            screenHeight = dm.heightPixels;
        }
        contentContainer.getLayoutParams().height = (int) (screenHeight * 0.55);

        return new MaterialAlertDialogBuilder(this).setTitle(title).setView(dialogView).create();
    }

    private SettingsListContent createSettingsListContent(String emptyText, boolean showFilter) {
        View contentView = getLayoutInflater().inflate(R.layout.dialog_top_offenders, null);
        AutoCompleteTextView filterSpinner = contentView.findViewById(R.id.top_offenders_filter);
        TextView summaryText = contentView.findViewById(R.id.top_offenders_summary);
        ProgressBar loading = contentView.findViewById(R.id.top_offenders_loading);
        ListView listView = contentView.findViewById(R.id.top_offenders_list);
        TextView emptyView = contentView.findViewById(R.id.top_offenders_empty);
        View filterLayout = contentView.findViewById(R.id.top_offenders_filter_layout);
        filterLayout.setVisibility(showFilter ? View.VISIBLE : View.GONE);
        emptyView.setText(emptyText);
        return new SettingsListContent(contentView, filterSpinner, summaryText, loading, listView, emptyView);
    }

    private List<SettingsSurfaceRow> buildTopOffenderRows(List<TopOffender> offenders) {
        List<SettingsSurfaceRow> rows = new ArrayList<>();
        for (int i = 0; i < offenders.size(); i++) {
            TopOffender o = offenders.get(i);
            rows.add(new SettingsSurfaceRow("#" + (i + 1), o.appName, o.packageName,
                    getString(R.string.stats_offender_metrics, o.killCount, o.relaunchCount, formatRecoveredSize(o.recoveredKb)),
                    getString(R.string.stats_offender_score, String.format(Locale.US, "%.1f", o.score)),
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
        String raw = entry.detail != null ? entry.detail : "";
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
        String type = entry.freezeType;
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

    private String resolveStatsAppName(com.gree1d.reappzuku.db.AppStats stats,
                                       com.gree1d.reappzuku.db.AppStatsDao appStatsDao) {
        if (stats.appName != null && !stats.appName.trim().isEmpty()) return stats.appName;
        try {
            android.content.pm.ApplicationInfo appInfo = getPackageManager().getApplicationInfo(stats.packageName, 0);
            CharSequence label = getPackageManager().getApplicationLabel(appInfo);
            if (label != null) {
                String name = label.toString();
                stats.appName = name;
                appStatsDao.updateAppName(stats.packageName, name);
                return name;
            }
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {}
        return stats.packageName;
    }

    private String resolveRestrictionTypeBadge(String action) {
        if (action == null) return "";
        switch (action.trim().toLowerCase()) {
            case "restrict-hard": case "reapply-hard": return getString(R.string.restriction_badge_hard);
            case "restrict-medium": case "reapply-medium": return getString(R.string.restriction_badge_medium);
            case "restrict-soft": case "reapply-soft": case "restrict": return getString(R.string.restriction_badge_soft);
            case "restrict-manual": case "reapply-manual": return getString(R.string.restriction_badge_manual);
            case "allow": return getString(R.string.restriction_badge_removed);
            default: return "";
        }
    }

    private String resolveSleepModeLogBadge(String action) {
        if (action == null) return "";
        switch (action.trim().toLowerCase()) {
            case "freeze":   return getString(R.string.log_badge_freeze);
            case "unfreeze": return getString(R.string.log_badge_unfreeze);
            default: return "";
        }
    }

    private String resolveSchedulerLogBadge(String action) {
        if (action == null) return "";
        switch (action.trim().toLowerCase()) {
            case "lift":    return getString(R.string.log_badge_lift);
            case "restore": return getString(R.string.log_badge_restore);
            default: return "";
        }
    }

    private String humanizeLogAction(String action) {
        if (action == null || action.trim().isEmpty()) return getString(R.string.log_action_event);
        String n = action.trim().replace('-', ' ').replace('_', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    private String humanizeLogOutcome(String outcome) {
        if (outcome == null || outcome.trim().isEmpty()) return "";
        String trimmed = outcome.trim();
        String suffix = "";
        int parenIdx = trimmed.indexOf(" (");
        if (parenIdx > 0 && trimmed.endsWith(")")) {
            suffix = trimmed.substring(parenIdx);
            trimmed = trimmed.substring(0, parenIdx);
        }
        String localized;
        switch (trimmed.toLowerCase()) {
            case "ok": localized = getString(R.string.log_outcome_ok); break;
            case "verified": localized = getString(R.string.log_outcome_verified); break;
            case "failed": localized = getString(R.string.log_outcome_failed); break;
            case "skipped": localized = getString(R.string.log_outcome_skipped); break;
            case "verify-failed": localized = getString(R.string.log_outcome_verify_failed); break;
            case "verify-unavailable": localized = getString(R.string.log_outcome_verify_unavailable); break;
            case "battery-whitelist-removed": localized = getString(R.string.log_outcome_battery_whitelist_removed); break;
            case "battery-whitelist-restored": localized = getString(R.string.log_outcome_battery_whitelist_restored); break;
            default:
                String n = trimmed.replace('-', ' ').replace('_', ' ');
                localized = n.toUpperCase(Locale.US);
        }
        return localized + suffix;
    }

    private void openAppInfo(String packageName) {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + packageName));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.settings_open_app_info_error), Toast.LENGTH_SHORT).show();
        }
    }

    private void applyCustomAccentToDialogButtons(AlertDialog dialog) {
        int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
        if (accent != ACCENT_CUSTOM) return;
        int nightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        int color = nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES ? Color.WHITE : Color.BLACK;
        for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
            android.widget.Button btn = dialog.getButton(which);
            if (btn != null) btn.setTextColor(color);
        }
    }

    private void applyCustomAccentToTabLayout(com.google.android.material.tabs.TabLayout tabs, int color) {
        tabs.setSelectedTabIndicatorColor(color);
        tabs.setTabTextColors(
                ContextCompat.getColor(this, R.color.text_secondary),
                color);
    }

    private String formatRecoveredSize(long kb) {
        if (kb < 1024) return getString(R.string.unit_kb, kb);
        if (kb < 1024 * 1024) return getString(R.string.unit_mb_precise, kb / 1024f);
        return getString(R.string.unit_gb_precise, kb / (1024f * 1024f));
    }

    private String formatRamMb(double mb) {
        if (mb < 1024.0) return getString(R.string.unit_mb, (int) mb);
        return getString(R.string.unit_gb, mb / 1024.0);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        binding = null;
    }
}
