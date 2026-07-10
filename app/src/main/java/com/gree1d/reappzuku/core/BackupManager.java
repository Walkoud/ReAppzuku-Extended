package com.gree1d.reappzuku.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

import com.gree1d.reappzuku.manager.PresetManager;
import com.gree1d.reappzuku.utils.PresetModel;
import com.gree1d.reappzuku.core.AppDebugManager;
import com.gree1d.reappzuku.core.AppDebugManager.Category;
import static com.gree1d.reappzuku.core.PreferenceKeys.*;

public class BackupManager {
    private static final String TAG = "BackupManager";
    private static final String KEY_BACKUP_VERSION = "backup_version";
    private static final int BACKUP_VERSION = 4;
    private static final String KEY_MANUAL_OPS_MASKS = "manual_ops_masks";
    private static final String KEY_PRESETS = "presets";
    private static final String KEY_PRESET_PREFIX = "preset_";

    private final Context context;
    private final SharedPreferences prefs;

    public BackupManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    private boolean getSafeBool(String key, boolean defVal) {
        try {
            return prefs.getBoolean(key, defVal);
        } catch (ClassCastException e) {
            AppDebugManager.w(Category.BACKUP_RESTORE, "BackupManager: getSafeBool: key=" + key + " stored as wrong type, falling back to String parse");
            String raw = prefs.getString(key, null);
            if (raw == null) return defVal;
            return Boolean.parseBoolean(raw);
        }
    }

    public String createBackupJson() {
        AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: createBackupJson: start");
        try {
            JSONObject root = new JSONObject();
            root.put(KEY_BACKUP_VERSION, BACKUP_VERSION);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: createBackupJson: version written");

            putStringSet(root, KEY_HIDDEN_APPS);
            putStringSet(root, KEY_WHITELISTED_APPS);
            putStringSet(root, KEY_BLACKLISTED_APPS);
            putStringSet(root, KEY_AUTOSTART_DISABLED_APPS);
            putStringSet(root, KEY_HARD_RESTRICTION_APPS);
            putStringSet(root, KEY_MANUAL_RESTRICTION_APPS);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: createBackupJson: app lists written");

            putManualOpsMasks(root);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: createBackupJson: manual ops masks written");

            putStringSet(root, KEY_SLEEP_MODE_APPS);
            putStringSet(root, KEY_SLEEP_MODE_APPS_PERMANENT);
            putStringSet(root, KEY_MEDIUM_RESTRICTION_APPS);
            putStringSet(root, KEY_BATTERY_WHITELIST_REMOVED);
            putStringSet(root, KEY_APP_LAUNCH_TRIGGER_PACKAGES);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: createBackupJson: extra sets written");

            root.put(KEY_KILL_MODE, prefs.getInt(KEY_KILL_MODE, 0));
            root.put(KEY_AUTO_KILL_ENABLED, getSafeBool(KEY_AUTO_KILL_ENABLED, false));
            root.put(KEY_PERIODIC_KILL_ENABLED, getSafeBool(KEY_PERIODIC_KILL_ENABLED, false));
            root.put(KEY_KILL_INTERVAL, prefs.getInt(KEY_KILL_INTERVAL, AppConstants.DEFAULT_KILL_INTERVAL_MS));
            root.put(KEY_KILL_ON_SCREEN_OFF, getSafeBool(KEY_KILL_ON_SCREEN_OFF, false));
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: createBackupJson: kill settings written");

            root.put(KEY_RAM_THRESHOLD, prefs.getInt(KEY_RAM_THRESHOLD, AppConstants.DEFAULT_RAM_THRESHOLD_PERCENT));
            root.put(KEY_RAM_THRESHOLD_ENABLED, getSafeBool(KEY_RAM_THRESHOLD_ENABLED, false));
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: createBackupJson: RAM settings written");

            root.put(KEY_SHOW_SYSTEM_APPS, getSafeBool(KEY_SHOW_SYSTEM_APPS, false));
            root.put(KEY_SHOW_PERSISTENT_APPS, getSafeBool(KEY_SHOW_PERSISTENT_APPS, false));
            root.put(KEY_THEME, prefs.getInt(KEY_THEME, androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM));
            root.put(KEY_ACCENT, prefs.getInt(KEY_ACCENT, AppConstants.ACCENT_SYSTEM));
            root.put(KEY_ACCENT_CUSTOM_COLOR, prefs.getInt(KEY_ACCENT_CUSTOM_COLOR, AppConstants.ACCENT_CUSTOM_DEFAULT_COLOR));
            root.put(KEY_ACCENT_ON_COLOR, prefs.getInt(KEY_ACCENT_ON_COLOR, AppConstants.ACCENT_ON_WHITE));
            root.put(KEY_AMOLED, getSafeBool(KEY_AMOLED, false));
            root.put(KEY_SORT_MODE, prefs.getInt(KEY_SORT_MODE, AppConstants.SORT_MODE_DEFAULT));
            root.put(KEY_NOTIFICATION_MODE, prefs.getInt(KEY_NOTIFICATION_MODE, NOTIFICATION_MODE_ALL));
            root.put(KEY_AUTO_KILL_TYPE, prefs.getInt(KEY_AUTO_KILL_TYPE, 0));
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: createBackupJson: display/UI settings written");

            root.put(KEY_SLEEP_MODE_ENABLED, getSafeBool(KEY_SLEEP_MODE_ENABLED, false));
            root.put(KEY_SLEEP_MODE_DELAY, prefs.getLong(KEY_SLEEP_MODE_DELAY, AppConstants.DEFAULT_SLEEP_MODE_DELAY_MS));
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: createBackupJson: sleep mode settings written");

            root.put(KEY_HW_TRIGGER_HEADSET, getSafeBool(KEY_HW_TRIGGER_HEADSET, false));
            root.put(KEY_HW_TRIGGER_USB, getSafeBool(KEY_HW_TRIGGER_USB, false));
            root.put(KEY_HW_TRIGGER_CHARGER, getSafeBool(KEY_HW_TRIGGER_CHARGER, false));
            root.put(KEY_HW_TRIGGER_WIFI, getSafeBool(KEY_HW_TRIGGER_WIFI, false));
            root.put(KEY_HW_TRIGGER_BLUETOOTH, getSafeBool(KEY_HW_TRIGGER_BLUETOOTH, false));
            root.put(KEY_HW_TRIGGER_GPS, getSafeBool(KEY_HW_TRIGGER_GPS, false));
            root.put(KEY_HW_TRIGGER_HOTSPOT, getSafeBool(KEY_HW_TRIGGER_HOTSPOT, false));
            root.put(KEY_APP_LAUNCH_TRIGGER_ENABLED, getSafeBool(KEY_APP_LAUNCH_TRIGGER_ENABLED, false));
            root.put(KEY_APP_LAUNCH_CLEAR_CACHE, getSafeBool(KEY_APP_LAUNCH_CLEAR_CACHE, false));
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: createBackupJson: hardware triggers written");

            // App Install Template
            root.put(KEY_TEMPLATE_ENABLED, getSafeBool(KEY_TEMPLATE_ENABLED, false));
            root.put(KEY_TEMPLATE_RESTRICTION_ENABLED, getSafeBool(KEY_TEMPLATE_RESTRICTION_ENABLED, false));
            root.put(KEY_TEMPLATE_RESTRICTION_TYPE, prefs.getString(KEY_TEMPLATE_RESTRICTION_TYPE, "SOFT"));
            root.put(KEY_TEMPLATE_SLEEP_MODE_ENABLED, getSafeBool(KEY_TEMPLATE_SLEEP_MODE_ENABLED, false));
            root.put(KEY_TEMPLATE_SLEEP_MODE_TYPE, prefs.getString(KEY_TEMPLATE_SLEEP_MODE_TYPE, "TIMER"));
            root.put(KEY_TEMPLATE_WHITELIST_ENABLED, getSafeBool(KEY_TEMPLATE_WHITELIST_ENABLED, false));
            root.put(KEY_TEMPLATE_BLACKLIST_ENABLED, getSafeBool(KEY_TEMPLATE_BLACKLIST_ENABLED, false));
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: createBackupJson: template settings written");

            putPresets(root);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: createBackupJson: presets written");

            String result = root.toString(4);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: createBackupJson: success, json length=" + result.length());
            return result;
        } catch (Exception e) {
            AppDebugManager.e(Category.BACKUP_RESTORE, "BackupManager: createBackupJson: FAILED", e);
            return null;
        }
    }

    public boolean restoreBackupJson(String json) {
        AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreBackupJson: start, json length=" + (json != null ? json.length() : -1));
        try {
            JSONObject root = new JSONObject(json);
            int version = root.optInt(KEY_BACKUP_VERSION, -1);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreBackupJson: backup version=" + version);

            SharedPreferences.Editor editor = prefs.edit();

            restoreSet(editor, root, KEY_HIDDEN_APPS);
            restoreSet(editor, root, KEY_WHITELISTED_APPS);
            restoreSet(editor, root, KEY_BLACKLISTED_APPS);
            restoreSet(editor, root, KEY_AUTOSTART_DISABLED_APPS);
            restoreSet(editor, root, KEY_HARD_RESTRICTION_APPS);
            restoreSet(editor, root, KEY_MANUAL_RESTRICTION_APPS);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreBackupJson: app lists restored");

            restoreManualOpsMasks(editor, root);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreBackupJson: manual ops masks restored");

            restoreSet(editor, root, KEY_SLEEP_MODE_APPS);
            restoreSet(editor, root, KEY_SLEEP_MODE_APPS_PERMANENT);
            restoreSet(editor, root, KEY_MEDIUM_RESTRICTION_APPS);
            restoreSet(editor, root, KEY_BATTERY_WHITELIST_REMOVED);
            restoreSet(editor, root, KEY_APP_LAUNCH_TRIGGER_PACKAGES);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreBackupJson: extra sets restored");

            restoreInt(editor, root, KEY_KILL_MODE);
            restoreBoolean(editor, root, KEY_AUTO_KILL_ENABLED);
            restoreBoolean(editor, root, KEY_PERIODIC_KILL_ENABLED);
            restoreInt(editor, root, KEY_KILL_INTERVAL);
            restoreBoolean(editor, root, KEY_KILL_ON_SCREEN_OFF);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreBackupJson: kill settings restored");

            restoreInt(editor, root, KEY_RAM_THRESHOLD);
            restoreBoolean(editor, root, KEY_RAM_THRESHOLD_ENABLED);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreBackupJson: RAM settings restored");

            restoreBoolean(editor, root, KEY_SHOW_SYSTEM_APPS);
            restoreBoolean(editor, root, KEY_SHOW_PERSISTENT_APPS);
            restoreInt(editor, root, KEY_THEME);
            restoreInt(editor, root, KEY_ACCENT);
            restoreInt(editor, root, KEY_ACCENT_CUSTOM_COLOR);
            restoreInt(editor, root, KEY_ACCENT_ON_COLOR);
            restoreBoolean(editor, root, KEY_AMOLED);
            restoreInt(editor, root, KEY_SORT_MODE);
            restoreInt(editor, root, KEY_NOTIFICATION_MODE);
            restoreInt(editor, root, KEY_AUTO_KILL_TYPE);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreBackupJson: display/UI settings restored");

            restoreBoolean(editor, root, KEY_SLEEP_MODE_ENABLED);
            restoreLong(editor, root, KEY_SLEEP_MODE_DELAY);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreBackupJson: sleep mode settings restored");

            restoreBoolean(editor, root, KEY_HW_TRIGGER_HEADSET);
            restoreBoolean(editor, root, KEY_HW_TRIGGER_USB);
            restoreBoolean(editor, root, KEY_HW_TRIGGER_CHARGER);
            restoreBoolean(editor, root, KEY_HW_TRIGGER_WIFI);
            restoreBoolean(editor, root, KEY_HW_TRIGGER_BLUETOOTH);
            restoreBoolean(editor, root, KEY_HW_TRIGGER_GPS);
            restoreBoolean(editor, root, KEY_HW_TRIGGER_HOTSPOT);
            restoreBoolean(editor, root, KEY_APP_LAUNCH_TRIGGER_ENABLED);
            restoreBoolean(editor, root, KEY_APP_LAUNCH_CLEAR_CACHE);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreBackupJson: hardware triggers restored");

            // App Install Template
            restoreBoolean(editor, root, KEY_TEMPLATE_ENABLED);
            restoreBoolean(editor, root, KEY_TEMPLATE_RESTRICTION_ENABLED);
            restoreString(editor, root, KEY_TEMPLATE_RESTRICTION_TYPE, "SOFT");
            restoreBoolean(editor, root, KEY_TEMPLATE_SLEEP_MODE_ENABLED);
            restoreString(editor, root, KEY_TEMPLATE_SLEEP_MODE_TYPE, "TIMER");
            restoreBoolean(editor, root, KEY_TEMPLATE_WHITELIST_ENABLED);
            restoreBoolean(editor, root, KEY_TEMPLATE_BLACKLIST_ENABLED);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreBackupJson: template settings restored");

            editor.apply();

            restorePresets(root);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreBackupJson: presets restored");

            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreBackupJson: success");
            return true;
        } catch (Exception e) {
            AppDebugManager.e(Category.BACKUP_RESTORE, "BackupManager: restoreBackupJson: FAILED", e);
            return false;
        }
    }

    private void restoreSet(SharedPreferences.Editor editor, JSONObject root, String key) throws Exception {
        if (root.has(key)) {
            JSONArray array = root.getJSONArray(key);
            Set<String> set = new HashSet<>();
            for (int i = 0; i < array.length(); i++) {
                set.add(array.getString(i));
            }
            editor.putStringSet(key, set);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreSet: " + key + " -> " + set.size() + " items");
        }
    }

    private void putStringSet(JSONObject root, String key) throws Exception {
        Set<String> stored = prefs.getStringSet(key, new HashSet<>());
        Set<String> set = stored == null ? new HashSet<>() : new HashSet<>(stored);
        root.put(key, new JSONArray(set));
        AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: putStringSet: " + key + " -> " + set.size() + " items");
    }

    private void putManualOpsMasks(JSONObject root) throws Exception {
        Set<String> manualPackages = prefs.getStringSet(KEY_MANUAL_RESTRICTION_APPS, new java.util.HashSet<>());
        if (manualPackages == null) manualPackages = new java.util.HashSet<>();
        JSONObject masks = new JSONObject();
        for (String pkg : manualPackages) {
            int mask = prefs.getInt(KEY_MANUAL_OPS_PREFIX + pkg, 0x01);
            masks.put(pkg, mask);
        }
        root.put(KEY_MANUAL_OPS_MASKS, masks);
        AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: putManualOpsMasks: " + manualPackages.size() + " packages");
    }

    private void restoreManualOpsMasks(SharedPreferences.Editor editor, JSONObject root) throws Exception {
        if (!root.has(KEY_MANUAL_OPS_MASKS)) return;
        JSONObject masks = root.getJSONObject(KEY_MANUAL_OPS_MASKS);
        java.util.Iterator<String> keys = masks.keys();
        int count = 0;
        while (keys.hasNext()) {
            String pkg = keys.next();
            editor.putInt(KEY_MANUAL_OPS_PREFIX + pkg, masks.getInt(pkg));
            count++;
        }
        AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreManualOpsMasks: " + count + " packages");
    }

    private void putPresets(JSONObject root) throws Exception {
        PresetManager presetManager = new PresetManager(context);
        JSONObject presets = new JSONObject();
        for (int presetNumber : new int[]{ PresetModel.PRESET_1, PresetModel.PRESET_2 }) {
            PresetModel model = presetManager.loadPreset(presetNumber);
            if (model == null) {
                AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: putPresets: preset #" + presetNumber + " not set, skipping");
                continue;
            }
            presets.put(KEY_PRESET_PREFIX + presetNumber, model.toJson());
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: putPresets: preset #" + presetNumber + " written, name=" + model.name);
        }
        root.put(KEY_PRESETS, presets);
    }

    private void restorePresets(JSONObject root) throws Exception {
        if (!root.has(KEY_PRESETS)) {
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restorePresets: no presets in backup, skipping");
            return;
        }
        JSONObject presets = root.getJSONObject(KEY_PRESETS);
        PresetManager presetManager = new PresetManager(context);
        for (int presetNumber : new int[]{ PresetModel.PRESET_1, PresetModel.PRESET_2 }) {
            String key = KEY_PRESET_PREFIX + presetNumber;
            if (!presets.has(key)) {
                AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restorePresets: preset #" + presetNumber + " not in backup, skipping");
                continue;
            }
            PresetModel model = PresetModel.fromJson(presetNumber, presets.getJSONObject(key));
            presetManager.savePreset(model);
            presetManager.scheduleAlarms(model);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restorePresets: preset #" + presetNumber + " restored, name=" + model.name);
        }
        presetManager.checkAndApplyCurrentPreset();
    }

    private void restoreBoolean(SharedPreferences.Editor editor, JSONObject root, String key) throws Exception {
        if (root.has(key)) {
            boolean value = root.getBoolean(key);
            editor.putBoolean(key, value);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreBoolean: " + key + "=" + value);
        }
    }

    private void restoreInt(SharedPreferences.Editor editor, JSONObject root, String key) throws Exception {
        if (root.has(key)) {
            int value = root.getInt(key);
            editor.putInt(key, value);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreInt: " + key + "=" + value);
        }
    }

    private void restoreLong(SharedPreferences.Editor editor, JSONObject root, String key) throws Exception {
        if (root.has(key)) {
            long value = root.getLong(key);
            editor.putLong(key, value);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreLong: " + key + "=" + value);
        }
    }

    private void restoreString(SharedPreferences.Editor editor, JSONObject root, String key, String defVal) throws Exception {
        if (root.has(key)) {
            String value = root.getString(key);
            editor.putString(key, value);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreString: " + key + "=" + value);
        }
    }
}
