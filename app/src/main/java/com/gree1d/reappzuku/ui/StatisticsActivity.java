package com.gree1d.reappzuku.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.gree1d.reappzuku.databinding.ActivityStatisticsBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.gree1d.reappzuku.core.ShellManager;
import com.gree1d.reappzuku.manager.BackgroundAppManager;
import com.gree1d.reappzuku.manager.CollectStatsManager;
import com.gree1d.reappzuku.core.BaseActivity;
import com.gree1d.reappzuku.service.ShappkyService;
import com.gree1d.reappzuku.R;
import com.gree1d.reappzuku.core.AppDebugManager;
import com.gree1d.reappzuku.core.AppDebugManager.Category;

import static com.gree1d.reappzuku.core.PreferenceKeys.*;
import static com.gree1d.reappzuku.core.AppConstants.*;

public class StatisticsActivity extends BaseActivity {

    private static final String TAG  = "StatisticsActivity";
    private static final String FILE = "StatisticsActivity";

    private static final int[] CHART_PERIODS_HOURS = { 2, 6, 12, 24 };

    private double batteryCapacityMah = 4000.0;

    static final int CHART_BATTERY = 0;
    static final int CHART_CPU     = 1;
    static final int CHART_RAM     = 2;
    static final int CHART_COUNT   = 3;

    private static final int[] SLICE_PALETTE = {
        0xFFE53935, 0xFF1E88E5, 0xFF43A047, 0xFFFB8C00, 0xFF8E24AA,
        0xFF00ACC1, 0xFFFFB300, 0xFF00897B, 0xFFF06292, 0xFF6D4C41,
        0xFF3949AB, 0xFF7CB342, 0xFFBDBDBD,
    };

    private String[] topOffenderFilterLabels;
    private String[] chartPeriodLabels;
    private int selectedPeriodIdx = 0;
    private int currentChartIdx   = CHART_BATTERY;

    List<CollectStatsManager.AppResourceStats> currentSorted = null;
    private double currentTotalHours = 0;

    private ActivityStatisticsBinding binding;
    ShellManager shellManager;
    BackgroundAppManager appManager;
    private CollectStatsManager collectStatsManager;
    final Handler handler = new Handler(Looper.getMainLooper());
    final ExecutorService executor = Executors.newCachedThreadPool();

    private StatisticsActivityDialogs dialogs;

    @SuppressWarnings("unchecked")
    private List<PieEntry>[] chartEntries = new List[CHART_COUNT];
    @SuppressWarnings("unchecked")
    private List<CollectStatsManager.AppResourceStats>[] chartSortedByMetric = new List[CHART_COUNT];
    @SuppressWarnings("unchecked")
    private List<CollectStatsManager.AppResourceStats>[] chartOthers = new List[CHART_COUNT];
    @SuppressWarnings("unchecked")
    private List<Integer>[] chartColors = new List[CHART_COUNT];
    private ChartMetric[] chartMetrics  = { ChartMetric.BATTERY, ChartMetric.CPU, ChartMetric.RAM };
    private double[]      chartTotals   = new double[CHART_COUNT];


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppDebugManager.d(Category.STATISTICS_PAGE, FILE + ": onCreate");
        binding = ActivityStatisticsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        topOffenderFilterLabels = getResources().getStringArray(R.array.settings_top_offender_filter_labels);
        chartPeriodLabels       = getResources().getStringArray(R.array.chart_period_labels);

        shellManager        = new ShellManager(getApplicationContext(), handler, executor);
        appManager          = new BackgroundAppManager(getApplicationContext(), handler, executor, shellManager);
        collectStatsManager = new CollectStatsManager(getApplicationContext(), handler, executor, shellManager);

        dialogs = new StatisticsActivityDialogs(this, appManager, topOffenderFilterLabels);

        setupToolbar();
        setupBottomNavigation();
        setupPeriodTabs();
        setupChartPager();
        setupListeners();

        batteryCapacityMah = collectStatsManager.getBatteryCapacityMah();
        AppDebugManager.d(Category.STATISTICS_PAGE, FILE + ": battery capacity mAh=" + batteryCapacityMah);

        loadCharts(CHART_PERIODS_HOURS[selectedPeriodIdx]);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AppDebugManager.d(Category.STATISTICS_PAGE, FILE + ": onDestroy");
        executor.shutdownNow();
        binding = null;
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

    private void setupListeners() {
        binding.layoutStats.setOnClickListener(v -> dialogs.showStatsDialog());
        binding.layoutTopOffenders.setOnClickListener(v -> dialogs.showTopOffendersDialog());
        binding.layoutRestrictionLog.setVisibility(
                appManager.supportsBackgroundRestriction() ? View.VISIBLE : View.GONE);
        binding.layoutRestrictionLog.setOnClickListener(v -> dialogs.showBackgroundRestrictionLogDialog());
        binding.layoutSleepModeLog.setOnClickListener(v -> dialogs.showSleepModeLogDialog());
        binding.layoutSchedulerLog.setOnClickListener(v -> dialogs.showSchedulerLogDialog());
    }


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
            case CHART_BATTERY: binding.tvChartTitle.setText(getString(R.string.chart_title_battery)); break;
            case CHART_CPU:     binding.tvChartTitle.setText(getString(R.string.chart_title_cpu));     break;
            case CHART_RAM:     binding.tvChartTitle.setText(getString(R.string.chart_title_ram));     break;
        }
    }


    private void loadCharts(int hours) {
        if (binding == null) return;

        if (!ShappkyService.isRunning()) {
            AppDebugManager.w(Category.STATISTICS_PAGE, FILE + ": loadCharts skipped, ShappkyService not running");
            showChartsLoading(false);
            binding.cardNoData.setVisibility(View.VISIBLE);
            binding.tvNoDataHint.setText(getString(R.string.stats_service_inactive_hint));
            binding.cardChartsPager.setVisibility(View.GONE);
            binding.tvPartialDataWarning.setVisibility(View.GONE);
            return;
        }

        AppDebugManager.d(Category.STATISTICS_PAGE, FILE + ": loadCharts hours=" + hours);
        showChartsLoading(true);
        collectStatsManager.getStatsForPeriodAsync(hours, periodStats -> {
            if (binding == null) return;
            showChartsLoading(false);
            if (!periodStats.hasData) {
                AppDebugManager.i(Category.STATISTICS_PAGE, FILE + ": loadCharts no data for hours=" + hours);
                binding.cardNoData.setVisibility(View.VISIBLE);
                binding.tvNoDataHint.setText(periodStats.dataHint);
                binding.cardChartsPager.setVisibility(View.GONE);
                binding.tvPartialDataWarning.setVisibility(View.GONE);
                return;
            }
            binding.cardNoData.setVisibility(View.GONE);
            binding.cardChartsPager.setVisibility(View.VISIBLE);

            if (periodStats.isPartialData) {
                AppDebugManager.w(Category.STATISTICS_PAGE, FILE + ": loadCharts partial data for hours=" + hours);
                binding.tvPartialDataWarning.setText(getString(R.string.stats_partial_data_warning));
                binding.tvPartialDataWarning.setVisibility(View.VISIBLE);
            } else {
                binding.tvPartialDataWarning.setVisibility(View.GONE);
            }

            List<CollectStatsManager.AppResourceStats> sorted = periodStats.sorted;
            currentSorted     = sorted;
            currentTotalHours = periodStats.actualHours;
            AppDebugManager.d(Category.STATISTICS_PAGE, FILE + ": loadCharts loaded " + sorted.size()
                    + " apps, actualHours=" + periodStats.actualHours);

            CollectStatsManager.AppResourceStats selfStats = null;
            for (CollectStatsManager.AppResourceStats s : sorted) {
                if (s.isSelf) { selfStats = s; break; }
            }

            buildPieChart(binding.chartBattery, binding.layoutBatteryOthers, sorted, ChartMetric.BATTERY, CHART_BATTERY);
            buildPieChart(binding.chartCpu,     binding.layoutCpuOthers,     sorted, ChartMetric.CPU,     CHART_CPU);
            buildPieChart(binding.chartRam,     binding.layoutRamOthers,     sorted, ChartMetric.RAM,     CHART_RAM);

            if (selfStats != null) {
                double totalBat = chartTotals[CHART_BATTERY];
                double totalCpu = chartTotals[CHART_CPU];
                double selfBatPct = totalBat > 0 ? (selfStats.batteryMah / totalBat) * 100.0 : 0;
                double selfCpuPct = totalCpu > 0 ? (selfStats.cpuPct    / totalCpu) * 100.0 : 0;
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

    private void showActiveChart(List<CollectStatsManager.AppResourceStats> sorted, double actualHours) {
        if (binding == null) return;
        binding.chartBattery.setVisibility(currentChartIdx == CHART_BATTERY ? View.VISIBLE : View.GONE);
        binding.chartCpu.setVisibility(currentChartIdx == CHART_CPU         ? View.VISIBLE : View.GONE);
        binding.chartRam.setVisibility(currentChartIdx == CHART_RAM         ? View.VISIBLE : View.GONE);

        double totalBat = 0, totalCpu = 0, totalRam = 0;
        for (CollectStatsManager.AppResourceStats s : sorted) {
            totalBat += s.batteryMah;
            totalCpu += s.cpuPct;
            totalRam += s.ramMb;
        }

        String centerText;
        switch (currentChartIdx) {
            case CHART_BATTERY: centerText = getString(R.string.stats_chart_total_battery, totalBat); break;
            case CHART_CPU:     centerText = String.format(Locale.getDefault(), "%.1f%%", Math.min(100.0, totalCpu)); break;
            case CHART_RAM:     centerText = formatRamMb(totalRam); break;
            default:            centerText = "";
        }
        binding.tvChartCenterValue.setText(centerText);
        binding.tvChartTotal.setText(centerText);
    }

    private void showChartsLoading(boolean loading) {
        if (binding == null) return;
        binding.layoutChartsLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
    }


    enum ChartMetric { BATTERY, CPU, RAM }

    private void buildPieChart(PieChart chart,
                                android.view.ViewGroup othersContainer,
                                List<CollectStatsManager.AppResourceStats> sorted,
                                ChartMetric metric,
                                int chartIdx) {
        double total = 0;
        for (CollectStatsManager.AppResourceStats s : sorted) total += metricValue(s, metric);
        if (total <= 0) {
            AppDebugManager.w(Category.STATISTICS_PAGE, FILE + ": buildPieChart skipped for metric=" + metric
                    + ", total<=0");
            return;
        }

        List<CollectStatsManager.AppResourceStats> byCurrent = new ArrayList<>(sorted);
        byCurrent.sort((a, b) -> Double.compare(metricValue(b, metric), metricValue(a, metric)));

        List<PieEntry> entries = new ArrayList<>();
        List<CollectStatsManager.AppResourceStats> othersList = new ArrayList<>();
        double othersValue = 0;

        for (int i = 0; i < byCurrent.size(); i++) {
            CollectStatsManager.AppResourceStats s = byCurrent.get(i);
            double val  = metricValue(s, metric);
            float  pct  = (float)(val / total * 100);
            boolean forceShow = i < CollectStatsManager.MIN_TOP_SLICES;
            if (forceShow || pct > CollectStatsManager.OTHERS_THRESHOLD_PCT) {
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
            List<CollectStatsManager.AppResourceStats> byCurrent,
            List<CollectStatsManager.AppResourceStats> othersList,
            List<Integer> colors,
            ChartMetric metric,
            double total) {

        android.widget.LinearLayout legend = binding.layoutChartLegend;
        if (legend == null) return;
        legend.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(this);
        int dotSizePx      = (int)(10 * getResources().getDisplayMetrics().density);
        int marginEndPx    = (int)( 7 * getResources().getDisplayMetrics().density);
        int rowMarginBottomPx = (int)(7 * getResources().getDisplayMetrics().density);

        for (int i = 0; i < entries.size(); i++) {
            PieEntry pe    = entries.get(i);
            int      color = colors.get(i);
            float    pct   = total > 0 ? (float)(pe.getValue() / total * 100) : 0f;

            String name;
            final String pkg;
            Object tag = pe.getData();
            final boolean isOthers = "__others__".equals(tag);
            if (isOthers) {
                name = getString(R.string.chart_others_label);
                pkg  = "__others__";
            } else {
                pkg = tag != null ? tag.toString() : "";
                CollectStatsManager.AppResourceStats found = findByPkg(byCurrent, pkg);
                name = (found != null && found.appName != null) ? found.appName : pkg;
            }
            final String finalName = name;
            final List<CollectStatsManager.AppResourceStats> finalOthers = othersList;
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
            row.setBackground(obtainStyledAttributes(new int[]{android.R.attr.selectableItemBackground}).getDrawable(0));
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> {
                if (isOthers) dialogs.showOthersDialog(finalOthers, metric, finalTotal);
                else          openAppDetail(pkg, finalName);
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
            if (i == count - 1 && count > 1) colors.add(SLICE_PALETTE[SLICE_PALETTE.length - 1]);
            else                              colors.add(SLICE_PALETTE[i % paletteSize]);
        }
        return colors;
    }


    private void openAppDetail(String packageName, String appName) {
        double totalCpuPct = 0, totalRamMb = 0;
        if (currentSorted != null) {
            for (CollectStatsManager.AppResourceStats s : currentSorted) {
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

    android.content.SharedPreferences prefs() {
        return sharedPreferences;
    }

    double metricValue(CollectStatsManager.AppResourceStats s, ChartMetric m) {
        if (s == null) return 0;
        switch (m) {
            case BATTERY: return s.batteryMah;
            case CPU:     return s.cpuPct;
            case RAM:     return s.ramMb;
            default:      return 0;
        }
    }

    private CollectStatsManager.AppResourceStats findByPkg(
            List<CollectStatsManager.AppResourceStats> list, String pkg) {
        for (CollectStatsManager.AppResourceStats s : list) {
            if (s.packageName.equals(pkg)) return s;
        }
        return null;
    }


    private void applyCustomAccentToTabLayout(com.google.android.material.tabs.TabLayout tabs, int color) {
        tabs.setSelectedTabIndicatorColor(color);
        tabs.setTabTextColors(
                ContextCompat.getColor(this, R.color.text_secondary),
                color);
    }

    private String formatRamMb(double mb) {
        if (mb < 1024.0) return getString(R.string.unit_mb, (int) mb);
        return getString(R.string.unit_gb, mb / 1024.0);
    }
}
