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

public class ComponentsAnalyzer {

    private static final String FILE_NAME = "ComponentsAnalyzer";

    private final AppTriggersAnalyzer analyzer;
    private String cachedBroadcastHistory = null;

    public ComponentsAnalyzer(AppTriggersAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

// ---- getBroadcastHistory ----
    public String getBroadcastHistory() {
        if (cachedBroadcastHistory == null) {
            AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": getBroadcastHistory: fetching from shell");
            cachedBroadcastHistory = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                    "dumpsys activity broadcasts history");
            if (cachedBroadcastHistory == null) cachedBroadcastHistory = "";
            AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": getBroadcastHistory: fetched len=" + cachedBroadcastHistory.length());
        }
        return cachedBroadcastHistory.isEmpty() ? null : cachedBroadcastHistory;
    }

// ---- analyzeBroadcastReceivers ----
    public List<TriggerInfo> analyzeBroadcastReceivers(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();


        String pkgOut = analyzer.getShellManager().runShellCommandAndGetFullOutput(
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
            String regOut = analyzer.getShellManager().runShellCommandAndGetFullOutput(
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
        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": dynamic receivers failed", e); }

        if (staticActions.isEmpty() && dynamicActions.isEmpty()) return list;


        if (!staticActions.isEmpty()) {
            int shown = Math.min(staticActions.size(), 5);
            StringBuilder detail = new StringBuilder(
                    String.join(", ", staticActions.subList(0, shown)));
            if (staticActions.size() > shown)
                detail.append(analyzer.getContext().getString(
                        R.string.triggers_receivers_detail_overflow, staticActions.size() - shown));

            StringBuilder expl = new StringBuilder(
                    analyzer.getContext().getString(R.string.triggers_receivers_explanation_base));
            if (staticActions.stream().anyMatch(a -> a.contains("BOOT") || a.contains("LOCKED_BOOT")))
                expl.append(analyzer.getContext().getString(R.string.triggers_receivers_explanation_boot));
            if (staticActions.stream().anyMatch(a -> a.contains("CONNECTIVITY") || a.contains("NETWORK")))
                expl.append(analyzer.getContext().getString(R.string.triggers_receivers_explanation_network));

            list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                    AppTriggersAnalyzer.KEY_CAT_RECEIVERS,
                    analyzer.getContext().getString(R.string.triggers_cat_receivers, staticActions.size()),
                    detail.toString(), expl.toString(), TriggerInfo.Severity.MEDIUM));
        }


        if (!dynamicActions.isEmpty()) {
            int shown = Math.min(dynamicActions.size(), 5);
            StringBuilder detail = new StringBuilder(
                    String.join(", ", dynamicActions.subList(0, shown)));
            if (dynamicActions.size() > shown)
                detail.append(analyzer.getContext().getString(
                        R.string.triggers_receivers_detail_overflow, dynamicActions.size() - shown));
            if (exportedDynamicReceivers > 0)
                detail.append(" · exported=").append(exportedDynamicReceivers);

            list.add(new TriggerInfo(TriggerInfo.Group.CAN_WAKE,
                    AppTriggersAnalyzer.KEY_CAT_RECEIVERS,
                    analyzer.getContext().getString(R.string.triggers_cat_receivers_dynamic, dynamicActions.size()),
                    detail.toString(),
                    analyzer.getContext().getString(R.string.triggers_receivers_explanation_base),
                    dynamicActions.size() > 3 ? TriggerInfo.Severity.HIGH : TriggerInfo.Severity.MEDIUM));
        }

        if (exportedDynamicReceivers > 0
                && analyzer.apiLevel == android.os.Build.VERSION_CODES.TIRAMISU) {
            list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                    AppTriggersAnalyzer.KEY_CAT_RECEIVERS,
                    analyzer.getContext().getString(R.string.triggers_cat_receivers_dynamic, exportedDynamicReceivers),
                    analyzer.getContext().getString(R.string.triggers_receiver_exported_detail),
                    analyzer.getContext().getString(R.string.triggers_receiver_exported_explanation),                    
                    TriggerInfo.Severity.MEDIUM));
        }

        return list;
    }

// ---- analyzeBootReceivers ----
    public List<TriggerInfo> analyzeBootReceivers(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        boolean hasBoot = false, hasLocked = false;

        try {
            String o1 = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                    "cmd package query-receivers -a android.intent.action.BOOT_COMPLETED");
            if (o1 != null && !o1.contains("Unknown option") && o1.contains(packageName))
                hasBoot = true;
        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": boot query-receivers failed", e); }
        try {
            String o2 = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                    "cmd package query-receivers -a android.intent.action.LOCKED_BOOT_COMPLETED");
            if (o2 != null && !o2.contains("Unknown option") && o2.contains(packageName))
                hasLocked = true;
        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": locked-boot query-receivers failed", e); }

        if (!hasBoot && !hasLocked) {
            try {
                String pkgOut = analyzer.getShellManager().runShellCommandAndGetFullOutput(
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
            } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": boot/package fallback failed", e); }
        }

        if (!hasBoot && !hasLocked) return list;

        String detail = hasBoot && hasLocked ? analyzer.getContext().getString(R.string.triggers_boot_detail_both)
                : hasLocked ? analyzer.getContext().getString(R.string.triggers_boot_detail_locked)
                            : analyzer.getContext().getString(R.string.triggers_boot_detail_normal);
        String expl = hasBoot && hasLocked ? analyzer.getContext().getString(R.string.triggers_boot_explanation_both)
                : hasLocked ? analyzer.getContext().getString(R.string.triggers_boot_explanation_locked)
                            : analyzer.getContext().getString(R.string.triggers_boot_explanation_normal);

        list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                AppTriggersAnalyzer.KEY_CAT_BOOT,
                analyzer.getContext().getString(R.string.triggers_cat_boot), detail, expl,
                TriggerInfo.Severity.HIGH));

        if (analyzer.apiLevel >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            list.addAll(analyzeBootFgsRestriction(packageName));
        }

        return list;
    }

// ---- analyzeBootFgsRestriction ----
    public List<TriggerInfo> analyzeBootFgsRestriction(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        try {
            String bh = getBroadcastHistory();
            if (bh != null && bh.contains(packageName)
                    && bh.contains("FGS_BOOT_COMPLETED_RESTRICTIONS")) {
                list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                        AppTriggersAnalyzer.KEY_CAT_BOOT_BLOCKED,
                        analyzer.getContext().getString(R.string.triggers_cat_boot),
                        analyzer.getContext().getString(R.string.triggers_fgs_boot_blocked_detail),                        
                        analyzer.getContext().getString(R.string.triggers_fgs_boot_blocked_explanation),                        
                        TriggerInfo.Severity.HIGH));
            }

            String logcat = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                    "logcat -d -t 100 -s ActivityManager:E | grep " + packageName);
            if (logcat != null
                    && logcat.contains("ForegroundServiceStartNotAllowedException")
                    && logcat.contains(packageName)) {
                list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                        AppTriggersAnalyzer.KEY_CAT_BOOT_BLOCKED,
                        analyzer.getContext().getString(R.string.triggers_cat_boot),
                        analyzer.getContext().getString(R.string.triggers_fgs_boot_exception_detail),
                        analyzer.getContext().getString(R.string.triggers_fgs_boot_exception_explanation),                        
                        TriggerInfo.Severity.HIGH));
            }
        } catch (Exception e) {
            AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": bootFgsRestriction fallback failed", e);
        }
        return list;
    }

// ---- analyzeContentProviders ----
    public List<TriggerInfo> analyzeContentProviders(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = analyzer.getShellManager().runShellCommandAndGetFullOutput(
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
                AppTriggersAnalyzer.KEY_CAT_PROVIDER,
                analyzer.getContext().getString(R.string.triggers_cat_provider), detail,
                analyzer.getContext().getString(R.string.triggers_provider_explanation),
                TriggerInfo.Severity.LOW));
        return list;
    }

// ---- analyzeContentObservers ----
    public List<TriggerInfo> analyzeContentObservers(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        try {
            String output = analyzer.getShellManager().runShellCommandAndGetFullOutput("dumpsys content");
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
                    analyzer.getContext().getString(R.string.triggers_content_obs_count, total));
            if (!uris.isEmpty())
                detail.append(": ").append(String.join(", ", uris));
            if (total > uris.size())
                detail.append(analyzer.getContext().getString(
                        R.string.triggers_content_obs_overflow, total - uris.size()));

            list.add(new TriggerInfo(TriggerInfo.Group.CAN_WAKE,
                    AppTriggersAnalyzer.KEY_CAT_CONTENT_OBS,
                    analyzer.getContext().getString(R.string.triggers_cat_content_obs),
                    detail.toString(),
                    analyzer.getContext().getString(R.string.triggers_content_obs_explanation),
                    total > 5 ? TriggerInfo.Severity.HIGH : TriggerInfo.Severity.MEDIUM));

        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": analyzeContentObservers failed", e); }
        return list;
    }

// ---- analyzeSyncAdapters ----
    public List<TriggerInfo> analyzeSyncAdapters(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();


        String output = analyzer.getShellManager().runShellCommandAndGetFullOutput("dumpsys content");
        if (output == null || output.trim().isEmpty()) {

            try {
                String pkgOut = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                        "dumpsys package " + packageName);
                if (pkgOut != null && pkgOut.toLowerCase().contains("syncadapter")) {
                    list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                            AppTriggersAnalyzer.KEY_CAT_SYNC,
                            analyzer.getContext().getString(R.string.triggers_cat_sync),
                            analyzer.getContext().getString(R.string.triggers_sync_detail, 1),
                            analyzer.getContext().getString(R.string.triggers_sync_explanation),
                            TriggerInfo.Severity.MEDIUM));
                }
            } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": sync/package fallback failed", e); }
            return list;
        }


        int count = 0;
        List<String> entries = new ArrayList<>();

        Pattern authPat      = Pattern.compile("authority=([\\w.]+)");
        Pattern acctPat      = Pattern.compile("accountType=([\\w.]+)");
        Pattern periodPat    = Pattern.compile("period=(\\d+)(ms|s)?");
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
            if (mP.find() && periodSec == 0) {
                long val    = Long.parseLong(mP.group(1));
                String unit = mP.group(2);
                periodSec = "ms".equals(unit) ? val / 1000 : val;
            } else {
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
                analyzer.getContext().getString(R.string.triggers_sync_detail, count));
        if (!entries.isEmpty())
            detail.append(": ").append(String.join(" | ", entries));

        list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                AppTriggersAnalyzer.KEY_CAT_SYNC,
                analyzer.getContext().getString(R.string.triggers_cat_sync),
                detail.toString(),
                analyzer.getContext().getString(R.string.triggers_sync_explanation),
                TriggerInfo.Severity.MEDIUM));
        return list;
    }

// ---- buildSyncEntry ----
    public String buildSyncEntry(String authority, String acctType,
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

// ---- analyzeFcmRegistration ----
    public List<TriggerInfo> analyzeFcmRegistration(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        try {
            String pkgOut = analyzer.getShellManager().runShellCommandAndGetFullOutput(
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
            if (hasFcmService) detail = analyzer.getContext().getString(R.string.triggers_fcm_service_detail);
            else if (hasDataMsg) detail = analyzer.getContext().getString(R.string.triggers_fcm_receiver_detail);
            else detail = analyzer.getContext().getString(R.string.triggers_fcm_generic_detail);

            list.add(new TriggerInfo(TriggerInfo.Group.CAN_WAKE,
                    AppTriggersAnalyzer.KEY_CAT_FCM,
                    analyzer.getContext().getString(R.string.triggers_cat_fcm),
                    detail,
                    analyzer.getContext().getString(R.string.triggers_fcm_explanation),
                    TriggerInfo.Severity.MEDIUM));

        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": analyzeFcmRegistration failed", e); }
        return list;
    }

}
