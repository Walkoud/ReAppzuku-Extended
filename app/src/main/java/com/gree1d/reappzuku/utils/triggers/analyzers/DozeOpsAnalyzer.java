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

public class DozeOpsAnalyzer {

    private static final String FILE_NAME = "DozeOpsAnalyzer";

    private final AppTriggersAnalyzer analyzer;

    public DozeOpsAnalyzer(AppTriggersAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

// ---- analyzeDozeExemption ----
    public List<TriggerInfo> analyzeDozeExemption(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                "dumpsys deviceidle | grep -E 'whitelist|except|power-save|restricted|exemption'");
        if (output == null || output.trim().isEmpty()) {
            if (analyzer.apiLevel >= AppTriggersAnalyzer.API_BAL_PRIVILEGES)
                return analyzeDozeExemptionFallback(packageName);
            return list;
        }

        for (String line : output.split("\n")) {
            if (!line.contains(packageName)) continue;
            boolean sys = line.contains("sys-");
            list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                    AppTriggersAnalyzer.KEY_CAT_DOZE,
                        analyzer.getContext().getString(R.string.triggers_cat_doze),
                    analyzer.getContext().getString(sys ? R.string.triggers_doze_sys_detail
                                         : R.string.triggers_doze_user_detail),
                    analyzer.getContext().getString(sys ? R.string.triggers_doze_sys_explanation
                                         : R.string.triggers_doze_user_explanation),
                    TriggerInfo.Severity.HIGH));
            break;
        }

        if (list.isEmpty() && analyzer.apiLevel >= AppTriggersAnalyzer.API_BAL_PRIVILEGES)
            list.addAll(analyzeDozeExemptionFallback(packageName));

        if (list.isEmpty()
                && analyzer.apiLevel >= android.os.Build.VERSION_CODES.R
                && analyzer.apiLevel <= android.os.Build.VERSION_CODES.TIRAMISU) {
            list.addAll(analyzeDozeStateFallback(packageName));
        }

        return list;
    }

// ---- analyzeDozeExemptionFallback ----
    public List<TriggerInfo> analyzeDozeExemptionFallback(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        try {
            String ops = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                    "cmd appops get " + packageName + " RUN_ANY_IN_BACKGROUND");
            if (ops != null && ops.contains("allow")) {
                list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                        AppTriggersAnalyzer.KEY_CAT_DOZE,
                        analyzer.getContext().getString(R.string.triggers_cat_doze),
                        analyzer.getContext().getString(R.string.triggers_doze_battery_opt_detail),                        
                        analyzer.getContext().getString(R.string.triggers_doze_battery_opt_explanation),                        
                        TriggerInfo.Severity.HIGH));
            }
        } catch (Exception e) {
            AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": doze exemption appops fallback failed", e);
        }

        try {
            String battery = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                    "dumpsys battery | grep -i " + packageName);
            if (battery != null && !battery.trim().isEmpty()
                    && battery.toLowerCase().contains("exempt")) {
                list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                        AppTriggersAnalyzer.KEY_CAT_DOZE,
                        analyzer.getContext().getString(R.string.triggers_cat_doze),
                        analyzer.getContext().getString(R.string.triggers_doze_battery_exempt_detail),                        
                        analyzer.getContext().getString(R.string.triggers_doze_battery_exempt_explanation),                        
                        TriggerInfo.Severity.HIGH));
            }
        } catch (Exception e) {
            AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": doze exemption battery fallback failed", e);
        }

        return list;
    }

// ---- analyzeDozeStateFallback ----
    public List<TriggerInfo> analyzeDozeStateFallback(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        try {
            String idleOut = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                    "dumpsys deviceidle | grep -E 'mState|mLightState'");
            if (idleOut == null || idleOut.trim().isEmpty()) return list;

            String dozeState = null, lightState = null;

            for (String line : idleOut.split("\n")) {
                String t = line.trim();
                Matcher mD = AppTriggersAnalyzer.DOZE_STATE_PAT.matcher(t);
                if (mD.find() && t.startsWith("mState") && dozeState == null)
                    dozeState = mD.group(1);
                Matcher mL = AppTriggersAnalyzer.LIGHT_DOZE_PAT.matcher(t);
                if (mL.find() && t.startsWith("mLightState") && lightState == null)
                    lightState = mL.group(1);
            }

            boolean inDeepDoze  = dozeState  != null && dozeState.contains("IDLE")
                                  && !dozeState.contains("PENDING");
            boolean inLightDoze = lightState != null && lightState.contains("IDLE")
                                  && !lightState.contains("PENDING");

            if (inDeepDoze || inLightDoze) {
                String stateLabel = inDeepDoze ? "Deep Doze" : "Light Doze";
                String stateVal   = inDeepDoze ? dozeState : lightState;
                list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                        AppTriggersAnalyzer.KEY_CAT_DOZE,
                        analyzer.getContext().getString(R.string.triggers_cat_doze),
                        stateLabel + " · " + stateVal,
                        analyzer.getContext().getString(R.string.triggers_doze_state_prefix, stateLabel) + 
                        analyzer.getContext().getString(R.string.triggers_doze_state_suffix),
                        TriggerInfo.Severity.INFO));
            }
        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": doze state fallback failed", e); }
        return list;
    }

// ---- analyzeStandbyBucket ----
    public List<TriggerInfo> analyzeStandbyBucket(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                "am get-standby-bucket " + packageName);
        if (output == null || output.trim().isEmpty()) {
            if (analyzer.apiLevel >= AppTriggersAnalyzer.API_BAL_PRIVILEGES)
                output = getStandbyBucketFallback(packageName);
        }
        if (output == null || output.trim().isEmpty()) return list;

        int bv = -1;
        try { bv = Integer.parseInt(output.trim()); }
        catch (NumberFormatException ignored) {
            Matcher m = Pattern.compile("(\\d+)").matcher(output);
            if (m.find()) bv = Integer.parseInt(m.group(1));
        }
        if (bv == -1) return list;

        String currentName = analyzer.bucketValueToName(bv);
        AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": analyzeStandbyBucket: pkg=" + packageName + " bv=" + bv + " name=" + currentName);


        List<String> history = new ArrayList<>();
        try {
            String usOut = analyzer.getShellManager().runShellCommandAndGetFullOutput("dumpsys usagestats");
            if (usOut != null) {
                boolean inPkg = false;
                Pattern bPat = Pattern.compile("bucket=(\\d+)");
                Pattern rPat = Pattern.compile("reason=0x([0-9a-fA-F]+)");
                for (String line : usOut.split("\n")) {
                    try {
                        if (line.contains("package=" + packageName)
                                || line.contains("Package[" + packageName)) inPkg = true;
                        if (inPkg && line.trim().startsWith("package=")
                                && !line.contains(packageName)) inPkg = false;
                        if (!inPkg) continue;

                        Matcher mB = bPat.matcher(line);
                        if (!mB.find()) continue;
                        String bn = analyzer.bucketValueToName(Integer.parseInt(mB.group(1)));

                        String reason = "";
                        Matcher mR = rPat.matcher(line);
                        if (mR.find()) {
                            int reasonHex = (int) Long.parseLong(mR.group(1), 16);
                            int main = (reasonHex >> 8) & 0xF;
                            switch (main) {
                                case 0x0: reason="default";     break;
                                case 0x1: reason="usage";       break;
                                case 0x2: reason="timeout";     break;
                                case 0x3: reason="predicted";   break;
                                case 0x4: reason="sys-forced";  break;
                                case 0x6: reason="user-forced"; break;
                            }
                            if (main == 0x2
                                    && analyzer.apiLevel >= android.os.Build.VERSION_CODES.S
                                    && analyzer.apiLevel <= android.os.Build.VERSION_CODES.TIRAMISU) {
                                int sub = reasonHex & 0xFF;
                                switch (sub) {
                                    case 0x01: reason += "(8d inactive)";  break;
                                    case 0x02: reason += "(45d inactive)"; break;
                                    case 0x03: reason += "(RESTRICTED)";   break;
                                }
                            }
                        }
                        String entry = bn + (reason.isEmpty() ? "" : "(" + reason + ")");
                        if (history.isEmpty() || !history.get(history.size()-1).startsWith(bn))
                            history.add(entry);
                    } catch (Exception e) {
                        AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": standby bucket history parse failed", e);
                    }
                }
            }
        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": usagestats failed", e); }

        if (history.size() > 4) history = history.subList(history.size()-4, history.size());

        if (history.isEmpty() || !history.get(history.size()-1).startsWith(currentName))
            history.add(currentName);

        String detail = currentName;
        if (history.size() > 1)
            detail += analyzer.getContext().getString(R.string.triggers_bucket_history,
                    String.join(" → ", history));

        TriggerInfo.Severity sev;
        String expl;
        if      (bv <= 10) { sev=TriggerInfo.Severity.HIGH;   expl=analyzer.getContext().getString(R.string.triggers_bucket_active_explanation); }
        else if (bv <= 20) { sev=TriggerInfo.Severity.MEDIUM; expl=analyzer.getContext().getString(R.string.triggers_bucket_working_set_explanation); }
        else if (bv <= 30) { sev=TriggerInfo.Severity.LOW;    expl=analyzer.getContext().getString(R.string.triggers_bucket_frequent_explanation); }
        else if (bv <= 40) { sev=TriggerInfo.Severity.INFO;   expl=analyzer.getContext().getString(R.string.triggers_bucket_rare_explanation); }
        else if (bv <= 45) {
            sev  = TriggerInfo.Severity.HIGH;
            expl = analyzer.getContext().getString(R.string.triggers_bucket_restricted_explanation);
        }
        else {
            sev  = TriggerInfo.Severity.HIGH;
            expl = analyzer.getContext().getString(R.string.triggers_bucket_never_explanation);
        }

        list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                AppTriggersAnalyzer.KEY_CAT_BUCKET,
                analyzer.getContext().getString(R.string.triggers_cat_bucket), detail, expl, sev));

        if (bv > 40
                && analyzer.apiLevel >= android.os.Build.VERSION_CODES.S
                && analyzer.apiLevel <= android.os.Build.VERSION_CODES.TIRAMISU) {
            list.addAll(analyzeRestrictedBucketEffects(packageName, bv));
        }

        return list;
    }

// ---- getStandbyBucketFallback ----
    public String getStandbyBucketFallback(String packageName) {
        try {
            String us = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                    "dumpsys usagestats | grep -A3 \"" + packageName + "\"");
            if (us != null) {
                Matcher m = Pattern.compile("standbyBucket=(\\d+)").matcher(us);
                if (m.find()) return m.group(1);
                Matcher m2 = Pattern.compile("bucket=(\\d+)").matcher(us);
                if (m2.find()) return m2.group(1);
            }
        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": standby fallback/usagestats failed", e); }

        try {
            String ops = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                    "cmd appops get " + packageName + " RUN_ANY_IN_BACKGROUND");
            if (ops != null) {
                if (ops.contains("ignore") || ops.contains("deny"))
                    return "45";
                if (ops.contains("allow"))
                    return "5";
            }
        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": standby fallback/appops failed", e); }

        return null;
    }

// ---- analyzeRestrictedBucketEffects ----
    public List<TriggerInfo> analyzeRestrictedBucketEffects(String packageName, int bucketValue) {
        List<TriggerInfo> list = new ArrayList<>();
        try {
            String ops = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                    "cmd appops get " + packageName + " RUN_ANY_IN_BACKGROUND");
            if (ops == null) return list;

            boolean isDenied = ops.contains("ignore") || ops.contains("deny");
            if (!isDenied) return list;

            String jobState = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                    "cmd jobscheduler get-job-state " + packageName);
            boolean jobsBlocked = jobState != null
                    && (jobState.contains("QUOTA") || jobState.contains("RESTRICTED"));

            list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                    AppTriggersAnalyzer.KEY_CAT_BUCKET,
                    analyzer.getContext().getString(R.string.triggers_cat_bucket),
                    "RESTRICTED confirmed · RUN_ANY_IN_BACKGROUND=deny"
                            + (jobsBlocked ? " · jobs blocked" : ""),
                    analyzer.getContext().getString(R.string.triggers_restricted_confirmed_explanation),                    
                    TriggerInfo.Severity.HIGH));
        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": restricted bucket effects check failed", e); }
        return list;
    }

// ---- analyzeAppOps ----
    public List<TriggerInfo> analyzeAppOps(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        try {


            String out = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                    "appops get " + packageName);
            if (out == null || out.trim().isEmpty() || out.contains("Failed transaction")) {
                AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": analyzeAppOps: appops get failed, trying cmd appops");
                out = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                        "cmd appops get " + packageName);
            }
            if (out == null || out.trim().isEmpty() || out.contains("Failed transaction")) {
                AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": analyzeAppOps: cmd appops failed, trying dumpsys fallback");
                out = parseDumpsysAppOpsForPackage(packageName);
            }
            if (out == null || out.trim().isEmpty()) return list;

            Pattern opPat   = Pattern.compile(
                    "^([A-Z_]+):\\s*(allow|foreground|ignore|deny|default)",
                    Pattern.CASE_INSENSITIVE);


            Pattern timePat = Pattern.compile(
                    "time=\\+([\\d]+[\\dhms]+(?:\\s*[\\dhms]+)*)\\s+ago", Pattern.CASE_INSENSITIVE);

            for (String line : out.split("\n")) {
                String t = line.trim();
                if (t.isEmpty()) continue;

                try {
                    Matcher mOp = opPat.matcher(t);
                    if (!mOp.find()) continue;

                    String op   = mOp.group(1).toUpperCase();
                    String mode = mOp.group(2).toLowerCase();

                    OpDescriptor desc = appOpDescriptor(op);
                    if (desc == null) continue;

                    boolean isFgsOp = op.equals("START_FOREGROUND");
                    if (!isFgsOp && ("ignore".equals(mode) || "deny".equals(mode))) continue;
                    if (isFgsOp && "allow".equals(mode)) continue;

                    String timeStr = null;
                    Matcher mTime = timePat.matcher(t);
                    if (mTime.find()) timeStr = mTime.group(1).trim();

                    boolean isPresenceOnly = op.startsWith("RUN_") || op.equals("START_FOREGROUND");
                    if (timeStr == null && !isPresenceOnly) continue;

                    StringBuilder detail = new StringBuilder(desc.label);
                    if (timeStr != null)
                        detail.append(" · ")
                              .append(analyzer.getContext().getString(R.string.triggers_appops_last_used, timeStr));
                    if (!"allow".equals(mode))
                        detail.append(" [").append(mode).append("]");

                    list.add(new TriggerInfo(desc.group,
                            AppTriggersAnalyzer.KEY_CAT_APPOPS,
                            analyzer.getContext().getString(R.string.triggers_cat_appops),
                            detail.toString(),
                            desc.explanation,
                            desc.severity));
                } catch (Exception e) {
                    AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": analyzeAppOps line parse failed", e);
                }
            }


            boolean hasRunAny = false;
            boolean hasRun    = false;
            for (TriggerInfo i : list) {
                if (i.detail != null && i.detail.startsWith("RUN_ANY")) hasRunAny = true;
                if (i.detail != null && i.detail.startsWith("RUN_IN"))  hasRun    = true;
            }
            if (hasRunAny && hasRun)
                list.removeIf(i -> i.detail != null && i.detail.startsWith("RUN_IN_BACKGROUND ·"));

            if (analyzer.apiLevel >= android.os.Build.VERSION_CODES.R
                    && analyzer.apiLevel <= android.os.Build.VERSION_CODES.TIRAMISU) {
                list.addAll(analyzeRestrictedOps(packageName, out));
            }

        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": analyzeAppOps failed", e); }
        return list;
    }

// ---- analyzeExactAlarmPermissions ----
    public List<TriggerInfo> analyzeExactAlarmPermissions(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        try {
            String pkgOut = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                    "dumpsys package " + packageName
                    + " | grep -E 'SCHEDULE_EXACT_ALARM|USE_EXACT_ALARM'");
            if (pkgOut == null) return list;

            boolean hasSchedule = pkgOut.contains("SCHEDULE_EXACT_ALARM") && pkgOut.contains("granted=true");
            boolean hasUse      = pkgOut.contains("USE_EXACT_ALARM")      && pkgOut.contains("granted=true");

            if (hasSchedule || hasUse) {
                String permLabel = hasUse ? "USE_EXACT_ALARM" : "SCHEDULE_EXACT_ALARM";
                list.add(new TriggerInfo(TriggerInfo.Group.CAN_WAKE,
                        AppTriggersAnalyzer.KEY_CAT_ALARMS,
                        analyzer.getContext().getString(R.string.triggers_cat_alarms),
                        analyzer.getContext().getString(R.string.trigger_exact_alarm_label, permLabel),
                        analyzer.getContext().getString(hasUse
                                ? R.string.trigger_exact_alarm_desc_use
                                : R.string.trigger_exact_alarm_desc_schedule),
                        hasUse ? TriggerInfo.Severity.HIGH : TriggerInfo.Severity.MEDIUM));
            }
        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": exact alarm perm check failed", e); }
        return list;
    }

// ---- analyzeRestrictedOps ----
    public List<TriggerInfo> analyzeRestrictedOps(String packageName, String appOpsOut) {
        List<TriggerInfo> list = new ArrayList<>();
        if (appOpsOut == null) return list;
        try {
            boolean hasActRec = appOpsOut.contains("ACTIVITY_RECOGNITION")
                    && appOpsOut.contains("allow");
            boolean hasManageMedia = appOpsOut.contains("MANAGE_MEDIA")
                    && appOpsOut.contains("allow");

            if (hasActRec) {
                Matcher mT = Pattern.compile(
                        "ACTIVITY_RECOGNITION.*?time=\\+([\\d\\w ]+)\\s+ago").matcher(appOpsOut);
                String timeStr = mT.find() ? mT.group(1).trim() : null;
                String detail = "ACTIVITY_RECOGNITION" + (timeStr != null ? " · " + timeStr + " ago" : "");
                list.add(new TriggerInfo(TriggerInfo.Group.ACTIVE_NOW,
                        AppTriggersAnalyzer.KEY_CAT_APPOPS,
                        analyzer.getContext().getString(R.string.triggers_cat_appops),
                        detail,
                        analyzer.getContext().getString(R.string.triggers_appops_activity_recognition_explanation),
                        TriggerInfo.Severity.MEDIUM));
            }
            if (hasManageMedia
                    && analyzer.apiLevel >= android.os.Build.VERSION_CODES.S) {
                list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                        AppTriggersAnalyzer.KEY_CAT_APPOPS,
                        analyzer.getContext().getString(R.string.triggers_cat_appops),
                        analyzer.getContext().getString(R.string.trigger_manage_media_label),
                        analyzer.getContext().getString(R.string.trigger_manage_media_desc),
                        TriggerInfo.Severity.LOW));
            }
        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": analyzeRestrictedOps failed", e); }
        return list;
    }

// ---- parseDumpsysAppOpsForPackage ----
    public String parseDumpsysAppOpsForPackage(String packageName) {
        try {
            String full = analyzer.getShellManager().runShellCommandAndGetFullOutput("dumpsys appops");
            if (full == null) return null;

            StringBuilder sb = new StringBuilder();
            boolean inPkg = false;
            int braceDepth = 0;

            for (String line : full.split("\n")) {
                if (!inPkg) {
                    if (line.trim().startsWith("Package " + packageName)) {
                        inPkg = true;
                        braceDepth = 0;
                    }
                    continue;
                }

                for (char c : line.toCharArray()) {
                    if (c == '{') braceDepth++;
                    else if (c == '}') braceDepth--;
                }

                if (braceDepth < 0 || (line.trim().startsWith("Package ")
                        && !line.contains(packageName))) break;

                Matcher m = Pattern.compile(
                        "([A-Z_]{3,40}):\\s*mode=(\\w+)(?:.*?time=\\+([\\d\\w ]+)\\s+ago)?")
                        .matcher(line);
                if (m.find()) {
                    sb.append(m.group(1)).append(": ").append(m.group(2));
                    if (m.group(3) != null) sb.append("; time=+").append(m.group(3)).append(" ago");
                    sb.append("\n");
                }
            }
            return sb.length() > 0 ? sb.toString() : null;
        } catch (Exception e) {
            AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": parseDumpsysAppOpsForPackage failed", e);
            return null;
        }
    }

// ---- appOpDescriptor ----
    private OpDescriptor appOpDescriptor(String op) {
        switch (op) {
            case "WAKE_LOCK":
                return new OpDescriptor("WAKE_LOCK",
                        analyzer.getContext().getString(R.string.triggers_appops_wakelock_explanation),
                        TriggerInfo.Group.ACTIVE_NOW, TriggerInfo.Severity.HIGH);
            case "RUN_IN_BACKGROUND":
                return new OpDescriptor("RUN_IN_BACKGROUND",
                        analyzer.getContext().getString(R.string.triggers_appops_run_bg_explanation),
                        TriggerInfo.Group.CAN_WAKE, TriggerInfo.Severity.MEDIUM);
            case "RUN_ANY_IN_BACKGROUND":
                return new OpDescriptor("RUN_ANY_IN_BACKGROUND",
                        analyzer.getContext().getString(R.string.triggers_appops_run_any_bg_explanation),
                        TriggerInfo.Group.CAN_WAKE, TriggerInfo.Severity.HIGH);
            case "SCHEDULE_EXACT_ALARM":
                return new OpDescriptor("Exact Alarm",
                        analyzer.getContext().getString(R.string.triggers_appops_schedule_exact_alarm_explanation),                        
                        TriggerInfo.Group.CAN_WAKE, TriggerInfo.Severity.HIGH);
            case "USE_EXACT_ALARM":
                return new OpDescriptor("USE_EXACT_ALARM",
                        analyzer.getContext().getString(R.string.triggers_appops_exact_alarm_explanation),
                        TriggerInfo.Group.CAN_WAKE, TriggerInfo.Severity.HIGH);
            case "USE_FULL_SCREEN_INTENT":
                return new OpDescriptor("Full-Screen Intent",
                        analyzer.getContext().getString(R.string.triggers_appops_full_screen_intent_explanation),                       
                        TriggerInfo.Group.CAN_WAKE, TriggerInfo.Severity.MEDIUM);
            case "MANAGE_MEDIA":
                return new OpDescriptor("Manage Media",
                        analyzer.getContext().getString(R.string.triggers_appops_manage_media_explanation),                        
                        TriggerInfo.Group.OTHER, TriggerInfo.Severity.LOW);
            case "RUN_USER_INITIATED_JOBS":
                return new OpDescriptor("User-Initiated Jobs",
                        analyzer.getContext().getString(R.string.triggers_appops_user_initiated_jobs_explanation),                        
                        TriggerInfo.Group.CAN_WAKE, TriggerInfo.Severity.MEDIUM);
            case "START_FOREGROUND":
                return new OpDescriptor("Start FGS (blocked)",
                        analyzer.getContext().getString(R.string.triggers_appops_start_fgs_blocked_explanation),                        
                        TriggerInfo.Group.OTHER, TriggerInfo.Severity.HIGH);
            case "RECEIVE_EXPLICIT_USER_INTERACTION":
                return new OpDescriptor("USER_INTERACTION",
                        analyzer.getContext().getString(R.string.triggers_appops_user_interaction_explanation),
                        TriggerInfo.Group.CAN_WAKE, TriggerInfo.Severity.MEDIUM);
            case "ACTIVITY_RECOGNITION":
                return new OpDescriptor("ACTIVITY_RECOGNITION",
                        analyzer.getContext().getString(R.string.triggers_appops_activity_recognition_explanation),
                        TriggerInfo.Group.ACTIVE_NOW, TriggerInfo.Severity.MEDIUM);
            default:
                return null;
        }
    }

// ---- NESTED:OpDescriptor ----
    private static final class OpDescriptor {
        final String               label;
        final String               explanation;
        final TriggerInfo.Group    group;
        final TriggerInfo.Severity severity;
        OpDescriptor(String label, String explanation,
                     TriggerInfo.Group group, TriggerInfo.Severity severity) {
            this.label       = label;
            this.explanation = explanation;
            this.group       = group;
            this.severity    = severity;
        }
    }

}
