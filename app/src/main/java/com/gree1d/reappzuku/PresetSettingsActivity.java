package com.gree1d.reappzuku;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.gree1d.reappzuku.AppConstants.*;
import static com.gree1d.reappzuku.PreferenceKeys.*;

public class PresetSettingsActivity extends BaseActivity {

    private static final String TAG = "PresetSettingsActivity";

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPresetSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        presetNumber = getIntent().getIntExtra(EXTRA_PRESET_NUMBER, PresetModel.PRESET_1);
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
        int onColor = sharedPreferences.getInt(KEY_ACCENT_ON_COLOR, ACCENT_ON_WHITE);

        if (accent == ACCENT_SYSTEM) {
            binding.toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.toolbar_navy));
            binding.toolbar.setTitleTextColor(Color.WHITE);
            return;
        }
        if (accent == ACCENT_CUSTOM) {
            int customColor = sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR);
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
            workingModel = saved;
            ownWhitelist = new HashSet<>(saved.whitelistedApps);
            ownBlacklist = new HashSet<>(saved.blacklistedApps);
            boolean hasOwnList = !ownWhitelist.isEmpty() || !ownBlacklist.isEmpty();
            appListMode = hasOwnList ? APP_LIST_MODE_OWN : APP_LIST_MODE_CURRENT;
        } else {
            workingModel = new PresetModel(presetNumber);
            workingModel.autoKillEnabled = sharedPreferences.getBoolean(KEY_AUTO_KILL_ENABLED, false);
            workingModel.periodicKillEnabled = sharedPreferences.getBoolean(KEY_PERIODIC_KILL_ENABLED, false);
            workingModel.killInterval = sharedPreferences.getInt(KEY_KILL_INTERVAL, DEFAULT_KILL_INTERVAL_MS);
            workingModel.killOnScreenOff = sharedPreferences.getBoolean(KEY_KILL_ON_SCREEN_OFF, false);
            workingModel.ramThresholdEnabled = sharedPreferences.getBoolean(KEY_RAM_THRESHOLD_ENABLED, false);
            workingModel.ramThreshold = sharedPreferences.getInt(KEY_RAM_THRESHOLD, DEFAULT_RAM_THRESHOLD_PERCENT);
            workingModel.autoKillType = autoKillManager.getAutoKillType();
            workingModel.killMode = autoKillManager.getKillMode();
            appListMode = APP_LIST_MODE_CURRENT;
        }
    }

    private void loadSettings() {
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
    }

    private void setupListeners() {
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

        binding.fabSavePreset.setOnClickListener(v -> savePreset());
    }

    private void applyAccentColors() {
        int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
        if (accent != ACCENT_CUSTOM) return;

        int color = sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR);
        int trackColor = darkenColor(color, 0.6f);

        TextView[] headers = {
                binding.sectionTitlePreset,
                binding.sectionTitleAutokill
        };
        for (TextView tv : headers) tv.setTextColor(color);

        android.content.res.ColorStateList thumbTint = new android.content.res.ColorStateList(
                new int[][] {
                        new int[] { android.R.attr.state_checked },
                        new int[] {}
                },
                new int[] { color, 0xFFAAAAAA }
        );
        android.content.res.ColorStateList trackTint = new android.content.res.ColorStateList(
                new int[][] {
                        new int[] { android.R.attr.state_checked },
                        new int[] {}
                },
                new int[] { trackColor, 0xFF555555 }
        );
        binding.switchPeriodicKill.setThumbTintList(thumbTint);
        binding.switchPeriodicKill.setTrackTintList(trackTint);
        binding.switchKillScreenOff.setThumbTintList(thumbTint);
        binding.switchKillScreenOff.setTrackTintList(trackTint);
        binding.switchRamThreshold.setThumbTintList(thumbTint);
        binding.switchRamThreshold.setTrackTintList(trackTint);

        binding.fabSavePreset.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(darkenColor(color, 0.7f)));
    }

    private void savePreset() {
        PresetModel other = presetManager.loadPreset(
                presetNumber == PresetModel.PRESET_1 ? PresetModel.PRESET_2 : PresetModel.PRESET_1);

        if (other != null && workingModel.overlapsWithExcludingSelf(other)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.preset_overlap_title))
                    .setMessage(getString(R.string.preset_overlap_message,
                            other.startHour, other.startMinute,
                            other.endHour, other.endMinute))
                    .setPositiveButton(getString(R.string.dialog_ok), null)
                    .show();
            return;
        }

        workingModel.autoKillEnabled = true;

        if (appListMode == APP_LIST_MODE_CURRENT) {
            workingModel.whitelistedApps = new HashSet<>(
                    sharedPreferences.getStringSet(KEY_WHITELISTED_APPS, new HashSet<>()));
            workingModel.blacklistedApps = new HashSet<>(
                    sharedPreferences.getStringSet(KEY_BLACKLISTED_APPS, new HashSet<>()));
        } else {
            workingModel.whitelistedApps = new HashSet<>(ownWhitelist);
            workingModel.blacklistedApps = new HashSet<>(ownBlacklist);
        }

        presetManager.savePreset(workingModel);
        presetManager.scheduleAlarms(workingModel);
        presetManager.checkAndApplyCurrentPreset();

        Log.d(TAG, "Preset #" + presetNumber + " saved and alarms scheduled");
        Toast.makeText(this, getString(R.string.preset_saved, presetNumber), Toast.LENGTH_SHORT).show();
        finish();
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
                    startHour[0] = h;
                    startMinute[0] = m;
                    btnFrom.setText(formatTime(h, m, use24h));
                }, startHour[0], startMinute[0], use24h).show());

        btnTo.setOnClickListener(v ->
                new android.app.TimePickerDialog(this, (tp, h, m) -> {
                    endHour[0] = h;
                    endMinute[0] = m;
                    btnTo.setText(formatTime(h, m, use24h));
                }, endHour[0], endMinute[0], use24h).show());

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.preset_time_range_dialog_title))
                .setView(view)
                .setPositiveButton(getString(R.string.dialog_save), (d, w) -> {
                    if (startHour[0] == endHour[0] && startMinute[0] == endMinute[0]) {
                        Toast.makeText(this, getString(R.string.preset_time_range_error_same), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    workingModel.startHour = startHour[0];
                    workingModel.startMinute = startMinute[0];
                    workingModel.endHour = endHour[0];
                    workingModel.endMinute = endMinute[0];
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
        showSingleChoiceDialog(getString(R.string.preset_app_list_mode_dialog_title),
                modes, appListMode, which -> {
                    appListMode = which;
                    updateAppListModeText();
                    updateListCounts();
                    updateKillModeListVisibility(workingModel.killMode);
                });
    }

    private void showKillIntervalDialog() {
        if (!workingModel.periodicKillEnabled) return;
        int currentInterval = workingModel.killInterval;
        int selectedIndex = 1;
        for (int i = 0; i < KILL_INTERVALS_MS.length; i++) {
            if (KILL_INTERVALS_MS[i] == currentInterval) { selectedIndex = i; break; }
        }
        showSingleChoiceDialog(getString(R.string.settings_check_frequency_title),
                getResources().getStringArray(R.array.settings_kill_interval_labels), selectedIndex, which -> {
                    workingModel.killInterval = KILL_INTERVALS_MS[which];
                    updateKillIntervalText(KILL_INTERVALS_MS[which]);
                });
    }

    private void showRamThresholdDialog() {
        int current = workingModel.ramThreshold;
        int selected = 1;
        for (int i = 0; i < RAM_THRESHOLD_VALUES.length; i++) {
            if (RAM_THRESHOLD_VALUES[i] == current) { selected = i; break; }
        }
        showSingleChoiceDialog(getString(R.string.settings_ram_threshold_dialog_title),
                getResources().getStringArray(R.array.settings_ram_threshold_labels), selected, which -> {
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
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
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
        showSingleChoiceDialog(getString(R.string.settings_kill_mode_dialog_title),
                modes, workingModel.killMode, which -> {
                    workingModel.killMode = which;
                    updateKillModeText(which);
                    updateKillModeListVisibility(which);
                });
    }

    private void showOwnWhitelistDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_filter, null);
        ListView listView = dialogView.findViewById(R.id.filter_list_view);
        ProgressBar progressBar = dialogView.findViewById(R.id.filter_loading_progress);
        EditText searchBox = dialogView.findViewById(R.id.filter_search);
        LinearLayout filterOptions = dialogView.findViewById(R.id.filter_options_container);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.settings_whitelist_dialog_title))
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
            FilterAppsAdapter filterAdapter = new FilterAppsAdapter(this, allApps, ownWhitelist);
            if (sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM) == ACCENT_CUSTOM)
                filterAdapter.setAccentColor(sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR));
            listView.setAdapter(filterAdapter);
            listView.setOnItemClickListener(null);

            progressBar.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
            searchBox.setVisibility(View.VISIBLE);
            filterOptions.setVisibility(View.VISIBLE);

            setupFilterListeners(dialogView, filterAdapter);
            appManager.updateRunningState(allApps, () -> {
                if (!dialog.isShowing()) return;
                filterAdapter.notifyDataSetChanged();
            });

            searchBox.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterAdapter.getFilter().filter(s); }
                @Override public void afterTextChanged(Editable s) {}
            });

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                ownWhitelist = filterAdapter.getSelectedPackages();
                updateListCounts();
                dialog.dismiss();
            });
        });
    }

    private void showOwnBlacklistDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_filter, null);
        ListView listView = dialogView.findViewById(R.id.filter_list_view);
        ProgressBar progressBar = dialogView.findViewById(R.id.filter_loading_progress);
        EditText searchBox = dialogView.findViewById(R.id.filter_search);
        LinearLayout filterOptions = dialogView.findViewById(R.id.filter_options_container);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.settings_blacklist_dialog_title))
                .setView(dialogView)
                .create();
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dialog_save), (d, w) -> {});
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.dialog_cancel), (d, w) -> d.dismiss());
        searchBox.setVisibility(View.GONE);
        dialog.show();
        resetDialogButtonColors(dialog);

        appManager.loadAllApps(allApps -> {
            FilterAppsAdapter filterAdapter = new FilterAppsAdapter(this, allApps, ownBlacklist);
            if (sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM) == ACCENT_CUSTOM)
                filterAdapter.setAccentColor(sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR));
            listView.setAdapter(filterAdapter);

            progressBar.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
            searchBox.setVisibility(View.VISIBLE);
            filterOptions.setVisibility(View.VISIBLE);

            setupFilterListeners(dialogView, filterAdapter);
            appManager.updateRunningState(allApps, () -> {
                if (!dialog.isShowing()) return;
                filterAdapter.notifyDataSetChanged();
            });

            searchBox.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterAdapter.getFilter().filter(s); }
                @Override public void afterTextChanged(Editable s) {}
            });

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                ownBlacklist = filterAdapter.getSelectedPackages();
                updateListCounts();
                dialog.dismiss();
            });
        });
    }

    private void updateTimeRangeText() {
        boolean use24h = android.text.format.DateFormat.is24HourFormat(this);
        String from = formatTime(workingModel.startHour, workingModel.startMinute, use24h);
        String to = formatTime(workingModel.endHour, workingModel.endMinute, use24h);
        binding.textPresetTimeRange.setText(from + " – " + to);
    }

    private void updateAppListModeText() {
        binding.textPresetAppListMode.setText(appListMode == APP_LIST_MODE_CURRENT
                ? getString(R.string.preset_app_list_mode_current)
                : getString(R.string.preset_app_list_mode_own));
    }

    private void updateKillIntervalText(int intervalMs) {
        String[] labels = getResources().getStringArray(R.array.settings_kill_interval_labels);
        for (int i = 0; i < KILL_INTERVALS_MS.length; i++) {
            if (KILL_INTERVALS_MS[i] == intervalMs) {
                binding.textKillInterval.setText(labels[i]);
                return;
            }
        }
        binding.textKillInterval.setText(getString(R.string.settings_interval_fallback, intervalMs / 1000));
    }

    private void updateKillIntervalVisibility(boolean periodicEnabled) {
        binding.layoutKillInterval.setAlpha(periodicEnabled ? 1.0f : 0.5f);
        binding.layoutKillInterval.setClickable(periodicEnabled);
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
        binding.layoutBlacklist.setVisibility(mode == 1 ? View.VISIBLE : View.GONE);
        binding.layoutWhitelist.setVisibility(mode == 0 ? View.VISIBLE : View.GONE);
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
            popup.getMenu().add(0, 0, 0, getString(R.string.filter_system))
                    .setCheckable(true).setChecked(showSystem[0]);
            popup.getMenu().add(0, 1, 1, getString(R.string.filter_user))
                    .setCheckable(true).setChecked(showUser[0]);
            popup.getMenu().add(0, 2, 2, getString(R.string.filter_running))
                    .setCheckable(true).setChecked(showRunning[0]);
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
        String label = getString(R.string.filter_sort_button);
        btn.setText(open ? label + "  ▲" : label + "  ▼");
    }

    private void showSingleChoiceDialog(String title, String[] options, int selected,
                                        java.util.function.IntConsumer onPick) {
        View view = getLayoutInflater().inflate(R.layout.dialog_single_choice, null);
        TextView titleView = view.findViewById(R.id.single_choice_title);
        RadioGroup group = view.findViewById(R.id.single_choice_group);
        titleView.setText(title);

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
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
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
        android.widget.Button btnPos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        android.widget.Button btnNeg = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        android.widget.Button btnNeu = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (btnPos != null) btnPos.setTextColor(color);
        if (btnNeg != null) btnNeg.setTextColor(color);
        if (btnNeu != null) btnNeu.setTextColor(color);
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
        int a = Color.alpha(color);
        int r = Math.round(Color.red(color) * factor);
        int g = Math.round(Color.green(color) * factor);
        int b = Math.round(Color.blue(color) * factor);
        return Color.argb(a, Math.min(r, 255), Math.min(g, 255), Math.min(b, 255));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        binding = null;
    }
}
