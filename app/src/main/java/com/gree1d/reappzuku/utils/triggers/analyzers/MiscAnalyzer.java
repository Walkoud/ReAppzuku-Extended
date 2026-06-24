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

public class MiscAnalyzer {

    private static final String FILE_NAME = "MiscAnalyzer";

    private final AppTriggersAnalyzer analyzer;
    private final ComponentsAnalyzer componentsAnalyzer;

    public MiscAnalyzer(AppTriggersAnalyzer analyzer, ComponentsAnalyzer componentsAnalyzer) {
        this.analyzer = analyzer;
        this.componentsAnalyzer = componentsAnalyzer;
    }

// ---- CONST:BAL_FGS_PAT ----
    private static final Pattern BAL_FGS_PAT = Pattern.compile(
            "backgroundStartPrivileges.*?allowsBackgroundForegroundServiceStarts=(true|false)",
            Pattern.DOTALL);


// ---- CONST:BAL_ACTIVITY_PAT ----
    private static final Pattern BAL_ACTIVITY_PAT = Pattern.compile(
            "backgroundStartPrivileges.*?allowsBackgroundActivityStarts=(true|false)",
            Pattern.DOTALL);


// ---- analyzeChainLaunch ----
    public List<TriggerInfo> analyzeChainLaunch(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();


        try {
            String procOut = analyzer.getShellManager().runShellCommandAndGetFullOutput("dumpsys activity processes");
            if (procOut != null) {
                boolean inBlock = false;
                Pattern callerPat = Pattern.compile("(?:clientPackage|callingPackage)=([\\w.]+)");
                for (String line : procOut.split("\n")) {
                    if (line.contains("ProcessRecord") && line.contains(packageName)) inBlock = true;
                    if (inBlock && line.contains("ProcessRecord") && !line.contains(packageName)) break;
                    if (!inBlock) continue;
                    Matcher m = callerPat.matcher(line);
                    if (m.find()) {
                        String caller = m.group(1);
                        if (!caller.equals(packageName) && !caller.equals("android")) {
                            String name = analyzer.resolveAppName(caller);
                            list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                                    analyzer.getContext().getString(R.string.triggers_cat_chain_launch),
                                    analyzer.getContext().getString(R.string.triggers_chain_direct_detail, name+"("+caller+")"),
                                    analyzer.getContext().getString(R.string.triggers_chain_direct_explanation, name),
                                    TriggerInfo.Severity.HIGH));
                        }
                        break;
                    }
                    if (analyzer.apiLevel >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                            && line.contains("BackgroundStartPrivileges")
                            && line.contains(packageName)) {
                        Matcher mFgs = BAL_FGS_PAT.matcher(line);
                        Matcher mAct = BAL_ACTIVITY_PAT.matcher(line);
                        boolean fgsAllowed = mFgs.find() && "true".equals(mFgs.group(1));
                        boolean actAllowed = mAct.find() && "true".equals(mAct.group(1));
                        if (fgsAllowed || actAllowed) {
                            String detail = fgsAllowed && actAllowed ? "FGS + Activity"
                                    : fgsAllowed ? "FGS only" : "Activity only";
                            list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                                    analyzer.getContext().getString(R.string.triggers_cat_chain_launch),
                                    "BackgroundStartPrivilege: " + detail,
                                    analyzer.getContext().getString(R.string.triggers_bal_privilege_explanation),                                    
                                    TriggerInfo.Severity.HIGH));
                        }
                    }
                }
            }
        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": chain/processes failed", e); }


        try {
            String bcastOut = componentsAnalyzer.getBroadcastHistory();
            if (bcastOut != null) {
                List<String> relevant = new ArrayList<>();
                for (String line : bcastOut.split("\n"))
                    if (line.contains(packageName)) relevant.add(line);

                int start = Math.max(0, relevant.size() - 30);
                List<String> callers = new ArrayList<>(), actions = new ArrayList<>();
                Pattern cPat = Pattern.compile("callerPackage=([\\w.]+)");
                Pattern aPat = Pattern.compile("act=([\\w.]+)");

                for (String line : relevant.subList(start, relevant.size())) {
                    Matcher mC = cPat.matcher(line), mA = aPat.matcher(line);
                    String caller = mC.find() ? mC.group(1) : null;
                    String action = mA.find() ? analyzer.shortenAction(mA.group(1)) : "?";
                    if (caller != null && !caller.equals(packageName)
                            && !caller.equals("android") && !caller.equals("null")
                            && !callers.contains(caller)) {
                        callers.add(caller); actions.add(action);
                    }
                }
                int shown = Math.min(callers.size(), 3);
                for (int i = 0; i < shown; i++) {
                    String pkg  = callers.get(i);
                    String name = analyzer.resolveAppName(pkg);
                    String detail = analyzer.getContext().getString(R.string.triggers_chain_broadcast_detail, name+"("+pkg+")", actions.get(i));
                    if (i == shown - 1 && callers.size() > shown)
                        detail += analyzer.getContext().getString(R.string.triggers_chain_overflow, callers.size() - shown);
                    list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                            analyzer.getContext().getString(R.string.triggers_cat_chain_launch),
                            detail,
                            analyzer.getContext().getString(R.string.triggers_chain_broadcast_explanation, name, actions.get(i)),
                            TriggerInfo.Severity.MEDIUM));
                }
            }
        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": chain/broadcasts failed", e); }

        if (list.isEmpty() && analyzer.apiLevel >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": analyzeChainLaunch: no results from processes/broadcasts, trying logcat fallback");
            list.addAll(analyzeChainLaunchLogcatFallback(packageName));
        }

        return list;
    }

// ---- analyzeChainLaunchLogcatFallback ----
    public List<TriggerInfo> analyzeChainLaunchLogcatFallback(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        try {
            String logcat = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                    "logcat -d -t 200 -s ActivityTaskManager:W | grep " + packageName);
            if (logcat == null || logcat.trim().isEmpty()) return list;

            Pattern callerPat = Pattern.compile("callingPackage:\\s*([\\w.]+)");

            for (String line : logcat.split("\n")) {
                if (!line.contains("blocked") || !line.contains(packageName)) continue;
                Matcher mC = callerPat.matcher(line);
                String caller = mC.find() ? mC.group(1) : "unknown";
                list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                        analyzer.getContext().getString(R.string.triggers_cat_chain_launch),
                        analyzer.getContext().getString(R.string.triggers_bal_blocked_detail_prefix) + caller,
                        analyzer.getContext().getString(R.string.triggers_bal_blocked_explanation),                        
                        TriggerInfo.Severity.MEDIUM));
                if (list.size() >= 2) break;
            }
        } catch (Exception e) {
            AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": chain/logcat fallback failed", e);
        }
        return list;
    }

// ---- analyzeBroadcastEfficiency ----
    public List<TriggerInfo> analyzeBroadcastEfficiency(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = componentsAnalyzer.getBroadcastHistory();
        if (output == null || output.trim().isEmpty()) return list;


        List<String> relevant = new ArrayList<>();
        for (String line : output.split("\n"))
            if (line.contains(packageName)) relevant.add(line);
        if (relevant.isEmpty()) return list;
        int start = Math.max(0, relevant.size() - 200);
        relevant = relevant.subList(start, relevant.size());

        int  delivered=0, launched=0, lastHour=0, lastDay=0;
        long nowMs=System.currentTimeMillis(), hourAgo=nowMs-3_600_000L, dayAgo=nowMs-86_400_000L;

        Pattern timePat  = Pattern.compile(
                "(?:enqueueClockTime|dispatchClockTime|finishTime)=(\\d{10,13})");
        Pattern startPat = Pattern.compile(
                "(?:start(?:ing)?\\s+proc|not\\s+running)", Pattern.CASE_INSENSITIVE);
        Pattern alivePat = Pattern.compile(
                "(?:already\\s+running|isAlive=true)",       Pattern.CASE_INSENSITIVE);

        long curTime=0; boolean curStart=false, curAlive=false, counted=false;

        for (String line : relevant) {
            if (line.contains("BroadcastRecord{") || line.contains("Broadcast #")) {
                if (counted) {
                    delivered++;
                    if (curStart && !curAlive) launched++;
                    if (curTime > hourAgo) lastHour++;
                    if (curTime > dayAgo)  lastDay++;
                }
                curTime=0; curStart=false; curAlive=false; counted=true;
            }
            Matcher mT = timePat.matcher(line);
            if (mT.find()) {
                long t = Long.parseLong(mT.group(1));
                if (t < 9_999_999_999L) t *= 1000;
                curTime = t;
            }
            if (startPat.matcher(line).find()) curStart = true;
            if (alivePat.matcher(line).find()) curAlive = true;
        }
        if (counted) {
            delivered++;
            if (curStart && !curAlive) launched++;
            if (curTime > hourAgo) lastHour++;
            if (curTime > dayAgo)  lastDay++;
        }

        if (delivered == 0) return list;
        int pct = (int)(launched * 100L / delivered);

        StringBuilder detail = new StringBuilder(
                analyzer.getContext().getString(R.string.triggers_bcast_eff_total, delivered));
        if (lastHour > 0) detail.append(analyzer.getContext().getString(R.string.triggers_bcast_eff_hour, lastHour));
        if (lastDay  > 0 && lastDay != lastHour)
            detail.append(analyzer.getContext().getString(R.string.triggers_bcast_eff_day, lastDay));
        if (launched > 0)
            detail.append(analyzer.getContext().getString(R.string.triggers_bcast_eff_launched, launched, pct));

        TriggerInfo.Severity sev = launched>10||(pct>50&&delivered>5) ? TriggerInfo.Severity.HIGH
                : launched>3||lastHour>20 ? TriggerInfo.Severity.MEDIUM : TriggerInfo.Severity.LOW;

        list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                analyzer.getContext().getString(R.string.triggers_cat_bcast_eff),
                detail.toString(),
                analyzer.getContext().getString(R.string.triggers_bcast_eff_explanation), sev));
        return list;
    }

// ---- analyzeMultipleProcesses ----
    public List<TriggerInfo> analyzeMultipleProcesses(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        try {

            String psOut = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                    "ps -A -o pid,name 2>/dev/null | grep " + packageName);
            if (psOut == null || psOut.trim().isEmpty()) {

                psOut = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                        "ps -A 2>/dev/null | grep " + packageName);
            }
            if (psOut == null || psOut.trim().isEmpty()) return list;

            List<String> processNames = new ArrayList<>();
            List<Integer> pids        = new ArrayList<>();

            Pattern pidPat  = Pattern.compile("^\\s*(\\d+)");
            Pattern namePat = Pattern.compile("(" + Pattern.quote(packageName) + "[:\\w]*)\\s*$");

            for (String line : psOut.split("\n")) {
                line = line.trim();
                if (line.isEmpty()) continue;

                Matcher mName = namePat.matcher(line);
                if (!mName.find()) continue;
                String procName = mName.group(1);
                if (!processNames.contains(procName)) processNames.add(procName);

                Matcher mPid = pidPat.matcher(line);
                if (mPid.find()) {
                    try { pids.add(Integer.parseInt(mPid.group(1))); }
                    catch (NumberFormatException ignored) {}
                }
            }

            int count = processNames.size();
            if (count <= 1) return list;

            AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": analyzeMultipleProcesses: found count=" + count + " processes=" + processNames);


            List<String> subNames = new ArrayList<>();
            for (String n : processNames) {
                if (n.equals(packageName)) subNames.add(0, "main");
                else if (n.startsWith(packageName + ":"))
                    subNames.add(n.substring(packageName.length()));
                else
                    subNames.add(n);
            }

            StringBuilder detail = new StringBuilder(
                    analyzer.getContext().getString(R.string.triggers_multiproc_count, count));
            detail.append(": ").append(String.join(", ", subNames));

            list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                    analyzer.getContext().getString(R.string.triggers_cat_multiproc),
                    detail.toString(),
                    analyzer.getContext().getString(R.string.triggers_multiproc_explanation),
                    count > 3 ? TriggerInfo.Severity.HIGH : TriggerInfo.Severity.MEDIUM));

        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": analyzeMultipleProcesses failed", e); }
        return list;
    }

// ---- analyzeAccessibilityAndIme ----
    public List<TriggerInfo> analyzeAccessibilityAndIme(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();


        try {
            String a11yOut = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                    "dumpsys accessibility");
            if (a11yOut != null) {
                boolean enabled = false;
                String  svcName = null;

                Pattern svcPat = Pattern.compile(
                        "([\\w.]+/[\\w.]+)", Pattern.CASE_INSENSITIVE);

                boolean inEnabled = false;
                for (String line : a11yOut.split("\n")) {
                    String t = line.trim();
                    if (t.startsWith("Enabled services:") || t.startsWith("Installed services:"))
                        inEnabled = true;
                    if (inEnabled && t.startsWith("---")) inEnabled = false;
                    if (!inEnabled) continue;

                    if (!t.contains(packageName)) continue;
                    enabled = true;
                    Matcher m = svcPat.matcher(t);
                    if (m.find()) svcName = m.group(1);
                    if (svcName != null && svcName.contains("/")) {

                        String cls = svcName.substring(svcName.indexOf('/') + 1);
                        if (cls.startsWith(packageName + "."))
                            cls = cls.substring(packageName.length() + 1);
                        svcName = cls;
                    }
                    break;
                }

                if (enabled) {
                    list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                            analyzer.getContext().getString(R.string.triggers_cat_accessibility),
                            svcName != null ? svcName
                                    : analyzer.getContext().getString(R.string.triggers_a11y_detail_generic),
                            analyzer.getContext().getString(R.string.triggers_a11y_explanation),
                            TriggerInfo.Severity.HIGH));
                }
            }
        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": analyzeAccessibility failed", e); }


        try {
            String imeOut = analyzer.getShellManager().runShellCommandAndGetFullOutput("dumpsys input_method");
            if (imeOut != null) {
                boolean isCurrentIme = false;
                String  imeName = null;

                for (String line : imeOut.split("\n")) {
                    String t = line.trim();

                    if ((t.startsWith("mCurMethodId=") || t.startsWith("mCurId="))
                            && t.contains(packageName)) {
                        isCurrentIme = true;
                        Matcher m = Pattern.compile(packageName + "/([\\w.$]+)").matcher(t);
                        if (m.find()) imeName = m.group(1);
                        break;
                    }
                }

                if (isCurrentIme) {
                    list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                            analyzer.getContext().getString(R.string.triggers_cat_ime),
                            imeName != null ? imeName
                                    : analyzer.getContext().getString(R.string.triggers_ime_detail_generic),
                            analyzer.getContext().getString(R.string.triggers_ime_explanation),
                            TriggerInfo.Severity.HIGH));
                }
            }
        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": analyzeIme failed", e); }

        return list;
    }

// ---- analyzeDeviceAdmin ----
    public List<TriggerInfo> analyzeDeviceAdmin(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        try {
            String dpOut = analyzer.getShellManager().runShellCommandAndGetFullOutput("dumpsys device_policy");
            if (dpOut == null || dpOut.trim().isEmpty()) return list;

            boolean isOwner  = false;
            boolean isAdmin  = false;
            String  ownerType = null;

            for (String line : dpOut.split("\n")) {
                String t = line.trim();

                if ((t.startsWith("Device Owner:") || t.startsWith("mDeviceOwner="))
                        && t.contains(packageName)) {
                    isOwner = true; ownerType = "device";
                }

                if ((t.startsWith("Profile Owner") || t.startsWith("mProfileOwner="))
                        && t.contains(packageName)) {
                    isOwner = true; ownerType = "profile";
                }

                if (t.contains("Active admin") || t.contains("AdminList:"))
                    isAdmin = t.contains(packageName);
                if (!isAdmin && t.contains(packageName)
                        && (t.contains("ComponentInfo") || t.contains("admin=")))
                    isAdmin = true;
            }

            if (!isOwner && !isAdmin) return list;

            String detail, expl;
            TriggerInfo.Severity sev;
            if (isOwner) {
                detail = analyzer.getContext().getString(ownerType.equals("device")
                        ? R.string.triggers_device_admin_owner_device
                        : R.string.triggers_device_admin_owner_profile);
                expl   = analyzer.getContext().getString(R.string.triggers_device_admin_owner_explanation);
                sev    = TriggerInfo.Severity.HIGH;
            } else {
                detail = analyzer.getContext().getString(R.string.triggers_device_admin_active);
                expl   = analyzer.getContext().getString(R.string.triggers_device_admin_explanation);
                sev    = TriggerInfo.Severity.MEDIUM;
            }

            list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                    analyzer.getContext().getString(R.string.triggers_cat_device_admin),
                    detail, expl, sev));

        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": analyzeDeviceAdmin failed", e); }
        return list;
    }

// ---- analyzeUsageStats ----
    public List<TriggerInfo> analyzeUsageStats(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        try {
            String out = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                    "dumpsys usagestats | grep -A 15 \"" + packageName + "\"");
            if (out == null || out.trim().isEmpty()) return list;


            Pattern usedPat  = Pattern.compile(
                    "(?:lastTimeUsed|mLastTimeUsed|last_time_used)[=:](\\d+)");
            Pattern fgPat    = Pattern.compile(
                    "(?:lastTimeForeground|mLastTimeForeground|last_time_fg)[=:](\\d+)");
            Pattern totalPat = Pattern.compile(
                    "(?:totalTimeInForeground|mTotalTimeInForeground|total_time_fg)[=:](\\d+)");

            long lastUsed  = -1;
            long lastFg    = -1;
            long totalFgMs = -1;

            for (String line : out.split("\n")) {
                if (!line.contains(packageName) && lastUsed == -1 && lastFg == -1) continue;

                try {
                    Matcher mU = usedPat.matcher(line);
                    if (mU.find() && lastUsed == -1)
                        lastUsed = Long.parseLong(mU.group(1));

                    Matcher mF = fgPat.matcher(line);
                    if (mF.find() && lastFg == -1)
                        lastFg = Long.parseLong(mF.group(1));

                    Matcher mT = totalPat.matcher(line);
                    if (mT.find() && totalFgMs == -1)
                        totalFgMs = Long.parseLong(mT.group(1));
                } catch (NumberFormatException e) {
                    AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": analyzeUsageStats parse failed", e);
                }

                if (lastUsed > 0 && lastFg > 0 && totalFgMs > 0) break;
            }

            if (lastUsed <= 0) return list;

            long nowMs     = System.currentTimeMillis();
            long sinceUsed = nowMs - lastUsed;
            long sinceFg   = lastFg > 0 ? nowMs - lastFg : -1;


            if (sinceUsed < 0 || sinceUsed > 30L * 24 * 3600 * 1000) return list;


            if (sinceFg >= 0 && sinceFg < 5 * 60 * 1000) return list;


            boolean isBgWake = sinceUsed < 10 * 60 * 1000
                    && (sinceFg < 0 || sinceFg > sinceUsed + 60_000);

            if (!isBgWake) return list;

            StringBuilder detail = new StringBuilder(
                    analyzer.getContext().getString(R.string.triggers_usagestats_last_used,
                            analyzer.formatDuration(sinceUsed)));
            if (sinceFg > 0)
                detail.append(" · ")
                      .append(analyzer.getContext().getString(R.string.triggers_usagestats_last_fg,
                              analyzer.formatDuration(sinceFg)));
            if (totalFgMs > 0)
                detail.append(" · ")
                      .append(analyzer.getContext().getString(R.string.triggers_usagestats_total_fg,
                              analyzer.formatDuration(totalFgMs)));

            list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                    analyzer.getContext().getString(R.string.triggers_cat_usagestats),
                    detail.toString(),
                    analyzer.getContext().getString(R.string.triggers_usagestats_explanation),
                    sinceUsed < 60_000 ? TriggerInfo.Severity.HIGH : TriggerInfo.Severity.MEDIUM));

        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": analyzeUsageStats failed", e); }
        return list;
    }

}
