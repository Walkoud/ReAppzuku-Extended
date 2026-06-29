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

public class SchedulingAnalyzer {

    private static final String FILE_NAME = "SchedulingAnalyzer";

    private final AppTriggersAnalyzer analyzer;
    private final DozeOpsAnalyzer dozeOpsAnalyzer;

    public SchedulingAnalyzer(AppTriggersAnalyzer analyzer, DozeOpsAnalyzer dozeOpsAnalyzer) {
        this.analyzer = analyzer;
        this.dozeOpsAnalyzer = dozeOpsAnalyzer;
    }

// ---- CONST:QUOTA_PAT ----
    private static final Pattern QUOTA_PAT = Pattern.compile(
            "RESTRICTED_BUCKET|QUOTA_EXCEEDED|INACTIVE|STANDBY_THROTTLE");


// ---- analyzeAlarms ----
    public List<TriggerInfo> analyzeAlarms(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = analyzer.getShellManager().runShellCommandAndGetFullOutput("dumpsys alarm");
        if (output == null || output.trim().isEmpty()) return list;

        AppTriggersAnalyzer.AlarmDumpsysParser parser = new AppTriggersAnalyzer.AlarmDumpsysParser();
        List<AppTriggersAnalyzer.AlarmEntry> entries = parser.parseEntries(output, packageName);
        List<String> topAlarmLines = parser.parseTopAlarms(output, packageName);

        int exactCount = 0, inexactCount = 0, awIdle = 0, clockCount = 0;
        int wakeupCount = 0, normalCount = 0;
        long minInterval = Long.MAX_VALUE, sumInterval = 0;
        int intervalSamples = 0;
        long minTriggerDiff = Long.MAX_VALUE;

        List<String> alarmDetails = new ArrayList<>();

        for (AppTriggersAnalyzer.AlarmEntry e : entries) {
            if (e.isWakeup) wakeupCount++; else normalCount++;
            if (e.isClockAlarm()) clockCount++;
            if (e.whileIdle) awIdle++;
            if (e.exact) exactCount++; else inexactCount++;

            if (e.intervalMs > 0) {
                if (e.intervalMs < minInterval) minInterval = e.intervalMs;
                sumInterval += e.intervalMs;
                intervalSamples++;
            }
            if (e.fireDiffMs != Long.MAX_VALUE && e.fireDiffMs < minTriggerDiff) {
                minTriggerDiff = e.fireDiffMs;
            }

            if (alarmDetails.size() < 5) {
                alarmDetails.add(buildAlarmDetailLine(e, packageName));
            }
        }

        int total = wakeupCount + normalCount;
        AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": analyzeAlarms: pkg=" + packageName + " total=" + total + " wakeup=" + wakeupCount + " exact=" + exactCount + " whileIdle=" + awIdle);
        if (total == 0 && topAlarmLines.isEmpty()) return list;

        StringBuilder detail = new StringBuilder();
        if (!alarmDetails.isEmpty()) {
            detail.append(String.join("\n", alarmDetails));
        } else {
            if (exactCount   > 0) detail.append(analyzer.getContext().getString(R.string.triggers_alarms_exact,      exactCount));
            if (inexactCount > 0) { if (detail.length() > 0) detail.append(", ");
                detail.append(analyzer.getContext().getString(R.string.triggers_alarms_inexact, inexactCount)); }
            if (awIdle       > 0) { if (detail.length() > 0) detail.append(", ");
                detail.append(analyzer.getContext().getString(R.string.triggers_alarms_while_idle, awIdle)); }
            if (clockCount   > 0) { if (detail.length() > 0) detail.append(", ");
                detail.append(analyzer.getContext().getString(R.string.triggers_alarms_clock, clockCount)); }
            if (wakeupCount  > 0 && detail.length() == 0)
                detail.append(analyzer.getContext().getString(R.string.triggers_alarms_wakeup_count, wakeupCount));
            if (normalCount  > 0 && wakeupCount == 0)
                detail.append(analyzer.getContext().getString(R.string.triggers_alarms_normal_count, normalCount));
        }
        if (minTriggerDiff != Long.MAX_VALUE)
            detail.append(analyzer.getContext().getString(R.string.triggers_alarms_next, analyzer.formatInterval(minTriggerDiff)));
        if (intervalSamples > 0)
            detail.append(analyzer.getContext().getString(R.string.triggers_alarms_avg_interval,
                    analyzer.formatInterval(sumInterval / intervalSamples)));
        if (!topAlarmLines.isEmpty())
            detail.append("\nTop: ").append(String.join(", ", topAlarmLines));

        StringBuilder expl = new StringBuilder();
        if (wakeupCount > 0) {
            expl.append(analyzer.getContext().getString(R.string.triggers_alarms_wakeup_explanation));
            if (minInterval < 60_000)       expl.append(analyzer.getContext().getString(R.string.triggers_alarms_wakeup_aggressive));
            else if (minInterval < 300_000) expl.append(analyzer.getContext().getString(R.string.triggers_alarms_wakeup_frequent));
        } else {
            expl.append(analyzer.getContext().getString(R.string.triggers_alarms_normal_explanation));
        }
        if (exactCount > 0) expl.append(analyzer.getContext().getString(R.string.triggers_alarms_exact_explanation));
        if (awIdle     > 0) expl.append(analyzer.getContext().getString(R.string.triggers_alarms_while_idle_explanation));

        TriggerInfo.Severity sev = wakeupCount > 0
                ? (minInterval < 120_000 ? TriggerInfo.Severity.HIGH : TriggerInfo.Severity.MEDIUM)
                : TriggerInfo.Severity.LOW;

        list.add(new TriggerInfo(TriggerInfo.Group.CAN_WAKE,
                AppTriggersAnalyzer.KEY_CAT_ALARMS,
                analyzer.getContext().getString(R.string.triggers_cat_alarms),
                detail.toString(), expl.toString(), sev));

        try {
            String cancelDetail = parseAlarmCancellations(output, packageName);
            if (cancelDetail != null) {
                list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                        AppTriggersAnalyzer.KEY_CAT_ALARMS_THROTTLED,
                        analyzer.getContext().getString(R.string.triggers_cat_alarms) + " [throttled]",
                        cancelDetail,
                        analyzer.getContext().getString(R.string.triggers_alarm_throttled_explanation),                        
                        TriggerInfo.Severity.MEDIUM));
            }
        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": alarm cancellation parse failed", e); }

        if (analyzer.apiLevel >= android.os.Build.VERSION_CODES.S
                && analyzer.apiLevel <= android.os.Build.VERSION_CODES.TIRAMISU) {
            list.addAll(dozeOpsAnalyzer.analyzeExactAlarmPermissions(packageName));
        }

        return list;
    }

// ---- buildAlarmDetailLine ----
    public String buildAlarmDetailLine(AppTriggersAnalyzer.AlarmEntry e, String packageName) {
        StringBuilder sb = new StringBuilder();
        switch (e.type) {
            case "RTC_WAKEUP":     sb.append("RTC_WU"); break;
            case "ELAPSED_WAKEUP": sb.append("EL_WU");  break;
            case "RTC":            sb.append("RTC");     break;
            default:               sb.append("ELAPSED"); break;
        }
        String shortTag = e.tag;
        if (shortTag.startsWith("*") && shortTag.contains("/"))
            shortTag = shortTag.substring(shortTag.indexOf('/') + 1);
        if (shortTag.startsWith(packageName + "/"))
            shortTag = shortTag.substring(packageName.length() + 1);
        if (shortTag.startsWith(packageName + "."))
            shortTag = shortTag.substring(packageName.length() + 1);
        if (shortTag.startsWith("."))
            shortTag = shortTag.substring(1);
        if (shortTag.length() > 40 && shortTag.contains("."))
            shortTag = shortTag.substring(shortTag.lastIndexOf('.') + 1);
        sb.append(" · ").append(shortTag);
        if (e.fireDiffMs != Long.MAX_VALUE) sb.append(" · in ").append(analyzer.formatInterval(e.fireDiffMs));
        if (e.intervalMs > 0)              sb.append(" · every ").append(analyzer.formatInterval(e.intervalMs));
        if (e.exact)                        sb.append(" · exact");
        if (e.whileIdle)                    sb.append(" · while-idle");
        if (e.pendingBroadcast)             sb.append(" · [queued broadcast]");
        if (e.quotaExceeded)               sb.append(" · [QUOTA_EXCEEDED]");
        return sb.toString();
    }

// ---- parseAlarmCancellations ----
    public String parseAlarmCancellations(String output, String packageName) {
        boolean inUidSection = false;
        int     cancelCount  = 0;
        String  lastReason   = null;
        String  lastBlocker  = null;

        String uidTag = null;
        if (analyzer.getCachedUid() != null) {
            try {
                int appId = Integer.parseInt(analyzer.getCachedUid()) - 10000;
                if (appId >= 0) uidTag = "u0a" + appId + ":";
            } catch (NumberFormatException ignored) {}
        }

        Pattern reasonPat = Pattern.compile("Reason=([\\w_]+)");
        Pattern policyPat = Pattern.compile(
                "app_standby=(-[\\d.smh]+)|battery_saver=(-[\\d.smh]+)|device_idle=(-[\\d.smh]+)");
        Pattern tagPat    = Pattern.compile(
                "tag=\\*alarm\\*:(" + Pattern.quote(packageName) + "[/\\w.]+)");

        boolean inCancelBlock = false;
        boolean blockHasPkg   = false;

        for (String line : output.split("\n")) {
            try {
                String t = line.trim();

                if (uidTag != null && t.startsWith(uidTag)) {
                    inUidSection = true; continue;
                }
                if (inUidSection && t.matches("u0a\\d+:.*")) {
                    inUidSection = false;
                }
                if (!inUidSection && uidTag == null && t.contains(packageName)) {
                    inUidSection = true;
                }
                if (!inUidSection) continue;

                if (t.startsWith("#") && t.contains("Reason=")) {
                    if (inCancelBlock && blockHasPkg) cancelCount++;
                    inCancelBlock = true;
                    blockHasPkg   = false;
                    Matcher m = reasonPat.matcher(t);
                    if (m.find()) lastReason = m.group(1);
                }

                if (inCancelBlock) {
                    if (tagPat.matcher(t).find()) blockHasPkg = true;
                    Matcher mPol = policyPat.matcher(t);
                    if (mPol.find()) {
                        if (mPol.group(1) != null)      lastBlocker = "app_standby";
                        else if (mPol.group(2) != null) lastBlocker = "battery_saver";
                        else if (mPol.group(3) != null) lastBlocker = "device_idle";
                    }
                    Matcher mQ = QUOTA_PAT.matcher(t);
                    if (mQ.find()) lastReason = mQ.group(0);
                    Matcher mFree = Pattern.compile("quotaTimeUntilFree=(\\d+)").matcher(t);
                    if (mFree.find()) {
                        long freeMs = Long.parseLong(mFree.group(1));
                        if (lastBlocker == null) lastBlocker = "quota(free in " + analyzer.formatDuration(freeMs) + ")";
                    }
                }
            } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": parseAlarmCancellations line failed", e); }
        }
        if (inCancelBlock && blockHasPkg) cancelCount++;

        if (cancelCount == 0) return null;

        String blocker = lastBlocker != null ? lastBlocker : (lastReason != null ? lastReason : "system");
        return cancelCount + "× alarm cancelled by " + blocker;
    }

// ---- analyzeJobs ----
    public List<TriggerInfo> analyzeJobs(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();

        try {
            String output = analyzer.getShellManager().runShellCommandAndGetFullOutput("dumpsys jobscheduler");
            if (output != null && !output.trim().isEmpty()) {
                int pending=0, running=0;
                int wmPending=0, wmRunning=0;
                int uijRunning=0, uijPending=0;
                int expeditedRunning=0, expeditedPending=0;
                int prefetchPending=0;
                boolean inPending=false, inRunning=false, inPast=false, inJobBlock=false;
                List<String> jobDetails  = new ArrayList<>();
                List<String> stopReasons = new ArrayList<>();
                StringBuilder jobBlock   = new StringBuilder();

                for (String line : output.split("\n")) {
                    String t = line.trim();
                    if (t.startsWith("Pending queue:") || t.startsWith("Pending:")
                            || t.startsWith("JobScheduler pending")) {
                        inPending=true; inRunning=false; inPast=false; continue;
                    }
                    if (t.startsWith("Active jobs:") || t.startsWith("Running:")
                            || t.startsWith("Currently running")) {
                        inRunning=true; inPending=false; inPast=false; continue;
                    }
                    if (t.startsWith("Past jobs:") || t.startsWith("History:")
                            || t.startsWith("Completed jobs:") || t.startsWith("Registered jobs:")) {
                        inPending=false; inRunning=false; inPast=true; continue;
                    }

                    boolean isJobHeader = t.startsWith("JOB #") || t.startsWith("JobInfo{")
                            || t.startsWith("Job{");

                    if ((inPending || inRunning) && isJobHeader && t.contains(packageName)) {
                        boolean isWmLine = t.contains("androidx.work") || t.contains("WorkManager")
                                || t.contains("systemjobscheduler");
                        boolean isUijLine = analyzer.apiLevel >= AppTriggersAnalyzer.API_BAL_PRIVILEGES
                                && (t.contains("isUserInitiated=true")
                                    || t.contains("userInitiated=true")
                                    || t.contains("RUN_USER_INITIATED_JOBS"));
                        boolean isExpeditedLine = analyzer.apiLevel >= android.os.Build.VERSION_CODES.S
                                && (t.contains("EXPEDITED")
                                    || t.contains("isExpedited=true")
                                    || t.contains("isExpedited: true"));
                        boolean isPrefetchLine = analyzer.apiLevel >= android.os.Build.VERSION_CODES.S
                                && (t.contains("isPrefetch=true") || t.contains("prefetch=true"));

                        if (inPending) {
                            pending++;
                            if (isWmLine)        wmPending++;
                            if (isUijLine)       uijPending++;
                            if (isExpeditedLine) expeditedPending++;
                            if (isPrefetchLine)  prefetchPending++;
                        }
                        if (inRunning) {
                            running++;
                            if (isWmLine)        wmRunning++;
                            if (isUijLine)       uijRunning++;
                            if (isExpeditedLine) expeditedRunning++;
                        }
                        inJobBlock=true; jobBlock.setLength(0);
                    }
                    if (inJobBlock) {
                        jobBlock.append(t).append("\n");
                        if (t.isEmpty() || (t.startsWith("JOB #") && jobBlock.length() > 10)) {
                            if (jobDetails.size() < 3) {
                                String d = parseJobBlock(jobBlock.toString());
                                if (d != null) jobDetails.add(d);
                            }
                            inJobBlock=false; jobBlock.setLength(0);
                        }
                    }
                    if (inPast && t.contains(packageName)) {
                        Matcher m = Pattern.compile("stopReason=([\\w_]+)").matcher(t);
                        if (m.find() && stopReasons.size() < 3) stopReasons.add(m.group(1));
                    }
                }

                if (pending > 0 || running > 0) {
                    AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": analyzeJobs: pkg=" + packageName + " running=" + running + " pending=" + pending);
                    StringBuilder detail = new StringBuilder();
                    if (running > 0) detail.append(analyzer.getContext().getString(R.string.triggers_jobs_detail_running, running));
                    if (pending > 0) { if (detail.length()>0) detail.append(", ");
                                       detail.append(analyzer.getContext().getString(R.string.triggers_jobs_detail_pending, pending)); }
                    if (wmPending > 0 || wmRunning > 0) {
                        detail.append(" · WM:");
                        if (wmRunning > 0) detail.append(wmRunning).append("r");
                        if (wmPending > 0) detail.append(wmPending).append("p");
                    }
                    if (uijRunning > 0 || uijPending > 0) {
                        detail.append(" · UIJ:");
                        if (uijRunning > 0) detail.append(uijRunning).append("r");
                        if (uijPending > 0) detail.append(uijPending).append("p");
                    }
                    if (expeditedRunning > 0 || expeditedPending > 0) {
                        detail.append(" · EXP:");
                        if (expeditedRunning > 0) detail.append(expeditedRunning).append("r");
                        if (expeditedPending > 0) detail.append(expeditedPending).append("p");
                    }
                    if (prefetchPending > 0) {
                        detail.append(" · prefetch:").append(prefetchPending);
                    }
                    if (!jobDetails.isEmpty())  detail.append(" · ").append(String.join("; ", jobDetails));
                    if (!stopReasons.isEmpty()) detail.append(analyzer.getContext().getString(
                            R.string.triggers_jobs_stop_reasons, String.join(", ", stopReasons)));

                    String expl = running>0&&pending>0
                            ? analyzer.getContext().getString(R.string.triggers_jobs_running_and_pending_explanation, running, pending)
                            : running>0 ? analyzer.getContext().getString(R.string.triggers_jobs_running_explanation, running)
                                        : analyzer.getContext().getString(R.string.triggers_jobs_pending_explanation, pending);

                    list.add(new TriggerInfo(TriggerInfo.Group.CAN_WAKE,
                            AppTriggersAnalyzer.KEY_CAT_JOBS,
                    analyzer.getContext().getString(R.string.triggers_cat_jobs),
                            detail.toString(), expl,
                            running > 0 ? TriggerInfo.Severity.HIGH : TriggerInfo.Severity.MEDIUM));
                }
            }
        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": analyzeJobs/jobscheduler failed", e); }

        if (list.isEmpty() && analyzer.apiLevel >= AppTriggersAnalyzer.API_BAL_PRIVILEGES) {
            AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": analyzeJobs: dumpsys empty, trying cmd jobscheduler fallback");
            List<String> cmdDetails = getJobsFallbackCmdJobscheduler(packageName);
            if (!cmdDetails.isEmpty()) {
                list.add(new TriggerInfo(TriggerInfo.Group.CAN_WAKE,
                        AppTriggersAnalyzer.KEY_CAT_JOBS,
                    analyzer.getContext().getString(R.string.triggers_cat_jobs),
                        String.join(", ", cmdDetails),
                        analyzer.getContext().getString(R.string.triggers_jobs_running_explanation, 0),
                        TriggerInfo.Severity.MEDIUM));
            }
        }

        return list;
    }

// ---- getJobsFallbackCmdJobscheduler ----
    private List<String> getJobsFallbackCmdJobscheduler(String packageName) {
        List<String> details = new ArrayList<>();
        try {
            String state = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                    "cmd jobscheduler get-job-state " + packageName);
            if (state != null && !state.trim().isEmpty() && !state.contains("Unknown")) {
                if (state.contains("Running")) details.add("running(cmd)");
                if (state.contains("Pending")) details.add("pending(cmd)");
                if (state.contains("Stopped")) details.add("stopped(cmd)");
            }
        } catch (Exception e) {
            AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": jobs cmd fallback failed", e);
        }
        return details;
    }

// ---- parseJobBlock ----
    public String parseJobBlock(String block) {
        List<String> parts = new ArrayList<>();

        boolean isWm = block.contains("WorkManager") || block.contains("androidx.work");


        Matcher mNet = Pattern.compile("required-network-type=([\\w_]+)").matcher(block);
        if (!mNet.find()) {
            mNet = Pattern.compile("networkType=([\\w_]+)").matcher(block);
            mNet.find();
        }
        try { if (mNet.group(1) != null) parts.add("net:" + mNet.group(1)); }
        catch (IllegalStateException ignored) {}


        if (block.contains("charging=true")        || block.contains("requireCharging=true"))   parts.add("charging");
        if (block.contains("idle=true")            || block.contains("requireDeviceIdle=true"))  parts.add("idle");
        if (block.contains("battery-not-low=true"))                                              parts.add("!batt-low");

        Matcher mPeriodHr = Pattern.compile(
                "period=\\+((?:(\\d+)h)?(?:(\\d+)m)?(\\d+)s)").matcher(block);
        Matcher mPeriodMs = Pattern.compile("periodMs=(\\d+)").matcher(block);
        if (mPeriodHr.find()) {
            long ms = 0;
            if (mPeriodHr.group(2) != null) ms += Long.parseLong(mPeriodHr.group(2)) * 3600_000L;
            if (mPeriodHr.group(3) != null) ms += Long.parseLong(mPeriodHr.group(3)) * 60_000L;
            ms += Long.parseLong(mPeriodHr.group(4)) * 1000L;
            if (ms > 0) parts.add("every " + analyzer.formatInterval(ms));
        } else if (mPeriodMs.find()) {
            long ms = Long.parseLong(mPeriodMs.group(1));
            if (ms > 0) parts.add("every " + analyzer.formatInterval(ms));
        }

        Matcher mLastRun = Pattern.compile(
                "(?:last-run|lastRunTime)=elapsed-((?:(\\d+)h)?(?:(\\d+)m)?(\\d+)s)").matcher(block);
        if (mLastRun.find()) {
            long ms = 0;
            if (mLastRun.group(2) != null) ms += Long.parseLong(mLastRun.group(2)) * 3600_000L;
            if (mLastRun.group(3) != null) ms += Long.parseLong(mLastRun.group(3)) * 60_000L;
            ms += Long.parseLong(mLastRun.group(4)) * 1000L;
            if (ms > 0) parts.add("last " + analyzer.formatInterval(ms) + " ago");
        }


        Matcher mDL = Pattern.compile("latest-runtime=(\\d+)").matcher(block);
        if (mDL.find()) {
            long diff = Long.parseLong(mDL.group(1)) - android.os.SystemClock.elapsedRealtime();
            if (diff > 0) parts.add("deadline:" + analyzer.formatInterval(diff));
        }


        Matcher mBk = Pattern.compile("backoff-policy=(\\w+)").matcher(block);
        if (mBk.find()) parts.add("backoff:" + mBk.group(1));

        if (isWm) parts.add(0, "WM");
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

// ---- analyzePendingIntents ----
    public List<TriggerInfo> analyzePendingIntents(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                "dumpsys activity intents");
        if (output == null || output.trim().isEmpty()) return list;


        final int MAX_PI_ENTRIES = 4;
        List<String> piEntries = new ArrayList<>();
        int actC=0, svcC=0, bcastC=0, alarmC=0, mediaC=0, pushC=0;
        List<String> creators = new ArrayList<>();

        Pattern recPat     = Pattern.compile(
                "PendingIntentRecord\\{[^}]+\\s+([\\w.]+)\\s+type=(\\w+)", Pattern.CASE_INSENSITIVE);
        Pattern actPat     = Pattern.compile("act=([\\w./-]+)");
        Pattern cmpPat     = Pattern.compile("cmp=([\\w./]+)");
        Pattern creatorPat = Pattern.compile("(?:creator=\\[|creatorPackage=)([\\w.]+)");

        boolean inBlock = false;
        String  blkType = null;
        String  blkAct  = null;
        String  blkCmp  = null;

        for (String line : output.split("\n")) {
            String t = line.trim();

            Matcher mRec = recPat.matcher(t);
            if (mRec.find()) {

                if (inBlock && blkType != null)
                    recordPiEntry(piEntries, blkType, blkAct, blkCmp, MAX_PI_ENTRIES, packageName);

                String owner = mRec.group(1);
                blkType = mRec.group(2).toLowerCase();
                blkAct  = null;
                blkCmp  = null;
                inBlock = owner.equals(packageName);

                if (inBlock) {
                    switch (blkType) {
                        case "activity":  actC++;  break;
                        case "service":   svcC++;  break;
                        case "broadcast": bcastC++; break;
                    }
                }

                Matcher mCr = creatorPat.matcher(t);
                if (mCr.find()) {
                    String cr = mCr.group(1);
                    if (!cr.equals(packageName) && !creators.contains(cr)) creators.add(cr);
                }
                continue;
            }

            if (!inBlock) {

                if (!t.contains(packageName)) continue;
                if      (t.contains("type=activity")  || t.contains("Activity"))  actC++;
                else if (t.contains("type=service")   || t.contains("Service"))   svcC++;
                else if (t.contains("type=broadcast") || t.contains("Broadcast")) bcastC++;
                Matcher mA = actPat.matcher(t);
                if (mA.find()) {
                    String a = mA.group(1);
                    if (a.contains("ALARM") || a.contains("alarmmanager")) alarmC++;
                    if (a.contains("MEDIA_BUTTON"))                        mediaC++;
                    if (a.contains("GCM")||a.contains("FCM")
                            ||a.contains("push")||a.contains("PUSH"))      pushC++;
                }
                Matcher mCr = creatorPat.matcher(t);
                if (mCr.find()) {
                    String cr = mCr.group(1);
                    if (!cr.equals(packageName) && !creators.contains(cr)) creators.add(cr);
                }
                continue;
            }


            Matcher mA = actPat.matcher(t);
            if (mA.find() && blkAct == null) {
                blkAct = mA.group(1);
                if (blkAct.contains("ALARM") || blkAct.contains("alarmmanager")) alarmC++;
                if (blkAct.contains("MEDIA_BUTTON"))                              mediaC++;
                if (blkAct.contains("GCM")||blkAct.contains("FCM")
                        ||blkAct.contains("push")||blkAct.contains("PUSH"))       pushC++;
            }
            Matcher mCmp = cmpPat.matcher(t);
            if (mCmp.find() && blkCmp == null) blkCmp = mCmp.group(1);
        }

        if (inBlock && blkType != null)
            recordPiEntry(piEntries, blkType, blkAct, blkCmp, MAX_PI_ENTRIES, packageName);

        int total = actC + svcC + bcastC;
        if (total == 0) return list;


        StringBuilder detail = new StringBuilder();
        if (!piEntries.isEmpty()) {
            detail.append(String.join("\n", piEntries));
            if (total > piEntries.size())
                detail.append("\n+").append(total - piEntries.size()).append(" more");
        } else {

            if (actC   > 0) detail.append(analyzer.getContext().getString(R.string.triggers_pending_activity,  actC));
            if (svcC   > 0) { if(detail.length()>0) detail.append(", ");
                detail.append(analyzer.getContext().getString(R.string.triggers_pending_service,  svcC)); }
            if (bcastC > 0) { if(detail.length()>0) detail.append(", ");
                detail.append(analyzer.getContext().getString(R.string.triggers_pending_broadcast, bcastC)); }
        }
        if (alarmC > 0) detail.append(analyzer.getContext().getString(R.string.triggers_pending_alarm,        alarmC));
        if (mediaC > 0) detail.append(analyzer.getContext().getString(R.string.triggers_pending_media_button, mediaC));
        if (pushC  > 0) detail.append(analyzer.getContext().getString(R.string.triggers_pending_push,         pushC));
        if (!creators.isEmpty())
            detail.append(analyzer.getContext().getString(R.string.triggers_pending_creators,
                    String.join(", ", creators.subList(0, Math.min(creators.size(), 2)))));

        list.add(new TriggerInfo(TriggerInfo.Group.CAN_WAKE,
                AppTriggersAnalyzer.KEY_CAT_PENDING_INTENTS,
                analyzer.getContext().getString(R.string.triggers_cat_pending_intents, total),
                detail.toString(),
                analyzer.getContext().getString(R.string.triggers_pending_explanation),
                TriggerInfo.Severity.MEDIUM));
        return list;
    }

// ---- recordPiEntry ----
    public void recordPiEntry(List<String> entries, String type, String act,
            String cmp, int maxEntries, String packageName) {
        if (entries.size() >= maxEntries) return;
        StringBuilder sb = new StringBuilder();
        switch (type) {
            case "broadcast": sb.append("BC"); break;
            case "service":   sb.append("SV"); break;
            case "activity":  sb.append("AC"); break;
            default: sb.append(type.substring(0, Math.min(2, type.length())).toUpperCase());
        }
        if (cmp != null) {
            String cls = cmp.contains("/") ? cmp.substring(cmp.indexOf('/') + 1) : cmp;
            if (cls.startsWith(packageName + ".")) cls = cls.substring(packageName.length() + 1);
            if (cls.startsWith(".")) cls = cls.substring(1);
            if (cls.length() > 40 && cls.contains("."))
                cls = cls.substring(cls.lastIndexOf('.') + 1);
            sb.append(" → ").append(cls);
        } else if (act != null) {
            sb.append(" → ").append(analyzer.shortenAction(act));
        }
        entries.add(sb.toString());
    }

}
