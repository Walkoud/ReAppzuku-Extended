package com.gree1d.reappzuku.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.MeasureSpec;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.PopupMenu;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import com.gree1d.reappzuku.R;
import com.gree1d.reappzuku.databinding.ActivitySettingsBinding;
import com.gree1d.reappzuku.core.AppDebugManager;
import com.gree1d.reappzuku.core.AppDebugManager.Category;
import com.gree1d.reappzuku.core.BackupManager;
import com.gree1d.reappzuku.core.BaseActivity;
import com.gree1d.reappzuku.manager.AdditionalScenariosManager;
import com.gree1d.reappzuku.manager.AutoKillManager;
import com.gree1d.reappzuku.manager.BackgroundAppManager;
import com.gree1d.reappzuku.manager.PresetManager;
import com.gree1d.reappzuku.manager.RestrictionsScheduler;
import com.gree1d.reappzuku.manager.SleepModeManager;
import com.gree1d.reappzuku.service.AppLaunchAccessibilityService;
import com.gree1d.reappzuku.service.AutoKillWorker;
import com.gree1d.reappzuku.service.ShappkyService;
import com.gree1d.reappzuku.utils.AppModel;
import com.gree1d.reappzuku.utils.PresetModel;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import static com.gree1d.reappzuku.core.AppConstants.*;
import static com.gree1d.reappzuku.core.PreferenceKeys.*;

abstract class SettingsActivityDialogs extends BaseActivity {

    private static final String FILE_NAME = "SettingsActivityDialogs";

    protected abstract ActivitySettingsBinding getBinding();
    protected abstract BackgroundAppManager getAppManager();
    protected abstract AutoKillManager getAutoKillManager();
    protected abstract SleepModeManager getSleepModeManager();
    protected abstract BackupManager getBackupManager();
    protected abstract RestrictionsScheduler getScheduler();
    protected abstract AdditionalScenariosManager getAdditionalScenariosManager();
    protected abstract ExecutorService getExecutor();
    protected abstract android.os.Handler getHandler();
    protected abstract SharedPreferences getSharedPreferences();
    protected abstract boolean isPresetActive();
    protected abstract boolean isServiceEnabled();
    protected abstract boolean getAutoKillPref(String key, boolean defVal);
    protected abstract int getAutoKillIntPref(String key, int defVal);
    protected abstract void putAutoKillIntPref(String key, int value);
    protected abstract void updateAutomationOptionsVisibility(boolean serviceEnabled, boolean periodicEnabled);
    protected abstract void updateKillModeVisibility();
    protected abstract void updateRamThresholdText(int threshold);
    protected abstract void updateRamThresholdLimitVisibility(boolean enabled);
    protected abstract void updateKillIntervalText(int intervalMs);
    protected abstract void updateAutoKillTypeText(int type);
    protected abstract void updateThemeText(int themeValue, boolean isAmoled);
    protected abstract void updateAccentText(int accentValue);
    protected abstract void updateOnColorText(int onColor);
    protected abstract void updateOnColorLayoutVisibility(int accent);
    protected abstract void updateAccentLayoutEnabled(int themeValue);
    protected abstract void updateNotificationModeText(int mode);
    protected abstract void updateSleepModeDelayText(long delayMs);
    protected abstract void updateAdditionalScenariosSummary();
    protected abstract void applyAutomationStateFromPreferences();
    protected abstract void loadSettings();
    protected abstract int darkenColor(int color, float factor);

    protected void showPresetActiveDialog() {
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.antichange_settings)
                .setPositiveButton(R.string.dialog_close, null)
                .show();
    }

    protected void showAutoKillTypeDialog() {
        String[] types = {
                getString(R.string.settings_auto_kill_type_force_stop),
                getString(R.string.settings_auto_kill_type_kill)
        };

        View titleView = LayoutInflater.from(this).inflate(R.layout.dialog_killtype_info, null);
        ((TextView) titleView.findViewById(R.id.dialog_title)).setText(R.string.settings_auto_kill_type_title);

        View bodyView = getLayoutInflater().inflate(R.layout.dialog_single_choice, null);
        bodyView.findViewById(R.id.single_choice_title).setVisibility(View.GONE);
        android.widget.RadioGroup group = bodyView.findViewById(R.id.single_choice_group);
        int accent = getSharedPreferences().getInt(KEY_ACCENT, ACCENT_SYSTEM);
        android.content.res.ColorStateList tint = (accent == ACCENT_CUSTOM)
                ? android.content.res.ColorStateList.valueOf(getDialogAccentColor()) : null;
        for (int i = 0; i < types.length; i++) {
            android.widget.RadioButton rb = new android.widget.RadioButton(this);
            rb.setText(types[i]); rb.setId(1000 + i);
            int dp12 = (int) (getResources().getDisplayMetrics().density * 12);
            rb.setPadding(dp12, dp12, dp12, dp12);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            rb.setLayoutParams(lp);
            if (tint != null) rb.setButtonTintList(tint);
            group.addView(rb);
        }
        group.check(1000 + getAutoKillManager().getAutoKillType());

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setCustomTitle(titleView)
                .setView(bodyView)
                .create();
        titleView.findViewById(R.id.btn_help).setOnClickListener(v -> {
            dialog.dismiss();
            showAutoKillTypeHelpDialog(() -> showAutoKillTypeDialog());
        });
        group.setOnCheckedChangeListener((g, id) -> {
            getAutoKillManager().setAutoKillType(id - 1000);
            updateAutoKillTypeText(id - 1000);
            dialog.dismiss();
        });
        dialog.show();
        resetDialogButtonColors(dialog);
    }

    protected void showAutoKillTypeHelpDialog(Runnable onBack) {
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

    protected void showKillModeDialog() {
        String[] modes = {
                getString(R.string.settings_mode_whitelist),
                getString(R.string.settings_mode_blacklist)
        };
        showSingleChoiceDialog(getString(R.string.settings_kill_mode_dialog_title),
                modes, getAutoKillManager().getKillMode(), which -> {
                    getAutoKillManager().setKillMode(which);
                    updateKillModeVisibility();
                    if (which == 0) {
                        boolean autoKillEnabled = getAutoKillPref(KEY_AUTO_KILL_ENABLED, false);
                        Set<String> whitelistedApps = getSharedPreferences().getStringSet(KEY_WHITELISTED_APPS, new HashSet<>());
                        if (autoKillEnabled && whitelistedApps.isEmpty()) {
                            resetDialogButtonColors(new MaterialAlertDialogBuilder(this)
                                    .setTitle(R.string.dialog_unsafe_whitelist_title)
                                    .setMessage(R.string.dialog_unsafe_whitelist_message)
                                    .setPositiveButton(R.string.dialog_unsafe_whitelist_ok, (d, w) -> d.dismiss())
                                    .setCancelable(false)
                                    .show());
                        }
                    }
                });
    }
    
    protected void showBlacklistDialog() {
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_filter, null);
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

        getAppManager().loadAllApps(allApps -> {
            allApps = filterOutProtected(allApps);
            Set<String> blacklisted = getAutoKillManager().getBlacklistedApps();
            FilterAppsAdapter filterAdapter = new FilterAppsAdapter(this, allApps, blacklisted);
            if (getSharedPreferences().getInt(KEY_ACCENT, ACCENT_SYSTEM) == ACCENT_CUSTOM)
                filterAdapter.setAccentColor(getSharedPreferences().getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR));
            listView.setAdapter(filterAdapter);
            progressBar.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
            searchBox.setVisibility(View.VISIBLE);
            filterOptions.setVisibility(View.VISIBLE);

            setupFilterListeners(dialogView, filterAdapter);
            getAppManager().updateRunningState(allApps, () -> {
                if (!dialog.isShowing()) return;
                filterAdapter.notifyDataSetChanged();
            });

            searchBox.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterAdapter.getFilter().filter(s); }
                @Override public void afterTextChanged(Editable s) {}
            });

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                getAutoKillManager().saveBlacklistedApps(filterAdapter.getSelectedPackages());
                dialog.dismiss();
            });
        });
    }

    protected void showWhitelistDialog() {
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_filter, null);
        ListView listView = dialogView.findViewById(R.id.filter_list_view);
        ProgressBar progressBar = dialogView.findViewById(R.id.filter_loading_progress);
        EditText searchBox = dialogView.findViewById(R.id.filter_search);
        LinearLayout filterOptions = dialogView.findViewById(R.id.filter_options_container);

        AlertDialog whitelistDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.settings_whitelist_dialog_title))
                .setView(dialogView)
                .create();
        whitelistDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.dialog_cancel), (dialog, which) -> dialog.dismiss());
        whitelistDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dialog_save), (dialog, which) -> {});

        progressBar.setVisibility(View.VISIBLE);
        listView.setVisibility(View.GONE);
        searchBox.setVisibility(View.GONE);
        whitelistDialog.show();
        resetDialogButtonColors(whitelistDialog);

        getAppManager().loadAllApps(allApps -> {
            allApps = filterOutProtected(allApps);
            Set<String> whitelistedApps = getAppManager().getWhitelistedApps();
            FilterAppsAdapter filterAdapter = new FilterAppsAdapter(this, allApps, whitelistedApps);
            if (getSharedPreferences().getInt(KEY_ACCENT, ACCENT_SYSTEM) == ACCENT_CUSTOM)
                filterAdapter.setAccentColor(getSharedPreferences().getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR));
            listView.setAdapter(filterAdapter);
            listView.setOnItemClickListener(null);

            progressBar.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
            searchBox.setVisibility(View.VISIBLE);
            filterOptions.setVisibility(View.VISIBLE);

            setupFilterListeners(dialogView, filterAdapter);
            getAppManager().updateRunningState(allApps, () -> {
                if (!whitelistDialog.isShowing()) return;
                filterAdapter.notifyDataSetChanged();
            });

            searchBox.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterAdapter.getFilter().filter(s); }
                @Override public void afterTextChanged(Editable s) {}
            });

            whitelistDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                getAppManager().saveWhitelistedApps(filterAdapter.getSelectedPackages());
                whitelistDialog.dismiss();
            });
        });
    }

    protected void showHiddenAppsDialog() {
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_filter, null);
        ListView listView = dialogView.findViewById(R.id.filter_list_view);
        ProgressBar progressBar = dialogView.findViewById(R.id.filter_loading_progress);
        EditText searchBox = dialogView.findViewById(R.id.filter_search);
        LinearLayout filterOptions = dialogView.findViewById(R.id.filter_options_container);

        AlertDialog filterDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.settings_hidden_apps_dialog_title))
                .setView(dialogView)
                .create();
        filterDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.dialog_cancel), (dialog, which) -> dialog.dismiss());
        filterDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dialog_save), (dialog, which) -> {});

        progressBar.setVisibility(View.VISIBLE);
        listView.setVisibility(View.GONE);
        searchBox.setVisibility(View.GONE);
        filterDialog.show();
        resetDialogButtonColors(filterDialog);

        getAppManager().loadAllApps(allApps -> {
            Set<String> hiddenApps = getAppManager().getHiddenApps();
            FilterAppsAdapter filterAdapter = new FilterAppsAdapter(this, allApps, hiddenApps);
            if (getSharedPreferences().getInt(KEY_ACCENT, ACCENT_SYSTEM) == ACCENT_CUSTOM)
                filterAdapter.setAccentColor(getSharedPreferences().getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR));
            listView.setAdapter(filterAdapter);
            listView.setOnItemClickListener(null);

            progressBar.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
            searchBox.setVisibility(View.VISIBLE);
            filterOptions.setVisibility(View.VISIBLE);

            setupFilterListeners(dialogView, filterAdapter);
            getAppManager().updateRunningState(allApps, () -> {
                if (!filterDialog.isShowing()) return;
                filterAdapter.notifyDataSetChanged();
            });

            searchBox.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterAdapter.getFilter().filter(s); }
                @Override public void afterTextChanged(Editable s) {}
            });

            filterDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                getAppManager().saveHiddenApps(filterAdapter.getSelectedPackages());
                filterDialog.dismiss();
            });
        });
    }

    protected void showBackgroundRestrictionDialog() {
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_filter, null);
        ListView listView = dialogView.findViewById(R.id.filter_list_view);
        ProgressBar progressBar = dialogView.findViewById(R.id.filter_loading_progress);
        EditText searchBox = dialogView.findViewById(R.id.filter_search);
        LinearLayout filterOptions = dialogView.findViewById(R.id.filter_options_container);

        View titleView = inflater.inflate(R.layout.dialog_backgroundrest_help, null);
        ((TextView) titleView.findViewById(R.id.dialog_title)).setText(R.string.settings_background_restriction_title);

        AlertDialog restrictionDialog = new MaterialAlertDialogBuilder(this)
                .setCustomTitle(titleView)
                .setView(dialogView)
                .create();
        restrictionDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.dialog_cancel), (dialog, which) -> dialog.dismiss());
        restrictionDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dialog_save), (dialog, which) -> {});

        progressBar.setVisibility(View.VISIBLE);
        listView.setVisibility(View.GONE);
        searchBox.setVisibility(View.GONE);
        restrictionDialog.show();
        resetDialogButtonColors(restrictionDialog);

        titleView.findViewById(R.id.btn_help).setOnClickListener(v -> {
            restrictionDialog.dismiss();
            showRestrictionTypeHelpDialog(() -> showBackgroundRestrictionDialog());
        });

        getAppManager().loadBackgroundRestrictionApps(rawApps -> {
            List<AppModel> allApps = filterOutProtected(rawApps);
            Set<String> desiredRestrictedApps = getAppManager().getBackgroundRestrictedApps();
            Set<String> hardRestrictedApps    = getAppManager().getHardRestrictedApps();
            Set<String> mediumRestrictedApps  = getAppManager().getMediumRestrictedApps();
            Set<String> manualRestrictedApps  = getAppManager().getManualRestrictedApps();
            java.util.Map<String, Integer> initialMasks = buildInitialManualMasks(manualRestrictedApps);
            java.util.Map<String, Integer> initialBuckets = buildInitialManualBuckets(manualRestrictedApps);

            FilterAppsAdapter filterAdapter = new FilterAppsAdapter(
                    this, allApps,
                    desiredRestrictedApps,
                    hardRestrictedApps,
                    mediumRestrictedApps,
                    manualRestrictedApps,
                    initialMasks,
                    initialBuckets);
            if (getSharedPreferences().getInt(KEY_ACCENT, ACCENT_SYSTEM) == ACCENT_CUSTOM)
                filterAdapter.setAccentColor(getSharedPreferences().getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR));
            listView.setAdapter(filterAdapter);
            listView.setOnItemClickListener(null);

            progressBar.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
            searchBox.setVisibility(View.VISIBLE);
            filterOptions.setVisibility(View.VISIBLE);

            setupFilterListeners(dialogView, filterAdapter);
            getAppManager().updateRunningState(allApps, () -> {
                if (!restrictionDialog.isShowing()) return;
                filterAdapter.notifyDataSetChanged();
            });

            searchBox.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterAdapter.getFilter().filter(s); }
                @Override public void afterTextChanged(Editable s) {}
            });

            filterAdapter.setOnSelectionChangedListener(() ->
                    restrictionDialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(getString(R.string.dialog_apply)));

            Runnable doApply = () -> {
                Set<String> targetPackages  = filterAdapter.getSelectedPackages();
                Set<String> hardPackages    = filterAdapter.getHardRestrictedPackages();
                Set<String> mediumPackages  = filterAdapter.getMediumRestrictedPackages();
                Set<String> manualPackages  = filterAdapter.getManualRestrictedPackages();
                java.util.Map<String, Integer> opsMasks = filterAdapter.getManualOpsMasks();
                java.util.Map<String, Integer> buckets  = filterAdapter.getManualBuckets();

                for (java.util.Map.Entry<String, Integer> e : opsMasks.entrySet()) {
                    getAppManager().saveManualOpsMask(e.getKey(), e.getValue());
                }
                for (java.util.Map.Entry<String, Integer> e : buckets.entrySet()) {
                    getAppManager().saveManualBucket(e.getKey(), e.getValue());
                }

                Set<String> newMediumSet = new java.util.HashSet<>(mediumPackages);
                newMediumSet.retainAll(targetPackages);
                getAppManager().saveMediumRestrictedApps(newMediumSet);

                Set<String> newManualSet = new java.util.HashSet<>(manualPackages);
                newManualSet.retainAll(targetPackages);
                getAppManager().saveManualRestrictedApps(newManualSet);

                Set<String> currentDesired = new java.util.HashSet<>(desiredRestrictedApps);
                Set<String> packagesToRestrict = new java.util.HashSet<>(targetPackages);
                packagesToRestrict.removeAll(currentDesired);

                int systemAppCount = 0;
                for (AppModel app : allApps) {
                    if (packagesToRestrict.contains(app.getPackageName()) && app.isSystemApp()) systemAppCount++;
                }

                if (systemAppCount > 0) {
                    resetDialogButtonColors(new MaterialAlertDialogBuilder(this)
                            .setTitle(getString(R.string.settings_restriction_system_apps_title))
                            .setMessage(getString(R.string.settings_restriction_system_apps_message, systemAppCount))
                            .setPositiveButton(getString(R.string.settings_restriction_system_apps_confirm), (d2, w2) ->
                                    getAppManager().applyBackgroundRestriction(targetPackages, hardPackages, null))
                            .setNegativeButton(getString(R.string.dialog_cancel), null)
                            .show());
                } else if (!packagesToRestrict.isEmpty()) {
                    resetDialogButtonColors(new MaterialAlertDialogBuilder(this)
                            .setTitle(getString(R.string.settings_restriction_warning_title))
                            .setMessage(getString(R.string.settings_restriction_warning_message))
                            .setPositiveButton(getString(R.string.dialog_apply), (d2, w2) ->
                                    getAppManager().applyBackgroundRestriction(targetPackages, hardPackages, null))
                            .setNegativeButton(getString(R.string.dialog_cancel), null)
                            .show());
                } else {
                    getAppManager().applyBackgroundRestriction(targetPackages, hardPackages, null);
                }
            };

            restrictionDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dialog_save), (dialog, which) -> doApply.run());
        });
    }

    private java.util.Map<String, Integer> buildInitialManualMasks(Set<String> manualPackages) {
        java.util.Map<String, Integer> masks = new java.util.HashMap<>();
        for (String pkg : manualPackages) {
            masks.put(pkg, getAppManager().getManualOpsMask(pkg));
        }
        return masks;
    }

    private java.util.Map<String, Integer> buildInitialManualBuckets(Set<String> manualPackages) {
        java.util.Map<String, Integer> buckets = new java.util.HashMap<>();
        for (String pkg : manualPackages) {
            int bucket = getAppManager().getManualBucket(pkg);
            if (bucket != 0) buckets.put(pkg, bucket);
        }
        return buckets;
    }

    protected void showRestrictionTypeHelpDialog(Runnable onBack) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int start = sb.length();
        sb.append(getString(R.string.bgrest_help_soft_title));
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), 0);
        sb.append("\n").append(getString(R.string.bgrest_help_soft_body)).append("\n\n\n\n");
        start = sb.length();
        sb.append(getString(R.string.bgrest_help_medium_title));
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), 0);
        sb.append("\n").append(getString(R.string.bgrest_help_medium_body)).append("\n\n\n\n");
        start = sb.length();
        sb.append(getString(R.string.bgrest_help_hard_title));
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), 0);
        sb.append("\n").append(getString(R.string.bgrest_help_hard_body)).append("\n\n\n\n");
        start = sb.length();
        sb.append(getString(R.string.bgrest_help_manual_title));
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), 0);
        sb.append("\n").append(getString(R.string.bgrest_help_manual_body));

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.dialog_backgroundrest_title))
                .setMessage(sb)
                .setPositiveButton(getString(R.string.dialog_ok_got_it), (d, w) -> {
                    d.dismiss();
                    if (onBack != null) onBack.run();
                })
                .create();
        dialog.show();
        resetDialogButtonColors(dialog);
        TextView messageView = dialog.findViewById(android.R.id.message);
        if (messageView != null) messageView.setText(sb);
    }

    protected void showSleepModeDelayDialog() {
        long current = getSharedPreferences().getLong(KEY_SLEEP_MODE_DELAY, DEFAULT_SLEEP_MODE_DELAY_MS);
        int selected = SLEEP_MODE_DELAYS_MS.length - 1;
        for (int i = 0; i < SLEEP_MODE_DELAYS_MS.length; i++) {
            if (SLEEP_MODE_DELAYS_MS[i] == current) { selected = i; break; }
        }
        showSingleChoiceDialog(getString(R.string.settings_sleep_mode_delay_title),
                getResources().getStringArray(R.array.settings_sleep_mode_delay_labels), selected, which -> {
                    getSharedPreferences().edit().putLong(KEY_SLEEP_MODE_DELAY, SLEEP_MODE_DELAYS_MS[which]).apply();
                    updateSleepModeDelayText(SLEEP_MODE_DELAYS_MS[which]);
                });
    }

    protected void showSleepModeAppsDialog() {
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_filter, null);
        ListView listView = dialogView.findViewById(R.id.filter_list_view);
        ProgressBar progressBar = dialogView.findViewById(R.id.filter_loading_progress);
        EditText searchBox = dialogView.findViewById(R.id.filter_search);
        LinearLayout filterOptions = dialogView.findViewById(R.id.filter_options_container);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.settings_sleep_mode_apps_dialog_title))
                .setView(dialogView)
                .create();
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.dialog_cancel), (d, w) -> d.dismiss());
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dialog_save), (d, w) -> {});

        progressBar.setVisibility(View.VISIBLE);
        listView.setVisibility(View.GONE);
        searchBox.setVisibility(View.GONE);
        filterOptions.setVisibility(View.GONE);
        dialog.show();
        resetDialogButtonColors(dialog);

        getSleepModeManager().loadSleepModeApps(rawApps -> {
            List<AppModel> allApps = filterOutProtected(rawApps);
            Set<String> timerApps = getSleepModeManager().getSleepModeApps();
            Set<String> permanentApps = getSleepModeManager().getPermanentFreezeApps();
            FilterAppsAdapter filterAdapter = new FilterAppsAdapter(this, allApps, timerApps, permanentApps, getSleepModeManager(), true);
            if (getSharedPreferences().getInt(KEY_ACCENT, ACCENT_SYSTEM) == ACCENT_CUSTOM)
                filterAdapter.setAccentColor(getSharedPreferences().getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR));
            listView.setAdapter(filterAdapter);
            listView.setOnItemClickListener(null);

            progressBar.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
            searchBox.setVisibility(View.VISIBLE);
            filterOptions.setVisibility(View.VISIBLE);

            setupFilterListeners(dialogView, filterAdapter);
            getAppManager().updateRunningState(allApps, () -> {
                if (!dialog.isShowing()) return;
                filterAdapter.notifyDataSetChanged();
            });

            searchBox.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterAdapter.getFilter().filter(s); }
                @Override public void afterTextChanged(Editable s) {}
            });

            filterAdapter.setOnSelectionChangedListener(() ->
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(getString(R.string.dialog_apply)));

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                getSleepModeManager().saveSleepModeApps(
                        filterAdapter.getTimerPackages(),
                        filterAdapter.getPermanentPackages(),
                        filterAdapter.getFreezeMethodMap(),
                        null);
                dialog.dismiss();
            });
        });
    }

    protected void showRamThresholdDialog() {
        int current = getAutoKillIntPref(KEY_RAM_THRESHOLD, DEFAULT_RAM_THRESHOLD_PERCENT);
        int selected = 1;
        for (int i = 0; i < RAM_THRESHOLD_VALUES.length; i++) {
            if (RAM_THRESHOLD_VALUES[i] == current) { selected = i; break; }
        }
        showSingleChoiceDialog(getString(R.string.settings_ram_threshold_dialog_title),
                getResources().getStringArray(R.array.settings_ram_threshold_labels), selected, which -> {
                    putAutoKillIntPref(KEY_RAM_THRESHOLD, RAM_THRESHOLD_VALUES[which]);
                    updateRamThresholdText(RAM_THRESHOLD_VALUES[which]);
                });
    }

    protected void showKillIntervalDialog() {
        if (!getBinding().switchAutoKill.isChecked() || !getBinding().switchPeriodicKill.isChecked()) return;
        int currentInterval = getAutoKillIntPref(KEY_KILL_INTERVAL, DEFAULT_KILL_INTERVAL_MS);
        int selectedIndex = 1;
        for (int i = 0; i < KILL_INTERVALS_MS.length; i++) {
            if (KILL_INTERVALS_MS[i] == currentInterval) { selectedIndex = i; break; }
        }
        showSingleChoiceDialog(getString(R.string.settings_check_frequency_title),
                getResources().getStringArray(R.array.settings_kill_interval_labels), selectedIndex, which -> {
                    putAutoKillIntPref(KEY_KILL_INTERVAL, KILL_INTERVALS_MS[which]);
                    updateKillIntervalText(KILL_INTERVALS_MS[which]);
                });
    }

    protected void showNotificationModeDialog() {
        int current = getSharedPreferences().getInt(KEY_NOTIFICATION_MODE, NOTIFICATION_MODE_ALL);
        String[] options = {
                getString(R.string.settings_notification_mode_all),
                getString(R.string.settings_notification_mode_important_only)
        };
        showSingleChoiceDialog(getString(R.string.settings_notification_mode_title),
                options, current, which -> {
                    getSharedPreferences().edit().putInt(KEY_NOTIFICATION_MODE, which).apply();
                    updateNotificationModeText(which);
                });
    }

    protected void showThemeDialog() {
        int currentTheme = getSharedPreferences().getInt(KEY_THEME,
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        boolean isAmoled = getSharedPreferences().getBoolean(KEY_AMOLED, false);
        int selectedIndex = isAmoled ? 3 : 0;
        if (!isAmoled) {
            for (int i = 0; i < THEME_VALUES.length; i++) {
                if (THEME_VALUES[i] == currentTheme) { selectedIndex = i; break; }
            }
        }
        showSingleChoiceDialog(getString(R.string.settings_theme_dialog_title),
                getResources().getStringArray(R.array.settings_theme_labels), selectedIndex, which -> {
                    if (which == 3) {
                        getSharedPreferences().edit()
                                .putBoolean(KEY_AMOLED, true)
                                .putInt(KEY_THEME, androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
                                .apply();
                        updateThemeText(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES, true);
                        updateAccentLayoutEnabled(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
                    } else {
                        int newTheme = THEME_VALUES[which];
                        getSharedPreferences().edit().putInt(KEY_THEME, newTheme).putBoolean(KEY_AMOLED, false).apply();
                        updateThemeText(newTheme, false);
                        updateAccentLayoutEnabled(newTheme);
                        if (newTheme == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
                            getSharedPreferences().edit().putInt(KEY_ACCENT, ACCENT_SYSTEM).apply();
                            updateAccentText(ACCENT_SYSTEM);
                        }
                        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(newTheme);
                    }
                    recreate();
                });
    }

    protected void showAccentDialog() {
        int currentAccent = getSharedPreferences().getInt(KEY_ACCENT, ACCENT_INDIGO);

        String[] builtinLabels = getResources().getStringArray(R.array.settings_accent_labels);
        String[] allLabels = new String[builtinLabels.length + 1];
        allLabels[0] = builtinLabels[0];
        allLabels[1] = getString(R.string.settings_accent_custom_label);
        System.arraycopy(builtinLabels, 1, allLabels, 2, builtinLabels.length - 1);

        int selectedIndex = (currentAccent == ACCENT_SYSTEM) ? 0
                : (currentAccent == ACCENT_CUSTOM) ? 1
                : currentAccent + 1;

        android.view.View view = getLayoutInflater().inflate(R.layout.dialog_single_choice, null);
        android.widget.TextView titleView = view.findViewById(R.id.single_choice_title);
        android.widget.RadioGroup group = view.findViewById(R.id.single_choice_group);

        titleView.setText(getString(R.string.settings_accent_title));

        int accent = getSharedPreferences().getInt(KEY_ACCENT, ACCENT_SYSTEM);
        android.content.res.ColorStateList tint = (accent == ACCENT_CUSTOM)
                ? android.content.res.ColorStateList.valueOf(getDialogAccentColor())
                : null;

        final AlertDialog[] dialogRef = {null};

        int dp12 = (int) (getResources().getDisplayMetrics().density * 12);
        for (int i = 0; i < allLabels.length; i++) {
            android.widget.RadioButton rb = new android.widget.RadioButton(this);
            rb.setText(allLabels[i]);
            rb.setId(1000 + i);
            rb.setPadding(dp12, dp12, dp12, dp12);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            rb.setLayoutParams(lp);
            if (tint != null) rb.setButtonTintList(tint);
            if (i == 1) {
                rb.setOnClickListener(v -> {
                    if (rb.getId() == group.getCheckedRadioButtonId()) {
                        openCustomColorPicker(dialogRef[0]);
                    }
                });
            }
            group.addView(rb);
        }
        group.check(1000 + selectedIndex);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(view)
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .create();

        dialogRef[0] = dialog;

        final boolean[] userInteracted = {false};
        group.setOnCheckedChangeListener((g, checkedId) -> {
            if (!userInteracted[0] || checkedId == -1) return;
            int which = checkedId - 1000;
            if (which == 0) {
                getSharedPreferences().edit().putInt(KEY_ACCENT, ACCENT_SYSTEM).apply();
                updateAccentText(ACCENT_SYSTEM);
                updateOnColorLayoutVisibility(ACCENT_SYSTEM);
                dialog.dismiss();
                recreate();
            } else if (which == 1) {
                openCustomColorPicker(dialog);
            } else {
                int accentValue = which - 1;
                getSharedPreferences().edit().putInt(KEY_ACCENT, accentValue).apply();
                updateAccentText(accentValue);
                updateOnColorLayoutVisibility(accentValue);
                dialog.dismiss();
                recreate();
            }
        });
        view.post(() -> userInteracted[0] = true);

        dialog.show();
        resetDialogButtonColors(dialog);
    }

    protected void openCustomColorPicker(AlertDialog parentDialog) {
        if (parentDialog != null) parentDialog.dismiss();
        int currentCustomColor = getSharedPreferences().getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR);
        ColorPickerDialog.show(this, currentCustomColor, pickedColor -> {
            getSharedPreferences().edit()
                    .putInt(KEY_ACCENT, ACCENT_CUSTOM)
                    .putInt(KEY_ACCENT_CUSTOM_COLOR, pickedColor)
                    .apply();
            updateAccentText(ACCENT_CUSTOM);
            updateOnColorLayoutVisibility(ACCENT_CUSTOM);
            recreate();
        });
    }

    protected void showAccentOnColorDialog() {
        int current = getSharedPreferences().getInt(KEY_ACCENT_ON_COLOR, ACCENT_ON_WHITE);
        String[] options = {
                getString(R.string.settings_accent_on_color_white),
                getString(R.string.settings_accent_on_color_black)
        };
        showSingleChoiceDialog(getString(R.string.settings_accent_on_color_title),
                options, current, which -> {
                    getSharedPreferences().edit().putInt(KEY_ACCENT_ON_COLOR, which).apply();
                    updateOnColorText(which);
                    recreate();
                });
    }

    protected void showRestrictionsSchedulerDialog() {
        Set<String> bgApps    = getAppManager().getBackgroundRestrictedApps();
        Set<String> sleepApps = getSleepModeManager().getSleepModeApps();
        Set<String> eligible  = new HashSet<>(bgApps);
        eligible.addAll(sleepApps);

        if (eligible.isEmpty()) {
            Toast.makeText(this, getString(R.string.scheduler_no_eligible_apps), Toast.LENGTH_SHORT).show();
            return;
        }

        PackageManager pm = getPackageManager();
        List<SchedulerAppItem> items = new ArrayList<>();
        boolean use24h = android.text.format.DateFormat.is24HourFormat(this);

        for (String pkg : eligible) {
            String appName;
            android.graphics.drawable.Drawable icon;
            try {
                android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                appName = pm.getApplicationLabel(ai).toString();
                icon    = pm.getApplicationIcon(ai);
            } catch (PackageManager.NameNotFoundException e) {
                appName = pkg;
                icon    = ContextCompat.getDrawable(this, android.R.drawable.sym_def_app_icon);
            }
            items.add(new SchedulerAppItem(pkg, appName, icon, findScheduleForPackage(pkg)));
        }
        java.util.Collections.sort(items, (a, b) -> a.appName.compareToIgnoreCase(b.appName));

        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_scheduler_list, null);
        android.widget.LinearLayout listContainer =
                dialogView.findViewById(R.id.scheduler_list_container);

        AlertDialog mainDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.settings_restrictions_scheduler_title))
                .setView(dialogView)
                .setPositiveButton(getString(R.string.dialog_ok), (d, w) -> d.dismiss())
                .create();
        mainDialog.show();
        resetDialogButtonColors(mainDialog);

        for (SchedulerAppItem item : items) {
            View row = inflater.inflate(R.layout.item_scheduler_app, listContainer, false);

            ((android.widget.ImageView) row.findViewById(R.id.scheduler_app_icon))
                    .setImageDrawable(item.icon);
            ((TextView) row.findViewById(R.id.scheduler_app_name))
                    .setText(item.appName);

            android.widget.ImageView schedIcon = row.findViewById(R.id.scheduler_entry_icon);
            TextView schedTime = row.findViewById(R.id.scheduler_entry_time);

            if (item.entry != null) {
                schedIcon.setVisibility(View.VISIBLE);
                schedTime.setVisibility(View.VISIBLE);
                schedTime.setText(formatScheduleTime(item.entry, use24h));
                int accent = getSharedPreferences().getInt(KEY_ACCENT, ACCENT_SYSTEM);
                if (accent == ACCENT_CUSTOM)
                    schedTime.setTextColor(getDialogAccentColor());
            } else {
                schedIcon.setVisibility(View.INVISIBLE);
                schedTime.setVisibility(View.INVISIBLE);
            }

            row.setOnClickListener(v -> showScheduleEntryDialog(item, use24h, mainDialog));
            listContainer.addView(row);
        }
    }

    protected void showScheduleEntryDialog(SchedulerAppItem item, boolean use24h,
                                            AlertDialog parentDialog) {
        Set<String> bgApps    = getAppManager().getBackgroundRestrictedApps();
        Set<String> sleepApps = getSleepModeManager().getSleepModeApps();
        boolean hasBg    = bgApps.contains(item.packageName);
        boolean hasSleep = sleepApps.contains(item.packageName);

        RestrictionsScheduler.ScheduleEntry src = item.entry;

        int[] startHour        = { src != null ? src.startHour        : 8  };
        int[] startMinute      = { src != null ? src.startMinute      : 0  };
        int[] endHour          = { src != null ? src.endHour          : 9  };
        int[] endMinute        = { src != null ? src.endMinute        : 0  };
        int[] protectFlags     = { src != null ? src.protectFlags     : RestrictionsScheduler.PROTECT_ALL };
        int[] onActivateAction = { src != null ? src.onActivateAction : RestrictionsScheduler.ON_ACTIVATE_NOTHING };

        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_scheduler_entry, null);

        CheckBox cbAutoKill = dialogView.findViewById(R.id.scheduler_cb_autokill);
        CheckBox cbBg       = dialogView.findViewById(R.id.scheduler_cb_bg);
        CheckBox cbSleep    = dialogView.findViewById(R.id.scheduler_cb_sleep);

        cbAutoKill.setChecked((protectFlags[0] & RestrictionsScheduler.PROTECT_AUTO_KILL) != 0);

        cbBg.setEnabled(hasBg);
        cbBg.setChecked(hasBg && (protectFlags[0] & RestrictionsScheduler.PROTECT_BG_RESTRICTIONS) != 0);

        cbSleep.setEnabled(hasSleep);
        cbSleep.setChecked(hasSleep && (protectFlags[0] & RestrictionsScheduler.PROTECT_SLEEP_MODE) != 0);

        TextView btnFrom = dialogView.findViewById(R.id.scheduler_btn_time_from);
        TextView btnTo   = dialogView.findViewById(R.id.scheduler_btn_time_to);
        btnFrom.setText(formatTime(startHour[0], startMinute[0], use24h));
        btnTo.setText(formatTime(endHour[0], endMinute[0], use24h));

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

        String[] selectedComponent = { src != null ? src.componentName : null };
        int[]    selectedCompType  = { src != null ? src.onActivateAction : RestrictionsScheduler.ON_ACTIVATE_NOTHING };

        android.widget.RadioGroup rgAction = dialogView.findViewById(R.id.scheduler_rg_action);
        LinearLayout componentContainer   = dialogView.findViewById(R.id.scheduler_component_container);
        TextView btnComponent             = dialogView.findViewById(R.id.scheduler_btn_component);
        CheckBox cbSetBucket              = dialogView.findViewById(R.id.scheduler_cb_set_bucket_active);

        boolean hadLaunch = onActivateAction[0] != RestrictionsScheduler.ON_ACTIVATE_NOTHING
                && selectedComponent[0] != null;
        rgAction.check(hadLaunch ? R.id.scheduler_rb_launch : R.id.scheduler_rb_none);
        componentContainer.setVisibility(hadLaunch ? View.VISIBLE : View.GONE);
        btnComponent.setText(selectedComponent[0] != null
                ? shortComponentName(selectedComponent[0])
                : getString(R.string.scheduler_component_not_selected));
        cbSetBucket.setChecked(src != null && src.setBucketActive);

        rgAction.setOnCheckedChangeListener((rg, id) -> {
            if (id == R.id.scheduler_rb_launch) {
                componentContainer.setVisibility(View.VISIBLE);
                if (selectedCompType[0] == RestrictionsScheduler.ON_ACTIVATE_NOTHING) {
                    selectedCompType[0] = RestrictionsScheduler.ON_ACTIVATE_ACTIVITY;
                }
                onActivateAction[0] = selectedCompType[0];
            } else {
                componentContainer.setVisibility(View.GONE);
                onActivateAction[0] = RestrictionsScheduler.ON_ACTIVATE_NOTHING;
            }
        });

        btnComponent.setOnClickListener(v ->
                showComponentPickerDialog(item.packageName, selectedComponent, selectedCompType,
                        onActivateAction, btnComponent));

        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(item.appName)
                .setView(dialogView)
                .setPositiveButton(getString(R.string.dialog_save), null)
                .setNegativeButton(getString(R.string.dialog_close), (d, w) -> d.dismiss());
        if (src != null) {
            builder.setNeutralButton(getString(R.string.scheduler_btn_remove), null);
        }

        AlertDialog entryDialog = builder.create();
        entryDialog.show();
        resetDialogButtonColors(entryDialog);

        int accent = getSharedPreferences().getInt(KEY_ACCENT, ACCENT_SYSTEM);
        if (accent == ACCENT_CUSTOM) {
            int color = getDialogAccentColor();
            android.content.res.ColorStateList tint = android.content.res.ColorStateList.valueOf(color);
            cbAutoKill.setButtonTintList(tint);
            cbBg.setButtonTintList(tint);
            cbSleep.setButtonTintList(tint);
            cbSetBucket.setButtonTintList(tint);
            for (int i = 0; i < rgAction.getChildCount(); i++) {
                android.view.View child = rgAction.getChildAt(i);
                if (child instanceof android.widget.RadioButton)
                    ((android.widget.RadioButton) child).setButtonTintList(tint);
            }
            btnFrom.setTextColor(color);
            btnTo.setTextColor(color);
            btnComponent.setTextColor(color);
        }

        entryDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int flags = 0;
            if (cbAutoKill.isChecked()) flags |= RestrictionsScheduler.PROTECT_AUTO_KILL;
            if (cbBg.isChecked())       flags |= RestrictionsScheduler.PROTECT_BG_RESTRICTIONS;
            if (cbSleep.isChecked())    flags |= RestrictionsScheduler.PROTECT_SLEEP_MODE;

            if (flags == 0) {
                Toast.makeText(this, getString(R.string.scheduler_error_no_flags), Toast.LENGTH_SHORT).show();
                return;
            }
            if (startHour[0] == endHour[0] && startMinute[0] == endMinute[0]) {
                Toast.makeText(this, getString(R.string.scheduler_error_same_time), Toast.LENGTH_SHORT).show();
                return;
            }

            RestrictionsScheduler.ScheduleEntry e = (src != null) ? cloneScheduleEntry(src)
                                                                   : new RestrictionsScheduler.ScheduleEntry();
            e.packageName      = item.packageName;
            e.startHour        = startHour[0];
            e.startMinute      = startMinute[0];
            e.endHour          = endHour[0];
            e.endMinute        = endMinute[0];
            e.protectFlags     = flags;
            e.onActivateAction = onActivateAction[0];
            e.componentName    = (onActivateAction[0] != RestrictionsScheduler.ON_ACTIVATE_NOTHING)
                    ? selectedComponent[0] : null;
            e.setBucketActive  = cbSetBucket.isChecked();
            e.enabled          = true;

            boolean ok = (src != null) ? getScheduler().updateSchedule(e) : getScheduler().addSchedule(e);
            if (!ok) {
                Toast.makeText(this,
                        getString(R.string.scheduler_error_limit, RestrictionsScheduler.MAX_SCHEDULES),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            entryDialog.dismiss();
            if (parentDialog.isShowing()) parentDialog.dismiss();
            showRestrictionsSchedulerDialog();
        });

        if (src != null) {
            entryDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                getScheduler().removeSchedule(src.id);
                entryDialog.dismiss();
                if (parentDialog.isShowing()) parentDialog.dismiss();
                showRestrictionsSchedulerDialog();
            });
        }
    }

    private void showComponentPickerDialog(String packageName,
                                           String[] selectedComponent,
                                           int[] selectedCompType,
                                           int[] onActivateAction,
                                           TextView btnComponent) {
        getExecutor().execute(() -> {
            List<String[]> items = new ArrayList<>();
            try {
                PackageInfo pi = getPackageManager().getPackageInfo(packageName,
                        PackageManager.GET_ACTIVITIES |
                        PackageManager.GET_SERVICES   |
                        PackageManager.GET_RECEIVERS);

                if (pi.activities != null) {
                    for (ActivityInfo ai : pi.activities) {
                        if (ai.exported || ai.name.equals(
                                getPackageManager().getLaunchIntentForPackage(packageName) != null
                                ? "" : ai.name)) {
                            items.add(new String[]{
                                    "[Activity] " + shortComponentName(ai.name),
                                    packageName + "/" + ai.name,
                                    String.valueOf(RestrictionsScheduler.ON_ACTIVATE_ACTIVITY)
                            });
                        }
                    }
                }
                if (pi.services != null) {
                    for (ServiceInfo si : pi.services) {
                        if (si.exported) {
                            items.add(new String[]{
                                    "[Service] " + shortComponentName(si.name),
                                    packageName + "/" + si.name,
                                    String.valueOf(RestrictionsScheduler.ON_ACTIVATE_SERVICE)
                            });
                        }
                    }
                }
                if (pi.receivers != null) {
                    for (ActivityInfo ri : pi.receivers) {
                        if (ri.exported) {
                            items.add(new String[]{
                                    "[Receiver] " + shortComponentName(ri.name),
                                    packageName + "/" + ri.name,
                                    String.valueOf(RestrictionsScheduler.ON_ACTIVATE_RECEIVER)
                            });
                        }
                    }
                }
            } catch (PackageManager.NameNotFoundException e) {
                AppDebugManager.e(Category.SETTINGS_PAGE, FILE_NAME + ": showComponentPickerDialog: package not found: " + packageName, e);
            }

            getHandler().post(() -> {
                if (items.isEmpty()) {
                    AppDebugManager.w(Category.SETTINGS_PAGE, FILE_NAME + ": showComponentPickerDialog: no exported components found for " + packageName);
                    Toast.makeText(this, getString(R.string.scheduler_component_none_found),
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                String[] labels = items.stream()
                        .map(i -> i[0])
                        .toArray(String[]::new);

                resetDialogButtonColors(new MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.scheduler_component_picker_title))
                        .setItems(labels, (d, which) -> {
                            String[] chosen = items.get(which);
                            selectedComponent[0] = chosen[1];
                            selectedCompType[0]  = Integer.parseInt(chosen[2]);
                            onActivateAction[0]  = selectedCompType[0];
                            btnComponent.setText(shortComponentName(chosen[1]));
                        })
                        .setNegativeButton(getString(R.string.dialog_close), null)
                        .show());
            });
        });
    }

    protected static final class SchedulerAppItem {
        final String packageName;
        final String appName;
        final android.graphics.drawable.Drawable icon;
        final RestrictionsScheduler.ScheduleEntry entry;

        SchedulerAppItem(String packageName, String appName,
                         android.graphics.drawable.Drawable icon,
                         RestrictionsScheduler.ScheduleEntry entry) {
            this.packageName = packageName;
            this.appName     = appName;
            this.icon        = icon;
            this.entry       = entry;
        }
    }

    private String shortComponentName(String componentName) {
        if (componentName == null) return "";
        int dot = componentName.lastIndexOf('.');
        return dot >= 0 ? componentName.substring(dot + 1) : componentName;
    }

    protected RestrictionsScheduler.ScheduleEntry findScheduleForPackage(String pkg) {
        for (RestrictionsScheduler.ScheduleEntry e : getScheduler().getSchedules()) {
            if (pkg.equals(e.packageName)) return e;
        }
        return null;
    }

    private RestrictionsScheduler.ScheduleEntry cloneScheduleEntry(
            RestrictionsScheduler.ScheduleEntry src) {
        RestrictionsScheduler.ScheduleEntry dst = new RestrictionsScheduler.ScheduleEntry();
        dst.id               = src.id;
        dst.packageName      = src.packageName;
        dst.startHour        = src.startHour;
        dst.startMinute      = src.startMinute;
        dst.endHour          = src.endHour;
        dst.endMinute        = src.endMinute;
        dst.protectFlags     = src.protectFlags;
        dst.onActivateAction = src.onActivateAction;
        dst.setBucketActive  = src.setBucketActive;
        dst.enabled          = src.enabled;
        return dst;
    }

    protected String formatTime(int hour, int minute, boolean use24h) {
        if (use24h) {
            return String.format(Locale.US, "%02d:%02d", hour, minute);
        }
        int h12 = hour % 12;
        if (h12 == 0) h12 = 12;
        return String.format(Locale.US, "%d:%02d %s", h12, minute, hour < 12 ? "AM" : "PM");
    }

    protected String formatScheduleTime(RestrictionsScheduler.ScheduleEntry entry, boolean use24h) {
        return formatTime(entry.startHour, entry.startMinute, use24h)
                + " – "
                + formatTime(entry.endHour, entry.endMinute, use24h);
    }

    protected void showBackupRestoreDialog() {
        String[] options = {
                getString(R.string.settings_backup_option_save),
                getString(R.string.settings_backup_option_restore)
        };
        resetDialogButtonColors(new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.settings_backup_restore_title))
                .setItems(options, (dialog, which) -> {
                    if (which == 0) getCreateBackupLauncher().launch("appzuku_backup.json");
                    else getRestoreBackupLauncher().launch(new String[]{"application/json"});
                })
                .show());
    }

    protected abstract androidx.activity.result.ActivityResultLauncher<String> getCreateBackupLauncher();
    protected abstract androidx.activity.result.ActivityResultLauncher<String[]> getRestoreBackupLauncher();

    protected void exportBackup(Uri uri) {
        getExecutor().execute(() -> {
            AppDebugManager.d(Category.SETTINGS_PAGE, FILE_NAME + ": exportBackup started");
            String json = getBackupManager().createBackupJson();
            if (json == null) {
                AppDebugManager.e(Category.SETTINGS_PAGE, FILE_NAME + ": exportBackup: createBackupJson returned null");
                getHandler().post(() -> Toast.makeText(this, getString(R.string.settings_backup_create_failed), Toast.LENGTH_SHORT).show());
                return;
            }
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os != null) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                    AppDebugManager.d(Category.SETTINGS_PAGE, FILE_NAME + ": exportBackup success");
                    getHandler().post(() -> Toast.makeText(this, getString(R.string.settings_backup_success), Toast.LENGTH_SHORT).show());
                } else {
                    AppDebugManager.e(Category.SETTINGS_PAGE, FILE_NAME + ": exportBackup: OutputStream is null");
                    getHandler().post(() -> Toast.makeText(this, getString(R.string.settings_backup_write_failed), Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                AppDebugManager.e(Category.SETTINGS_PAGE, FILE_NAME + ": exportBackup failed", e);
                getHandler().post(() -> Toast.makeText(this, getString(R.string.settings_backup_export_failed, e.getMessage()), Toast.LENGTH_SHORT).show());
            }
        });
    }

    protected void importBackup(Uri uri) {
        getExecutor().execute(() -> {
            AppDebugManager.d(Category.SETTINGS_PAGE, FILE_NAME + ": importBackup started");
            try (InputStream is = getContentResolver().openInputStream(uri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);

                boolean success = getBackupManager().restoreBackupJson(sb.toString());
                getHandler().post(() -> {
                    if (success) {
                        AppDebugManager.d(Category.SETTINGS_PAGE, FILE_NAME + ": importBackup restore success");
                        Set<String> restoredRestrictedApps = new java.util.HashSet<>(
                                getSharedPreferences().getStringSet(KEY_AUTOSTART_DISABLED_APPS, new java.util.HashSet<>()));
                        Runnable finishRestore = () -> {
                            applyAutomationStateFromPreferences();
                            loadSettings();
                            updateKillModeVisibility();
                            if (getAppManager().supportsBackgroundRestriction()
                                    && !restoredRestrictedApps.isEmpty()
                                    && !getAppManager().canApplyBackgroundRestrictionNow()) {
                                Toast.makeText(this, getString(R.string.settings_restore_need_permission), Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(this, getString(R.string.settings_restore_success), Toast.LENGTH_SHORT).show();
                            }
                        };
                        if (getAppManager().canApplyBackgroundRestrictionNow()) {
                            getAppManager().applyBackgroundRestriction(restoredRestrictedApps, finishRestore);
                        } else {
                            finishRestore.run();
                        }
                    } else {
                        AppDebugManager.w(Category.SETTINGS_PAGE, FILE_NAME + ": importBackup: restoreBackupJson returned false");
                        Toast.makeText(this, getString(R.string.settings_restore_failed), Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                AppDebugManager.e(Category.SETTINGS_PAGE, FILE_NAME + ": importBackup failed", e);
                getHandler().post(() -> Toast.makeText(this, getString(R.string.settings_restore_import_failed, e.getMessage()), Toast.LENGTH_SHORT).show());
            }
        });
    }

    protected void showSpecialThanksDialog() {
        String[] names = getResources().getStringArray(R.array.special_thanks_list);
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.special_thanks_desc)).append("\n\n");
        for (String name : names) {
            sb.append("• ").append(name).append("\n");
        }

        TextView textView = new TextView(this);
        textView.setText(sb.toString().trim());
        textView.setTextColor(getColor(R.color.text_primary));
        textView.setTextSize(16f);
        int paddingH = (int) (24 * getResources().getDisplayMetrics().density);
        int paddingV = (int) (16 * getResources().getDisplayMetrics().density);
        textView.setPadding(paddingH, paddingV, paddingH, paddingV);

        int maxHeightPx = (int) (400 * getResources().getDisplayMetrics().density);
        ScrollView scrollView = new ScrollView(this) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int heightSpec = MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST);
                super.onMeasure(widthMeasureSpec, heightSpec);
            }
        };
        scrollView.addView(textView);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.special_thanks_title))
                .setView(scrollView)
                .setPositiveButton(getString(R.string.dialog_close), (d, w) -> d.dismiss())
                .create();
        dialog.show();
        resetDialogButtonColors(dialog);
    }

    protected void showEasterEggDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(android.view.Gravity.CENTER);
        int pad = (int) (getResources().getDisplayMetrics().density * 24);
        layout.setPadding(pad, pad, pad, pad);

        android.widget.ImageView imageView = new android.widget.ImageView(this);
        imageView.setImageResource(R.drawable.settings_page_info);
        int size = (int) (getResources().getDisplayMetrics().density * 220);
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(size, size);
        imageView.setLayoutParams(imgParams);
        imageView.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        layout.addView(imageView);

        TextView textView = new TextView(this);
        textView.setText(getString(R.string.easter_egg_hunter));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        textView.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tvParams.topMargin = (int) (getResources().getDisplayMetrics().density * 16);
        textView.setLayoutParams(tvParams);
        layout.addView(textView);

        resetDialogButtonColors(new MaterialAlertDialogBuilder(this)
                .setView(layout)
                .setPositiveButton(getString(R.string.dialog_ok), null)
                .show());
    }

    protected void showPresetPickerDialog() {
        PresetManager presetManager = new PresetManager(this);
        int activePreset = presetManager.getActivePresetNumber();

        View view = getLayoutInflater().inflate(R.layout.dialog_single_choice, null);
        TextView titleView = view.findViewById(R.id.single_choice_title);
        RadioGroup group = view.findViewById(R.id.single_choice_group);
        titleView.setText(getString(R.string.settings_presets_title));

        int accent = getSharedPreferences().getInt(KEY_ACCENT, ACCENT_SYSTEM);
        android.content.res.ColorStateList tint = (accent == ACCENT_CUSTOM)
                ? android.content.res.ColorStateList.valueOf(getDialogAccentColor()) : null;

        int dp8 = (int) (getResources().getDisplayMetrics().density * 8);
        int dp12 = (int) (getResources().getDisplayMetrics().density * 12);

        for (int presetNumber : new int[]{ PresetModel.PRESET_1, PresetModel.PRESET_2 }) {
            String name = presetManager.presetExists(presetNumber)
                    ? presetManager.getPresetName(presetNumber)
                    : getString(R.string.preset_title, presetNumber);

            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

            android.widget.RadioButton rb = new android.widget.RadioButton(this);
            rb.setText(name);
            rb.setId(1000 + presetNumber);
            rb.setPadding(dp12, dp12, dp12, dp12);
            rb.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            if (tint != null) rb.setButtonTintList(tint);
            row.addView(rb);

            if (activePreset == presetNumber) {
                TextView badge = new TextView(this);
                badge.setText(getString(R.string.preset_badge_active));
                badge.setTextSize(11);
                badge.setTextColor(Color.WHITE);
                badge.setBackground(buildBadgeBackground());
                badge.setPadding(dp8, dp8 / 2, dp8, dp8 / 2);
                android.widget.LinearLayout.LayoutParams badgeLp =
                        new android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                badgeLp.setMarginEnd(dp12);
                badge.setLayoutParams(badgeLp);
                row.addView(badge);
            }

            group.addView(row);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(view)
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .create();

        for (int i = 0; i < group.getChildCount(); i++) {
            android.view.View row = group.getChildAt(i);
            android.widget.RadioButton rb = findRadioButton(row);
            if (rb == null) continue;
            int presetNumber = rb.getId() - 1000;

            android.view.View.OnClickListener openPreset = v -> {
                dialog.dismiss();
                Intent intent = new Intent(this, PresetSettingsActivity.class);
                intent.putExtra(PresetSettingsActivity.EXTRA_PRESET_NUMBER, presetNumber);
                startActivity(intent);
            };
            row.setOnClickListener(openPreset);
            rb.setOnClickListener(openPreset);
        }

        dialog.show();
        resetDialogButtonColors(dialog);
    }

    private android.widget.RadioButton findRadioButton(android.view.View view) {
        if (view instanceof android.widget.RadioButton) return (android.widget.RadioButton) view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                android.widget.RadioButton found = findRadioButton(vg.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private GradientDrawable buildBadgeBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(32f);
        int accent = getSharedPreferences().getInt(KEY_ACCENT, ACCENT_SYSTEM);
        if (accent == ACCENT_CUSTOM) {
            bg.setColor(getSharedPreferences().getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR));
        } else {
            bg.setColor(0xFF388E3C);
        }
        return bg;
    }

    protected void showAdditionalScenariosDialog() {
        int dp4 = (int) (getResources().getDisplayMetrics().density * 4);
        int dp8 = (int) (getResources().getDisplayMetrics().density * 8);
        int dp16 = (int) (getResources().getDisplayMetrics().density * 16);
        int dp24 = (int) (getResources().getDisplayMetrics().density * 24);

        LinearLayout.LayoutParams cbParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cbParams.setMarginStart(dp16);

        int accent = getSharedPreferences().getInt(KEY_ACCENT, ACCENT_SYSTEM);
        boolean isCustomAccent = accent == ACCENT_CUSTOM;
        int customColor = getSharedPreferences().getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR);
        android.content.res.ColorStateList checkboxTint = isCustomAccent
                ? android.content.res.ColorStateList.valueOf(customColor) : null;
        int onColor = getSharedPreferences().getInt(KEY_ACCENT_ON_COLOR, ACCENT_ON_WHITE);
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
        headerHw.setTypeface(null, Typeface.BOLD);
        headerHw.setTextColor(colorAccent);
        headerHw.setPadding(dp24, dp8, dp24, dp4);
        root.addView(headerHw);

        CheckBox cbHeadset = new CheckBox(this);
        cbHeadset.setText(getString(R.string.scenarios_hw_headset));
        cbHeadset.setChecked(getAdditionalScenariosManager().isHeadsetTriggerEnabled());
        cbHeadset.setPadding(dp24, dp8, dp24, dp8);
        if (checkboxTint != null) cbHeadset.setButtonTintList(checkboxTint);
        root.addView(cbHeadset);

        CheckBox cbUsb = new CheckBox(this);
        cbUsb.setText(getString(R.string.scenarios_hw_usb));
        cbUsb.setChecked(getAdditionalScenariosManager().isUsbTriggerEnabled());
        cbUsb.setPadding(dp24, dp8, dp24, dp8);
        if (checkboxTint != null) cbUsb.setButtonTintList(checkboxTint);
        root.addView(cbUsb);

        CheckBox cbCharger = new CheckBox(this);
        cbCharger.setText(getString(R.string.scenarios_hw_charger));
        cbCharger.setChecked(getAdditionalScenariosManager().isChargerTriggerEnabled());
        cbCharger.setPadding(dp24, dp8, dp24, dp8);
        if (checkboxTint != null) cbCharger.setButtonTintList(checkboxTint);
        root.addView(cbCharger);

        CheckBox cbWifi = new CheckBox(this);
        cbWifi.setText(getString(R.string.scenarios_hw_wifi));
        cbWifi.setChecked(getAdditionalScenariosManager().isWifiTriggerEnabled());
        cbWifi.setPadding(dp24, dp8, dp24, dp8);
        if (checkboxTint != null) cbWifi.setButtonTintList(checkboxTint);
        root.addView(cbWifi);

        CheckBox cbBluetooth = new CheckBox(this);
        cbBluetooth.setText(getString(R.string.scenarios_hw_bluetooth));
        cbBluetooth.setChecked(getAdditionalScenariosManager().isBluetoothTriggerEnabled());
        cbBluetooth.setPadding(dp24, dp8, dp24, dp8);
        if (checkboxTint != null) cbBluetooth.setButtonTintList(checkboxTint);
        root.addView(cbBluetooth);

        CheckBox cbGps = new CheckBox(this);
        cbGps.setText(getString(R.string.scenarios_hw_gps));
        cbGps.setChecked(getAdditionalScenariosManager().isGpsTriggerEnabled());
        cbGps.setPadding(dp24, dp8, dp24, dp8);
        if (checkboxTint != null) cbGps.setButtonTintList(checkboxTint);
        root.addView(cbGps);

        CheckBox cbHotspot = new CheckBox(this);
        cbHotspot.setText(getString(R.string.scenarios_hw_hotspot));
        cbHotspot.setChecked(getAdditionalScenariosManager().isHotspotTriggerEnabled());
        cbHotspot.setPadding(dp24, dp8, dp24, dp8);
        if (checkboxTint != null) cbHotspot.setButtonTintList(checkboxTint);
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
        headerLaunch.setTypeface(null, Typeface.BOLD);
        headerLaunch.setTextColor(colorAccent);
        headerLaunch.setPadding(dp24, dp4, dp24, dp4);
        root.addView(headerLaunch);

        CheckBox cbAppLaunch = new CheckBox(this);
        cbAppLaunch.setText(getString(R.string.scenarios_app_launch_enable));
        cbAppLaunch.setChecked(getAdditionalScenariosManager().isAppLaunchTriggerEnabled());
        cbAppLaunch.setPadding(dp24, dp8, dp24, dp8);
        if (checkboxTint != null) cbAppLaunch.setButtonTintList(checkboxTint);
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
        updateDialogTargetAppsList(tvTargetAppsList);
        layoutTargetApps.addView(tvTargetAppsList);

        CheckBox cbClearCache = new CheckBox(this);
        cbClearCache.setText(getString(R.string.scenarios_app_launch_clear_cache));
        cbClearCache.setChecked(getAdditionalScenariosManager().isAppLaunchClearCacheEnabled());
        cbClearCache.setPadding(dp4, dp8, dp24, dp8);
        cbClearCache.setLayoutParams(cbParams);
        if (checkboxTint != null) cbClearCache.setButtonTintList(checkboxTint);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            cbClearCache.setEnabled(false);
            cbClearCache.setAlpha(0.4f);
        }
        layoutTargetApps.addView(cbClearCache);

        root.addView(layoutTargetApps);

        cbAppLaunch.setOnCheckedChangeListener((btn, isChecked) ->
                layoutTargetApps.setVisibility(isChecked ? View.VISIBLE : View.GONE));

        layoutTargetApps.setOnClickListener(v -> showTargetAppsPickerDialog(() ->
                updateDialogTargetAppsList(tvTargetAppsList)));

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
            getAdditionalScenariosManager().setHeadsetTriggerEnabled(cbHeadset.isChecked());
            getAdditionalScenariosManager().setUsbTriggerEnabled(cbUsb.isChecked());
            getAdditionalScenariosManager().setChargerTriggerEnabled(cbCharger.isChecked());
            getAdditionalScenariosManager().setWifiTriggerEnabled(cbWifi.isChecked());
            getAdditionalScenariosManager().setBluetoothTriggerEnabled(cbBluetooth.isChecked());
            getAdditionalScenariosManager().setGpsTriggerEnabled(cbGps.isChecked());
            getAdditionalScenariosManager().setHotspotTriggerEnabled(cbHotspot.isChecked());
            getAdditionalScenariosManager().updateHardwareReceiverState();

            boolean appLaunchWasOff = !getAdditionalScenariosManager().isAppLaunchTriggerEnabled();
            getAdditionalScenariosManager().setAppLaunchTriggerEnabled(cbAppLaunch.isChecked());
            getAdditionalScenariosManager().setAppLaunchClearCacheEnabled(cbClearCache.isChecked());

            if (cbAppLaunch.isChecked() && appLaunchWasOff && !isAccessibilityServiceEnabled()) {
                showAccessibilityServiceRequiredDialog();
            }

            updateAdditionalScenariosSummary();
            dialog.dismiss();
        });
    }

    protected void updateDialogTargetAppsList(TextView tvTargetAppsList) {
        Set<String> packages = getAdditionalScenariosManager().getAppLaunchTriggerPackages();
        if (packages.isEmpty()) {
            tvTargetAppsList.setText(getString(R.string.scenarios_app_launch_empty));
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
        tvTargetAppsList.setText(android.text.TextUtils.join(", ", names));
    }

    protected void showTargetAppsPickerDialog(Runnable onSaved) {
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
        int pickerAccent = getSharedPreferences().getInt(KEY_ACCENT, ACCENT_SYSTEM);
        int pickerButtonColor = (pickerAccent == ACCENT_CUSTOM)
                ? ((getSharedPreferences().getInt(KEY_ACCENT_ON_COLOR, ACCENT_ON_WHITE) == ACCENT_ON_BLACK) ? Color.BLACK : Color.WHITE)
                : ContextCompat.getColor(this, R.color.dialog_button_text);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(pickerButtonColor);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(pickerButtonColor);

        getAppManager().loadAllApps(allApps -> {
            Set<String> currentTargets = getAdditionalScenariosManager().getAppLaunchTriggerPackages();
            FilterAppsAdapter filterAdapter = new FilterAppsAdapter(this, allApps, currentTargets);
            if (getSharedPreferences().getInt(KEY_ACCENT, ACCENT_SYSTEM) == ACCENT_CUSTOM)
                filterAdapter.setAccentColor(getSharedPreferences().getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR));
            listView.setAdapter(filterAdapter);
            listView.setOnItemClickListener(null);

            progressBar.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
            searchBox.setVisibility(View.VISIBLE);

            searchBox.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterAdapter.getFilter().filter(s); }
                @Override public void afterTextChanged(Editable s) {}
            });

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                getAdditionalScenariosManager().saveAppLaunchTriggerPackages(filterAdapter.getSelectedPackages());
                if (onSaved != null) onSaved.run();
                dialog.dismiss();
            });
        });
    }

    protected boolean isAccessibilityServiceEnabled() {
        String expectedService = getPackageName() + "/" + AppLaunchAccessibilityService.class.getName();
        String enabledServices = android.provider.Settings.Secure.getString(
                getContentResolver(), android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabledServices != null && enabledServices.contains(expectedService);
    }

    protected void showAccessibilityServiceRequiredDialog() {
        resetDialogButtonColors(new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.scenarios_accessibility_required_title))
                .setMessage(getString(R.string.scenarios_accessibility_required_message))
                .setPositiveButton(getString(R.string.scenarios_accessibility_open_settings), (dialog, which) -> {
                    dialog.dismiss();
                    startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
                })
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .show());
    }

    protected void showDebugMenuDialog() {
        AppDebugManager.Category[] categories = AppDebugManager.Category.values();
        int dp8  = (int) (getResources().getDisplayMetrics().density * 8);
        int dp16 = (int) (getResources().getDisplayMetrics().density * 16);
        int dp24 = (int) (getResources().getDisplayMetrics().density * 24);

        int accent = getSharedPreferences().getInt(KEY_ACCENT, ACCENT_SYSTEM);
        boolean isCustomAccent = accent == ACCENT_CUSTOM;
        int customColor = getSharedPreferences().getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR);
        android.content.res.ColorStateList switchTint = isCustomAccent
                ? android.content.res.ColorStateList.valueOf(customColor) : null;
        int onColor = getSharedPreferences().getInt(KEY_ACCENT_ON_COLOR, ACCENT_ON_WHITE);
        int buttonTextColor = isCustomAccent
                ? ((onColor == ACCENT_ON_BLACK) ? Color.BLACK : Color.WHITE)
                : ContextCompat.getColor(this, R.color.dialog_button_text);

        String[] categoryLabels = {
            getString(R.string.appdebug_main_page),
            getString(R.string.appdebug_settings_page),
            getString(R.string.appdebug_statistics_page),
            getString(R.string.appdebug_core),
            getString(R.string.appdebug_foreground_service),
            getString(R.string.appdebug_triggers),
            getString(R.string.appdebug_advanced_conditions),
            getString(R.string.appdebug_scan),
            getString(R.string.appdebug_auto_kill_base),
            getString(R.string.appdebug_auto_kill_presets),
            getString(R.string.appdebug_shortcuts_widgets),
            getString(R.string.appdebug_background_restrictions),
            getString(R.string.appdebug_restrictions_scheduler),
            getString(R.string.appdebug_sleep_mode),
            getString(R.string.appdebug_backup_restore),
            getString(R.string.appdebug_utils)
        };

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp8, 0, dp8);

        MaterialSwitch[] switches = new MaterialSwitch[categories.length];
        for (int i = 0; i < categories.length; i++) {
            AppDebugManager.Category cat = categories[i];

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dp24, dp8, dp16, dp8);

            TextView label = new TextView(this);
            label.setText(categoryLabels[i]);
            label.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(label);

            MaterialSwitch sw = new MaterialSwitch(this);
            sw.setChecked(AppDebugManager.isCategoryEnabled(cat));
            if (switchTint != null) {
                int trackColor = darkenColor(customColor, 0.6f);
                android.content.res.ColorStateList thumbTint = new android.content.res.ColorStateList(
                    new int[][] { new int[] { android.R.attr.state_checked }, new int[] {} },
                    new int[] { customColor, 0xFFAAAAAA });
                android.content.res.ColorStateList trackTintList = new android.content.res.ColorStateList(
                    new int[][] { new int[] { android.R.attr.state_checked }, new int[] {} },
                    new int[] { trackColor, 0xFF555555 });
                sw.setThumbTintList(thumbTint);
                sw.setTrackTintList(trackTintList);
            }
            switches[i] = sw;
            row.addView(sw);
            root.addView(row);
        }

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(root);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Debug menu")
                .setView(scrollView)
                .create();
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK", (d, w) -> {
            for (int i = 0; i < categories.length; i++) {
                AppDebugManager.setCategory(categories[i], switches[i].isChecked());
            }
        });
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.dialog_cancel), (d, w) -> d.dismiss());
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(buttonTextColor);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(buttonTextColor);
    }

    protected void showSingleChoiceDialog(String title, String[] options, int selected,
                                          java.util.function.IntConsumer onPick) {
        android.view.View view = getLayoutInflater().inflate(R.layout.dialog_single_choice, null);
        android.widget.TextView titleView = view.findViewById(R.id.single_choice_title);
        android.widget.RadioGroup group = view.findViewById(R.id.single_choice_group);

        titleView.setText(title);

        int accent = getSharedPreferences().getInt(KEY_ACCENT, ACCENT_SYSTEM);
        android.content.res.ColorStateList tint = (accent == ACCENT_CUSTOM)
                ? android.content.res.ColorStateList.valueOf(getDialogAccentColor())
                : null;

        int dp12 = (int) (getResources().getDisplayMetrics().density * 12);
        for (int i = 0; i < options.length; i++) {
            android.widget.RadioButton rb = new android.widget.RadioButton(this);
            rb.setText(options[i]);
            rb.setId(1000 + i);
            rb.setPadding(dp12, dp12, dp12, dp12);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            rb.setLayoutParams(lp);
            if (tint != null) rb.setButtonTintList(tint);
            group.addView(rb);
        }
        group.check(1000 + selected);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(view)
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .create();

        final boolean[] userInteracted = {false};
        group.setOnCheckedChangeListener((g, checkedId) -> {
            if (!userInteracted[0] || checkedId == -1) return;
            onPick.accept(checkedId - 1000);
            dialog.dismiss();
        });
        view.post(() -> userInteracted[0] = true);

        dialog.show();
        resetDialogButtonColors(dialog);
    }

    protected void resetDialogButtonColors(AlertDialog dialog) {
        int color = ContextCompat.getColor(this, R.color.dialog_button_text);
        android.widget.Button btnPos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        android.widget.Button btnNeg = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        android.widget.Button btnNeu = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (btnPos != null) btnPos.setTextColor(color);
        if (btnNeg != null) btnNeg.setTextColor(color);
        if (btnNeu != null) btnNeu.setTextColor(color);
    }

    protected int getDialogAccentColor() {
        int accent = getSharedPreferences().getInt(KEY_ACCENT, ACCENT_SYSTEM);
        if (accent == ACCENT_CUSTOM)
            return getSharedPreferences().getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR);
        return ContextCompat.getColor(this, R.color.dialog_button_text);
    }

    protected void setupFilterListeners(View dialogView, FilterAppsAdapter adapter) {
        TextView btnSort = dialogView.findViewById(R.id.filter_btn_sort);
        TextView btnClear = dialogView.findViewById(R.id.filter_btn_clear);
        TextView btnSelectAll = dialogView.findViewById(R.id.filter_btn_select_all);

        if (getSharedPreferences().getInt(KEY_ACCENT, ACCENT_SYSTEM) == ACCENT_CUSTOM) {
            int color = getDialogAccentColor();
            btnSort.setTextColor(color);
            btnClear.setTextColor(color);
            btnSelectAll.setTextColor(color);
        }

        final boolean[] showSystem  = {false};
        final boolean[] showUser    = {true};
        final boolean[] showRunning = {false};
        final boolean[] showSelected    = {false};
        final boolean[] showNotSelected = {false};
        final Set<BackgroundAppManager.RestrictionType>[] showRestrictionTypes = new Set[]{new HashSet<>()};

        updateSortButtonText(btnSort, false);

        btnSort.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, btnSort);
            popup.getMenu().add(0, 0, 0, getString(R.string.filter_system))
                    .setCheckable(true).setChecked(showSystem[0]);
            popup.getMenu().add(0, 1, 1, getString(R.string.filter_user))
                    .setCheckable(true).setChecked(showUser[0]);
            popup.getMenu().add(0, 2, 2, getString(R.string.filter_running))
                    .setCheckable(true).setChecked(showRunning[0]);
            // Selection filters for all dialogs
            popup.getMenu().add(0, 3, 3, getString(R.string.filter_selected))
                    .setCheckable(true).setChecked(showSelected[0]);
            popup.getMenu().add(0, 4, 4, getString(R.string.filter_not_selected))
                    .setCheckable(true).setChecked(showNotSelected[0]);
            // Restriction type filters (Background Restrictions only)
            if (adapter.isRestrictionMode()) {
                popup.getMenu().add(0, 5, 5, getString(R.string.filter_restriction_soft_option))
                        .setCheckable(true)
                        .setChecked(showRestrictionTypes[0].contains(BackgroundAppManager.RestrictionType.SOFT));
                popup.getMenu().add(0, 6, 6, getString(R.string.filter_restriction_medium_option))
                        .setCheckable(true)
                        .setChecked(showRestrictionTypes[0].contains(BackgroundAppManager.RestrictionType.MEDIUM));
                popup.getMenu().add(0, 7, 7, getString(R.string.filter_restriction_hard_option))
                        .setCheckable(true)
                        .setChecked(showRestrictionTypes[0].contains(BackgroundAppManager.RestrictionType.HARD));
                popup.getMenu().add(0, 8, 8, getString(R.string.filter_restriction_manual_option))
                        .setCheckable(true)
                        .setChecked(showRestrictionTypes[0].contains(BackgroundAppManager.RestrictionType.MANUAL));
            }
            popup.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 0: showSystem[0]  = !showSystem[0];  item.setChecked(showSystem[0]);  break;
                    case 1: showUser[0]    = !showUser[0];    item.setChecked(showUser[0]);    break;
                    case 2: showRunning[0] = !showRunning[0]; item.setChecked(showRunning[0]); break;
                    case 3:
                        showSelected[0] = !showSelected[0];
                        item.setChecked(showSelected[0]);
                        adapter.setFilterSelection(showSelected[0], showNotSelected[0]);
                        break;
                    case 4:
                        showNotSelected[0] = !showNotSelected[0];
                        item.setChecked(showNotSelected[0]);
                        adapter.setFilterSelection(showSelected[0], showNotSelected[0]);
                        break;
                    case 5: case 6: case 7: case 8: {
                        BackgroundAppManager.RestrictionType rt;
                        switch (item.getItemId()) {
                            case 5:  rt = BackgroundAppManager.RestrictionType.SOFT; break;
                            case 6:  rt = BackgroundAppManager.RestrictionType.MEDIUM; break;
                            case 7:  rt = BackgroundAppManager.RestrictionType.HARD; break;
                            default: rt = BackgroundAppManager.RestrictionType.MANUAL; break;
                        }
                        if (showRestrictionTypes[0].contains(rt)) {
                            showRestrictionTypes[0].remove(rt);
                            item.setChecked(false);
                        } else {
                            showRestrictionTypes[0].add(rt);
                            item.setChecked(true);
                        }
                        adapter.setRestrictionTypeFilter(showRestrictionTypes[0]);
                        break;
                    }
                }
                adapter.setFilters(showSystem[0], showUser[0], showRunning[0]);
                return true;
            });
            popup.setOnDismissListener(d -> updateSortButtonText(btnSort, false));
            updateSortButtonText(btnSort, true);
            popup.show();
        });

        btnClear.setOnClickListener(v -> adapter.clearSelection());

        btnSelectAll.setOnClickListener(v -> adapter.selectAllFiltered());

        TextView btnSetType = dialogView.findViewById(R.id.filter_btn_set_type);
        if (adapter.isRestrictionMode()) {
            btnSetType.setVisibility(View.VISIBLE);
            if (getSharedPreferences().getInt(KEY_ACCENT, ACCENT_SYSTEM) == ACCENT_CUSTOM) {
                btnSetType.setTextColor(getDialogAccentColor());
            }
            btnSetType.setOnClickListener(v -> {
                if (adapter.getSelectedPackages().isEmpty()) {
                    Toast.makeText(this, R.string.filter_set_type_no_selection, Toast.LENGTH_SHORT).show();
                    return;
                }
                showBatchRestrictionTypeDialog(adapter, btnSetType);
            });
        }
    }

    private void showBatchRestrictionTypeDialog(FilterAppsAdapter adapter, TextView anchor) {
        int paddingH = (int) (getResources().getDisplayMetrics().density * 24);

        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.setPadding(0, 16, 0, 8);

        android.widget.RadioGroup radioGroup = new android.widget.RadioGroup(this);
        radioGroup.setOrientation(android.widget.RadioGroup.VERTICAL);

        android.widget.RadioButton softBtn = new android.widget.RadioButton(this);
        softBtn.setId(View.generateViewId());
        softBtn.setText(getString(R.string.filter_restriction_soft_option));
        softBtn.setPadding(paddingH, 24, paddingH, 24);
        softBtn.setChecked(true);
        radioGroup.addView(softBtn);

        radioGroup.addView(makeDivider(paddingH));

        android.widget.RadioButton mediumBtn = new android.widget.RadioButton(this);
        mediumBtn.setId(View.generateViewId());
        mediumBtn.setText(getString(R.string.filter_restriction_medium_option));
        mediumBtn.setPadding(paddingH, 24, paddingH, 24);
        radioGroup.addView(mediumBtn);

        radioGroup.addView(makeDivider(paddingH));

        android.widget.RadioButton hardBtn = new android.widget.RadioButton(this);
        hardBtn.setId(View.generateViewId());
        hardBtn.setText(getString(R.string.filter_restriction_hard_option));
        hardBtn.setPadding(paddingH, 24, paddingH, 24);
        radioGroup.addView(hardBtn);

        radioGroup.addView(makeDivider(paddingH));

        android.widget.RadioButton manualBtn = new android.widget.RadioButton(this);
        manualBtn.setId(View.generateViewId());
        manualBtn.setText(getString(R.string.filter_restriction_manual_option));
        manualBtn.setPadding(paddingH, 24, paddingH, 24);
        radioGroup.addView(manualBtn);

        container.addView(radioGroup);

        int accent = getSharedPreferences().getInt(KEY_ACCENT, ACCENT_SYSTEM);
        if (accent == ACCENT_CUSTOM) {
            int color = getDialogAccentColor();
            android.content.res.ColorStateList tint = android.content.res.ColorStateList.valueOf(color);
            softBtn.setButtonTintList(tint);
            mediumBtn.setButtonTintList(tint);
            hardBtn.setButtonTintList(tint);
            manualBtn.setButtonTintList(tint);
        }

        int count = adapter.getSelectedPackages().size();
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.filter_set_type_dialog_title, count))
                .setView(container)
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .setPositiveButton(getString(R.string.dialog_apply), (dialog, which) -> {
                    BackgroundAppManager.RestrictionType chosen;
                    if (hardBtn.isChecked()) {
                        chosen = BackgroundAppManager.RestrictionType.HARD;
                    } else if (mediumBtn.isChecked()) {
                        chosen = BackgroundAppManager.RestrictionType.MEDIUM;
                    } else if (manualBtn.isChecked()) {
                        chosen = BackgroundAppManager.RestrictionType.MANUAL;
                        Toast.makeText(this, R.string.filter_set_type_manual_hint, Toast.LENGTH_LONG).show();
                    } else {
                        chosen = BackgroundAppManager.RestrictionType.SOFT;
                    }
                    adapter.setRestrictionTypeForAllSelected(chosen);
                })
                .show();
    }

    protected void showInstallTemplateDialog() {
        SharedPreferences prefs = getSharedPreferences();
        int dp24 = (int) (24 * getResources().getDisplayMetrics().density);
        int dp12 = (int) (12 * getResources().getDisplayMetrics().density);
        int dp8 = (int) (8 * getResources().getDisplayMetrics().density);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp24, dp8, dp24, dp8);

        // --- Master toggle ---
        LinearLayout masterRow = new LinearLayout(this);
        masterRow.setOrientation(LinearLayout.HORIZONTAL);
        masterRow.setPadding(0, dp12, 0, dp12);
        LinearLayout masterText = new LinearLayout(this);
        masterText.setOrientation(LinearLayout.VERTICAL);
        masterText.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView masterTitle = new TextView(this);
        masterTitle.setText(R.string.template_master_title);
        masterTitle.setTextColor(getColorFromAttr(android.R.attr.textColorPrimary));
        masterTitle.setTextSize(16);
        masterText.addView(masterTitle);
        TextView masterSub = new TextView(this);
        masterSub.setText(R.string.template_master_subtitle);
        masterSub.setTextColor(getColorFromAttr(android.R.attr.textColorSecondary));
        masterSub.setTextSize(12);
        masterText.addView(masterSub);
        masterRow.addView(masterText);
        MaterialSwitch masterSwitch = new MaterialSwitch(this);
        masterSwitch.setChecked(prefs.getBoolean(KEY_TEMPLATE_ENABLED, false));
        masterRow.addView(masterSwitch);
        root.addView(masterRow);

        root.addView(makeDivider(dp24));

        // --- Sub-container (shown/hidden by master toggle) ---
        LinearLayout subContainer = new LinearLayout(this);
        subContainer.setOrientation(LinearLayout.VERTICAL);
        subContainer.setVisibility(masterSwitch.isChecked() ? View.VISIBLE : View.GONE);

        // Restriction row
        LinearLayout restrictionRow = buildTemplateOptionRow(
                getString(R.string.template_restriction_title),
                getString(R.string.template_restriction_subtitle),
                prefs.getBoolean(KEY_TEMPLATE_RESTRICTION_ENABLED, false));
        CheckBox restrictionCheck = (CheckBox) restrictionRow.getTag();
        subContainer.addView(restrictionRow);
        TextView restrictionTypeLabel = new TextView(this);
        final String[] currentType = {prefs.getString(KEY_TEMPLATE_RESTRICTION_TYPE, "SOFT")};
        restrictionTypeLabel.setText("Type: " + currentType[0]);
        restrictionTypeLabel.setTextColor(getColorFromAttr(android.R.attr.textColorSecondary));
        restrictionTypeLabel.setTextSize(14);
        restrictionTypeLabel.setPadding(dp24 + dp12, 0, 0, dp8);
        restrictionTypeLabel.setVisibility(prefs.getBoolean(KEY_TEMPLATE_RESTRICTION_ENABLED, false) ? View.VISIBLE : View.GONE);
        subContainer.addView(restrictionTypeLabel);
        restrictionCheck.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean(KEY_TEMPLATE_RESTRICTION_ENABLED, isChecked).apply();
            restrictionTypeLabel.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
        restrictionRow.setOnClickListener(v -> {
            restrictionCheck.toggle();
        });
        restrictionTypeLabel.setOnClickListener(v -> {
            String[] labels = {
                    getString(R.string.filter_restriction_soft_option),
                    getString(R.string.filter_restriction_medium_option),
                    getString(R.string.filter_restriction_hard_option),
                    getString(R.string.filter_restriction_manual_option)
            };
            String[] values = {"SOFT", "MEDIUM", "HARD", "MANUAL"};
            int checked = java.util.Arrays.asList(values).indexOf(currentType[0]);
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Select restriction type")
                    .setSingleChoiceItems(labels, checked, (d, which) -> {
                        currentType[0] = values[which];
                        restrictionTypeLabel.setText("Type: " + currentType[0]);
                        prefs.edit().putString(KEY_TEMPLATE_RESTRICTION_TYPE, currentType[0]).apply();
                        d.dismiss();
                    })
                    .setPositiveButton(R.string.dialog_cancel, null)
                    .show();
        });

        subContainer.addView(makeDivider(dp24));

        // Sleep mode row
        LinearLayout sleepRow = buildTemplateOptionRow(
                getString(R.string.template_sleep_title),
                getString(R.string.template_sleep_subtitle),
                prefs.getBoolean(KEY_TEMPLATE_SLEEP_MODE_ENABLED, false));
        CheckBox sleepCheck = (CheckBox) sleepRow.getTag();
        subContainer.addView(sleepRow);
        TextView sleepTypeLabel = new TextView(this);
        final String[] currentSleepType = {prefs.getString(KEY_TEMPLATE_SLEEP_MODE_TYPE, "TIMER")};
        sleepTypeLabel.setText("Type: " + currentSleepType[0]);
        sleepTypeLabel.setTextColor(getColorFromAttr(android.R.attr.textColorSecondary));
        sleepTypeLabel.setTextSize(14);
        sleepTypeLabel.setPadding(dp24 + dp12, 0, 0, dp8);
        sleepTypeLabel.setVisibility(prefs.getBoolean(KEY_TEMPLATE_SLEEP_MODE_ENABLED, false) ? View.VISIBLE : View.GONE);
        subContainer.addView(sleepTypeLabel);
        sleepCheck.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean(KEY_TEMPLATE_SLEEP_MODE_ENABLED, isChecked).apply();
            sleepTypeLabel.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
        sleepRow.setOnClickListener(v -> {
            sleepCheck.toggle();
        });
        sleepTypeLabel.setOnClickListener(v -> {
            String[] sleepLabels = {"Timer", "Permanent"};
            String[] sleepValues = {"TIMER", "PERMANENT"};
            int checked = java.util.Arrays.asList(sleepValues).indexOf(currentSleepType[0]);
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Select sleep mode type")
                    .setSingleChoiceItems(sleepLabels, checked, (d, which) -> {
                        currentSleepType[0] = sleepValues[which];
                        sleepTypeLabel.setText("Type: " + currentSleepType[0]);
                        prefs.edit().putString(KEY_TEMPLATE_SLEEP_MODE_TYPE, currentSleepType[0]).apply();
                        d.dismiss();
                    })
                    .setPositiveButton(R.string.dialog_cancel, null)
                    .show();
        });

        subContainer.addView(makeDivider(dp24));

        // Whitelist row
        LinearLayout whitelistRow = buildTemplateOptionRow(
                getString(R.string.template_whitelist_title),
                getString(R.string.template_whitelist_subtitle),
                prefs.getBoolean(KEY_TEMPLATE_WHITELIST_ENABLED, false));
        CheckBox whitelistCheck = (CheckBox) whitelistRow.getTag();
        whitelistCheck.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean(KEY_TEMPLATE_WHITELIST_ENABLED, isChecked).apply();
        });
        whitelistRow.setOnClickListener(v -> {
            whitelistCheck.toggle();
        });
        subContainer.addView(whitelistRow);

        subContainer.addView(makeDivider(dp24));

        // Blacklist row
        LinearLayout blacklistRow = buildTemplateOptionRow(
                getString(R.string.template_blacklist_title),
                getString(R.string.template_blacklist_subtitle),
                prefs.getBoolean(KEY_TEMPLATE_BLACKLIST_ENABLED, false));
        CheckBox blacklistCheck = (CheckBox) blacklistRow.getTag();
        blacklistCheck.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean(KEY_TEMPLATE_BLACKLIST_ENABLED, isChecked).apply();
        });
        blacklistRow.setOnClickListener(v -> {
            blacklistCheck.toggle();
        });
        subContainer.addView(blacklistRow);

        subContainer.addView(makeDivider(dp24));

        // Notification row
        LinearLayout notificationRow = buildTemplateOptionRow(
                getString(R.string.template_notification_title),
                getString(R.string.template_notification_subtitle),
                prefs.getBoolean(KEY_TEMPLATE_NOTIFICATION_ENABLED, false));
        CheckBox notificationCheck = (CheckBox) notificationRow.getTag();
        notificationCheck.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean(KEY_TEMPLATE_NOTIFICATION_ENABLED, isChecked).apply();
        });
        notificationRow.setOnClickListener(v -> {
            notificationCheck.toggle();
        });
        subContainer.addView(notificationRow);

        root.addView(subContainer);

        masterSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            subContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            prefs.edit().putBoolean(KEY_TEMPLATE_ENABLED, isChecked).apply();
            if (isChecked && !ShappkyService.isRunning()) {
                Intent serviceIntent = new Intent(SettingsActivityDialogs.this, ShappkyService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(SettingsActivityDialogs.this, serviceIntent);
                } else {
                    startService(serviceIntent);
                }
            }
        });

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(root);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.settings_install_template_title))
                .setView(scrollView)
                .setPositiveButton(getString(R.string.dialog_close), null)
                .show();
        dialog.setOnDismissListener(d -> loadSettings());
    }

    private LinearLayout buildTemplateOptionRow(String title, String subtitle, boolean checked) {
        int dp12 = (int) (12 * getResources().getDisplayMetrics().density);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp12, 0, dp12);
        CheckBox checkBox = new CheckBox(this);
        checkBox.setChecked(checked);
        checkBox.setClickable(false);
        checkBox.setFocusable(false);
        row.addView(checkBox);
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(getColorFromAttr(android.R.attr.textColorPrimary));
        titleView.setTextSize(16);
        textCol.addView(titleView);
        TextView subView = new TextView(this);
        subView.setText(subtitle);
        subView.setTextColor(getColorFromAttr(android.R.attr.textColorSecondary));
        subView.setTextSize(12);
        textCol.addView(subView);
        row.addView(textCol);
        row.setTag(checkBox);
        return row;
    }

    private int getColorFromAttr(int attr) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return tv.data;
        }
        return 0xFF888888;
    }

    private View makeDivider(int paddingH) {
        View divider = new View(this);
        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1);
        p.setMargins(paddingH, 4, paddingH, 4);
        divider.setLayoutParams(p);
        divider.setBackgroundColor(0x22888888);
        return divider;
    }

    private void updateSortButtonText(TextView btn, boolean open) {
        String label = getString(R.string.filter_sort_button);
        btn.setText(open ? label + "  ▲" : label + "  ▼");
    }

    protected List<AppModel> filterOutProtected(List<AppModel> apps) {
        List<AppModel> result = new ArrayList<>();
        for (AppModel app : apps) {
            if (!app.isProtected()) result.add(app);
        }
        return result;
    }
}
