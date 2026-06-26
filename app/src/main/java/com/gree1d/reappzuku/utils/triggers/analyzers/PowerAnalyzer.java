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

public class PowerAnalyzer {

    private static final String FILE_NAME = "PowerAnalyzer";

    private final AppTriggersAnalyzer analyzer;

    public PowerAnalyzer(AppTriggersAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

// ---- CONST:WORK_SOURCE_PAT ----
    private static final Pattern WORK_SOURCE_PAT     = Pattern.compile("WorkSource\\{(\\d+)\\s+([\\w.]+)\\}");


// ---- analyzeWakelocks ----
    public List<TriggerInfo> analyzeWakelocks(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();

        String powerOutput = analyzer.getShellManager().runShellCommandAndGetFullOutput("dumpsys power");
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
            Pattern acquirePat = Pattern.compile("\\bacq(?:uire)?[=:](\\d+)");
            Pattern releasePat = Pattern.compile("\\brel(?:ease)?[=:](\\d+)");
            Pattern heldLegacy = Pattern.compile("(\\d+m\\s*\\d+s|\\d+s)");
            Pattern tagPat     = Pattern.compile("'([^']{1,60})'");
            String  uid        = analyzer.getCachedUid();

            for (String line : wlBlock.toString().split("\n")) {
                boolean byUid = uid != null && line.contains("uid=" + uid);
                boolean byTag = line.contains(packageName);
                if (!byUid && !byTag) continue;

                AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": Wakelocks/dumpsys power - matched line: " + line.trim());

                String typeLabel, typeExplain;
                if      (line.contains("PARTIAL"))      { typeLabel="Partial";   typeExplain=analyzer.getContext().getString(R.string.triggers_wakelock_partial_explain); }
                else if (line.contains("FULL"))         { typeLabel="Full";      typeExplain=analyzer.getContext().getString(R.string.triggers_wakelock_full_explain); }
                else if (line.contains("SCREEN"))       { typeLabel="Screen";    typeExplain=analyzer.getContext().getString(R.string.triggers_wakelock_screen_explain); }
                else if (line.contains("PROXIMITY"))    { typeLabel="Proximity"; typeExplain=analyzer.getContext().getString(R.string.triggers_wakelock_proximity_explain); }
                else                                    { typeLabel="WakeLock";  typeExplain=analyzer.getContext().getString(R.string.triggers_wakelock_generic_explain); }


                String tag = "";
                Matcher mTag = tagPat.matcher(line);
                if (mTag.find()) tag = mTag.group(1);


                String heldStr = "";
                Matcher mHeldMs = heldMsPat.matcher(line);
                if (mHeldMs.find()) {
                    heldStr = analyzer.formatDuration(Long.parseLong(mHeldMs.group(1)));
                } else {
                    Matcher mLeg = heldLegacy.matcher(line);
                    if (mLeg.find()) heldStr = mLeg.group(1);
                }


                String acqRel = "";
                Matcher mAcq = acquirePat.matcher(line);
                Matcher mRel = releasePat.matcher(line);
                if (mAcq.find() && mRel.find()) {
                    acqRel = analyzer.getContext().getString(R.string.triggers_wakelock_acq_rel,
                            Integer.parseInt(mAcq.group(1)), Integer.parseInt(mRel.group(1)));
                } else if (mAcq.find()) {
                    acqRel = analyzer.getContext().getString(R.string.triggers_wakelock_acq_only,
                            Integer.parseInt(mAcq.group(1)));
                }

                StringBuilder detail = new StringBuilder(typeLabel);
                if (!tag.isEmpty())     detail.append(" · ").append(tag);
                if (!heldStr.isEmpty()) detail.append(" · ")
                        .append(analyzer.getContext().getString(R.string.triggers_wakelock_detail_held, heldStr));
                if (!acqRel.isEmpty())  detail.append(" · ").append(acqRel);

                if (analyzer.apiLevel >= Build.VERSION_CODES.S && analyzer.apiLevel <= Build.VERSION_CODES.TIRAMISU) {
                    Matcher mWs = WORK_SOURCE_PAT.matcher(line);
                    while (mWs.find()) {
                        String wsPkg = mWs.group(2);
                        if (wsPkg.equals(packageName)) {
                            detail.append(" · via WorkSource");
                            break;
                        }
                    }
                }

                if (byTag && !byUid)
                    detail.append(" ").append(analyzer.getContext().getString(R.string.triggers_wakelock_held_by_system));

                list.add(new TriggerInfo(TriggerInfo.Group.ACTIVE_NOW,
                        AppTriggersAnalyzer.KEY_CAT_WAKELOCK,
                        analyzer.getContext().getString(R.string.triggers_cat_wakelock),
                        detail.toString(),
                        analyzer.getContext().getString(R.string.triggers_wakelock_explanation, typeExplain),
                        TriggerInfo.Severity.HIGH));
            }
        }

        if (list.isEmpty()) {
            AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": Wakelocks/dumpsys power - no matches, trying batterystats fallback");
            String bsOut = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                    "dumpsys batterystats " + packageName);
            if (bsOut != null) {
                Pattern p = Pattern.compile(
                        "(?:Wakelock|wake_lock)\\s+(\\S+)[^:]*:\\s+(\\d+)ms\\s+(?:realtime|total)[^(]*\\((\\d+)\\s+times\\)",
                        Pattern.CASE_INSENSITIVE);
                for (String line : bsOut.split("\n")) {
                    Matcher m = p.matcher(line);
                    if (!m.find()) continue;
                    long heldMs = Long.parseLong(m.group(2));
                    int  count  = Integer.parseInt(m.group(3));
                    if (heldMs == 0 && count == 0) continue;
                    list.add(new TriggerInfo(TriggerInfo.Group.ACTIVE_NOW,
                            AppTriggersAnalyzer.KEY_CAT_WAKELOCK,
                        analyzer.getContext().getString(R.string.triggers_cat_wakelock),
                            analyzer.getContext().getString(R.string.triggers_wakelock_fallback_detail,
                                    m.group(1), analyzer.formatDuration(heldMs), count),
                            analyzer.getContext().getString(R.string.triggers_wakelock_fallback_explanation),
                            count > 10 || heldMs > 60_000
                                    ? TriggerInfo.Severity.HIGH : TriggerInfo.Severity.MEDIUM));
                }
            }
        }

        if (list.isEmpty() && analyzer.apiLevel >= AppTriggersAnalyzer.API_BAL_PRIVILEGES) {
            AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": analyzeWakelocks: batterystats empty, trying sysfs fallback");
            list.addAll(analyzeWakelocksSysFsFallback(packageName, analyzer.getCachedUid()));
        }

        if (list.isEmpty()
                && analyzer.apiLevel >= Build.VERSION_CODES.R
                && analyzer.apiLevel <= Build.VERSION_CODES.TIRAMISU) {
            list.addAll(analyzeKernelWakelocksFallback(packageName));
        }

        boolean activeNow = list.stream().anyMatch(
                t -> t.group == TriggerInfo.Group.ACTIVE_NOW);
        appendWakelockHistory(list, packageName, activeNow);

        return list;
    }

// ---- analyzeWakelocksSysFsFallback ----
    public List<TriggerInfo> analyzeWakelocksSysFsFallback(String packageName, String uid) {
        List<TriggerInfo> list = new ArrayList<>();
        try {
            String wakeupSrc = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                    "cat /sys/kernel/wakeup_sources | grep -i " + packageName);
            if (wakeupSrc == null || wakeupSrc.trim().isEmpty()) {
                wakeupSrc = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                        "cat /d/wakeup_sources 2>/dev/null | grep -i " + packageName);
            }
            if (wakeupSrc == null || wakeupSrc.trim().isEmpty()) return list;

            Pattern namePat  = Pattern.compile("^(\\S+)");
            Pattern totalPat = Pattern.compile("\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)");

            for (String line : wakeupSrc.split("\n")) {
                Matcher mN = namePat.matcher(line.trim());
                Matcher mT = totalPat.matcher(line);
                if (!mN.find() || !mT.find()) continue;

                String name       = mN.group(1);
                long   activeCount = Long.parseLong(mT.group(1));
                long   totalTimeMs = Long.parseLong(mT.group(6));

                if (activeCount == 0 && totalTimeMs == 0) continue;

                list.add(new TriggerInfo(TriggerInfo.Group.ACTIVE_NOW,
                        AppTriggersAnalyzer.KEY_CAT_WAKELOCK,
                        analyzer.getContext().getString(R.string.triggers_cat_wakelock),
                        "WakeSrc · " + name
                        + " · " + analyzer.getContext().getString(R.string.triggers_wakelock_detail_held,
                                analyzer.formatDuration(totalTimeMs))
                        + " · " + activeCount + "×",
                        analyzer.getContext().getString(R.string.triggers_wakelock_wakeup_sources_explanation),
                        activeCount > 20 || totalTimeMs > 60_000
                                ? TriggerInfo.Severity.HIGH : TriggerInfo.Severity.MEDIUM));
            }
        } catch (Exception e) {
            AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": wakelock wakeup_sources fallback failed", e);
        }
        return list;
    }

// ---- analyzeKernelWakelocksFallback ----
    public List<TriggerInfo> analyzeKernelWakelocksFallback(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        try {
            String kernelWl = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                    "cat /sys/power/wake_lock");
            if (kernelWl != null && kernelWl.contains(packageName)) {
                list.add(new TriggerInfo(TriggerInfo.Group.ACTIVE_NOW,
                        AppTriggersAnalyzer.KEY_CAT_WAKELOCK,
                        analyzer.getContext().getString(R.string.triggers_cat_wakelock),
                        "Kernel wakelock: " + packageName,
                        analyzer.getContext().getString(R.string.triggers_kernel_wakelock_explanation),                        
                        TriggerInfo.Severity.HIGH));
            }
        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": kernel wakelock check failed", e); }
        return list;
    }

// ---- appendWakelockHistory ----
    public void appendWakelockHistory(List<TriggerInfo> list, String packageName, boolean activeNow) {
        try {
            String history = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                    "dumpsys batterystats --history");
            if (history == null || history.trim().isEmpty()) return;

            Pattern wakePat = Pattern.compile(
                    "\\+(\\d+)h(\\d+)m(\\d+)s(?:(\\d+)ms)?\\s.*?([+-])wake_lock[^=]*=\\S*"
                    + Pattern.quote(packageName) + "\\S*");
            Pattern procPat = Pattern.compile(
                    "\\+(\\d+)h(\\d+)m(\\d+)s(?:(\\d+)ms)?\\s.*?(?:Died|proc).*?"
                    + Pattern.quote(packageName));
            Pattern timePat = Pattern.compile("RESET:TIME:\\s*(\\d+)");

            long baseUnixMs = 0;
            long baseOffsetMs = 0;

            for (String line : history.split("\n")) {
                Matcher mt = timePat.matcher(line);
                if (!mt.find()) continue;
                baseUnixMs = Long.parseLong(mt.group(1)) * 1000L;
                baseOffsetMs = parseHistoryOffset(line);
            }

            List<Long> deathOffsets = new ArrayList<>();
            for (String line : history.split("\n")) {
                Matcher mp = procPat.matcher(line);
                if (mp.find()) deathOffsets.add(parseHistoryOffset(line));
            }

            List<long[]> pairs = new ArrayList<>();
            long pendingAcquire = -1;

            for (String line : history.split("\n")) {
                Matcher me = wakePat.matcher(line);
                if (!me.find()) continue;
                long offsetMs = parseHistoryOffset(line);
                char sign = me.group(5).charAt(0);
                if (sign == '+') {
                    pendingAcquire = offsetMs;
                } else if (sign == '-' && pendingAcquire >= 0) {
                    pairs.add(new long[]{pendingAcquire, offsetMs, 0});
                    pendingAcquire = -1;
                }
            }
            if (pendingAcquire >= 0) {
                long deathOffset = -1;
                for (long d : deathOffsets) {
                    if (d >= pendingAcquire) { deathOffset = d; break; }
                }
                pairs.add(new long[]{pendingAcquire, deathOffset, deathOffset >= 0 ? 1 : -1});
            }

            boolean historyHasOpen = pairs.stream().anyMatch(p -> p[2] == -1);
            String syntheticLine = null;
            if (activeNow && !historyHasOpen) {
                java.text.SimpleDateFormat nowSdf = new java.text.SimpleDateFormat(
                        "HH:mm:ss", java.util.Locale.getDefault());
                syntheticLine = nowSdf.format(new java.util.Date()) + " → now  (active, not in history buffer)";
            }

            if (pairs.isEmpty() && syntheticLine == null) return;

            int from = Math.max(0, pairs.size() - 5);
            List<long[]> last5 = pairs.subList(from, pairs.size());

            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                    "HH:mm:ss", java.util.Locale.getDefault());
            StringBuilder sb = new StringBuilder();

            for (long[] pair : last5) {
                long acqUnix = baseUnixMs + pair[0] - baseOffsetMs;
                String acqTime = sdf.format(new java.util.Date(acqUnix));
                if (pair[2] == 1) {
                    long relUnix = baseUnixMs + pair[1] - baseOffsetMs;
                    String relTime = sdf.format(new java.util.Date(relUnix));
                    long durMs = pair[1] - pair[0];
                    sb.append(acqTime).append(" → ").append(relTime)
                      .append("  (").append(analyzer.formatDuration(durMs)).append(") released by system\n");
                } else if (pair[2] == -1) {
                    sb.append(acqTime).append(" → ?\n");
                } else {
                    long relUnix = baseUnixMs + pair[1] - baseOffsetMs;
                    String relTime = sdf.format(new java.util.Date(relUnix));
                    long durMs = pair[1] - pair[0];
                    sb.append(acqTime).append(" → ").append(relTime)
                      .append("  (").append(analyzer.formatDuration(durMs)).append(")\n");
                }
            }

            if (syntheticLine != null) sb.append(syntheticLine).append("\n");

            String detail = sb.toString().trim();
            if (detail.isEmpty()) return;

            list.add(new TriggerInfo(
                    TriggerInfo.Group.OTHER,
                    AppTriggersAnalyzer.KEY_CAT_WAKELOCK,
                    "WakeLock History",
                    "",
                    detail,
                    TriggerInfo.Severity.INFO));

        } catch (Exception e) {
            AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": wakelock history parse failed", e);
        }
    }

// ---- parseHistoryOffset ----
    public long parseHistoryOffset(String line) {
        Pattern p = Pattern.compile("\\+(\\d+)h(\\d+)m(\\d+)s(?:(\\d+)ms)?");
        Matcher m = p.matcher(line);
        if (!m.find()) return 0;
        long ms = Long.parseLong(m.group(1)) * 3_600_000L
                + Long.parseLong(m.group(2)) * 60_000L
                + Long.parseLong(m.group(3)) * 1_000L;
        if (m.group(4) != null) ms += Long.parseLong(m.group(4));
        return ms;
    }

// ---- analyzeExcessiveWakeups ----
    public List<TriggerInfo> analyzeExcessiveWakeups(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                "dumpsys batterystats " + packageName);
        if (output == null || output.trim().isEmpty()) return list;

        int alarmW=0, jobW=0, gcmW=0, bcastW=0;
        List<String> alarmTags = new ArrayList<>();

        Pattern ap = Pattern.compile(
                "(?:Wakeup alarm|wakeup_alarm)\\s+([\\w./]+)[^:]*:\\s*(\\d+)\\s+times", Pattern.CASE_INSENSITIVE);
        Pattern jp = Pattern.compile(
                "Job\\s+\\S+:\\s+\\d+ms.*?\\((\\d+)\\s+times\\)", Pattern.CASE_INSENSITIVE);
        Pattern gp = Pattern.compile(
                "(?:GCM|FCM|push).*?wakeup.*?:\\s*(\\d+)",        Pattern.CASE_INSENSITIVE);
        Pattern bp = Pattern.compile(
                "Broadcast\\s+\\S+.*?\\((\\d+)\\s+times\\)",      Pattern.CASE_INSENSITIVE);
        Pattern pkgSectionPat = Pattern.compile(
                "^\\s{2}(?:u\\d+[a-z]\\d+|Package)\\s+" + Pattern.quote(packageName));

        boolean inPkgSection = false;

        for (String line : output.split("\n")) {
            if (pkgSectionPat.matcher(line).find()) {
                inPkgSection = true;
                continue;
            }
            if (inPkgSection && line.matches("^\\s{2}(?:u\\d+[a-z]\\d+|Package)\\s+\\S+.*")) {
                break;
            }
            if (!inPkgSection && !line.contains(packageName)) continue;

            try {
                Matcher m;
                if ((m=ap.matcher(line)).find()) {
                    int cnt = Integer.parseInt(m.group(2));
                    alarmW += cnt;
                    if (alarmTags.size() < 3) {
                        String tag = m.group(1);
                        if (tag.contains("/")) tag = tag.substring(tag.indexOf('/') + 1);
                        if (tag.startsWith(".")) tag = tag.substring(1);
                        if (tag.startsWith(packageName + ".")) tag = tag.substring(packageName.length() + 1);
                        alarmTags.add(tag + "×" + cnt);
                    }
                    continue;
                }
                if ((m=jp.matcher(line)).find()) { jobW  +=Integer.parseInt(m.group(1)); continue; }
                if ((m=gp.matcher(line)).find()) { gcmW  +=Integer.parseInt(m.group(1)); continue; }
                if ((m=bp.matcher(line)).find())   bcastW+=Integer.parseInt(m.group(1));
            } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": analyzeExcessiveWakeups line parse failed", e); }
        }

        int total = alarmW + jobW + gcmW + bcastW;
        AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": analyzeExcessiveWakeups: pkg=" + packageName + " total=" + total + " alarms=" + alarmW + " jobs=" + jobW + " gcm=" + gcmW + " bcast=" + bcastW);
        if (total == 0) return list;

        StringBuilder detail = new StringBuilder(
                analyzer.getContext().getString(R.string.triggers_wakeups_total, total));
        if (alarmW > 0) {
            detail.append(analyzer.getContext().getString(R.string.triggers_wakeups_alarms, alarmW));
            if (!alarmTags.isEmpty())
                detail.append(" (").append(String.join(", ", alarmTags)).append(")");
        }
        if (jobW   > 0) detail.append(analyzer.getContext().getString(R.string.triggers_wakeups_jobs,      jobW));
        if (gcmW   > 0) detail.append(analyzer.getContext().getString(R.string.triggers_wakeups_gcm,       gcmW));
        if (bcastW > 0) detail.append(analyzer.getContext().getString(R.string.triggers_wakeups_broadcast, bcastW));

        list.add(new TriggerInfo(TriggerInfo.Group.CAN_WAKE,
                AppTriggersAnalyzer.KEY_CAT_WAKEUPS,
                analyzer.getContext().getString(R.string.triggers_cat_wakeups),
                detail.toString(),
                analyzer.getContext().getString(R.string.triggers_wakeups_explanation),
                total>50 ? TriggerInfo.Severity.HIGH
                : total>15 ? TriggerInfo.Severity.MEDIUM : TriggerInfo.Severity.LOW));
        return list;
    }

// ---- analyzeBatteryStats ----
    public List<TriggerInfo> analyzeBatteryStats(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                "dumpsys batterystats " + packageName);
        if (output == null || output.trim().isEmpty()) return list;

        long wlMs=0; int wlCnt=0, alarms=0, jobs=0, syncs=0;
        double powerMah = -1;

        Pattern wp   = Pattern.compile("(?:Wakelock|wake_lock)\\s+\\S+[^:]*:\\s+(\\d+)ms\\s+(?:realtime|total)[^(]*\\((\\d+)\\s+times\\)", Pattern.CASE_INSENSITIVE);
        Pattern ap   = Pattern.compile("(?:Wakeup alarm|wakeup_alarm)[^:]*:\\s*(\\d+)\\s+times",                                          Pattern.CASE_INSENSITIVE);
        Pattern jp   = Pattern.compile("Job\\s+\\S+[^:]*:\\s+\\d+ms\\s+(?:realtime|total)[^(]*\\((\\d+)\\s+times\\)",                     Pattern.CASE_INSENSITIVE);
        Pattern sp   = Pattern.compile("Sync\\s+\\S+[^:]*:\\s+\\d+ms\\s+(?:realtime|total)[^(]*\\((\\d+)\\s+times\\)",                    Pattern.CASE_INSENSITIVE);

        Pattern pwrP = Pattern.compile("Uid\\s+u0a\\d+:\\s*([\\d.]+)(?:\\s*mAh)?", Pattern.CASE_INSENSITIVE);

        boolean inPowerSection = false;
        String  uid = analyzer.getCachedUid();

        for (String line : output.split("\n")) {
            try {

                if (line.contains("Estimated power use")) { inPowerSection = true; continue; }
                if (inPowerSection && !line.startsWith("  ")) inPowerSection = false;

                if (inPowerSection && powerMah < 0) {


                    if (uid != null) {
                        try {
                            int uidInt = Integer.parseInt(uid);
                            int appId  = uidInt - 10000;
                            if (appId >= 0 && line.contains("u0a" + appId)) {
                                Matcher mPwr = pwrP.matcher(line);
                                if (mPwr.find()) powerMah = Double.parseDouble(mPwr.group(1));
                            }
                        } catch (NumberFormatException ignored) {}
                    }

                    if (powerMah < 0 && line.contains(packageName)) {
                        Matcher mPwr = pwrP.matcher(line);
                        if (mPwr.find()) powerMah = Double.parseDouble(mPwr.group(1));
                    }
                }

                Matcher m;
                if ((m=wp.matcher(line)).find()) { wlMs+=Long.parseLong(m.group(1)); wlCnt+=Integer.parseInt(m.group(2)); continue; }
                if ((m=ap.matcher(line)).find()) { alarms+=Integer.parseInt(m.group(1)); continue; }
                if ((m=jp.matcher(line)).find()) { jobs  +=Integer.parseInt(m.group(1)); continue; }
                if ((m=sp.matcher(line)).find())   syncs +=Integer.parseInt(m.group(1));
            } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": analyzeBatteryStats line parse failed", e); }
        }
        if (wlCnt==0&&alarms==0&&jobs==0&&syncs==0&&powerMah<0) return list;
        AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": analyzeBatteryStats: pkg=" + packageName + " powerMah=" + powerMah + " wlCnt=" + wlCnt + " alarms=" + alarms + " jobs=" + jobs + " syncs=" + syncs);

        StringBuilder detail = new StringBuilder();
        if (powerMah >= 0)
            detail.append(String.format("%.2f mAh", powerMah));
        if (wlCnt  > 0) { if(detail.length()>0) detail.append(", ");
            detail.append(analyzer.getContext().getString(R.string.triggers_batterystats_wakelock, wlCnt, analyzer.formatDuration(wlMs))); }
        if (alarms > 0) { if(detail.length()>0) detail.append(", ");
            detail.append(analyzer.getContext().getString(R.string.triggers_batterystats_alarms, alarms)); }
        if (jobs   > 0) { if(detail.length()>0) detail.append(", ");
            detail.append(analyzer.getContext().getString(R.string.triggers_batterystats_jobs,   jobs)); }
        if (syncs  > 0) { if(detail.length()>0) detail.append(", ");
            detail.append(analyzer.getContext().getString(R.string.triggers_batterystats_syncs,  syncs)); }

        TriggerInfo.Severity sev = alarms>50||wlMs>600_000||(powerMah>50) ? TriggerInfo.Severity.HIGH
                : alarms>10||wlMs>60_000||jobs>20||(powerMah>10) ? TriggerInfo.Severity.MEDIUM : TriggerInfo.Severity.LOW;

        list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                AppTriggersAnalyzer.KEY_CAT_BATTERY_STATS,
                analyzer.getContext().getString(R.string.triggers_cat_batterystats),
                detail.toString(),
                analyzer.getContext().getString(R.string.triggers_batterystats_explanation), sev));
        return list;
    }

}
