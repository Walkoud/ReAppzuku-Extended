package com.gree1d.reappzuku;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.text.SpannableString;
import android.text.Spannable;
import android.text.style.ForegroundColorSpan;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.gree1d.reappzuku.databinding.ActivityMainBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import android.widget.PopupMenu;
import android.widget.Toast;
import android.net.Uri;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import android.widget.PopupWindow;
import android.widget.ImageView;
import android.view.LayoutInflater;
import android.widget.CheckBox;

import rikka.shizuku.Shizuku;

import static com.gree1d.reappzuku.PreferenceKeys.*;
import static com.gree1d.reappzuku.AppConstants.*;

public class MainActivity extends BaseActivity {
    private static final String TAG = "MainActivity";
    private static final int NOTIFICATION_PERMISSION_CODE = 1;

    private ActivityMainBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ShellManager shellManager;
    private BackgroundAppManager appManager;
    private AutoKillManager autoKillManager;
    private RamMonitor ramMonitor;
    private CpuMonitor cpuMonitor;
    private BackgroundAppsRecyclerViewAdapter listAdapter;
    private final List<AppModel> appsDataList = new ArrayList<>();
    private final List<AppModel> fullAppsList = new ArrayList<>();
    private String currentSearchQuery = "";
    private int currentSortMode = AppConstants.SORT_MODE_DEFAULT;
    private MenuItem selectAllMenuItem;

    private int appliedAccent;
    private boolean appliedIsAmoled;
    private int appliedCustomColor;
    private int appliedOnColor;

    private final Shizuku.OnRequestPermissionResultListener shizukuPermissionListener = (requestCode, grantResult) -> {
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            loadBackgroundApps();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[] { Manifest.permission.POST_NOTIFICATIONS },
                        NOTIFICATION_PERMISSION_CODE);
            }
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
        boolean isAmoled = sharedPreferences.getBoolean(KEY_AMOLED, false);
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
            binding.toolbar.setTitleTextColor(isLightAccent() ? Color.BLACK : Color.WHITE);
        }
        appliedAccent = accent;
        appliedIsAmoled = isAmoled;
        appliedCustomColor = sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR);
        appliedOnColor = sharedPreferences.getInt(KEY_ACCENT_ON_COLOR, ACCENT_ON_WHITE);

        shellManager = new ShellManager(this, handler, executor);
        appManager = new BackgroundAppManager(this, handler, executor, shellManager);
        autoKillManager = new AutoKillManager(this, handler, executor, shellManager, appManager.getCurrentAppsList());
        ramMonitor = new RamMonitor(this, handler, binding.ramUsage, binding.ramUsageText);
        cpuMonitor = new CpuMonitor(handler, executor, shellManager);
        cpuMonitor.setOnCpuUpdateListener(() -> {
            if (currentSortMode == AppConstants.SORT_MODE_CPU_DESC
                    || currentSortMode == AppConstants.SORT_MODE_CPU_ASC) {
                appManager.sortAppList(appsDataList, currentSortMode);
            }
            android.util.Log.d("CpuSort", "mode=" + currentSortMode + " appsDataList.size=" + appsDataList.size());
            for (int i = 0; i < Math.min(4, appsDataList.size()); i++) {
                AppModel a = appsDataList.get(i);
                android.util.Log.d("CpuSort", i + ": " + a.getAppName() + " cpu=" + a.getCpuUsage());
            }
            listAdapter.updateCpu(appsDataList);
        });

        listAdapter = new BackgroundAppsRecyclerViewAdapter(this);
        binding.recyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        binding.recyclerView.setAdapter(listAdapter);

        cpuMonitor.setAppsList(fullAppsList);

        setupKillButton();
        setupBottomNavigation();
        setupListeners();

        binding.swiperefreshlayout1.post(this::recalculateListHeight);
        loadSettingsAndApplyToManager();

        shellManager.setShizukuPermissionListener(shizukuPermissionListener);
        shellManager.setOnRootCheckCompleteListener(() -> shellManager.checkShellPermissions());
        loadBackgroundApps();
        ramMonitor.startMonitoring();
    }

    private void recalculateListHeight() {
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int appNavBarPx = (int) (64 * getResources().getDisplayMetrics().density);

        int systemNavHeight = 0;
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(binding.swiperefreshlayout1);
        if (insets != null) {
            systemNavHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
        }

        int[] srlPos = new int[2];
        binding.swiperefreshlayout1.getLocationOnScreen(srlPos);
        int srlTop = srlPos[1];

        int desiredHeight = screenHeight - appNavBarPx - systemNavHeight - srlTop;
        if (desiredHeight > 0) {
            android.view.ViewGroup.LayoutParams params = binding.swiperefreshlayout1.getLayoutParams();
            params.height = desiredHeight;
            binding.swiperefreshlayout1.setLayoutParams(params);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            binding.swiperefreshlayout1.post(this::recalculateListHeight);
        }
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.navIconMain.setSelected(true);
        binding.bottomNavigation.navIconSettings.setSelected(false);
        binding.bottomNavigation.navIconStatistics.setSelected(false);
        binding.bottomNavigation.navBtnMain.setOnClickListener(v -> {});
        binding.bottomNavigation.navBtnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        binding.bottomNavigation.navBtnStatistics.setOnClickListener(v ->
                startActivity(new Intent(this, StatisticsActivity.class)));
        applyNavBarInsets(binding.bottomNavigation.getRoot());
    }

    private boolean isLightAccent() {
        int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
        if (accent == ACCENT_CUSTOM) {
            return sharedPreferences.getInt(KEY_ACCENT_ON_COLOR, ACCENT_ON_WHITE) == ACCENT_ON_BLACK;
        }
        return accent == ACCENT_APRICOT || accent == ACCENT_SKY ||
                accent == ACCENT_PAPAYA || accent == ACCENT_LAVENDER ||
                accent == ACCENT_MINT || accent == ACCENT_PEACH ||
                accent == ACCENT_POWDER || accent == ACCENT_FOG;
    }

    private void setupKillButton() {
        int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
        if (accent == ACCENT_CUSTOM) {
            int customColor = sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR);
            int onColor = sharedPreferences.getInt(KEY_ACCENT_ON_COLOR, ACCENT_ON_WHITE) == ACCENT_ON_BLACK
                    ? Color.BLACK : Color.WHITE;
            binding.killButton.setBackgroundTintList(
                    ColorStateList.valueOf(customColor));
            binding.killButton.setTextColor(onColor);
        } else if (accent != ACCENT_SYSTEM) {
            int accentColor = resolveColorAttr(androidx.appcompat.R.attr.colorPrimary);
            binding.killButton.setBackgroundTintList(
                    ColorStateList.valueOf(accentColor));
            binding.killButton.setTextColor(isLightAccent() ? Color.BLACK : Color.WHITE);
        } else {
            binding.killButton.setBackgroundTintList(
                    ColorStateList.valueOf(Color.parseColor("#0136FF")));
            binding.killButton.setTextColor(Color.WHITE);
        }
    }

    private void setupListeners() {
        binding.swiperefreshlayout1.setOnRefreshListener(this::loadBackgroundApps);
        binding.killButton.setOnClickListener(view -> killSelectedApps());
        binding.scanButtonLayout.setOnClickListener(v -> showSystemScanDialog());

        listAdapter.setOnAppActionListener(new BackgroundAppsRecyclerViewAdapter.OnAppActionListener() {
            @Override
            public void onKillApp(AppModel app, int position) {
                autoKillManager.killApp(app.getPackageName(), MainActivity.this::loadBackgroundApps);
            }

            @Override
            public void onToggleWhitelist(AppModel app, int position) {
                boolean isNowWhitelisted = autoKillManager.toggleWhitelist(app.getPackageName());
                app.setWhitelisted(isNowWhitelisted);
                listAdapter.notifyItemChanged(position);

                String message = isNowWhitelisted
                        ? getString(R.string.main_added_to_whitelist)
                        : getString(R.string.main_removed_from_whitelist);
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAppClick(AppModel app, int position) {
                if (app.isProtected() || app.isWhitelisted()) {
                    return;
                }
                app.setSelected(!app.isSelected());
                boolean hasSelection = fullAppsList.stream().anyMatch(AppModel::isSelected);
                if (!listAdapter.refreshSelectionMode(hasSelection)) {
                    listAdapter.notifyItemChanged(position);
                }
                updateSelectMenuVisibility();
            }

            @Override
            public void onOverflowClick(AppModel app, View anchor) {
                showAppOptionsMenu(app, anchor);
            }
        });
    }

    private void showAppOptionsMenu(AppModel app, View anchor) {
            String packageName = app.getPackageName();
    
            int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
            int accentColor = accent == ACCENT_CUSTOM
                    ? sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR)
                    : resolveColorAttr(androidx.appcompat.R.attr.colorPrimary);
    
            AppOptionsBottomSheet sheet = AppOptionsBottomSheet.newInstance(
                    app,
                    appManager.getWhitelistedApps().contains(packageName),
                    autoKillManager.getBlacklistedApps().contains(packageName),
                    appManager.getHiddenApps().contains(packageName),
                    appManager.supportsBackgroundRestriction(),
                    getBackgroundRestrictionMenuTitle(app),
                    accentColor
            );
    
            try {
                android.graphics.drawable.Drawable icon = getPackageManager()
                        .getApplicationIcon(packageName);
                sheet.setAppIcon(icon);
            } catch (PackageManager.NameNotFoundException ignored) {}
    
            sheet.setListener(new AppOptionsBottomSheet.Listener() {
                @Override
                public void onAppInfo() {
                    openAppInfo(packageName);
                }
    
                @Override
                public void onAppTriggers() {
                    showAppTriggersDialog(app);
                }
    
                @Override
                public void onUninstall() {
                    showUninstallConfirmation(app);
                }
    
                @Override
                public void onToggleHiddenSingle() {
                    toggleListMembership(app, "hidden");
                }
    
                @Override
                public void onToggleWhitelist(boolean nowChecked) {
                    toggleListMembership(app, "whitelist");
                }
    
                @Override
                public void onToggleBlacklist(boolean nowChecked) {
                    toggleListMembership(app, "blacklist");
                }
    
                @Override
                public void onToggleHidden(boolean nowChecked) {
                    toggleListMembership(app, "hidden");
                }
    
                @Override
                public void onToggleBackgroundRestriction(boolean nowChecked) {
                    toggleBackgroundRestriction(app);
                }
            });
    
            sheet.show(getSupportFragmentManager(), "app_options");
        }


    private void openAppInfo(String packageName) {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + packageName));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.main_open_app_info_error), Toast.LENGTH_SHORT).show();
        }
    }

    private void showUninstallConfirmation(AppModel app) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.main_uninstall_title, app.getAppName()))
                .setMessage(getString(R.string.main_uninstall_message))
                .setPositiveButton(getString(R.string.main_uninstall_confirm), (d, which) -> {
                    autoKillManager.uninstallPackage(app.getPackageName(), this::loadBackgroundApps);
                })
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .create();
        dialog.show();
        tintDialogButtons(dialog);
    }

    private void showAppTriggersDialog(AppModel app) {
        AlertDialog loadingDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.menu_app_triggers) + ": " + app.getAppName())
                .setMessage(getString(R.string.triggers_loading))
                .setCancelable(true)
                .create();
        loadingDialog.show();

        executor.execute(() -> {
            AppTriggersAnalyzer analyzer = new AppTriggersAnalyzer(MainActivity.this, shellManager);
            List<AppTriggersAnalyzer.TriggerInfo> triggers = analyzer.analyze(app.getPackageName());
            AppTriggersAnalyzer.AppStatus status = analyzer.resolveAppStatus(app.getPackageName());
            int aggressionScore = analyzer.calculateAggressionScore(triggers);

            handler.post(() -> {
                loadingDialog.dismiss();
                if (isFinishing() || isDestroyed()) return;
                showTriggersResult(app, triggers, status, aggressionScore);
            });
        });
    }

    private void showSystemScanDialog() {
        AlertDialog loadingDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.scansystem_dialog_title))
                .setMessage(getString(R.string.scansystem_scanning))
                .setCancelable(true)
                .create();
        loadingDialog.show();

        List<AppModel> snapshot = fullAppsList.stream()
                .filter(app -> !app.isProtected())
                .filter(app -> !app.isPersistentApp())
                .filter(app -> !app.isWhitelisted())
                .filter(app -> !app.getPackageName().equals(getPackageName()))
                .collect(Collectors.toList());

        executor.execute(() -> {
            ScanSystem scanner = new ScanSystem(MainActivity.this, shellManager);
            List<ScanSystem.AppLoad> loads = scanner.scan(snapshot);

            handler.post(() -> {
                loadingDialog.dismiss();
                if (isFinishing() || isDestroyed()) return;

                if (loads.isEmpty()) {
                    AlertDialog emptyDialog = new MaterialAlertDialogBuilder(this)
                            .setTitle(getString(R.string.scansystem_dialog_title))
                            .setMessage(getString(R.string.scansystem_no_load))
                            .setPositiveButton(getString(R.string.dialog_close), null)
                            .create();
                    emptyDialog.show();
                    tintDialogButtons(emptyDialog);
                    return;
                }

                View dialogView = getLayoutInflater().inflate(R.layout.dialog_system_scan, null);
                androidx.recyclerview.widget.RecyclerView recycler =
                        dialogView.findViewById(R.id.scan_recycler);
                recycler.setLayoutManager(
                        new androidx.recyclerview.widget.LinearLayoutManager(this));
                recycler.setAdapter(new ScanResultAdapter(this, loads));

                AlertDialog resultDialog = new MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.scansystem_dialog_title))
                        .setView(dialogView)
                        .setPositiveButton(getString(R.string.dialog_close), null)
                        .create();
                resultDialog.show();
                tintDialogButtons(resultDialog);
            });
        });
    }

    private void showTriggersResult(AppModel app, List<AppTriggersAnalyzer.TriggerInfo> triggers,
                                     AppTriggersAnalyzer.AppStatus status, int aggressionScore) {

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_app_triggers, null);
        LinearLayout container = dialogView.findViewById(R.id.triggers_container);

        if (status != null) {
            String statusLabel;
            String statusHint;
            switch (status) {
                case ACTIVE:
                    statusLabel = getString(R.string.triggers_status_active);
                    statusHint  = getString(R.string.app_status_hint_foreground);
                    break;
                case BACKGROUND_SERVICE:
                    statusLabel = getString(R.string.triggers_status_background);
                    statusHint  = getString(R.string.app_status_hint_fgs);
                    break;
                case BACKGROUND:
                    statusLabel = getString(R.string.triggers_status_background);
                    statusHint  = null;
                    break;
                case CACHED_WITH_SERVICE:
                    statusLabel = getString(R.string.triggers_status_cached);
                    statusHint  = getString(R.string.app_status_hint_cached_service);
                    break;
                case CACHED_RECENT:
                    statusLabel = getString(R.string.triggers_status_cached);
                    statusHint  = getString(R.string.app_status_hint_cached_recent);
                    break;
                default:
                    statusLabel = getString(R.string.triggers_status_cached);
                    statusHint  = getString(R.string.app_status_hint_cached_idle);
                    break;
            }

            TextView statusView = new TextView(this);
            String labelPart = getString(R.string.triggers_status_label_prefix);
            String fullText = statusHint != null
                    ? labelPart + " " + statusLabel + "  •  " + statusHint
                    : labelPart + " " + statusLabel;
            SpannableString spannable = new SpannableString(fullText);
            int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
            int primaryColor = (accent == ACCENT_CUSTOM)
                    ? sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR)
                    : resolveColorAttr(androidx.appcompat.R.attr.colorPrimary);
            spannable.setSpan(new ForegroundColorSpan(primaryColor), 0, labelPart.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            statusView.setText(spannable);
            statusView.setTextSize(13f);
            LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            statusLp.setMargins(8, 4, 8, 4);
            statusView.setLayoutParams(statusLp);
            container.addView(statusView);
        }

        TextView scoreView = new TextView(this);
        String scoreLabelPart = getString(R.string.triggers_aggression_label_prefix);
        String scoreFullText = scoreLabelPart + " " + aggressionScore + "/100";
        SpannableString scoreSpannable = new SpannableString(scoreFullText);
        int accentScore = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
        int scoreColor = (accentScore == ACCENT_CUSTOM)
                ? sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR)
                : resolveColorAttr(androidx.appcompat.R.attr.colorPrimary);
        scoreSpannable.setSpan(new ForegroundColorSpan(scoreColor), 0, scoreLabelPart.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        scoreView.setText(scoreSpannable);
        scoreView.setTextSize(13f);
        LinearLayout.LayoutParams scoreLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        scoreLp.setMargins(8, 4, 8, 16);
        scoreView.setLayoutParams(scoreLp);
        container.addView(scoreView);

        List<AppTriggersAnalyzer.TriggerInfo> activeNow = new ArrayList<>();
        List<AppTriggersAnalyzer.TriggerInfo> canWake   = new ArrayList<>();
        List<AppTriggersAnalyzer.TriggerInfo> other     = new ArrayList<>();
        for (AppTriggersAnalyzer.TriggerInfo t : triggers) {
            switch (t.group) {
                case ACTIVE_NOW: activeNow.add(t); break;
                case CAN_WAKE:   canWake.add(t);   break;
                default:         other.add(t);     break;
            }
        }

        if (!activeNow.isEmpty()) {
            addSectionHeader(container, getString(R.string.triggers_section_active_now));
            for (AppTriggersAnalyzer.TriggerInfo t : activeNow) addTriggerItem(container, t);
        }
        if (!canWake.isEmpty()) {
            addSectionHeader(container, getString(R.string.triggers_section_can_wake));
            for (AppTriggersAnalyzer.TriggerInfo t : canWake) addTriggerItem(container, t);
        }
        if (!other.isEmpty()) {
            addSectionHeader(container, getString(R.string.triggers_section_other));
            for (AppTriggersAnalyzer.TriggerInfo t : other) addTriggerItem(container, t);
        }

        AlertDialog triggersDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.menu_app_triggers) + ": " + app.getAppName())
                .setView(dialogView)
                .setPositiveButton(getString(R.string.dialog_close), null)
                .create();
        triggersDialog.show();
        tintDialogButtons(triggersDialog);
    }

    private void addSectionHeader(LinearLayout container, String title) {
        TextView header = new TextView(this);
        header.setText(title);
        header.setTextSize(12f);
        int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
        int color = (accent == ACCENT_CUSTOM)
                ? sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR)
                : resolveColorAttr(androidx.appcompat.R.attr.colorPrimary);
        header.setTextColor(color);
        header.setAllCaps(true);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(8, 24, 8, 4);
        header.setLayoutParams(lp);
        container.addView(header);
    }

    private void addTriggerItem(LinearLayout container, AppTriggersAnalyzer.TriggerInfo trigger) {
        View item = getLayoutInflater().inflate(R.layout.item_trigger, container, false);

        TextView categoryView    = item.findViewById(R.id.trigger_category);
        TextView detailView      = item.findViewById(R.id.trigger_detail);
        TextView explanationView = item.findViewById(R.id.trigger_explanation);
        TextView arrowView       = item.findViewById(R.id.trigger_arrow);
        View headerView          = item.findViewById(R.id.trigger_header);

        categoryView.setText(trigger.category);
        detailView.setText(trigger.detail);
        explanationView.setText(trigger.explanation);

        if (trigger.explanation == null || trigger.explanation.isEmpty()) {
            arrowView.setVisibility(View.INVISIBLE);
        } else {
            headerView.setOnClickListener(v -> {
                boolean isExpanded = explanationView.getVisibility() == View.VISIBLE;
                explanationView.setVisibility(isExpanded ? View.GONE : View.VISIBLE);
                arrowView.setText(isExpanded ? "▶" : "▼");
            });
        }

        container.addView(item);
    }

    private int resolveColorAttr(int attr) {
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    private void tintDialogButtons(AlertDialog dialog) {
        int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
        if (accent != ACCENT_CUSTOM) return;
        int color = sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR);
        ColorStateList tint = ColorStateList.valueOf(color);
        Button positive = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        Button negative = dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE);
        Button neutral  = dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL);
        if (positive != null) positive.setTextColor(tint);
        if (negative != null) negative.setTextColor(tint);
        if (neutral  != null) neutral.setTextColor(tint);
    }

    private void toggleListMembership(AppModel app, String listType) {
        String packageName = app.getPackageName();
        Set<String> currentSet;
        String addedMsg, removedMsg;

        switch (listType) {
            case "whitelist":
                currentSet = appManager.getWhitelistedApps();
                addedMsg = getString(R.string.main_added_to_whitelist);
                removedMsg = getString(R.string.main_removed_from_whitelist);
                break;
            case "blacklist":
                currentSet = autoKillManager.getBlacklistedApps();
                addedMsg = getString(R.string.main_added_to_blacklist);
                removedMsg = getString(R.string.main_removed_from_blacklist);
                break;
            case "hidden":
                currentSet = appManager.getHiddenApps();
                addedMsg = getString(R.string.main_app_hidden);
                removedMsg = getString(R.string.main_app_visible);
                break;
            default:
                return;
        }

        boolean wasInList = currentSet.contains(packageName);
        if (wasInList) {
            currentSet.remove(packageName);
        } else {
            currentSet.add(packageName);
        }

        switch (listType) {
            case "whitelist":
                appManager.saveWhitelistedApps(currentSet);
                app.setWhitelisted(!wasInList);
                break;
            case "blacklist":
                autoKillManager.saveBlacklistedApps(currentSet);
                break;
            case "hidden":
                appManager.saveHiddenApps(currentSet);
                break;
        }

        int adapterPos = -1;
        for (int i = 0; i < appsDataList.size(); i++) {
            if (appsDataList.get(i).getPackageName().equals(packageName)) {
                adapterPos = i;
                break;
            }
        }
        if (adapterPos >= 0) {
            listAdapter.notifyItemChanged(adapterPos);
        }
        Toast.makeText(this, wasInList ? removedMsg : addedMsg, Toast.LENGTH_SHORT).show();
    }

    private void toggleBackgroundRestriction(AppModel app) {
        if (app.needsBackgroundRestrictionReapply()) {
            showOutOfSyncRestrictionDialog(app);
            return;
        }
        if (app.isBackgroundRestrictionExternal()) {
            showExternalRestrictionDialog(app);
            return;
        }
        boolean enableRestriction = !app.isBackgroundRestrictionDesired();
        if (enableRestriction && app.isSystemApp()) {
            AlertDialog warnDialog = new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.main_system_app_warning_title))
                    .setMessage(getString(R.string.main_system_app_restriction_warning))
                    .setPositiveButton(getString(R.string.dialog_apply), (dialog, which) -> applyBackgroundRestriction(app, true))
                    .setNegativeButton(getString(R.string.dialog_cancel), null)
                    .create();
            warnDialog.show();
            tintDialogButtons(warnDialog);
            return;
        }
        applyBackgroundRestriction(app, enableRestriction);
    }

    private void showOutOfSyncRestrictionDialog(AppModel app) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.main_restriction_out_of_sync_title))
                .setMessage(getString(R.string.main_restriction_out_of_sync_message, app.getAppName()))
                .setPositiveButton(getString(R.string.main_restriction_resume), (d, which) -> applyBackgroundRestriction(app, true))
                .setNeutralButton(getString(R.string.main_restriction_remove_from_list), (d, which) -> applyBackgroundRestriction(app, false))
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .create();
        dialog.show();
        tintDialogButtons(dialog);
    }

    private void showExternalRestrictionDialog(AppModel app) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.main_restriction_external_title))
                .setMessage(getString(R.string.main_restriction_external_message, app.getAppName()))
                .setPositiveButton(getString(R.string.main_restriction_add_to_reappzuku), (d, which) -> applyBackgroundRestriction(app, true))
                .setNeutralButton(getString(R.string.main_restriction_remove), (d, which) -> applyBackgroundRestriction(app, false))
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .create();
        dialog.show();
        tintDialogButtons(dialog);
    }

    private void applyBackgroundRestriction(AppModel app, boolean enableRestriction) {
        appManager.setBackgroundRestricted(app.getPackageName(), enableRestriction, this::loadBackgroundApps);
    }

    private String getBackgroundRestrictionMenuTitle(AppModel app) {
        if (app.needsBackgroundRestrictionReapply()) {
            return getString(R.string.main_restriction_menu_out_of_sync);
        }
        if (app.isBackgroundRestrictionExternal()) {
            return getString(R.string.main_restriction_menu_external);
        }
        if (app.isBackgroundRestrictionDesired() && !app.isBackgroundRestrictionActualKnown()) {
            return getString(R.string.main_restriction_menu_saved);
        }
        return getString(R.string.main_restriction_menu_default);
    }

    private void loadBackgroundApps() {
        binding.swiperefreshlayout1.setRefreshing(true);

        final Set<String> selectedPackages = fullAppsList.stream()
                .filter(AppModel::isSelected)
                .map(AppModel::getPackageName)
                .collect(Collectors.toSet());

        appManager.loadBackgroundApps(result -> {
            fullAppsList.clear();
            fullAppsList.addAll(result);

            for (AppModel app : fullAppsList) {
                if (selectedPackages.contains(app.getPackageName()) && !app.isProtected()) {
                    app.setSelected(true);
                }
            }

            filterApps(currentSearchQuery);
            binding.runningApps.setText(getString(R.string.main_active_apps_count, fullAppsList.size()));
            binding.swiperefreshlayout1.setRefreshing(false);
            cpuMonitor.refreshAppsList(fullAppsList);
        });
    }

    private void filterApps(String query) {
        currentSearchQuery = query;
        appsDataList.clear();
        if (query == null || query.isEmpty()) {
            appsDataList.addAll(fullAppsList);
        } else {
            String lowerQuery = query.toLowerCase();
            for (AppModel app : fullAppsList) {
                if (app.getAppName().toLowerCase().contains(lowerQuery) ||
                        app.getPackageName().toLowerCase().contains(lowerQuery)) {
                    appsDataList.add(app);
                }
            }
        }
        appManager.sortAppList(appsDataList, currentSortMode);
        listAdapter.submitList(new ArrayList<>(appsDataList));
        updateSelectMenuVisibility();
    }

    private void killSelectedApps() {
        List<String> packagesToKill = fullAppsList.stream()
                .filter(AppModel::isSelected)
                .map(AppModel::getPackageName)
                .collect(Collectors.toList());

        binding.killButton.setVisibility(View.GONE);
        binding.bottomNavigation.getRoot().setVisibility(View.VISIBLE);

        for (AppModel app : fullAppsList) {
            app.setSelected(false);
        }
        listAdapter.submitList(new ArrayList<>(appsDataList));

        autoKillManager.killPackages(packagesToKill, () -> {
            loadBackgroundApps();
        });
    }

    private void updateKillButtonText() {
        long count = fullAppsList.stream().filter(AppModel::isSelected).count();
        binding.killButton.setText(count >= 2
                ? getString(R.string.fab_kill_apps) + " (" + count + ")"
                : getString(R.string.fab_kill_app) + " (" + count + ")");
    }

    private void updateSelectMenuVisibility() {
        boolean hasSelection = fullAppsList.stream().anyMatch(AppModel::isSelected);
        if (hasSelection) {
            binding.bottomNavigation.getRoot().setVisibility(View.GONE);
            int navBarHeight = 0;
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(binding.coordinator);
            if (insets != null) {
                navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            }
            int basePx = (int) (64 * getResources().getDisplayMetrics().density);
            android.view.ViewGroup.LayoutParams params = binding.killButton.getLayoutParams();
            params.height = basePx + navBarHeight;
            binding.killButton.setLayoutParams(params);
            binding.killButton.setPadding(0, 0, 0, navBarHeight);
            binding.killButton.setVisibility(View.VISIBLE);
            updateKillButtonText();
        } else {
            binding.killButton.setVisibility(View.GONE);
            binding.bottomNavigation.getRoot().setVisibility(View.VISIBLE);
        }
        updateSelectAllMenuItem();
    }

    private void updateSelectAllMenuItem() {
        if (selectAllMenuItem == null) return;
        boolean hasSelection = fullAppsList.stream().anyMatch(AppModel::isSelected);
        if (hasSelection) {
            selectAllMenuItem.setIcon(R.drawable.ic_unselect_all);
            selectAllMenuItem.setTitle(getString(R.string.menu_deselect_all));
        } else {
            selectAllMenuItem.setIcon(R.drawable.ic_select_all);
            selectAllMenuItem.setTitle(getString(R.string.menu_select_all));
        }
        tintMenuItem(selectAllMenuItem);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupBottomNavigation();

        int newAccent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
        boolean newIsAmoled = sharedPreferences.getBoolean(KEY_AMOLED, false);
        int newCustomColor = sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR);
        int newOnColor = sharedPreferences.getInt(KEY_ACCENT_ON_COLOR, ACCENT_ON_WHITE);
        if (newAccent != appliedAccent || newIsAmoled != appliedIsAmoled
                || (newAccent == ACCENT_CUSTOM && newCustomColor != appliedCustomColor)
                || (newAccent == ACCENT_CUSTOM && newOnColor != appliedOnColor)) {
            recreate();
            return;
        }
        loadSettingsAndApplyToManager();
        ensureServiceRunning();
        loadBackgroundApps();
        cpuMonitor.startMonitoring();
    }

    @Override
    protected void onPause() {
        super.onPause();
        cpuMonitor.stopMonitoring();
    }

    private void ensureServiceRunning() {
        if (sharedPreferences.getBoolean(KEY_AUTO_KILL_ENABLED, false)
                && !ShappkyService.isRunning()) {
            Intent intent = new Intent(this, ShappkyService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }
    }

    private void loadSettingsAndApplyToManager() {
        boolean showSystemApps = sharedPreferences.getBoolean(KEY_SHOW_SYSTEM_APPS, false);
        boolean showPersistentApps = sharedPreferences.getBoolean(KEY_SHOW_PERSISTENT_APPS, false);
        currentSortMode = sharedPreferences.getInt(KEY_SORT_MODE, AppConstants.SORT_MODE_DEFAULT);
        appManager.setShowSystemApps(showSystemApps);
        appManager.setShowPersistentApps(showPersistentApps);
    }

    private void selectAll() {
        for (AppModel app : fullAppsList) {
            if (!app.isProtected() && !app.isWhitelisted()) {
                app.setSelected(true);
            }
        }
        listAdapter.submitList(new ArrayList<>(appsDataList));
        updateSelectMenuVisibility();
        if (selectAllMenuItem != null) {
            selectAllMenuItem.setIcon(R.drawable.ic_unselect_all);
            selectAllMenuItem.setTitle(getString(R.string.menu_deselect_all));
            tintMenuItem(selectAllMenuItem);
        }
    }

    private void unselectAll() {
        for (AppModel app : fullAppsList) {
            app.setSelected(false);
        }
        listAdapter.submitList(new ArrayList<>(appsDataList));
        updateSelectMenuVisibility();
        if (selectAllMenuItem != null) {
            selectAllMenuItem.setIcon(R.drawable.ic_select_all);
            selectAllMenuItem.setTitle(getString(R.string.menu_select_all));
            tintMenuItem(selectAllMenuItem);
        }
    }

    private void tintMenuItem(MenuItem item) {
        if (item == null || item.getIcon() == null) return;
        item.getIcon().setTint(isLightAccent() ? android.graphics.Color.BLACK : android.graphics.Color.WHITE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        selectAllMenuItem = menu.findItem(R.id.action_select_all);

        applyToolbarIconTint(menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setQueryHint(getString(R.string.main_search_hint));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterApps(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterApps(newText);
                return true;
            }
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_select_all) {
            boolean hasSelection = fullAppsList.stream().anyMatch(AppModel::isSelected);
            if (hasSelection) {
                unselectAll();
            } else {
                selectAll();
            }
            return true;
        } else if (itemId == R.id.action_sort) {
            showSortDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSortDialog() {
        int dp8  = (int) (getResources().getDisplayMetrics().density * 8);
        int dp16 = (int) (getResources().getDisplayMetrics().density * 16);

        int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
        boolean isCustomAccent = accent == ACCENT_CUSTOM;
        android.content.res.ColorStateList accentTint = isCustomAccent
                ? android.content.res.ColorStateList.valueOf(
                        sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR))
                : null;

        LinearLayout.LayoutParams fullWidth = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp8, 0, dp8);

        android.widget.RadioGroup radioGroup = new android.widget.RadioGroup(this);
        radioGroup.setOrientation(android.widget.RadioGroup.VERTICAL);
        radioGroup.setLayoutParams(fullWidth);

        int[][] sortModes = {
            { AppConstants.SORT_MODE_DEFAULT,   R.string.sort_default   },
            { AppConstants.SORT_MODE_RAM_DESC,  R.string.sort_ram_desc  },
            { AppConstants.SORT_MODE_RAM_ASC,   R.string.sort_ram_asc   },
            { AppConstants.SORT_MODE_CPU_DESC,  R.string.sort_cpu_desc  },
            { AppConstants.SORT_MODE_CPU_ASC,   R.string.sort_cpu_asc   },
            { AppConstants.SORT_MODE_NAME_ASC,  R.string.sort_name_asc  },
            { AppConstants.SORT_MODE_NAME_DESC, R.string.sort_name_desc },
        };

        int checkedRadioId = -1;
        for (int[] entry : sortModes) {
            int sortMode = entry[0];
            android.widget.RadioButton rb = new android.widget.RadioButton(this);
            rb.setId(1000 + sortMode);
            rb.setText(getString(entry[1]));
            rb.setPadding(dp16, dp8, dp16, dp8);
            rb.setLayoutParams(fullWidth);
            if (accentTint != null) rb.setButtonTintList(accentTint);
            radioGroup.addView(rb);
            if (sortMode == currentSortMode) checkedRadioId = rb.getId();
        }
        if (checkedRadioId != -1) radioGroup.check(checkedRadioId);
        root.addView(radioGroup);

        View divider = new View(this);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        dividerParams.setMargins(0, dp8, 0, dp8);
        divider.setLayoutParams(dividerParams);
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.colorControlHighlight, tv, true);
        divider.setBackgroundColor(tv.data);
        root.addView(divider);

        CheckBox checkboxSystem = new CheckBox(this);
        checkboxSystem.setText(getString(R.string.settings_show_system_apps_title));
        checkboxSystem.setPadding(dp16, dp8, dp16, dp8);
        checkboxSystem.setLayoutParams(fullWidth);
        checkboxSystem.setChecked(sharedPreferences.getBoolean(KEY_SHOW_SYSTEM_APPS, false));
        if (accentTint != null) checkboxSystem.setButtonTintList(accentTint);
        root.addView(checkboxSystem);

        CheckBox checkboxPersistent = new CheckBox(this);
        checkboxPersistent.setText(getString(R.string.settings_show_persistent_apps_title));
        checkboxPersistent.setPadding(dp16, dp8, dp16, dp8);
        checkboxPersistent.setLayoutParams(fullWidth);
        checkboxPersistent.setChecked(sharedPreferences.getBoolean(KEY_SHOW_PERSISTENT_APPS, false));
        if (accentTint != null) checkboxPersistent.setButtonTintList(accentTint);
        root.addView(checkboxPersistent);

        checkboxSystem.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !sharedPreferences.getBoolean("system_apps_warning_shown", false)) {
                buttonView.setChecked(false);
                AlertDialog warnDialog = new MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.settings_system_apps_warning_title))
                        .setMessage(getString(R.string.settings_system_apps_warning_message))
                        .setPositiveButton(getString(R.string.settings_system_apps_i_understand), (d, w) -> {
                            sharedPreferences.edit()
                                    .putBoolean("system_apps_warning_shown", true)
                                    .apply();
                            buttonView.setChecked(true);
                        })
                        .setNegativeButton(getString(R.string.dialog_cancel), null)
                        .create();
                warnDialog.show();
                tintDialogButtons(warnDialog);
            }
        });

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.addView(root);

        AlertDialog sortDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.sort_title))
                .setView(scrollView)
                .setPositiveButton(getString(R.string.dialog_apply), (dialog, which) -> {
                    int checkedId = radioGroup.getCheckedRadioButtonId();
                    int newSortMode = (checkedId != -1) ? (checkedId - 1000) : AppConstants.SORT_MODE_DEFAULT;

                    currentSortMode = newSortMode;

                    sharedPreferences.edit()
                            .putInt(KEY_SORT_MODE, newSortMode)
                            .putBoolean(KEY_SHOW_SYSTEM_APPS, checkboxSystem.isChecked())
                            .putBoolean(KEY_SHOW_PERSISTENT_APPS, checkboxPersistent.isChecked())
                            .apply();

                    loadSettingsAndApplyToManager();
                    loadBackgroundApps();
                })
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .create();

        sortDialog.show();
        tintDialogButtons(sortDialog);
    }

    private void applyToolbarIconTint(Menu menu) {
        int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
        int color;
        if (accent == ACCENT_CUSTOM) {
            color = sharedPreferences.getInt(KEY_ACCENT_ON_COLOR, ACCENT_ON_WHITE) == ACCENT_ON_BLACK
                    ? Color.BLACK : Color.WHITE;
        } else {
            color = isLightAccent() ? Color.BLACK : Color.WHITE;
        }

        int[] iconIds = {R.id.action_search, R.id.action_sort, R.id.action_select_all};
        for (int id : iconIds) {
            MenuItem menuItem = menu.findItem(id);
            if (menuItem != null && menuItem.getIcon() != null) {
                menuItem.getIcon().setTint(color);
            }
        }
        binding.toolbar.setTitleTextColor(color);
    }
}
