package com.gree1d.reappzuku.manager;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.gree1d.reappzuku.core.ShellManager;
import com.gree1d.reappzuku.utils.AppModel;
import com.gree1d.reappzuku.utils.triggers.AppTriggersAnalyzer;
import com.gree1d.reappzuku.R;

public class ScanSystem {

    private static final String TAG = "ScanSystem";

    public enum Category { WAKELOCK, NETWORK, FGS, ALARM, SENSOR, LOCATION }

    public static class Finding {
        public final Category category;
        public final String   detail;
        public Finding(Category category, String detail) {
            this.category = category;
            this.detail   = detail;
        }
    }

    public static class AppLoad {
        public final String        packageName;
        public final String        appName;
        public final List<Finding> findings;
        public AppLoad(String packageName, String appName) {
            this.packageName = packageName;
            this.appName     = appName;
            this.findings    = new ArrayList<>();
        }
    }

    private final Context      context;
    private final ShellManager shellManager;

    public ScanSystem(Context context, ShellManager shellManager) {
        this.context      = context.getApplicationContext();
        this.shellManager = shellManager;
    }

    public List<AppLoad> scan(List<AppModel> apps) {
        Map<String, AppLoad> map = new LinkedHashMap<>();
        Map<String, String>  uidMap = new LinkedHashMap<>();

        for (AppModel app : apps) {
            String pkg = app.getPackageName();
            map.put(pkg, new AppLoad(pkg, app.getAppName()));
            try {
                int uid = context.getPackageManager().getApplicationInfo(pkg, 0).uid;
                uidMap.put(pkg, String.valueOf(uid));
            } catch (PackageManager.NameNotFoundException ignored) {}
        }

        ExecutorService pool = Executors.newFixedThreadPool(7);

        Future<Void> fWakelocks = pool.submit(() -> { scanWakelocks(map, uidMap);    return null; });
        Future<Void> fNetwork   = pool.submit(() -> { scanNetwork(map, uidMap);      return null; });
        Future<Void> fServices  = pool.submit(() -> { scanServices(map);             return null; });
        Future<Void> fAlarms    = pool.submit(() -> { scanAlarms(map);               return null; });
        Future<Void> fSensors   = pool.submit(() -> { scanSensors(map);              return null; });
        Future<Void> fLocation  = pool.submit(() -> { scanLocation(map);             return null; });
        Future<Void> fHistory   = pool.submit(() -> { scanWakelockHistory(map);      return null; });

        pool.shutdown();
        for (Future<Void> f : Arrays.asList(fWakelocks, fNetwork, fServices, fAlarms, fSensors, fLocation, fHistory)) {
            try { f.get(); } catch (Exception e) { Log.w(TAG, "scan task failed: " + e.getMessage()); }
        }

        List<AppLoad> result = new ArrayList<>();
        for (AppLoad load : map.values()) {
            if (load.findings.isEmpty()) continue;
            boolean onlyNetwork = load.findings.stream()
                    .allMatch(f -> f.category == Category.NETWORK);
            if (!onlyNetwork) result.add(load);
        }
        return result;
    }

    private void scanWakelocks(Map<String, AppLoad> map, Map<String, String> uidMap) {
        String powerOutput = shellManager.runShellCommandAndGetFullOutput("dumpsys power");
        if (powerOutput == null || powerOutput.trim().isEmpty()) return;

        StringBuilder wlBlock = new StringBuilder();
        boolean inSection = false;
        for (String line : powerOutput.split("\n")) {
            if (line.trim().startsWith("Wake Locks:"))               { inSection = true;  continue; }
            if (inSection && line.trim().startsWith("Suspend Blockers:")) break;
            if (inSection) wlBlock.append(line).append("\n");
        }
        if (wlBlock.length() == 0) return;

        Pattern heldMsPat  = Pattern.compile("held=(\\d+)ms");
        Pattern acquirePat = Pattern.compile("\\bacq(?:uire)?[=:](\\d+)");
        Pattern releasePat = Pattern.compile("\\brel(?:ease)?[=:](\\d+)");
        Pattern heldLegacy = Pattern.compile("(\\d+m\\s*\\d+s|\\d+s)");
        Pattern tagPat     = Pattern.compile("'([^']{1,60})'");

        for (String line : wlBlock.toString().split("\n")) {
            for (Map.Entry<String, AppLoad> entry : map.entrySet()) {
                String pkg = entry.getKey();
                String uid = uidMap.get(pkg);
                boolean byUid = uid != null && line.contains("uid=" + uid);
                boolean byTag = line.contains(pkg);
                if (!byUid && !byTag) continue;

                String typeLabel;
                if      (line.contains("PARTIAL"))   typeLabel = "Partial";
                else if (line.contains("FULL"))       typeLabel = "Full";
                else if (line.contains("SCREEN"))     typeLabel = "Screen";
                else if (line.contains("PROXIMITY"))  typeLabel = "Proximity";
                else                                  typeLabel = "WakeLock";

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
                    acqRel = "acq=" + mAcq.group(1) + " rel=" + mRel.group(1);
                } else if (mAcq.reset().find()) {
                    acqRel = "acq=" + mAcq.group(1);
                }

                StringBuilder detail = new StringBuilder(typeLabel);
                if (!tag.isEmpty())     detail.append(" · ").append(tag);
                if (!heldStr.isEmpty()) detail.append(" · held ").append(heldStr);
                if (!acqRel.isEmpty())  detail.append(" · ").append(acqRel);
                if (byTag && !byUid)    detail.append(" · via system");

                entry.getValue().findings.add(new Finding(Category.WAKELOCK, detail.toString()));
                break;
            }
        }
    }

    private void scanNetwork(Map<String, AppLoad> map, Map<String, String> uidMap) {
        String netstats = shellManager.runShellCommandAndGetFullOutput("dumpsys netstats detail");

        for (Map.Entry<String, AppLoad> entry : map.entrySet()) {
            String pkg = entry.getKey();
            String uid = uidMap.get(pkg);
            if (uid == null) continue;

            long rxBytes = 0, txBytes = 0;

            if (netstats != null) {
                boolean inSection = false;
                for (String line : netstats.split("\n")) {
                    if (line.contains("uid=" + uid) && line.contains("ident=")) {
                        inSection = true; continue;
                    }
                    if (inSection && line.contains("ident=") && !line.contains("uid=" + uid)) {
                        inSection = false;
                    }
                    if (!inSection) continue;
                    Matcher mRb = Pattern.compile("\\brb=(\\d+)").matcher(line);
                    Matcher mTb = Pattern.compile("\\btb=(\\d+)").matcher(line);
                    if (mRb.find()) rxBytes += Long.parseLong(mRb.group(1));
                    if (mTb.find()) txBytes += Long.parseLong(mTb.group(1));
                }
            }

            if (rxBytes + txBytes < 10 * 1024) {
                long[] proc = readNetworkBytesProcFallback(uid);
                rxBytes = proc[0]; txBytes = proc[1];
            }

            if (rxBytes + txBytes < 10 * 1024) continue;

            entry.getValue().findings.add(new Finding(Category.NETWORK,
                    "↓ " + formatBytes(rxBytes) + " / ↑ " + formatBytes(txBytes)));
        }
    }

    private long[] readNetworkBytesProcFallback(String uid) {
        long rx = 0, tx = 0;
        try {
            String stats = shellManager.runShellCommandAndGetFullOutput(
                    "cat /proc/net/xt_qtaguid/stats | grep \" " + uid + " \"");
            if (stats == null || stats.trim().isEmpty()) {
                String rxStr = shellManager.runShellCommandAndGetFullOutput(
                        "cat /proc/uid_stat/" + uid + "/tcp_rcv");
                String txStr = shellManager.runShellCommandAndGetFullOutput(
                        "cat /proc/uid_stat/" + uid + "/tcp_snd");
                if (rxStr != null) try { rx = Long.parseLong(rxStr.trim()); } catch (Exception ignored) {}
                if (txStr != null) try { tx = Long.parseLong(txStr.trim()); } catch (Exception ignored) {}
                return new long[]{rx, tx};
            }
            for (String line : stats.split("\n")) {
                String[] p = line.trim().split("\\s+");
                if (p.length < 8) continue;
                try { rx += Long.parseLong(p[5]); } catch (Exception ignored) {}
                try { tx += Long.parseLong(p[7]); } catch (Exception ignored) {}
            }
        } catch (Exception e) { Log.w(TAG, "network proc fallback: " + e.getMessage()); }
        return new long[]{rx, tx};
    }

    private void scanServices(Map<String, AppLoad> map) {
        String output = shellManager.runShellCommandAndGetFullOutput("dumpsys activity services");
        if (output == null || output.trim().isEmpty()) return;

        Pattern svcPat  = Pattern.compile("ServiceRecord\\{[^}]+\\s+([\\w.]+)/\\.?([\\w.$]+)");
        Pattern typePat = Pattern.compile("foregroundServiceType=(\\S+)");
        Pattern chanPat = Pattern.compile("channelId=([\\w.\\-]+)");
        Pattern impPat  = Pattern.compile("importance=(\\d+)");

        String  curPkg   = null, curSvc = null, curFgType = null;
        String  curChan  = null, curImp = null;
        boolean isFg = false, isSticky = false;
        boolean killable = true;

        for (String line : output.split("\n")) {
            String t = line.trim();
            Matcher mSvc = svcPat.matcher(t);
            if (mSvc.find()) {
                if (curPkg != null && map.containsKey(curPkg)) {
                    emitService(map.get(curPkg), curSvc, curFgType, curChan, curImp,
                            isFg, isSticky, killable);
                }
                curPkg    = mSvc.group(1);
                curSvc    = mSvc.group(2);
                curFgType = null; curChan = null; curImp = null;
                isFg = false; isSticky = false; killable = true;
                continue;
            }
            if (curPkg == null) continue;
            if (t.contains("isForeground=true"))                              isFg = true;
            if (t.contains("START_STICKY") || t.contains("startRequested=true")) isSticky = true;
            if (t.contains("stopWithTask=false") || t.contains("persistentProcess=true")) killable = false;
            Matcher mType = typePat.matcher(t);
            if (mType.find() && curFgType == null) curFgType = parseFgsType(mType.group(1));
            Matcher mChan = chanPat.matcher(t);
            if (mChan.find() && curChan == null) curChan = mChan.group(1);
            Matcher mImp = impPat.matcher(t);
            if (mImp.find() && curImp == null) curImp = mapNotifImportance(Integer.parseInt(mImp.group(1)));
        }
        if (curPkg != null && map.containsKey(curPkg)) {
            emitService(map.get(curPkg), curSvc, curFgType, curChan, curImp,
                    isFg, isSticky, killable);
        }
    }

    private void emitService(AppLoad load, String svc, String fgType, String chan,
            String imp, boolean isFg, boolean isSticky, boolean killable) {
        if (isFg) {
            StringBuilder detail = new StringBuilder(svc != null ? svc : load.packageName);
            if (fgType != null && !"NONE".equals(fgType)) detail.append(" [").append(fgType).append("]");
            if (chan    != null) detail.append(" · ch:").append(chan);
            if (imp     != null) detail.append(" · notif:").append(imp);
            detail.append(" · ").append(killable ? "can stop" : "protected");
            load.findings.add(new Finding(Category.FGS, detail.toString()));
        } else if (isSticky) {
            load.findings.add(new Finding(Category.FGS,
                    "Sticky: " + (svc != null ? svc : load.packageName)));
        }
    }

    private void scanAlarms(Map<String, AppLoad> map) {
        String output = shellManager.runShellCommandAndGetFullOutput("dumpsys alarm");
        if (output == null || output.trim().isEmpty()) return;

        AppTriggersAnalyzer.AlarmDumpsysParser parser = new AppTriggersAnalyzer.AlarmDumpsysParser();

        for (Map.Entry<String, AppLoad> entry : map.entrySet()) {
            String pkg = entry.getKey();
            List<AppTriggersAnalyzer.AlarmEntry> entries = parser.parseEntries(output, pkg);
            if (entries.isEmpty()) continue;

            int wakeupCount = 0, normalCount = 0;
            long minInterval = Long.MAX_VALUE, minTriggerDiff = Long.MAX_VALUE;
            List<String> alarmDetails = new ArrayList<>();

            for (AppTriggersAnalyzer.AlarmEntry e : entries) {
                if (e.isWakeup) wakeupCount++; else normalCount++;
                if (e.intervalMs > 0 && e.intervalMs < minInterval) minInterval = e.intervalMs;
                if (e.fireDiffMs != Long.MAX_VALUE && e.fireDiffMs < minTriggerDiff) minTriggerDiff = e.fireDiffMs;
                if (alarmDetails.size() < 4) alarmDetails.add(buildAlarmLine(e, pkg));
            }

            StringBuilder detail = new StringBuilder();
            detail.append(String.join("\n", alarmDetails));
            if (minTriggerDiff != Long.MAX_VALUE)
                detail.append(" · in ").append(formatInterval(minTriggerDiff));
            if (minInterval != Long.MAX_VALUE)
                detail.append(" · every ").append(formatInterval(minInterval));

            entry.getValue().findings.add(new Finding(Category.ALARM, detail.toString()));
        }
    }

    private String buildAlarmLine(AppTriggersAnalyzer.AlarmEntry e, String pkg) {
        StringBuilder sb = new StringBuilder();
        switch (e.type) {
            case "RTC_WAKEUP":     sb.append("RTC_WU"); break;
            case "ELAPSED_WAKEUP": sb.append("EL_WU");  break;
            case "RTC":            sb.append("RTC");     break;
            default:               sb.append("ELAPSED"); break;
        }
        String tag = e.tag != null ? e.tag : "";
        if (tag.startsWith("*") && tag.contains("/")) tag = tag.substring(tag.indexOf('/') + 1);
        if (tag.startsWith(pkg + ".")) tag = tag.substring(pkg.length() + 1);
        if (tag.startsWith(".")) tag = tag.substring(1);
        if (tag.length() > 40 && tag.contains(".")) tag = tag.substring(tag.lastIndexOf('.') + 1);
        sb.append(" · ").append(tag);
        if (e.exact) sb.append(" · exact");
        if (e.whileIdle) sb.append(" · while-idle");
        return sb.toString();
    }

    private void scanSensors(Map<String, AppLoad> map) {
        String output = shellManager.runShellCommandAndGetFullOutput("dumpsys sensorservice");
        if (output == null || output.trim().isEmpty()) {
            scanSensorsBatteryStatsFallback(map);
            return;
        }

        Map<String, List<String>> found = new LinkedHashMap<>();
        boolean inConn   = false;
        boolean relevant = false;
        String  connPkg  = null;
        List<String> connFound = new ArrayList<>();

        for (String line : output.split("\n")) {
            String t = line.trim();

            if (t.startsWith("Connection Number:") || t.startsWith("Active connections")) {
                if (relevant && connPkg != null && !connFound.isEmpty()) {
                    found.computeIfAbsent(connPkg, k -> new ArrayList<>()).addAll(connFound);
                }
                inConn = true; relevant = false; connPkg = null; connFound.clear();
                continue;
            }
            if (!inConn) continue;

            if (t.startsWith("packageName=") || t.startsWith("package=") || t.startsWith("Identity=")) {
                relevant = false; connPkg = null;
                for (String pkg : map.keySet()) {
                    if (t.contains(pkg)) { relevant = true; connPkg = pkg; break; }
                }
            }
            if (!relevant) continue;

            if (t.startsWith("Sensor:") || t.startsWith("SensorName=") || t.startsWith("sensor=")) {
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
                if (!connFound.contains(label)) connFound.add(label);
            }
            if (t.contains("GNSS") || t.contains("Gnss") || t.contains("GPS"))
                if (!connFound.contains("GPS")) connFound.add("GPS");
        }
        if (relevant && connPkg != null && !connFound.isEmpty()) {
            found.computeIfAbsent(connPkg, k -> new ArrayList<>()).addAll(connFound);
        }

        for (Map.Entry<String, List<String>> e : found.entrySet()) {
            AppLoad load = map.get(e.getKey());
            if (load != null) load.findings.add(new Finding(Category.SENSOR,
                    String.join(", ", e.getValue())));
        }

        for (String pkg : map.keySet()) {
            if (!found.containsKey(pkg)) {
                scanSensorsBatteryStatsFallback(map, pkg);
            }
        }
    }

    private void scanSensorsBatteryStatsFallback(Map<String, AppLoad> map) {
        for (String pkg : map.keySet()) scanSensorsBatteryStatsFallback(map, pkg);
    }

    private void scanSensorsBatteryStatsFallback(Map<String, AppLoad> map, String pkg) {
        try {
            String output = shellManager.runShellCommandAndGetFullOutput(
                    "dumpsys batterystats " + pkg);
            if (output == null) return;
            boolean inPkg = false;
            List<String> sensors = new ArrayList<>();
            Pattern srPat = Pattern.compile(
                    "Sensor\\s+(?:#)?(\\d+)[^:]*:\\s*(.*)", Pattern.CASE_INSENSITIVE);
            for (String line : output.split("\n")) {
                if (line.contains(pkg)) inPkg = true;
                if (!inPkg) continue;
                Matcher m = srPat.matcher(line.trim());
                if (!m.find()) continue;
                int handle = Integer.parseInt(m.group(1));
                String rest = m.group(2).trim();
                String dur = "";
                Matcher mDur = Pattern.compile("(\\d+)ms").matcher(rest);
                if (mDur.find()) dur = " (" + formatDuration(Long.parseLong(mDur.group(1))) + ")";
                String label = sensorHandleToName(handle) + dur;
                if (!sensors.contains(label)) sensors.add(label);
            }
            if (!sensors.isEmpty()) {
                AppLoad load = map.get(pkg);
                if (load != null) load.findings.add(new Finding(Category.SENSOR,
                        String.join(", ", sensors)));
            }
        } catch (Exception e) { Log.w(TAG, "sensors batterystats fallback: " + e.getMessage()); }
    }

    private void scanLocation(Map<String, AppLoad> map) {
        String output = shellManager.runShellCommandAndGetFullOutput("dumpsys location");
        if (output == null || output.trim().isEmpty()) return;

        Pattern ivPat  = Pattern.compile("interval=(\\d+)");
        Pattern fgPat  = Pattern.compile("foreground=(true|false)");
        Pattern gpsPat = Pattern.compile("activeGps(?:TimeMs)?=(\\d+)");
        Pattern accPat = Pattern.compile(
                "(PRIORITY_HIGH_ACCURACY|HIGH_ACCURACY|PRIORITY_BALANCED|BALANCED"
                + "|PRIORITY_LOW_POWER|LOW_POWER|PRIORITY_NO_POWER|NO_POWER|PASSIVE)",
                Pattern.CASE_INSENSITIVE);

        for (Map.Entry<String, AppLoad> entry : map.entrySet()) {
            String pkg = entry.getKey();

            Pattern reqPat = Pattern.compile(
                    "LocationRequest\\[([^]]+)].*?" + Pattern.quote(pkg), Pattern.CASE_INSENSITIVE);

            int     reqCount = 0;
            boolean hasFg = false, hasBg = false;
            String  bestAcc  = null;
            long    minIvMs  = Long.MAX_VALUE;
            long    activeGps = 0;
            boolean inBlock  = false;

            for (String line : output.split("\n")) {
                String t = line.trim();
                boolean hasPkg = t.contains(pkg);

                if (reqPat.matcher(t).find() || (hasPkg && t.startsWith("LocationRequest"))) {
                    inBlock = true; reqCount++;
                    Matcher mA = accPat.matcher(t);
                    if (mA.find()) bestAcc = mergeAccuracy(bestAcc, normalizeAccuracy(mA.group(1)));
                    Matcher mI = ivPat.matcher(t);
                    if (mI.find()) { long iv = Long.parseLong(mI.group(1)); if (iv > 0 && iv < minIvMs) minIvMs = iv; }
                    continue;
                }
                if (inBlock && (t.isEmpty() || (!hasPkg && t.startsWith("LocationRequest"))))
                    inBlock = false;
                if (!inBlock && !hasPkg) continue;

                Matcher mF = fgPat.matcher(t);
                if (mF.find()) { if ("true".equalsIgnoreCase(mF.group(1))) hasFg = true; else hasBg = true; }
                Matcher mG = gpsPat.matcher(t);
                if (mG.find()) activeGps += Long.parseLong(mG.group(1));
            }

            if (reqCount == 0) continue;

            StringBuilder detail = new StringBuilder(reqCount + " request" + (reqCount > 1 ? "s" : ""));
            if (bestAcc != null) detail.append(" · ").append(bestAcc);
            detail.append(" · ").append(hasFg && hasBg ? "fg+bg" : hasFg ? "foreground" : "background");
            if (minIvMs != Long.MAX_VALUE) detail.append(", every ").append(formatInterval(minIvMs));
            if (activeGps > 0) detail.append(" · GPS ").append(formatDuration(activeGps));

            entry.getValue().findings.add(new Finding(Category.LOCATION, detail.toString()));
        }
    }

    private String parseFgsType(String raw) {
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

    private String mapNotifImportance(int imp) {
        switch (imp) {
            case 5: return "URGENT"; case 4: return "HIGH";
            case 3: return "DEFAULT"; case 2: return "LOW";
            case 1: return "MIN"; default: return "NONE";
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

    private String mergeAccuracy(String current, String candidate) {
        String[] order = {"HIGH_ACCURACY","BALANCED","LOW_POWER","NO_POWER","PASSIVE"};
        if (current == null) return candidate;
        int ci = indexOf(order, current), ni = indexOf(order, candidate);
        return ci <= ni ? current : candidate;
    }

    private String normalizeAccuracy(String raw) {
        String u = raw.toUpperCase();
        if (u.contains("HIGH"))    return "HIGH_ACCURACY";
        if (u.contains("BALANCE")) return "BALANCED";
        if (u.contains("LOW"))     return "LOW_POWER";
        if (u.contains("NO_POWER")) return "NO_POWER";
        return "PASSIVE";
    }

    private int indexOf(String[] arr, String val) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(val)) return i;
        return arr.length;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024)    return bytes + " B";
        if (bytes < 1048576) return (bytes / 1024) + " KB";
        return (bytes / 1048576) + " MB";
    }

    private String formatDuration(long ms) {
        if (ms < 1000)       return ms + "ms";
        if (ms < 60_000)     return (ms / 1000) + "s";
        if (ms < 3_600_000)  return (ms / 60_000) + "m " + ((ms % 60_000) / 1000) + "s";
        return (ms / 3_600_000) + "h " + ((ms % 3_600_000) / 60_000) + "m";
    }

    private String formatInterval(long ms) {
        if (ms < 1000)      return ms + "ms";
        if (ms < 60_000)    return (ms / 1000) + "s";
        if (ms < 3_600_000) return (ms / 60_000) + "min";
        return (ms / 3_600_000) + "h";
    }
    private void scanWakelockHistory(Map<String, AppLoad> map) {
        try {
            String history = shellManager.runShellCommandAndGetFullOutput(
                    "dumpsys batterystats --history");
            if (history == null || history.trim().isEmpty()) return;

            Pattern timePat = Pattern.compile("RESET:TIME:\\s*(\\d+)");
            long baseUnixMs = 0, baseOffsetMs = 0;
            for (String line : history.split("\n")) {
                Matcher mt = timePat.matcher(line);
                if (!mt.find()) continue;
                baseUnixMs   = Long.parseLong(mt.group(1)) * 1000L;
                baseOffsetMs = parseHistoryOffset(line);
            }

            for (Map.Entry<String, AppLoad> entry : map.entrySet()) {
                String pkg = entry.getKey();
                boolean activeNow = entry.getValue().findings.stream()
                        .anyMatch(f -> f.category == Category.WAKELOCK);

                Pattern wakePat = Pattern.compile(
                        "\\+(\\d+)h(\\d+)m(\\d+)s(?:(\\d+)ms)?\\s.*?([+-])wake_lock[^=]*=\\S*"
                        + Pattern.quote(pkg) + "\\S*");
                Pattern procPat = Pattern.compile(
                        "\\+(\\d+)h(\\d+)m(\\d+)s(?:(\\d+)ms)?\\s.*?(?:Died|proc).*?"
                        + Pattern.quote(pkg));

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
                    syntheticLine = nowSdf.format(new java.util.Date())
                            + " → now  (active, confirmed by AppOps)";
                }
                if (pairs.isEmpty() && syntheticLine == null) continue;

                int from = Math.max(0, pairs.size() - 5);
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                        "HH:mm:ss", java.util.Locale.getDefault());
                StringBuilder sb = new StringBuilder();
                for (long[] pair : pairs.subList(from, pairs.size())) {
                    long acqUnix = baseUnixMs + pair[0] - baseOffsetMs;
                    String acqTime = sdf.format(new java.util.Date(acqUnix));
                    if (pair[2] == 1) {
                        long relUnix = baseUnixMs + pair[1] - baseOffsetMs;
                        long durMs = pair[1] - pair[0];
                        sb.append(acqTime).append(" → ")
                          .append(sdf.format(new java.util.Date(relUnix)))
                          .append("  (").append(formatDuration(durMs)).append(") released by system\n");
                    } else if (pair[2] == -1) {
                        sb.append(acqTime).append(" → ?\n");
                    } else {
                        long relUnix = baseUnixMs + pair[1] - baseOffsetMs;
                        long durMs = pair[1] - pair[0];
                        sb.append(acqTime).append(" → ")
                          .append(sdf.format(new java.util.Date(relUnix)))
                          .append("  (").append(formatDuration(durMs)).append(")\n");
                    }
                }
                if (syntheticLine != null) sb.append(syntheticLine).append("\n");

                String detail = sb.toString().trim();
                if (!detail.isEmpty()) {
                    entry.getValue().findings.add(new Finding(Category.WAKELOCK,
                            context.getString(R.string.scansystem_wakelock_history) + "\n" + detail));
                }
            }
        } catch (Exception e) { Log.w(TAG, "wakelock history: " + e.getMessage()); }
    }

    private long parseHistoryOffset(String line) {
        Pattern p = Pattern.compile("\\+(\\d+)h(\\d+)m(\\d+)s(?:(\\d+)ms)?");
        Matcher m = p.matcher(line);
        if (!m.find()) return 0;
        long ms = Long.parseLong(m.group(1)) * 3_600_000L
                + Long.parseLong(m.group(2)) * 60_000L
                + Long.parseLong(m.group(3)) * 1_000L;
        if (m.group(4) != null) ms += Long.parseLong(m.group(4));
        return ms;
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
            default:
                int standard = h & 0xFF;
                if (standard != h && standard > 0) return sensorHandleToName(standard);
                return "Sensor#" + h;
        }
    }

}
