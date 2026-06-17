package com.gree1d.reappzuku;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.RadioButton;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import android.widget.PopupMenu;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.os.Handler;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import android.widget.Toast;
import android.os.Looper;

import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.core.content.ContextCompat;

import com.gree1d.reappzuku.databinding.ActivitySettingsBinding;

import java.util.Set;
import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;

import static com.gree1d.reappzuku.PreferenceKeys.*;
import static com.gree1d.reappzuku.AppConstants.*;

public class SettingsActivity extends BaseActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "SettingsActivity";

    private ActivitySettingsBinding binding;
    private ShellManager shellManager;
    private BackgroundAppManager appManager;
    private AutoKillManager autoKillManager;
    private SleepModeManager sleepModeManager;
    private BackupManager backupManager;
    private RestrictionsScheduler scheduler;
    private AdditionalScenariosManager additionalScenariosManager;
    private RamKillShortcutManager ramKillShortcutManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private int easterEggClickCount = 0;
    private static final int EASTER_EGG_THRESHOLD = 5;
    private PresetManager presetManager;

    private final CompoundButton.OnCheckedChangeListener autoKillListener = (buttonView, isChecked) -> {
        if (isChecked && !hasPrivilege()) {
            buttonView.setChecked(false);
            Toast.makeText(this, getString(R.string.settings_requires_privilege), Toast.LENGTH_LONG).show();
            return;
        }
        putAutoKillPref(KEY_AUTO_KILL_ENABLED, isChecked);
        boolean periodicEnabled = binding.switchPeriodicKill.isChecked();
        updateAutomationOptionsVisibility(isChecked, periodicEnabled);
        applyServiceDependentState(isChecked);
        if (isChecked) {
            startAutomationService();
            AutoKillWorker.schedule(this, "Periodic Kill");
        } else {
            stopService(new Intent(this, ShappkyService.class));
            AutoKillWorker.cancel(this);
        }
    };

    private final CompoundButton.OnCheckedChangeListener periodicKillListener = (buttonView, isChecked) -> {
        putAutoKillPref(KEY_PERIODIC_KILL_ENABLED, isChecked);
        boolean serviceEnabled = binding.switchAutoKill.isChecked();
        updateAutomationOptionsVisibility(serviceEnabled, isChecked);
    };

    private final CompoundButton.OnCheckedChangeListener screenOffListener = (buttonView, isChecked) ->
            putAutoKillPref(KEY_KILL_ON_SCREEN_OFF, isChecked);

    private final CompoundButton.OnCheckedChangeListener ramThresholdListener = (buttonView, isChecked) -> {
        putAutoKillPref(KEY_RAM_THRESHOLD_ENABLED, isChecked);
        updateRamThresholdLimitVisibility(isChecked && binding.switchAutoKill.isChecked());
    };

    private final ActivityResultLauncher<String> createBackupLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> { if (uri != null) exportBackup(uri); });

    private final ActivityResultLauncher<String[]> restoreBackupLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> { if (uri != null) importBackup(uri); });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        shellManager = new ShellManager(this.getApplicationContext(), handler, executor);
        appManager = new BackgroundAppManager(this.getApplicationContext(), handler, executor, shellManager);
        autoKillManager = new AutoKillManager(this.getApplicationContext(), handler, executor, shellManager, appManager.getCurrentAppsList());
        sleepModeManager = new SleepModeManager(this.getApplicationContext(), handler, executor, shellManager);
        backupManager = new BackupManager(this);
        scheduler = new RestrictionsScheduler(
                getApplicationContext(), handler, executor, shellManager, appManager, sleepModeManager);
        additionalScenariosManager = new AdditionalScenariosManager(this);
        ramKillShortcutManager = new RamKillShortcutManager(this, shellManager);
        presetManager = new PresetManager(this);

        setupToolbar();
        loadSettings();
        setupListeners();
        setupBottomNavigation();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        int accent    = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
        int onColor   = sharedPreferences.getInt(KEY_ACCENT_ON_COLOR, ACCENT_ON_WHITE);
        boolean isAmoled = sharedPreferences.getBoolean(KEY_AMOLED, false);

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
            applyCustomAccentToSectionHeaders(customColor);
            applyCustomAccentToSwitches(customColor);
            return;
        }

        boolean isLightAccent = (accent == ACCENT_APRICOT || accent == ACCENT_SKY ||
                accent == ACCENT_PAPAYA || accent == ACCENT_LAVENDER ||
                accent == ACCENT_MINT || accent == ACCENT_PEACH ||
                accent == ACCENT_POWDER || accent == ACCENT_FOG);
        binding.toolbar.setTitleTextColor(isLightAccent ? Color.BLACK : Color.WHITE);
    }

    private void applyCustomAccentToSectionHeaders(int color) {
        int[] titleIds = {
            R.id.section_title_information,
            R.id.section_title_appearance,
            R.id.section_title_stability,
            R.id.section_title_autokill,
            R.id.section_title_advanced,
            R.id.section_title_about
        };
        for (int id : titleIds) {
            TextView tv = findViewById(id);
            if (tv != null) tv.setTextColor(color);
        }
    }

    private void applyCustomAccentToSwitches(int color) {
        int trackColor = darkenColor(color, 0.6f);

        int[] switchIds = {
            R.id.switch_auto_kill,
            R.id.switch_periodic_kill,
            R.id.switch_kill_screen_off,
            R.id.switch_ram_threshold,
            R.id.switch_sleep_mode
        };

        for (int id : switchIds) {
            MaterialSwitch sw = findViewById(id);
            if (sw == null) continue;

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
            sw.setThumbTintList(thumbTint);
            sw.setTrackTintList(trackTint);
        }
    }

    private static int darkenColor(int color, float factor) {
        int a = android.graphics.Color.alpha(color);
        int r = Math.round(android.graphics.Color.red(color)   * factor);
        int g = Math.round(android.graphics.Color.green(color) * factor);
        int b = Math.round(android.graphics.Color.blue(color)  * factor);
        return android.graphics.Color.argb(a,
                Math.min(r, 255), Math.min(g, 255), Math.min(b, 255));
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.navIconMain.setSelected(false);
        binding.bottomNavigation.navIconSettings.setSelected(true);
        binding.bottomNavigation.navIconStatistics.setSelected(false);
        binding.bottomNavigation.navBtnMain.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            finish();
        });
        binding.bottomNavigation.navBtnSettings.setOnClickListener(v -> {});
        binding.bottomNavigation.navBtnStatistics.setOnClickListener(v -> {
            startActivity(new Intent(this, StatisticsActivity.class));
            finish();
        });
        applyNavBarInsets(binding.bottomNavigation.getRoot());
    }

    private void loadSettings() {
        int theme = sharedPreferences.getInt(KEY_THEME,
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        boolean isAmoled = sharedPreferences.getBoolean(KEY_AMOLED, false);
        updateThemeText(theme, isAmoled);

        int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
        updateAccentText(accent);
        updateAccentLayoutEnabled(theme);

        int onColor = sharedPreferences.getInt(KEY_ACCENT_ON_COLOR, ACCENT_ON_WHITE);
        updateOnColorText(onColor);
        updateOnColorLayoutVisibility(accent);

        int notificationMode = sharedPreferences.getInt(KEY_NOTIFICATION_MODE, NOTIFICATION_MODE_ALL);
        updateNotificationModeText(notificationMode);

        boolean serviceEnabled = getAutoKillPref(KEY_AUTO_KILL_ENABLED, false);
        binding.switchAutoKill.setChecked(serviceEnabled);

        boolean periodicKillEnabled = getAutoKillPref(KEY_PERIODIC_KILL_ENABLED, false);
        binding.switchPeriodicKill.setChecked(periodicKillEnabled);

        int killInterval = getAutoKillIntPref(KEY_KILL_INTERVAL, DEFAULT_KILL_INTERVAL_MS);
        updateKillIntervalText(killInterval);

        binding.switchKillScreenOff.setChecked(getAutoKillPref(KEY_KILL_ON_SCREEN_OFF, false));

        boolean ramThresholdEnabled = getAutoKillPref(KEY_RAM_THRESHOLD_ENABLED, false);
        binding.switchRamThreshold.setChecked(ramThresholdEnabled);
        int ramThreshold = getAutoKillIntPref(KEY_RAM_THRESHOLD, DEFAULT_RAM_THRESHOLD_PERCENT);
        updateRamThresholdText(ramThreshold);
        updateRamThresholdLimitVisibility(ramThresholdEnabled && serviceEnabled);

        updateAutoKillTypeText(autoKillManager.getAutoKillType());
        updateAutomationOptionsVisibility(serviceEnabled, periodicKillEnabled);

        binding.switchSleepMode.setChecked(sleepModeManager.isSleepModeEnabled());
        long sleepDelay = sharedPreferences.getLong(KEY_SLEEP_MODE_DELAY, DEFAULT_SLEEP_MODE_DELAY_MS);
        updateSleepModeDelayText(sleepDelay);

        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            binding.textVersion.setText(getString(R.string.settings_version_label, versionName));
        } catch (Exception e) {
            binding.textVersion.setText(R.string.app_name);
        }

        loadAdditionalScenariosSettings();
    }

    private void setupListeners() {
        binding.layoutTheme.setOnClickListener(v -> showThemeDialog());

        binding.layoutAccent.setOnClickListener(v -> {
            int theme = sharedPreferences.getInt(KEY_THEME,
                    androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            if (theme == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) return;
            showAccentDialog();
        });

        binding.layoutAccentOnColor.setOnClickListener(v -> showAccentOnColorDialog());

        binding.layoutNotificationMode.setOnClickListener(v -> showNotificationModeDialog());

        binding.switchAutoKill.setOnCheckedChangeListener(autoKillListener);

        binding.switchPeriodicKill.setOnCheckedChangeListener(periodicKillListener);

        binding.switchKillScreenOff.setOnCheckedChangeListener(screenOffListener);

        binding.switchRamThreshold.setOnCheckedChangeListener(ramThresholdListener);
        binding.layoutRamThreshold.setOnClickListener(v -> showRamThresholdDialog());

        binding.layoutKillInterval.setOnClickListener(v -> showKillIntervalDialog());
        binding.layoutHiddenApps.setOnClickListener(v -> showHiddenAppsDialog());


        binding.layoutWhitelist.setOnClickListener(v -> {
            if (!isServiceEnabled()) { showServiceRequiredToast(); return; }
            showWhitelistDialog();
        });
        binding.layoutBlacklist.setOnClickListener(v -> {
            if (!isServiceEnabled()) { showServiceRequiredToast(); return; }
            showBlacklistDialog();
        });


        binding.layoutBackgroundRestriction.setVisibility(
                appManager.supportsBackgroundRestriction() ? View.VISIBLE : View.GONE);
        binding.layoutBackgroundRestriction.setOnClickListener(v -> {
            if (!isServiceEnabled()) { showServiceRequiredToast(); return; }
            showBackgroundRestrictionDialog();
        });
        binding.layoutReapplyRestrictions.setVisibility(
                appManager.supportsBackgroundRestriction() ? View.VISIBLE : View.GONE);
        binding.layoutReapplyRestrictions.setOnClickListener(v -> {
            if (!isServiceEnabled()) { showServiceRequiredToast(); return; }
            Set<String> savedRestrictions = appManager.getBackgroundRestrictedApps();
            if (savedRestrictions.isEmpty()) {
                Toast.makeText(this, getString(R.string.settings_no_saved_restrictions), Toast.LENGTH_SHORT).show();
                return;
            }
            appManager.reapplySavedBackgroundRestrictions(null);
        });
        binding.layoutRestrictionsScheduler.setOnClickListener(v -> {
            if (!isServiceEnabled()) { showServiceRequiredToast(); return; }
            showRestrictionsSchedulerDialog();
        });


        binding.switchSleepMode.setChecked(sleepModeManager.isSleepModeEnabled());
        binding.switchSleepMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !isServiceEnabled()) {
                buttonView.setChecked(false);
                showServiceRequiredToast();
                return;
            }
            if (isChecked) {
                resetDialogButtonColors(new MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.settings_sleep_mode_title))
                        .setMessage(getString(R.string.settings_sleep_mode_restart_message))
                        .setPositiveButton(getString(R.string.dialog_ok), (dialog, which) -> {
                            sleepModeManager.setSleepModeEnabled(true);
                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                                    () -> android.os.Process.killProcess(android.os.Process.myPid()), 300);
                        })
                        .setNegativeButton(getString(R.string.dialog_cancel), (dialog, which) -> buttonView.setChecked(false))
                        .setCancelable(false)
                        .show());
            } else {
                sleepModeManager.setSleepModeEnabled(false);
            }
        });
        binding.layoutSleepModeApps.setOnClickListener(v -> {
            if (!isServiceEnabled()) { showServiceRequiredToast(); return; }
            showSleepModeAppsDialog();
        });
        binding.layoutSleepModeDelay.setOnClickListener(v -> {
            if (!isServiceEnabled()) { showServiceRequiredToast(); return; }
            showSleepModeDelayDialog();
        });


        binding.layoutKillMode.setOnClickListener(v -> {
            if (!isServiceEnabled()) { showServiceRequiredToast(); return; }
            showKillModeDialog();
        });
        binding.layoutAutoKillType.setOnClickListener(v -> {
            if (!isServiceEnabled()) { showServiceRequiredToast(); return; }
            showAutoKillTypeDialog();
        });

        binding.layoutHelp.setOnClickListener(v -> openUrl(getString(R.string.url_help)));

        updateShellModeText();

        binding.layoutClearCache.setOnClickListener(v -> {
            binding.layoutClearCache.setEnabled(false);
            appManager.clearCaches(() -> binding.layoutClearCache.setEnabled(true));
        });

        binding.layoutBackupRestore.setOnClickListener(v -> showBackupRestoreDialog());
        binding.layoutGithub.setOnClickListener(v -> openUrl("https://github.com/gree1d/ReAppzuku"));
        binding.layoutCheckUpdates.setOnClickListener(v -> UpdateChecker.checkForUpdatesManual(this, sharedPreferences));
        binding.layoutTelegram.setOnClickListener(v -> openUrl("https://t.me/AkM0o"));
        binding.layoutSpecialThanks.setOnClickListener(v -> showSpecialThanksDialog());
        binding.textVersion.setOnClickListener(v -> {
            easterEggClickCount++;
            if (easterEggClickCount == EASTER_EGG_THRESHOLD) {
                easterEggClickCount = 0;
                showEasterEggDialog();
            }
        });

        updateKillModeVisibility();
        applyServiceDependentState(isServiceEnabled());
        applyPresetActiveState(isPresetActive());
        setupAdditionalScenariosListeners();
    }

    private void updateKillModeVisibility() {
        int mode = autoKillManager.getKillMode();
        binding.textKillMode.setText(mode == 0 ? R.string.settings_mode_whitelist : R.string.settings_mode_blacklist);
        binding.layoutBlacklist.setVisibility(mode == 1 ? View.VISIBLE : View.GONE);
        binding.layoutWhitelist.setVisibility(mode == 0 ? View.VISIBLE : View.GONE);
    }


    private boolean hasPrivilege() {
        return shellManager.hasShizukuPermission() || shellManager.resolveAnyShellPermission();
    }


    private boolean isPresetActive() {
        return presetManager != null && presetManager.getActivePresetNumber() != 0;
    }

    private boolean getAutoKillPref(String key, boolean defVal) {
        if (isPresetActive()) {
            return sharedPreferences.getBoolean(PresetManager.KEY_BACKUP_PREFIX + key, defVal);
        }
        return sharedPreferences.getBoolean(key, defVal);
    }

    private void putAutoKillPref(String key, boolean value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(key, value);
        if (isPresetActive()) {
            editor.putBoolean(PresetManager.KEY_BACKUP_PREFIX + key, value);
        }
        editor.apply();
    }

    private int getAutoKillIntPref(String key, int defVal) {
        if (isPresetActive()) {
            return sharedPreferences.getInt(PresetManager.KEY_BACKUP_PREFIX + key, defVal);
        }
        return sharedPreferences.getInt(key, defVal);
    }

    private void putAutoKillIntPref(String key, int value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(key, value);
        if (isPresetActive()) {
            editor.putInt(PresetManager.KEY_BACKUP_PREFIX + key, value);
        }
        editor.apply();
    }

    private boolean isServiceEnabled() {
        return getAutoKillPref(KEY_AUTO_KILL_ENABLED, false);
    }


    private void showServiceRequiredToast() {
        Toast.makeText(this, getString(R.string.settings_requires_service_enabled), Toast.LENGTH_SHORT).show();
    }


    private void applyServiceDependentState(boolean serviceEnabled) {
        float alpha = serviceEnabled ? 1.0f : 0.5f;


        binding.layoutKillMode.setAlpha(alpha);
        binding.layoutAutoKillType.setAlpha(alpha);
        binding.layoutWhitelist.setAlpha(alpha);
        binding.layoutBlacklist.setAlpha(alpha);


        if (appManager.supportsBackgroundRestriction()) {
            binding.layoutBackgroundRestriction.setAlpha(alpha);
            binding.layoutReapplyRestrictions.setAlpha(alpha);
            binding.layoutRestrictionsScheduler.setAlpha(alpha);
        }


        binding.switchSleepMode.setEnabled(serviceEnabled);
        binding.layoutSleepModeApps.setAlpha(alpha);
        binding.layoutSleepModeDelay.setAlpha(alpha);
    }

    private void applyPresetActiveState(boolean presetActive) {
        float alpha = presetActive ? 0.5f : 1.0f;
        View.OnClickListener presetBlocker = presetActive ? v -> showPresetActiveDialog() : null;

        binding.switchPeriodicKill.setEnabled(!presetActive);
        binding.switchKillScreenOff.setEnabled(!presetActive);
        binding.switchRamThreshold.setEnabled(!presetActive);

        binding.layoutPeriodicKill.setAlpha(alpha);
        binding.layoutPeriodicKill.setOnClickListener(presetActive ? v -> showPresetActiveDialog() : null);
        binding.layoutScreenLock.setAlpha(alpha);
        binding.layoutScreenLock.setOnClickListener(presetBlocker);
        binding.layoutRamThresholdToggle.setAlpha(alpha);
        binding.layoutRamThresholdToggle.setOnClickListener(presetBlocker);
        binding.layoutRamThreshold.setAlpha(alpha);
        binding.layoutRamThreshold.setOnClickListener(presetActive ? v -> showPresetActiveDialog() : v -> showRamThresholdDialog());
        binding.layoutKillInterval.setAlpha(alpha);
        binding.layoutKillInterval.setOnClickListener(presetActive ? v -> showPresetActiveDialog() : v -> showKillIntervalDialog());
        binding.layoutKillMode.setAlpha(alpha);
        binding.layoutKillMode.setOnClickListener(presetActive ? v -> showPresetActiveDialog() : v -> {
            if (!isServiceEnabled()) { showServiceRequiredToast(); return; }
            showKillModeDialog();
        });
        binding.layoutAutoKillType.setAlpha(alpha);
        binding.layoutAutoKillType.setOnClickListener(presetActive ? v -> showPresetActiveDialog() : v -> {
            if (!isServiceEnabled()) { showServiceRequiredToast(); return; }
            showAutoKillTypeDialog();
        });
        binding.layoutBlacklist.setAlpha(alpha);
        binding.layoutBlacklist.setOnClickListener(presetActive ? v -> showPresetActiveDialog() : v -> {
            if (!isServiceEnabled()) { showServiceRequiredToast(); return; }
            showBlacklistDialog();
        });
        binding.layoutWhitelist.setAlpha(alpha);
        binding.layoutWhitelist.setOnClickListener(presetActive ? v -> showPresetActiveDialog() : v -> {
            if (!isServiceEnabled()) { showServiceRequiredToast(); return; }
            showWhitelistDialog();
        });
        binding.layoutAdditionalScenarios.setAlpha(alpha);
        binding.layoutAdditionalScenarios.setOnClickListener(presetActive ? v -> showPresetActiveDialog() : v -> {
            if (!isServiceEnabled()) { showServiceRequiredToast(); return; }
            showAdditionalScenariosDialog();
        });
    }

    private void showPresetActiveDialog() {
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.antichange_settings)
                .setPositiveButton(R.string.dialog_close, null)
                .show();
    }

    private void updateShellModeText() {
        executor.execute(() -> {
            final boolean privileged = shellManager.hasShizukuPermission() || shellManager.resolveAnyShellPermission();
            final String text;
            if (shellManager.hasShizukuPermission()) {
                text = getString(R.string.settings_shell_shizuku_ok);
            } else if (shellManager.resolveAnyShellPermission()) {
                text = getString(R.string.settings_shell_root_ok);
            } else {
                text = getString(R.string.settings_shell_no_access);
            }
            handler.post(() -> {
                binding.textShellMode.setText(text);
                if (!privileged) {

                    binding.switchAutoKill.setEnabled(false);
                    binding.switchAutoKill.setAlpha(0.5f);

                    if (sharedPreferences.getBoolean(KEY_AUTO_KILL_ENABLED, false)) {
                        sharedPreferences.edit()
                                .putBoolean(KEY_AUTO_KILL_ENABLED, false)
                                .putBoolean(PresetManager.KEY_BACKUP_PREFIX + KEY_AUTO_KILL_ENABLED, false)
                                .apply();
                        binding.switchAutoKill.setChecked(false);
                        stopService(new Intent(SettingsActivity.this, ShappkyService.class));
                        AutoKillWorker.cancel(SettingsActivity.this);
                    }
                    applyServiceDependentState(false);
                } else {
                    binding.switchAutoKill.setEnabled(true);
                    binding.switchAutoKill.setAlpha(1.0f);
                }
            });
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
        android.widget.RadioGroup group = bodyView.findViewById(R.id.single_choice_group);
        int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
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
        group.check(1000 + autoKillManager.getAutoKillType());

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setCustomTitle(titleView)
                .setView(bodyView)
                .create();
        titleView.findViewById(R.id.btn_help).setOnClickListener(v -> {
            dialog.dismiss();
            showAutoKillTypeHelpDialog(() -> showAutoKillTypeDialog());
        });
        group.setOnCheckedChangeListener((g, id) -> {
            autoKillManager.setAutoKillType(id - 1000);
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
                modes, autoKillManager.getKillMode(), which -> {
                    autoKillManager.setKillMode(which);
                    updateKillModeVisibility();
                    if (which == 0) {
                        boolean autoKillEnabled = getAutoKillPref(KEY_AUTO_KILL_ENABLED, false);
                        Set<String> whitelistedApps = sharedPreferences.getStringSet(KEY_WHITELISTED_APPS, new HashSet<>());
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

    private void showBlacklistDialog() {
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

        appManager.loadAllApps(allApps -> {
            allApps = filterOutProtected(allApps);
            Set<String> blacklisted = autoKillManager.getBlacklistedApps();
            FilterAppsAdapter filterAdapter = new FilterAppsAdapter(this, allApps, blacklisted);
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
                autoKillManager.saveBlacklistedApps(filterAdapter.getSelectedPackages());
                dialog.dismiss();
            });
        });
    }

    private void showWhitelistDialog() {
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

        appManager.loadAllApps(allApps -> {
            allApps = filterOutProtected(allApps);
            Set<String> whitelistedApps = appManager.getWhitelistedApps();
            FilterAppsAdapter filterAdapter = new FilterAppsAdapter(this, allApps, whitelistedApps);
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
                if (!whitelistDialog.isShowing()) return;
                filterAdapter.notifyDataSetChanged();
            });

            searchBox.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterAdapter.getFilter().filter(s); }
                @Override public void afterTextChanged(Editable s) {}
            });

            whitelistDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                appManager.saveWhitelistedApps(filterAdapter.getSelectedPackages());
                whitelistDialog.dismiss();
            });
        });
    }

    private void showHiddenAppsDialog() {
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

        appManager.loadAllApps(allApps -> {
            Set<String> hiddenApps = appManager.getHiddenApps();
            FilterAppsAdapter filterAdapter = new FilterAppsAdapter(this, allApps, hiddenApps);
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
                if (!filterDialog.isShowing()) return;
                filterAdapter.notifyDataSetChanged();
            });

            searchBox.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterAdapter.getFilter().filter(s); }
                @Override public void afterTextChanged(Editable s) {}
            });

            filterDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                appManager.saveHiddenApps(filterAdapter.getSelectedPackages());
                filterDialog.dismiss();
            });
        });
    }

    private void showBackgroundRestrictionDialog() {
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


        appManager.loadBackgroundRestrictionApps(rawApps -> {
            List<AppModel> allApps = filterOutProtected(rawApps);
            Set<String> desiredRestrictedApps = appManager.getBackgroundRestrictedApps();
            Set<String> hardRestrictedApps    = appManager.getHardRestrictedApps();
            Set<String> mediumRestrictedApps  = appManager.getMediumRestrictedApps();
            Set<String> manualRestrictedApps  = appManager.getManualRestrictedApps();
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
                if (!restrictionDialog.isShowing()) return;
                filterAdapter.notifyDataSetChanged();
            });

            searchBox.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterAdapter.getFilter().filter(s); }
                @Override public void afterTextChanged(Editable s) {}
            });

            filterAdapter.setOnSelectionChangedListener(() -> {
                restrictionDialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(getString(R.string.dialog_apply));
            });

            Runnable doApply = () -> {
                Set<String> targetPackages  = filterAdapter.getSelectedPackages();
                Set<String> hardPackages    = filterAdapter.getHardRestrictedPackages();
                Set<String> mediumPackages  = filterAdapter.getMediumRestrictedPackages();
                Set<String> manualPackages  = filterAdapter.getManualRestrictedPackages();
                java.util.Map<String, Integer> opsMasks    = filterAdapter.getManualOpsMasks();
                java.util.Map<String, Integer> buckets     = filterAdapter.getManualBuckets();

                for (java.util.Map.Entry<String, Integer> e : opsMasks.entrySet()) {
                    appManager.saveManualOpsMask(e.getKey(), e.getValue());
                }
                for (java.util.Map.Entry<String, Integer> e : buckets.entrySet()) {
                    appManager.saveManualBucket(e.getKey(), e.getValue());
                }

                Set<String> newMediumSet = new java.util.HashSet<>(mediumPackages);
                newMediumSet.retainAll(targetPackages);
                appManager.saveMediumRestrictedApps(newMediumSet);

                Set<String> newManualSet = new java.util.HashSet<>(manualPackages);
                newManualSet.retainAll(targetPackages);
                appManager.saveManualRestrictedApps(newManualSet);

                Set<String> currentDesired = new java.util.HashSet<>(desiredRestrictedApps);
                Set<String> packagesToRestrict = new java.util.HashSet<>(targetPackages);
                packagesToRestrict.removeAll(currentDesired);

                int systemAppCount = 0;
                for (AppModel app : allApps) {
                    if (packagesToRestrict.contains(app.getPackageName()) && app.isSystemApp()) systemAppCount++;
                }

                if (systemAppCount > 0) {
                    resetDialogButtonColors(new MaterialAlertDialogBuilder(SettingsActivity.this)
                            .setTitle(getString(R.string.settings_restriction_system_apps_title))
                            .setMessage(getString(R.string.settings_restriction_system_apps_message, systemAppCount))
                            .setPositiveButton(getString(R.string.settings_restriction_system_apps_confirm), (d2, w2) ->
                                    appManager.applyBackgroundRestriction(targetPackages, hardPackages, null))
                            .setNegativeButton(getString(R.string.dialog_cancel), null)
                            .show());
                } else if (!packagesToRestrict.isEmpty()) {
                    resetDialogButtonColors(new MaterialAlertDialogBuilder(SettingsActivity.this)
                            .setTitle(getString(R.string.settings_restriction_warning_title))
                            .setMessage(getString(R.string.settings_restriction_warning_message))
                            .setPositiveButton(getString(R.string.dialog_apply), (d2, w2) ->
                                    appManager.applyBackgroundRestriction(targetPackages, hardPackages, null))
                            .setNegativeButton(getString(R.string.dialog_cancel), null)
                            .show());
                } else {
                    appManager.applyBackgroundRestriction(targetPackages, hardPackages, null);
                }
            };

            restrictionDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dialog_save), (dialog, which) -> doApply.run());
        });
    }

    private java.util.Map<String, Integer> buildInitialManualMasks(Set<String> manualPackages) {
        java.util.Map<String, Integer> masks = new java.util.HashMap<>();
        for (String pkg : manualPackages) {
            masks.put(pkg, appManager.getManualOpsMask(pkg));
        }
        return masks;
    }

    private java.util.Map<String, Integer> buildInitialManualBuckets(Set<String> manualPackages) {
        java.util.Map<String, Integer> buckets = new java.util.HashMap<>();
        for (String pkg : manualPackages) {
            int bucket = appManager.getManualBucket(pkg);
            if (bucket != 0) buckets.put(pkg, bucket);
        }
        return buckets;
    }

    private void showRestrictionTypeHelpDialog(Runnable onBack) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int start = sb.length();
        sb.append(getString(R.string.bgrest_help_soft_title));
        sb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, sb.length(), 0);
        sb.append("\n").append(getString(R.string.bgrest_help_soft_body)).append("\n\n\n\n");
        start = sb.length();
        sb.append(getString(R.string.bgrest_help_medium_title));
        sb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, sb.length(), 0);
        sb.append("\n").append(getString(R.string.bgrest_help_medium_body)).append("\n\n\n\n");
        start = sb.length();
        sb.append(getString(R.string.bgrest_help_hard_title));
        sb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, sb.length(), 0);
        sb.append("\n").append(getString(R.string.bgrest_help_hard_body)).append("\n\n\n\n");
        start = sb.length();
        sb.append(getString(R.string.bgrest_help_manual_title));
        sb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, sb.length(), 0);
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

    private void showSpecialThanksDialog() {
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

    private void updateSleepModeDelayText(long delayMs) {
        String[] labels = getResources().getStringArray(R.array.settings_sleep_mode_delay_labels);
        for (int i = 0; i < SLEEP_MODE_DELAYS_MS.length; i++) {
            if (SLEEP_MODE_DELAYS_MS[i] == delayMs) { binding.textSleepModeDelay.setText(labels[i]); return; }
        }
        binding.textSleepModeDelay.setText(getString(R.string.settings_sleep_mode_delay_fallback, delayMs / 60000));
    }

    private void showSleepModeDelayDialog() {
        long current = sharedPreferences.getLong(KEY_SLEEP_MODE_DELAY, DEFAULT_SLEEP_MODE_DELAY_MS);
        int selected = SLEEP_MODE_DELAYS_MS.length - 1;
        for (int i = 0; i < SLEEP_MODE_DELAYS_MS.length; i++) {
            if (SLEEP_MODE_DELAYS_MS[i] == current) { selected = i; break; }
        }
        showSingleChoiceDialog(getString(R.string.settings_sleep_mode_delay_title),
                getResources().getStringArray(R.array.settings_sleep_mode_delay_labels), selected, which -> {
                    sharedPreferences.edit().putLong(KEY_SLEEP_MODE_DELAY, SLEEP_MODE_DELAYS_MS[which]).apply();
                    updateSleepModeDelayText(SLEEP_MODE_DELAYS_MS[which]);
                });
    }

    private void showSleepModeAppsDialog() {
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

        sleepModeManager.loadSleepModeApps(rawApps -> {
            List<AppModel> allApps = filterOutProtected(rawApps);
            Set<String> timerApps = sleepModeManager.getSleepModeApps();
            Set<String> permanentApps = sleepModeManager.getPermanentFreezeApps();
            FilterAppsAdapter filterAdapter = new FilterAppsAdapter(this, allApps, timerApps, permanentApps, sleepModeManager, true);
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

            filterAdapter.setOnSelectionChangedListener(() ->
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(getString(R.string.dialog_apply)));

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                sleepModeManager.saveSleepModeApps(
                        filterAdapter.getTimerPackages(),
                        filterAdapter.getPermanentPackages(),
                        filterAdapter.getFreezeMethodMap(),
                        null);
                dialog.dismiss();
            });
        });
    }

    private void updateRamThresholdText(int threshold) {
        binding.textRamThreshold.setText(getString(R.string.settings_ram_threshold_summary, threshold));
    }

    private void updateRamThresholdLimitVisibility(boolean enabled) {
        binding.layoutRamThreshold.setAlpha(enabled ? 1.0f : 0.5f);
        binding.layoutRamThreshold.setClickable(enabled);
    }

    private void showRamThresholdDialog() {
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

    private void updateNotificationModeText(int mode) {
        binding.textNotificationMode.setText(mode == NOTIFICATION_MODE_IMPORTANT_ONLY
                ? getString(R.string.settings_notification_mode_important_only)
                : getString(R.string.settings_notification_mode_all));
    }

    private void showNotificationModeDialog() {
        int current = sharedPreferences.getInt(KEY_NOTIFICATION_MODE, NOTIFICATION_MODE_ALL);
        String[] options = {
                getString(R.string.settings_notification_mode_all),
                getString(R.string.settings_notification_mode_important_only)
        };
        showSingleChoiceDialog(getString(R.string.settings_notification_mode_title),
                options, current, which -> {
                    sharedPreferences.edit().putInt(KEY_NOTIFICATION_MODE, which).apply();
                    updateNotificationModeText(which);
                });
    }

    private void updateThemeText(int themeValue, boolean isAmoled) {
        if (isAmoled) { binding.textTheme.setText(getString(R.string.settings_theme_amoled_short)); return; }
        String[] themeLabels = getResources().getStringArray(R.array.settings_theme_labels);
        for (int i = 0; i < THEME_VALUES.length; i++) {
            if (THEME_VALUES[i] == themeValue) { binding.textTheme.setText(themeLabels[i]); return; }
        }
    }

    private void updateAccentText(int accentValue) {
        if (accentValue == ACCENT_CUSTOM) {
            int color = sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR);
            binding.textAccent.setText(String.format("#%06X", 0xFFFFFF & color));
        } else {
            String[] accentLabels = getResources().getStringArray(R.array.settings_accent_labels);
            if (accentValue >= 0 && accentValue < accentLabels.length)
                binding.textAccent.setText(accentLabels[accentValue]);
        }
    }

    private void updateOnColorText(int onColor) {
        binding.textAccentOnColor.setText(onColor == ACCENT_ON_BLACK
                ? R.string.settings_accent_on_color_black
                : R.string.settings_accent_on_color_white);
    }

    private void updateOnColorLayoutVisibility(int accent) {
        boolean visible = (accent == ACCENT_CUSTOM);
        binding.layoutAccentOnColor.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void updateAccentLayoutEnabled(int themeValue) {
        boolean isSystemTheme = (themeValue == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        binding.layoutAccent.setAlpha(isSystemTheme ? 0.4f : 1.0f);
        binding.layoutAccent.setClickable(!isSystemTheme);
    }

    private void showThemeDialog() {
        int currentTheme = sharedPreferences.getInt(KEY_THEME,
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        boolean isAmoled = sharedPreferences.getBoolean(KEY_AMOLED, false);
        int selectedIndex = isAmoled ? 3 : 0;
        if (!isAmoled) {
            for (int i = 0; i < THEME_VALUES.length; i++) {
                if (THEME_VALUES[i] == currentTheme) { selectedIndex = i; break; }
            }
        }
        showSingleChoiceDialog(getString(R.string.settings_theme_dialog_title),
                getResources().getStringArray(R.array.settings_theme_labels), selectedIndex, which -> {
                    if (which == 3) {
                        sharedPreferences.edit()
                                .putBoolean(KEY_AMOLED, true)
                                .putInt(KEY_THEME, androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
                                .apply();
                        updateThemeText(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES, true);
                        updateAccentLayoutEnabled(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
                    } else {
                        int newTheme = THEME_VALUES[which];
                        sharedPreferences.edit().putInt(KEY_THEME, newTheme).putBoolean(KEY_AMOLED, false).apply();
                        updateThemeText(newTheme, false);
                        updateAccentLayoutEnabled(newTheme);
                        if (newTheme == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
                            sharedPreferences.edit().putInt(KEY_ACCENT, ACCENT_SYSTEM).apply();
                            updateAccentText(ACCENT_SYSTEM);
                        }
                        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(newTheme);
                    }
                    recreate();
                });
    }

    private void showAccentDialog() {
        int currentAccent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_INDIGO);

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

        int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
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
                sharedPreferences.edit().putInt(KEY_ACCENT, ACCENT_SYSTEM).apply();
                updateAccentText(ACCENT_SYSTEM);
                updateOnColorLayoutVisibility(ACCENT_SYSTEM);
                dialog.dismiss();
                recreate();
            } else if (which == 1) {
                openCustomColorPicker(dialog);
            } else {
                int accentValue = which - 1;
                sharedPreferences.edit().putInt(KEY_ACCENT, accentValue).apply();
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

    private void openCustomColorPicker(AlertDialog parentDialog) {
        if (parentDialog != null) parentDialog.dismiss();
        int currentCustomColor = sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR);
        ColorPickerDialog.show(this, currentCustomColor, pickedColor -> {
            sharedPreferences.edit()
                    .putInt(KEY_ACCENT, ACCENT_CUSTOM)
                    .putInt(KEY_ACCENT_CUSTOM_COLOR, pickedColor)
                    .apply();
            updateAccentText(ACCENT_CUSTOM);
            updateOnColorLayoutVisibility(ACCENT_CUSTOM);
            recreate();
        });
    }

    private void showAccentOnColorDialog() {
        int current = sharedPreferences.getInt(KEY_ACCENT_ON_COLOR, ACCENT_ON_WHITE);
        String[] options = {
                getString(R.string.settings_accent_on_color_white),
                getString(R.string.settings_accent_on_color_black)
        };
        showSingleChoiceDialog(getString(R.string.settings_accent_on_color_title),
                options, current, which -> {
                    sharedPreferences.edit().putInt(KEY_ACCENT_ON_COLOR, which).apply();
                    updateOnColorText(which);
                    recreate();
                });
    }

    private void updateAutomationOptionsVisibility(boolean serviceEnabled, boolean periodicEnabled) {
        float serviceAlpha = serviceEnabled ? 1.0f : 0.5f;
        float periodicAlpha = (serviceEnabled && periodicEnabled) ? 1.0f : 0.5f;

        binding.layoutPeriodicKill.setAlpha(serviceAlpha);
        binding.switchPeriodicKill.setEnabled(serviceEnabled);
        binding.layoutScreenLock.setAlpha(serviceAlpha);
        binding.switchKillScreenOff.setEnabled(serviceEnabled);
        binding.layoutRamThresholdToggle.setAlpha(serviceAlpha);
        binding.switchRamThreshold.setEnabled(serviceEnabled);
        boolean ramEnabled = serviceEnabled && binding.switchRamThreshold.isChecked();
        updateRamThresholdLimitVisibility(ramEnabled);
        binding.layoutKillInterval.setAlpha(periodicAlpha);
        binding.layoutKillInterval.setClickable(serviceEnabled && periodicEnabled);
    }

    private void updateKillIntervalText(int intervalMs) {
        String[] killIntervalLabels = getResources().getStringArray(R.array.settings_kill_interval_labels);
        for (int i = 0; i < KILL_INTERVALS_MS.length; i++) {
            if (KILL_INTERVALS_MS[i] == intervalMs) { binding.textKillInterval.setText(killIntervalLabels[i]); return; }
        }
        binding.textKillInterval.setText(getString(R.string.settings_interval_fallback, intervalMs / 1000));
    }

    private void updateAutoKillTypeText(int type) {
        binding.textAutoKillType.setText(type == 1
                ? getString(R.string.settings_auto_kill_type_kill)
                : getString(R.string.settings_auto_kill_type_force_stop));
    }

    private void showKillIntervalDialog() {
        if (!binding.switchAutoKill.isChecked() || !binding.switchPeriodicKill.isChecked()) return;
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

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Log.e(TAG, "Failed to open URL: " + url, e);
            Toast.makeText(this, R.string.url_open_failed, Toast.LENGTH_SHORT).show();
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

        final boolean[] showSystem  = {false};
        final boolean[] showUser    = {true};
        final boolean[] showRunning = {false};

        updateSortButtonText(btnSort, false);

        btnSort.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, btnSort);
            popup.getMenu().add(0, 0, 0, getString(R.string.filter_system))
                    .setCheckable(true).setChecked(showSystem[0]);
            popup.getMenu().add(0, 1, 1, getString(R.string.filter_user))
                    .setCheckable(true).setChecked(showUser[0]);
            popup.getMenu().add(0, 2, 2, getString(R.string.filter_running))
                    .setCheckable(true).setChecked(showRunning[0]);
            popup.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 0: showSystem[0]  = !showSystem[0];  item.setChecked(showSystem[0]);  break;
                    case 1: showUser[0]    = !showUser[0];    item.setChecked(showUser[0]);    break;
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

    private List<AppModel> filterOutProtected(List<AppModel> apps) {
        List<AppModel> result = new ArrayList<>();
        for (AppModel app : apps) {
            if (!app.isProtected()) result.add(app);
        }
        return result;
    }

    private void showRestrictionsSchedulerDialog() {
        Set<String> bgApps    = appManager.getBackgroundRestrictedApps();
        Set<String> sleepApps = sleepModeManager.getSleepModeApps();
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
                int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
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

    private void showScheduleEntryDialog(SchedulerAppItem item, boolean use24h,
                                          AlertDialog parentDialog) {
        Set<String> bgApps    = appManager.getBackgroundRestrictedApps();
        Set<String> sleepApps = sleepModeManager.getSleepModeApps();
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

        int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
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

            boolean ok = (src != null) ? scheduler.updateSchedule(e) : scheduler.addSchedule(e);
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
                scheduler.removeSchedule(src.id);
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
        executor.execute(() -> {

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
                Log.e(TAG, "showComponentPickerDialog: package not found: " + packageName, e);
            }

            handler.post(() -> {
                if (items.isEmpty()) {
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


    private String shortComponentName(String componentName) {
        if (componentName == null) return "";
        int dot = componentName.lastIndexOf('.');
        return dot >= 0 ? componentName.substring(dot + 1) : componentName;
    }

    private RestrictionsScheduler.ScheduleEntry findScheduleForPackage(String pkg) {
        for (RestrictionsScheduler.ScheduleEntry e : scheduler.getSchedules()) {
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

    private String formatTime(int hour, int minute, boolean use24h) {
        if (use24h) {
            return String.format(Locale.US, "%02d:%02d", hour, minute);
        }
        int h12 = hour % 12;
        if (h12 == 0) h12 = 12;
        return String.format(Locale.US, "%d:%02d %s", h12, minute, hour < 12 ? "AM" : "PM");
    }

    private String formatScheduleTime(RestrictionsScheduler.ScheduleEntry entry, boolean use24h) {
        return formatTime(entry.startHour, entry.startMinute, use24h)
                + " – "
                + formatTime(entry.endHour, entry.endMinute, use24h);
    }

    private static final class SchedulerAppItem {
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

    private void showBackupRestoreDialog() {
        String[] options = {
                getString(R.string.settings_backup_option_save),
                getString(R.string.settings_backup_option_restore)
        };
        resetDialogButtonColors(new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.settings_backup_restore_title))
                .setItems(options, (dialog, which) -> {
                    if (which == 0) createBackupLauncher.launch("appzuku_backup.json");
                    else restoreBackupLauncher.launch(new String[]{"application/json"});
                })
                .show());
    }

    private void exportBackup(Uri uri) {
        executor.execute(() -> {
            String json = backupManager.createBackupJson();
            if (json == null) {
                handler.post(() -> Toast.makeText(this, getString(R.string.settings_backup_create_failed), Toast.LENGTH_SHORT).show());
                return;
            }
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os != null) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                    handler.post(() -> Toast.makeText(this, getString(R.string.settings_backup_success), Toast.LENGTH_SHORT).show());
                } else {
                    handler.post(() -> Toast.makeText(this, getString(R.string.settings_backup_write_failed), Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                Log.e(TAG, "Export failed", e);
                handler.post(() -> Toast.makeText(this, getString(R.string.settings_backup_export_failed, e.getMessage()), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void importBackup(Uri uri) {
        executor.execute(() -> {
            try (InputStream is = getContentResolver().openInputStream(uri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);

                boolean success = backupManager.restoreBackupJson(sb.toString());
                handler.post(() -> {
                    if (success) {
                        Set<String> restoredRestrictedApps = new java.util.HashSet<>(
                                sharedPreferences.getStringSet(KEY_AUTOSTART_DISABLED_APPS, new java.util.HashSet<>()));
                        Runnable finishRestore = () -> {
                            applyAutomationStateFromPreferences();
                            loadSettings();
                            updateKillModeVisibility();
                            if (appManager.supportsBackgroundRestriction()
                                    && !restoredRestrictedApps.isEmpty()
                                    && !appManager.canApplyBackgroundRestrictionNow()) {
                                Toast.makeText(this, getString(R.string.settings_restore_need_permission), Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(this, getString(R.string.settings_restore_success), Toast.LENGTH_SHORT).show();
                            }
                        };
                        if (appManager.canApplyBackgroundRestrictionNow()) {
                            appManager.applyBackgroundRestriction(restoredRestrictedApps, finishRestore);
                        } else {
                            finishRestore.run();
                        }
                    } else {
                        Toast.makeText(this, getString(R.string.settings_restore_failed), Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Import failed", e);
                handler.post(() -> Toast.makeText(this, getString(R.string.settings_restore_import_failed, e.getMessage()), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showSingleChoiceDialog(String title, String[] options, int selected,
                                        java.util.function.IntConsumer onPick) {
        android.view.View view = getLayoutInflater().inflate(R.layout.dialog_single_choice, null);
        android.widget.TextView titleView = view.findViewById(R.id.single_choice_title);
        android.widget.RadioGroup group = view.findViewById(R.id.single_choice_group);

        titleView.setText(title);

        int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
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

    private void startAutomationService() {
        Intent serviceIntent = new Intent(this, ShappkyService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void applyAutomationStateFromPreferences() {
        boolean automationEnabled = sharedPreferences.getBoolean(KEY_AUTO_KILL_ENABLED, false);
        if (automationEnabled) {
            int killMode = sharedPreferences.getInt(KEY_KILL_MODE, 0);
            Set<String> whitelistedApps = sharedPreferences.getStringSet(KEY_WHITELISTED_APPS, new HashSet<>());
            if (killMode == 0 && whitelistedApps.isEmpty()) {
                sharedPreferences.edit()
                        .putBoolean(KEY_AUTO_KILL_ENABLED, false)
                        .putBoolean(PresetManager.KEY_BACKUP_PREFIX + KEY_AUTO_KILL_ENABLED, false)
                        .apply();
                if (binding.switchAutoKill != null) binding.switchAutoKill.setChecked(false);
                resetDialogButtonColors(new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.dialog_unsafe_whitelist_title)
                        .setMessage(R.string.dialog_unsafe_whitelist_message)
                        .setPositiveButton(R.string.dialog_unsafe_whitelist_ok, (dialog, which) -> dialog.dismiss())
                        .setCancelable(false)
                        .show());
                stopService(new Intent(this, ShappkyService.class));
                AutoKillWorker.cancel(this);
                return;
            }
            startAutomationService();
            AutoKillWorker.schedule(this, "Periodic Kill");
        } else {
            stopService(new Intent(this, ShappkyService.class));
            AutoKillWorker.cancel(this);
        }
    }

    private void showEasterEggDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(android.view.Gravity.CENTER);
        int pad = (int) (getResources().getDisplayMetrics().density * 24);
        layout.setPadding(pad, pad, pad, pad);

        ImageView imageView = new ImageView(this);
        imageView.setImageResource(R.drawable.settings_page_info);
        int size = (int) (getResources().getDisplayMetrics().density * 220);
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(size, size);
        imageView.setLayoutParams(imgParams);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
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

    private void loadAdditionalScenariosSettings() {
        updateAdditionalScenariosSummary();
    }

    private void setupAdditionalScenariosListeners() {
        binding.layoutAdditionalScenarios.setOnClickListener(v -> {
            if (!isServiceEnabled()) { showServiceRequiredToast(); return; }
            showAdditionalScenariosDialog();
        });
        binding.layoutPresets.setOnClickListener(v -> showPresetPickerDialog());
        binding.layoutAddShortcut.setOnClickListener(v -> ramKillShortcutManager.requestPinShortcut());
    }

    private void updateAdditionalScenariosSummary() {
        List<String> active = new ArrayList<>();
        if (additionalScenariosManager.isHeadsetTriggerEnabled())
            active.add(getString(R.string.scenarios_hw_headset_short));
        if (additionalScenariosManager.isUsbTriggerEnabled())
            active.add(getString(R.string.scenarios_hw_usb_short));
        if (additionalScenariosManager.isChargerTriggerEnabled())
            active.add(getString(R.string.scenarios_hw_charger_short));
        if (additionalScenariosManager.isWifiTriggerEnabled())
            active.add(getString(R.string.scenarios_hw_wifi_short));
        if (additionalScenariosManager.isBluetoothTriggerEnabled())
            active.add(getString(R.string.scenarios_hw_bluetooth_short));
        if (additionalScenariosManager.isGpsTriggerEnabled())
            active.add(getString(R.string.scenarios_hw_gps_short));
        if (additionalScenariosManager.isHotspotTriggerEnabled())
            active.add(getString(R.string.scenarios_hw_hotspot_short));
        if (additionalScenariosManager.isAppLaunchTriggerEnabled())
            active.add(getString(R.string.scenarios_app_launch_short));

        if (active.isEmpty()) {
            binding.textAdditionalScenariosSummary.setText(getString(R.string.scenarios_summary_none));
        } else {
            binding.textAdditionalScenariosSummary.setText(android.text.TextUtils.join(", ", active));
        }
    }

    private void showPresetPickerDialog() {
        PresetManager presetManager = new PresetManager(this);
        int activePreset = presetManager.getActivePresetNumber();

        View view = getLayoutInflater().inflate(R.layout.dialog_single_choice, null);
        TextView titleView = view.findViewById(R.id.single_choice_title);
        RadioGroup group = view.findViewById(R.id.single_choice_group);
        titleView.setText(getString(R.string.settings_presets_title));

        int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
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
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.widget.RadioButton found = findRadioButton(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private android.graphics.drawable.GradientDrawable buildBadgeBackground() {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(32f);
        int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
        if (accent == ACCENT_CUSTOM) {
            bg.setColor(sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR));
        } else {
            bg.setColor(0xFF388E3C);
        }
        return bg;
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

        CheckBox cbHeadset = new CheckBox(this);
        cbHeadset.setText(getString(R.string.scenarios_hw_headset));
        cbHeadset.setChecked(additionalScenariosManager.isHeadsetTriggerEnabled());
        cbHeadset.setPadding(dp24, dp8, dp24, dp8);
        if (checkboxTint != null) cbHeadset.setButtonTintList(checkboxTint);
        root.addView(cbHeadset);

        CheckBox cbUsb = new CheckBox(this);
        cbUsb.setText(getString(R.string.scenarios_hw_usb));
        cbUsb.setChecked(additionalScenariosManager.isUsbTriggerEnabled());
        cbUsb.setPadding(dp24, dp8, dp24, dp8);
        if (checkboxTint != null) cbUsb.setButtonTintList(checkboxTint);
        root.addView(cbUsb);

        CheckBox cbCharger = new CheckBox(this);
        cbCharger.setText(getString(R.string.scenarios_hw_charger));
        cbCharger.setChecked(additionalScenariosManager.isChargerTriggerEnabled());
        cbCharger.setPadding(dp24, dp8, dp24, dp8);
        if (checkboxTint != null) cbCharger.setButtonTintList(checkboxTint);
        root.addView(cbCharger);

        CheckBox cbWifi = new CheckBox(this);
        cbWifi.setText(getString(R.string.scenarios_hw_wifi));
        cbWifi.setChecked(additionalScenariosManager.isWifiTriggerEnabled());
        cbWifi.setPadding(dp24, dp8, dp24, dp8);
        if (checkboxTint != null) cbWifi.setButtonTintList(checkboxTint);
        root.addView(cbWifi);

        CheckBox cbBluetooth = new CheckBox(this);
        cbBluetooth.setText(getString(R.string.scenarios_hw_bluetooth));
        cbBluetooth.setChecked(additionalScenariosManager.isBluetoothTriggerEnabled());
        cbBluetooth.setPadding(dp24, dp8, dp24, dp8);
        if (checkboxTint != null) cbBluetooth.setButtonTintList(checkboxTint);
        root.addView(cbBluetooth);

        CheckBox cbGps = new CheckBox(this);
        cbGps.setText(getString(R.string.scenarios_hw_gps));
        cbGps.setChecked(additionalScenariosManager.isGpsTriggerEnabled());
        cbGps.setPadding(dp24, dp8, dp24, dp8);
        if (checkboxTint != null) cbGps.setButtonTintList(checkboxTint);
        root.addView(cbGps);

        CheckBox cbHotspot = new CheckBox(this);
        cbHotspot.setText(getString(R.string.scenarios_hw_hotspot));
        cbHotspot.setChecked(additionalScenariosManager.isHotspotTriggerEnabled());
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
        headerLaunch.setTypeface(null, android.graphics.Typeface.BOLD);
        headerLaunch.setTextColor(colorAccent);
        headerLaunch.setPadding(dp24, dp4, dp24, dp4);
        root.addView(headerLaunch);

        CheckBox cbAppLaunch = new CheckBox(this);
        cbAppLaunch.setText(getString(R.string.scenarios_app_launch_enable));
        cbAppLaunch.setChecked(additionalScenariosManager.isAppLaunchTriggerEnabled());
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
        cbClearCache.setChecked(additionalScenariosManager.isAppLaunchClearCacheEnabled());
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
            additionalScenariosManager.setHeadsetTriggerEnabled(cbHeadset.isChecked());
            additionalScenariosManager.setUsbTriggerEnabled(cbUsb.isChecked());
            additionalScenariosManager.setChargerTriggerEnabled(cbCharger.isChecked());
            additionalScenariosManager.setWifiTriggerEnabled(cbWifi.isChecked());
            additionalScenariosManager.setBluetoothTriggerEnabled(cbBluetooth.isChecked());
            additionalScenariosManager.setGpsTriggerEnabled(cbGps.isChecked());
            additionalScenariosManager.setHotspotTriggerEnabled(cbHotspot.isChecked());
            additionalScenariosManager.updateHardwareReceiverState();

            boolean appLaunchWasOff = !additionalScenariosManager.isAppLaunchTriggerEnabled();
            additionalScenariosManager.setAppLaunchTriggerEnabled(cbAppLaunch.isChecked());
            additionalScenariosManager.setAppLaunchClearCacheEnabled(cbClearCache.isChecked());

            if (cbAppLaunch.isChecked() && appLaunchWasOff && !isAccessibilityServiceEnabled()) {
                showAccessibilityServiceRequiredDialog();
            }

            updateAdditionalScenariosSummary();
            dialog.dismiss();
        });
    }

    private void updateDialogTargetAppsList(TextView tvTargetAppsList) {
        Set<String> packages = additionalScenariosManager.getAppLaunchTriggerPackages();
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
        int pickerAccent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
        int pickerButtonColor = (pickerAccent == ACCENT_CUSTOM)
                ? ((sharedPreferences.getInt(KEY_ACCENT_ON_COLOR, ACCENT_ON_WHITE) == ACCENT_ON_BLACK) ? Color.BLACK : Color.WHITE)
                : ContextCompat.getColor(this, R.color.dialog_button_text);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(pickerButtonColor);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(pickerButtonColor);

        appManager.loadAllApps(allApps -> {
            Set<String> currentTargets = additionalScenariosManager.getAppLaunchTriggerPackages();
            FilterAppsAdapter filterAdapter = new FilterAppsAdapter(this, allApps, currentTargets);
            if (sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM) == ACCENT_CUSTOM)
                filterAdapter.setAccentColor(sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR));
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
                additionalScenariosManager.saveAppLaunchTriggerPackages(filterAdapter.getSelectedPackages());
                if (onSaved != null) onSaved.run();
                dialog.dismiss();
            });
        });
    }

    private boolean isAccessibilityServiceEnabled() {
        String expectedService = getPackageName() + "/" + AppLaunchAccessibilityService.class.getName();
        String enabledServices = android.provider.Settings.Secure.getString(
                getContentResolver(), android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabledServices != null && enabledServices.contains(expectedService);
    }

    private void showAccessibilityServiceRequiredDialog() {
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

    @Override
    protected void onResume() {
        super.onResume();
        if (binding == null) return;
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        boolean autoKill = getAutoKillPref(KEY_AUTO_KILL_ENABLED, false);
        boolean periodic = getAutoKillPref(KEY_PERIODIC_KILL_ENABLED, false);
        boolean screenOff = getAutoKillPref(KEY_KILL_ON_SCREEN_OFF, false);
        boolean ramEnabled = getAutoKillPref(KEY_RAM_THRESHOLD_ENABLED, false);
        binding.switchAutoKill.setChecked(autoKill);
        binding.switchPeriodicKill.setChecked(periodic);
        binding.switchKillScreenOff.setChecked(screenOff);
        binding.switchRamThreshold.setChecked(ramEnabled);
        updateAutomationOptionsVisibility(autoKill, periodic);
        applyServiceDependentState(autoKill);
        applyPresetActiveState(isPresetActive());
        updateRamThresholdLimitVisibility(ramEnabled && autoKill);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (binding == null) return;
        switch (key) {
            case KEY_AUTO_KILL_ENABLED: {
                boolean val = getAutoKillPref(KEY_AUTO_KILL_ENABLED, false);
                binding.switchAutoKill.setOnCheckedChangeListener(null);
                binding.switchAutoKill.setChecked(val);
                binding.switchAutoKill.setOnCheckedChangeListener(autoKillListener);
                updateAutomationOptionsVisibility(val, getAutoKillPref(KEY_PERIODIC_KILL_ENABLED, false));
                applyServiceDependentState(val);
                updateRamThresholdLimitVisibility(getAutoKillPref(KEY_RAM_THRESHOLD_ENABLED, false) && val);
                break;
            }
            case KEY_PERIODIC_KILL_ENABLED: {
                boolean val = getAutoKillPref(KEY_PERIODIC_KILL_ENABLED, false);
                binding.switchPeriodicKill.setOnCheckedChangeListener(null);
                binding.switchPeriodicKill.setChecked(val);
                binding.switchPeriodicKill.setOnCheckedChangeListener(periodicKillListener);
                updateAutomationOptionsVisibility(getAutoKillPref(KEY_AUTO_KILL_ENABLED, false), val);
                break;
            }
            case KEY_KILL_INTERVAL:
                updateKillIntervalText(getAutoKillIntPref(KEY_KILL_INTERVAL, DEFAULT_KILL_INTERVAL_MS));
                break;
            case KEY_KILL_ON_SCREEN_OFF: {
                boolean val = getAutoKillPref(KEY_KILL_ON_SCREEN_OFF, false);
                binding.switchKillScreenOff.setOnCheckedChangeListener(null);
                binding.switchKillScreenOff.setChecked(val);
                binding.switchKillScreenOff.setOnCheckedChangeListener(screenOffListener);
                break;
            }
            case KEY_RAM_THRESHOLD_ENABLED: {
                boolean val = getAutoKillPref(KEY_RAM_THRESHOLD_ENABLED, false);
                binding.switchRamThreshold.setOnCheckedChangeListener(null);
                binding.switchRamThreshold.setChecked(val);
                binding.switchRamThreshold.setOnCheckedChangeListener(ramThresholdListener);
                updateRamThresholdLimitVisibility(val && getAutoKillPref(KEY_AUTO_KILL_ENABLED, false));
                break;
            }
            case KEY_RAM_THRESHOLD:
                updateRamThresholdText(getAutoKillIntPref(KEY_RAM_THRESHOLD, DEFAULT_RAM_THRESHOLD_PERCENT));
                break;
            case KEY_AUTO_KILL_TYPE:
                updateAutoKillTypeText(autoKillManager.getAutoKillType());
                break;
            case KEY_KILL_MODE: {
                int mode = autoKillManager.getKillMode();
                binding.textKillMode.setText(mode == 0 ? R.string.settings_mode_whitelist : R.string.settings_mode_blacklist);
                binding.layoutBlacklist.setVisibility(mode == 1 ? View.VISIBLE : View.GONE);
                binding.layoutWhitelist.setVisibility(mode == 0 ? View.VISIBLE : View.GONE);
                break;
            }
            case KEY_ACTIVE_PRESET:
                applyPresetActiveState(isPresetActive());
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        binding = null;
    }
}
