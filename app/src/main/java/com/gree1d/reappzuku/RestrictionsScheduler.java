package com.gree1d.reappzuku;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import com.gree1d.reappzuku.db.AppDatabase;
import com.gree1d.reappzuku.db.SchedulerLog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import static com.gree1d.reappzuku.PreferenceKeys.*;

/**
 * RestrictionsScheduler — temporary protection of restricted apps from Auto-Kill,
 * Background Restrictions, and Sleep Mode on a schedule.
 *
 * ── How it works ────────────────────────────────────────────────────────────
 * The user creates a ScheduleEntry for apps from the Background Restrictions list.
 * Each schedule defines: package, time window, protection bitmask, action on activation.
 * On window activation:
 *   - package is added to the "temp_protected" group
 *   - appops restrictions are lifted (if PROTECT_BG_RESTRICTIONS)
 *   - package is unfrozen (if PROTECT_SLEEP_MODE)
 *   - main activity is optionally launched
 * On window deactivation:
 *   - restrictions are restored
 *   - am force-stop / am kill is executed (per KEY_AUTO_KILL_TYPE)
 *   - package is removed from temp_protected
 *
 * ── Scheduler ───────────────────────────────────────────────────────────────
 * scheduleNext() sets ONE exact AlarmManager alarm for the nearest upcoming event
 * (start or end of any active schedule).
 * Fires via SchedulerReceiver → ShappkyService action "SCHEDULER_TICK".
 * After each fire, the next alarm is scheduled.
 * On reboot, BOOT_COMPLETED restores the alarm via scheduleNext().
 *
 * ── Integration ─────────────────────────────────────────────────────────────
 * AutoKillManager.performAutoKill()       — skip isProtected(pkg, PROTECT_AUTO_KILL)
 * SleepModeManager.freezeBackground...()  — skip getTempProtectedPackages()
 * BackgroundAppManager.reapplySaved...()  — skip getTempProtectedPackages()
 * ShappkyService.onStartCommand()         — add case "SCHEDULER_TICK": scheduler.tick()
 * KillTriggerReceiver / BOOT_COMPLETED    — call scheduler.scheduleNext()
 *
 * ── Limits ──────────────────────────────────────────────────────────────────
 * Maximum MAX_SCHEDULES (15) schedules.
 */
public class RestrictionsScheduler {

    private static final String TAG = "RestrictionsScheduler";

    // ── ShappkyService action ────────────────────────────────────────────────
    /** Intent action — ShappkyService calls tick() when received. */
    public static final String ACTION_SCHEDULER_TICK = "SCHEDULER_TICK";

    // ── AlarmManager ─────────────────────────────────────────────────────────
    private static final int SCHEDULER_ALARM_REQUEST_CODE = 2001;

    // ── Limit ────────────────────────────────────────────────────────────────
    public static final int MAX_SCHEDULES = 15;

    // ── Preference keys ──────────────────────────────────────────────────────
    private static final String KEY_SCHEDULES      = "restrictions_schedules";
    private static final String KEY_TEMP_PROTECTED = "temp_protected_packages";

    // ── Protection flags (bitmask) ───────────────────────────────────────────
    public static final int PROTECT_AUTO_KILL       = 1;
    public static final int PROTECT_BG_RESTRICTIONS = 1 << 1;
    public static final int PROTECT_SLEEP_MODE      = 1 << 2;
    public static final int PROTECT_ALL             = PROTECT_AUTO_KILL | PROTECT_BG_RESTRICTIONS | PROTECT_SLEEP_MODE;

    // ── Activation actions ───────────────────────────────────────────────────
    public static final int ON_ACTIVATE_NOTHING    = 0;
    public static final int ON_ACTIVATE_LAUNCH_APP = 1;

    // ── appops op codes ──────────────────────────────────────────────────────
    private static final String OP_RUN_ANY_IN_BG = "RUN_ANY_IN_BACKGROUND";
    private static final String OP_RUN_IN_BG     = "RUN_IN_BACKGROUND";
    private static final String OP_FG            = "START_FOREGROUND";
    private static final String OP_FGS_FROM_BG   = "START_FOREGROUND_SERVICES_FROM_BACKGROUND";
    private static final String OP_WAKE_LOCK      = "WAKE_LOCK";
    private static final String OP_ALARM          = "ALARM_WAKEUP";
    private static final String OP_BOOT           = "RECEIVE_BOOT_COMPLETED";

    // =========================================================================
    // ScheduleEntry
    // =========================================================================

    /** A single temporary protection schedule. */
    public static class ScheduleEntry {
        /** Unique ID (System.currentTimeMillis() at creation). */
        public long id;
        /** App package name. */
        public String packageName;
        /** Protection start hour (0-23). */
        public int startHour;
        /** Protection start minute (0-59). */
        public int startMinute;
        /** Protection end hour (0-23). */
        public int endHour;
        /** Protection end minute (0-59). */
        public int endMinute;
        /** Bitmask of PROTECT_* flags. */
        public int protectFlags;
        /** Action on activation: ON_ACTIVATE_NOTHING / ON_ACTIVATE_LAUNCH_APP. */
        public int onActivateAction;
        /** Whether the schedule is enabled. */
        public boolean enabled;

        public ScheduleEntry() {
            this.id               = System.currentTimeMillis();
            this.protectFlags     = PROTECT_ALL;
            this.onActivateAction = ON_ACTIVATE_NOTHING;
            this.enabled          = true;
        }

        /**
         * Returns true if the current time falls within the [start, end) window.
         * Supports midnight crossover (e.g. 23:00–02:00).
         */
        public boolean isActiveNow(int currentHour, int currentMinute) {
            if (!enabled) return false;
            int now  = currentHour  * 60 + currentMinute;
            int from = startHour    * 60 + startMinute;
            int to   = endHour      * 60 + endMinute;
            if (from == to) return false;
            if (from < to)  return now >= from && now < to;
            return now >= from || now < to; // midnight crossover
        }

        /** Next timestamp for the window start (>= now). */
        public long nextStartMillis() {
            return nextEventMillis(startHour, startMinute);
        }

        /** Next timestamp for the window end (>= now). */
        public long nextEndMillis() {
            return nextEventMillis(endHour, endMinute);
        }

        private long nextEventMillis(int hour, int minute) {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.MINUTE,      minute);
            cal.set(Calendar.SECOND,      0);
            cal.set(Calendar.MILLISECOND, 0);
            if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }
            return cal.getTimeInMillis();
        }

        public JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("id",          id);
            obj.put("packageName", packageName);
            obj.put("startHour",   startHour);
            obj.put("startMin",    startMinute);
            obj.put("endHour",     endHour);
            obj.put("endMin",      endMinute);
            obj.put("flags",       protectFlags);
            obj.put("onActivate",  onActivateAction);
            obj.put("enabled",     enabled);
            return obj;
        }

        public static ScheduleEntry fromJson(JSONObject obj) throws JSONException {
            ScheduleEntry e = new ScheduleEntry();
            e.id               = obj.getLong("id");
            e.packageName      = obj.getString("packageName");
            e.startHour        = obj.getInt("startHour");
            e.startMinute      = obj.getInt("startMin");
            e.endHour          = obj.getInt("endHour");
            e.endMinute        = obj.getInt("endMin");
            e.protectFlags     = obj.getInt("flags");
            e.onActivateAction = obj.getInt("onActivate");
            e.enabled          = obj.optBoolean("enabled", true);
            return e;
        }
    }

    // =========================================================================
    // SchedulerLog
    // =========================================================================

    /**
     * Scheduler event log. Entries are stored in Room (table: scheduler_log).
     *
     * action:
     *   "lift"    — background restrictions lifted
     *   "restore" — background restrictions restored
     *
     * outcome for "lift":
     *   "ok" / "error" / "denied" / "skipped"
     *
     * outcome for "restore":
     *   "ok" / "error" / "skipped"
     *
     * detail:
     *   "lift"    → "action=launch" / "action=none"
     *   "restore" → "stop=force-stop" / "stop=am-kill"
     */
    public static final class SchedulerLog {

        private static final int MAX_ENTRIES    = 200;
        private static final int MAX_DETAIL_LEN = 180;

        private SchedulerLog() {}

        /**
         * Log a background restrictions lift event.
         *
         * @param outcome   "ok" / "error" / "denied" / "skipped"
         * @param hasLaunch true if main activity launch is configured
         * @param use24h    time format (12h / 24h) — stored in detail, not in timestamp
         */
        public static void logLift(Context context, String packageName,
                                   String outcome, boolean hasLaunch, boolean use24h) {
            String detail = "action=" + (hasLaunch ? "launch" : "none");
            append(context, "lift", packageName, outcome, detail);
        }

        /**
         * Log a background restrictions restore event.
         *
         * @param outcome   "ok" / "error" / "skipped"
         * @param forceStop true = am force-stop, false = am kill
         * @param use24h    time format
         */
        public static void logRestore(Context context, String packageName,
                                      String outcome, boolean forceStop, boolean use24h) {
            String detail = "stop=" + (forceStop ? "force-stop" : "am-kill");
            append(context, "restore", packageName, outcome, detail);
        }

        public static String readDisplayText(Context context) {
            if (context == null) return context.getString(R.string.log_scheduler_empty);
            List<SchedulerLog.Entry> entries = readEntries(context);
            if (entries.isEmpty()) return context.getString(R.string.log_scheduler_empty);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < entries.size(); i++) {
                if (i > 0) sb.append('\n');
                sb.append(entries.get(i).toDisplayLine());
            }
            return sb.toString();
        }

        /** Returns entries sorted newest first. */
        public static List<SchedulerLog.Entry> readEntries(Context context) {
            List<com.gree1d.reappzuku.db.SchedulerLog> rows =
                    AppDatabase.getInstance(context).schedulerLogDao().getRecent(MAX_ENTRIES);
            List<SchedulerLog.Entry> result = new ArrayList<>(rows.size());
            for (com.gree1d.reappzuku.db.SchedulerLog row : rows) {
                result.add(new Entry(
                        formatTimestamp(row.timestamp),
                        row.action      != null ? row.action      : "event",
                        row.packageName != null ? row.packageName : "-",
                        row.outcome     != null ? row.outcome     : "unknown",
                        row.detail      != null ? row.detail      : ""
                ));
            }
            return result;
        }

        public static void clear(Context context) {
            if (context == null) return;
            AppDatabase.getInstance(context).schedulerLogDao().clearAll();
        }

        private static void append(Context context, String action, String packageName,
                                   String outcome, String detail) {
            if (context == null) return;

            com.gree1d.reappzuku.db.SchedulerLog entry = new com.gree1d.reappzuku.db.SchedulerLog();
            entry.timestamp   = System.currentTimeMillis();
            entry.action      = sanitize(action);
            entry.packageName = sanitize(packageName != null ? packageName : "-");
            entry.outcome     = sanitize(outcome     != null ? outcome     : "unknown");
            entry.detail      = sanitize(detail);

            com.gree1d.reappzuku.db.SchedulerLog.Dao dao =
                    AppDatabase.getInstance(context).schedulerLogDao();
            dao.insert(entry);

            // Trim to MAX_ENTRIES
            int count = dao.getCount();
            if (count > MAX_ENTRIES) {
                dao.deleteOldest(count - MAX_ENTRIES);
            }
        }

        private static String formatTimestamp(long millis) {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(millis));
        }

        private static String sanitize(String value) {
            if (value == null) return "";
            String s = value.replace('\r', ' ').replace('\n', ' ')
                            .replace('|', '/').replaceAll("\\s+", " ").trim();
            return s.length() > MAX_DETAIL_LEN ? s.substring(0, MAX_DETAIL_LEN - 3) + "..." : s;
        }

        /** Public read-only log entry. */
        public static final class Entry {
            public final String timestamp;
            public final String action;
            public final String packageName;
            public final String outcome;
            public final String detail;

            private Entry(String timestamp, String action, String packageName,
                          String outcome, String detail) {
                this.timestamp   = timestamp;
                this.action      = action;
                this.packageName = packageName;
                this.outcome     = outcome;
                this.detail      = detail;
            }

            public String toDisplayLine() {
                StringBuilder line = new StringBuilder()
                        .append(timestamp).append(" | ")
                        .append(action).append(" | ")
                        .append(packageName).append(" | ")
                        .append(outcome);
                if (!detail.isEmpty()) line.append(" | ").append(detail);
                return line.toString();
            }
        }
    }

    // =========================================================================
    // SchedulerReceiver (nested BroadcastReceiver)
    // =========================================================================

    /**
     * Receives alarm from AlarmManager and starts ShappkyService with action SCHEDULER_TICK.
     *
     * Register in AndroidManifest.xml:
     *   <receiver android:name=".RestrictionsScheduler$SchedulerReceiver"
     *             android:exported="false" />
     */
    public static final class SchedulerReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Intent serviceIntent = new Intent(context, ShappkyService.class);
            serviceIntent.setAction(ACTION_SCHEDULER_TICK);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }

    // =========================================================================
    // Fields
    // =========================================================================

    private final Context              context;
    private final Handler              handler;
    private final ExecutorService      executor;
    private final ShellManager         shellManager;
    private final BackgroundAppManager backgroundAppManager;
    private final SharedPreferences    prefs;

    public RestrictionsScheduler(Context context,
                                 Handler handler,
                                 ExecutorService executor,
                                 ShellManager shellManager,
                                 BackgroundAppManager backgroundAppManager) {
        this.context              = context;
        this.handler              = handler;
        this.executor             = executor;
        this.shellManager         = shellManager;
        this.backgroundAppManager = backgroundAppManager;
        this.prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    // =========================================================================
    // Public API — schedule management
    // =========================================================================

    public List<ScheduleEntry> getSchedules() {
        List<ScheduleEntry> list = new ArrayList<>();
        String json = prefs.getString(KEY_SCHEDULES, null);
        if (json == null || json.isEmpty()) return list;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(ScheduleEntry.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            Log.e(TAG, "getSchedules: parse error", e);
        }
        return list;
    }

    public void saveSchedules(List<ScheduleEntry> schedules) {
        JSONArray arr = new JSONArray();
        for (ScheduleEntry entry : schedules) {
            try { arr.put(entry.toJson()); }
            catch (JSONException e) { Log.e(TAG, "saveSchedules: " + entry.packageName, e); }
        }
        prefs.edit().putString(KEY_SCHEDULES, arr.toString()).apply();
    }

    /**
     * Adds a schedule. Automatically reschedules the alarm.
     * @return false if MAX_SCHEDULES limit is reached.
     */
    public boolean addSchedule(ScheduleEntry entry) {
        List<ScheduleEntry> list = getSchedules();
        if (list.size() >= MAX_SCHEDULES) {
            Log.w(TAG, "addSchedule: limit reached (" + MAX_SCHEDULES + ")");
            return false;
        }
        list.add(entry);
        saveSchedules(list);
        scheduleNext();
        return true;
    }

    /**
     * Updates a schedule by id. Automatically reschedules the alarm.
     * @return false if not found.
     */
    public boolean updateSchedule(ScheduleEntry updated) {
        List<ScheduleEntry> list = getSchedules();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == updated.id) {
                list.set(i, updated);
                saveSchedules(list);
                scheduleNext();
                return true;
            }
        }
        return false;
    }

    /** Removes a schedule by id. Automatically reschedules the alarm. */
    public void removeSchedule(long id) {
        List<ScheduleEntry> list = getSchedules();
        list.removeIf(e -> e.id == id);
        saveSchedules(list);
        scheduleNext();
    }

    /**
     * Returns packages that are currently temporarily protected.
     * Used by AutoKillManager, SleepModeManager, BackgroundAppManager.
     */
    public Set<String> getTempProtectedPackages() {
        return new HashSet<>(prefs.getStringSet(KEY_TEMP_PROTECTED, new HashSet<>()));
    }

    /**
     * Checks whether a package is protected by the given flag right now.
     * Convenience helper for integration in other managers.
     */
    public boolean isProtected(String packageName, int flag) {
        if (!getTempProtectedPackages().contains(packageName)) return false;
        Calendar cal = Calendar.getInstance();
        int h = cal.get(Calendar.HOUR_OF_DAY);
        int m = cal.get(Calendar.MINUTE);
        for (ScheduleEntry e : getSchedules()) {
            if (e.packageName.equals(packageName) && e.isActiveNow(h, m)) {
                return (e.protectFlags & flag) != 0;
            }
        }
        return false;
    }

    // =========================================================================
    // AlarmManager — next event scheduling
    // =========================================================================

    /**
     * Finds the nearest upcoming event across all schedules and sets one exact alarm.
     * Call:
     *   - after any schedule change (already done in add/update/remove)
     *   - in ShappkyService.onCreate()
     *   - in BOOT_COMPLETED receiver
     */
    public void scheduleNext() {
        List<ScheduleEntry> schedules = getSchedules();
        if (schedules.isEmpty()) {
            cancelAlarm();
            return;
        }

        long now     = System.currentTimeMillis();
        long nearest = Long.MAX_VALUE;

        Calendar cal = Calendar.getInstance();
        int h = cal.get(Calendar.HOUR_OF_DAY);
        int m = cal.get(Calendar.MINUTE);

        for (ScheduleEntry e : schedules) {
            if (!e.enabled) continue;
            // If already inside the window — nearest event is the window end
            long candidate = e.isActiveNow(h, m) ? e.nextEndMillis() : e.nextStartMillis();
            if (candidate < nearest) nearest = candidate;
        }

        if (nearest == Long.MAX_VALUE || nearest <= now) {
            cancelAlarm();
            return;
        }

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nearest, getAlarmIntent());
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, nearest, getAlarmIntent());
        }

        Log.d(TAG, "scheduleNext: alarm in " + ((nearest - now) / 1000 / 60) + " min");
    }

    /**
     * Static variant for use from BootReceiver where a full RestrictionsScheduler
     * instance is not available. Reads schedules from SharedPreferences and sets
     * the next AlarmManager alarm. Shares getAlarmIntent() so the PendingIntent
     * is identical to the one used by the instance method.
     */
    public static void scheduleNextStatic(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_SCHEDULES, null);
        if (json == null || json.isEmpty()) return;

        List<ScheduleEntry> schedules = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                schedules.add(ScheduleEntry.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            Log.e(TAG, "scheduleNextStatic: parse error", e);
            return;
        }

        long now     = System.currentTimeMillis();
        long nearest = Long.MAX_VALUE;
        Calendar cal = Calendar.getInstance();
        int h = cal.get(Calendar.HOUR_OF_DAY);
        int m = cal.get(Calendar.MINUTE);

        for (ScheduleEntry e : schedules) {
            if (!e.enabled) continue;
            long candidate = e.isActiveNow(h, m) ? e.nextEndMillis() : e.nextStartMillis();
            if (candidate < nearest) nearest = candidate;
        }

        if (nearest == Long.MAX_VALUE || nearest <= now) return;

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        PendingIntent pi = getAlarmIntent(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nearest, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, nearest, pi);
        }
        Log.d(TAG, "scheduleNextStatic: alarm in " + ((nearest - now) / 1000 / 60) + " min");
    }

    private void cancelAlarm() {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(getAlarmIntent(context));
    }

    private PendingIntent getAlarmIntent() {
        return getAlarmIntent(context);
    }

    private static PendingIntent getAlarmIntent(Context context) {
        Intent intent = new Intent(context, SchedulerReceiver.class);
        intent.setAction(ACTION_SCHEDULER_TICK);
        return PendingIntent.getBroadcast(
                context,
                SCHEDULER_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    // =========================================================================
    // tick() — called from ShappkyService on ACTION_SCHEDULER_TICK
    // =========================================================================

    /**
     * Main scheduler method. Add to ShappkyService.onStartCommand():
     *   case "SCHEDULER_TICK":
     *       scheduler.tick();
     *       break;
     *
     * Computes the diff of active/inactive schedules, applies or removes protection,
     * optionally launches apps, then schedules the next alarm.
     * All heavy operations run on the executor thread.
     */
    public void tick() {
        executor.execute(() -> {
            Calendar cal = Calendar.getInstance();
            int hour   = cal.get(Calendar.HOUR_OF_DAY);
            int minute = cal.get(Calendar.MINUTE);

            List<ScheduleEntry> schedules    = getSchedules();
            Set<String>         wasProtected = getTempProtectedPackages();

            // Compute what should be protected right now
            Set<String> shouldBeProtected = new HashSet<>();
            for (ScheduleEntry e : schedules) {
                if (e.isActiveNow(hour, minute)) shouldBeProtected.add(e.packageName);
            }

            Set<String> newlyActivated   = diff(shouldBeProtected, wasProtected);
            Set<String> newlyDeactivated = diff(wasProtected, shouldBeProtected);

            // Persist new state immediately (before operations, so other managers
            // see the updated temp_protected during concurrent calls)
            saveTempProtectedPackages(shouldBeProtected);

            boolean use24h = android.text.format.DateFormat.is24HourFormat(context);

            // ── Activations ──────────────────────────────────────────────────
            for (String pkg : newlyActivated) {
                ScheduleEntry entry = findActiveEntry(schedules, pkg, hour, minute);
                if (entry == null) continue;
                Log.d(TAG, "tick: activating " + pkg);

                if ((entry.protectFlags & PROTECT_BG_RESTRICTIONS) != 0) {
                    boolean hasLaunch = (entry.onActivateAction == ON_ACTIVATE_LAUNCH_APP);
                    String outcome    = liftBackgroundRestriction(pkg);
                    SchedulerLog.logLift(context, pkg, outcome, hasLaunch, use24h);
                }

                if ((entry.protectFlags & PROTECT_SLEEP_MODE) != 0) {
                    shellManager.unfreezePackage(pkg);
                }

                if (entry.onActivateAction == ON_ACTIVATE_LAUNCH_APP) {
                    handler.post(() -> launchApp(pkg));
                }
            }

            // ── Deactivations ────────────────────────────────────────────────
            for (String pkg : newlyDeactivated) {
                ScheduleEntry entry = findEntryForPackage(schedules, pkg);
                if (entry == null) continue;
                Log.d(TAG, "tick: deactivating " + pkg);

                boolean forceStop = isForceStopMode();

                if ((entry.protectFlags & PROTECT_BG_RESTRICTIONS) != 0) {
                    String outcome = restoreBackgroundRestriction(pkg, forceStop);
                    SchedulerLog.logRestore(context, pkg, outcome, forceStop, use24h);
                } else if ((entry.protectFlags & PROTECT_AUTO_KILL) != 0) {
                    // Restrictions were not lifted, but Auto-Kill protection has ended —
                    // stop the app
                    stopApp(pkg, forceStop);
                    SchedulerLog.logRestore(context, pkg, "ok", forceStop, use24h);
                }
                // PROTECT_SLEEP_MODE: package removed from temp_protected;
                // next call to freezeBackgroundRestrictedApps() will freeze it normally.
            }

            // Schedule the next event
            scheduleNext();
        });
    }

    // =========================================================================
    // Lift and restore restrictions
    // =========================================================================

    /**
     * Lifts all appops restrictions for a package.
     * @return "ok" / "error" / "denied" / "skipped"
     */
    private String liftBackgroundRestriction(String packageName) {
        if (!backgroundAppManager.getBackgroundRestrictedApps().contains(packageName)) {
            Log.d(TAG, "liftBgRestriction: " + packageName + " not restricted, skipping");
            return "skipped";
        }

        restoreBatteryWhitelistIfNeeded(packageName);

        boolean anyDenied = false;
        boolean anyError  = false;

        String[] ops = {
            OP_RUN_ANY_IN_BG, OP_RUN_IN_BG, OP_FG,
            OP_FGS_FROM_BG, OP_WAKE_LOCK, OP_ALARM, OP_BOOT
        };

        for (String op : ops) {
            ShellManager.ShellResult r = shellManager.runShellCommandForResult(
                    "cmd appops set --user current " + packageName + " " + op + " allow");
            if (!r.succeeded()) {
                String out = r.output().toLowerCase(Locale.US);
                if (out.contains("permission denied") || out.contains("not allowed")) {
                    anyDenied = true;
                } else {
                    anyError = true;
                }
            }
        }

        if (anyDenied) return "denied";
        if (anyError)  return "error";
        return "ok";
    }

    /**
     * Restores appops restrictions and stops the app.
     * @param forceStop true = am force-stop, false = am kill
     * @return "ok" / "error" / "skipped"
     */
    private String restoreBackgroundRestriction(String packageName, boolean forceStop) {
        if (!backgroundAppManager.getBackgroundRestrictedApps().contains(packageName)) {
            Log.d(TAG, "restoreBgRestriction: " + packageName + " no longer restricted, skip");
            return "skipped";
        }

        boolean isHard   = backgroundAppManager.isHardRestricted(packageName);
        boolean anyError = false;

        if (isHard) {
            String[] opsHard = {
                OP_FG, OP_RUN_ANY_IN_BG, OP_RUN_IN_BG,
                OP_FGS_FROM_BG, OP_WAKE_LOCK, OP_ALARM, OP_BOOT
            };
            for (String op : opsHard) {
                ShellManager.ShellResult r = shellManager.runShellCommandForResult(
                        "cmd appops set --user current " + packageName + " " + op + " ignore");
                if (!r.succeeded()) anyError = true;
            }
            applyBatteryWhitelistRemovalIfNeeded(packageName);
        } else {
            ShellManager.ShellResult r = shellManager.runShellCommandForResult(
                    "cmd appops set --user current " + packageName
                    + " " + OP_RUN_ANY_IN_BG + " ignore");
            if (!r.succeeded()) anyError = true;
        }

        stopApp(packageName, forceStop);

        return anyError ? "error" : "ok";
    }

    /** Runs am force-stop or am kill depending on forceStop. */
    private void stopApp(String packageName, boolean forceStop) {
        String cmd = (forceStop ? "am force-stop " : "am kill ") + packageName;
        shellManager.runShellCommandForResult(cmd);
        Log.d(TAG, "stopApp: " + cmd);
    }

    /**
     * Determines stop mode from KEY_AUTO_KILL_TYPE setting.
     * KEY_AUTO_KILL_TYPE: 0 = am force-stop (default), 1 = am kill
     */
    private boolean isForceStopMode() {
        return prefs.getInt(KEY_AUTO_KILL_TYPE, 0) == 0;
    }

    // =========================================================================
    // App launch
    // =========================================================================

    /** Launches the launcher activity. Must be called on the main thread (via handler.post). */
    private void launchApp(String packageName) {
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                context.startActivity(intent);
                Log.d(TAG, "launchApp: launched " + packageName);
            } else {
                Log.w(TAG, "launchApp: no launcher activity for " + packageName);
            }
        } catch (Exception e) {
            Log.e(TAG, "launchApp: failed " + packageName, e);
        }
    }

    // =========================================================================
    // Battery whitelist helpers (mirrors logic from BackgroundAppManager)
    // =========================================================================

    private boolean isInBatteryWhitelist(String packageName) {
        String output = shellManager.runShellCommandAndGetFullOutput("dumpsys deviceidle whitelist");
        if (output == null) return false;
        for (String line : output.split("\n")) {
            String t = line.trim();
            if (t.startsWith("user,") && t.contains("," + packageName + ",")) return true;
        }
        return false;
    }

    private void applyBatteryWhitelistRemovalIfNeeded(String packageName) {
        if (!isInBatteryWhitelist(packageName)) return;
        ShellManager.ShellResult r = shellManager.runShellCommandForResult(
                "cmd deviceidle whitelist -" + packageName);
        if (r.succeeded()) {
            Set<String> removed = backgroundAppManager.getBatteryWhitelistRemoved();
            removed.add(packageName);
            backgroundAppManager.saveBatteryWhitelistRemoved(removed);
        }
    }

    private void restoreBatteryWhitelistIfNeeded(String packageName) {
        Set<String> removed = backgroundAppManager.getBatteryWhitelistRemoved();
        if (!removed.contains(packageName)) return;
        shellManager.runShellCommandForResult("cmd deviceidle whitelist +" + packageName);
        removed.remove(packageName);
        backgroundAppManager.saveBatteryWhitelistRemoved(removed);
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    /** Set difference: elements in a that are not in b. */
    private static Set<String> diff(Set<String> a, Set<String> b) {
        Set<String> result = new HashSet<>(a);
        result.removeAll(b);
        return result;
    }

    private ScheduleEntry findActiveEntry(List<ScheduleEntry> list, String pkg,
                                          int hour, int minute) {
        for (ScheduleEntry e : list) {
            if (e.packageName.equals(pkg) && e.isActiveNow(hour, minute)) return e;
        }
        return null;
    }

    private ScheduleEntry findEntryForPackage(List<ScheduleEntry> list, String pkg) {
        for (ScheduleEntry e : list) {
            if (e.packageName.equals(pkg)) return e;
        }
        return null;
    }

    private void saveTempProtectedPackages(Set<String> packages) {
        prefs.edit().putStringSet(KEY_TEMP_PROTECTED, new HashSet<>(packages)).apply();
    }
}
