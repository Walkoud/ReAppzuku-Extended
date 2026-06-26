package com.gree1d.reappzuku.utils.triggers.analyzers;

import android.content.Context;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.gree1d.reappzuku.R;
import com.gree1d.reappzuku.core.ShellManager;
import com.gree1d.reappzuku.core.AppDebugManager;
import com.gree1d.reappzuku.core.AppDebugManager.Category;
import com.gree1d.reappzuku.utils.triggers.AppTriggersAnalyzer;
import com.gree1d.reappzuku.utils.triggers.AppTriggersAnalyzer.TriggerInfo;

public class ProcessAnalyzer {

    private static final String FILE_NAME = "ProcessAnalyzer";

    private final AppTriggersAnalyzer analyzer;

    public ProcessAnalyzer(AppTriggersAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

// ---- CONST:FGS_TIMEOUT_PAT ----
    private static final Pattern FGS_TIMEOUT_PAT     = Pattern.compile("remainingTimeLimitMs=(\\d+)");


// ---- CONST:FGS_EXCEEDED_PAT ----
    private static final Pattern FGS_EXCEEDED_PAT    = Pattern.compile("timeLimitExceeded=(true|false)");


// ---- CONST:FGS_ALLOW_START_PAT ----
    private static final Pattern FGS_ALLOW_START_PAT = Pattern.compile("getFgsAllowStart=([A-Z_]+)");


// ---- CONST:FGS_BG_START_PAT ----
    private static final Pattern FGS_BG_START_PAT    = Pattern.compile("startedFromBg=(true|false)");


// ---- CONST:FGS_OPT_IN_PAT ----
    private static final Pattern FGS_OPT_IN_PAT      = Pattern.compile("fgsStartedWhileOptIn=(true|false)");


// ---- CONST:BINDER_PATS ----
    private static final Pattern[] BINDER_PATS = {
            Pattern.compile("ProcessRecord\\{[^}]+\\s([\\w.]+)/"),
            Pattern.compile("client=ProcessRecord\\{[^}]+\\s([\\w.]+)/")
    };


// ---- analyzeProcessState ----
    public List<TriggerInfo> analyzeProcessState(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                "dumpsys activity processes");
        if (output == null || output.trim().isEmpty()) return list;

        boolean inBlock    = false;
        int     adj        = Integer.MAX_VALUE;
        String  procState  = null;
        boolean persistent = false;

        Pattern procPat  = Pattern.compile(
                "ProcessRecord\\{[^}]+\\s" + Pattern.quote(packageName) + "/");
        Pattern adjPat   = Pattern.compile("\\badj=([-\\d]+)");
        Pattern statePat = Pattern.compile("\\bcurProcState=(\\w+)");

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
        AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": analyzeProcessState: pkg=" + packageName + " procState=" + procState + " adj=" + adj + " label=" + label + " persistent=" + persistent);

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
        if (persistent) detail += ", " + analyzer.getContext().getString(R.string.triggers_proc_persistent);

        list.add(new TriggerInfo(group,
                AppTriggersAnalyzer.KEY_CAT_PROC_STATE,
                analyzer.getContext().getString(R.string.triggers_cat_proc_state),
                detail,
                analyzer.getContext().getString(R.string.triggers_proc_state_explanation, label),
                severity));

        if (analyzer.apiLevel >= AppTriggersAnalyzer.API_PROCESS_FREEZER && analyzer.apiLevel <= Build.VERSION_CODES.TIRAMISU) {
            String pid = null;
            try {
                String psOut = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                        "ps -eo pid,name | grep " + packageName);
                if (psOut != null) {
                    for (String line : psOut.trim().split("\n")) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length >= 2 && parts[1].startsWith(packageName)) {
                            pid = parts[0]; break;
                        }
                    }
                }
            } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": frozen pid lookup failed", e); }
            if (isProcessFrozen(packageName, pid)) {
                AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": analyzeProcessState: process is FROZEN pkg=" + packageName + " pid=" + pid);
                list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                        AppTriggersAnalyzer.KEY_CAT_PROC_STATE,
                        analyzer.getContext().getString(R.string.triggers_cat_proc_state),
                        analyzer.getContext().getString(R.string.triggers_proc_frozen_detail),
                        analyzer.getContext().getString(R.string.triggers_proc_frozen_explanation),
                        TriggerInfo.Severity.INFO));
            }
        }

        return list;
    }

// ---- mapProcState ----
    public String mapProcState(String state, int adj) {
        if (state != null) {
            switch (state) {
                case "0":  return "Persistent";
                case "1":  return "Persistent (UI)";
                case "2":  return "Foreground (Top)";
                case "3":  return "Bound to Top";
                case "4":  return "Foreground Service";
                case "5":  return "Top (Sleeping)";
                case "6":  return "Important Foreground";
                case "7":  return "Important Background";
                case "8":  return "Transient Background";
                case "9":  return "Backup";
                case "10": return "Service";
                case "11": return "Receiver";
                case "12": return "Home";
                case "13": return "Last Activity";
                case "14": return "Cached (Activity)";
                case "15": return "Cached (Client)";
                case "16": return "Cached (Empty)";
                case "19": return "Cached (Empty)";
                case "20": return "Non-existent";
            }
        }
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

// ---- analyzeServicesAndBindings ----
    public List<TriggerInfo> analyzeServicesAndBindings(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                "dumpsys activity services " + packageName);
        if (output == null || output.trim().isEmpty()) return list;

        boolean inBlock       = false;
        String  currentSvc    = null;
        String  fgType        = null;
        boolean killable      = true;
        String  notifChannel  = null;
        String  notifImport   = null;
        boolean isForeground  = false;
        boolean isSticky      = false;
        boolean isBfslPush    = false;
        String  fgsAllowStartReason = null;
        List<String> binders  = new ArrayList<>();

        for (String line : output.split("\n")) {
            String t = line.trim();

            if (t.contains("ServiceRecord") && t.contains(packageName)) {
                if (inBlock) {
                    emitServiceTriggers(list, currentSvc, packageName, fgType, notifChannel,
                            notifImport, killable, isForeground, isSticky, isBfslPush,
                            fgsAllowStartReason);
                }
                inBlock      = true;
                currentSvc   = extractServiceShortName(t, packageName);
                fgType       = null;
                killable     = true;
                notifChannel = null;
                notifImport  = null;
                isForeground = false;
                isSticky     = false;
                isBfslPush   = false;
                fgsAllowStartReason = null;
                continue;
            }
            if (inBlock && t.contains("ServiceRecord") && !t.contains(packageName)) {
                emitServiceTriggers(list, currentSvc, packageName, fgType, notifChannel,
                        notifImport, killable, isForeground, isSticky, isBfslPush,
                        fgsAllowStartReason);
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

            if (t.contains("isForeground=true")) isForeground = true;
            if (t.contains("START_STICKY") || t.contains("startRequested=true")) isSticky = true;

            if (t.contains("getFgsAllowStart=PUSH_MESSAGING")
                    || t.contains("mAllowStart_noBinding=PUSH_MESSAGING")
                    || t.contains("code:PUSH_MESSAGING")) {
                isBfslPush = true;
            }

            if (analyzer.apiLevel >= AppTriggersAnalyzer.API_BAL_PRIVILEGES) {
                Matcher mAllow = FGS_ALLOW_START_PAT.matcher(t);
                if (mAllow.find()) {
                    String reason = mAllow.group(1);
                    if (!reason.equals("NONE") && !reason.equals("PUSH_MESSAGING")) {
                        fgsAllowStartReason = reason;
                    }
                }
            }

            if (analyzer.apiLevel >= AppTriggersAnalyzer.API_FGS_BG_BLOCKED && analyzer.apiLevel <= Build.VERSION_CODES.TIRAMISU) {
                Matcher mBg = FGS_BG_START_PAT.matcher(t);
                if (mBg.find() && "true".equals(mBg.group(1)) && fgsAllowStartReason == null) {
                    fgsAllowStartReason = "started-from-bg";
                }
                Matcher mOpt = FGS_OPT_IN_PAT.matcher(t);
                if (mOpt.find() && "true".equals(mOpt.group(1)) && fgsAllowStartReason == null) {
                    fgsAllowStartReason = "via-exemption";
                }
            }

            if (analyzer.apiLevel >= AppTriggersAnalyzer.API_MEDIA_PROCESSING) {
                Matcher mExceeded = FGS_EXCEEDED_PAT.matcher(t);
                if (mExceeded.find() && "true".equals(mExceeded.group(1))) {
                    list.add(new TriggerInfo(TriggerInfo.Group.ACTIVE_NOW,
                            AppTriggersAnalyzer.KEY_CAT_FGS_TIMEOUT,
                            analyzer.getContext().getString(R.string.triggers_cat_fgs_timeout_exceeded),
                            currentSvc + " · timeLimitExceeded",
                            analyzer.getContext().getString(R.string.triggers_fgs_timeout_exceeded_explanation),
                            TriggerInfo.Severity.HIGH));
                }
                Matcher mRemain = FGS_TIMEOUT_PAT.matcher(t);
                if (mRemain.find()) {
                    long remainMs = Long.parseLong(mRemain.group(1));
                    if (remainMs < 30 * 60 * 1000L) {
                        list.add(new TriggerInfo(TriggerInfo.Group.ACTIVE_NOW,
                                AppTriggersAnalyzer.KEY_CAT_FGS_NEAR_TIMEOUT,
                                analyzer.getContext().getString(R.string.triggers_cat_fgs_near_timeout),
                                currentSvc + " · remaining=" + analyzer.formatDuration(remainMs),
                                analyzer.getContext().getString(R.string.triggers_fgs_near_timeout_explanation),
                                TriggerInfo.Severity.MEDIUM));
                    }
                }
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

        if (inBlock) {
            emitServiceTriggers(list, currentSvc, packageName, fgType, notifChannel,
                    notifImport, killable, isForeground, isSticky, isBfslPush,
                    fgsAllowStartReason);
        }

        if (!binders.isEmpty()) {
            AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": analyzeServicesAndBindings: found " + binders.size() + " binder(s): " + binders);
            StringBuilder detail = new StringBuilder();
            StringBuilder expl   = new StringBuilder(
                    analyzer.getContext().getString(R.string.triggers_bindings_explanation_base));
            for (int i = 0; i < Math.min(binders.size(), 4); i++) {
                if (i > 0) detail.append(", ");
                String p = binders.get(i);
                detail.append(analyzer.resolveAppName(p)).append(" (").append(p).append(")");
            }
            if (binders.size() > 4)
                detail.append(analyzer.getContext().getString(
                        R.string.triggers_bindings_overflow, binders.size() - 4));
            if (analyzer.anyContains(binders, "google.gms", "gms"))
                expl.append(analyzer.getContext().getString(R.string.triggers_bindings_gms_note));
            if (analyzer.anyContains(binders, "push", "firebase", "fcm"))
                expl.append(analyzer.getContext().getString(R.string.triggers_bindings_push_note));

            list.add(new TriggerInfo(TriggerInfo.Group.ACTIVE_NOW,
                    AppTriggersAnalyzer.KEY_CAT_BINDINGS,
                    analyzer.getContext().getString(R.string.triggers_cat_bindings, binders.size()),
                    detail.toString(), expl.toString(),
                    TriggerInfo.Severity.HIGH));
        }

        if (analyzer.apiLevel >= AppTriggersAnalyzer.API_FGS_BG_BLOCKED && analyzer.apiLevel <= Build.VERSION_CODES.TIRAMISU) {
            list.addAll(analyzeFgsStartBlocked(packageName));
        }

        return list;
    }

// ---- emitServiceTriggers ----
    public void emitServiceTriggers(List<TriggerInfo> list, String currentSvc,
            String packageName, String fgType, String notifChannel, String notifImport,
            boolean killable, boolean isForeground, boolean isSticky, boolean isBfslPush,
            String fgsAllowStartReason) {
        if (isForeground) {
            AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": emitServiceTriggers: FGS detected svc=" + currentSvc + " fgType=" + fgType + " killable=" + killable + " isBfslPush=" + isBfslPush);
            String svcName = currentSvc != null ? currentSvc : packageName;
            StringBuilder detail = new StringBuilder(svcName);
            if (fgType       != null) detail.append(" [").append(fgType).append("]");
            if (notifChannel != null) detail.append(" · ch:").append(notifChannel);
            if (notifImport  != null) detail.append(" · notif:").append(notifImport);
            detail.append(" · ").append(killable
                    ? analyzer.getContext().getString(R.string.triggers_fg_service_killable)
                    : analyzer.getContext().getString(R.string.triggers_fg_service_not_killable));
            if (isBfslPush) detail.append(" · via FCM");
            if (fgsAllowStartReason != null) detail.append(" · via ").append(fgsAllowStartReason);
            list.add(new TriggerInfo(TriggerInfo.Group.ACTIVE_NOW,
                    AppTriggersAnalyzer.KEY_CAT_FG_SERVICE,
                    analyzer.getContext().getString(R.string.triggers_cat_fg_service),
                    detail.toString(),
                    analyzer.getContext().getString(R.string.triggers_fg_service_explanation),
                    TriggerInfo.Severity.HIGH));
        }
        if (isSticky && !isForeground) {
            list.add(new TriggerInfo(TriggerInfo.Group.ACTIVE_NOW,
                    AppTriggersAnalyzer.KEY_CAT_STICKY,
                    analyzer.getContext().getString(R.string.triggers_cat_sticky),
                    currentSvc != null ? currentSvc : packageName,
                    analyzer.getContext().getString(R.string.triggers_sticky_explanation),
                    TriggerInfo.Severity.HIGH));
        }
    }

// ---- parseForegroundServiceType ----
    public String parseForegroundServiceType(String raw) {
        try {
            int mask = Integer.parseInt(raw);
            if (mask == 0) return "NONE";
            Object[][] bits = {
                {0x001,"DATA_SYNC"},{0x002,"MEDIA_PLAYBACK"},{0x004,"PHONE_CALL"},
                {0x008,"LOCATION"},{0x010,"CONNECTED_DEVICE"},{0x020,"MEDIA_PROJECTION"},
                {0x040,"CAMERA"},{0x080,"MICROPHONE"},{0x100,"HEALTH"},
                {0x200,"REMOTE_MESSAGING"},{0x400,"SYSTEM_EXEMPTED"},{0x800,"SHORT_SERVICE"},
                {0x1000,"MEDIA_PROCESSING"}
            };
            StringBuilder sb = new StringBuilder();
            for (Object[] b : bits) if ((mask & (int) b[0]) != 0) {
                if (sb.length() > 0) sb.append("|");
                sb.append((String) b[1]);
            }
            return sb.length() > 0 ? sb.toString() : raw;
        } catch (NumberFormatException ignored) { return raw; }
    }

// ---- mapNotifImportance ----
    public String mapNotifImportance(int imp) {
        switch (imp) {
            case 5: return "URGENT";
            case 4: return "HIGH";
            case 3: return "DEFAULT";
            case 2: return "LOW";
            case 1: return "MIN";
            default: return "NONE";
        }
    }

// ---- analyzeFgNotification ----
    public List<TriggerInfo> analyzeFgNotification(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        try {
            String output = analyzer.getShellManager().runShellCommandAndGetFullOutput(
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
                if (mName.find() && chanName == null) chanName = analyzer.trimTo(mName.group(1).trim(), 30);

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
                    AppTriggersAnalyzer.KEY_CAT_FG_NOTIFICATION,
                    analyzer.getContext().getString(R.string.triggers_cat_fg_notification),
                    detail.toString(),
                    analyzer.getContext().getString(isHighPriority
                            ? R.string.triggers_fg_notification_high_explanation
                            : R.string.triggers_fg_notification_explanation),
                    isHighPriority ? TriggerInfo.Severity.HIGH : TriggerInfo.Severity.MEDIUM));
        } catch (Exception e) {
            AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": analyzeFgNotification failed", e);
        }
        return list;
    }

// ---- analyzeFgsStartBlocked ----
    public List<TriggerInfo> analyzeFgsStartBlocked(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        try {
            String logcat = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                    "logcat -d -t 200 -s AndroidRuntime:E ActivityManager:W | grep " + packageName);
            if (logcat != null
                    && logcat.contains("ForegroundServiceStartNotAllowedException")
                    && logcat.contains(packageName)) {
                list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                        AppTriggersAnalyzer.KEY_CAT_FGS_BLOCKED,
                        analyzer.getContext().getString(R.string.triggers_cat_fg_service),
                        analyzer.getContext().getString(R.string.triggers_fgs_blocked_detail),
                        analyzer.getContext().getString(R.string.triggers_fgs_blocked_explanation),
                        TriggerInfo.Severity.MEDIUM));
            }
        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": fgs blocked logcat failed", e); }
        return list;
    }

// ---- isProcessFrozen ----
    public boolean isProcessFrozen(String packageName, String pid) {
        try {
            String out = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                    "dumpsys activity | grep -A3 'Apps frozen'");
            if (out != null && pid != null && out.contains(pid)) return true;
        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": frozen check dumpsys failed", e); }

        if (analyzer.getCachedUid() != null && pid != null) {
            try {
                String freeze = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                        "cat /sys/fs/cgroup/uid_" + analyzer.getCachedUid() + "/pid_" + pid + "/cgroup.freeze");
                if ("1".equals(freeze != null ? freeze.trim() : "")) return true;
            } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": frozen check cgroup failed", e); }
        }
        return false;
    }

// ---- extractServiceShortName ----
    public String extractServiceShortName(String line, String packageName) {
        Matcher m = Pattern.compile("ServiceRecord\\{[^}]+\\s([\\w./]+)\\}").matcher(line);
        if (!m.find()) return null;
        String full = m.group(1);
        if (!full.contains("/")) return full;
        String cls = full.substring(full.indexOf('/') + 1);
        if (cls.startsWith("."))               return cls.substring(1);
        if (cls.startsWith(packageName + ".")) return cls.substring(packageName.length() + 1);
        return cls;
    }

}
