package com.gree1d.reappzuku;

import android.content.Context;
import android.util.Log;

import com.gree1d.reappzuku.AppTriggersAnalyzer.TriggerInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppTriggerAnalyzersExt {

    private static final String TAG = "AppTriggerAnalyzersExt";

    private final Context              context;
    private final ShellManager         shellManager;
    private final AppTriggersAnalyzer  analyzer;

    private String cachedBroadcastHistory = null;

    public AppTriggerAnalyzersExt(Context context, ShellManager shellManager,
                                   AppTriggersAnalyzer analyzer) {
        this.context      = context.getApplicationContext();
        this.shellManager = shellManager;
        this.analyzer     = analyzer;
    }

    private String getBroadcastHistory() {
        if (cachedBroadcastHistory == null) {
            cachedBroadcastHistory = shellManager.runShellCommandAndGetFullOutput(
                    "dumpsys activity broadcasts history");
            if (cachedBroadcastHistory == null) cachedBroadcastHistory = "";
        }
        return cachedBroadcastHistory.isEmpty() ? null : cachedBroadcastHistory;
    }

    List<TriggerInfo> analyzeAlarms(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = shellManager.runShellCommandAndGetFullOutput("dumpsys alarm");
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
        if (total == 0 && topAlarmLines.isEmpty()) return list;

        StringBuilder detail = new StringBuilder();
        if (!alarmDetails.isEmpty()) {
            detail.append(String.join("\n", alarmDetails));
        } else {
            if (exactCount   > 0) detail.append(context.getString(R.string.triggers_alarms_exact,      exactCount));
            if (inexactCount > 0) { if (detail.length() > 0) detail.append(", ");
                detail.append(context.getString(R.string.triggers_alarms_inexact, inexactCount)); }
            if (awIdle       > 0) { if (detail.length() > 0) detail.append(", ");
                detail.append(context.getString(R.string.triggers_alarms_while_idle, awIdle)); }
            if (clockCount   > 0) { if (detail.length() > 0) detail.append(", ");
                detail.append(context.getString(R.string.triggers_alarms_clock, clockCount)); }
            if (wakeupCount  > 0 && detail.length() == 0)
                detail.append(context.getString(R.string.triggers_alarms_wakeup_count, wakeupCount));
            if (normalCount  > 0 && wakeupCount == 0)
                detail.append(context.getString(R.string.triggers_alarms_normal_count, normalCount));
        }
        if (minTriggerDiff != Long.MAX_VALUE)
            detail.append(context.getString(R.string.triggers_alarms_next, analyzer.formatInterval(minTriggerDiff)));
        if (intervalSamples > 0)
            detail.append(context.getString(R.string.triggers_alarms_avg_interval,
                    analyzer.formatInterval(sumInterval / intervalSamples)));
        if (!topAlarmLines.isEmpty())
            detail.append("\nTop: ").append(String.join(", ", topAlarmLines));

        StringBuilder expl = new StringBuilder();
        if (wakeupCount > 0) {
            expl.append(context.getString(R.string.triggers_alarms_wakeup_explanation));
            if (minInterval < 60_000)       expl.append(context.getString(R.string.triggers_alarms_wakeup_aggressive));
            else if (minInterval < 300_000) expl.append(context.getString(R.string.triggers_alarms_wakeup_frequent));
        } else {
            expl.append(context.getString(R.string.triggers_alarms_normal_explanation));
        }
        if (exactCount > 0) expl.append(context.getString(R.string.triggers_alarms_exact_explanation));
        if (awIdle     > 0) expl.append(context.getString(R.string.triggers_alarms_while_idle_explanation));

        TriggerInfo.Severity sev = wakeupCount > 0
                ? (minInterval < 120_000 ? TriggerInfo.Severity.HIGH : TriggerInfo.Severity.MEDIUM)
                : TriggerInfo.Severity.LOW;

        list.add(new TriggerInfo(TriggerInfo.Group.CAN_WAKE,
                context.getString(R.string.triggers_cat_alarms),
                detail.toString(), expl.toString(), sev));

        try {
            String cancelDetail = parseAlarmCancellations(output, packageName);
            if (cancelDetail != null) {
                list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                        context.getString(R.string.triggers_cat_alarms) + " [throttled]",
                        cancelDetail,
                        context.getString(R.string.triggers_alarm_throttled_explanation),                        
                        TriggerInfo.Severity.MEDIUM));
            }
        } catch (Exception e) { Log.w(TAG, "alarm cancellation parse failed: " + e.getMessage()); }

        if (analyzer.apiLevel >= android.os.Build.VERSION_CODES.S
                && analyzer.apiLevel <= android.os.Build.VERSION_CODES.TIRAMISU) {
            list.addAll(analyzeExactAlarmPermissions(packageName));
        }

        return list;
    }

    String buildAlarmDetailLine(AppTriggersAnalyzer.AlarmEntry e, String packageName) {
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

    private static final Pattern QUOTA_PAT = Pattern.compile(
            "RESTRICTED_BUCKET|QUOTA_EXCEEDED|INACTIVE|STANDBY_THROTTLE");

    private String parseAlarmCancellations(String output, String packageName) {
        boolean inUidSection = false;
        int     cancelCount  = 0;
        String  lastReason   = null;
        String  lastBlocker  = null;

        String uidTag = null;
        if (analyzer.cachedUid != null) {
            try {
                int appId = Integer.parseInt(analyzer.cachedUid) - 10000;
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
            } catch (Exception e) { Log.w(TAG, "parseAlarmCancellations line failed: " + e.getMessage()); }
        }
        if (inCancelBlock && blockHasPkg) cancelCount++;

        if (cancelCount == 0) return null;

        String blocker = lastBlocker != null ? lastBlocker : (lastReason != null ? lastReason : "system");
        return cancelCount + "× alarm cancelled by " + blocker;
    }

    List<TriggerInfo> analyzeJobs(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();

        try {
            String output = shellManager.runShellCommandAndGetFullOutput("dumpsys jobscheduler");
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
                            || t.startsWith("Completed jobs:")) {
                        inPending=false; inRunning=false; inPast=true; continue;
                    }

                    if ((inPending || inRunning) && t.contains(packageName)) {
                        boolean isWmLine = t.contains("androidx.work") || t.contains("WorkManager")
                                || t.contains("systemjobscheduler");
                        boolean isUijLine = analyzer.apiLevel >= AppTriggersAnalyzer.API_BAL_PRIVILEGES
                                && (t.contains("isUserInitiated=true")
                                    || t.contains("userInitiated=true")
                                    || t.contains("RUN_USER_INITIATED_JOBS"));

                        boolean isExpeditedLine = analyzer.apiLevel >= android.os.Build.VERSION_CODES.S
                                && analyzer.apiLevel <= android.os.Build.VERSION_CODES.TIRAMISU
                                && (t.contains("EXPEDITED")
                                    || t.contains("isExpedited=true")
                                    || t.contains("isExpedited: true"));
                        boolean isPrefetchLine = analyzer.apiLevel == android.os.Build.VERSION_CODES.TIRAMISU
                                && (t.contains("isPrefetch=true") || t.contains("prefetch=true"));

                        if (inPending) {
                            pending++;
                            if (isWmLine)       wmPending++;
                            if (isUijLine)      uijPending++;
                            if (isExpeditedLine) expeditedPending++;
                            if (isPrefetchLine)  prefetchPending++;
                        }
                        if (inRunning) {
                            running++;
                            if (isWmLine)       wmRunning++;
                            if (isUijLine)      uijRunning++;
                            if (isExpeditedLine) expeditedRunning++;
                        }
                        if (t.startsWith("JOB #") || t.startsWith("JobInfo{")
                                || t.startsWith("Job{"))
                            { inJobBlock=true; jobBlock.setLength(0); }
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
                    StringBuilder detail = new StringBuilder();
                    if (running > 0) detail.append(context.getString(R.string.triggers_jobs_detail_running, running));
                    if (pending > 0) { if (detail.length()>0) detail.append(", ");
                                       detail.append(context.getString(R.string.triggers_jobs_detail_pending, pending)); }
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
                    if (!stopReasons.isEmpty()) detail.append(context.getString(
                            R.string.triggers_jobs_stop_reasons, String.join(", ", stopReasons)));

                    String expl = running>0&&pending>0
                            ? context.getString(R.string.triggers_jobs_running_and_pending_explanation, running, pending)
                            : running>0 ? context.getString(R.string.triggers_jobs_running_explanation, running)
                                        : context.getString(R.string.triggers_jobs_pending_explanation, pending);

                    list.add(new TriggerInfo(TriggerInfo.Group.CAN_WAKE,
                            context.getString(R.string.triggers_cat_jobs),
                            detail.toString(), expl,
                            running > 0 ? TriggerInfo.Severity.HIGH : TriggerInfo.Severity.MEDIUM));
                }
            }
        } catch (Exception e) { Log.w(TAG, "analyzeJobs/jobscheduler failed: " + e.getMessage()); }

        if (list.isEmpty() && analyzer.apiLevel >= AppTriggersAnalyzer.API_BAL_PRIVILEGES) {
            List<String> cmdDetails = getJobsFallbackCmdJobscheduler(packageName);
            if (!cmdDetails.isEmpty()) {
                list.add(new TriggerInfo(TriggerInfo.Group.CAN_WAKE,
                        context.getString(R.string.triggers_cat_jobs),
                        String.join(", ", cmdDetails),
                        context.getString(R.string.triggers_jobs_running_explanation, 0),
                        TriggerInfo.Severity.MEDIUM));
            }
        }

        return list;
    }

    private List<String> getJobsFallbackCmdJobscheduler(String packageName) {
        List<String> details = new ArrayList<>();
        try {
            String state = shellManager.runShellCommandAndGetFullOutput(
                    "cmd jobscheduler get-job-state " + packageName);
            if (state != null && !state.trim().isEmpty() && !state.contains("Unknown")) {
                if (state.contains("Running")) details.add("running(cmd)");
                if (state.contains("Pending")) details.add("pending(cmd)");
                if (state.contains("Stopped")) details.add("stopped(cmd)");
            }
        } catch (Exception e) {
            Log.w(TAG, "jobs cmd fallback failed: " + e.getMessage());
        }
        return details;
    }

    String parseJobBlock(String block) {
        List<String> parts = new ArrayList<>();

        boolean isWm = block.contains("WorkManager") || block.contains("androidx.work");


        Matcher mNet = Pattern.compile("required-network-type=([\\w_]+)").matcher(block);
        if (!mNet.find()) mNet = Pattern.compile("networkType=([\\w_]+)").matcher(block);
        if (mNet.find()) parts.add("net:" + mNet.group(1));


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
            long diff = Long.parseLong(mDL.group(1)) - System.currentTimeMillis();
            if (diff > 0) parts.add("deadline:" + analyzer.formatInterval(diff));
        }


        Matcher mBk = Pattern.compile("backoff-policy=(\\w+)").matcher(block);
        if (mBk.find()) parts.add("backoff:" + mBk.group(1));

        if (isWm) parts.add(0, "WM");
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    List<TriggerInfo> analyzePendingIntents(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = shellManager.runShellCommandAndGetFullOutput(
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

            if (actC   > 0) detail.append(context.getString(R.string.triggers_pending_activity,  actC));
            if (svcC   > 0) { if(detail.length()>0) detail.append(", ");
                detail.append(context.getString(R.string.triggers_pending_service,  svcC)); }
            if (bcastC > 0) { if(detail.length()>0) detail.append(", ");
                detail.append(context.getString(R.string.triggers_pending_broadcast, bcastC)); }
        }
        if (alarmC > 0) detail.append(context.getString(R.string.triggers_pending_alarm,        alarmC));
        if (mediaC > 0) detail.append(context.getString(R.string.triggers_pending_media_button, mediaC));
        if (pushC  > 0) detail.append(context.getString(R.string.triggers_pending_push,         pushC));
        if (!creators.isEmpty())
            detail.append(context.getString(R.string.triggers_pending_creators,
                    String.join(", ", creators.subList(0, Math.min(creators.size(), 2)))));

        list.add(new TriggerInfo(TriggerInfo.Group.CAN_WAKE,
                context.getString(R.string.triggers_cat_pending_intents, total),
                detail.toString(),
                context.getString(R.string.triggers_pending_explanation),
                TriggerInfo.Severity.MEDIUM));
        return list;
    }


    void recordPiEntry(List<String> entries, String type, String act,
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


    List<TriggerInfo> analyzeExcessiveWakeups(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = shellManager.runShellCommandAndGetFullOutput(
                "dumpsys batterystats " + packageName);
        if (output == null || output.trim().isEmpty()) return list;

        int alarmW=0, jobW=0, gcmW=0, bcastW=0;
        List<String> alarmTags = new ArrayList<>();


        Pattern ap = Pattern.compile(
                "Wakeup alarm\\s+([\\w./]+):\\s*(\\d+)\\s+times", Pattern.CASE_INSENSITIVE);
        Pattern jp = Pattern.compile(
                "Job\\s+\\S+:\\s+\\d+ms.*?\\((\\d+)\\s+times\\)", Pattern.CASE_INSENSITIVE);
        Pattern gp = Pattern.compile(
                "(?:GCM|FCM|push).*?wakeup.*?:\\s*(\\d+)",        Pattern.CASE_INSENSITIVE);
        Pattern bp = Pattern.compile(
                "Broadcast\\s+\\S+.*?\\((\\d+)\\s+times\\)",      Pattern.CASE_INSENSITIVE);

        for (String line : output.split("\n")) {
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
            } catch (Exception e) { Log.w(TAG, "analyzeExcessiveWakeups line parse failed: " + e.getMessage()); }
        }

        int total = alarmW + jobW + gcmW + bcastW;
        if (total == 0) return list;

        StringBuilder detail = new StringBuilder(
                context.getString(R.string.triggers_wakeups_total, total));
        if (alarmW > 0) {
            detail.append(context.getString(R.string.triggers_wakeups_alarms, alarmW));
            if (!alarmTags.isEmpty())
                detail.append(" (").append(String.join(", ", alarmTags)).append(")");
        }
        if (jobW   > 0) detail.append(context.getString(R.string.triggers_wakeups_jobs,      jobW));
        if (gcmW   > 0) detail.append(context.getString(R.string.triggers_wakeups_gcm,       gcmW));
        if (bcastW > 0) detail.append(context.getString(R.string.triggers_wakeups_broadcast, bcastW));

        list.add(new TriggerInfo(TriggerInfo.Group.CAN_WAKE,
                context.getString(R.string.triggers_cat_wakeups),
                detail.toString(),
                context.getString(R.string.triggers_wakeups_explanation),
                total>50 ? TriggerInfo.Severity.HIGH
                : total>15 ? TriggerInfo.Severity.MEDIUM : TriggerInfo.Severity.LOW));
        return list;
    }


    private static final Pattern BAL_FGS_PAT = Pattern.compile(
            "backgroundStartPrivileges.*?allowsBackgroundForegroundServiceStarts=(true|false)",
            Pattern.DOTALL);
    private static final Pattern BAL_ACTIVITY_PAT = Pattern.compile(
            "backgroundStartPrivileges.*?allowsBackgroundActivityStarts=(true|false)",
            Pattern.DOTALL);

    List<TriggerInfo> analyzeChainLaunch(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();


        try {
            String procOut = shellManager.runShellCommandAndGetFullOutput("dumpsys activity processes");
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
                                    context.getString(R.string.triggers_cat_chain_launch),
                                    context.getString(R.string.triggers_chain_direct_detail, name+"("+caller+")"),
                                    context.getString(R.string.triggers_chain_direct_explanation, name),
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
                                    context.getString(R.string.triggers_cat_chain_launch),
                                    "BackgroundStartPrivilege: " + detail,
                                    context.getString(R.string.triggers_bal_privilege_explanation),                                    
                                    TriggerInfo.Severity.HIGH));
                        }
                    }
                }
            }
        } catch (Exception e) { Log.w(TAG, "chain/processes failed: " + e.getMessage()); }


        try {
            String bcastOut = getBroadcastHistory();
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
                for (int i = 0; i < Math.min(callers.size(), 3); i++) {
                    String pkg  = callers.get(i);
                    String name = analyzer.resolveAppName(pkg);
                    list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                            context.getString(R.string.triggers_cat_chain_launch),
                            context.getString(R.string.triggers_chain_broadcast_detail, name+"("+pkg+")", actions.get(i)),
                            context.getString(R.string.triggers_chain_broadcast_explanation, name, actions.get(i)),
                            TriggerInfo.Severity.MEDIUM));
                }
            }
        } catch (Exception e) { Log.w(TAG, "chain/broadcasts failed: " + e.getMessage()); }

        if (list.isEmpty() && analyzer.apiLevel >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            list.addAll(analyzeChainLaunchLogcatFallback(packageName));
        }

        return list;
    }

    private List<TriggerInfo> analyzeChainLaunchLogcatFallback(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        try {
            String logcat = shellManager.runShellCommandAndGetFullOutput(
                    "logcat -d -t 200 -s ActivityTaskManager:W | grep " + packageName);
            if (logcat == null || logcat.trim().isEmpty()) return list;

            Pattern callerPat = Pattern.compile("callingPackage:\\s*([\\w.]+)");

            for (String line : logcat.split("\n")) {
                if (!line.contains("blocked") || !line.contains(packageName)) continue;
                Matcher mC = callerPat.matcher(line);
                String caller = mC.find() ? mC.group(1) : "unknown";
                list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                        context.getString(R.string.triggers_cat_chain_launch),
                        context.getString(R.string.triggers_bal_blocked_detail_prefix) + caller,
                        context.getString(R.string.triggers_bal_blocked_explanation),                        
                        TriggerInfo.Severity.MEDIUM));
                if (list.size() >= 2) break;
            }
        } catch (Exception e) {
            Log.w(TAG, "chain/logcat fallback failed: " + e.getMessage());
        }
        return list;
    }

    List<TriggerInfo> analyzeBroadcastReceivers(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();


        String pkgOut = shellManager.runShellCommandAndGetFullOutput(
                "dumpsys package " + packageName);
        List<String> staticActions = new ArrayList<>();
        if (pkgOut != null) {
            boolean inSection = false;
            for (String line : pkgOut.split("\n")) {
                String t = line.trim();
                if (t.startsWith("Receiver #") || t.startsWith("ReceiverInfo{")) inSection = true;
                if (inSection && t.startsWith("Action:")) {
                    String a = analyzer.shortenAction(
                            t.replaceFirst("Action:\\s*\"?", "").replace("\"", "").trim());
                    if (!staticActions.contains(a)) staticActions.add(a);
                }
                if (inSection && t.startsWith("Service #")) break;
            }
        }


        List<String> dynamicActions = new ArrayList<>();
        int exportedDynamicReceivers = 0;
        try {
            String regOut = shellManager.runShellCommandAndGetFullOutput(
                    "dumpsys activity broadcasts registered");
            if (regOut != null) {
                boolean inBlock = false;
                boolean blockExported = false;
                for (String line : regOut.split("\n")) {
                    String t = line.trim();
                    if (t.startsWith("ReceiverList{") || t.startsWith("* ReceiverList")) {
                        inBlock = t.contains(packageName);
                        blockExported = false;
                        continue;
                    }
                    if (inBlock && t.startsWith("ReceiverList{") && !t.contains(packageName)) {
                        inBlock = false;
                        continue;
                    }
                    if (!inBlock) continue;

                    if (analyzer.apiLevel == android.os.Build.VERSION_CODES.TIRAMISU) {
                        if (t.contains("exported=true")) {
                            blockExported = true;
                            exportedDynamicReceivers++;
                        }
                    }

                    if (t.startsWith("Action:") || t.startsWith("+ Action:")) {
                        String a = analyzer.shortenAction(
                                t.replaceFirst("\\+?\\s*Action:\\s*\"?", "").replace("\"", "").trim());
                        if (!a.isEmpty() && !dynamicActions.contains(a)) dynamicActions.add(a);
                    }
                }
            }
        } catch (Exception e) { Log.w(TAG, "dynamic receivers failed: " + e.getMessage()); }

        if (staticActions.isEmpty() && dynamicActions.isEmpty()) return list;


        if (!staticActions.isEmpty()) {
            int shown = Math.min(staticActions.size(), 5);
            StringBuilder detail = new StringBuilder(
                    String.join(", ", staticActions.subList(0, shown)));
            if (staticActions.size() > shown)
                detail.append(context.getString(
                        R.string.triggers_receivers_detail_overflow, staticActions.size() - shown));

            StringBuilder expl = new StringBuilder(
                    context.getString(R.string.triggers_receivers_explanation_base));
            if (staticActions.stream().anyMatch(a -> a.contains("BOOT") || a.contains("LOCKED_BOOT")))
                expl.append(context.getString(R.string.triggers_receivers_explanation_boot));
            if (staticActions.stream().anyMatch(a -> a.contains("CONNECTIVITY") || a.contains("NETWORK")))
                expl.append(context.getString(R.string.triggers_receivers_explanation_network));

            list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                    context.getString(R.string.triggers_cat_receivers, staticActions.size()),
                    detail.toString(), expl.toString(), TriggerInfo.Severity.MEDIUM));
        }


        if (!dynamicActions.isEmpty()) {
            int shown = Math.min(dynamicActions.size(), 5);
            StringBuilder detail = new StringBuilder(
                    String.join(", ", dynamicActions.subList(0, shown)));
            if (dynamicActions.size() > shown)
                detail.append(context.getString(
                        R.string.triggers_receivers_detail_overflow, dynamicActions.size() - shown));
            if (exportedDynamicReceivers > 0)
                detail.append(" · exported=").append(exportedDynamicReceivers);

            list.add(new TriggerInfo(TriggerInfo.Group.CAN_WAKE,
                    context.getString(R.string.triggers_cat_receivers_dynamic, dynamicActions.size()),
                    detail.toString(),
                    context.getString(R.string.triggers_receivers_explanation_base),
                    dynamicActions.size() > 3 ? TriggerInfo.Severity.HIGH : TriggerInfo.Severity.MEDIUM));
        }

        if (exportedDynamicReceivers > 0
                && analyzer.apiLevel == android.os.Build.VERSION_CODES.TIRAMISU) {
            list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                    context.getString(R.string.triggers_cat_receivers_dynamic, exportedDynamicReceivers),
                    context.getString(R.string.triggers_receiver_exported_detail),
                    context.getString(R.string.triggers_receiver_exported_explanation),                    
                    TriggerInfo.Severity.MEDIUM));
        }

        return list;
    }

    List<TriggerInfo> analyzeBootReceivers(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        boolean hasBoot = false, hasLocked = false;

        try {
            String o1 = shellManager.runShellCommandAndGetFullOutput(
                    "cmd package query-receivers -a android.intent.action.BOOT_COMPLETED");
            if (o1 != null && !o1.contains("Unknown option") && o1.contains(packageName))
                hasBoot = true;
        } catch (Exception e) { Log.w(TAG, "boot query-receivers failed: " + e.getMessage()); }
        try {
            String o2 = shellManager.runShellCommandAndGetFullOutput(
                    "cmd package query-receivers -a android.intent.action.LOCKED_BOOT_COMPLETED");
            if (o2 != null && !o2.contains("Unknown option") && o2.contains(packageName))
                hasLocked = true;
        } catch (Exception e) { Log.w(TAG, "locked-boot query-receivers failed: " + e.getMessage()); }

        if (!hasBoot && !hasLocked) {
            try {
                String pkgOut = shellManager.runShellCommandAndGetFullOutput(
                        "dumpsys package " + packageName);
                if (pkgOut != null) {
                    boolean inReceivers = false;
                    for (String line : pkgOut.split("\n")) {
                        String t = line.trim();
                        if (t.startsWith("Receiver #") || t.equals("receivers:")) inReceivers = true;
                        if (inReceivers && (t.startsWith("Activity #")
                                || t.startsWith("Service #")
                                || t.startsWith("Provider #"))) break;
                        if (!inReceivers && (t.contains("BOOT_COMPLETED")
                                || t.contains("LOCKED_BOOT"))) {
                            if (t.contains("BOOT_COMPLETED"))  hasBoot   = true;
                            if (t.contains("LOCKED_BOOT"))     hasLocked = true;
                            continue;
                        }
                        if (!inReceivers) continue;
                        if (t.contains("BOOT_COMPLETED"))  hasBoot   = true;
                        if (t.contains("LOCKED_BOOT"))     hasLocked = true;
                        if (hasBoot && hasLocked) break;
                    }
                }
            } catch (Exception e) { Log.w(TAG, "boot/package fallback failed: " + e.getMessage()); }
        }

        if (!hasBoot && !hasLocked) return list;

        String detail = hasBoot && hasLocked ? context.getString(R.string.triggers_boot_detail_both)
                : hasLocked ? context.getString(R.string.triggers_boot_detail_locked)
                            : context.getString(R.string.triggers_boot_detail_normal);
        String expl = hasBoot && hasLocked ? context.getString(R.string.triggers_boot_explanation_both)
                : hasLocked ? context.getString(R.string.triggers_boot_explanation_locked)
                            : context.getString(R.string.triggers_boot_explanation_normal);

        list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                context.getString(R.string.triggers_cat_boot), detail, expl,
                TriggerInfo.Severity.HIGH));

        if (analyzer.apiLevel >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            list.addAll(analyzeBootFgsRestriction(packageName));
        }

        return list;
    }

    private List<TriggerInfo> analyzeBootFgsRestriction(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        try {
            String bh = getBroadcastHistory();
            if (bh != null && bh.contains(packageName)
                    && bh.contains("FGS_BOOT_COMPLETED_RESTRICTIONS")) {
                list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                        context.getString(R.string.triggers_cat_boot),
                        context.getString(R.string.triggers_fgs_boot_blocked_detail),                        
                        context.getString(R.string.triggers_fgs_boot_blocked_explanation),                        
                        TriggerInfo.Severity.HIGH));
            }

            String logcat = shellManager.runShellCommandAndGetFullOutput(
                    "logcat -d -t 100 -s ActivityManager:E | grep " + packageName);
            if (logcat != null
                    && logcat.contains("ForegroundServiceStartNotAllowedException")
                    && logcat.contains(packageName)) {
                list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                        context.getString(R.string.triggers_cat_boot),
                        context.getString(R.string.triggers_fgs_boot_exception_detail),
                        context.getString(R.string.triggers_fgs_boot_exception_explanation),                        
                        TriggerInfo.Severity.HIGH));
            }
        } catch (Exception e) {
            Log.w(TAG, "bootFgsRestriction fallback failed: " + e.getMessage());
        }
        return list;
    }

    List<TriggerInfo> analyzeContentProviders(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = shellManager.runShellCommandAndGetFullOutput(
                "dumpsys package " + packageName);
        if (output == null || output.trim().isEmpty()) return list;

        boolean inSection = false;
        List<String> auths = new ArrayList<>();
        for (String line : output.split("\n")) {
            String t = line.trim();
            if (t.startsWith("Provider #")) inSection = true;
            if (inSection && t.startsWith("authority=")) {
                String a = t.replaceFirst("authority=", "").trim();
                if (a.startsWith(packageName + ".")) a = a.substring(packageName.length() + 1);
                if (!auths.contains(a)) auths.add(a);
            }
            if (inSection && t.startsWith("Activity #")) break;
        }
        if (auths.isEmpty()) return list;

        int shown = Math.min(auths.size(), 3);
        String detail = String.join(", ", auths.subList(0, shown))
                + (auths.size() > shown ? " (+" + (auths.size() - shown) + ")" : "");

        list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                context.getString(R.string.triggers_cat_provider), detail,
                context.getString(R.string.triggers_provider_explanation),
                TriggerInfo.Severity.LOW));
        return list;
    }

    List<TriggerInfo> analyzeSyncAdapters(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();


        String output = shellManager.runShellCommandAndGetFullOutput("dumpsys content");
        if (output == null || output.trim().isEmpty()) {

            try {
                String pkgOut = shellManager.runShellCommandAndGetFullOutput(
                        "dumpsys package " + packageName);
                if (pkgOut != null && pkgOut.toLowerCase().contains("syncadapter")) {
                    list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                            context.getString(R.string.triggers_cat_sync),
                            context.getString(R.string.triggers_sync_detail, 1),
                            context.getString(R.string.triggers_sync_explanation),
                            TriggerInfo.Severity.MEDIUM));
                }
            } catch (Exception e) { Log.w(TAG, "sync/package fallback failed: " + e.getMessage()); }
            return list;
        }


        int count = 0;
        List<String> entries = new ArrayList<>();

        Pattern authPat      = Pattern.compile("authority=([\\w.]+)");
        Pattern acctPat      = Pattern.compile("accountType=([\\w.]+)");
        Pattern periodPat    = Pattern.compile("period=(\\d+)s");
        Pattern periodMsPat  = Pattern.compile("(?:mPeriod|periodMs)=(\\d+)");
        Pattern lastSuccPat  = Pattern.compile("lastSuccessTime=([\\d\\- :]+)");
        Pattern nextRunPat   = Pattern.compile("nextRunTime=([\\d\\- :]+)");
        Pattern syncablePat  = Pattern.compile("(?:syncable|mSyncable)=(true|false)");

        boolean inBlock   = false;
        String  authority = null;
        String  acctType  = null;
        boolean syncable  = false;
        long    periodSec = 0;
        String  lastSucc  = null;
        String  nextRun   = null;

        for (String line : output.split("\n")) {
            String t = line.trim();


            boolean isHeader = t.startsWith("SyncAdapterType") || t.startsWith("SyncAdapter:");
            if (isHeader) {

                if (inBlock && authority != null) {
                    count++;
                    if (entries.size() < 3) entries.add(
                            buildSyncEntry(authority, acctType, syncable, periodSec, lastSucc, nextRun));
                }

                inBlock   = t.contains(packageName);
                authority = null; acctType = null; syncable = false;
                periodSec = 0; lastSucc = null; nextRun = null;

                if (inBlock) {
                    Matcher mA = authPat.matcher(t);
                    if (mA.find()) authority = mA.group(1);
                    Matcher mAc = acctPat.matcher(t);
                    if (mAc.find()) acctType = mAc.group(1);
                }
                continue;
            }

            if (!inBlock) {

                if (t.contains(packageName) && t.contains("authority=")) {
                    inBlock = true;
                    Matcher mA = authPat.matcher(t);
                    if (mA.find()) authority = mA.group(1);
                    Matcher mAc = acctPat.matcher(t);
                    if (mAc.find()) acctType = mAc.group(1);
                }
                continue;
            }


            Matcher mSy = syncablePat.matcher(t);
            if (mSy.find()) syncable = "true".equals(mSy.group(1));

            Matcher mP = periodPat.matcher(t);
            if (mP.find() && periodSec == 0) periodSec = Long.parseLong(mP.group(1));
            else {
                Matcher mPms = periodMsPat.matcher(t);
                if (mPms.find() && periodSec == 0) periodSec = Long.parseLong(mPms.group(1)) / 1000;
            }

            Matcher mLs = lastSuccPat.matcher(t);
            if (mLs.find() && lastSucc == null) lastSucc = mLs.group(1).trim();

            Matcher mNr = nextRunPat.matcher(t);
            if (mNr.find() && nextRun == null) nextRun = mNr.group(1).trim();


            if (t.isEmpty() && authority != null) {
                count++;
                if (entries.size() < 3) entries.add(
                        buildSyncEntry(authority, acctType, syncable, periodSec, lastSucc, nextRun));
                inBlock = false; authority = null; acctType = null;
                syncable = false; periodSec = 0; lastSucc = null; nextRun = null;
            }
        }

        if (inBlock && authority != null) {
            count++;
            if (entries.size() < 3) entries.add(
                    buildSyncEntry(authority, acctType, syncable, periodSec, lastSucc, nextRun));
        }

        if (count == 0) return list;

        StringBuilder detail = new StringBuilder(
                context.getString(R.string.triggers_sync_detail, count));
        if (!entries.isEmpty())
            detail.append(": ").append(String.join(" | ", entries));

        list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                context.getString(R.string.triggers_cat_sync),
                detail.toString(),
                context.getString(R.string.triggers_sync_explanation),
                TriggerInfo.Severity.MEDIUM));
        return list;
    }


    String buildSyncEntry(String authority, String acctType,
            boolean syncable, long periodSec, String lastSucc, String nextRun) {
        StringBuilder sb = new StringBuilder();

        String auth = authority.contains(".")
                ? authority.substring(authority.lastIndexOf('.') + 1) : authority;
        sb.append(auth);
        if (!syncable) sb.append("(off)");
        if (periodSec > 0) sb.append(" every ").append(analyzer.formatInterval(periodSec * 1000L));
        if (lastSucc != null) {

            String t = lastSucc.contains(" ") ? lastSucc.substring(lastSucc.indexOf(' ') + 1) : lastSucc;
            if (t.length() > 5) t = t.substring(0, 5);
            sb.append(" last:").append(t);
        }
        return sb.toString();
    }

    List<TriggerInfo> analyzeDozeExemption(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = shellManager.runShellCommandAndGetFullOutput(
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
                    context.getString(R.string.triggers_cat_doze),
                    context.getString(sys ? R.string.triggers_doze_sys_detail
                                         : R.string.triggers_doze_user_detail),
                    context.getString(sys ? R.string.triggers_doze_sys_explanation
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

    private List<TriggerInfo> analyzeDozeExemptionFallback(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        try {
            String ops = shellManager.runShellCommandAndGetFullOutput(
                    "cmd appops get " + packageName + " RUN_ANY_IN_BACKGROUND");
            if (ops != null && ops.contains("allow")) {
                list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                        context.getString(R.string.triggers_cat_doze),
                        context.getString(R.string.triggers_doze_battery_opt_detail),                        
                        context.getString(R.string.triggers_doze_battery_opt_explanation),                        
                        TriggerInfo.Severity.HIGH));
            }
        } catch (Exception e) {
            Log.w(TAG, "doze exemption appops fallback failed: " + e.getMessage());
        }

        try {
            String battery = shellManager.runShellCommandAndGetFullOutput(
                    "dumpsys battery | grep -i " + packageName);
            if (battery != null && !battery.trim().isEmpty()
                    && battery.toLowerCase().contains("exempt")) {
                list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                        context.getString(R.string.triggers_cat_doze),
                        context.getString(R.string.triggers_doze_battery_exempt_detail),                        
                        context.getString(R.string.triggers_doze_battery_exempt_explanation),                        
                        TriggerInfo.Severity.HIGH));
            }
        } catch (Exception e) {
            Log.w(TAG, "doze exemption battery fallback failed: " + e.getMessage());
        }

        return list;
    }


    List<TriggerInfo> analyzeStandbyBucket(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = shellManager.runShellCommandAndGetFullOutput(
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


        List<String> history = new ArrayList<>();
        try {
            String usOut = shellManager.runShellCommandAndGetFullOutput("dumpsys usagestats");
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
                        Log.w(TAG, "standby bucket history parse failed: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) { Log.w(TAG, "usagestats failed: " + e.getMessage()); }

        if (history.size() > 4) history = history.subList(history.size()-4, history.size());

        if (history.isEmpty() || !history.get(history.size()-1).startsWith(currentName))
            history.add(currentName);

        String detail = currentName;
        if (history.size() > 1)
            detail += context.getString(R.string.triggers_bucket_history,
                    String.join(" → ", history));

        TriggerInfo.Severity sev;
        String expl;
        if      (bv <= 10) { sev=TriggerInfo.Severity.HIGH;   expl=context.getString(R.string.triggers_bucket_active_explanation); }
        else if (bv <= 20) { sev=TriggerInfo.Severity.MEDIUM; expl=context.getString(R.string.triggers_bucket_working_set_explanation); }
        else if (bv <= 30) { sev=TriggerInfo.Severity.LOW;    expl=context.getString(R.string.triggers_bucket_frequent_explanation); }
        else if (bv <= 40) { sev=TriggerInfo.Severity.INFO;   expl=context.getString(R.string.triggers_bucket_rare_explanation); }
        else if (bv <= 45) {
            sev  = TriggerInfo.Severity.HIGH;
            expl = context.getString(R.string.triggers_bucket_restricted_explanation);
        }
        else {
            sev  = TriggerInfo.Severity.HIGH;
            expl = context.getString(R.string.triggers_bucket_never_explanation);
        }

        list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                context.getString(R.string.triggers_cat_bucket), detail, expl, sev));

        if (bv > 40
                && analyzer.apiLevel >= android.os.Build.VERSION_CODES.S
                && analyzer.apiLevel <= android.os.Build.VERSION_CODES.TIRAMISU) {
            list.addAll(analyzeRestrictedBucketEffects(packageName, bv));
        }

        return list;
    }

    private String getStandbyBucketFallback(String packageName) {
        try {
            String us = shellManager.runShellCommandAndGetFullOutput(
                    "dumpsys usagestats | grep -A3 \"" + packageName + "\"");
            if (us != null) {
                Matcher m = Pattern.compile("standbyBucket=(\\d+)").matcher(us);
                if (m.find()) return m.group(1);
                Matcher m2 = Pattern.compile("bucket=(\\d+)").matcher(us);
                if (m2.find()) return m2.group(1);
            }
        } catch (Exception e) { Log.w(TAG, "standby fallback/usagestats: " + e.getMessage()); }

        try {
            String ops = shellManager.runShellCommandAndGetFullOutput(
                    "cmd appops get " + packageName + " RUN_ANY_IN_BACKGROUND");
            if (ops != null) {
                if (ops.contains("ignore") || ops.contains("deny"))
                    return "45";
                if (ops.contains("allow"))
                    return "5";
            }
        } catch (Exception e) { Log.w(TAG, "standby fallback/appops: " + e.getMessage()); }

        return null;
    }

    List<TriggerInfo> analyzeBatteryStats(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = shellManager.runShellCommandAndGetFullOutput(
                "dumpsys batterystats " + packageName);
        if (output == null || output.trim().isEmpty()) return list;

        long wlMs=0; int wlCnt=0, alarms=0, jobs=0, syncs=0;
        double powerMah = -1;

        Pattern wp   = Pattern.compile("Wakelock\\s+\\S+:\\s+(\\d+)ms realtime.*?\\((\\d+)\\s+times\\)", Pattern.CASE_INSENSITIVE);
        Pattern ap   = Pattern.compile("Wakeup alarm.*?:\\s*(\\d+)\\s+times",                            Pattern.CASE_INSENSITIVE);
        Pattern jp   = Pattern.compile("Job\\s+\\S+:\\s+\\d+ms realtime.*?\\((\\d+)\\s+times\\)",        Pattern.CASE_INSENSITIVE);
        Pattern sp   = Pattern.compile("Sync\\s+\\S+:\\s+\\d+ms realtime.*?\\((\\d+)\\s+times\\)",       Pattern.CASE_INSENSITIVE);

        Pattern pwrP = Pattern.compile("Uid\\s+u0a\\d+:\\s*([\\d.]+)(?:\\s*mAh)?", Pattern.CASE_INSENSITIVE);

        boolean inPowerSection = false;
        String  uid = analyzer.cachedUid;

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
            } catch (Exception e) { Log.w(TAG, "analyzeBatteryStats line parse failed: " + e.getMessage()); }
        }
        if (wlCnt==0&&alarms==0&&jobs==0&&syncs==0&&powerMah<0) return list;

        StringBuilder detail = new StringBuilder();
        if (powerMah >= 0)
            detail.append(String.format("%.2f mAh", powerMah));
        if (wlCnt  > 0) { if(detail.length()>0) detail.append(", ");
            detail.append(context.getString(R.string.triggers_batterystats_wakelock, wlCnt, analyzer.formatDuration(wlMs))); }
        if (alarms > 0) { if(detail.length()>0) detail.append(", ");
            detail.append(context.getString(R.string.triggers_batterystats_alarms, alarms)); }
        if (jobs   > 0) { if(detail.length()>0) detail.append(", ");
            detail.append(context.getString(R.string.triggers_batterystats_jobs,   jobs)); }
        if (syncs  > 0) { if(detail.length()>0) detail.append(", ");
            detail.append(context.getString(R.string.triggers_batterystats_syncs,  syncs)); }

        TriggerInfo.Severity sev = alarms>50||wlMs>600_000||(powerMah>50) ? TriggerInfo.Severity.HIGH
                : alarms>10||wlMs>60_000||jobs>20||(powerMah>10) ? TriggerInfo.Severity.MEDIUM : TriggerInfo.Severity.LOW;

        list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                context.getString(R.string.triggers_cat_batterystats),
                detail.toString(),
                context.getString(R.string.triggers_batterystats_explanation), sev));
        return list;
    }


    List<TriggerInfo> analyzeBroadcastEfficiency(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = getBroadcastHistory();
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
                context.getString(R.string.triggers_bcast_eff_total, delivered));
        if (lastHour > 0) detail.append(context.getString(R.string.triggers_bcast_eff_hour, lastHour));
        if (lastDay  > 0 && lastDay != lastHour)
            detail.append(context.getString(R.string.triggers_bcast_eff_day, lastDay));
        if (launched > 0)
            detail.append(context.getString(R.string.triggers_bcast_eff_launched, launched, pct));

        TriggerInfo.Severity sev = launched>10||(pct>50&&delivered>5) ? TriggerInfo.Severity.HIGH
                : launched>3||lastHour>20 ? TriggerInfo.Severity.MEDIUM : TriggerInfo.Severity.LOW;

        list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                context.getString(R.string.triggers_cat_bcast_eff),
                detail.toString(),
                context.getString(R.string.triggers_bcast_eff_explanation), sev));
        return list;
    }


    List<TriggerInfo> analyzeContentObservers(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        try {
            String output = shellManager.runShellCommandAndGetFullOutput("dumpsys content");
            if (output == null || output.trim().isEmpty()) return list;

            boolean inObservers = false;
            List<String> uris   = new ArrayList<>();
            int total = 0;

            Pattern uriPat = Pattern.compile("uri=([^\\s,]+)");
            Pattern pkgPat = Pattern.compile("package=([\\w.]+)");

            for (String line : output.split("\n")) {
                String t = line.trim();
                if (t.startsWith("Observers:") || t.startsWith("Content observers:"))
                    { inObservers = true; continue; }
                if (inObservers && t.startsWith("---")) { inObservers = false; continue; }
                if (!inObservers) continue;


                boolean hasPkg = t.contains(packageName);
                if (!hasPkg) {
                    Matcher mPkg = pkgPat.matcher(t);
                    hasPkg = mPkg.find() && mPkg.group(1).equals(packageName);
                }
                if (!hasPkg) continue;

                total++;
                Matcher mUri = uriPat.matcher(t);
                if (mUri.find()) {
                    String uri = mUri.group(1);

                    uri = uri.replace("content://", "");
                    if (uri.length() > 40) uri = uri.substring(0, 40) + "…";
                    if (!uris.contains(uri) && uris.size() < 4) uris.add(uri);
                }
            }

            if (total == 0) return list;

            StringBuilder detail = new StringBuilder(
                    context.getString(R.string.triggers_content_obs_count, total));
            if (!uris.isEmpty())
                detail.append(": ").append(String.join(", ", uris));
            if (total > uris.size())
                detail.append(context.getString(
                        R.string.triggers_content_obs_overflow, total - uris.size()));

            list.add(new TriggerInfo(TriggerInfo.Group.CAN_WAKE,
                    context.getString(R.string.triggers_cat_content_obs),
                    detail.toString(),
                    context.getString(R.string.triggers_content_obs_explanation),
                    total > 5 ? TriggerInfo.Severity.HIGH : TriggerInfo.Severity.MEDIUM));

        } catch (Exception e) { Log.w(TAG, "analyzeContentObservers failed: " + e.getMessage()); }
        return list;
    }


    List<TriggerInfo> analyzeFcmRegistration(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        try {
            String pkgOut = shellManager.runShellCommandAndGetFullOutput(
                    "dumpsys package " + packageName);
            if (pkgOut == null) return list;

            boolean hasFirebase = false;
            boolean hasFcmService = false;
            boolean hasDataMsg = false;

            for (String line : pkgOut.split("\n")) {
                String t = line.toLowerCase();
                if (t.contains("firebase") || t.contains("fcm") || t.contains("iid"))
                    hasFirebase = true;
                if (t.contains("firebasemessagingservice")
                        || t.contains("com.google.firebase.messaging"))
                    hasFcmService = true;
                if (t.contains("com.google.android.c2dm.intent.receive")
                        || t.contains("com.google.firebase.messaging.intent.action"))
                    hasDataMsg = true;
            }

            if (!hasFirebase && !hasFcmService && !hasDataMsg) return list;

            String detail;
            if (hasFcmService) detail = context.getString(R.string.triggers_fcm_service_detail);
            else if (hasDataMsg) detail = context.getString(R.string.triggers_fcm_receiver_detail);
            else detail = context.getString(R.string.triggers_fcm_generic_detail);

            list.add(new TriggerInfo(TriggerInfo.Group.CAN_WAKE,
                    context.getString(R.string.triggers_cat_fcm),
                    detail,
                    context.getString(R.string.triggers_fcm_explanation),
                    TriggerInfo.Severity.MEDIUM));

        } catch (Exception e) { Log.w(TAG, "analyzeFcmRegistration failed: " + e.getMessage()); }
        return list;
    }


    List<TriggerInfo> analyzeMultipleProcesses(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        try {

            String psOut = shellManager.runShellCommandAndGetFullOutput(
                    "ps -A -o pid,name 2>/dev/null | grep " + packageName);
            if (psOut == null || psOut.trim().isEmpty()) {

                psOut = shellManager.runShellCommandAndGetFullOutput(
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


            List<String> subNames = new ArrayList<>();
            for (String n : processNames) {
                if (n.equals(packageName)) subNames.add(0, "main");
                else if (n.startsWith(packageName + ":"))
                    subNames.add(n.substring(packageName.length()));
                else
                    subNames.add(n);
            }

            StringBuilder detail = new StringBuilder(
                    context.getString(R.string.triggers_multiproc_count, count));
            detail.append(": ").append(String.join(", ", subNames));

            list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                    context.getString(R.string.triggers_cat_multiproc),
                    detail.toString(),
                    context.getString(R.string.triggers_multiproc_explanation),
                    count > 3 ? TriggerInfo.Severity.HIGH : TriggerInfo.Severity.MEDIUM));

        } catch (Exception e) { Log.w(TAG, "analyzeMultipleProcesses failed: " + e.getMessage()); }
        return list;
    }


    List<TriggerInfo> analyzeAccessibilityAndIme(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();


        try {
            String a11yOut = shellManager.runShellCommandAndGetFullOutput(
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
                            context.getString(R.string.triggers_cat_accessibility),
                            svcName != null ? svcName
                                    : context.getString(R.string.triggers_a11y_detail_generic),
                            context.getString(R.string.triggers_a11y_explanation),
                            TriggerInfo.Severity.HIGH));
                }
            }
        } catch (Exception e) { Log.w(TAG, "analyzeAccessibility failed: " + e.getMessage()); }


        try {
            String imeOut = shellManager.runShellCommandAndGetFullOutput("dumpsys input_method");
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
                            context.getString(R.string.triggers_cat_ime),
                            imeName != null ? imeName
                                    : context.getString(R.string.triggers_ime_detail_generic),
                            context.getString(R.string.triggers_ime_explanation),
                            TriggerInfo.Severity.HIGH));
                }
            }
        } catch (Exception e) { Log.w(TAG, "analyzeIme failed: " + e.getMessage()); }

        return list;
    }


    List<TriggerInfo> analyzeDeviceAdmin(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        try {
            String dpOut = shellManager.runShellCommandAndGetFullOutput("dumpsys device_policy");
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
                detail = context.getString(ownerType.equals("device")
                        ? R.string.triggers_device_admin_owner_device
                        : R.string.triggers_device_admin_owner_profile);
                expl   = context.getString(R.string.triggers_device_admin_owner_explanation);
                sev    = TriggerInfo.Severity.HIGH;
            } else {
                detail = context.getString(R.string.triggers_device_admin_active);
                expl   = context.getString(R.string.triggers_device_admin_explanation);
                sev    = TriggerInfo.Severity.MEDIUM;
            }

            list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                    context.getString(R.string.triggers_cat_device_admin),
                    detail, expl, sev));

        } catch (Exception e) { Log.w(TAG, "analyzeDeviceAdmin failed: " + e.getMessage()); }
        return list;
    }


    List<TriggerInfo> analyzeAppOps(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        try {


            String out = shellManager.runShellCommandAndGetFullOutput(
                    "appops get " + packageName);
            if (out == null || out.trim().isEmpty() || out.contains("Failed transaction")) {
                out = shellManager.runShellCommandAndGetFullOutput(
                        "cmd appops get " + packageName);
            }
            if (out == null || out.trim().isEmpty() || out.contains("Failed transaction")) {
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
                              .append(context.getString(R.string.triggers_appops_last_used, timeStr));
                    if (!"allow".equals(mode))
                        detail.append(" [").append(mode).append("]");

                    list.add(new TriggerInfo(desc.group,
                            context.getString(R.string.triggers_cat_appops),
                            detail.toString(),
                            desc.explanation,
                            desc.severity));
                } catch (Exception e) {
                    Log.w(TAG, "analyzeAppOps line parse failed: " + e.getMessage());
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

        } catch (Exception e) { Log.w(TAG, "analyzeAppOps failed: " + e.getMessage()); }
        return list;
    }


    private List<TriggerInfo> analyzeExactAlarmPermissions(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        try {
            String pkgOut = shellManager.runShellCommandAndGetFullOutput(
                    "dumpsys package " + packageName
                    + " | grep -E 'SCHEDULE_EXACT_ALARM|USE_EXACT_ALARM'");
            if (pkgOut == null) return list;

            boolean hasSchedule = pkgOut.contains("SCHEDULE_EXACT_ALARM") && pkgOut.contains("granted=true");
            boolean hasUse      = pkgOut.contains("USE_EXACT_ALARM")      && pkgOut.contains("granted=true");

            if (hasSchedule || hasUse) {
                String permLabel = hasUse ? "USE_EXACT_ALARM" : "SCHEDULE_EXACT_ALARM";
                list.add(new TriggerInfo(TriggerInfo.Group.CAN_WAKE,
                        context.getString(R.string.triggers_cat_alarms),
                        context.getString(R.string.trigger_exact_alarm_label, permLabel),
                        context.getString(hasUse
                                ? R.string.trigger_exact_alarm_desc_use
                                : R.string.trigger_exact_alarm_desc_schedule),
                        hasUse ? TriggerInfo.Severity.HIGH : TriggerInfo.Severity.MEDIUM));
            }
        } catch (Exception e) { Log.w(TAG, "exact alarm perm check failed: " + e.getMessage()); }
        return list;
    }

    private List<TriggerInfo> analyzeRestrictedBucketEffects(String packageName, int bucketValue) {
        List<TriggerInfo> list = new ArrayList<>();
        try {
            String ops = shellManager.runShellCommandAndGetFullOutput(
                    "cmd appops get " + packageName + " RUN_ANY_IN_BACKGROUND");
            if (ops == null) return list;

            boolean isDenied = ops.contains("ignore") || ops.contains("deny");
            if (!isDenied) return list;

            String jobState = shellManager.runShellCommandAndGetFullOutput(
                    "cmd jobscheduler get-job-state " + packageName);
            boolean jobsBlocked = jobState != null
                    && (jobState.contains("QUOTA") || jobState.contains("RESTRICTED"));

            list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                    context.getString(R.string.triggers_cat_bucket),
                    "RESTRICTED confirmed · RUN_ANY_IN_BACKGROUND=deny"
                            + (jobsBlocked ? " · jobs blocked" : ""),
                    context.getString(R.string.triggers_restricted_confirmed_explanation),                    
                    TriggerInfo.Severity.HIGH));
        } catch (Exception e) { Log.w(TAG, "restricted bucket effects check failed: " + e.getMessage()); }
        return list;
    }

    private List<TriggerInfo> analyzeDozeStateFallback(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        try {
            String idleOut = shellManager.runShellCommandAndGetFullOutput(
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
                        context.getString(R.string.triggers_cat_doze),
                        stateLabel + " · " + stateVal,
                        context.getString(R.string.triggers_doze_state_prefix, stateLabel) + 
                        context.getString(R.string.triggers_doze_state_suffix),
                        TriggerInfo.Severity.INFO));
            }
        } catch (Exception e) { Log.w(TAG, "doze state fallback failed: " + e.getMessage()); }
        return list;
    }

    private List<TriggerInfo> analyzeRestrictedOps(String packageName, String appOpsOut) {
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
                        context.getString(R.string.triggers_cat_appops),
                        detail,
                        context.getString(R.string.triggers_appops_activity_recognition_explanation),
                        TriggerInfo.Severity.MEDIUM));
            }
            if (hasManageMedia
                    && analyzer.apiLevel >= android.os.Build.VERSION_CODES.S) {
                list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                        context.getString(R.string.triggers_cat_appops),
                        context.getString(R.string.trigger_manage_media_label),
                        context.getString(R.string.trigger_manage_media_desc),
                        TriggerInfo.Severity.LOW));
            }
        } catch (Exception e) { Log.w(TAG, "analyzeRestrictedOps failed: " + e.getMessage()); }
        return list;
    }


    private String parseDumpsysAppOpsForPackage(String packageName) {
        try {
            String full = shellManager.runShellCommandAndGetFullOutput("dumpsys appops");
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
            Log.w(TAG, "parseDumpsysAppOpsForPackage failed: " + e.getMessage());
            return null;
        }
    }

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


    private OpDescriptor appOpDescriptor(String op) {
        switch (op) {
            case "WAKE_LOCK":
                return new OpDescriptor("WAKE_LOCK",
                        context.getString(R.string.triggers_appops_wakelock_explanation),
                        TriggerInfo.Group.ACTIVE_NOW, TriggerInfo.Severity.HIGH);
            case "RUN_IN_BACKGROUND":
                return new OpDescriptor("RUN_IN_BACKGROUND",
                        context.getString(R.string.triggers_appops_run_bg_explanation),
                        TriggerInfo.Group.CAN_WAKE, TriggerInfo.Severity.MEDIUM);
            case "RUN_ANY_IN_BACKGROUND":
                return new OpDescriptor("RUN_ANY_IN_BACKGROUND",
                        context.getString(R.string.triggers_appops_run_any_bg_explanation),
                        TriggerInfo.Group.CAN_WAKE, TriggerInfo.Severity.HIGH);
            case "SCHEDULE_EXACT_ALARM":
                return new OpDescriptor("Exact Alarm",
                        context.getString(R.string.triggers_appops_schedule_exact_alarm_explanation),                        
                        TriggerInfo.Group.CAN_WAKE, TriggerInfo.Severity.HIGH);
            case "USE_EXACT_ALARM":
                return new OpDescriptor("USE_EXACT_ALARM",
                        context.getString(R.string.triggers_appops_exact_alarm_explanation),
                        TriggerInfo.Group.CAN_WAKE, TriggerInfo.Severity.HIGH);
            case "USE_FULL_SCREEN_INTENT":
                return new OpDescriptor("Full-Screen Intent",
                        context.getString(R.string.triggers_appops_full_screen_intent_explanation),                       
                        TriggerInfo.Group.CAN_WAKE, TriggerInfo.Severity.MEDIUM);
            case "MANAGE_MEDIA":
                return new OpDescriptor("Manage Media",
                        context.getString(R.string.triggers_appops_manage_media_explanation),                        
                        TriggerInfo.Group.OTHER, TriggerInfo.Severity.LOW);
            case "RUN_USER_INITIATED_JOBS":
                return new OpDescriptor("User-Initiated Jobs",
                        context.getString(R.string.triggers_appops_user_initiated_jobs_explanation),                        
                        TriggerInfo.Group.CAN_WAKE, TriggerInfo.Severity.MEDIUM);
            case "START_FOREGROUND":
                return new OpDescriptor("Start FGS (blocked)",
                        context.getString(R.string.triggers_appops_start_fgs_blocked_explanation),                        
                        TriggerInfo.Group.OTHER, TriggerInfo.Severity.HIGH);
            case "RECEIVE_EXPLICIT_USER_INTERACTION":
                return new OpDescriptor("USER_INTERACTION",
                        context.getString(R.string.triggers_appops_user_interaction_explanation),
                        TriggerInfo.Group.CAN_WAKE, TriggerInfo.Severity.MEDIUM);
            case "ACTIVITY_RECOGNITION":
                return new OpDescriptor("ACTIVITY_RECOGNITION",
                        context.getString(R.string.triggers_appops_activity_recognition_explanation),
                        TriggerInfo.Group.ACTIVE_NOW, TriggerInfo.Severity.MEDIUM);
            default:
                return null;
        }
    }


    List<TriggerInfo> analyzeUsageStats(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        try {
            String out = shellManager.runShellCommandAndGetFullOutput(
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
                    Log.w(TAG, "analyzeUsageStats parse failed: " + e.getMessage());
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
                    context.getString(R.string.triggers_usagestats_last_used,
                            analyzer.formatDuration(sinceUsed)));
            if (sinceFg > 0)
                detail.append(" · ")
                      .append(context.getString(R.string.triggers_usagestats_last_fg,
                              analyzer.formatDuration(sinceFg)));
            if (totalFgMs > 0)
                detail.append(" · ")
                      .append(context.getString(R.string.triggers_usagestats_total_fg,
                              analyzer.formatDuration(totalFgMs)));

            list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                    context.getString(R.string.triggers_cat_usagestats),
                    detail.toString(),
                    context.getString(R.string.triggers_usagestats_explanation),
                    sinceUsed < 60_000 ? TriggerInfo.Severity.HIGH : TriggerInfo.Severity.MEDIUM));

        } catch (Exception e) { Log.w(TAG, "analyzeUsageStats failed: " + e.getMessage()); }
        return list;
    }


}
