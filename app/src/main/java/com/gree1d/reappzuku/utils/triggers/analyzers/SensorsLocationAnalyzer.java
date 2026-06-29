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

public class SensorsLocationAnalyzer {

    private static final String FILE_NAME = "SensorsLocationAnalyzer";

    private final AppTriggersAnalyzer analyzer;

    public SensorsLocationAnalyzer(AppTriggersAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

// ---- CONST:LOCATION_PROVIDER_PAT ----
    private static final Pattern LOCATION_PROVIDER_PAT = Pattern.compile(
            "provider=(gps|network|fused|passive|gnss)", Pattern.CASE_INSENSITIVE);


// ---- analyzeSensors ----
    public List<TriggerInfo> analyzeSensors(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();

        List<String> sensors = parseSensorService(packageName);
        if (sensors.isEmpty()) {
            AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": Sensors/sensorservice - no results, trying batterystats fallback");
            sensors = parseSensorsBatteryStats(packageName);
            if (!sensors.isEmpty()) AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": Sensors/batterystats - OK: " + sensors);
            else AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": Sensors/batterystats - no results");
        } else {
            AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": Sensors/sensorservice - OK: " + sensors);
        }
        if (sensors.isEmpty()) return list;

        boolean heavy = sensors.stream()
                .anyMatch(s -> s.startsWith("GPS") || s.startsWith("Gyro")
                        || s.startsWith("Baro"));

        list.add(new TriggerInfo(TriggerInfo.Group.ACTIVE_NOW,
                AppTriggersAnalyzer.KEY_CAT_SENSORS,
                analyzer.getContext().getString(R.string.triggers_cat_sensors, sensors.size()),
                String.join(", ", sensors.subList(0, Math.min(sensors.size(), 6))),
                analyzer.getContext().getString(R.string.triggers_sensors_explanation),
                heavy ? TriggerInfo.Severity.HIGH : TriggerInfo.Severity.MEDIUM));
        return list;
    }

// ---- parseSensorService ----
    private List<String> parseSensorService(String packageName) {
        List<String> result = new ArrayList<>();
        String output = analyzer.getShellManager().runShellCommandAndGetFullOutput("dumpsys sensorservice");
        if (output == null || output.trim().isEmpty()) return result;

        boolean inConn = false, relevant = false;
        List<String> found = new ArrayList<>();

        for (String line : output.split("\n")) {
            String t = line.trim();

            if (t.startsWith("Connection Number:") || t.startsWith("Active connections")) {
                if (relevant && !found.isEmpty())
                    for (String s : found) if (!result.contains(s)) result.add(s);
                inConn = true; relevant = false; found.clear();
                continue;
            }
            if (!inConn) continue;


            if (t.startsWith("packageName=") || t.startsWith("package=")
                    || t.startsWith("Identity="))
                relevant = t.contains(packageName);

            if (!relevant) continue;

            if (t.startsWith("Sensor:") || t.startsWith("SensorName=")
                    || t.startsWith("sensor=")) {
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
                if (!found.contains(label)) found.add(label);
            }
            if (t.contains("GNSS") || t.contains("Gnss") || t.contains("GPS"))
                if (!found.contains("GPS")) found.add("GPS");
        }
        if (relevant && !found.isEmpty())
            for (String s : found) if (!result.contains(s)) result.add(s);
        return result;
    }

// ---- parseSensorsBatteryStats ----
    private List<String> parseSensorsBatteryStats(String packageName) {
        List<String> result = new ArrayList<>();
        String output = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                "dumpsys batterystats " + packageName);
        if (output == null) return result;

        boolean inPkg = false;
        Pattern srPat = Pattern.compile(
                "Sensor\\s+(?:#)?(\\d+)[^:]*:\\s*(.*)", Pattern.CASE_INSENSITIVE);
        for (String line : output.split("\n")) {
            if (line.contains(packageName)) inPkg = true;
            if (!inPkg) continue;
            Matcher m = srPat.matcher(line.trim());
            if (!m.find()) continue;
            int    handle = Integer.parseInt(m.group(1));
            String rest   = m.group(2).trim();
            String dur    = "";
            Matcher mDur = Pattern.compile("(\\d+)ms").matcher(rest);
            if (mDur.find()) dur = " (" + analyzer.formatDuration(Long.parseLong(mDur.group(1))) + ")";
            String label = sensorHandleToName(handle) + dur;
            if (!result.contains(label)) result.add(label);
        }
        return result;
    }

// ---- sensorHandleToName ----
    public String sensorHandleToName(int h) {
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

// ---- classifySensor ----
    public String classifySensor(String raw) {
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

// ---- analyzeLocationRequests ----
    public List<TriggerInfo> analyzeLocationRequests(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = analyzer.getShellManager().runShellCommandAndGetFullOutput("dumpsys location");
        if (output == null || output.trim().isEmpty()) return list;

        int     reqCount  = 0;
        boolean hasFg = false, hasBg = false;
        String  bestAcc   = null;
        long    minIvMs   = Long.MAX_VALUE;
        long    activeGps = 0;

        Pattern reqPat  = Pattern.compile(
                "LocationRequest\\[([^]]+)].*?" + Pattern.quote(packageName),
                Pattern.CASE_INSENSITIVE);
        Pattern ivPat   = Pattern.compile("interval=(\\d+)");
        Pattern fgPat   = Pattern.compile("foreground=(true|false)");
        Pattern gpsPat  = Pattern.compile("activeGps(?:TimeMs)?=(\\d+)");
        Pattern accPat  = Pattern.compile(
                "(PRIORITY_HIGH_ACCURACY|HIGH_ACCURACY|PRIORITY_BALANCED|BALANCED"
                + "|PRIORITY_LOW_POWER|LOW_POWER|PRIORITY_NO_POWER|NO_POWER|PASSIVE)",
                Pattern.CASE_INSENSITIVE);

        boolean inBlock = false;
        for (String line : output.split("\n")) {
            String t = line.trim();
            boolean hasPkg = t.contains(packageName);

            if (reqPat.matcher(t).find()
                    || (hasPkg && t.startsWith("LocationRequest"))) {
                inBlock = true; reqCount++;
                Matcher mA = accPat.matcher(t);
                if (mA.find()) bestAcc = mergeAccuracy(bestAcc, normalizeAccuracy(mA.group(1)));
                Matcher mI = ivPat.matcher(t);
                if (mI.find()) { long iv=Long.parseLong(mI.group(1)); if(iv>0&&iv<minIvMs) minIvMs=iv; }

                if (analyzer.apiLevel >= Build.VERSION_CODES.R && analyzer.apiLevel <= Build.VERSION_CODES.TIRAMISU) {
                    Matcher mProv = LOCATION_PROVIDER_PAT.matcher(t);
                    if (mProv.find()) {
                        String provider = mProv.group(1).toLowerCase();
                        if ("gps".equals(provider) && bestAcc == null) bestAcc = "HIGH_ACCURACY";
                    }
                }
                continue;
            }
            if (inBlock && (t.isEmpty() || (!hasPkg && t.startsWith("LocationRequest"))))
                inBlock = false;
            if (!inBlock && !hasPkg) continue;

            Matcher mF = fgPat.matcher(t);
            if (mF.find()) { if("true".equalsIgnoreCase(mF.group(1))) hasFg=true; else hasBg=true; }
            Matcher mG = gpsPat.matcher(t);
            if (mG.find()) activeGps += Long.parseLong(mG.group(1));
        }

        if (reqCount == 0) return list;

        AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": analyzeLocationRequests: pkg=" + packageName + " reqCount=" + reqCount + " accuracy=" + bestAcc + " hasFg=" + hasFg + " hasBg=" + hasBg);

        StringBuilder detail = new StringBuilder(
                analyzer.getContext().getString(R.string.triggers_location_requests, reqCount));
        if (bestAcc != null) detail.append(" · ").append(bestAcc);
        detail.append(" · ").append(hasFg && hasBg ? analyzer.getContext().getString(R.string.triggers_location_fg_bg)
                : hasFg ? analyzer.getContext().getString(R.string.triggers_location_fg)
                        : analyzer.getContext().getString(R.string.triggers_location_bg));
        if (minIvMs != Long.MAX_VALUE)
            detail.append(analyzer.getContext().getString(R.string.triggers_location_interval, analyzer.formatInterval(minIvMs)));
        if (activeGps > 0)
            detail.append(analyzer.getContext().getString(R.string.triggers_location_active_gps, analyzer.formatDuration(activeGps)));

        TriggerInfo.Severity sev = hasBg && "HIGH_ACCURACY".equals(bestAcc)
                ? TriggerInfo.Severity.HIGH
                : "HIGH_ACCURACY".equals(bestAcc) || hasBg
                        ? TriggerInfo.Severity.MEDIUM : TriggerInfo.Severity.LOW;

        list.add(new TriggerInfo(TriggerInfo.Group.ACTIVE_NOW,
                AppTriggersAnalyzer.KEY_CAT_LOCATION,
                analyzer.getContext().getString(R.string.triggers_cat_location),
                detail.toString(),
                analyzer.getContext().getString(hasBg ? R.string.triggers_location_bg_explanation
                                        : R.string.triggers_location_fg_explanation),
                sev));

        if (analyzer.apiLevel >= Build.VERSION_CODES.R && analyzer.apiLevel <= Build.VERSION_CODES.TIRAMISU) {
            list.addAll(analyzeBackgroundLocationPermission(packageName));
        }

        return list;
    }

// ---- analyzeBackgroundLocationPermission ----
    public List<TriggerInfo> analyzeBackgroundLocationPermission(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        try {
            String pkgOut = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                    "dumpsys package " + packageName + " | grep -A1 ACCESS_BACKGROUND_LOCATION");
            if (pkgOut != null && pkgOut.contains("granted=true")) {
                list.add(new TriggerInfo(TriggerInfo.Group.ACTIVE_NOW,
                        AppTriggersAnalyzer.KEY_CAT_LOCATION,
                analyzer.getContext().getString(R.string.triggers_cat_location),
                        analyzer.getContext().getString(R.string.triggers_bg_location_detail),                        
                        analyzer.getContext().getString(R.string.triggers_bg_location_explanation),                        
                        TriggerInfo.Severity.HIGH));
            }
        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": bg location perm check failed", e); }
        return list;
    }

// ---- normalizeAccuracy ----
    public String normalizeAccuracy(String raw) {
        String n = raw.toUpperCase();
        if (n.contains("HIGH"))    return "HIGH_ACCURACY";
        if (n.contains("BALANCE")) return "BALANCED";
        if (n.contains("LOW"))     return "LOW_POWER";
        return "NO_POWER";
    }

// ---- mergeAccuracy ----
    public String mergeAccuracy(String cur, String cand) {
        if (cur == null) return cand;
        String[] ord = {"HIGH_ACCURACY","BALANCED","LOW_POWER","NO_POWER"};
        int ci=3, ca=3;
        for (int i=0;i<ord.length;i++) { if(ord[i].equals(cur)) ci=i; if(ord[i].equals(cand)) ca=i; }
        return ci <= ca ? cur : cand;
    }

}
