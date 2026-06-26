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

public class NetworkAnalyzer {

    private static final String FILE_NAME = "NetworkAnalyzer";

    private final AppTriggersAnalyzer analyzer;

    public NetworkAnalyzer(AppTriggersAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

// ---- analyzeNetworkActivity ----
    public List<TriggerInfo> analyzeNetworkActivity(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String uid = analyzer.getCachedUid();
        if (uid == null) return list;

        long rxBytes = 0, txBytes = 0;
        String netstats = null;
        try {
            netstats = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                    "dumpsys netstats detail | grep -A5 uid=" + uid);
            if (netstats == null || netstats.trim().isEmpty()) {
                AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": NetworkActivity/netstats detail - empty, trying fallback");
                netstats = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                        "dumpsys netstats | grep " + packageName);
            } else {
                AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": NetworkActivity/netstats detail - OK");
            }
        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": NetworkActivity/netstats failed", e); }
        if (netstats != null) {
            // New format (Android 14+): rb= tb= rp= tp= st= op=
            // Old format:               rxBytes= txBytes=
            Matcher mRx = Pattern.compile("(?:rxBytes|rb)=(\\d+)").matcher(netstats);
            Matcher mTx = Pattern.compile("(?:txBytes|tb)=(\\d+)").matcher(netstats);
            while (mRx.find()) rxBytes += Long.parseLong(mRx.group(1));
            while (mTx.find()) txBytes += Long.parseLong(mTx.group(1));
        }

        List<String> established = new ArrayList<>();
        try {
            String connOut = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                    "dumpsys connectivity | grep -i " + packageName);
            if (connOut != null) {
                Pattern addrPat = Pattern.compile(
                        "(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+)");
                for (String line : connOut.split("\n")) {
                    if (!line.toLowerCase().contains("established")
                            && !line.toLowerCase().contains("connected")) continue;
                    Matcher m = addrPat.matcher(line);
                    while (m.find() && established.size() < 5) {
                        String addr = m.group(1);
                        if (!established.contains(addr)) established.add(addr);
                    }
                }
            }
        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": NetworkActivity/connectivity failed", e); }

        long total = rxBytes + txBytes;
        if (total == 0 && established.isEmpty() && analyzer.apiLevel >= AppTriggersAnalyzer.API_BAL_PRIVILEGES) {
            long[] procBytes = readNetworkBytesProcFallback(uid);
            rxBytes = procBytes[0];
            txBytes = procBytes[1];
            total = rxBytes + txBytes;
        }
        AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": analyzeNetworkActivity: uid=" + uid + " rx=" + rxBytes + " tx=" + txBytes + " established=" + established.size());
        if (total < 10 * 1024 && established.isEmpty()) return list;

        StringBuilder detail = new StringBuilder();
        if (!established.isEmpty()) {
            detail.append(analyzer.getContext().getString(
                    R.string.triggers_network_established, established.size()));
            detail.append(": ").append(String.join(", ", established));
        }
        if (total > 0) {
            if (detail.length() > 0) detail.append(", ");
            detail.append(analyzer.getContext().getString(R.string.triggers_network_traffic,
                    analyzer.formatBytes(rxBytes), analyzer.formatBytes(txBytes)));
        }

        TriggerInfo.Severity sev = !established.isEmpty() ? TriggerInfo.Severity.HIGH
                : total > 1024 * 1024 ? TriggerInfo.Severity.MEDIUM : TriggerInfo.Severity.LOW;

        list.add(new TriggerInfo(TriggerInfo.Group.ACTIVE_NOW,
                AppTriggersAnalyzer.KEY_CAT_NETWORK,
                analyzer.getContext().getString(R.string.triggers_cat_network),
                detail.toString(),
                analyzer.getContext().getString(R.string.triggers_network_explanation),
                sev));

        if (analyzer.apiLevel >= Build.VERSION_CODES.R && analyzer.apiLevel <= Build.VERSION_CODES.TIRAMISU) {
            list.addAll(analyzeNetworkPolicy(packageName));
        }

        return list;
    }

// ---- readNetworkBytesProcFallback ----
    public long[] readNetworkBytesProcFallback(String uid) {
        long rx = 0, tx = 0;
        if (uid == null) return new long[]{0, 0};
        try {
            String stats = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                    "cat /proc/net/xt_qtaguid/stats | grep \" " + uid + " \"");
            if (stats == null || stats.trim().isEmpty()) {
                stats = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                        "cat /proc/uid_stat/" + uid + "/tcp_rcv");
                if (stats != null && !stats.trim().isEmpty()) {
                    try { rx = Long.parseLong(stats.trim()); } catch (NumberFormatException ignored) {}
                }
                String txStr = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                        "cat /proc/uid_stat/" + uid + "/tcp_snd");
                if (txStr != null && !txStr.trim().isEmpty()) {
                    try { tx = Long.parseLong(txStr.trim()); } catch (NumberFormatException ignored) {}
                }
                return new long[]{rx, tx};
            }
            for (String line : stats.split("\n")) {
                String[] p = line.trim().split("\\s+");
                if (p.length < 8) continue;
                try {
                    rx += Long.parseLong(p[5]);
                    tx += Long.parseLong(p[7]);
                } catch (NumberFormatException ignored) {}
            }
        } catch (Exception e) {
            AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": network /proc fallback failed", e);
        }
        return new long[]{rx, tx};
    }

// ---- analyzeNetworkPolicy ----
    public List<TriggerInfo> analyzeNetworkPolicy(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        if (analyzer.getCachedUid() == null) return list;
        try {
            String netPolicy = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                    "dumpsys netpolicy | grep uid=" + analyzer.getCachedUid());
            if (netPolicy == null || netPolicy.trim().isEmpty()) {
                netPolicy = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                        "dumpsys netpolicy | grep " + packageName);
            }
            if (netPolicy == null) return list;

            boolean rejected = netPolicy.contains("REJECT_METERED_BACKGROUND");
            boolean allowed  = netPolicy.contains("ALLOW_METERED_BACKGROUND");
            Matcher mPolicy  = Pattern.compile("policy=(\\d+)").matcher(netPolicy);
            if (mPolicy.find()) {
                int policy = Integer.parseInt(mPolicy.group(1));
                if ((policy & 2) != 0) rejected = true;
                if ((policy & 4) != 0) allowed  = true;
            }

            if (rejected) {
                AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": analyzeNetworkPolicy: background REJECTED for uid=" + analyzer.getCachedUid());
                list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                        AppTriggersAnalyzer.KEY_CAT_NETWORK,
                analyzer.getContext().getString(R.string.triggers_cat_network),
                        analyzer.getContext().getString(R.string.triggers_network_bg_blocked_detail),
                        analyzer.getContext().getString(R.string.triggers_network_bg_blocked_explanation),
                        TriggerInfo.Severity.INFO));
            } else if (allowed) {
                AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": analyzeNetworkPolicy: background ALLOWED for uid=" + analyzer.getCachedUid());
                list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                        AppTriggersAnalyzer.KEY_CAT_NETWORK,
                analyzer.getContext().getString(R.string.triggers_cat_network),
                        analyzer.getContext().getString(R.string.triggers_network_bg_allowed_detail),                       
                        analyzer.getContext().getString(R.string.triggers_network_bg_allowed_explanation),                       
                        TriggerInfo.Severity.MEDIUM));
            }
        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": netpolicy check failed", e); }
        return list;
    }

}
