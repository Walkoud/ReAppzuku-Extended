package com.gree1d.reappzuku;

import android.content.Context;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScanSystem {

    public static class AppLoad {
        public final String packageName;
        public final String appName;
        public final List<String> reasons;

        public AppLoad(String packageName, String appName) {
            this.packageName = packageName;
            this.appName     = appName;
            this.reasons     = new ArrayList<>();
        }
    }

    private static final long WINDOW_MS = 5 * 60 * 1000L;

    private final Context      context;
    private final ShellManager shellManager;

    public ScanSystem(Context context, ShellManager shellManager) {
        this.context      = context.getApplicationContext();
        this.shellManager = shellManager;
    }

    public List<AppLoad> scan(List<AppModel> apps) {
        Map<String, AppLoad> map = new LinkedHashMap<>();
        for (AppModel app : apps) {
            map.put(app.getPackageName(), new AppLoad(app.getPackageName(), app.getAppName()));
        }

        ExecutorService pool = Executors.newFixedThreadPool(6);

        Future<Map<String, List<String>>> fWakelocks  = pool.submit(this::collectWakelocks);
        Future<Map<String, List<String>>> fNetwork    = pool.submit(this::collectNetwork);
        Future<Map<String, List<String>>> fFgs        = pool.submit(this::collectFgs);
        Future<Map<String, List<String>>> fAlarms     = pool.submit(this::collectAlarmWakeups);
        Future<Map<String, List<String>>> fSensors    = pool.submit(this::collectSensors);
        Future<Map<String, List<String>>> fLocation   = pool.submit(this::collectLocation);

        pool.shutdown();

        mergeInto(map, safeGet(fWakelocks));
        mergeInto(map, safeGet(fNetwork));
        mergeInto(map, safeGet(fFgs));
        mergeInto(map, safeGet(fAlarms));
        mergeInto(map, safeGet(fSensors));
        mergeInto(map, safeGet(fLocation));

        List<AppLoad> result = new ArrayList<>();
        for (AppLoad load : map.values()) {
            if (!load.reasons.isEmpty()) result.add(load);
        }
        return result;
    }

    private void mergeInto(Map<String, AppLoad> map, Map<String, List<String>> data) {
        if (data == null) return;
        for (Map.Entry<String, List<String>> e : data.entrySet()) {
            AppLoad load = map.get(e.getKey());
            if (load != null) load.reasons.addAll(e.getValue());
        }
    }

    private <T> T safeGet(Future<T> f) {
        try { return f.get(); } catch (Exception e) { return null; }
    }

    private Map<String, List<String>> collectWakelocks() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        try {
            String out = shellManager.runShellCommandAndGetFullOutput("dumpsys power");
            if (out == null) return result;

            boolean inSection = false;
            long nowElapsed = android.os.SystemClock.elapsedRealtime();

            Pattern pkgPat  = Pattern.compile("([a-z][\\w.]+)");
            Pattern timePat = Pattern.compile("acquireTime=(\\d+)");
            Pattern heldPat = Pattern.compile("PARTIAL_WAKE_LOCK");

            for (String line : out.split("\n")) {
                String t = line.trim();
                if (t.startsWith("Wake Locks:")) { inSection = true; continue; }
                if (inSection && t.startsWith("Suspend Blockers:")) break;
                if (!inSection || !t.contains("PARTIAL_WAKE_LOCK")) continue;

                Matcher mTime = timePat.matcher(t);
                if (mTime.find()) {
                    long acquireElapsed = Long.parseLong(mTime.group(1));
                    if (nowElapsed - acquireElapsed > WINDOW_MS) continue;
                }

                Matcher mPkg = pkgPat.matcher(t);
                while (mPkg.find()) {
                    String pkg = mPkg.group(1);
                    if (pkg.contains(".") && result.containsKey(pkg) || isKnownPkg(pkg, result)) {
                        addReason(result, pkg, context.getString(R.string.scansystem_reason_wakelock));
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    private Map<String, List<String>> collectNetwork() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        try {
            String out = shellManager.runShellCommandAndGetFullOutput("dumpsys netstats detail");
            if (out == null) return result;

            Pattern uidPat   = Pattern.compile("uid=(\\d+)");
            Pattern bytesPat = Pattern.compile("rxBytes=(\\d+).*?txBytes=(\\d+)");

            String currentUid = null;
            for (String line : out.split("\n")) {
                String t = line.trim();
                Matcher mUid = uidPat.matcher(t);
                if (mUid.find()) { currentUid = mUid.group(1); continue; }
                if (currentUid == null) continue;
                Matcher mBytes = bytesPat.matcher(t);
                if (mBytes.find()) {
                    long rx = Long.parseLong(mBytes.group(1));
                    long tx = Long.parseLong(mBytes.group(2));
                    if (rx + tx > 0) {
                        String pkg = resolvePkg(currentUid);
                        if (pkg != null) {
                            addReason(result, pkg, context.getString(R.string.scansystem_reason_network));
                        }
                        currentUid = null;
                    }
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    private Map<String, List<String>> collectFgs() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        try {
            String out = shellManager.runShellCommandAndGetFullOutput("dumpsys activity services");
            if (out == null) return result;

            Pattern pkgPat  = Pattern.compile("ServiceRecord\\{[^}]+\\s([\\w.]+)/");
            Pattern fgsPat  = Pattern.compile("isForeground=true");
            Pattern typePat = Pattern.compile("foregroundServiceType=(\\d+)");

            String  curPkg  = null;
            boolean isFg    = false;
            String  fgsType = null;

            for (String line : out.split("\n")) {
                String t = line.trim();
                Matcher mPkg = pkgPat.matcher(t);
                if (mPkg.find()) {
                    if (curPkg != null && isFg) {
                        String label = fgsType != null
                                ? context.getString(R.string.scansystem_reason_fgs_typed, fgsType)
                                : context.getString(R.string.scansystem_reason_fgs);
                        addReason(result, curPkg, label);
                    }
                    curPkg  = mPkg.group(1);
                    isFg    = false;
                    fgsType = null;
                }
                if (t.contains("isForeground=true")) isFg = true;
                Matcher mType = typePat.matcher(t);
                if (mType.find()) fgsType = mType.group(1);
            }
            if (curPkg != null && isFg) {
                String label = fgsType != null
                        ? context.getString(R.string.scansystem_reason_fgs_typed, fgsType)
                        : context.getString(R.string.scansystem_reason_fgs);
                addReason(result, curPkg, label);
            }
        } catch (Exception ignored) {}
        return result;
    }

    private Map<String, List<String>> collectAlarmWakeups() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        try {
            String out = shellManager.runShellCommandAndGetFullOutput("dumpsys alarm");
            if (out == null) return result;

            Pattern statPat   = Pattern.compile(
                    "([\\w.]+)\\s+running.*?(\\d+)\\s+wakeup", Pattern.CASE_INSENSITIVE);
            Pattern timePat   = Pattern.compile("(\\d+)s\\)");
            boolean inStats   = false;

            for (String line : out.split("\n")) {
                String t = line.trim();
                if (t.startsWith("Alarm Stats:") || t.startsWith("Top Alarms:")) {
                    inStats = true; continue;
                }
                if (inStats && t.isEmpty()) { inStats = false; continue; }
                if (!inStats) continue;

                Matcher m = statPat.matcher(t);
                if (m.find()) {
                    String pkg     = m.group(1);
                    int    wakeups = Integer.parseInt(m.group(2));
                    if (wakeups > 0) {
                        addReason(result, pkg,
                                context.getString(R.string.scansystem_reason_alarm, wakeups));
                    }
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    private Map<String, List<String>> collectSensors() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        try {
            String out = shellManager.runShellCommandAndGetFullOutput("dumpsys sensorservice");
            if (out == null) return result;

            boolean inActive = false;
            Pattern pkgPat   = Pattern.compile("([a-z][\\w.]+\\.[\\w.]+)");
            Pattern periodPat = Pattern.compile("samplingPeriod=(\\d+)");

            for (String line : out.split("\n")) {
                String t = line.trim();
                if (t.startsWith("Active sensors:") || t.startsWith("Listeners:")) {
                    inActive = true; continue;
                }
                if (inActive && t.isEmpty()) { inActive = false; continue; }
                if (!inActive) continue;

                Matcher mPeriod = periodPat.matcher(t);
                if (!mPeriod.find() || Long.parseLong(mPeriod.group(1)) == 0) continue;

                Matcher mPkg = pkgPat.matcher(t);
                if (mPkg.find()) {
                    addReason(result, mPkg.group(1),
                            context.getString(R.string.scansystem_reason_sensor));
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    private Map<String, List<String>> collectLocation() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        try {
            String out = shellManager.runShellCommandAndGetFullOutput("dumpsys location");
            if (out == null) return result;

            boolean inReceivers = false;
            Pattern pkgPat      = Pattern.compile("([a-z][\\w.]+\\.[\\w.]+)");
            Pattern intervalPat = Pattern.compile("interval=(\\d+)");
            Pattern providerPat = Pattern.compile("(gps|network|fused|gnss|passive)",
                    Pattern.CASE_INSENSITIVE);

            for (String line : out.split("\n")) {
                String t = line.trim();
                if (t.startsWith("Active receivers:") || t.startsWith("GPS requests:")) {
                    inReceivers = true; continue;
                }
                if (inReceivers && t.isEmpty()) { inReceivers = false; continue; }
                if (!inReceivers) continue;

                Matcher mInterval = intervalPat.matcher(t);
                if (mInterval.find() && Long.parseLong(mInterval.group(1)) == 0) continue;

                Matcher mPkg = pkgPat.matcher(t);
                if (!mPkg.find()) continue;
                String pkg = mPkg.group(1);

                String provider = "";
                Matcher mProv = providerPat.matcher(t);
                if (mProv.find()) provider = mProv.group(1).toUpperCase();

                String label = provider.isEmpty()
                        ? context.getString(R.string.scansystem_reason_location)
                        : context.getString(R.string.scansystem_reason_location_provider, provider);
                addReason(result, pkg, label);
            }
        } catch (Exception ignored) {}
        return result;
    }

    private String resolvePkg(String uid) {
        try {
            String[] pkgs = context.getPackageManager()
                    .getPackagesForUid(Integer.parseInt(uid));
            if (pkgs != null && pkgs.length > 0) return pkgs[0];
        } catch (Exception ignored) {}
        return null;
    }

    private boolean isKnownPkg(String pkg, Map<String, List<String>> map) {
        return map.containsKey(pkg);
    }

    private void addReason(Map<String, List<String>> map, String pkg, String reason) {
        if (!map.containsKey(pkg)) map.put(pkg, new ArrayList<>());
        List<String> reasons = map.get(pkg);
        if (!reasons.contains(reason)) reasons.add(reason);
    }
}
