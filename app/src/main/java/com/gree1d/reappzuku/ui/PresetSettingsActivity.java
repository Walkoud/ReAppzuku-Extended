package com.gree1d.reappzuku.ui;

import com.gree1d.reappzuku.databinding.ActivityPresetSettingsBinding;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.gree1d.reappzuku.utils.AppModel;
import com.gree1d.reappzuku.core.ShellManager;
import com.gree1d.reappzuku.utils.PresetModel;
import com.gree1d.reappzuku.manager.BackgroundAppManager;
import com.gree1d.reappzuku.manager.AutoKillManager;
import com.gree1d.reappzuku.manager.PresetManager;
import com.gree1d.reappzuku.core.BaseActivity;
import com.gree1d.reappzuku.R;
import com.gree1d.reappzuku.core.AppDebugManager;
import com.gree1d.reappzuku.core.AppDebugManager.Category;

import static com.gree1d.reappzuku.core.AppConstants.*;
import static com.gree1d.reappzuku.core.PreferenceKeys.*;

public class PresetSettingsActivity extends BaseActivity {

    public static final String EXTRA_PRESET_NUMBER = "preset_number";

    private static final int APP_LIST_MODE_CURRENT = 0;
    private static final int APP_LIST_MODE_OWN = 1;

    private ActivityPresetSettingsBinding binding;
    private SharedPreferences sharedPreferences;
    private BackgroundAppManager appManager;
    private AutoKillManager autoKillManager;
    private ShellManager shellManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private int presetNumber;
    private PresetManager presetManager;
    private PresetModel workingModel;

    private int appListMode = APP_LIST_MODE_CURRENT;
    private Set<String> ownWhitelist = new HashSet<>();
    private Set<String> ownBlacklist = new HashSet<>();

    private final ActivityResultLauncher<Intent> exportLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        buildCurrentModel();
                        presetManager.exportPresetToJson(workingModel, uri);
                        Toast.makeText(this, getString(R.string.preset_export_success), Toast.LENGTH_SHORT).show();
                        AppDebugManager.d(Category.SETTINGS_PAGE, "PresetSettingsActivity: export success, uri=" + uri);
                    }
                }
            });

    private final ActivityResultLauncher<Intent> importLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        PresetModel imported = presetManager.importPresetFromJson(presetNumber, uri);
                        if (imported != null) {
                            workingModel = imported;
                            ownWhitelist = new HashSet<>(imported.whitelistedApps);
                            ownBlacklist = new HashSet<>(imported.blacklistedApps);
                            boolean hasOwnList = !ownWhitelist.isEmpty() || !ownBlacklist.isEmpty();
                            appListMode = hasOwnList ? APP_LIST_MODE_OWN : APP_LIST_MODE_CURRENT;
                            loadSettings();
                            Toast.makeText(this, getString(R.string.preset_import_success), Toast.LENGTH_SHORT).show();
                            AppDebugManager.d(Category.SETTINGS_PAGE, "PresetSettingsActivity: import success, uri=" + uri + ", appListMode=" + appListMode);
                        } else {
                            AppDebugManager.w(Category.SETTINGS_PAGE, "PresetSettingsActivity: import failed, uri=" + uri);
                            Toast.makeText(this, getString(R.string.preset_import_failed), Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPresetSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        presetNumber = getIntent().getIntExtra(EXTRA_PRESET_NUMBER, PresetModel.PRESET_1);
        AppDebugManager.d(Category.SETTINGS_PAGE, "PresetSettingsActivity: onCreate presetNumber=" + presetNumber);
        presetManager = new PresetManager(this);
        sharedPreferences = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        shellManager = new ShellManager(this.getApplicationContext(), handler, executor);
        appManager = new BackgroundAppManager(this.getApplicationContext(), handler, executor, shellManager);
        autoKillManager = new AutoKillManager(this.getApplicationContext(), handler, executor, shellManager, appManager.getCurrentAppsList());

        setupToolbar();
        loadWorkingModel();
        loadSettings();
        setupListeners();
        applyAccentColors();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.preset_title, presetNumber));
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
        if (accent == ACCENT_SYSTEM) {
            binding.toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.toolbar_navy));
            binding.toolbar.setTitleTextColor(Color.WHITE);
            return;
        }
        if (accent == ACCENT_CUSTOM) {
            int customColor = sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR);
            int onColor = sharedPreferences.getInt(KEY_ACCENT_ON_COLOR, ACCENT_ON_WHITE);
            binding.toolbar.setBackgroundColor(customColor);
            int textColor = (onColor == ACCENT_ON_BLACK) ? Color.BLACK : Color.WHITE;
            binding.toolbar.setTitleTextColor(textColor);
            if (binding.toolbar.getNavigationIcon() != null)
                androidx.core.graphics.drawable.DrawableCompat.setTint(
                        binding.toolbar.getNavigationIcon(), textColor);
        }
    }

    private void loadWorkingModel() {
        PresetModel saved = presetManager.loadPreset(presetNumber);
        if (saved != null) {
            AppDebugManager.d(Category.SETTINGS_PAGE, "PresetSettingsActivity: loadWorkingModel — loaded saved preset #" + presetNumber + ", enabled=" + saved.enabled);
            workingModel = saved;
            ownWhitelist = new HashSet<>(saved.whitelistedApps);
            ownBlacklist = new HashSet<>(saved.blacklistedApps);
            boolean hasOwnList = !ownWhitelist.isEmpty() || !ownBlacklist.isEmpty();
            appListMode = hasOwnList ? APP_LIST_MODE_OWN : APP_LIST_MODE_CURRENT;
        } else {
            AppDebugManager.d(Category.SETTINGS_PAGE, "PresetSettingsActivity: loadWorkingModel — no saved preset #" + presetNumber + ", building from current prefs");
            workingModel = new PresetModel(presetNumber);
            workingModel.autoKillEnabled = sharedPreferences.getBoolean(KEY_AUTO_KILL_ENABLED, false);
            workingModel.periodicKillEnabled = sharedPreferences.getBoolean(KEY_PERIODIC_KILL_ENABLED, false);
            workingModel.killInterval = sharedPreferences.getInt(KEY_KILL_INTERVAL, DEFAULT_KILL_INTERVAL_MS);
            workingModel.killOnScreenOff = sharedPreferences.getBoolean(KEY_KILL_ON_SCREEN_OFF, false);
            workingModel.ramThresholdEnabled = sharedPreferences.getBoolean(KEY_RAM_THRESHOLD_ENABLED, false);
            workingModel.ramThreshold = sharedPreferences.getInt(KEY_RAM_THRESHOLD, DEFAULT_RAM_THRESHOLD_PERCENT);
            workingModel.autoKillType = autoKillManager.getAutoKillType();
            workingModel.killMode = autoKillManager.getKillMode();
            workingModel.hwTriggerHeadset = sharedPreferences.getBoolean(KEY_HW_TRIGGER_HEADSET, false);
            workingModel.hwTriggerUsb = sharedPreferences.getBoolean(KEY_HW_TRIGGER_USB, false);
            workingModel.hwTriggerCharger = sharedPreferences.getBoolean(KEY_HW_TRIGGER_CHARGER, false);
            workingModel.hwTriggerWifi = sharedPreferences.getBoolean(KEY_HW_TRIGGER_WIFI, false);
            workingModel.hwTriggerBluetooth = sharedPreferences.getBoolean(KEY_HW_TRIGGER_BLUETOOTH, false);
            workingModel.hwTriggerGps = sharedPreferences.getBoolean(KEY_HW_TRIGGER_GPS, false);
            workingModel.hwTriggerHotspot = sharedPreferences.getBoolean(KEY_HW_TRIGGER_HOTSPOT, false);
            workingModel.appLaunchTriggerEnabled = sharedPreferences.getBoolean(KEY_APP_LAUNCH_TRIGGER_ENABLED, false);
            workingModel.appLaunchClearCache = sharedPreferences.getBoolean(KEY_APP_LAUNCH_CLEAR_CACHE, false);
            workingModel.appLaunchTriggerPackages = new HashSet<>(
                    sharedPreferences.getStringSet(KEY_APP_LAUNCH_TRIGGER_PACKAGES, new HashSet<>()));
            appListMode = APP_LIST_MODE_CURRENT;
        }
    }

    private void loadSettings() {
        binding.switchPresetEnabled.setChecked(workingModel.enabled);
        binding.textPresetName.setText(workingModel.name);
        updateTimeRangeText();
        updateAppListModeText();
        binding.switchPeriodicKill.setChecked(workingModel.periodicKillEnabled);
        updateKillIntervalText(workingModel.killInterval);
        updateKillIntervalVisibility(workingModel.periodicKillEnabled);
        binding.switchKillScreenOff.setChecked(workingModel.killOnScreenOff);
        binding.switchRamThreshold.setChecked(workingModel.ramThresholdEnabled);
        updateRamThresholdText(workingModel.ramThreshold);
        updateRamThresholdVisibility(workingModel.ramThresholdEnabled);
        updateAutoKillTypeText(workingModel.autoKillType);
        updateKillModeText(workingModel.killMode);
        updateKillModeListVisibility(workingModel.killMode);
        updateListCounts();
        updateAdditionalScenariosSummary();
    }

    private void setupListeners() {
        binding.switchPresetEnabled.setOnCheckedChangeListener((btn, isChecked) -> {
            workingModel.enabled = isChecked;
            AppDebugManager.d(Category.SETTINGS_PAGE, "PresetSettingsActivity: preset #" + presetNumber + " enabled=" + isChecked);
            if (!isChecked && presetManager.getActivePresetNumber() == presetNumber) {
                presetManager.forceDeactivateIfActive(presetNumber);
                AppDebugManager.d(Category.SETTINGS_PAGE, "PresetSettingsActivity: preset #" + presetNumber + " disabled — force deactivated");
            }
        });

        binding.layoutPresetName.setOnClickListener(v -> showPresetNameDialog());
        binding.layoutPresetTimeRange.setOnClickListener(v -> showTimeRangeDialog());
        binding.layoutPresetAppListMode.setOnClickListener(v -> showAppListModeDialog());

        binding.switchPeriodicKill.setOnCheckedChangeListener((btn, isChecked) -> {
            workingModel.periodicKillEnabled = isChecked;
            updateKillIntervalVisibility(isChecked);
        });
        binding.layoutKillInterval.setOnClickListener(v -> showKillIntervalDialog());
        binding.switchKillScreenOff.setOnCheckedChangeListener((btn, isChecked) ->
                workingModel.killOnScreenOff = isChecked);
        binding.switchRamThreshold.setOnCheckedChangeListener((btn, isChecked) -> {
            workingModel.ramThresholdEnabled = isChecked;
            updateRamThresholdVisibility(isChecked);
        });
        binding.layoutRamThreshold.setOnClickListener(v -> showRamThresholdDialog());
        binding.layoutAutoKillType.setOnClickListener(v -> showAutoKillTypeDialog());
        binding.layoutKillMode.setOnClickListener(v -> showKillModeDialog());
        binding.layoutWhitelist.setOnClickListener(v -> {
            if (appListMode == APP_LIST_MODE_CURRENT) {
                Toast.makeText(this, getString(R.string.preset_app_list_mode_switch_hint), Toast.LENGTH_SHORT).show();
                return;
            }
            showOwnWhitelistDialog();
        });
        binding.layoutBlacklist.setOnClickListener(v -> {
            if (appListMode == APP_LIST_MODE_CURRENT) {
                Toast.makeText(this, getString(R.string.preset_app_list_mode_switch_hint), Toast.LENGTH_SHORT).show();
                return;
            }
            showOwnBlacklistDialog();
        });

        binding.layoutAdditionalScenarios.setOnClickListener(v -> showAdditionalScenariosDialog());

        binding.fabSavePreset.setOnClickListener(v -> savePreset());
        binding.btnExportJson.setOnClickListener(v -> launchJsonExport());
        binding.btnImportJson.setOnClickListener(v -> launchJsonImport());
        binding.btnResetPreset.setOnClickListener(v -> showResetConfirmationDialog());
    }

    private void applyAccentColors() {
        int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
        if (accent != ACCENT_CUSTOM) return;
        int color = sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR);
        int trackColor = darkenColor(color, 0.6f);

        TextView[] headers = { binding.sectionTitlePreset, binding.sectionTitleAutokill };
        for (TextView tv : headers) tv.setTextColor(color);

        android.content.res.ColorStateList thumbTint = new android.content.res.ColorStateList(
                new int[][] { new int[] { android.R.attr.state_checked }, new int[] {} },
                new int[] { color, 0xFFAAAAAA });
        android.content.res.ColorStateList trackTint = new android.content.res.ColorStateList(
                new int[][] { new int[] { android.R.attr.state_checked }, new int[] {} },
                new int[] { trackColor, 0xFF555555 });

        for (com.google.android.material.materialswitch.MaterialSwitch sw :
                new com.google.android.material.materialswitch.MaterialSwitch[]{
                        binding.switchPresetEnabled,
                        binding.switchPeriodicKill,
                        binding.switchKillScreenOff,
                        binding.switchRamThreshold }) {
            sw.setThumbTintList(thumbTint);
            sw.setTrackTintList(trackTint);
        }
        binding.fabSavePreset.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(darkenColor(color, 0.7f)));
    }

    private void savePreset() {
        PresetModel other = presetManager.loadPreset(
                presetNumber == PresetModel.PRESET_1 ? PresetModel.PRESET_2 : PresetModel.PRESET_1);
        if (other != null && workingModel.overlapsWithExcludingSelf(other)) {
            AppDebugManager.w(Category.SETTINGS_PAGE, "PresetSettingsActivity: savePreset #" + presetNumber + " blocked — time overlap with preset #" + other.presetNumber);
            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.preset_overlap_title))
                    .setMessage(getString(R.string.preset_overlap_message,
                            other.startHour, other.startMinute, other.endHour, other.endMinute))
                    .setPositiveButton(getString(R.string.dialog_ok), null)
                    .show();
            return;
        }

        buildCurrentModel();
        workingModel.autoKillEnabled = true;

        presetManager.savePreset(workingModel);

        if (workingModel.enabled) {
            presetManager.scheduleAlarms(workingModel);
            presetManager.checkAndApplyCurrentPreset();
        } else {
            presetManager.cancelAlarms(workingModel.presetNumber);
            presetManager.forceDeactivateIfActive(workingModel.presetNumber);
        }

        AppDebugManager.d(Category.SETTINGS_PAGE, "PresetSettingsActivity: preset #" + presetNumber + " saved, enabled=" + workingModel.enabled);
        Toast.makeText(this, getString(R.string.preset_saved, presetNumber), Toast.LENGTH_SHORT).show();
        finish();
    }

    private void buildCurrentModel() {
        if (appListMode == APP_LIST_MODE_CURRENT) {
            workingModel.whitelistedApps = new HashSet<>(
                    sharedPreferences.getStringSet(KEY_WHITELISTED_APPS, new HashSet<>()));
            workingModel.blacklistedApps = new HashSet<>(
                    sharedPreferences.getStringSet(KEY_BLACKLISTED_APPS, new HashSet<>()));
        } else {
            workingModel.whitelistedApps = new HashSet<>(ownWhitelist);
            workingModel.blacklistedApps = new HashSet<>(ownBlacklist);
        }
    }

    private void launchJsonExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "preset" + presetNumber + ".json");
        exportLauncher.launch(intent);
    }

    private void launchJsonImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        importLauncher.launch(intent);
    }

    private void showResetConfirmationDialog() {
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.preset_reset_dialog_title))
                .setMessage(getString(R.string.preset_reset_dialog_message, presetNumber))
                .setPositiveButton(getString(R.string.preset_reset_button), (d, w) -> resetPreset())
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .create();
        dialog.show();
        resetDialogButtonColors(dialog);
        android.widget.Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positive != null) {
            positive.setTextColor(ContextCompat.getColor(this, R.color.sheet_delete_color));
        }
    }

    private void resetPreset() {
        AppDebugManager.d(Category.SETTINGS_PAGE, "PresetSettingsActivity: resetPreset #" + presetNumber + " (local only, not yet saved)");

        workingModel = new PresetModel(presetNumber);
        workingModel.autoKillEnabled = sharedPreferences.getBoolean(KEY_AUTO_KILL_ENABLED, false);
        workingModel.periodicKillEnabled = sharedPreferences.getBoolean(KEY_PERIODIC_KILL_ENABLED, false);
        workingModel.killInterval = sharedPreferences.getInt(KEY_KILL_INTERVAL, DEFAULT_KILL_INTERVAL_MS);
        workingModel.killOnScreenOff = sharedPreferences.getBoolean(KEY_KILL_ON_SCREEN_OFF, false);
        workingModel.ramThresholdEnabled = sharedPreferences.getBoolean(KEY_RAM_THRESHOLD_ENABLED, false);
        workingModel.ramThreshold = sharedPreferences.getInt(KEY_RAM_THRESHOLD, DEFAULT_RAM_THRESHOLD_PERCENT);
        workingModel.autoKillType = autoKillManager.getAutoKillType();
        workingModel.killMode = autoKillManager.getKillMode();
        workingModel.hwTriggerHeadset = sharedPreferences.getBoolean(KEY_HW_TRIGGER_HEADSET, false);
        workingModel.hwTriggerUsb = sharedPreferences.getBoolean(KEY_HW_TRIGGER_USB, false);
        workingModel.hwTriggerCharger = sharedPreferences.getBoolean(KEY_HW_TRIGGER_CHARGER, false);
        workingModel.hwTriggerWifi = sharedPreferences.getBoolean(KEY_HW_TRIGGER_WIFI, false);
        workingModel.hwTriggerBluetooth = sharedPreferences.getBoolean(KEY_HW_TRIGGER_BLUETOOTH, false);
        workingModel.hwTriggerGps = sharedPreferences.getBoolean(KEY_HW_TRIGGER_GPS, false);
        workingModel.hwTriggerHotspot = sharedPreferences.getBoolean(KEY_HW_TRIGGER_HOTSPOT, false);
        workingModel.appLaunchTriggerEnabled = sharedPreferences.getBoolean(KEY_APP_LAUNCH_TRIGGER_ENABLED, false);
        workingModel.appLaunchClearCache = sharedPreferences.getBoolean(KEY_APP_LAUNCH_CLEAR_CACHE, false);
        workingModel.appLaunchTriggerPackages = new HashSet<>(
                sharedPreferences.getStringSet(KEY_APP_LAUNCH_TRIGGER_PACKAGES, new HashSet<>()));

        ownWhitelist = new HashSet<>();
        ownBlacklist = new HashSet<>();
        appListMode = APP_LIST_MODE_CURRENT;

        loadSettings();
        Toast.makeText(this, getString(R.string.preset_reset_success, presetNumber), Toast.LENGTH_SHORT).show();
    }

    private void showPresetNameDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(workingModel.name);
        input.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(30) });
        input.selectAll();
        int dp16 = (int) (getResources().getDisplayMetrics().density * 16);
        input.setPadding(dp16, dp16, dp16, dp16);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.preset_name_dialog_title))
                .setView(input)
                .setPositiveButton(getString(R.string.dialog_save), (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        AppDebugManager.d(Category.SETTINGS_PAGE, "PresetSettingsActivity: preset #" + presetNumber + " name changed to \"" + name + "\"");
                        workingModel.name = name;
                        binding.textPresetName.setText(name);
                    }
                })
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .create();
        dialog.show();
        resetDialogButtonColors(dialog);
    }

    private void showTimeRangeDialog() {
        boolean use24h = android.text.format.DateFormat.is24HourFormat(this);
        int[] startHour = { workingModel.startHour };
        int[] startMinute = { workingModel.startMinute };
        int[] endHour = { workingModel.endHour };
        int[] endMinute = { workingModel.endMinute };

        View view = getLayoutInflater().inflate(R.layout.dialog_preset_time_range, null);
        TextView btnFrom = view.findViewById(R.id.preset_btn_time_from);
        TextView btnTo = view.findViewById(R.id.preset_btn_time_to);
        btnFrom.setText(formatTime(startHour[0], startMinute[0], use24h));
        btnTo.setText(formatTime(endHour[0], endMinute[0], use24h));

        int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
        if (accent == ACCENT_CUSTOM) {
            int color = sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR);
            btnFrom.setTextColor(color);
            btnTo.setTextColor(color);
        }

        btnFrom.setOnClickListener(v ->
                new android.app.TimePickerDialog(this, (tp, h, m) -> {
                    startHour[0] = h; startMinute[0] = m;
                    btnFrom.setText(formatTime(h, m, use24h));
                }, startHour[0], startMinute[0], use24h).show());
        btnTo.setOnClickListener(v ->
                new android.app.TimePickerDialog(this, (tp, h, m) -> {
                    endHour[0] = h; endMinute[0] = m;
                    btnTo.setText(formatTime(h, m, use24h));
                }, endHour[0], endMinute[0], use24h).show());

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.preset_time_range_dialog_title))
                .setView(view)
                .setPositiveButton(getString(R.string.dialog_save), (d, w) -> {
                    if (startHour[0] == endHour[0] && startMinute[0] == endMinute[0]) {
                        AppDebugManager.w(Category.SETTINGS_PAGE, "PresetSettingsActivity: showTimeRangeDialog — start equals end time, rejected");
                        Toast.makeText(this, getString(R.string.preset_time_range_error_same), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    workingModel.startHour = startHour[0];
                    workingModel.startMinute = startMinute[0];
                    workingModel.endHour = endHour[0];
                    workingModel.endMinute = endMinute[0];
                    AppDebugManager.d(Category.SETTINGS_PAGE, "PresetSettingsActivity: time range set " + startHour[0] + ":" + startMinute[0] + " – " + endHour[0] + ":" + endMinute[0]);
                    updateTimeRangeText();
                })
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .create();
        dialog.show();
        resetDialogButtonColors(dialog);
    }

    private void showAppListModeDialog() {
        String[] modes = {
                getString(R.string.preset_app_list_mode_current),
                getString(R.string.preset_app_list_mode_own)
        };
        showSingleChoiceDialog(getString(R.string.preset_app_list_mode_dialog_title), modes, appListMode, which -> {
            AppDebugManager.d(Category.SETTINGS_PAGE, "PresetSettingsActivity: appListMode changed to " + which + " (" + modes[which] + ")");
            appListMode = which;
            updateAppListModeText();
            updateListCounts();
            updateKillModeListVisibility(workingModel.killMode);
        });
    }

    private void showKillIntervalDialog() {
        if (!workingModel.periodicKillEnabled) return;
        int selectedIndex = 1;
        for (int i = 0; i < KILL_INTERVALS_MS.length; i++) {
            if (KILL_INTERVALS_MS[i] == workingModel.killInterval) { selectedIndex = i; break; }
        }
        showSingleChoiceDialog(getString(R.string.settings_check_frequency_title),
                getResources().getStringArray(R.array.settings_kill_interval_labels), selectedIndex, which -> {
                    AppDebugManager.d(Category.SETTINGS_PAGE, "PresetSettingsActivity: killInterval changed to " + KILL_INTERVALS_MS[which] + "ms");
                    workingModel.killInterval = KILL_INTERVALS_MS[which];
                    updateKillIntervalText(KILL_INTERVALS_MS[which]);
                });
    }

    private void showRamThresholdDialog() {
        int selected = 1;
        for (int i = 0; i < RAM_THRESHOLD_VALUES.length; i++) {
            if (RAM_THRESHOLD_VALUES[i] == workingModel.ramThreshold) { selected = i; break; }
        }
        showSingleChoiceDialog(getString(R.string.settings_ram_threshold_dialog_title),
                getResources().getStringArray(R.array.settings_ram_threshold_labels), selected, which -> {
                    AppDebugManager.d(Category.SETTINGS_PAGE, "PresetSettingsActivity: ramThreshold changed to " + RAM_THRESHOLD_VALUES[which] + "%");
                    workingModel.ramThreshold = RAM_THRESHOLD_VALUES[which];
                    updateRamThresholdText(RAM_THRESHOLD_VALUES[which]);
                });
    }

    private void showAutoKillTypeDialog() {
        String[] types = {
                getString(R.string.settings_auto_kill_type_force_stop),
                getString(R.string.settings_auto_kill_type_kill)
        };
        View titleView = LayoutInflater.from(this).inflate(R.layout.dialog_killtype_info, null);
        ((TextView) titleView.findViewById(R.id.dialog_title)).setText(R.string.settings_auto_kill_type_title);
        View bodyView = getLayoutInflater().inflate(R.layout.dialog_single_choice, null);
        bodyView.findViewById(R.id.single_choice_title).setVisibility(View.GONE);
        RadioGroup group = bodyView.findViewById(R.id.single_choice_group);

        int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
        android.content.res.ColorStateList tint = (accent == ACCENT_CUSTOM)
                ? android.content.res.ColorStateList.valueOf(getDialogAccentColor()) : null;
        int dp12 = (int) (getResources().getDisplayMetrics().density * 12);
        for (int i = 0; i < types.length; i++) {
            android.widget.RadioButton rb = new android.widget.RadioButton(this);
            rb.setText(types[i]);
            rb.setId(1000 + i);
            rb.setPadding(dp12, dp12, dp12, dp12);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rb.setLayoutParams(lp);
            if (tint != null) rb.setButtonTintList(tint);
            group.addView(rb);
        }
        group.check(1000 + workingModel.autoKillType);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setCustomTitle(titleView)
                .setView(bodyView)
                .create();
        titleView.findViewById(R.id.btn_help).setOnClickListener(v -> {
            dialog.dismiss();
            showAutoKillTypeHelpDialog(() -> showAutoKillTypeDialog());
        });
        group.setOnCheckedChangeListener((g, id) -> {
            AppDebugManager.d(Category.SETTINGS_PAGE, "PresetSettingsActivity: autoKillType changed to " + (id - 1000));
            workingModel.autoKillType = id - 1000;
            updateAutoKillTypeText(id - 1000);
            dialog.dismiss();
        });
        dialog.show();
        resetDialogButtonColors(dialog);
    }

    private void showAutoKillTypeHelpDialog(Runnable onBack) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.settings_auto_kill_type_help_title))
                .setMessage(getString(R.string.settings_auto_kill_type_help_message))
                .setPositiveButton(getString(R.string.dialog_ok_got_it), (d, w) -> {
                    d.dismiss();
                    if (onBack != null) onBack.run();
                })
                .create();
        dialog.show();
        resetDialogButtonColors(dialog);
    }

    private void showKillModeDialog() {
        String[] modes = {
                getString(R.string.settings_mode_whitelist),
                getString(R.string.settings_mode_blacklist)
        };
        showSingleChoiceDialog(getString(R.string.settings_kill_mode_dialog_title), modes, workingModel.killMode, which -> {
            AppDebugManager.d(Category.SETTINGS_PAGE, "PresetSettingsActivity: killMode changed to " + which + " (" + modes[which] + ")");
            workingModel.killMode = which;
            updateKillModeText(which);
            updateKillModeListVisibility(which);
        });
    }

    private void showAdditionalScenariosDialog() {
        int dp4 = (int) (getResources().getDisplayMetrics().density * 4);
        int dp8 = (int) (getResources().getDisplayMetrics().density * 8);
        int dp16 = (int) (getResources().getDisplayMetrics().density * 16);
        int dp24 = (int) (getResources().getDisplayMetrics().density * 24);

        LinearLayout.LayoutParams cbParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cbParams.setMarginStart(dp16);

        int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
        boolean isCustomAccent = accent == ACCENT_CUSTOM;
        int customColor = sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR);
        android.content.res.ColorStateList checkboxTint = isCustomAccent
                ? android.content.res.ColorStateList.valueOf(customColor) : null;
        int onColor = sharedPreferences.getInt(KEY_ACCENT_ON_COLOR, ACCENT_ON_WHITE);
        int buttonTextColor = isCustomAccent
                ? ((onColor == ACCENT_ON_BLACK) ? Color.BLACK : Color.WHITE)
                : ContextCompat.getColor(this, R.color.dialog_button_text);

        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.textColorPrimary, tv, true);
        int colorPrimary = ContextCompat.getColor(this, tv.resourceId);
        getTheme().resolveAttribute(android.R.attr.textColorSecondary, tv, true);
        int colorSecondary = ContextCompat.getColor(this, tv.resourceId);
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorSecondary, tv, true);
        int colorAccent = isCustomAccent ? customColor : tv.data;
        getTheme().resolveAttribute(android.R.attr.colorControlHighlight, tv, true);
        int colorDivider = tv.data;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp8, 0, dp8);

        TextView headerHw = new TextView(this);
        headerHw.setText(getString(R.string.scenarios_hardware_events_title));
        headerHw.setTextSize(12);
        headerHw.setTypeface(null, android.graphics.Typeface.BOLD);
        headerHw.setTextColor(colorAccent);
        headerHw.setPadding(dp24, dp8, dp24, dp4);
        root.addView(headerHw);

        CheckBox cbHeadset = makeCheckBox(getString(R.string.scenarios_hw_headset), workingModel.hwTriggerHeadset, dp24, dp8, checkboxTint);
        CheckBox cbUsb = makeCheckBox(getString(R.string.scenarios_hw_usb), workingModel.hwTriggerUsb, dp24, dp8, checkboxTint);
        CheckBox cbCharger = makeCheckBox(getString(R.string.scenarios_hw_charger), workingModel.hwTriggerCharger, dp24, dp8, checkboxTint);
        CheckBox cbWifi = makeCheckBox(getString(R.string.scenarios_hw_wifi), workingModel.hwTriggerWifi, dp24, dp8, checkboxTint);
        CheckBox cbBluetooth = makeCheckBox(getString(R.string.scenarios_hw_bluetooth), workingModel.hwTriggerBluetooth, dp24, dp8, checkboxTint);
        CheckBox cbGps = makeCheckBox(getString(R.string.scenarios_hw_gps), workingModel.hwTriggerGps, dp24, dp8, checkboxTint);
        CheckBox cbHotspot = makeCheckBox(getString(R.string.scenarios_hw_hotspot), workingModel.hwTriggerHotspot, dp24, dp8, checkboxTint);

        root.addView(cbHeadset);
        root.addView(cbUsb);
        root.addView(cbCharger);
        root.addView(cbWifi);
        root.addView(cbBluetooth);
        root.addView(cbGps);
        root.addView(cbHotspot);

        TextView noteHw = new TextView(this);
        noteHw.setText(getString(R.string.scenarios_hw_delay_note));
        noteHw.setTextSize(12);
        noteHw.setTextColor(colorSecondary);
        noteHw.setPadding(dp24, dp4, dp24, dp16);
        root.addView(noteHw);

        View divider = new View(this);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        dividerParams.setMargins(dp16, 0, dp16, dp16);
        divider.setLayoutParams(dividerParams);
        divider.setBackgroundColor(colorDivider);
        root.addView(divider);

        TextView headerLaunch = new TextView(this);
        headerLaunch.setText(getString(R.string.scenarios_app_launch_title));
        headerLaunch.setTextSize(12);
        headerLaunch.setTypeface(null, android.graphics.Typeface.BOLD);
        headerLaunch.setTextColor(colorAccent);
        headerLaunch.setPadding(dp24, dp4, dp24, dp4);
        root.addView(headerLaunch);

        CheckBox cbAppLaunch = makeCheckBox(getString(R.string.scenarios_app_launch_enable),
                workingModel.appLaunchTriggerEnabled, dp24, dp8, checkboxTint);
        root.addView(cbAppLaunch);

        LinearLayout layoutTargetApps = new LinearLayout(this);
        layoutTargetApps.setOrientation(LinearLayout.VERTICAL);
        layoutTargetApps.setVisibility(cbAppLaunch.isChecked() ? View.VISIBLE : View.GONE);

        TextView tvTargetAppsLabel = new TextView(this);
        tvTargetAppsLabel.setText(getString(R.string.scenarios_app_launch_target_apps));
        tvTargetAppsLabel.setTextSize(14);
        tvTargetAppsLabel.setTextColor(colorPrimary);
        tvTargetAppsLabel.setPadding(dp24, dp8, dp24, 0);
        layoutTargetApps.addView(tvTargetAppsLabel);

        TextView tvTargetAppsList = new TextView(this);
        tvTargetAppsList.setTextSize(12);
        tvTargetAppsList.setTextColor(colorSecondary);
        tvTargetAppsList.setPadding(dp24, dp4, dp24, dp8);
        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        layoutTargetApps.setBackgroundResource(outValue.resourceId);
        updateTargetAppsList(tvTargetAppsList);
        layoutTargetApps.addView(tvTargetAppsList);

        CheckBox cbClearCache = makeCheckBox(getString(R.string.scenarios_app_launch_clear_cache),
                workingModel.appLaunchClearCache, dp4, dp8, checkboxTint);
        cbClearCache.setLayoutParams(cbParams);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            cbClearCache.setEnabled(false);
            cbClearCache.setAlpha(0.4f);
        }
        layoutTargetApps.addView(cbClearCache);
        root.addView(layoutTargetApps);

        cbAppLaunch.setOnCheckedChangeListener((btn, isChecked) ->
                layoutTargetApps.setVisibility(isChecked ? View.VISIBLE : View.GONE));

        layoutTargetApps.setOnClickListener(v ->
                showTargetAppsPickerDialog(() -> updateTargetAppsList(tvTargetAppsList)));

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(root);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.section_additional_scenarios))
                .setView(scrollView)
                .create();
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dialog_save), (d, w) -> {});
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.dialog_cancel), (d, w) -> d.dismiss());
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(buttonTextColor);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(buttonTextColor);

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            workingModel.hwTriggerHeadset = cbHeadset.isChecked();
            workingModel.hwTriggerUsb = cbUsb.isChecked();
            workingModel.hwTriggerCharger = cbCharger.isChecked();
            workingModel.hwTriggerWifi = cbWifi.isChecked();
            workingModel.hwTriggerBluetooth = cbBluetooth.isChecked();
            workingModel.hwTriggerGps = cbGps.isChecked();
            workingModel.hwTriggerHotspot = cbHotspot.isChecked();
            workingModel.appLaunchTriggerEnabled = cbAppLaunch.isChecked();
            workingModel.appLaunchClearCache = cbClearCache.isChecked();
            AppDebugManager.d(Category.SETTINGS_PAGE, "PresetSettingsActivity: additionalScenarios saved — headset=" + workingModel.hwTriggerHeadset
                    + ", usb=" + workingModel.hwTriggerUsb + ", charger=" + workingModel.hwTriggerCharger
                    + ", wifi=" + workingModel.hwTriggerWifi + ", bt=" + workingModel.hwTriggerBluetooth
                    + ", gps=" + workingModel.hwTriggerGps + ", hotspot=" + workingModel.hwTriggerHotspot
                    + ", appLaunch=" + workingModel.appLaunchTriggerEnabled + ", clearCache=" + workingModel.appLaunchClearCache);
            updateAdditionalScenariosSummary();
            dialog.dismiss();
        });
    }

    private CheckBox makeCheckBox(String text, boolean checked, int paddingH, int paddingV,
                                   android.content.res.ColorStateList tint) {
        CheckBox cb = new CheckBox(this);
        cb.setText(text);
        cb.setChecked(checked);
        cb.setPadding(paddingH, paddingV, paddingH, paddingV);
        if (tint != null) cb.setButtonTintList(tint);
        return cb;
    }

    private void updateTargetAppsList(TextView tv) {
        Set<String> packages = workingModel.appLaunchTriggerPackages;
        if (packages.isEmpty()) {
            tv.setText(getString(R.string.scenarios_app_launch_empty));
            return;
        }
        PackageManager pm = getPackageManager();
        List<String> names = new ArrayList<>();
        for (String pkg : packages) {
            try {
                android.content.pm.ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                names.add(pm.getApplicationLabel(info).toString());
            } catch (PackageManager.NameNotFoundException ignored) {
                names.add(pkg);
            }
        }
        tv.setText(android.text.TextUtils.join(", ", names));
    }

    private void showTargetAppsPickerDialog(Runnable onSaved) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_filter, null);
        ListView listView = dialogView.findViewById(R.id.filter_list_view);
        ProgressBar progressBar = dialogView.findViewById(R.id.filter_loading_progress);
        EditText searchBox = dialogView.findViewById(R.id.filter_search);
        LinearLayout filterOptions = dialogView.findViewById(R.id.filter_options_container);
        filterOptions.setVisibility(View.GONE);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.scenarios_app_launch_add_title))
                .setView(dialogView)
                .create();
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.dialog_cancel), (d, w) -> d.dismiss());
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dialog_save), (d, w) -> {});

        progressBar.setVisibility(View.VISIBLE);
        listView.setVisibility(View.GONE);
        searchBox.setVisibility(View.GONE);
        dialog.show();
        resetDialogButtonColors(dialog);

        appManager.loadAllApps(allApps -> {
            FilterAppsAdapter filterAdapter = new FilterAppsAdapter(this, allApps,
                    workingModel.appLaunchTriggerPackages);
            if (sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM) == ACCENT_CUSTOM)
                filterAdapter.setAccentColor(sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR));
            listView.setAdapter(filterAdapter);

            progressBar.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
            searchBox.setVisibility(View.VISIBLE);

            searchBox.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterAdapter.getFilter().filter(s); }
                @Override public void afterTextChanged(Editable s) {}
            });

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                workingModel.appLaunchTriggerPackages = filterAdapter.getSelectedPackages();
                AppDebugManager.d(Category.SETTINGS_PAGE, "PresetSettingsActivity: appLaunchTriggerPackages updated, count=" + workingModel.appLaunchTriggerPackages.size());
                if (onSaved != null) onSaved.run();
                dialog.dismiss();
            });
        });
    }

    private void showOwnWhitelistDialog() {
        AppDebugManager.d(Category.SETTINGS_PAGE, "PresetSettingsActivity: showOwnWhitelistDialog, current size=" + ownWhitelist.size());
        showAppListDialog(getString(R.string.settings_whitelist_dialog_title), ownWhitelist, selected -> {
            AppDebugManager.d(Category.SETTINGS_PAGE, "PresetSettingsActivity: ownWhitelist saved, size=" + selected.size());
            ownWhitelist = selected;
            updateListCounts();
        });
    }

    private void showOwnBlacklistDialog() {
        AppDebugManager.d(Category.SETTINGS_PAGE, "PresetSettingsActivity: showOwnBlacklistDialog, current size=" + ownBlacklist.size());
        showAppListDialog(getString(R.string.settings_blacklist_dialog_title), ownBlacklist, selected -> {
            AppDebugManager.d(Category.SETTINGS_PAGE, "PresetSettingsActivity: ownBlacklist saved, size=" + selected.size());
            ownBlacklist = selected;
            updateListCounts();
        });
    }

    private void showAppListDialog(String title, Set<String> current, java.util.function.Consumer<Set<String>> onSave) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_filter, null);
        ListView listView = dialogView.findViewById(R.id.filter_list_view);
        ProgressBar progressBar = dialogView.findViewById(R.id.filter_loading_progress);
        EditText searchBox = dialogView.findViewById(R.id.filter_search);
        LinearLayout filterOptions = dialogView.findViewById(R.id.filter_options_container);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(dialogView)
                .create();
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.dialog_cancel), (d, w) -> d.dismiss());
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dialog_save), (d, w) -> {});

        progressBar.setVisibility(View.VISIBLE);
        listView.setVisibility(View.GONE);
        searchBox.setVisibility(View.GONE);
        dialog.show();
        resetDialogButtonColors(dialog);

        appManager.loadAllApps(allApps -> {
            FilterAppsAdapter adapter = new FilterAppsAdapter(this, allApps, current);
            if (sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM) == ACCENT_CUSTOM)
                adapter.setAccentColor(sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR));
            listView.setAdapter(adapter);

            progressBar.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
            searchBox.setVisibility(View.VISIBLE);
            filterOptions.setVisibility(View.VISIBLE);

            setupFilterListeners(dialogView, adapter);
            appManager.updateRunningState(allApps, () -> {
                if (!dialog.isShowing()) return;
                adapter.notifyDataSetChanged();
            });

            searchBox.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { adapter.getFilter().filter(s); }
                @Override public void afterTextChanged(Editable s) {}
            });

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                onSave.accept(adapter.getSelectedPackages());
                dialog.dismiss();
            });
        });
    }

    private void updateAdditionalScenariosSummary() {
        List<String> active = new ArrayList<>();
        if (workingModel.hwTriggerHeadset) active.add(getString(R.string.scenarios_hw_headset_short));
        if (workingModel.hwTriggerUsb) active.add(getString(R.string.scenarios_hw_usb_short));
        if (workingModel.hwTriggerCharger) active.add(getString(R.string.scenarios_hw_charger_short));
        if (workingModel.hwTriggerWifi) active.add(getString(R.string.scenarios_hw_wifi_short));
        if (workingModel.hwTriggerBluetooth) active.add(getString(R.string.scenarios_hw_bluetooth_short));
        if (workingModel.hwTriggerGps) active.add(getString(R.string.scenarios_hw_gps_short));
        if (workingModel.hwTriggerHotspot) active.add(getString(R.string.scenarios_hw_hotspot_short));
        if (workingModel.appLaunchTriggerEnabled) active.add(getString(R.string.scenarios_app_launch_short));

        if (active.isEmpty()) {
            binding.textAdditionalScenariosSummary.setText(getString(R.string.scenarios_summary_none));
        } else {
            binding.textAdditionalScenariosSummary.setText(android.text.TextUtils.join(", ", active));
        }
    }

    private void updateTimeRangeText() {
        boolean use24h = android.text.format.DateFormat.is24HourFormat(this);
        binding.textPresetTimeRange.setText(
                formatTime(workingModel.startHour, workingModel.startMinute, use24h)
                + " – "
                + formatTime(workingModel.endHour, workingModel.endMinute, use24h));
    }

    private void updateAppListModeText() {
        binding.textPresetAppListMode.setText(appListMode == APP_LIST_MODE_CURRENT
                ? getString(R.string.preset_app_list_mode_current)
                : getString(R.string.preset_app_list_mode_own));
    }

    private void updateKillIntervalText(int intervalMs) {
        String[] labels = getResources().getStringArray(R.array.settings_kill_interval_labels);
        for (int i = 0; i < KILL_INTERVALS_MS.length; i++) {
            if (KILL_INTERVALS_MS[i] == intervalMs) { binding.textKillInterval.setText(labels[i]); return; }
        }
        binding.textKillInterval.setText(getString(R.string.settings_interval_fallback, intervalMs / 1000));
    }

    private void updateKillIntervalVisibility(boolean enabled) {
        binding.layoutKillInterval.setAlpha(enabled ? 1.0f : 0.5f);
        binding.layoutKillInterval.setClickable(enabled);
    }

    private void updateRamThresholdText(int threshold) {
        binding.textRamThreshold.setText(getString(R.string.settings_ram_threshold_summary, threshold));
    }

    private void updateRamThresholdVisibility(boolean enabled) {
        binding.layoutRamThreshold.setAlpha(enabled ? 1.0f : 0.5f);
        binding.layoutRamThreshold.setClickable(enabled);
    }

    private void updateAutoKillTypeText(int type) {
        binding.textAutoKillType.setText(type == 1
                ? getString(R.string.settings_auto_kill_type_kill)
                : getString(R.string.settings_auto_kill_type_force_stop));
    }

    private void updateKillModeText(int mode) {
        binding.textKillMode.setText(mode == 0
                ? getString(R.string.settings_mode_whitelist)
                : getString(R.string.settings_mode_blacklist));
    }

    private void updateKillModeListVisibility(int mode) {
        boolean ownMode = appListMode == APP_LIST_MODE_OWN;
        binding.layoutWhitelist.setVisibility(mode == 0 ? View.VISIBLE : View.GONE);
        binding.layoutBlacklist.setVisibility(mode == 1 ? View.VISIBLE : View.GONE);
        binding.layoutWhitelist.setAlpha(ownMode ? 1.0f : 0.5f);
        binding.layoutBlacklist.setAlpha(ownMode ? 1.0f : 0.5f);
    }

    private void updateListCounts() {
        if (appListMode == APP_LIST_MODE_CURRENT) {
            Set<String> wl = sharedPreferences.getStringSet(KEY_WHITELISTED_APPS, new HashSet<>());
            Set<String> bl = sharedPreferences.getStringSet(KEY_BLACKLISTED_APPS, new HashSet<>());
            binding.textWhitelistCount.setText(getString(R.string.preset_list_count_current, wl.size()));
            binding.textBlacklistCount.setText(getString(R.string.preset_list_count_current, bl.size()));
        } else {
            binding.textWhitelistCount.setText(getString(R.string.preset_list_count_own, ownWhitelist.size()));
            binding.textBlacklistCount.setText(getString(R.string.preset_list_count_own, ownBlacklist.size()));
        }
    }

    private void setupFilterListeners(View dialogView, FilterAppsAdapter adapter) {
        TextView btnSort = dialogView.findViewById(R.id.filter_btn_sort);
        TextView btnClear = dialogView.findViewById(R.id.filter_btn_clear);
        if (sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM) == ACCENT_CUSTOM) {
            int color = getDialogAccentColor();
            btnSort.setTextColor(color);
            btnClear.setTextColor(color);
        }
        final boolean[] showSystem = {false};
        final boolean[] showUser = {true};
        final boolean[] showRunning = {false};
        updateSortButtonText(btnSort, false);
        btnSort.setOnClickListener(v -> {
            android.widget.PopupMenu popup = new android.widget.PopupMenu(this, btnSort);
            popup.getMenu().add(0, 0, 0, getString(R.string.filter_system)).setCheckable(true).setChecked(showSystem[0]);
            popup.getMenu().add(0, 1, 1, getString(R.string.filter_user)).setCheckable(true).setChecked(showUser[0]);
            popup.getMenu().add(0, 2, 2, getString(R.string.filter_running)).setCheckable(true).setChecked(showRunning[0]);
            popup.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 0: showSystem[0] = !showSystem[0]; item.setChecked(showSystem[0]); break;
                    case 1: showUser[0] = !showUser[0]; item.setChecked(showUser[0]); break;
                    case 2: showRunning[0] = !showRunning[0]; item.setChecked(showRunning[0]); break;
                }
                adapter.setFilters(showSystem[0], showUser[0], showRunning[0]);
                return true;
            });
            popup.setOnDismissListener(d -> updateSortButtonText(btnSort, false));
            updateSortButtonText(btnSort, true);
            popup.show();
        });
        btnClear.setOnClickListener(v -> adapter.clearSelection());
    }

    private void updateSortButtonText(TextView btn, boolean open) {
        btn.setText(getString(R.string.filter_sort_button) + (open ? "  ▲" : "  ▼"));
    }

    private void showSingleChoiceDialog(String title, String[] options, int selected,
                                        java.util.function.IntConsumer onPick) {
        View view = getLayoutInflater().inflate(R.layout.dialog_single_choice, null);
        ((TextView) view.findViewById(R.id.single_choice_title)).setText(title);
        RadioGroup group = view.findViewById(R.id.single_choice_group);

        int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
        android.content.res.ColorStateList tint = (accent == ACCENT_CUSTOM)
                ? android.content.res.ColorStateList.valueOf(getDialogAccentColor()) : null;
        int dp12 = (int) (getResources().getDisplayMetrics().density * 12);
        for (int i = 0; i < options.length; i++) {
            android.widget.RadioButton rb = new android.widget.RadioButton(this);
            rb.setText(options[i]);
            rb.setId(1000 + i);
            rb.setPadding(dp12, dp12, dp12, dp12);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rb.setLayoutParams(lp);
            if (tint != null) rb.setButtonTintList(tint);
            group.addView(rb);
        }
        group.check(1000 + selected);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(view)
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .create();
        final boolean[] interacted = {false};
        group.setOnCheckedChangeListener((g, id) -> {
            if (!interacted[0] || id == -1) return;
            onPick.accept(id - 1000);
            dialog.dismiss();
        });
        view.post(() -> interacted[0] = true);
        dialog.show();
        resetDialogButtonColors(dialog);
    }

    private void resetDialogButtonColors(AlertDialog dialog) {
        int color = ContextCompat.getColor(this, R.color.dialog_button_text);
        android.widget.Button p = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        android.widget.Button n = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        android.widget.Button neu = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (p != null) p.setTextColor(color);
        if (n != null) n.setTextColor(color);
        if (neu != null) neu.setTextColor(color);
    }

    private int getDialogAccentColor() {
        int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
        if (accent == ACCENT_CUSTOM)
            return sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR);
        return ContextCompat.getColor(this, R.color.dialog_button_text);
    }

    private String formatTime(int hour, int minute, boolean use24h) {
        if (use24h) return String.format(Locale.US, "%02d:%02d", hour, minute);
        int h12 = hour % 12;
        if (h12 == 0) h12 = 12;
        return String.format(Locale.US, "%d:%02d %s", h12, minute, hour < 12 ? "AM" : "PM");
    }

    private static int darkenColor(int color, float factor) {
        return Color.argb(Color.alpha(color),
                Math.min(Math.round(Color.red(color) * factor), 255),
                Math.min(Math.round(Color.green(color) * factor), 255),
                Math.min(Math.round(Color.blue(color) * factor), 255));
    }

    @Override
    protected void onDestroy() {
        AppDebugManager.d(Category.SETTINGS_PAGE, "PresetSettingsActivity: onDestroy preset #" + presetNumber);
        super.onDestroy();
        executor.shutdownNow();
        binding = null;
    }
}
