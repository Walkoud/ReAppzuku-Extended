package com.gree1d.reappzuku;

import android.content.Context;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ScanSystem {

    public enum Category {
        WAKELOCK, NETWORK, FGS, ALARM, SENSOR, LOCATION
    }

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

    private static final EnumSet<AppTriggersAnalyzer.AnalysisType> SCAN_TYPES = EnumSet.of(
            AppTriggersAnalyzer.AnalysisType.WAKELOCKS,
            AppTriggersAnalyzer.AnalysisType.NETWORK_ACTIVITY,
            AppTriggersAnalyzer.AnalysisType.SERVICES_AND_BINDINGS,
            AppTriggersAnalyzer.AnalysisType.ALARMS,
            AppTriggersAnalyzer.AnalysisType.EXCESSIVE_WAKEUPS,
            AppTriggersAnalyzer.AnalysisType.SENSORS,
            AppTriggersAnalyzer.AnalysisType.LOCATION_REQUESTS
    );

    private final Context             context;
    private final AppTriggersAnalyzer analyzer;

    public ScanSystem(Context context, ShellManager shellManager) {
        this.context  = context.getApplicationContext();
        this.analyzer = new AppTriggersAnalyzer(context, shellManager);
    }

    public List<AppLoad> scan(List<AppModel> apps) {
        ExecutorService pool = Executors.newFixedThreadPool(
                Math.min(apps.size(), Runtime.getRuntime().availableProcessors()));

        Map<String, Future<List<AppTriggersAnalyzer.TriggerInfo>>> futures = new LinkedHashMap<>();
        for (AppModel app : apps) {
            String pkg = app.getPackageName();
            futures.put(pkg, pool.submit(() -> analyzer.analyze(pkg, SCAN_TYPES)));
        }
        pool.shutdown();

        String catWakelock  = context.getString(R.string.triggers_cat_wakelock);
        String catNetwork   = context.getString(R.string.triggers_cat_network);
        String catFgService = context.getString(R.string.triggers_cat_fg_service);
        String catSticky    = context.getString(R.string.triggers_cat_sticky);
        String catBindings  = context.getString(R.string.triggers_cat_bindings, 0);
        String catAlarms    = context.getString(R.string.triggers_cat_alarms);
        String catWakeups   = context.getString(R.string.triggers_cat_wakeups);
        String catSensors   = context.getString(R.string.triggers_cat_sensors, 0);
        String catLocation  = context.getString(R.string.triggers_cat_location);

        List<AppLoad> result = new ArrayList<>();

        for (AppModel app : apps) {
            List<AppTriggersAnalyzer.TriggerInfo> triggers = safeGet(futures.get(app.getPackageName()));
            if (triggers == null) continue;

            AppLoad load = new AppLoad(app.getPackageName(), app.getAppName());

            for (AppTriggersAnalyzer.TriggerInfo t : triggers) {
                Category cat = resolveCategory(t.category,
                        catWakelock, catNetwork,
                        catFgService, catSticky, catBindings,
                        catAlarms, catWakeups,
                        catSensors, catLocation);
                if (cat != null) load.findings.add(new Finding(cat, t.detail));
            }

            if (!load.findings.isEmpty()) result.add(load);
        }
        return result;
    }

    private Category resolveCategory(String cat,
            String wakelock, String network,
            String fgService, String sticky, String bindings,
            String alarms, String wakeups,
            String sensors, String location) {
        if (cat == null) return null;
        if (cat.equals(wakelock))                        return Category.WAKELOCK;
        if (cat.equals(network))                         return Category.NETWORK;
        if (cat.equals(fgService) || cat.equals(sticky)
                || cat.startsWith(stripCount(bindings))) return Category.FGS;
        if (cat.equals(alarms) || cat.equals(wakeups))  return Category.ALARM;
        if (cat.startsWith(stripCount(sensors)))         return Category.SENSOR;
        if (cat.equals(location))                        return Category.LOCATION;
        return null;
    }

    private String stripCount(String s) {
        int paren = s.indexOf('(');
        return paren > 0 ? s.substring(0, paren).trim() : s.trim();
    }

    private <T> T safeGet(Future<T> f) {
        try { return f.get(); } catch (Exception e) { return null; }
    }
}
