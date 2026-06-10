package com.gree1d.reappzuku;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

import static com.gree1d.reappzuku.PreferenceKeys.*;

public class BackupManager {
    private static final String TAG = "BackupManager";
    private static final String KEY_BACKUP_VERSION = "backup_version";
    private static final int BACKUP_VERSION = 4;
    private static final String KEY_MANUAL_OPS_MASKS = "manual_ops_masks";

    private final SharedPreferences prefs;

    public BackupManager(Context context) {
        this.prefs = context.getSharedPreferences(PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    private boolean getSafeBool(String key, boolean defVal) {
        try {
            return prefs.getBoolean(key, defVal);
        } catch (ClassCastException e) {
            Log.w(TAG, "getSafeBool: key=" + key + " stored as wrong type, falling back to String parse");
            String raw = prefs.getString(key, null);
            if (raw == null) return defVal;
            return Boolean.parseBoolean(raw);
        }
    }

    public String createBackupJson() {
        Log.d(TAG, "createBackupJson: start");
        try {
            JSONObject root = new JSONObject();
            root.put(KEY_BACKUP_VERSION, BACKUP_VERSION);
            Log.d(TAG, "createBackupJson: version written");

            putStringSet(root, KEY_HIDDEN_APPS);
            putStringSet(root, KEY_WHITELISTED_APPS);
            putStringSet(root, KEY_BLACKLISTED_APPS);
            putStringSet(root, KEY_AUTOSTART_DISABLED_APPS);
            putStringSet(root, KEY_HARD_RESTRICTION_APPS);
            putStringSet(root, KEY_MANUAL_RESTRICTION_APPS);
            Log.d(TAG, "createBackupJson: app lists written");

            putManualOpsMasks(root);
            Log.d(TAG, "createBackupJson: manual ops masks written");

            putStringSet(root, KEY_SLEEP_MODE_APPS);
            putStringSet(root, KEY_SLEEP_MODE_APPS_PERMANENT);
            putStringSet(root, KEY_MEDIUM_RESTRICTION_APPS);
            putStringSet(root, KEY_BATTERY_WHITELIST_REMOVED);
            putStringSet(root, KEY_APP_LAUNCH_TRIGGER_PACKAGES);
            Log.d(TAG, "createBackupJson: extra sets written");

            root.put(KEY_KILL_MODE, prefs.getInt(KEY_KILL_MODE, 0));
            root.put(KEY_AUTO_KILL_ENABLED, getSafeBool(KEY_AUTO_KILL_ENABLED, false));
            root.put(KEY_PERIODIC_KILL_ENABLED, getSafeBool(KEY_PERIODIC_KILL_ENABLED, false));
            root.put(KEY_KILL_INTERVAL, prefs.getInt(KEY_KILL_INTERVAL, AppConstants.DEFAULT_KILL_INTERVAL_MS));
            root.put(KEY_KILL_ON_SCREEN_OFF, getSafeBool(KEY_KILL_ON_SCREEN_OFF, false));
            Log.d(TAG, "createBackupJson: kill settings written");

            root.put(KEY_RAM_THRESHOLD, prefs.getInt(KEY_RAM_THRESHOLD, AppConstants.DEFAULT_RAM_THRESHOLD_PERCENT));
            root.put(KEY_RAM_THRESHOLD_ENABLED, getSafeBool(KEY_RAM_THRESHOLD_ENABLED, false));
            Log.d(TAG, "createBackupJson: RAM settings written");

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
            Log.d(TAG, "createBackupJson: display/UI settings written");

            root.put(KEY_SLEEP_MODE_ENABLED, getSafeBool(KEY_SLEEP_MODE_ENABLED, false));
            root.put(KEY_SLEEP_MODE_DELAY, prefs.getLong(KEY_SLEEP_MODE_DELAY, AppConstants.DEFAULT_SLEEP_MODE_DELAY_MS));
            Log.d(TAG, "createBackupJson: sleep mode settings written");

            root.put(KEY_HW_TRIGGER_HEADSET, getSafeBool(KEY_HW_TRIGGER_HEADSET, false));
            root.put(KEY_HW_TRIGGER_USB, getSafeBool(KEY_HW_TRIGGER_USB, false));
            root.put(KEY_HW_TRIGGER_CHARGER, getSafeBool(KEY_HW_TRIGGER_CHARGER, false));
            root.put(KEY_HW_TRIGGER_WIFI, getSafeBool(KEY_HW_TRIGGER_WIFI, false));
            root.put(KEY_HW_TRIGGER_BLUETOOTH, getSafeBool(KEY_HW_TRIGGER_BLUETOOTH, false));
            root.put(KEY_HW_TRIGGER_GPS, getSafeBool(KEY_HW_TRIGGER_GPS, false));
            root.put(KEY_HW_TRIGGER_HOTSPOT, getSafeBool(KEY_HW_TRIGGER_HOTSPOT, false));
            root.put(KEY_APP_LAUNCH_TRIGGER_ENABLED, getSafeBool(KEY_APP_LAUNCH_TRIGGER_ENABLED, false));
            root.put(KEY_APP_LAUNCH_CLEAR_CACHE, getSafeBool(KEY_APP_LAUNCH_CLEAR_CACHE, false));
            Log.d(TAG, "createBackupJson: hardware triggers written");

            String result = root.toString(4);
            Log.d(TAG, "createBackupJson: success, json length=" + result.length());
            return result;
        } catch (Exception e) {
            Log.e(TAG, "createBackupJson: FAILED", e);
            return null;
        }
    }

    public boolean restoreBackupJson(String json) {
        Log.d(TAG, "restoreBackupJson: start, json length=" + (json != null ? json.length() : -1));
        try {
            JSONObject root = new JSONObject(json);
            int version = root.optInt(KEY_BACKUP_VERSION, -1);
            Log.d(TAG, "restoreBackupJson: backup version=" + version);

            SharedPreferences.Editor editor = prefs.edit();

            restoreSet(editor, root, KEY_HIDDEN_APPS);
            restoreSet(editor, root, KEY_WHITELISTED_APPS);
            restoreSet(editor, root, KEY_BLACKLISTED_APPS);
            restoreSet(editor, root, KEY_AUTOSTART_DISABLED_APPS);
            restoreSet(editor, root, KEY_HARD_RESTRICTION_APPS);
            restoreSet(editor, root, KEY_MANUAL_RESTRICTION_APPS);
            Log.d(TAG, "restoreBackupJson: app lists restored");

            restoreManualOpsMasks(editor, root);
            Log.d(TAG, "restoreBackupJson: manual ops masks restored");

            restoreSet(editor, root, KEY_SLEEP_MODE_APPS);
            restoreSet(editor, root, KEY_SLEEP_MODE_APPS_PERMANENT);
            restoreSet(editor, root, KEY_MEDIUM_RESTRICTION_APPS);
            restoreSet(editor, root, KEY_BATTERY_WHITELIST_REMOVED);
            restoreSet(editor, root, KEY_APP_LAUNCH_TRIGGER_PACKAGES);
            Log.d(TAG, "restoreBackupJson: extra sets restored");

            restoreInt(editor, root, KEY_KILL_MODE);
            restoreBoolean(editor, root, KEY_AUTO_KILL_ENABLED);
            restoreBoolean(editor, root, KEY_PERIODIC_KILL_ENABLED);
            restoreInt(editor, root, KEY_KILL_INTERVAL);
            restoreBoolean(editor, root, KEY_KILL_ON_SCREEN_OFF);
            Log.d(TAG, "restoreBackupJson: kill settings restored");

            restoreInt(editor, root, KEY_RAM_THRESHOLD);
            restoreBoolean(editor, root, KEY_RAM_THRESHOLD_ENABLED);
            Log.d(TAG, "restoreBackupJson: RAM settings restored");

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
            Log.d(TAG, "restoreBackupJson: display/UI settings restored");

            restoreBoolean(editor, root, KEY_SLEEP_MODE_ENABLED);
            restoreLong(editor, root, KEY_SLEEP_MODE_DELAY);
            Log.d(TAG, "restoreBackupJson: sleep mode settings restored");

            restoreBoolean(editor, root, KEY_HW_TRIGGER_HEADSET);
            restoreBoolean(editor, root, KEY_HW_TRIGGER_USB);
            restoreBoolean(editor, root, KEY_HW_TRIGGER_CHARGER);
            restoreBoolean(editor, root, KEY_HW_TRIGGER_WIFI);
            restoreBoolean(editor, root, KEY_HW_TRIGGER_BLUETOOTH);
            restoreBoolean(editor, root, KEY_HW_TRIGGER_GPS);
            restoreBoolean(editor, root, KEY_HW_TRIGGER_HOTSPOT);
            restoreBoolean(editor, root, KEY_APP_LAUNCH_TRIGGER_ENABLED);
            restoreBoolean(editor, root, KEY_APP_LAUNCH_CLEAR_CACHE);
            Log.d(TAG, "restoreBackupJson: hardware triggers restored");

            editor.apply();
            Log.d(TAG, "restoreBackupJson: success");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "restoreBackupJson: FAILED", e);
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
            Log.d(TAG, "restoreSet: " + key + " -> " + set.size() + " items");
        }
    }

    private void putStringSet(JSONObject root, String key) throws Exception {
        Set<String> stored = prefs.getStringSet(key, new HashSet<>());
        Set<String> set = stored == null ? new HashSet<>() : new HashSet<>(stored);
        root.put(key, new JSONArray(set));
        Log.d(TAG, "putStringSet: " + key + " -> " + set.size() + " items");
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
        Log.d(TAG, "putManualOpsMasks: " + manualPackages.size() + " packages");
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
        Log.d(TAG, "restoreManualOpsMasks: " + count + " packages");
    }

    private void restoreBoolean(SharedPreferences.Editor editor, JSONObject root, String key) throws Exception {
        if (root.has(key)) {
            boolean value = root.getBoolean(key);
            editor.putBoolean(key, value);
            Log.d(TAG, "restoreBoolean: " + key + "=" + value);
        }
    }

    private void restoreInt(SharedPreferences.Editor editor, JSONObject root, String key) throws Exception {
        if (root.has(key)) {
            int value = root.getInt(key);
            editor.putInt(key, value);
            Log.d(TAG, "restoreInt: " + key + "=" + value);
        }
    }

    private void restoreLong(SharedPreferences.Editor editor, JSONObject root, String key) throws Exception {
        if (root.has(key)) {
            long value = root.getLong(key);
            editor.putLong(key, value);
            Log.d(TAG, "restoreLong: " + key + "=" + value);
        }
    }
}
