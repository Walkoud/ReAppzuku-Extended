package com.gree1d.reappzuku;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppTriggersAnalyzer {

    private static final String TAG = "AppTriggersAnalyzer";

    public static final class TriggerInfo {

        public enum Group { ACTIVE_NOW, CAN_WAKE, OTHER }

        public enum Severity { HIGH, MEDIUM, LOW, INFO }

        public final Group    group;
        public final String   category;
        public final String   detail;
        public final String   explanation;
        public final Severity severity;

        public TriggerInfo(Group group, String category, String detail,
                           String explanation, Severity severity) {
            this.group       = group;
            this.category    = category;
            this.detail      = detail;
            this.explanation = explanation;
            this.severity    = severity;
        }

        public TriggerInfo(String category, String detail,
                           String explanation, Severity severity) {
            this(Group.OTHER, category, detail, explanation, severity);
        }
    }

    static final class AlarmEntry {

        final String  type;
        final String  tag;
        final long    fireDiffMs;
        final long    intervalMs;
        final boolean exact;
        final boolean whileIdle;
        final boolean isWakeup;

        AlarmEntry(String type, String tag, long fireDiffMs, long intervalMs,
                   boolean exact, boolean whileIdle, boolean isWakeup) {
            this.type       = type;
            this.tag        = tag;
            this.fireDiffMs = fireDiffMs;
            this.intervalMs = intervalMs;
            this.exact      = exact;
            this.whileIdle  = whileIdle;
            this.isWakeup   = isWakeup;
        }

        boolean isClockAlarm() {
            return tag != null && (tag.contains("AlarmClock") || tag.contains("ALARM_CLOCK"));
        }
    }

    static final class AlarmDumpsysParser {

        private static final Pattern TYPE_LINE_PAT = Pattern.compile(
                "^\\s*(RTC_WAKEUP|RTC|ELAPSED_WAKEUP|ELAPSED)\\s+#\\d+");
        private static final Pattern TAG_PAT        = Pattern.compile("\\btag=(\\S+)");
        private static final Pattern WINDOW_PAT     = Pattern.compile("\\bwindow=(-?\\d+)");
        private static final Pattern INTERVAL_PAT   = Pattern.compile("\\binterval=(\\d+)");
        private static final Pattern FLAGS_PAT      = Pattern.compile("\\bflgs=0x([0-9a-fA-F]+)");
        private static final Pattern ELAPSED_HR_PAT = Pattern.compile(
                "whenElapsed=\\+((?:(\\d+)h)?(?:(\\d+)m)?(\\d+)s)");
        private static final Pattern ELAPSED_MS_PAT = Pattern.compile("whenElapsed=(\\d+)");
        private static final Pattern WHEN_UNIX_PAT  = Pattern.compile("\\bwhen=(\\d{10,13})");
        private static final Pattern TOP_ENTRY_PAT  = Pattern.compile(
                "(\\S+)\\s+running,\\s*(\\d+)\\s+wakeups?,\\s*(\\d+)\\s+alarms?:\\s*\\d+:([\\w.]+)\\s+tag=(\\S+)");

        List<AlarmEntry> parseEntries(String output, String packageName) {
            List<AlarmEntry> result = new ArrayList<>();
            String       curType     = null;
            boolean      curIsWakeup = false;
            List<String> block       = new ArrayList<>();

            for (String line : output.split("\n")) {
                Matcher mType = TYPE_LINE_PAT.matcher(line);
                if (mType.find()) {
                    if (curType != null) {
                        AlarmEntry e = parseBlock(block, curType, curIsWakeup, packageName);
                        if (e != null) result.add(e);
                    }
                    curType     = mType.group(1);
                    curIsWakeup = curType.contains("WAKEUP");
                    block.clear();
                } else if (curType != null) {
                    block.add(line);
                }
            }
            if (curType != null) {
                AlarmEntry e = parseBlock(block, curType, curIsWakeup, packageName);
                if (e != null) result.add(e);
            }
            return result;
        }

        private AlarmEntry parseBlock(List<String> lines, String type,
                                      boolean isWakeup, String packageName) {
            String  tag       = null;
            long    fireDiff  = Long.MAX_VALUE;
            long    interval  = 0;
            boolean exact     = false;
            boolean whileIdle = false;
            long    nowMs     = System.currentTimeMillis();
            long    bootMs    = nowMs - SystemClock.elapsedRealtime();

            for (String line : lines) {
                String t = line.trim();
                if (tag == null) {
                    Matcher m = TAG_PAT.matcher(t);
                    if (m.find()) tag = m.group(1);
                }
                if (fireDiff == Long.MAX_VALUE) fireDiff = parseFireDiff(t, nowMs, bootMs);
                Matcher mWin = WINDOW_PAT.matcher(t);
                if (mWin.find()) exact = Long.parseLong(mWin.group(1)) < 0;
                if (interval == 0) {
                    Matcher mIv = INTERVAL_PAT.matcher(t);
                    if (mIv.find()) interval = Long.parseLong(mIv.group(1));
                }
                if (!whileIdle) {
                    Matcher mFlg = FLAGS_PAT.matcher(t);
                    if (mFlg.find()) whileIdle = (Long.parseLong(mFlg.group(1), 16) & 0xCL) != 0;
                    if (t.contains("ALLOW_WHILE_IDLE") || t.contains("allowWhileIdle=true"))
                        whileIdle = true;
                }
            }
            if (tag == null || !tag.contains(packageName)) return null;
            return new AlarmEntry(type, tag, fireDiff, interval, exact, whileIdle, isWakeup);
        }

        private long parseFireDiff(String line, long nowMs, long bootMs) {
            Matcher mHr = ELAPSED_HR_PAT.matcher(line);
            if (mHr.find()) {
                long ms = 0;
                if (mHr.group(2) != null) ms += Long.parseLong(mHr.group(2)) * 3_600_000L;
                if (mHr.group(3) != null) ms += Long.parseLong(mHr.group(3)) * 60_000L;
                ms += Long.parseLong(mHr.group(4)) * 1_000L;
                return ms > 0 ? ms : Long.MAX_VALUE;
            }
            Matcher mUnix = WHEN_UNIX_PAT.matcher(line);
            if (mUnix.find()) {
                long diff = Long.parseLong(mUnix.group(1)) - nowMs;
                return diff > 0 ? diff : Long.MAX_VALUE;
            }
            Matcher mMs = ELAPSED_MS_PAT.matcher(line);
            if (mMs.find()) {
                long diff = bootMs + Long.parseLong(mMs.group(1)) - nowMs;
                return diff > 0 ? diff : Long.MAX_VALUE;
            }
            return Long.MAX_VALUE;
        }

        List<String> parseTopAlarms(String output, String packageName) {
            List<String> result = new ArrayList<>();
            boolean inTop = false;
            for (String line : output.split("\n")) {
                String t = line.trim();
                if (t.startsWith("Top Alarms:") || t.startsWith("Top alarm senders:")) {
                    inTop = true; continue;
                }
                if (inTop && (t.isEmpty() || (t.endsWith(":") && !t.startsWith("+")))) {
                    inTop = false; continue;
                }
                if (!inTop) continue;
                Matcher m = TOP_ENTRY_PAT.matcher(t);
                if (!m.find() || !m.group(4).equals(packageName)) continue;
                String shortTag = m.group(5)
                        .replaceAll("^\\*[^*]+\\*/", "")
                        .replaceAll(".*\\.([^.]+)$", "$1");
                result.add(shortTag + ":" + m.group(2) + "×wakeup/" + m.group(1));
                if (result.size() >= 3) break;
            }
            return result;
        }
    }

    private final ShellManager           shellManager;
    private final Context                context;
    private final AppTriggerAnalyzersExt ext;

    String cachedUid = null;

    public AppTriggersAnalyzer(Context context, ShellManager shellManager) {
        this.context      = context.getApplicationContext();
        this.shellManager = shellManager;
        this.ext          = new AppTriggerAnalyzersExt(context, shellManager, this);
    }

    public List<TriggerInfo> analyze(String packageName) {
        cachedUid = resolveUid(packageName);

        List<TriggerInfo> results = new ArrayList<>();

        safeAdd(results, () -> analyzeProcessState(packageName));
        safeAdd(results, () -> analyzeServicesAndBindings(packageName));
        safeAdd(results, () -> analyzeFgNotification(packageName));
        safeAdd(results, () -> analyzeWakelocks(packageName));
        safeAdd(results, () -> analyzeNetworkActivity(packageName));
        safeAdd(results, () -> analyzeSensors(packageName));
        safeAdd(results, () -> analyzeLocationRequests(packageName));
        safeAdd(results, () -> analyzeAudioFocus(packageName));
        safeAdd(results, () -> analyzeBluetooth(packageName));

        safeAdd(results, () -> ext.analyzeAlarms(packageName));
        safeAdd(results, () -> ext.analyzeJobs(packageName));
        safeAdd(results, () -> ext.analyzePendingIntents(packageName));
        safeAdd(results, () -> ext.analyzeExcessiveWakeups(packageName));
        safeAdd(results, () -> ext.analyzeContentObservers(packageName));
        safeAdd(results, () -> ext.analyzeFcmRegistration(packageName));
        safeAdd(results, () -> ext.analyzeAppOps(packageName));

        safeAdd(results, () -> ext.analyzeChainLaunch(packageName));
        safeAdd(results, () -> ext.analyzeBroadcastReceivers(packageName));
        safeAdd(results, () -> ext.analyzeBootReceivers(packageName));
        safeAdd(results, () -> ext.analyzeContentProviders(packageName));
        safeAdd(results, () -> ext.analyzeSyncAdapters(packageName));
        safeAdd(results, () -> ext.analyzeDozeExemption(packageName));
        safeAdd(results, () -> ext.analyzeStandbyBucket(packageName));
        safeAdd(results, () -> ext.analyzeBatteryStats(packageName));
        safeAdd(results, () -> ext.analyzeBroadcastEfficiency(packageName));
        safeAdd(results, () -> ext.analyzeMultipleProcesses(packageName));
        safeAdd(results, () -> ext.analyzeAccessibilityAndIme(packageName));
        safeAdd(results, () -> ext.analyzeDeviceAdmin(packageName));
        safeAdd(results, () -> ext.analyzeUsageStats(packageName));

        if (results.isEmpty()) {
            results.add(new TriggerInfo(
                    context.getString(R.string.triggers_none_title),
                    context.getString(R.string.triggers_none_detail),
                    context.getString(R.string.triggers_none_explanation),
                    TriggerInfo.Severity.INFO));
        }

        return results;
    }

    public enum AppStatus { ACTIVE, BACKGROUND, CACHED }

    public AppStatus resolveAppStatus(String packageName) {
        Log.d(TAG, "resolveAppStatus: start pkg=" + packageName);
        try {

            String psOutput = shellManager.runShellCommandAndGetFullOutput(
                    "ps -eo pid,name | grep " + packageName);
            Log.d(TAG, "resolveAppStatus: ps output=" + (psOutput != null ? psOutput.trim() : "null"));

            if (psOutput == null || psOutput.trim().isEmpty()) {
                Log.d(TAG, "resolveAppStatus: result=null (not running)");
                return null;
            }

            String pid = null;
            for (String line : psOutput.trim().split("\n")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 2) continue;
                if (parts[1].equals(packageName)) {
                    pid = parts[0];
                    break;
                }
                if (pid == null && parts[1].startsWith(packageName)) {
                    pid = parts[0];
                }
            }

            if (pid == null) {
                Log.d(TAG, "resolveAppStatus: result=null (pid not parsed)");
                return null;
            }

            String adjStr = shellManager.runShellCommandAndGetFullOutput(
                    "cat /proc/" + pid + "/oom_score_adj");
            Log.d(TAG, "resolveAppStatus: pid=" + pid + " oom_score_adj=" + (adjStr != null ? adjStr.trim() : "null"));

            if (adjStr == null || adjStr.trim().isEmpty()) {

                Log.d(TAG, "resolveAppStatus: result=null (oom_score_adj unreadable)");
                return null;
            }

            int adj = Integer.parseInt(adjStr.trim());

            if (adj <= 224) {
                Log.d(TAG, "resolveAppStatus: result=ACTIVE (adj=" + adj + ")");
                return AppStatus.ACTIVE;
            }
            if (adj <= 499) {
                Log.d(TAG, "resolveAppStatus: result=BACKGROUND (adj=" + adj + ")");
                return AppStatus.BACKGROUND;
            }
            Log.d(TAG, "resolveAppStatus: result=CACHED (adj=" + adj + ")");
            return AppStatus.CACHED;

        } catch (NumberFormatException e) {
            Log.w(TAG, "resolveAppStatus: oom_score_adj parse error: " + e.getMessage());
            return null;
        } catch (Exception e) {
            Log.w(TAG, "resolveAppStatus failed: " + e.getMessage());
            return null;
        }
    }

    private interface Analyzer { List<TriggerInfo> run() throws Exception; }

    private void safeAdd(List<TriggerInfo> out, Analyzer a) {
        try {
            List<TriggerInfo> partial = a.run();
            if (partial != null) out.addAll(partial);
        } catch (Exception e) {
            Log.w(TAG, "analyzer failed: " + e.getMessage());
        }
    }

    private List<TriggerInfo> analyzeProcessState(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = shellManager.runShellCommandAndGetFullOutput(
                "dumpsys activity processes");
        if (output == null || output.trim().isEmpty()) return list;

        boolean inBlock    = false;
        int     adj        = Integer.MAX_VALUE;
        String  procState  = null;
        boolean persistent = false;

        Pattern procPat  = Pattern.compile(
                "ProcessRecord\\{[^}]+\\s" + Pattern.quote(packageName) + "/");
        Pattern adjPat   = Pattern.compile("\\badj=([-\\d]+)");
        Pattern statePat = Pattern.compile("\\bcurProcState=([A-Z_]+)");

        for (String line : output.split("\n")) {
            if (procPat.matcher(line).find()) {
                inBlock    = true;
                persistent = line.contains("persistent=true");
                continue;
            }
            if (inBlock && line.trim().startsWith("ProcessRecord{")
                    && !line.contains(packageName)) break;
            if (!inBlock) continue;

            Matcher mAdj = adjPat.matcher(line);
            if (mAdj.find() && adj == Integer.MAX_VALUE)
                adj = Integer.parseInt(mAdj.group(1));

            Matcher mState = statePat.matcher(line);
            if (mState.find() && procState == null)
                procState = mState.group(1);

            if (line.contains("persistent=true")) persistent = true;
        }

        if (procState == null && adj == Integer.MAX_VALUE) return list;

        String label = mapProcState(procState, adj);

        TriggerInfo.Severity severity;
        TriggerInfo.Group    group;
        if (persistent || "PERSISTENT".equals(procState)) {
            severity = TriggerInfo.Severity.HIGH;   group = TriggerInfo.Group.ACTIVE_NOW;
        } else if (adj != Integer.MAX_VALUE && adj <= 200) {
            severity = TriggerInfo.Severity.HIGH;   group = TriggerInfo.Group.ACTIVE_NOW;
        } else if (adj != Integer.MAX_VALUE && adj <= 500) {
            severity = TriggerInfo.Severity.MEDIUM; group = TriggerInfo.Group.ACTIVE_NOW;
        } else {
            severity = TriggerInfo.Severity.LOW;    group = TriggerInfo.Group.OTHER;
        }

        String detail = label + (adj != Integer.MAX_VALUE ? " (adj=" + adj + ")" : "");
        if (persistent) detail += ", " + context.getString(R.string.triggers_proc_persistent);

        list.add(new TriggerInfo(group,
                context.getString(R.string.triggers_cat_proc_state),
                detail,
                context.getString(R.string.triggers_proc_state_explanation, label),
                severity));
        return list;
    }

    private String mapProcState(String state, int adj) {
        if (state != null) switch (state) {
            case "PERSISTENT":               return "Persistent";
            case "TOP":                      return "Foreground (Top)";
            case "BOUND_TOP":                return "Bound to Top";
            case "FOREGROUND_SERVICE":       return "Foreground Service";
            case "BOUND_FOREGROUND_SERVICE": return "Bound FG Service";
            case "IMPORTANT_FOREGROUND":     return "Important Foreground";
            case "IMPORTANT_BACKGROUND":     return "Important Background";
            case "TRANSIENT_BACKGROUND":     return "Transient Background";
            case "BACKUP":                   return "Backup";
            case "SERVICE":                  return "Service";
            case "RECEIVER":                 return "Receiver";
            case "HOME":                     return "Home";
            case "LAST_ACTIVITY":            return "Last Activity";
            case "CACHED_ACTIVITY":          return "Cached (Activity)";
            case "CACHED_ACTIVITY_CLIENT":   return "Cached (Client)";
            case "CACHED_EMPTY":             return "Cached (Empty)";
            default:                         return state;
        }
        if (adj <= 0)   return "Persistent";
        if (adj <= 100) return "Foreground";
        if (adj <= 200) return "Visible";
        if (adj <= 500) return "Service";
        return "Cached";
    }

    private static final Pattern[] BINDER_PATS = {
            Pattern.compile("ProcessRecord\\{[^}]+\\s([\\w.]+)/"),
            Pattern.compile("client=ProcessRecord\\{[^}]+\\s([\\w.]+)/")
    };

    private List<TriggerInfo> analyzeServicesAndBindings(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = shellManager.runShellCommandAndGetFullOutput(
                "dumpsys activity services " + packageName);
        if (output == null || output.trim().isEmpty()) return list;

        boolean inBlock       = false;
        String  currentSvc    = null;
        String  fgType        = null;
        boolean killable      = true;
        String  notifChannel  = null;
        String  notifImport   = null;
        List<String> binders  = new ArrayList<>();

        for (String line : output.split("\n")) {
            String t = line.trim();

            if (t.contains("ServiceRecord") && t.contains(packageName)) {
                inBlock      = true;
                currentSvc   = extractServiceShortName(t, packageName);
                fgType       = null;
                killable     = true;
                notifChannel = null;
                notifImport  = null;
                continue;
            }
            if (inBlock && t.contains("ServiceRecord") && !t.contains(packageName)) {
                inBlock = false;
            }
            if (!inBlock) continue;

            Matcher mFgType = Pattern.compile("foregroundServiceType=(\\S+)").matcher(t);
            if (mFgType.find()) fgType = parseForegroundServiceType(mFgType.group(1));

            if (t.contains("stopWithTask=false") || t.contains("persistentProcess=true"))
                killable = false;

            Matcher mChan = Pattern.compile("channelId=([\\w.\\-]+)").matcher(t);
            if (mChan.find()) notifChannel = mChan.group(1);

            Matcher mImp = Pattern.compile("importance=(\\d+)").matcher(t);
            if (mImp.find()) notifImport = mapNotifImportance(Integer.parseInt(mImp.group(1)));


            if (t.contains("isForeground=true")) {
                String svcName = currentSvc != null ? currentSvc : packageName;
                StringBuilder detail = new StringBuilder(svcName);
                if (fgType       != null) detail.append(" [").append(fgType).append("]");
                if (notifChannel != null) detail.append(" · ch:").append(notifChannel);
                if (notifImport  != null) detail.append(" · notif:").append(notifImport);
                detail.append(" · ").append(killable
                        ? context.getString(R.string.triggers_fg_service_killable)
                        : context.getString(R.string.triggers_fg_service_not_killable));

                list.add(new TriggerInfo(TriggerInfo.Group.ACTIVE_NOW,
                        context.getString(R.string.triggers_cat_fg_service),
                        detail.toString(),
                        context.getString(R.string.triggers_fg_service_explanation),
                        TriggerInfo.Severity.HIGH));
            }


            if ((t.contains("START_STICKY") || t.contains("startRequested=true"))
                    && !t.contains("isForeground=true")) {
                list.add(new TriggerInfo(TriggerInfo.Group.ACTIVE_NOW,
                        context.getString(R.string.triggers_cat_sticky),
                        currentSvc != null ? currentSvc : packageName,
                        context.getString(R.string.triggers_sticky_explanation),
                        TriggerInfo.Severity.HIGH));
            }


            for (Pattern bp : BINDER_PATS) {
                Matcher m = bp.matcher(t);
                if (m.find()) {
                    String pkg = m.group(1);
                    if (!pkg.equals(packageName) && !pkg.equals("android")
                            && !binders.contains(pkg)) binders.add(pkg);
                }
            }
        }

        if (!binders.isEmpty()) {
            StringBuilder detail = new StringBuilder();
            StringBuilder expl   = new StringBuilder(
                    context.getString(R.string.triggers_bindings_explanation_base));
            for (int i = 0; i < Math.min(binders.size(), 4); i++) {
                if (i > 0) detail.append(", ");
                String p = binders.get(i);
                detail.append(resolveAppName(p)).append(" (").append(p).append(")");
            }
            if (binders.size() > 4)
                detail.append(context.getString(
                        R.string.triggers_bindings_overflow, binders.size() - 4));
            if (anyContains(binders, "google.gms", "gms"))
                expl.append(context.getString(R.string.triggers_bindings_gms_note));
            if (anyContains(binders, "push", "firebase", "fcm"))
                expl.append(context.getString(R.string.triggers_bindings_push_note));

            list.add(new TriggerInfo(TriggerInfo.Group.ACTIVE_NOW,
                    context.getString(R.string.triggers_cat_bindings, binders.size()),
                    detail.toString(), expl.toString(),
                    TriggerInfo.Severity.HIGH));
        }

        return list;
    }

    private String parseForegroundServiceType(String raw) {
        try {
            int mask = Integer.parseInt(raw);
            if (mask == 0) return "NONE";
            Object[][] bits = {
                {0x001,"DATA_SYNC"},{0x002,"MEDIA_PLAYBACK"},{0x004,"PHONE_CALL"},
                {0x008,"LOCATION"},{0x010,"CONNECTED_DEVICE"},{0x020,"MEDIA_PROJECTION"},
                {0x040,"CAMERA"},{0x080,"MICROPHONE"},{0x100,"HEALTH"},
                {0x200,"REMOTE_MESSAGING"},{0x400,"SYSTEM_EXEMPTED"},{0x800,"SHORT_SERVICE"}
            };
            StringBuilder sb = new StringBuilder();
            for (Object[] b : bits) if ((mask & (int) b[0]) != 0) {
                if (sb.length() > 0) sb.append("|");
                sb.append((String) b[1]);
            }
            return sb.length() > 0 ? sb.toString() : raw;
        } catch (NumberFormatException ignored) { return raw; }
    }

    private String mapNotifImportance(int imp) {
        switch (imp) {
            case 5: return "URGENT";
            case 4: return "HIGH";
            case 3: return "DEFAULT";
            case 2: return "LOW";
            case 1: return "MIN";
            default: return "NONE";
        }
    }

    private List<TriggerInfo> analyzeWakelocks(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();

        String powerOutput = shellManager.runShellCommandAndGetFullOutput("dumpsys power");
        if (powerOutput == null || powerOutput.trim().isEmpty()) return list;


        StringBuilder wlBlock = new StringBuilder();
        boolean inSection = false;
        for (String line : powerOutput.split("\n")) {
            if (line.trim().startsWith("Wake Locks:"))          { inSection = true;  continue; }
            if (inSection && line.trim().startsWith("Suspend Blockers:")) break;
            if (inSection) wlBlock.append(line).append("\n");
        }

        if (wlBlock.length() > 0) {
            Pattern heldMsPat  = Pattern.compile("held=(\\d+)ms");
            Pattern acquirePat = Pattern.compile("acq(?:uire)?[=:](\\d+)");
            Pattern releasePat = Pattern.compile("rel(?:ease)?[=:](\\d+)");
            Pattern heldLegacy = Pattern.compile("(\\d+m\\s*\\d+s|\\d+s)");
            Pattern tagPat     = Pattern.compile("'([^']{1,60})'");
            String  uid        = cachedUid;

            for (String line : wlBlock.toString().split("\n")) {
                boolean byUid = uid != null && line.contains("uid=" + uid);
                boolean byTag = line.contains(packageName);
                if (!byUid && !byTag) continue;


                String typeLabel, typeExplain;
                if      (line.contains("PARTIAL"))      { typeLabel="Partial";   typeExplain=context.getString(R.string.triggers_wakelock_partial_explain); }
                else if (line.contains("FULL"))         { typeLabel="Full";      typeExplain=context.getString(R.string.triggers_wakelock_full_explain); }
                else if (line.contains("SCREEN"))       { typeLabel="Screen";    typeExplain=context.getString(R.string.triggers_wakelock_screen_explain); }
                else if (line.contains("PROXIMITY"))    { typeLabel="Proximity"; typeExplain=context.getString(R.string.triggers_wakelock_proximity_explain); }
                else                                    { typeLabel="WakeLock";  typeExplain=context.getString(R.string.triggers_wakelock_generic_explain); }


                String tag = "";
                Matcher mTag = tagPat.matcher(line);
                if (mTag.find()) tag = mTag.group(1);


                String heldStr = "";
                Matcher mHeldMs = heldMsPat.matcher(line);
                if (mHeldMs.find()) {
                    heldStr = formatDuration(Long.parseLong(mHeldMs.group(1)));
                } else {
                    Matcher mLeg = heldLegacy.matcher(line);
                    if (mLeg.find()) heldStr = mLeg.group(1);
                }


                String acqRel = "";
                Matcher mAcq = acquirePat.matcher(line);
                Matcher mRel = releasePat.matcher(line);
                if (mAcq.find() && mRel.find()) {
                    acqRel = context.getString(R.string.triggers_wakelock_acq_rel,
                            Integer.parseInt(mAcq.group(1)), Integer.parseInt(mRel.group(1)));
                } else if (mAcq.find()) {
                    acqRel = context.getString(R.string.triggers_wakelock_acq_only,
                            Integer.parseInt(mAcq.group(1)));
                }

                StringBuilder detail = new StringBuilder(typeLabel);
                if (!tag.isEmpty())     detail.append(" · ").append(tag);
                if (!heldStr.isEmpty()) detail.append(" · ")
                        .append(context.getString(R.string.triggers_wakelock_detail_held, heldStr));
                if (!acqRel.isEmpty())  detail.append(" · ").append(acqRel);

                if (byTag && !byUid)
                    detail.append(" ").append(context.getString(R.string.triggers_wakelock_held_by_system));

                list.add(new TriggerInfo(TriggerInfo.Group.ACTIVE_NOW,
                        context.getString(R.string.triggers_cat_wakelock),
                        detail.toString(),
                        context.getString(R.string.triggers_wakelock_explanation, typeExplain),
                        TriggerInfo.Severity.HIGH));
            }
        }

        if (list.isEmpty()) {
            String bsOut = shellManager.runShellCommandAndGetFullOutput(
                    "dumpsys batterystats " + packageName);
            if (bsOut != null) {
                Pattern p = Pattern.compile(
                        "Wakelock\\s+(\\S+):\\s+(\\d+)ms realtime.*?\\((\\d+)\\s+times\\)",
                        Pattern.CASE_INSENSITIVE);
                for (String line : bsOut.split("\n")) {
                    Matcher m = p.matcher(line);
                    if (!m.find()) continue;
                    long heldMs = Long.parseLong(m.group(2));
                    int  count  = Integer.parseInt(m.group(3));
                    if (heldMs == 0 && count == 0) continue;
                    list.add(new TriggerInfo(TriggerInfo.Group.ACTIVE_NOW,
                            context.getString(R.string.triggers_cat_wakelock),
                            context.getString(R.string.triggers_wakelock_fallback_detail,
                                    m.group(1), formatDuration(heldMs), count),
                            context.getString(R.string.triggers_wakelock_fallback_explanation),
                            count > 10 || heldMs > 60_000
                                    ? TriggerInfo.Severity.HIGH : TriggerInfo.Severity.MEDIUM));
                }
            }
        }

        return list;
    }

    private List<TriggerInfo> analyzeNetworkActivity(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String uid = cachedUid;
        if (uid == null) return list;


        long rxBytes = 0, txBytes = 0;
        String netstats = null;
        try {
            netstats = shellManager.runShellCommandAndGetFullOutput(
                    "dumpsys netstats detail | grep -A5 uid=" + uid);
            if (netstats == null || netstats.trim().isEmpty())
                netstats = shellManager.runShellCommandAndGetFullOutput(
                        "dumpsys netstats | grep " + packageName);
        } catch (Exception e) { Log.w(TAG, "netstats failed: " + e.getMessage()); }
        if (netstats != null) {
            Matcher mRx = Pattern.compile("rxBytes=(\\d+)").matcher(netstats);
            Matcher mTx = Pattern.compile("txBytes=(\\d+)").matcher(netstats);
            while (mRx.find()) rxBytes += Long.parseLong(mRx.group(1));
            while (mTx.find()) txBytes += Long.parseLong(mTx.group(1));
        }

        List<String> established = new ArrayList<>();
        for (String procFile : new String[]{"tcp6", "tcp"}) {
            try {
                String raw = shellManager.runShellCommandAndGetFullOutput(
                        "grep ' " + uid + " ' /proc/net/" + procFile + " 2>/dev/null");
                if (raw == null) continue;
                for (String line : raw.split("\n")) {
                    String[] cols = line.trim().split("\\s+");
                    if (cols.length < 4) continue;
                    if (!"01".equals(cols[3])) continue;
                    String remote = hexToAddress(cols.length > 2 ? cols[2] : "", procFile.equals("tcp6"));
                    if (!established.contains(remote) && established.size() < 5)
                        established.add(remote);
                }
            } catch (Exception e) { Log.w(TAG, "proc/net/" + procFile + " failed: " + e.getMessage()); }
        }

        long total = rxBytes + txBytes;
        if (total < 10 * 1024 && established.isEmpty()) return list;

        StringBuilder detail = new StringBuilder();
        if (!established.isEmpty()) {
            detail.append(context.getString(
                    R.string.triggers_network_established, established.size()));
            detail.append(": ").append(String.join(", ", established));
        }
        if (total > 0) {
            if (detail.length() > 0) detail.append(", ");
            detail.append(context.getString(R.string.triggers_network_traffic,
                    formatBytes(rxBytes), formatBytes(txBytes)));
        }

        TriggerInfo.Severity sev = !established.isEmpty() ? TriggerInfo.Severity.HIGH
                : total > 1024 * 1024 ? TriggerInfo.Severity.MEDIUM : TriggerInfo.Severity.LOW;

        list.add(new TriggerInfo(TriggerInfo.Group.ACTIVE_NOW,
                context.getString(R.string.triggers_cat_network),
                detail.toString(),
                context.getString(R.string.triggers_network_explanation),
                sev));
        return list;
    }

    private String hexToAddress(String hex, boolean is6) {
        try {
            String[] parts = hex.split(":");
            if (parts.length < 2) return hex;
            int port = Integer.parseInt(parts[1], 16);
            if (!is6 && parts[0].length() == 8) {
                long a = Long.parseLong(parts[0], 16);
                return String.format("%d.%d.%d.%d:%d",
                        a & 0xFF, (a >> 8) & 0xFF, (a >> 16) & 0xFF, (a >> 24) & 0xFF, port);
            }
            return "*:" + port;
        } catch (Exception e) { return hex; }
    }

    private List<TriggerInfo> analyzeSensors(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();

        List<String> sensors = parseSensorService(packageName);
        if (sensors.isEmpty()) sensors = parseSensorsBatteryStats(packageName);
        if (sensors.isEmpty()) return list;

        boolean heavy = sensors.stream()
                .anyMatch(s -> s.startsWith("GPS") || s.startsWith("Gyro")
                        || s.startsWith("Baro"));

        list.add(new TriggerInfo(TriggerInfo.Group.ACTIVE_NOW,
                context.getString(R.string.triggers_cat_sensors, sensors.size()),
                String.join(", ", sensors.subList(0, Math.min(sensors.size(), 6))),
                context.getString(R.string.triggers_sensors_explanation),
                heavy ? TriggerInfo.Severity.HIGH : TriggerInfo.Severity.MEDIUM));
        return list;
    }

    private List<String> parseSensorService(String packageName) {
        List<String> result = new ArrayList<>();
        String output = shellManager.runShellCommandAndGetFullOutput("dumpsys sensorservice");
        if (output == null || output.trim().isEmpty()) return result;

        boolean inConn = false, relevant = false;
        List<String> found = new ArrayList<>();

        for (String line : output.split("\n")) {
            String t = line.trim();

            if (t.startsWith("Connection Number:") || t.startsWith("Active connections")) {
                if (relevant && !found.isEmpty())
                    for (String s : found) if (!result.contains(s)) result.add(s);
                inConn = true; relevant = false; found.clear();
                continue;
            }
            if (!inConn) continue;


            if (t.startsWith("packageName=") || t.startsWith("package=")
                    || t.startsWith("Identity="))
                relevant = t.contains(packageName);

            if (!relevant) continue;

            if (t.startsWith("Sensor:") || t.startsWith("SensorName=")
                    || t.startsWith("sensor=")) {
                String raw = t.replaceFirst("(?:Sensor:|SensorName=|sensor=)\\s*", "");
                int delim = raw.indexOf("  ");
                if (delim > 0) raw = raw.substring(0, delim).trim();

                String rate = "";
                Matcher mUs = Pattern.compile("samplingPeriod[Uu]s[=:]\\s*(\\d+)").matcher(t);
                if (mUs.find()) {
                    long us = Long.parseLong(mUs.group(1));
                    if (us > 0) rate = "@" + (1_000_000L / us) + "Hz";
                } else {
                    Matcher mHz = Pattern.compile("rate[=:]\\s*(\\d+)\\s*[Hh]z").matcher(t);
                    if (mHz.find()) rate = "@" + mHz.group(1) + "Hz";
                }
                String label = classifySensor(raw) + (rate.isEmpty() ? "" : " " + rate);
                if (!found.contains(label)) found.add(label);
            }
            if (t.contains("GNSS") || t.contains("Gnss") || t.contains("GPS"))
                if (!found.contains("GPS")) found.add("GPS");
        }
        if (relevant && !found.isEmpty())
            for (String s : found) if (!result.contains(s)) result.add(s);
        return result;
    }

    private List<String> parseSensorsBatteryStats(String packageName) {
        List<String> result = new ArrayList<>();
        String output = shellManager.runShellCommandAndGetFullOutput(
                "dumpsys batterystats " + packageName);
        if (output == null) return result;

        boolean inPkg = false;
        Pattern srPat = Pattern.compile(
                "Sensor\\s+(?:#)?(\\d+)[^:]*:\\s*(.*)", Pattern.CASE_INSENSITIVE);
        for (String line : output.split("\n")) {
            if (line.contains(packageName)) inPkg = true;
            if (!inPkg) continue;
            Matcher m = srPat.matcher(line.trim());
            if (!m.find()) continue;
            int    handle = Integer.parseInt(m.group(1));
            String rest   = m.group(2).trim();
            String dur    = "";
            Matcher mDur = Pattern.compile("(\\d+)ms").matcher(rest);
            if (mDur.find()) dur = " (" + formatDuration(Long.parseLong(mDur.group(1))) + ")";
            String label = sensorHandleToName(handle) + dur;
            if (!result.contains(label)) result.add(label);
        }
        return result;
    }

    private String sensorHandleToName(int h) {
        switch (h) {
            case 1:  return "Accelerometer";   case 2:  return "Magnetometer";
            case 3:  return "Orientation";     case 4:  return "Gyroscope";
            case 5:  return "Light";           case 6:  return "Pressure";
            case 8:  return "Proximity";       case 9:  return "Gravity";
            case 10: return "Linear Accel";    case 11: return "Rotation Vector";
            case 14: return "Uncal Magneto";   case 15: return "Game Rotation";
            case 16: return "Uncal Gyro";      case 17: return "Step Detector";
            case 18: return "Step Counter";    case 19: return "Geo Rotation";
            case 21: return "Tilt Detector";   case 24: return "Pickup Gesture";
            case 28: return "Stationary";      case 29: return "Motion Detect";
            case 30: return "Heart Beat";      case 34: return "OffBody Detect";
            case 35: return "Uncal Accel";
            default: return "Sensor#" + h;
        }
    }

    private String classifySensor(String raw) {
        String n = raw.toLowerCase();
        if (n.contains("accelero"))                             return "Accelerometer";
        if (n.contains("gyro"))                                 return "Gyroscope";
        if (n.contains("magnet"))                               return "Magnetometer";
        if (n.contains("barometer") || n.contains("pressure")) return "Barometer";
        if (n.contains("proximity"))                            return "Proximity";
        if (n.contains("light"))                                return "Light";
        if (n.contains("gravity"))                              return "Gravity";
        if (n.contains("rotation"))                             return "Rotation";
        if (n.contains("step") || n.contains("pedometer"))     return "Pedometer";
        if (n.contains("heart") || n.contains("pulse"))        return "HeartRate";
        if (n.contains("gnss") || n.contains("gps"))           return "GPS";
        if (n.contains("temperature"))                          return "Temperature";
        if (n.contains("humidity"))                             return "Humidity";
        return raw.length() > 20 ? raw.substring(0, 20) : raw;
    }

    private List<TriggerInfo> analyzeLocationRequests(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = shellManager.runShellCommandAndGetFullOutput("dumpsys location");
        if (output == null || output.trim().isEmpty()) return list;

        int     reqCount  = 0;
        boolean hasFg = false, hasBg = false;
        String  bestAcc   = null;
        long    minIvMs   = Long.MAX_VALUE;
        long    activeGps = 0;

        Pattern reqPat  = Pattern.compile(
                "LocationRequest\\[([^]]+)].*?" + Pattern.quote(packageName),
                Pattern.CASE_INSENSITIVE);
        Pattern ivPat   = Pattern.compile("interval=(\\d+)");
        Pattern fgPat   = Pattern.compile("foreground=(true|false)");
        Pattern gpsPat  = Pattern.compile("activeGps(?:TimeMs)?=(\\d+)");
        Pattern accPat  = Pattern.compile(
                "(PRIORITY_HIGH_ACCURACY|HIGH_ACCURACY|PRIORITY_BALANCED|BALANCED"
                + "|PRIORITY_LOW_POWER|LOW_POWER|PRIORITY_NO_POWER|NO_POWER|PASSIVE)",
                Pattern.CASE_INSENSITIVE);

        boolean inBlock = false;
        for (String line : output.split("\n")) {
            String t = line.trim();
            boolean hasPkg = t.contains(packageName);

            if (reqPat.matcher(t).find()
                    || (hasPkg && t.startsWith("LocationRequest"))) {
                inBlock = true; reqCount++;
                Matcher mA = accPat.matcher(t);
                if (mA.find()) bestAcc = mergeAccuracy(bestAcc, normalizeAccuracy(mA.group(1)));
                Matcher mI = ivPat.matcher(t);
                if (mI.find()) { long iv=Long.parseLong(mI.group(1)); if(iv>0&&iv<minIvMs) minIvMs=iv; }
                continue;
            }
            if (inBlock && (t.isEmpty() || (!hasPkg && t.startsWith("LocationRequest"))))
                inBlock = false;
            if (!inBlock && !hasPkg) continue;

            Matcher mF = fgPat.matcher(t);
            if (mF.find()) { if("true".equalsIgnoreCase(mF.group(1))) hasFg=true; else hasBg=true; }
            Matcher mG = gpsPat.matcher(t);
            if (mG.find()) activeGps += Long.parseLong(mG.group(1));
        }

        if (reqCount == 0) return list;

        StringBuilder detail = new StringBuilder(
                context.getString(R.string.triggers_location_requests, reqCount));
        if (bestAcc != null) detail.append(" · ").append(bestAcc);
        detail.append(" · ").append(hasFg && hasBg ? context.getString(R.string.triggers_location_fg_bg)
                : hasFg ? context.getString(R.string.triggers_location_fg)
                        : context.getString(R.string.triggers_location_bg));
        if (minIvMs != Long.MAX_VALUE)
            detail.append(context.getString(R.string.triggers_location_interval, formatInterval(minIvMs)));
        if (activeGps > 0)
            detail.append(context.getString(R.string.triggers_location_active_gps, formatDuration(activeGps)));

        TriggerInfo.Severity sev = hasBg && "HIGH_ACCURACY".equals(bestAcc)
                ? TriggerInfo.Severity.HIGH
                : "HIGH_ACCURACY".equals(bestAcc) || hasBg
                        ? TriggerInfo.Severity.MEDIUM : TriggerInfo.Severity.LOW;

        list.add(new TriggerInfo(TriggerInfo.Group.ACTIVE_NOW,
                context.getString(R.string.triggers_cat_location),
                detail.toString(),
                context.getString(hasBg ? R.string.triggers_location_bg_explanation
                                        : R.string.triggers_location_fg_explanation),
                sev));
        return list;
    }

    private List<TriggerInfo> analyzeFgNotification(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        try {
            String output = shellManager.runShellCommandAndGetFullOutput(
                    "dumpsys notification | grep -A20 'pkg=" + packageName + "'");
            if (output == null || output.trim().isEmpty()) return list;

            Pattern chanPat   = Pattern.compile("channelId=([\\w.\\-]+)");
            Pattern namePat   = Pattern.compile("name=([^,\\n]{1,40})");
            Pattern impPat    = Pattern.compile("importance=(\\d+)");
            Pattern soundPat  = Pattern.compile("sound=([^,\\n]+)");
            Pattern vibPat    = Pattern.compile("vibration=([^,\\n]+)");

            boolean inPkg = false;
            String chanId = null, chanName = null, importance = null;
            boolean hasSound = false, hasVibration = false;

            for (String line : output.split("\n")) {
                if (line.contains("pkg=" + packageName)) { inPkg = true; }
                if (inPkg && line.contains("pkg=") && !line.contains(packageName)) break;
                if (!inPkg) continue;

                Matcher mChan = chanPat.matcher(line);
                if (mChan.find() && chanId == null) chanId = mChan.group(1);

                Matcher mName = namePat.matcher(line);
                if (mName.find() && chanName == null) chanName = trimTo(mName.group(1).trim(), 30);

                Matcher mImp = impPat.matcher(line);
                if (mImp.find() && importance == null)
                    importance = mapNotifImportance(Integer.parseInt(mImp.group(1)));

                Matcher mSound = soundPat.matcher(line);
                if (mSound.find() && !mSound.group(1).trim().equals("null")) hasSound = true;

                Matcher mVib = vibPat.matcher(line);
                if (mVib.find() && !mVib.group(1).trim().equals("null")
                        && !mVib.group(1).trim().equals("[]")) hasVibration = true;
            }

            if (chanId == null && importance == null) return list;

            StringBuilder detail = new StringBuilder();
            if (chanName != null) detail.append(chanName);
            else if (chanId != null) detail.append(chanId);
            if (importance != null) detail.append(" · ").append(importance);
            if (hasSound)     detail.append(" · sound");
            if (hasVibration) detail.append(" · vibration");

            boolean isHighPriority = "URGENT".equals(importance) || "HIGH".equals(importance);
            list.add(new TriggerInfo(TriggerInfo.Group.ACTIVE_NOW,
                    context.getString(R.string.triggers_cat_fg_notification),
                    detail.toString(),
                    context.getString(isHighPriority
                            ? R.string.triggers_fg_notification_high_explanation
                            : R.string.triggers_fg_notification_explanation),
                    isHighPriority ? TriggerInfo.Severity.HIGH : TriggerInfo.Severity.MEDIUM));
        } catch (Exception e) {
            Log.w(TAG, "analyzeFgNotification failed: " + e.getMessage());
        }
        return list;
    }


    private List<TriggerInfo> analyzeAudioFocus(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();


        try {
            String audioOut = shellManager.runShellCommandAndGetFullOutput("dumpsys audio");
            if (audioOut != null) {
                boolean inFocusSection = false;
                String  focusType      = null;
                String  focusStream    = null;

                Pattern focusTypePat   = Pattern.compile(
                        "focusGain=([\\w_]+)", Pattern.CASE_INSENSITIVE);
                Pattern streamTypePat  = Pattern.compile(
                        "stream=(\\d+)");

                for (String line : audioOut.split("\n")) {
                    String t = line.trim();
                    if (t.startsWith("Audio Focus stack")) { inFocusSection = true; continue; }
                    if (inFocusSection && t.startsWith("---")) break;
                    if (!inFocusSection) continue;

                    if (!t.contains(packageName)) continue;

                    Matcher mFt = focusTypePat.matcher(t);
                    if (mFt.find() && focusType == null)
                        focusType = mapAudioFocusGain(mFt.group(1));

                    Matcher mSt = streamTypePat.matcher(t);
                    if (mSt.find() && focusStream == null)
                        focusStream = mapAudioStream(Integer.parseInt(mSt.group(1)));
                }

                if (focusType != null) {
                    String detail = focusType
                            + (focusStream != null ? " · stream:" + focusStream : "");
                    boolean isGain = focusType.contains("GAIN");
                    list.add(new TriggerInfo(TriggerInfo.Group.ACTIVE_NOW,
                            context.getString(R.string.triggers_cat_audio_focus),
                            detail,
                            context.getString(isGain
                                    ? R.string.triggers_audio_focus_gain_explanation
                                    : R.string.triggers_audio_focus_duck_explanation),
                            isGain ? TriggerInfo.Severity.HIGH : TriggerInfo.Severity.MEDIUM));
                }
            }
        } catch (Exception e) { Log.w(TAG, "analyzeAudioFocus/audio failed: " + e.getMessage()); }


        try {
            String msOut = shellManager.runShellCommandAndGetFullOutput("dumpsys media_session");
            if (msOut != null) {
                boolean inSession  = false;
                String  sessionTag = null;
                String  state      = null;

                Pattern tagPat    = Pattern.compile("tag=([^,\\s]+)");
                Pattern statePat  = Pattern.compile("state=(\\d+)");

                for (String line : msOut.split("\n")) {
                    String t = line.trim();
                    if (t.contains("package=" + packageName)
                            || t.contains("packageName=" + packageName)) {
                        inSession = true; sessionTag = null; state = null;
                    }
                    if (inSession && t.contains("package=")
                            && !t.contains(packageName)) inSession = false;
                    if (!inSession) continue;

                    Matcher mTag = tagPat.matcher(t);
                    if (mTag.find() && sessionTag == null)
                        sessionTag = trimTo(mTag.group(1), 30);

                    Matcher mSt = statePat.matcher(t);
                    if (mSt.find() && state == null)
                        state = mapMediaSessionState(Integer.parseInt(mSt.group(1)));
                }

                if (state != null) {
                    String detail = (sessionTag != null ? sessionTag + " · " : "") + state;
                    boolean isPlaying = "PLAYING".equals(state);

                    boolean alreadyReported = list.stream()
                            .anyMatch(i -> i.category.equals(
                                    context.getString(R.string.triggers_cat_audio_focus)));
                    if (!alreadyReported || !isPlaying) {
                        list.add(new TriggerInfo(TriggerInfo.Group.ACTIVE_NOW,
                                context.getString(R.string.triggers_cat_media_session),
                                detail,
                                context.getString(isPlaying
                                        ? R.string.triggers_media_session_playing_explanation
                                        : R.string.triggers_media_session_paused_explanation),
                                isPlaying ? TriggerInfo.Severity.HIGH : TriggerInfo.Severity.MEDIUM));
                    }
                }
            }
        } catch (Exception e) { Log.w(TAG, "analyzeAudioFocus/media_session failed: " + e.getMessage()); }

        return list;
    }

    private String mapAudioFocusGain(String raw) {
        switch (raw.toUpperCase()) {
            case "AUDIOFOCUS_GAIN":               return "GAIN (exclusive)";
            case "AUDIOFOCUS_GAIN_TRANSIENT":     return "GAIN_TRANSIENT";
            case "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK": return "GAIN_TRANSIENT_DUCK";
            case "AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE": return "GAIN_EXCLUSIVE";
            case "AUDIOFOCUS_LOSS":               return "LOSS";
            case "AUDIOFOCUS_LOSS_TRANSIENT":     return "LOSS_TRANSIENT";
            case "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK": return "LOSS_DUCK";
            default: return raw;
        }
    }

    private String mapAudioStream(int stream) {
        switch (stream) {
            case 0:  return "VOICE_CALL";
            case 1:  return "SYSTEM";
            case 2:  return "RING";
            case 3:  return "MUSIC";
            case 4:  return "ALARM";
            case 5:  return "NOTIFICATION";
            case 6:  return "BLUETOOTH_SCO";
            case 10: return "ACCESSIBILITY";
            default: return "STREAM_" + stream;
        }
    }

    private String mapMediaSessionState(int state) {
        switch (state) {
            case 0:  return "NONE";
            case 1:  return "STOPPED";
            case 2:  return "PAUSED";
            case 3:  return "PLAYING";
            case 4:  return "FAST_FORWARDING";
            case 5:  return "REWINDING";
            case 6:  return "BUFFERING";
            case 7:  return "ERROR";
            case 8:  return "CONNECTING";
            case 9:  return "SKIPPING_TO_PREVIOUS";
            case 10: return "SKIPPING_TO_NEXT";
            case 11: return "SKIPPING_TO_QUEUE_ITEM";
            default: return "STATE_" + state;
        }
    }


    private List<TriggerInfo> analyzeBluetooth(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();


        try {
            String btOut = shellManager.runShellCommandAndGetFullOutput("dumpsys bluetooth_manager");
            if (btOut != null) {
                boolean inScan  = false;
                int     scanCnt = 0;
                String  scanMode = null;

                Pattern modePat = Pattern.compile("scanMode=(\\w+)", Pattern.CASE_INSENSITIVE);

                for (String line : btOut.split("\n")) {
                    String t = line.trim();
                    if (t.startsWith("Scan clients:") || t.startsWith("Active scan clients"))
                        { inScan = true; continue; }
                    if (inScan && t.startsWith("---")) break;
                    if (!inScan) continue;

                    if (t.contains(packageName)) {
                        scanCnt++;
                        Matcher m = modePat.matcher(t);
                        if (m.find() && scanMode == null) scanMode = m.group(1);
                    }
                }

                if (scanCnt > 0) {
                    String detail = context.getString(R.string.triggers_ble_scan_count, scanCnt)
                            + (scanMode != null ? " · mode:" + scanMode : "");
                    boolean isLowLatency = scanMode != null
                            && scanMode.toUpperCase().contains("LOW_LATENCY");
                    list.add(new TriggerInfo(TriggerInfo.Group.ACTIVE_NOW,
                            context.getString(R.string.triggers_cat_ble_scan),
                            detail,
                            context.getString(isLowLatency
                                    ? R.string.triggers_ble_scan_low_latency_explanation
                                    : R.string.triggers_ble_scan_explanation),
                            isLowLatency ? TriggerInfo.Severity.HIGH : TriggerInfo.Severity.MEDIUM));
                }
            }
        } catch (Exception e) { Log.w(TAG, "analyzeBluetooth/manager failed: " + e.getMessage()); }


        try {
            String gattOut = shellManager.runShellCommandAndGetFullOutput("dumpsys gatt");
            if (gattOut != null) {
                int     connCnt    = 0;
                boolean inConn     = false;
                List<String> addrs = new ArrayList<>();

                Pattern addrPat = Pattern.compile(
                        "address=([0-9A-Fa-f:]{17})", Pattern.CASE_INSENSITIVE);

                for (String line : gattOut.split("\n")) {
                    String t = line.trim();
                    if (t.startsWith("GATT Connections:") || t.startsWith("Connections:"))
                        { inConn = true; continue; }
                    if (inConn && t.startsWith("---")) break;
                    if (!inConn) continue;

                    if (!t.contains(packageName)) continue;
                    connCnt++;
                    Matcher m = addrPat.matcher(t);
                    if (m.find() && addrs.size() < 3) addrs.add(m.group(1));
                }

                if (connCnt > 0) {
                    StringBuilder detail = new StringBuilder(
                            context.getString(R.string.triggers_gatt_conn_count, connCnt));
                    if (!addrs.isEmpty())
                        detail.append(": ").append(String.join(", ", addrs));
                    list.add(new TriggerInfo(TriggerInfo.Group.ACTIVE_NOW,
                            context.getString(R.string.triggers_cat_gatt),
                            detail.toString(),
                            context.getString(R.string.triggers_gatt_explanation),
                            TriggerInfo.Severity.HIGH));
                }
            }
        } catch (Exception e) { Log.w(TAG, "analyzeBluetooth/gatt failed: " + e.getMessage()); }

        return list;
    }


    private String resolveUid(String packageName) {
        String out = shellManager.runShellCommandAndGetFullOutput(
                "dumpsys package " + packageName + " | grep userId=");
        if (out == null) return null;
        for (String line : out.split("\n")) {
            Matcher m = Pattern.compile("userId=(\\d+)").matcher(line);
            if (m.find()) return m.group(1);
        }
        return null;
    }

    private String resolveAppName(String pkg) {
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(pkg, 0);
            return context.getPackageManager().getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException e) { return pkg; }
    }

    private String bucketValueToName(int v) {
        if (v <= 10) return "ACTIVE";
        if (v <= 20) return "WORKING_SET";
        if (v <= 30) return "FREQUENT";
        if (v <= 40) return "RARE";
        if (v <= 45) return "RESTRICTED";
        return "NEVER";
    }

    private String shortenAction(String action) {
        if (action.startsWith("android.intent.action.")) return action.substring(22);
        if (action.startsWith("android.net.conn."))      return action.substring(17);
        if (action.startsWith("android.net."))           return action.substring(12);
        if (action.startsWith("com.android."))           return action.substring(12);
        return action;
    }

    private String extractServiceShortName(String line, String packageName) {
        Matcher m = Pattern.compile("ServiceRecord\\{[^}]+\\s([\\w./]+)\\}").matcher(line);
        if (!m.find()) return null;
        String full = m.group(1);
        if (!full.contains("/")) return full;
        String cls = full.substring(full.indexOf('/') + 1);
        if (cls.startsWith("."))               return cls.substring(1);
        if (cls.startsWith(packageName + ".")) return cls.substring(packageName.length() + 1);
        return cls;
    }

    private String normalizeAccuracy(String raw) {
        String n = raw.toUpperCase();
        if (n.contains("HIGH"))    return "HIGH_ACCURACY";
        if (n.contains("BALANCE")) return "BALANCED";
        if (n.contains("LOW"))     return "LOW_POWER";
        return "NO_POWER";
    }

    private String mergeAccuracy(String cur, String cand) {
        if (cur == null) return cand;
        String[] ord = {"HIGH_ACCURACY","BALANCED","LOW_POWER","NO_POWER"};
        int ci=3, ca=3;
        for (int i=0;i<ord.length;i++) { if(ord[i].equals(cur)) ci=i; if(ord[i].equals(cand)) ca=i; }
        return ci <= ca ? cur : cand;
    }

    private String formatInterval(long ms) {
        long sec = ms / 1000;
        if (sec < 60)   return context.getString(R.string.triggers_alarms_interval_sec,  (int) sec);
        if (sec < 3600) return context.getString(R.string.triggers_alarms_interval_min,  (int)(sec/60));
        return             context.getString(R.string.triggers_alarms_interval_hour, (int)(sec/3600));
    }

    private String formatDuration(long ms) {
        long sec = ms / 1000;
        if (sec < 60)   return sec + context.getString(R.string.time_unit_sec);
        if (sec < 3600) return (sec/60) + context.getString(R.string.time_unit_min);
        return               (sec/3600) + context.getString(R.string.time_unit_hour);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024)            return bytes + " B";
        if (bytes < 1024*1024)       return (bytes/1024) + " KB";
        if (bytes < 1024L*1024*1024) return (bytes/(1024*1024)) + " MB";
        return (bytes/(1024L*1024*1024)) + " GB";
    }

    private static boolean anyContains(List<String> list, String... tokens) {
        for (String s : list) for (String t : tokens) if (s.contains(t)) return true;
        return false;
    }

    private static String trimTo(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
}
