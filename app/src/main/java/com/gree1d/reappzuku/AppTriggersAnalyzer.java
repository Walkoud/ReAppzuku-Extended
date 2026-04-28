package com.gree1d.reappzuku;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyzes why a specific app is running in the background.
 * Uses shell commands via ShellManager (Root or Shizuku).
 *
 * All methods are blocking — call only from a background thread.
 */
public class AppTriggersAnalyzer {

    private static final String TAG = "AppTriggersAnalyzer";

    public static final class TriggerInfo {
        public final String category;
        public final String detail;
        public final String explanation;
        public final Severity severity;

        public enum Severity { HIGH, MEDIUM, LOW, INFO }

        public TriggerInfo(String category, String detail, String explanation, Severity severity) {
            this.category    = category;
            this.detail      = detail;
            this.explanation = explanation;
            this.severity    = severity;
        }
    }

    private final ShellManager shellManager;
    private final Context context;

    public AppTriggersAnalyzer(Context context, ShellManager shellManager) {
        this.context      = context.getApplicationContext();
        this.shellManager = shellManager;
    }

    /**
     * Run all analyses for the given package and return a combined list.
     * Blocking — must be called from a background thread.
     */
    public List<TriggerInfo> analyze(String packageName) {
        List<TriggerInfo> results = new ArrayList<>();

        // --- Existing ---
        results.addAll(analyzeCallerApp(packageName));
        results.addAll(analyzeServicesAndBindings(packageName)); // объединяет fg + sticky + bindings
        results.addAll(analyzeBroadcastReceivers(packageName));
        results.addAll(analyzeContentProviders(packageName));
        results.addAll(analyzeSyncAdapters(packageName));
        results.addAll(analyzeAlarms(packageName));
        results.addAll(analyzeJobs(packageName));
        results.addAll(analyzeDozeExemption(packageName));
        results.addAll(analyzeWakelocks(packageName));

        // --- New ---
        results.addAll(analyzeBroadcastHistory(packageName));
        results.addAll(analyzePendingIntents(packageName));
        results.addAll(analyzeBootReceivers(packageName));
        results.addAll(analyzeStandbyBucket(packageName));
        results.addAll(analyzeBatteryStats(packageName));
        results.addAll(analyzeNetworkActivity(packageName));

        if (results.isEmpty()) {
            results.add(new TriggerInfo(
                    context.getString(R.string.triggers_none_title),
                    context.getString(R.string.triggers_none_detail),
                    context.getString(R.string.triggers_none_explanation),
                    TriggerInfo.Severity.INFO));
        }

        return results;
    }

    // -------------------------------------------------------------------------
    // 0. Caller app
    // -------------------------------------------------------------------------

    private List<TriggerInfo> analyzeCallerApp(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = shellManager.runShellCommandAndGetFullOutput("dumpsys activity processes");
        if (output == null || output.trim().isEmpty()) return list;

        boolean inTargetProcess = false;
        String callerPkg = null;

        for (String line : output.split("\n")) {
            if (line.contains("ProcessRecord") && line.contains(packageName)) {
                inTargetProcess = true;
                callerPkg = null;
            }
            if (inTargetProcess && line.contains("ProcessRecord") && !line.contains(packageName)) {
                break;
            }
            if (!inTargetProcess) continue;

            Matcher m = Pattern.compile("clientPackage=([\\w.]+)").matcher(line);
            if (m.find()) { callerPkg = m.group(1); break; }

            Matcher m2 = Pattern.compile("callingPackage=([\\w.]+)").matcher(line);
            if (m2.find()) { callerPkg = m2.group(1); break; }
        }

        if (callerPkg == null || callerPkg.equals(packageName) || callerPkg.equals("android")) {
            return list;
        }

        String callerName = resolveAppName(callerPkg);
        String detail = callerName + " (" + callerPkg + ")";

        list.add(new TriggerInfo(
                context.getString(R.string.triggers_cat_caller),
                detail,
                context.getString(R.string.triggers_caller_explanation, callerName),
                TriggerInfo.Severity.HIGH));
        return list;
    }

    private String resolveAppName(String pkg) {
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    // -------------------------------------------------------------------------
    // 1+2+bindings. Services (Foreground + Sticky + Who's binding us)
    //
    // Объединено в один dumpsys-вызов вместо трёх отдельных.
    // -------------------------------------------------------------------------

    private List<TriggerInfo> analyzeServicesAndBindings(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = shellManager.runShellCommandAndGetFullOutput(
                "dumpsys activity services " + packageName);
        if (output == null || output.trim().isEmpty()) return list;

        boolean inTargetPackage = false;
        boolean inBindings = false;
        String currentService = null;
        List<String> binderPackages = new ArrayList<>();

        for (String line : output.split("\n")) {
            String trimmed = line.trim();

            // Вход в блок сервиса нужного пакета
            if (trimmed.contains("ServiceRecord") && trimmed.contains(packageName)) {
                inTargetPackage = true;
                inBindings = false;
                currentService = extractServiceShortName(trimmed, packageName);
                continue;
            }

            // Выход из блока — новый сервис другого пакета
            if (inTargetPackage && trimmed.contains("ServiceRecord") && !trimmed.contains(packageName)) {
                inTargetPackage = false;
                inBindings = false;
                currentService = null;
            }

            if (!inTargetPackage) continue;

            // Foreground service
            if (trimmed.contains("isForeground=true")) {
                list.add(new TriggerInfo(
                        context.getString(R.string.triggers_cat_fg_service),
                        currentService != null ? currentService : packageName,
                        context.getString(R.string.triggers_fg_service_explanation),
                        TriggerInfo.Severity.HIGH));
            }

            // Sticky service (START_STICKY без foreground)
            if ((trimmed.contains("START_STICKY") || trimmed.contains("startRequested=true"))
                    && !trimmed.contains("isForeground=true")) {
                list.add(new TriggerInfo(
                        context.getString(R.string.triggers_cat_sticky),
                        currentService != null ? currentService : packageName,
                        context.getString(R.string.triggers_sticky_explanation),
                        TriggerInfo.Severity.HIGH));
            }

            // Секция биндингов
            if (trimmed.startsWith("Bindings:")) {
                inBindings = true;
                continue;
            }

            // Парсим кто забиндился: ищем packageName биндящего процесса
            if (inBindings) {
                // Строки вида: "* IntentBindRecord{...} (1 binding):"
                // или "ProcessRecord{... com.google.gms/...}"
                Matcher mBinder = Pattern.compile("ProcessRecord\\{[^}]+\\s([\\w.]+)/").matcher(trimmed);
                if (mBinder.find()) {
                    String binderPkg = mBinder.group(1);
                    if (!binderPkg.equals(packageName) && !binderPkg.equals("android")
                            && !binderPackages.contains(binderPkg)) {
                        binderPackages.add(binderPkg);
                    }
                }
                // Альтернативный формат: "client=ProcessRecord{... pkg/...}"
                Matcher mClient = Pattern.compile("client=ProcessRecord\\{[^}]+\\s([\\w.]+)/").matcher(trimmed);
                if (mClient.find()) {
                    String binderPkg = mClient.group(1);
                    if (!binderPkg.equals(packageName) && !binderPkg.equals("android")
                            && !binderPackages.contains(binderPkg)) {
                        binderPackages.add(binderPkg);
                    }
                }
            }
        }

        // Добавляем биндинги как отдельный триггер
        if (!binderPackages.isEmpty()) {
            StringBuilder detail = new StringBuilder();
            StringBuilder explanation = new StringBuilder(
                    context.getString(R.string.triggers_bindings_explanation_base));

            for (int i = 0; i < Math.min(binderPackages.size(), 4); i++) {
                if (i > 0) detail.append(", ");
                String pkg = binderPackages.get(i);
                detail.append(resolveAppName(pkg)).append(" (").append(pkg).append(")");
            }
            if (binderPackages.size() > 4) {
                detail.append(context.getString(R.string.triggers_bindings_overflow, binderPackages.size() - 4));
            }

            // Упоминаем известных «держателей»
            boolean hasGms = binderPackages.stream().anyMatch(p -> p.contains("google.gms") || p.contains("gms"));
            boolean hasPush = binderPackages.stream().anyMatch(p ->
                    p.contains("push") || p.contains("firebase") || p.contains("fcm"));
            if (hasGms)  explanation.append(context.getString(R.string.triggers_bindings_gms_note));
            if (hasPush) explanation.append(context.getString(R.string.triggers_bindings_push_note));

            list.add(new TriggerInfo(
                    context.getString(R.string.triggers_cat_bindings, binderPackages.size()),
                    detail.toString(),
                    explanation.toString(),
                    TriggerInfo.Severity.HIGH));
        }

        return list;
    }

    // -------------------------------------------------------------------------
    // 3. Broadcast Receivers
    // -------------------------------------------------------------------------

    private List<TriggerInfo> analyzeBroadcastReceivers(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = shellManager.runShellCommandAndGetFullOutput(
                "dumpsys package " + packageName);
        if (output == null || output.trim().isEmpty()) return list;

        boolean inReceiverSection = false;
        List<String> actions = new ArrayList<>();

        for (String line : output.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("Receiver #") || trimmed.startsWith("ReceiverInfo{")) {
                inReceiverSection = true;
            }
            if (inReceiverSection && trimmed.startsWith("Action:")) {
                String action = trimmed.replaceFirst("Action:\\s*\"?", "").replace("\"", "").trim();
                action = shortenAction(action);
                if (!actions.contains(action)) actions.add(action);
            }
            if (inReceiverSection && trimmed.startsWith("Service #")) break;
        }

        if (actions.isEmpty()) return list;

        int shown = Math.min(actions.size(), 5);
        StringBuilder detail = new StringBuilder(String.join(", ", actions.subList(0, shown)));
        if (actions.size() > shown) {
            detail.append(context.getString(R.string.triggers_receivers_detail_overflow,
                    actions.size() - shown));
        }

        boolean hasBootReceiver = actions.stream().anyMatch(
                a -> a.contains("BOOT") || a.contains("LOCKED_BOOT"));
        boolean hasConnectivity = actions.stream().anyMatch(
                a -> a.contains("CONNECTIVITY") || a.contains("NETWORK"));

        StringBuilder explanation = new StringBuilder(
                context.getString(R.string.triggers_receivers_explanation_base));
        if (hasBootReceiver) explanation.append(context.getString(R.string.triggers_receivers_explanation_boot));
        if (hasConnectivity) explanation.append(context.getString(R.string.triggers_receivers_explanation_network));

        list.add(new TriggerInfo(
                context.getString(R.string.triggers_cat_receivers, actions.size()),
                detail.toString(),
                explanation.toString(),
                TriggerInfo.Severity.MEDIUM));
        return list;
    }

    private String shortenAction(String action) {
        if (action.startsWith("android.intent.action.")) return action.substring("android.intent.action.".length());
        if (action.startsWith("android.net.conn."))       return action.substring("android.net.conn.".length());
        if (action.startsWith("android.net."))            return action.substring("android.net.".length());
        if (action.startsWith("com.android."))            return action.substring("com.android.".length());
        return action;
    }

    // -------------------------------------------------------------------------
    // 4. Content Providers
    // -------------------------------------------------------------------------

    private List<TriggerInfo> analyzeContentProviders(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = shellManager.runShellCommandAndGetFullOutput(
                "dumpsys package " + packageName);
        if (output == null || output.trim().isEmpty()) return list;

        boolean inProviderSection = false;
        List<String> authorities = new ArrayList<>();

        for (String line : output.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("Provider #")) {
                inProviderSection = true;
            }
            if (inProviderSection && trimmed.startsWith("authority=")) {
                String auth = trimmed.replaceFirst("authority=", "").trim();
                if (auth.startsWith(packageName + ".")) {
                    auth = auth.substring(packageName.length() + 1);
                }
                if (!authorities.contains(auth)) authorities.add(auth);
            }
            if (inProviderSection && trimmed.startsWith("Activity #")) break;
        }

        if (authorities.isEmpty()) return list;

        int shown = Math.min(authorities.size(), 3);
        String detail = String.join(", ", authorities.subList(0, shown));
        if (authorities.size() > shown) detail += " (+" + (authorities.size() - shown) + ")";

        list.add(new TriggerInfo(
                context.getString(R.string.triggers_cat_provider),
                detail,
                context.getString(R.string.triggers_provider_explanation),
                TriggerInfo.Severity.LOW));
        return list;
    }

    // -------------------------------------------------------------------------
    // 5. Sync Adapters
    // -------------------------------------------------------------------------

    private List<TriggerInfo> analyzeSyncAdapters(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = shellManager.runShellCommandAndGetFullOutput(
                "dumpsys content | grep -A3 " + packageName);
        if (output == null || output.trim().isEmpty()) return list;

        int accountCount = 0;
        for (String line : output.split("\n")) {
            if (line.contains(packageName) && line.contains("accountType")) {
                accountCount++;
            }
        }

        if (accountCount == 0) {
            String pkgOutput = shellManager.runShellCommandAndGetFullOutput(
                    "dumpsys package " + packageName + " | grep -i sync");
            if (pkgOutput != null && pkgOutput.toLowerCase().contains("syncadapter")) {
                accountCount = 1;
            }
        }

        if (accountCount == 0) return list;

        list.add(new TriggerInfo(
                context.getString(R.string.triggers_cat_sync),
                context.getString(R.string.triggers_sync_detail, accountCount),
                context.getString(R.string.triggers_sync_explanation),
                TriggerInfo.Severity.MEDIUM));
        return list;
    }

    // -------------------------------------------------------------------------
    // 6. Alarms
    // -------------------------------------------------------------------------

    private List<TriggerInfo> analyzeAlarms(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = shellManager.runShellCommandAndGetFullOutput("dumpsys alarm");
        if (output == null || output.trim().isEmpty()) return list;

        int wakeupCount  = 0;
        int normalCount  = 0;
        long minInterval = Long.MAX_VALUE;
        boolean hasExact = false;

        for (String line : output.split("\n")) {
            if (!line.contains(packageName)) continue;
            boolean isWakeup = line.contains("RTC_WAKEUP") || line.contains("ELAPSED_WAKEUP")
                    || line.contains("*walarm*");
            if (isWakeup) wakeupCount++; else normalCount++;
            if (line.contains("*walarm*")) hasExact = true;
            Matcher m = Pattern.compile("repeatInterval=(\\d+)").matcher(line);
            if (m.find()) {
                long interval = Long.parseLong(m.group(1));
                if (interval > 0 && interval < minInterval) minInterval = interval;
            }
        }

        if (wakeupCount + normalCount == 0) return list;

        StringBuilder detail = new StringBuilder();
        if (wakeupCount > 0) {
            detail.append(context.getString(R.string.triggers_alarms_detail_wakeup, wakeupCount));
        }
        if (normalCount > 0) {
            if (detail.length() > 0) detail.append(", ");
            detail.append(context.getString(R.string.triggers_alarms_detail_normal, normalCount));
        }
        if (minInterval != Long.MAX_VALUE) {
            detail.append(context.getString(R.string.triggers_alarms_detail_repeat,
                    formatInterval(minInterval)));
        }
        if (hasExact) {
            detail.append(context.getString(R.string.triggers_alarms_detail_exact));
        }

        StringBuilder explanation = new StringBuilder();
        if (wakeupCount > 0) {
            explanation.append(context.getString(R.string.triggers_alarms_wakeup_explanation));
            if (minInterval != Long.MAX_VALUE && minInterval < 60_000) {
                explanation.append(context.getString(R.string.triggers_alarms_wakeup_aggressive));
            } else if (minInterval != Long.MAX_VALUE && minInterval < 300_000) {
                explanation.append(context.getString(R.string.triggers_alarms_wakeup_frequent));
            }
        } else {
            explanation.append(context.getString(R.string.triggers_alarms_normal_explanation));
        }
        if (hasExact) {
            explanation.append(context.getString(R.string.triggers_alarms_exact_explanation));
        }

        TriggerInfo.Severity severity = wakeupCount > 0
                ? (minInterval != Long.MAX_VALUE && minInterval < 120_000
                        ? TriggerInfo.Severity.HIGH : TriggerInfo.Severity.MEDIUM)
                : TriggerInfo.Severity.LOW;

        list.add(new TriggerInfo(
                context.getString(R.string.triggers_cat_alarms),
                detail.toString(),
                explanation.toString(),
                severity));
        return list;
    }

    private String formatInterval(long ms) {
        long sec = ms / 1000;
        if (sec < 60)   return context.getString(R.string.triggers_alarms_interval_sec, (int) sec);
        if (sec < 3600) return context.getString(R.string.triggers_alarms_interval_min, (int) (sec / 60));
        return context.getString(R.string.triggers_alarms_interval_hour, (int) (sec / 3600));
    }

    // -------------------------------------------------------------------------
    // 7. Jobs / WorkManager
    // -------------------------------------------------------------------------

    private List<TriggerInfo> analyzeJobs(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = shellManager.runShellCommandAndGetFullOutput("dumpsys jobscheduler");
        if (output == null || output.trim().isEmpty()) return list;

        int pending = 0;
        int running = 0;
        boolean inPending = false;
        boolean inRunning = false;

        for (String line : output.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("Pending queue:"))  { inPending = true;  inRunning = false; continue; }
            if (trimmed.startsWith("Active jobs:"))    { inRunning = true;  inPending = false; continue; }
            if (trimmed.startsWith("Past jobs:"))      { inPending = false; inRunning = false; }
            if ((inPending || inRunning) && trimmed.contains(packageName)) {
                if (inPending) pending++;
                if (inRunning) running++;
            }
        }

        if (pending == 0 && running == 0) return list;

        StringBuilder detail = new StringBuilder();
        if (running > 0) {
            detail.append(context.getString(R.string.triggers_jobs_detail_running, running));
        }
        if (pending > 0) {
            if (detail.length() > 0) detail.append(", ");
            detail.append(context.getString(R.string.triggers_jobs_detail_pending, pending));
        }

        String explanation;
        if (running > 0 && pending > 0) {
            explanation = context.getString(R.string.triggers_jobs_running_and_pending_explanation, running, pending);
        } else if (running > 0) {
            explanation = context.getString(R.string.triggers_jobs_running_explanation, running);
        } else {
            explanation = context.getString(R.string.triggers_jobs_pending_explanation, pending);
        }

        list.add(new TriggerInfo(
                context.getString(R.string.triggers_cat_jobs),
                detail.toString(),
                explanation,
                running > 0 ? TriggerInfo.Severity.HIGH : TriggerInfo.Severity.MEDIUM));
        return list;
    }

    // -------------------------------------------------------------------------
    // 8. Doze exemption
    // -------------------------------------------------------------------------

    private List<TriggerInfo> analyzeDozeExemption(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = shellManager.runShellCommandAndGetFullOutput(
                "dumpsys deviceidle | grep -E 'whitelist|except'");
        if (output == null || output.trim().isEmpty()) return list;

        for (String line : output.split("\n")) {
            if (!line.contains(packageName)) continue;
            boolean isSys = line.contains("sys-");
            list.add(new TriggerInfo(
                    context.getString(R.string.triggers_cat_doze),
                    context.getString(isSys ? R.string.triggers_doze_sys_detail : R.string.triggers_doze_user_detail),
                    context.getString(isSys ? R.string.triggers_doze_sys_explanation : R.string.triggers_doze_user_explanation),
                    TriggerInfo.Severity.HIGH));
            break;
        }
        return list;
    }

    // -------------------------------------------------------------------------
    // 9. WakeLocks
    // -------------------------------------------------------------------------

    private List<TriggerInfo> analyzeWakelocks(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();

        String uidOutput = shellManager.runShellCommandAndGetFullOutput(
                "dumpsys package " + packageName + " | grep userId=");
        if (uidOutput == null) return list;

        String uid = null;
        for (String line : uidOutput.split("\n")) {
            Matcher m = Pattern.compile("userId=(\\d+)").matcher(line);
            if (m.find()) { uid = m.group(1); break; }
        }
        if (uid == null) return list;

        String powerOutput = shellManager.runShellCommandAndGetFullOutput("dumpsys power");
        if (powerOutput == null || powerOutput.trim().isEmpty()) return list;

        if (!powerOutput.contains("Wake Locks:")) {
            Log.d(TAG, "dumpsys power: Wake Locks section not available");
            return list;
        }

        boolean inWakeLockSection = false;
        for (String line : powerOutput.split("\n")) {
            if (line.trim().startsWith("Wake Locks:"))                            { inWakeLockSection = true; continue; }
            if (inWakeLockSection && line.trim().startsWith("Suspend Blockers:")) break;
            if (!inWakeLockSection || !line.contains("uid=" + uid))               continue;

            int typeResId, explainResId;
            if (line.contains("PARTIAL_WAKE_LOCK")) {
                typeResId    = R.string.triggers_wakelock_partial_type;
                explainResId = R.string.triggers_wakelock_partial_explain;
            } else if (line.contains("FULL_WAKE_LOCK")) {
                typeResId    = R.string.triggers_wakelock_full_type;
                explainResId = R.string.triggers_wakelock_full_explain;
            } else {
                typeResId    = R.string.triggers_wakelock_generic_type;
                explainResId = R.string.triggers_wakelock_generic_explain;
            }

            String tag = "";
            Matcher tagMatcher = Pattern.compile("'([^']+)'").matcher(line);
            if (tagMatcher.find()) tag = tagMatcher.group(1);

            String held = "";
            Matcher timeMatcher = Pattern.compile("(\\d+m\\s*\\d+s|\\d+s)").matcher(line);
            if (timeMatcher.find()) {
                held = context.getString(R.string.triggers_wakelock_detail_held, timeMatcher.group(1));
            }

            String type   = context.getString(typeResId);
            String detail = type + (tag.isEmpty() ? "" : " · " + tag) + held;
            String explanation = context.getString(R.string.triggers_wakelock_explanation,
                    context.getString(explainResId));

            list.add(new TriggerInfo(
                    context.getString(R.string.triggers_cat_wakelock),
                    detail,
                    explanation,
                    TriggerInfo.Severity.HIGH));
        }
        return list;
    }

    // =========================================================================
    // NEW DIAGNOSTIC METHODS
    // =========================================================================

    // -------------------------------------------------------------------------
    // 10. Broadcast History (цепной запуск)
    //
    // dumpsys activity broadcasts history — показывает очередь доставленных
    // broadcast-ов. Ищем записи где targetComp или pkg совпадает с нашим
    // пакетом, и вытаскиваем кто был отправителем (callerPackage / pid).
    // -------------------------------------------------------------------------

    private List<TriggerInfo> analyzeBroadcastHistory(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = shellManager.runShellCommandAndGetFullOutput(
                "dumpsys activity broadcasts history");
        if (output == null || output.trim().isEmpty()) return list;

        // Структура: блоки вида
        //   BroadcastRecord{... act=android.intent.action.BOOT_COMPLETED ...}
        //     callerPackage=com.example.launcher
        //     receivers: ...
        //       ResolveInfo{... com.our.pkg/.OurReceiver ...}

        List<String> chainCallers = new ArrayList<>(); // кто запускал наш пакет
        List<String> chainActions = new ArrayList<>();  // через какой action

        String[] lines = output.split("\n");
        String currentAction = null;
        String currentCaller = null;
        boolean relevantBroadcast = false;

        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();

            // Начало нового BroadcastRecord
            if (trimmed.startsWith("BroadcastRecord{") || trimmed.contains("act=")) {
                // Сброс состояния предыдущей записи
                if (relevantBroadcast && currentCaller != null && currentAction != null) {
                    if (!chainCallers.contains(currentCaller)) {
                        chainCallers.add(currentCaller);
                        chainActions.add(currentAction);
                    }
                }
                relevantBroadcast = false;
                currentCaller = null;

                // Извлекаем action
                Matcher mAct = Pattern.compile("act=([\\w.]+)").matcher(trimmed);
                currentAction = mAct.find() ? shortenAction(mAct.group(1)) : null;
            }

            // Кто отправил
            if (trimmed.startsWith("callerPackage=")) {
                String caller = trimmed.replace("callerPackage=", "").trim();
                if (!caller.equals(packageName) && !caller.equals("android") && !caller.equals("null")) {
                    currentCaller = caller;
                }
            }
            // Альтернатива: callerApp
            if (trimmed.startsWith("callerApp=") && currentCaller == null) {
                Matcher m = Pattern.compile("callerApp=ProcessRecord\\{[^}]+\\s([\\w.]+)/").matcher(trimmed);
                if (m.find()) {
                    String caller = m.group(1);
                    if (!caller.equals(packageName) && !caller.equals("android")) {
                        currentCaller = caller;
                    }
                }
            }

            // Проверяем — наш пакет в списке получателей
            if (trimmed.contains(packageName) && !trimmed.startsWith("BroadcastRecord")) {
                relevantBroadcast = true;
            }
        }

        // Последняя запись
        if (relevantBroadcast && currentCaller != null && currentAction != null) {
            if (!chainCallers.contains(currentCaller)) {
                chainCallers.add(currentCaller);
                chainActions.add(currentAction);
            }
        }

        if (chainCallers.isEmpty()) return list;

        // Формируем detail: "AppName (pkg) via ACTION, ..."
        StringBuilder detail = new StringBuilder();
        int shown = Math.min(chainCallers.size(), 3);
        for (int i = 0; i < shown; i++) {
            if (i > 0) detail.append("\n");
            String pkg = chainCallers.get(i);
            String action = chainActions.get(i);
            detail.append(resolveAppName(pkg))
                    .append(" (").append(pkg).append(")")
                    .append(" → ").append(action != null ? action : "?");
        }
        if (chainCallers.size() > shown) {
            detail.append(context.getString(R.string.triggers_broadcast_history_overflow,
                    chainCallers.size() - shown));
        }

        list.add(new TriggerInfo(
                context.getString(R.string.triggers_cat_broadcast_history),
                detail.toString(),
                context.getString(R.string.triggers_broadcast_history_explanation),
                TriggerInfo.Severity.HIGH));
        return list;
    }

    // -------------------------------------------------------------------------
    // 11. Pending Intents
    //
    // dumpsys activity intents — список зарегистрированных PendingIntent-ов.
    // Если наш пакет держит PendingIntent-ы, это означает что система может
    // разбудить его в любой момент через AlarmManager / уведомления / etc.
    // -------------------------------------------------------------------------

    private List<TriggerInfo> analyzePendingIntents(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = shellManager.runShellCommandAndGetFullOutput(
                "dumpsys activity intents");
        if (output == null || output.trim().isEmpty()) return list;

        int activityIntents  = 0;
        int serviceIntents   = 0;
        int broadcastIntents = 0;
        List<String> intentDetails = new ArrayList<>();

        for (String line : output.split("\n")) {
            if (!line.contains(packageName)) continue;

            String trimmed = line.trim();

            // Определяем тип PendingIntent
            if (trimmed.contains("type=activity") || trimmed.contains("Activity")) {
                activityIntents++;
            } else if (trimmed.contains("type=service") || trimmed.contains("Service")) {
                serviceIntents++;
            } else if (trimmed.contains("type=broadcast") || trimmed.contains("Broadcast")) {
                broadcastIntents++;
            }

            // Извлекаем action если есть
            Matcher mAct = Pattern.compile("act=([\\w.]+)").matcher(trimmed);
            if (mAct.find()) {
                String action = shortenAction(mAct.group(1));
                if (!intentDetails.contains(action)) intentDetails.add(action);
            }

            // Кто создал PendingIntent (creatorPackage)
            Matcher mCreator = Pattern.compile("creator=\\[([\\w.]+)\\]").matcher(trimmed);
            if (!mCreator.find()) {
                mCreator = Pattern.compile("uid=\\d+ ([\\w.]+)").matcher(trimmed);
            }
        }

        int total = activityIntents + serviceIntents + broadcastIntents;
        if (total == 0) return list;

        StringBuilder detail = new StringBuilder();
        if (activityIntents  > 0) detail.append(context.getString(R.string.triggers_pending_activity,  activityIntents));
        if (serviceIntents   > 0) {
            if (detail.length() > 0) detail.append(", ");
            detail.append(context.getString(R.string.triggers_pending_service, serviceIntents));
        }
        if (broadcastIntents > 0) {
            if (detail.length() > 0) detail.append(", ");
            detail.append(context.getString(R.string.triggers_pending_broadcast, broadcastIntents));
        }
        if (!intentDetails.isEmpty()) {
            detail.append(" (").append(String.join(", ", intentDetails.subList(0, Math.min(intentDetails.size(), 3)))).append(")");
        }

        list.add(new TriggerInfo(
                context.getString(R.string.triggers_cat_pending_intents, total),
                detail.toString(),
                context.getString(R.string.triggers_pending_explanation),
                TriggerInfo.Severity.MEDIUM));
        return list;
    }

    // -------------------------------------------------------------------------
    // 12. Boot Receivers (автозапуск при загрузке)
    //
    // cmd package query-receivers --action android.intent.action.BOOT_COMPLETED
    // Точечно проверяем: есть ли у пакета receiver на BOOT_COMPLETED или
    // LOCKED_BOOT_COMPLETED. Работает через Shizuku.
    // -------------------------------------------------------------------------

    private List<TriggerInfo> analyzeBootReceivers(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();

        boolean hasBoot = false;
        boolean hasLockedBoot = false;

        String bootOutput = shellManager.runShellCommandAndGetFullOutput(
                "cmd package query-receivers --action android.intent.action.BOOT_COMPLETED");
        if (bootOutput != null && bootOutput.contains(packageName)) {
            hasBoot = true;
        }

        String lockedBootOutput = shellManager.runShellCommandAndGetFullOutput(
                "cmd package query-receivers --action android.intent.action.LOCKED_BOOT_COMPLETED");
        if (lockedBootOutput != null && lockedBootOutput.contains(packageName)) {
            hasLockedBoot = true;
        }

        if (!hasBoot && !hasLockedBoot) return list;

        String detail;
        String explanation;

        if (hasBoot && hasLockedBoot) {
            detail = context.getString(R.string.triggers_boot_detail_both);
            explanation = context.getString(R.string.triggers_boot_explanation_both);
        } else if (hasLockedBoot) {
            detail = context.getString(R.string.triggers_boot_detail_locked);
            explanation = context.getString(R.string.triggers_boot_explanation_locked);
        } else {
            detail = context.getString(R.string.triggers_boot_detail_normal);
            explanation = context.getString(R.string.triggers_boot_explanation_normal);
        }

        list.add(new TriggerInfo(
                context.getString(R.string.triggers_cat_boot),
                detail,
                explanation,
                TriggerInfo.Severity.HIGH));
        return list;
    }

    // -------------------------------------------------------------------------
    // 13. App Standby Bucket
    //
    // am get-standby-bucket <pkg> — возвращает числовой bucket или его имя.
    // ACTIVE=10, WORKING_SET=20, FREQUENT=30, RARE=40, RESTRICTED=45, NEVER=50
    // Если приложение в ACTIVE/WORKING_SET без видимой причины — подозрительно.
    // -------------------------------------------------------------------------

    private List<TriggerInfo> analyzeStandbyBucket(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = shellManager.runShellCommandAndGetFullOutput(
                "am get-standby-bucket " + packageName);
        if (output == null || output.trim().isEmpty()) return list;

        String trimmed = output.trim();

        // Парсим: может быть число ("10") или строка ("ACTIVE")
        int bucketValue = -1;
        String bucketName;

        try {
            bucketValue = Integer.parseInt(trimmed);
        } catch (NumberFormatException ignored) {
            // Некоторые ROM-ы возвращают имя
        }

        if (bucketValue == -1) {
            // Попробуем найти число в строке (например "Standby bucket: 10")
            Matcher m = Pattern.compile("(\\d+)").matcher(trimmed);
            if (m.find()) bucketValue = Integer.parseInt(m.group(1));
        }

        if (bucketValue == -1) return list; // не распознали

        // Маппинг bucket → имя и severity
        if (bucketValue <= 10) {
            bucketName = "ACTIVE";
        } else if (bucketValue <= 20) {
            bucketName = "WORKING_SET";
        } else if (bucketValue <= 30) {
            bucketName = "FREQUENT";
        } else if (bucketValue <= 40) {
            bucketName = "RARE";
        } else if (bucketValue <= 45) {
            bucketName = "RESTRICTED";
        } else {
            bucketName = "NEVER";
        }

        // RARE / RESTRICTED / NEVER — система сама ограничила, это норма
        // ACTIVE / WORKING_SET — интересно, стоит показать
        TriggerInfo.Severity severity;
        String explanation;

        if (bucketValue <= 10) {
            severity = TriggerInfo.Severity.HIGH;
            explanation = context.getString(R.string.triggers_bucket_active_explanation);
        } else if (bucketValue <= 20) {
            severity = TriggerInfo.Severity.MEDIUM;
            explanation = context.getString(R.string.triggers_bucket_working_set_explanation);
        } else if (bucketValue <= 30) {
            severity = TriggerInfo.Severity.LOW;
            explanation = context.getString(R.string.triggers_bucket_frequent_explanation);
        } else {
            severity = TriggerInfo.Severity.INFO;
            explanation = context.getString(R.string.triggers_bucket_rare_explanation);
        }

        list.add(new TriggerInfo(
                context.getString(R.string.triggers_cat_bucket),
                bucketName,
                explanation,
                severity));
        return list;
    }

    // -------------------------------------------------------------------------
    // 14. Battery Stats (история активности)
    //
    // dumpsys batterystats <pkg> — даёт историческую картину:
    // сколько раз держал wakelock, сколько было будильников, job-ов.
    // Дополняет текущие analyzeWakelocks/analyzeAlarms историческими данными.
    // -------------------------------------------------------------------------

    private List<TriggerInfo> analyzeBatteryStats(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        String output = shellManager.runShellCommandAndGetFullOutput(
                "dumpsys batterystats " + packageName);
        if (output == null || output.trim().isEmpty()) return list;

        long wakelockTimeMs  = 0;
        int  wakelockCount   = 0;
        int  wakeupAlarms    = 0;
        int  jobsRun         = 0;
        int  syncs           = 0;

        // Паттерны для секций batterystats
        // Wakelock: "Wakelock <name>: X realtime (X times)"
        Pattern wakelockPattern = Pattern.compile(
                "Wakelock\\s+\\S+:\\s+(\\d+)ms realtime.*?\\((\\d+)\\s+times\\)", Pattern.CASE_INSENSITIVE);
        // Alarm wakeups: "Wakeup alarm <action>: X times"
        Pattern alarmPattern = Pattern.compile(
                "Wakeup alarm.*?:\\s*(\\d+)\\s+times", Pattern.CASE_INSENSITIVE);
        // Jobs: "Job <name>: X realtime (X times)"
        Pattern jobPattern = Pattern.compile(
                "Job\\s+\\S+:\\s+\\d+ms realtime.*?\\((\\d+)\\s+times\\)", Pattern.CASE_INSENSITIVE);
        // Syncs
        Pattern syncPattern = Pattern.compile(
                "Sync\\s+\\S+:\\s+\\d+ms realtime.*?\\((\\d+)\\s+times\\)", Pattern.CASE_INSENSITIVE);

        for (String line : output.split("\n")) {
            Matcher m;

            m = wakelockPattern.matcher(line);
            if (m.find()) {
                wakelockTimeMs += Long.parseLong(m.group(1));
                wakelockCount  += Integer.parseInt(m.group(2));
                continue;
            }
            m = alarmPattern.matcher(line);
            if (m.find()) {
                wakeupAlarms += Integer.parseInt(m.group(1));
                continue;
            }
            m = jobPattern.matcher(line);
            if (m.find()) {
                jobsRun += Integer.parseInt(m.group(1));
                continue;
            }
            m = syncPattern.matcher(line);
            if (m.find()) {
                syncs += Integer.parseInt(m.group(1));
            }
        }

        if (wakelockCount == 0 && wakeupAlarms == 0 && jobsRun == 0 && syncs == 0) return list;

        StringBuilder detail = new StringBuilder();
        if (wakelockCount > 0) {
            detail.append(context.getString(R.string.triggers_batterystats_wakelock,
                    wakelockCount, formatBatteryStatsDuration(wakelockTimeMs)));
        }
        if (wakeupAlarms > 0) {
            if (detail.length() > 0) detail.append(", ");
            detail.append(context.getString(R.string.triggers_batterystats_alarms, wakeupAlarms));
        }
        if (jobsRun > 0) {
            if (detail.length() > 0) detail.append(", ");
            detail.append(context.getString(R.string.triggers_batterystats_jobs, jobsRun));
        }
        if (syncs > 0) {
            if (detail.length() > 0) detail.append(", ");
            detail.append(context.getString(R.string.triggers_batterystats_syncs, syncs));
        }

        // Severity по суммарной нагрузке
        TriggerInfo.Severity severity;
        if (wakeupAlarms > 50 || wakelockTimeMs > 600_000L) {
            severity = TriggerInfo.Severity.HIGH;
        } else if (wakeupAlarms > 10 || wakelockTimeMs > 60_000L || jobsRun > 20) {
            severity = TriggerInfo.Severity.MEDIUM;
        } else {
            severity = TriggerInfo.Severity.LOW;
        }

        list.add(new TriggerInfo(
                context.getString(R.string.triggers_cat_batterystats),
                detail.toString(),
                context.getString(R.string.triggers_batterystats_explanation),
                severity));
        return list;
    }

    private String formatBatteryStatsDuration(long ms) {
        long sec = ms / 1000;
        if (sec < 60)   return sec + context.getString(R.string.time_unit_sec);
        if (sec < 3600) return (sec / 60) + context.getString(R.string.time_unit_min);
        return (sec / 3600) + context.getString(R.string.time_unit_hour);
    }

    // -------------------------------------------------------------------------
    // 15. Network Activity
    //
    // dumpsys netstats detail — смотрим есть ли у пакета активный трафик.
    // Косвенный признак: приложение активно общается с сервером в фоне.
    // Также проверяем dumpsys connectivity на активные соединения.
    // -------------------------------------------------------------------------

    private List<TriggerInfo> analyzeNetworkActivity(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();

        // Сначала получаем UID пакета
        String uidOutput = shellManager.runShellCommandAndGetFullOutput(
                "dumpsys package " + packageName + " | grep userId=");
        if (uidOutput == null) return list;

        String uid = null;
        for (String line : uidOutput.split("\n")) {
            Matcher m = Pattern.compile("userId=(\\d+)").matcher(line);
            if (m.find()) { uid = m.group(1); break; }
        }
        if (uid == null) return list;

        // Проверяем netstats: есть ли записи для этого UID с ненулевым трафиком
        String netstatsOutput = shellManager.runShellCommandAndGetFullOutput(
                "dumpsys netstats detail | grep -A5 uid=" + uid);
        if (netstatsOutput == null || netstatsOutput.trim().isEmpty()) {
            // Запасной вариант — просто grep пакета
            netstatsOutput = shellManager.runShellCommandAndGetFullOutput(
                    "dumpsys netstats | grep " + packageName);
        }

        long rxBytes = 0;
        long txBytes = 0;
        boolean hasActiveConnection = false;

        if (netstatsOutput != null) {
            // Паттерн: rxBytes=12345 txBytes=6789
            Matcher mRx = Pattern.compile("rxBytes=(\\d+)").matcher(netstatsOutput);
            Matcher mTx = Pattern.compile("txBytes=(\\d+)").matcher(netstatsOutput);
            while (mRx.find()) rxBytes += Long.parseLong(mRx.group(1));
            while (mTx.find()) txBytes += Long.parseLong(mTx.group(1));
        }

        // Проверяем активные сетевые соединения через connectivity / netstat
        String connOutput = shellManager.runShellCommandAndGetFullOutput(
                "cat /proc/net/tcp6 2>/dev/null | grep " + uid
                + " ; cat /proc/net/tcp 2>/dev/null | grep " + uid);
        if (connOutput != null && !connOutput.trim().isEmpty()) {
            // Считаем ESTABLISHED соединения (state=01 в /proc/net/tcp)
            int established = 0;
            for (String line : connOutput.split("\n")) {
                // Формат /proc/net/tcp: ... st=01 ... uid=XXXX
                // st=01 означает ESTABLISHED
                if (line.contains(" 01 ") && line.contains(uid)) {
                    established++;
                }
            }
            if (established > 0) hasActiveConnection = true;
        }

        // Если нет ни трафика ни соединений — не показываем
        long totalBytes = rxBytes + txBytes;
        if (totalBytes == 0 && !hasActiveConnection) return list;

        StringBuilder detail = new StringBuilder();
        if (hasActiveConnection) {
            detail.append(context.getString(R.string.triggers_network_active_connections));
        }
        if (totalBytes > 0) {
            if (detail.length() > 0) detail.append(", ");
            detail.append(context.getString(R.string.triggers_network_traffic,
                    formatBytes(rxBytes), formatBytes(txBytes)));
        }

        TriggerInfo.Severity severity = hasActiveConnection
                ? TriggerInfo.Severity.HIGH
                : (totalBytes > 1024 * 1024 ? TriggerInfo.Severity.MEDIUM : TriggerInfo.Severity.LOW);

        list.add(new TriggerInfo(
                context.getString(R.string.triggers_cat_network),
                detail.toString(),
                context.getString(R.string.triggers_network_explanation),
                severity));
        return list;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024)             return bytes + " B";
        if (bytes < 1024 * 1024)      return (bytes / 1024) + " KB";
        if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)) + " MB";
        return (bytes / (1024 * 1024 * 1024)) + " GB";
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String extractServiceShortName(String line, String packageName) {
        Matcher m = Pattern.compile("ServiceRecord\\{[^}]+\\s([\\w./]+)\\}").matcher(line);
        if (!m.find()) return null;
        String fullName = m.group(1);
        if (fullName.contains("/")) {
            String className = fullName.substring(fullName.indexOf('/') + 1);
            if (className.startsWith(".")) return className.substring(1);
            if (className.startsWith(packageName + ".")) return className.substring(packageName.length() + 1);
            return className;
        }
        return fullName;
    }
}
