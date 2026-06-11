package com.gree1d.reappzuku;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import static com.gree1d.reappzuku.PreferenceKeys.*;

public class PresetManager {

    private static final String TAG = "PresetManager";

    private static final String FILE_PRESET_1 = "preset1.json";
    private static final String FILE_PRESET_2 = "preset2.json";

    private static final String ACTION_PRESET_ACTIVATE = "com.gree1d.reappzuku.PRESET_ACTIVATE";
    private static final String ACTION_PRESET_DEACTIVATE = "com.gree1d.reappzuku.PRESET_DEACTIVATE";
    private static final String EXTRA_PRESET_NUMBER = "preset_number";

    private static final String KEY_ACTIVE_PRESET = "active_preset_number";
    private static final String KEY_BACKUP_PREFIX = "preset_backup_";

    private static final int REQUEST_CODE_ACTIVATE_1 = 1001;
    private static final int REQUEST_CODE_DEACTIVATE_1 = 1002;
    private static final int REQUEST_CODE_ACTIVATE_2 = 1003;
    private static final int REQUEST_CODE_DEACTIVATE_2 = 1004;

    private final Context context;
    private final SharedPreferences prefs;

    public PresetManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public void savePreset(PresetModel model) {
        String fileName = model.presetNumber == PresetModel.PRESET_1 ? FILE_PRESET_1 : FILE_PRESET_2;
        File file = new File(context.getFilesDir(), fileName);
        Log.d(TAG, "savePreset #" + model.presetNumber + " name=" + model.name
                + " file=" + file.getAbsolutePath());
        try (FileWriter writer = new FileWriter(file)) {
            String json = model.toJson().toString();
            writer.write(json);
            Log.d(TAG, "savePreset #" + model.presetNumber + " OK | " + json);
        } catch (IOException | JSONException e) {
            Log.e(TAG, "savePreset #" + model.presetNumber + " FAILED", e);
        }
    }

    public PresetModel loadPreset(int presetNumber) {
        String fileName = presetNumber == PresetModel.PRESET_1 ? FILE_PRESET_1 : FILE_PRESET_2;
        File file = new File(context.getFilesDir(), fileName);
        Log.d(TAG, "loadPreset #" + presetNumber + " path=" + file.getAbsolutePath()
                + " exists=" + file.exists());
        if (!file.exists()) return null;
        try (FileReader reader = new FileReader(file)) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
            PresetModel model = PresetModel.fromJson(new JSONObject(sb.toString()));
            Log.d(TAG, "loadPreset #" + presetNumber + " OK | name=" + model.name
                    + " start=" + model.startHour + ":" + String.format("%02d", model.startMinute)
                    + " end=" + model.endHour + ":" + String.format("%02d", model.endMinute)
                    + " killMode=" + model.killMode + " killType=" + model.autoKillType
                    + " interval=" + model.killInterval
                    + " screenOff=" + model.killOnScreenOff
                    + " ramEnabled=" + model.ramThresholdEnabled + " ramThreshold=" + model.ramThreshold
                    + " whitelist=" + model.whitelistedApps.size()
                    + " blacklist=" + model.blacklistedApps.size());
            return model;
        } catch (IOException | JSONException e) {
            Log.e(TAG, "loadPreset #" + presetNumber + " FAILED", e);
            return null;
        }
    }

    public void deletePreset(int presetNumber) {
        String fileName = presetNumber == PresetModel.PRESET_1 ? FILE_PRESET_1 : FILE_PRESET_2;
        File file = new File(context.getFilesDir(), fileName);
        boolean deleted = false;
        if (file.exists()) deleted = file.delete();
        Log.d(TAG, "deletePreset #" + presetNumber + " fileDeleted=" + deleted
                + " wasActive=" + (getActivePresetNumber() == presetNumber));
        cancelAlarms(presetNumber);
        if (getActivePresetNumber() == presetNumber) {
            restoreBackup();
            clearActivePreset();
        }
    }

    public boolean presetExists(int presetNumber) {
        String fileName = presetNumber == PresetModel.PRESET_1 ? FILE_PRESET_1 : FILE_PRESET_2;
        return new File(context.getFilesDir(), fileName).exists();
    }

    public int getActivePresetNumber() {
        return prefs.getInt(KEY_ACTIVE_PRESET, 0);
    }

    private void setActivePresetNumber(int number) {
        prefs.edit().putInt(KEY_ACTIVE_PRESET, number).apply();
    }

    private void clearActivePreset() {
        prefs.edit().remove(KEY_ACTIVE_PRESET).apply();
    }

    public void activatePreset(int presetNumber) {
        Log.d(TAG, "activatePreset #" + presetNumber + " | currentActive=" + getActivePresetNumber());
        PresetModel model = loadPreset(presetNumber);
        if (model == null) {
            Log.w(TAG, "activatePreset #" + presetNumber + " ABORTED — preset file not found");
            return;
        }

        int currentActive = getActivePresetNumber();
        if (currentActive != 0 && currentActive != presetNumber) {
            Log.d(TAG, "activatePreset: replacing active preset #" + currentActive + " → restoring backup first");
            restoreBackup();
        }
        if (currentActive == 0) {
            Log.d(TAG, "activatePreset: no preset was active — saving backup of current settings");
            saveBackup();
        }

        applyPreset(model);
        setActivePresetNumber(presetNumber);
        Log.d(TAG, "activatePreset #" + presetNumber + " DONE");
    }

    public void deactivatePreset(int presetNumber) {
        int currentActive = getActivePresetNumber();
        Log.d(TAG, "deactivatePreset #" + presetNumber + " | currentActive=" + currentActive);
        if (currentActive != presetNumber) {
            Log.w(TAG, "deactivatePreset #" + presetNumber + " SKIPPED — not the active preset");
            return;
        }
        restoreBackup();
        clearActivePreset();
        Log.d(TAG, "deactivatePreset #" + presetNumber + " DONE");
    }

    private void applyPreset(PresetModel model) {
        Log.d(TAG, "applyPreset #" + model.presetNumber
                + " | autoKillEnabled=" + model.autoKillEnabled
                + " periodicEnabled=" + model.periodicKillEnabled
                + " interval=" + model.killInterval
                + " screenOff=" + model.killOnScreenOff
                + " killMode=" + model.killMode
                + " killType=" + model.autoKillType
                + " ramEnabled=" + model.ramThresholdEnabled
                + " ramThreshold=" + model.ramThreshold
                + " whitelist=" + model.whitelistedApps
                + " blacklist=" + model.blacklistedApps);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_AUTO_KILL_ENABLED, model.autoKillEnabled);
        editor.putBoolean(KEY_PERIODIC_KILL_ENABLED, model.periodicKillEnabled);
        editor.putInt(KEY_KILL_INTERVAL, model.killInterval);
        editor.putBoolean(KEY_KILL_ON_SCREEN_OFF, model.killOnScreenOff);
        editor.putBoolean(KEY_RAM_THRESHOLD_ENABLED, model.ramThresholdEnabled);
        editor.putInt(KEY_RAM_THRESHOLD, model.ramThreshold);
        editor.putInt(KEY_AUTO_KILL_TYPE, model.autoKillType);
        editor.putInt(KEY_KILL_MODE, model.killMode);
        editor.putStringSet(KEY_WHITELISTED_APPS, new HashSet<>(model.whitelistedApps));
        editor.putStringSet(KEY_BLACKLISTED_APPS, new HashSet<>(model.blacklistedApps));
        editor.apply();
        Log.d(TAG, "applyPreset #" + model.presetNumber + " prefs written — rescheduling worker");
        rescheduleWorker();
    }

    private void saveBackup() {
        boolean autoKill = prefs.getBoolean(KEY_AUTO_KILL_ENABLED, false);
        boolean periodic = prefs.getBoolean(KEY_PERIODIC_KILL_ENABLED, false);
        int interval = prefs.getInt(KEY_KILL_INTERVAL, 15);
        boolean screenOff = prefs.getBoolean(KEY_KILL_ON_SCREEN_OFF, false);
        boolean ramEnabled = prefs.getBoolean(KEY_RAM_THRESHOLD_ENABLED, false);
        int ramThreshold = prefs.getInt(KEY_RAM_THRESHOLD, 80);
        int killType = prefs.getInt(KEY_AUTO_KILL_TYPE, 0);
        int killMode = prefs.getInt(KEY_KILL_MODE, 1);
        Set<String> whitelist = prefs.getStringSet(KEY_WHITELISTED_APPS, new HashSet<>());
        Set<String> blacklist = prefs.getStringSet(KEY_BLACKLISTED_APPS, new HashSet<>());

        Log.d(TAG, "saveBackup | autoKill=" + autoKill + " periodic=" + periodic
                + " interval=" + interval + " screenOff=" + screenOff
                + " killMode=" + killMode + " killType=" + killType
                + " ramEnabled=" + ramEnabled + " ramThreshold=" + ramThreshold
                + " whitelist=" + whitelist + " blacklist=" + blacklist);

        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_BACKUP_PREFIX + KEY_AUTO_KILL_ENABLED, autoKill);
        editor.putBoolean(KEY_BACKUP_PREFIX + KEY_PERIODIC_KILL_ENABLED, periodic);
        editor.putInt(KEY_BACKUP_PREFIX + KEY_KILL_INTERVAL, interval);
        editor.putBoolean(KEY_BACKUP_PREFIX + KEY_KILL_ON_SCREEN_OFF, screenOff);
        editor.putBoolean(KEY_BACKUP_PREFIX + KEY_RAM_THRESHOLD_ENABLED, ramEnabled);
        editor.putInt(KEY_BACKUP_PREFIX + KEY_RAM_THRESHOLD, ramThreshold);
        editor.putInt(KEY_BACKUP_PREFIX + KEY_AUTO_KILL_TYPE, killType);
        editor.putInt(KEY_BACKUP_PREFIX + KEY_KILL_MODE, killMode);
        editor.putStringSet(KEY_BACKUP_PREFIX + KEY_WHITELISTED_APPS, new HashSet<>(whitelist));
        editor.putStringSet(KEY_BACKUP_PREFIX + KEY_BLACKLISTED_APPS, new HashSet<>(blacklist));
        editor.apply();
        Log.d(TAG, "saveBackup DONE");
    }

    private void restoreBackup() {
        boolean hasBackup = prefs.contains(KEY_BACKUP_PREFIX + KEY_AUTO_KILL_ENABLED);
        Log.d(TAG, "restoreBackup | hasBackup=" + hasBackup);
        if (!hasBackup) return;

        boolean autoKill = prefs.getBoolean(KEY_BACKUP_PREFIX + KEY_AUTO_KILL_ENABLED, false);
        boolean periodic = prefs.getBoolean(KEY_BACKUP_PREFIX + KEY_PERIODIC_KILL_ENABLED, false);
        int interval = prefs.getInt(KEY_BACKUP_PREFIX + KEY_KILL_INTERVAL, 15);
        boolean screenOff = prefs.getBoolean(KEY_BACKUP_PREFIX + KEY_KILL_ON_SCREEN_OFF, false);
        boolean ramEnabled = prefs.getBoolean(KEY_BACKUP_PREFIX + KEY_RAM_THRESHOLD_ENABLED, false);
        int ramThreshold = prefs.getInt(KEY_BACKUP_PREFIX + KEY_RAM_THRESHOLD, 80);
        int killType = prefs.getInt(KEY_BACKUP_PREFIX + KEY_AUTO_KILL_TYPE, 0);
        int killMode = prefs.getInt(KEY_BACKUP_PREFIX + KEY_KILL_MODE, 1);
        Set<String> whitelist = prefs.getStringSet(KEY_BACKUP_PREFIX + KEY_WHITELISTED_APPS, new HashSet<>());
        Set<String> blacklist = prefs.getStringSet(KEY_BACKUP_PREFIX + KEY_BLACKLISTED_APPS, new HashSet<>());

        Log.d(TAG, "restoreBackup values | autoKill=" + autoKill + " periodic=" + periodic
                + " interval=" + interval + " screenOff=" + screenOff
                + " killMode=" + killMode + " killType=" + killType
                + " ramEnabled=" + ramEnabled + " ramThreshold=" + ramThreshold
                + " whitelist=" + whitelist + " blacklist=" + blacklist);

        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_AUTO_KILL_ENABLED, autoKill);
        editor.putBoolean(KEY_PERIODIC_KILL_ENABLED, periodic);
        editor.putInt(KEY_KILL_INTERVAL, interval);
        editor.putBoolean(KEY_KILL_ON_SCREEN_OFF, screenOff);
        editor.putBoolean(KEY_RAM_THRESHOLD_ENABLED, ramEnabled);
        editor.putInt(KEY_RAM_THRESHOLD, ramThreshold);
        editor.putInt(KEY_AUTO_KILL_TYPE, killType);
        editor.putInt(KEY_KILL_MODE, killMode);
        editor.putStringSet(KEY_WHITELISTED_APPS, new HashSet<>(whitelist));
        editor.putStringSet(KEY_BLACKLISTED_APPS, new HashSet<>(blacklist));

        editor.remove(KEY_BACKUP_PREFIX + KEY_AUTO_KILL_ENABLED);
        editor.remove(KEY_BACKUP_PREFIX + KEY_PERIODIC_KILL_ENABLED);
        editor.remove(KEY_BACKUP_PREFIX + KEY_KILL_INTERVAL);
        editor.remove(KEY_BACKUP_PREFIX + KEY_KILL_ON_SCREEN_OFF);
        editor.remove(KEY_BACKUP_PREFIX + KEY_RAM_THRESHOLD_ENABLED);
        editor.remove(KEY_BACKUP_PREFIX + KEY_RAM_THRESHOLD);
        editor.remove(KEY_BACKUP_PREFIX + KEY_AUTO_KILL_TYPE);
        editor.remove(KEY_BACKUP_PREFIX + KEY_KILL_MODE);
        editor.remove(KEY_BACKUP_PREFIX + KEY_WHITELISTED_APPS);
        editor.remove(KEY_BACKUP_PREFIX + KEY_BLACKLISTED_APPS);
        editor.apply();

        Log.d(TAG, "restoreBackup DONE — rescheduling worker");
        rescheduleWorker();
    }

    private void rescheduleWorker() {
        boolean autoKillEnabled = prefs.getBoolean(KEY_AUTO_KILL_ENABLED, false);
        boolean periodicEnabled = prefs.getBoolean(KEY_PERIODIC_KILL_ENABLED, false);
        int interval = prefs.getInt(KEY_KILL_INTERVAL, 15);
        Log.d(TAG, "rescheduleWorker | autoKill=" + autoKillEnabled
                + " periodic=" + periodicEnabled + " interval=" + interval);
        AutoKillWorker.cancel(context);
        if (autoKillEnabled && periodicEnabled) {
            AutoKillWorker.schedule(context);
            Log.d(TAG, "rescheduleWorker — worker scheduled with interval=" + interval);
        } else {
            Log.d(TAG, "rescheduleWorker — worker cancelled (autoKill or periodic disabled)");
        }
    }

    public void scheduleAlarms(PresetModel model) {
        cancelAlarms(model.presetNumber);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e(TAG, "scheduleAlarms #" + model.presetNumber + " — AlarmManager is null");
            return;
        }

        PendingIntent activateIntent = buildPendingIntent(model.presetNumber, ACTION_PRESET_ACTIVATE);
        PendingIntent deactivateIntent = buildPendingIntent(model.presetNumber, ACTION_PRESET_DEACTIVATE);

        long activateTime = nextAlarmTime(model.startHour, model.startMinute);
        long deactivateTime = nextAlarmTime(model.endHour, model.endMinute);

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, activateTime, activateIntent);
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deactivateTime, deactivateIntent);

        Log.d(TAG, "scheduleAlarms #" + model.presetNumber
                + " | activateAt=" + model.startHour + ":" + String.format("%02d", model.startMinute)
                + " (ms=" + activateTime + ")"
                + " | deactivateAt=" + model.endHour + ":" + String.format("%02d", model.endMinute)
                + " (ms=" + deactivateTime + ")");
    }

    public void cancelAlarms(int presetNumber) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e(TAG, "cancelAlarms #" + presetNumber + " — AlarmManager is null");
            return;
        }

        PendingIntent activateIntent = buildPendingIntent(presetNumber, ACTION_PRESET_ACTIVATE);
        PendingIntent deactivateIntent = buildPendingIntent(presetNumber, ACTION_PRESET_DEACTIVATE);

        alarmManager.cancel(activateIntent);
        alarmManager.cancel(deactivateIntent);

        Log.d(TAG, "cancelAlarms #" + presetNumber + " DONE");
    }

    private PendingIntent buildPendingIntent(int presetNumber, String action) {
        Intent intent = new Intent(context, PresetReceiver.class);
        intent.setAction(action);
        intent.putExtra(EXTRA_PRESET_NUMBER, presetNumber);

        int requestCode;
        if (presetNumber == PresetModel.PRESET_1) {
            requestCode = action.equals(ACTION_PRESET_ACTIVATE)
                    ? REQUEST_CODE_ACTIVATE_1 : REQUEST_CODE_DEACTIVATE_1;
        } else {
            requestCode = action.equals(ACTION_PRESET_ACTIVATE)
                    ? REQUEST_CODE_ACTIVATE_2 : REQUEST_CODE_DEACTIVATE_2;
        }

        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private long nextAlarmTime(int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        return calendar.getTimeInMillis();
    }

    public boolean isCurrentlyActive(PresetModel model) {
        Calendar now = Calendar.getInstance();
        int nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int startMinutes = model.getStartTotalMinutes();
        int endMinutes = model.getEndTotalMinutes();

        boolean active;
        if (endMinutes <= startMinutes) {
            active = nowMinutes >= startMinutes || nowMinutes < endMinutes;
        } else {
            active = nowMinutes >= startMinutes && nowMinutes < endMinutes;
        }

        Log.d(TAG, "isCurrentlyActive #" + model.presetNumber
                + " | now=" + now.get(Calendar.HOUR_OF_DAY) + ":" + String.format("%02d", now.get(Calendar.MINUTE))
                + " (" + nowMinutes + "min)"
                + " range=" + model.startHour + ":" + String.format("%02d", model.startMinute)
                + "–" + model.endHour + ":" + String.format("%02d", model.endMinute)
                + " crossesMidnight=" + (endMinutes <= startMinutes)
                + " → active=" + active);
        return active;
    }

    public void rescheduleNextDayAlarm(int presetNumber, String action, int hour, int minute) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        PendingIntent pi = buildPendingIntent(presetNumber, action);
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pi);
        Log.d(TAG, "rescheduleNextDayAlarm #" + presetNumber + " action=" + action
                + " at=" + hour + ":" + String.format("%02d", minute)
                + " (tomorrow, ms=" + calendar.getTimeInMillis() + ")");
    }

    public void checkAndApplyCurrentPreset() {
        Log.d(TAG, "checkAndApplyCurrentPreset | currentActive=" + getActivePresetNumber());
        for (int number : new int[]{PresetModel.PRESET_1, PresetModel.PRESET_2}) {
            PresetModel model = loadPreset(number);
            if (model == null) {
                Log.d(TAG, "checkAndApplyCurrentPreset | preset #" + number + " not found, skipping");
                continue;
            }
            if (isCurrentlyActive(model)) {
                if (getActivePresetNumber() != number) {
                    Log.d(TAG, "checkAndApplyCurrentPreset | preset #" + number + " is in its time window — activating");
                    activatePreset(number);
                } else {
                    Log.d(TAG, "checkAndApplyCurrentPreset | preset #" + number + " already active — skipping");
                }
                return;
            }
        }

        int currentActive = getActivePresetNumber();
        if (currentActive != 0) {
            PresetModel active = loadPreset(currentActive);
            if (active == null || !isCurrentlyActive(active)) {
                Log.d(TAG, "checkAndApplyCurrentPreset | preset #" + currentActive
                        + " is outside its time window — deactivating");
                deactivatePreset(currentActive);
            }
        } else {
            Log.d(TAG, "checkAndApplyCurrentPreset | no preset active and none in window — nothing to do");
        }
    }

    public static class PresetReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                Log.w(TAG, "PresetReceiver: received null intent or action");
                return;
            }
            int presetNumber = intent.getIntExtra(EXTRA_PRESET_NUMBER, 0);
            Log.d(TAG, "PresetReceiver.onReceive | action=" + intent.getAction()
                    + " presetNumber=" + presetNumber);
            if (presetNumber == 0) {
                Log.w(TAG, "PresetReceiver: missing preset_number extra");
                return;
            }

            PresetManager manager = new PresetManager(context);

            if (ACTION_PRESET_ACTIVATE.equals(intent.getAction())) {
                manager.activatePreset(presetNumber);
                PresetModel model = manager.loadPreset(presetNumber);
                if (model != null) {
                    manager.rescheduleNextDayAlarm(presetNumber, ACTION_PRESET_ACTIVATE,
                            model.startHour, model.startMinute);
                }
            } else if (ACTION_PRESET_DEACTIVATE.equals(intent.getAction())) {
                manager.deactivatePreset(presetNumber);
                PresetModel model = manager.loadPreset(presetNumber);
                if (model != null) {
                    manager.rescheduleNextDayAlarm(presetNumber, ACTION_PRESET_DEACTIVATE,
                            model.endHour, model.endMinute);
                }
            }
        }
    }
}
