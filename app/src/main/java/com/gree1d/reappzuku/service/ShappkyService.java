package com.gree1d.reappzuku.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;

import com.gree1d.reappzuku.core.ShellManager;
import com.gree1d.reappzuku.manager.BackgroundAppManager;
import com.gree1d.reappzuku.manager.AutoKillManager;
import com.gree1d.reappzuku.manager.SleepModeManager;
import com.gree1d.reappzuku.manager.CollectStatsManager;
import com.gree1d.reappzuku.service.CollectStatsReceiver;
import com.gree1d.reappzuku.manager.RestrictionsScheduler;
import com.gree1d.reappzuku.manager.RestrictionsWatchdogManager;
import com.gree1d.reappzuku.manager.AdditionalScenariosManager;
import com.gree1d.reappzuku.manager.RamKillShortcutManager;
import com.gree1d.reappzuku.manager.PresetManager;
import com.gree1d.reappzuku.manager.UpdateChecker;
import com.gree1d.reappzuku.R;
import com.gree1d.reappzuku.utils.AppzukuWidget;
import com.gree1d.reappzuku.core.AppDebugManager;
import com.gree1d.reappzuku.core.AppDebugManager.Category;

import static com.gree1d.reappzuku.core.PreferenceKeys.*;
import static com.gree1d.reappzuku.core.AppConstants.*;

public class ShappkyService extends Service {

    private static final String FILE_NAME = "ShappkyService";
    static final String ACTION_IDLE_FREEZE = "com.gree1d.reappzuku.IDLE_FREEZE";
    static final String ACTION_HEARTBEAT_CHECK = "com.gree1d.reappzuku.HEARTBEAT_CHECK";
    private static final int FREEZE_ALARM_REQUEST_CODE = 1001;
    private static final int RESTART_ALARM_REQUEST_CODE = 1002;
    private static final int HEARTBEAT_ALARM_REQUEST_CODE = 1003;
    private static final int SNAPSHOT_ALARM_REQUEST_CODE = 1004;
    private static final long HEARTBEAT_INTERVAL_MS = 2 * 60 * 1000L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static boolean isRunning = false;

    private ShellManager shellManager;
    private BackgroundAppManager appManager;
    private AutoKillManager autoKillManager;
    private SleepModeManager sleepModeManager;
    private CollectStatsManager collectStatsManager;
    private RestrictionsScheduler scheduler;
    private KillTriggerReceiver screenOffReceiver;
    private RestrictionsWatchdogManager watchdog;
    private AdditionalScenariosManager additionalScenariosManager;
    private RamKillShortcutManager ramKillShortcutManager;

    private boolean isFrozen = false;
    private boolean shizukuLostNotificationShown = false;

    public static boolean isRunning() {
        return isRunning;
    }

    private boolean isAllNotificationsEnabled() {
        SharedPreferences prefs = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        int mode = prefs.getInt(KEY_NOTIFICATION_MODE, NOTIFICATION_MODE_ALL);
        return mode == NOTIFICATION_MODE_ALL;
    }

    public static void updateNotification(Context context, String title, String text) {
        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        int mode = prefs.getInt(KEY_NOTIFICATION_MODE, NOTIFICATION_MODE_ALL);
        if (mode != NOTIFICATION_MODE_ALL) return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_SERVICE)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_shappky)
                .setOngoing(true)
                .setSilent(true);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID_SERVICE, builder.build());
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AppDebugManager.d(Category.FOREGROUND_SERVICE, FILE_NAME + ": onCreate started");
        shellManager = new ShellManager(this, handler, executor);
        appManager = new BackgroundAppManager(this, handler, executor, shellManager);
        AppDebugManager.d(Category.BACKGROUND_RESTRICTIONS, FILE_NAME + ": BackgroundAppManager initialized");
        autoKillManager = new AutoKillManager(this, handler, executor, shellManager, appManager.getCurrentAppsList());
        sleepModeManager = new SleepModeManager(this, handler, executor, shellManager);
        collectStatsManager = new CollectStatsManager(this, shellManager);
        scheduler = new RestrictionsScheduler(this, handler, executor, shellManager, appManager, sleepModeManager);
        autoKillManager.setScheduler(scheduler);
        sleepModeManager.setScheduler(scheduler);
        appManager.setScheduler(scheduler);
        AppDebugManager.d(Category.BACKGROUND_RESTRICTIONS, FILE_NAME + ": BackgroundAppManager scheduler attached");
        watchdog = new RestrictionsWatchdogManager(this, handler, appManager, shellManager, scheduler);
        createNotificationChannel();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
                .setContentTitle(getString(R.string.service_notification_title))
                .setContentText(getString(R.string.service_notification_text))
                .setSmallIcon(R.drawable.ic_shappky)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID_SERVICE, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            AppDebugManager.d(Category.FOREGROUND_SERVICE, FILE_NAME + ": startForeground called (FOREGROUND_SERVICE_TYPE_SPECIAL_USE, API " + Build.VERSION.SDK_INT + ")");
        } else {
            startForeground(NOTIFICATION_ID_SERVICE, notification);
            AppDebugManager.d(Category.FOREGROUND_SERVICE, FILE_NAME + ": startForeground called (legacy, API " + Build.VERSION.SDK_INT + ")");
        }
        isRunning = true;
        AppDebugManager.d(Category.FOREGROUND_SERVICE, FILE_NAME + ": Service is now running (isRunning=true)");

        screenOffReceiver = new KillTriggerReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenOffReceiver, filter);

        additionalScenariosManager = new AdditionalScenariosManager(this);
        AppDebugManager.d(Category.ADVANCED_CONDITIONS, FILE_NAME + ": AdditionalScenariosManager initialized");
        additionalScenariosManager.updateHardwareReceiverState();
        ramKillShortcutManager = new RamKillShortcutManager(this, shellManager);

        scheduleNextKill();
        scheduler.scheduleNext();

        cancelShizukuLostNotification();
        AppDebugManager.d(Category.CORE, FILE_NAME + ": Shizuku-lost notification cancelled on service create");
        AppDebugManager.d(Category.CORE, FILE_NAME + ": Registering onRootCheckCompleteListener -> scheduleShizukuCheck");
        shellManager.setOnRootCheckCompleteListener(this::scheduleShizukuCheck);
        scheduleSnapshotAlarm();
        scheduleWidgetUpdate();

        AppDebugManager.d(Category.BACKGROUND_RESTRICTIONS, FILE_NAME + ": reapplySavedBackgroundRestrictions starting on service create");
        appManager.reapplySavedBackgroundRestrictions(() ->
                AppDebugManager.d(Category.BACKGROUND_RESTRICTIONS, FILE_NAME + ": reapplySavedBackgroundRestrictions finished"));
        watchdog.startIfNeeded();

        UpdateChecker.schedulePeriodicCheck(getApplicationContext());
        AppDebugManager.d(Category.FOREGROUND_SERVICE, FILE_NAME + ": onCreate completed");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        AppDebugManager.d(Category.FOREGROUND_SERVICE, FILE_NAME + ": onStartCommand: action=" + (intent != null ? intent.getAction() : "null"));
        if (intent == null) {
            AppDebugManager.w(Category.FOREGROUND_SERVICE, FILE_NAME + ": onStartCommand: intent is null, returning START_STICKY");
            return START_STICKY;
        }

        String action = intent.getAction();
        if (action == null) {
            AppDebugManager.w(Category.FOREGROUND_SERVICE, FILE_NAME + ": onStartCommand: action is null, returning START_STICKY");
            return START_STICKY;
        }

        switch (action) {
            case "TRIGGER_KILL":
                executor.execute(() -> {
                    SharedPreferences prefs = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
                    boolean ramThresholdEnabled = prefs.getBoolean(KEY_RAM_THRESHOLD_ENABLED, false);
                    if (ramThresholdEnabled) {
                        int threshold = prefs.getInt(KEY_RAM_THRESHOLD, DEFAULT_RAM_THRESHOLD_PERCENT);
                        int ramPercent = getCurrentRamUsagePercent();
                        AppDebugManager.d(Category.AUTO_KILL_BASE, FILE_NAME + ": TRIGGER_KILL: RAM threshold check: " + ramPercent + "% / " + threshold + "%");
                        if (ramPercent >= threshold) {
                            AppDebugManager.d(Category.AUTO_KILL_BASE, FILE_NAME + ": TRIGGER_KILL: threshold reached, starting Screen-Off Kill");
                            autoKillManager.performAutoKill(() -> KillTriggerReceiver.releaseAutoKillWakeLock(), resolveKillSource("Screen-Off Kill"));
                        } else {
                            AppDebugManager.d(Category.AUTO_KILL_BASE, FILE_NAME + ": TRIGGER_KILL: RAM below threshold, kill skipped");
                            KillTriggerReceiver.releaseAutoKillWakeLock();
                        }
                    } else {
                        AppDebugManager.d(Category.AUTO_KILL_BASE, FILE_NAME + ": TRIGGER_KILL: no RAM threshold, starting Screen-Off Kill");
                        autoKillManager.performAutoKill(() -> KillTriggerReceiver.releaseAutoKillWakeLock(), resolveKillSource("Screen-Off Kill"));
                    }
                });
                break;

            case "SCREEN_OFF":
                if (sleepModeManager.isSleepModeEnabled()) {
                    scheduleIdleFreezeAlarm();
                    long delayMs = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                            .getLong(KEY_SLEEP_MODE_DELAY, DEFAULT_SLEEP_MODE_DELAY_MS);
                    AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": Idle freeze alarm scheduled (" + (delayMs / 60000) + " min)");
                } else {
                    AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": SCREEN_OFF received but sleep mode disabled, no alarm scheduled");
                }
                break;

            case "SCREEN_ON":
                handler.postDelayed(() -> {
                    cancelIdleFreezeAlarm();
                    if (isFrozen) {
                        AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": Screen on after idle freeze, unfreezing apps");
                        isFrozen = false;
                        cancelHeartbeatAlarm();
                        sleepModeManager.unfreezeBackgroundRestrictedApps(null);
                    } else {
                        AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": Screen on before idle threshold, alarm cancelled");
                    }
                }, 1500);
                break;

            case "IDLE_FREEZE":
                if (!sleepModeManager.isSleepModeEnabled()) {
                    AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": Sleep mode disabled, skipping freeze");
                    break;
                }
                AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": Idle threshold reached, freezing background restricted apps");
                sleepModeManager.freezeBackgroundRestrictedApps(() -> {
                    isFrozen = true;
                    AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": Apps frozen successfully");
                    scheduleHeartbeatAlarm();
                });
                break;

            case "HEARTBEAT_CHECK":
                handleHeartbeatCheck();
                break;

            case "SCHEDULER_TICK":
                scheduler.tick();
                break;

            case "WIDGET_KILL":
                ramKillShortcutManager.performKillAndUpdate(autoKillManager);
                break;
                
            case "SHORTCUT_KILL_FOREGROUND":
                String targetPkg = intent.getStringExtra("target_package");
                if (targetPkg != null && !targetPkg.isEmpty()) {
                    AppDebugManager.d(Category.SHORTCUTS_WIDGETS, FILE_NAME + ": SHORTCUT_KILL_FOREGROUND received for " + targetPkg);
                    autoKillManager.killApp(targetPkg, null);
                } else {
                    AppDebugManager.w(Category.SHORTCUTS_WIDGETS, FILE_NAME + ": SHORTCUT_KILL_FOREGROUND received but target_package is null");
                }
                break;

            case "UPDATE_HW_RECEIVERS":
                AppDebugManager.d(Category.ADVANCED_CONDITIONS, FILE_NAME + ": UPDATE_HW_RECEIVERS received, updating hardware receiver state");
                additionalScenariosManager.updateHardwareReceiverState();
                break;

            case "TAKE_SNAPSHOT":
                AppDebugManager.d(Category.UTILS, FILE_NAME + ": TAKE_SNAPSHOT received");
                collectStatsManager.takeSnapshotAsync(() -> {
                    releaseSnapshotWakeLock();
                    scheduleSnapshotAlarm();
                });
                break;
        }

        return START_STICKY;
    }

    private void scheduleShizukuCheck() {
        AppDebugManager.d(Category.CORE, FILE_NAME + ": scheduleShizukuCheck: starting Root/Shizuku poll loop");
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;

                if (shellManager.hasRootAccess()) {
                    AppDebugManager.d(Category.CORE, FILE_NAME + ": Root access available, skipping Shizuku check");
                    handler.postDelayed(this, SHIZUKU_POLL_INTERVAL_MS);
                    return;
                }

                boolean shizukuOk = shellManager.hasShizukuPermission();
                AppDebugManager.d(Category.CORE, FILE_NAME + ": Shizuku permission check result: " + shizukuOk);

                if (!shizukuOk) {
                    if (!shizukuLostNotificationShown) {
                        AppDebugManager.w(Category.CORE, FILE_NAME + ": Shizuku permission lost, sending notification");
                        shizukuLostNotificationShown = true;
                    }
                    sendShizukuLostNotification();
                } else {
                    if (shizukuLostNotificationShown) {
                        AppDebugManager.d(Category.CORE, FILE_NAME + ": Shizuku permission restored, cancelling notification");
                        shizukuLostNotificationShown = false;
                    }
                    cancelShizukuLostNotification();
                }

                handler.postDelayed(this, SHIZUKU_POLL_INTERVAL_MS);
            }
        }, 0);
    }

    private void sendShizukuLostNotification() {
        AppDebugManager.w(Category.CORE, FILE_NAME + ": sendShizukuLostNotification: showing Shizuku-lost notification");
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID_ACTIONS)
                .setContentTitle(getString(R.string.service_shizuku_lost_title))
                .setContentText(getString(R.string.service_shizuku_lost_text))
                .setSmallIcon(R.drawable.ic_shappky)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(false);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID_SHIZUKU_LOST, builder.build());
        } else {
            AppDebugManager.e(Category.CORE, FILE_NAME + ": sendShizukuLostNotification: NotificationManager is null, cannot show notification");
        }
    }

    private void cancelShizukuLostNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(NOTIFICATION_ID_SHIZUKU_LOST);
        }
    }

    private boolean canScheduleExactAlarms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms();
        }
        return true;
    }

    private void scheduleIdleFreezeAlarm() {
        cancelIdleFreezeAlarm();
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            AppDebugManager.e(Category.SLEEP_MODE, FILE_NAME + ": scheduleIdleFreezeAlarm: AlarmManager is null, cannot schedule");
            return;
        }
        SharedPreferences prefs = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        long delayMs = prefs.getLong(KEY_SLEEP_MODE_DELAY, DEFAULT_SLEEP_MODE_DELAY_MS);
        PendingIntent pendingIntent = getFreezeAlarmIntent();
        long triggerAt = System.currentTimeMillis() + delayMs;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                AppDebugManager.w(Category.SLEEP_MODE, FILE_NAME + ": scheduleIdleFreezeAlarm: exact alarm not permitted, using inexact");
            }
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
        AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": scheduleIdleFreezeAlarm: armed, triggerAt=" + triggerAt);
    }

    private void cancelIdleFreezeAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        alarmManager.cancel(getFreezeAlarmIntent());
        AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": Idle freeze alarm cancelled");
    }

    private PendingIntent getFreezeAlarmIntent() {
        Intent intent = new Intent(this, KillTriggerReceiver.class);
        intent.setAction(ACTION_IDLE_FREEZE);
        return PendingIntent.getBroadcast(
                this,
                FREEZE_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void scheduleHeartbeatAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            AppDebugManager.e(Category.SLEEP_MODE, FILE_NAME + ": scheduleHeartbeatAlarm: AlarmManager is null, cannot schedule");
            return;
        }
        PendingIntent pendingIntent = getHeartbeatAlarmIntent();
        long triggerAt = System.currentTimeMillis() + HEARTBEAT_INTERVAL_MS;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                AppDebugManager.w(Category.SLEEP_MODE, FILE_NAME + ": scheduleHeartbeatAlarm: exact alarm not permitted, using inexact");
            }
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
        AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": scheduleHeartbeatAlarm: armed, triggerAt=" + triggerAt);
    }

    private void cancelHeartbeatAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        alarmManager.cancel(getHeartbeatAlarmIntent());
        AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": Heartbeat alarm cancelled");
    }

    private PendingIntent getHeartbeatAlarmIntent() {
        Intent intent = new Intent(this, KillTriggerReceiver.class);
        intent.setAction(ACTION_HEARTBEAT_CHECK);
        return PendingIntent.getBroadcast(
                this,
                HEARTBEAT_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void handleHeartbeatCheck() {
        if (!isFrozen) {
            AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": Heartbeat check: already unfrozen, stopping heartbeat");
            return;
        }

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean screenOn = pm != null && pm.isInteractive();

        if (screenOn) {
            AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": Heartbeat check: screen is on but SCREEN_ON was missed, unfreezing now");
            isFrozen = false;
            sleepModeManager.unfreezeBackgroundRestrictedApps(null);
        } else {
            AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": Heartbeat check: screen still off, rescheduling");
            scheduleHeartbeatAlarm();
        }
    }

    private void scheduleServiceRestart() {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(this, RestartReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(
                this,
                RESTART_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );
        am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 3000, pi);
    }

    private static final long SNAPSHOT_INTERVAL_MS = 15 * 60 * 1000L;
    private static final long WIDGET_UPDATE_INTERVAL_MS = 60 * 1000L;

    private void scheduleSnapshotAlarm() {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            AppDebugManager.e(Category.UTILS, FILE_NAME
                    + ": scheduleSnapshotAlarm: AlarmManager is null, cannot schedule");
            return;
        }

        long now = System.currentTimeMillis();
        long triggerAt = now + SNAPSHOT_INTERVAL_MS;
        PendingIntent pi = getSnapshotAlarmIntent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                AppDebugManager.w(Category.UTILS, FILE_NAME + ": scheduleSnapshotAlarm: exact alarm not permitted, using inexact");
            }
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
        AppDebugManager.d(Category.UTILS, FILE_NAME
                + ": scheduleSnapshotAlarm: armed, triggerAt=" + triggerAt
                + " (in " + ((triggerAt - now) / 60_000) + " min)");
    }

    private void releaseSnapshotWakeLock() {
        CollectStatsReceiver.releaseSnapshotWakeLock();
    }

    private void cancelSnapshotAlarm() {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(getSnapshotAlarmIntent());
        AppDebugManager.d(Category.UTILS, FILE_NAME + ": cancelSnapshotAlarm: cancelled");
    }

    private PendingIntent getSnapshotAlarmIntent() {
        Intent intent = new Intent(this, CollectStatsReceiver.class);
        intent.setAction(CollectStatsReceiver.ACTION_COLLECT_SNAPSHOT);
        return PendingIntent.getBroadcast(
                this,
                SNAPSHOT_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void scheduleWidgetUpdate() {
        Runnable widgetRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;
                AppzukuWidget.updateAllWidgetsFromJava(ShappkyService.this);
                ramKillShortcutManager.updateShortcut();
                handler.postDelayed(this, WIDGET_UPDATE_INTERVAL_MS);
            }
        };
        handler.post(widgetRunnable);
    }

    private void scheduleNextKill() {
        if (!isRunning)
            return;

        SharedPreferences prefs = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        int killInterval = prefs.getInt(KEY_KILL_INTERVAL, DEFAULT_KILL_INTERVAL_MS);

        handler.postDelayed(() -> {
            if (!isRunning)
                return;

            executor.execute(() -> {
                boolean autoKillEnabled = prefs.getBoolean(KEY_AUTO_KILL_ENABLED, false);
                boolean periodicKillEnabled = prefs.getBoolean(KEY_PERIODIC_KILL_ENABLED, false);
                boolean ramThresholdEnabled = prefs.getBoolean(KEY_RAM_THRESHOLD_ENABLED, false);

                if (autoKillEnabled && periodicKillEnabled) {
                    if (ramThresholdEnabled) {
                        int threshold = prefs.getInt(KEY_RAM_THRESHOLD, DEFAULT_RAM_THRESHOLD_PERCENT);
                        int ramPercent = getCurrentRamUsagePercent();
                        AppDebugManager.d(Category.AUTO_KILL_BASE, FILE_NAME + ": scheduleNextKill: RAM threshold check: " + ramPercent + "% / " + threshold + "%");
                        if (ramPercent >= threshold) {
                            AppDebugManager.d(Category.AUTO_KILL_BASE, FILE_NAME + ": scheduleNextKill: threshold reached, starting Service Periodic Kill");
                            autoKillManager.performAutoKill(() -> handler.post(this::scheduleNextKill), resolveKillSource("Service Periodic Kill"));
                        } else {
                            AppDebugManager.d(Category.AUTO_KILL_BASE, FILE_NAME + ": scheduleNextKill: RAM below threshold, kill skipped");
                            handler.post(this::scheduleNextKill);
                        }
                    } else {
                        AppDebugManager.d(Category.AUTO_KILL_BASE, FILE_NAME + ": scheduleNextKill: no RAM threshold, starting Service Periodic Kill");
                        autoKillManager.performAutoKill(() -> handler.post(this::scheduleNextKill), resolveKillSource("Service Periodic Kill"));
                    }
                } else {
                    AppDebugManager.d(Category.AUTO_KILL_BASE, FILE_NAME + ": scheduleNextKill: skipped (autoKill=" + autoKillEnabled + " periodic=" + periodicKillEnabled + ")");
                    handler.post(this::scheduleNextKill);
                }
            });
        }, killInterval);
    }

    private int getCurrentRamUsagePercent() {
        try (java.io.RandomAccessFile reader = new java.io.RandomAccessFile("/proc/meminfo", "r")) {
            String load = reader.readLine();
            long totalRam = Long.parseLong(load.replaceAll("\\D+", ""));
            load = reader.readLine();
            load = reader.readLine();
            long availableRam = Long.parseLong(load.replaceAll("\\D+", ""));
            return (int) ((totalRam - availableRam) * 100 / totalRam);
        } catch (IOException | NumberFormatException e) {
            return 0;
        }
    }

    private String resolveKillSource(String defaultSource) {
        SharedPreferences prefs = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        int activePreset = prefs.getInt(KEY_ACTIVE_PRESET, 0);
        if (activePreset != 0) {
            PresetManager pm = new PresetManager(this);
            return defaultSource + " · " + pm.getPresetName(activePreset);
        }
        return defaultSource;
    }

    @Override
    public void onDestroy() {
        AppDebugManager.d(Category.FOREGROUND_SERVICE, FILE_NAME + ": onDestroy called, stopping service");
        isRunning = false;
        scheduleServiceRestart();
        AppDebugManager.d(Category.FOREGROUND_SERVICE, FILE_NAME + ": Service restart scheduled via AlarmManager");
        cancelIdleFreezeAlarm();
        cancelSnapshotAlarm();
        cancelShizukuLostNotification();
        AppDebugManager.d(Category.CORE, FILE_NAME + ": Shizuku-lost notification cancelled on service destroy");
        if (screenOffReceiver != null) {
            unregisterReceiver(screenOffReceiver);
        }
        if (additionalScenariosManager != null) {
            AppDebugManager.d(Category.ADVANCED_CONDITIONS, FILE_NAME + ": Stopping AdditionalScenariosManager (onDestroy)");
            additionalScenariosManager.stop();
        }
        watchdog.stop();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
        executor.shutdownNow();
        AppDebugManager.d(Category.FOREGROUND_SERVICE, FILE_NAME + ": onDestroy completed, executor shut down");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AppDebugManager.d(Category.FOREGROUND_SERVICE, FILE_NAME + ": createNotificationChannel: registering channels");
            NotificationManager nm = getSystemService(NotificationManager.class);

            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID_SERVICE,
                    getString(R.string.service_channel_foreground_name),
                    NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(serviceChannel);

            NotificationChannel actionsChannel = new NotificationChannel(
                    CHANNEL_ID_ACTIONS,
                    getString(R.string.service_channel_actions_name),
                    NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(actionsChannel);
        }
    }
    
    public static class RestartReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ShappkyService.isRunning()) {
                AppDebugManager.d(Category.FOREGROUND_SERVICE, "ShappkyService.RestartReceiver: Service not running, restarting via " +
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? "startForegroundService" : "startService"));
                Intent service = new Intent(context, ShappkyService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(service);
                } else {
                    context.startService(service);
                }
            } else {
                AppDebugManager.d(Category.FOREGROUND_SERVICE, "ShappkyService.RestartReceiver: Service already running, restart skipped");
            }
        }
    }
}
