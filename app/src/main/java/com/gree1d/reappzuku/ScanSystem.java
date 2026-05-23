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
            AppTriggersAnalyzer.AnalysisType.SENSORS,
            AppTriggersAnalyzer.AnalysisType.LOCATION_REQUESTS
    );

    private final AppTriggersAnalyzer analyzer;

    public ScanSystem(Context context, ShellManager shellManager) {
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

        List<AppLoad> result = new ArrayList<>();
        for (AppModel app : apps) {
            List<AppTriggersAnalyzer.TriggerInfo> triggers = safeGet(futures.get(app.getPackageName()));
            if (triggers == null) continue;

            AppLoad load = new AppLoad(app.getPackageName(), app.getAppName());
            for (AppTriggersAnalyzer.TriggerInfo t : triggers) {
                Category cat = mapCategory(t.category);
                if (cat != null) load.findings.add(new Finding(cat, t.detail));
            }
            if (!load.findings.isEmpty()) result.add(load);
        }
        return result;
    }

    private Category mapCategory(String category) {
        if (category == null) return null;
        String c = category.toLowerCase();
        if (c.contains("wakelock"))                          return Category.WAKELOCK;
        if (c.contains("network") || c.contains("traffic")) return Category.NETWORK;
        if (c.contains("foreground") || c.contains("service") || c.contains("binding")) return Category.FGS;
        if (c.contains("alarm") || c.contains("wakeup"))    return Category.ALARM;
        if (c.contains("sensor"))                            return Category.SENSOR;
        if (c.contains("location") || c.contains("gps"))    return Category.LOCATION;
        return null;
    }

    private <T> T safeGet(Future<T> f) {
        try { return f.get(); } catch (Exception e) { return null; }
    }
}
