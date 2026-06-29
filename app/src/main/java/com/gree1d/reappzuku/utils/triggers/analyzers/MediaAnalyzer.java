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

public class MediaAnalyzer {

    private static final String FILE_NAME = "MediaAnalyzer";

    private final AppTriggersAnalyzer analyzer;

    public MediaAnalyzer(AppTriggersAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

// ---- CONST:BLE_CALLBACK_PAT ----
    private static final Pattern BLE_CALLBACK_PAT    = Pattern.compile("scanCallbackType=([\\w_]+)");


// ---- CONST:BLE_REPORT_DELAY_PAT ----
    private static final Pattern BLE_REPORT_DELAY_PAT = Pattern.compile("reportDelay=(\\d+)");


// ---- analyzeAudioFocus ----
    public List<TriggerInfo> analyzeAudioFocus(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();


        try {
            String audioOut = analyzer.getShellManager().runShellCommandAndGetFullOutput("dumpsys audio");
            if (audioOut != null) {
                boolean inFocusSection = false;
                String  focusType      = null;
                String  focusStream    = null;

                Pattern focusTypePat   = Pattern.compile(
                        "focusGain=([\\w_]+)", Pattern.CASE_INSENSITIVE);
                Pattern streamTypePat  = Pattern.compile(
                        "stream=(\\d+)");

                for (String line : audioOut.split("\n")) {
                    String t = line.trim();
                    if (t.startsWith("Audio Focus stack")) { inFocusSection = true; continue; }
                    if (inFocusSection && t.startsWith("---")) break;
                    if (!inFocusSection) continue;

                    if (!t.contains(packageName)) continue;

                    Matcher mFt = focusTypePat.matcher(t);
                    if (mFt.find() && focusType == null)
                        focusType = mapAudioFocusGain(mFt.group(1));

                    Matcher mSt = streamTypePat.matcher(t);
                    if (mSt.find() && focusStream == null)
                        focusStream = mapAudioStream(Integer.parseInt(mSt.group(1)));
                }

                if (focusType != null) {
                    String detail = focusType
                            + (focusStream != null ? " · stream:" + focusStream : "");
                    boolean isGain = focusType.contains("GAIN");
                    AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": analyzeAudioFocus: found focusType=" + focusType + " stream=" + focusStream);
                    list.add(new TriggerInfo(TriggerInfo.Group.ACTIVE_NOW,
                            AppTriggersAnalyzer.KEY_CAT_AUDIO_FOCUS,
                            analyzer.getContext().getString(R.string.triggers_cat_audio_focus),
                            detail,
                            analyzer.getContext().getString(isGain
                                    ? R.string.triggers_audio_focus_gain_explanation
                                    : R.string.triggers_audio_focus_duck_explanation),
                            isGain ? TriggerInfo.Severity.HIGH : TriggerInfo.Severity.MEDIUM));
                }
            }
        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": analyzeAudioFocus/audio failed", e); }


        try {
            String msOut = analyzer.getShellManager().runShellCommandAndGetFullOutput("dumpsys media_session");
            if (msOut != null) {
                boolean inSession  = false;
                String  sessionTag = null;
                String  state      = null;

                Pattern tagPat    = Pattern.compile("tag=([^,\\s]+)");
                Pattern statePat  = Pattern.compile("state=(\\d+)");

                for (String line : msOut.split("\n")) {
                    String t = line.trim();
                    if (t.contains("package=" + packageName)
                            || t.contains("packageName=" + packageName)) {
                        inSession = true; sessionTag = null; state = null;
                    }
                    if (inSession && t.contains("package=")
                            && !t.contains(packageName)) inSession = false;
                    if (!inSession) continue;

                    Matcher mTag = tagPat.matcher(t);
                    if (mTag.find() && sessionTag == null)
                        sessionTag = analyzer.trimTo(mTag.group(1), 30);

                    Matcher mSt = statePat.matcher(t);
                    if (mSt.find() && state == null)
                        state = mapMediaSessionState(Integer.parseInt(mSt.group(1)));
                }

                if (state != null) {
                    String detail = (sessionTag != null ? sessionTag + " · " : "") + state;
                    boolean isPlaying = "PLAYING".equals(state);
                    AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": analyzeAudioFocus: media_session state=" + state + " tag=" + sessionTag);

                    boolean alreadyReported = list.stream()
                            .anyMatch(i -> AppTriggersAnalyzer.KEY_CAT_AUDIO_FOCUS.equals(i.key));
                    if (!alreadyReported || !isPlaying) {
                        list.add(new TriggerInfo(TriggerInfo.Group.ACTIVE_NOW,
                                AppTriggersAnalyzer.KEY_CAT_MEDIA_SESSION,
                                analyzer.getContext().getString(R.string.triggers_cat_media_session),
                                detail,
                                analyzer.getContext().getString(isPlaying
                                        ? R.string.triggers_media_session_playing_explanation
                                        : R.string.triggers_media_session_paused_explanation),
                                isPlaying ? TriggerInfo.Severity.HIGH : TriggerInfo.Severity.MEDIUM));
                    }
                }
            }
        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": analyzeAudioFocus/media_session failed", e); }

        return list;
    }

// ---- mapAudioFocusGain ----
    public String mapAudioFocusGain(String raw) {
        switch (raw.toUpperCase()) {
            case "AUDIOFOCUS_GAIN":               return "GAIN (exclusive)";
            case "AUDIOFOCUS_GAIN_TRANSIENT":     return "GAIN_TRANSIENT";
            case "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK": return "GAIN_TRANSIENT_DUCK";
            case "AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE": return "GAIN_EXCLUSIVE";
            case "AUDIOFOCUS_LOSS":               return "LOSS";
            case "AUDIOFOCUS_LOSS_TRANSIENT":     return "LOSS_TRANSIENT";
            case "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK": return "LOSS_DUCK";
            default: return raw;
        }
    }

// ---- mapAudioStream ----
    public String mapAudioStream(int stream) {
        switch (stream) {
            case 0:  return "VOICE_CALL";
            case 1:  return "SYSTEM";
            case 2:  return "RING";
            case 3:  return "MUSIC";
            case 4:  return "ALARM";
            case 5:  return "NOTIFICATION";
            case 6:  return "BLUETOOTH_SCO";
            case 10: return "ACCESSIBILITY";
            default: return "STREAM_" + stream;
        }
    }

// ---- mapMediaSessionState ----
    public String mapMediaSessionState(int state) {
        switch (state) {
            case 0:  return "NONE";
            case 1:  return "STOPPED";
            case 2:  return "PAUSED";
            case 3:  return "PLAYING";
            case 4:  return "FAST_FORWARDING";
            case 5:  return "REWINDING";
            case 6:  return "BUFFERING";
            case 7:  return "ERROR";
            case 8:  return "CONNECTING";
            case 9:  return "SKIPPING_TO_PREVIOUS";
            case 10: return "SKIPPING_TO_NEXT";
            case 11: return "SKIPPING_TO_QUEUE_ITEM";
            default: return "STATE_" + state;
        }
    }

// ---- analyzeBluetooth ----
    public List<TriggerInfo> analyzeBluetooth(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();


        try {
            String btOut = analyzer.getShellManager().runShellCommandAndGetFullOutput("dumpsys bluetooth_manager");
            if (btOut != null) {
                boolean inScan  = false;
                int     scanCnt = 0;
                String  scanMode = null;

                Pattern modePat = Pattern.compile("scanMode=(\\w+)", Pattern.CASE_INSENSITIVE);

                for (String line : btOut.split("\n")) {
                    String t = line.trim();
                    if (t.startsWith("Scan clients:") || t.startsWith("Active scan clients"))
                        { inScan = true; continue; }
                    if (inScan && t.startsWith("---")) break;
                    if (!inScan) continue;

                    if (t.contains(packageName)) {
                        scanCnt++;
                        Matcher m = modePat.matcher(t);
                        if (m.find() && scanMode == null) scanMode = m.group(1);

                        if (analyzer.apiLevel >= Build.VERSION_CODES.S
                                && analyzer.apiLevel <= Build.VERSION_CODES.TIRAMISU) {
                            Matcher mCb = BLE_CALLBACK_PAT.matcher(t);
                            if (mCb.find() && mCb.group(1).contains("ALL_MATCHES")) {
                                if (scanMode == null) scanMode = "ALL_MATCHES";
                                else scanMode += "+ALL_MATCHES";
                            }
                            Matcher mDelay = BLE_REPORT_DELAY_PAT.matcher(t);
                            if (mDelay.find()) {
                                long delay = Long.parseLong(mDelay.group(1));
                                String suffix = delay == 0 ? "+report:instant"
                                        : "+report:" + (delay / 1000) + "s";
                                scanMode = (scanMode != null ? scanMode : "") + suffix;
                            }
                        }
                    }
                }

                if (scanCnt > 0) {
                    String detail = analyzer.getContext().getString(R.string.triggers_ble_scan_count, scanCnt)
                            + (scanMode != null ? " · mode:" + scanMode : "");
                    boolean isLowLatency = scanMode != null
                            && scanMode.toUpperCase().contains("LOW_LATENCY");
                    AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": Bluetooth/manager - BLE scan found: count=" + scanCnt + " mode=" + scanMode);
                    list.add(new TriggerInfo(TriggerInfo.Group.ACTIVE_NOW,
                            AppTriggersAnalyzer.KEY_CAT_BLE_SCAN,
                            analyzer.getContext().getString(R.string.triggers_cat_ble_scan),
                            detail,
                            analyzer.getContext().getString(isLowLatency
                                    ? R.string.triggers_ble_scan_low_latency_explanation
                                    : R.string.triggers_ble_scan_explanation),
                            isLowLatency ? TriggerInfo.Severity.HIGH : TriggerInfo.Severity.MEDIUM));
                }
            }
        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": Bluetooth/manager failed", e); }


        try {
            String gattOut = analyzer.getShellManager().runShellCommandAndGetFullOutput("dumpsys gatt");
            if (gattOut != null) {
                int     connCnt    = 0;
                boolean inConn     = false;
                List<String> addrs = new ArrayList<>();

                Pattern addrPat = Pattern.compile(
                        "address=([0-9A-Fa-f:]{17})", Pattern.CASE_INSENSITIVE);

                for (String line : gattOut.split("\n")) {
                    String t = line.trim();
                    if (t.startsWith("GATT Connections:") || t.startsWith("Connections:"))
                        { inConn = true; continue; }
                    if (inConn && t.startsWith("---")) break;
                    if (!inConn) continue;

                    if (!t.contains(packageName)) continue;
                    connCnt++;
                    Matcher m = addrPat.matcher(t);
                    if (m.find() && addrs.size() < 3) addrs.add(m.group(1));
                }

                if (connCnt > 0) {
                    AppDebugManager.d(Category.TRIGGERS, FILE_NAME + ": Bluetooth/gatt - connections found: count=" + connCnt + " addrs=" + addrs);
                    StringBuilder detail = new StringBuilder(
                            analyzer.getContext().getString(R.string.triggers_gatt_conn_count, connCnt));
                    if (!addrs.isEmpty())
                        detail.append(": ").append(String.join(", ", addrs));
                    list.add(new TriggerInfo(TriggerInfo.Group.ACTIVE_NOW,
                            AppTriggersAnalyzer.KEY_CAT_GATT,
                                analyzer.getContext().getString(R.string.triggers_cat_gatt),
                            detail.toString(),
                            analyzer.getContext().getString(R.string.triggers_gatt_explanation),
                            TriggerInfo.Severity.HIGH));
                }
            }
        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": Bluetooth/gatt failed", e); }

        if (analyzer.apiLevel >= Build.VERSION_CODES.S && analyzer.apiLevel <= Build.VERSION_CODES.TIRAMISU) {
            list.addAll(analyzeBluetoothPermissions(packageName));
        }

        return list;
    }

// ---- analyzeBluetoothPermissions ----
    public List<TriggerInfo> analyzeBluetoothPermissions(String packageName) {
        List<TriggerInfo> list = new ArrayList<>();
        try {
            String pkgOut = analyzer.getShellManager().runShellCommandAndGetFullOutput(
                    "dumpsys package " + packageName
                    + " | grep -E 'BLUETOOTH_SCAN|BLUETOOTH_CONNECT|NEARBY_DEVICES'");
            if (pkgOut == null) return list;

            boolean hasScan    = pkgOut.contains("BLUETOOTH_SCAN")    && pkgOut.contains("granted=true");
            boolean hasConnect = pkgOut.contains("BLUETOOTH_CONNECT") && pkgOut.contains("granted=true");

            if (hasScan || hasConnect) {
                String detail = (hasScan ? "BLUETOOTH_SCAN " : "")
                        + (hasConnect ? "BLUETOOTH_CONNECT" : "");
                list.add(new TriggerInfo(TriggerInfo.Group.OTHER,
                        AppTriggersAnalyzer.KEY_CAT_BLE_SCAN,
                        analyzer.getContext().getString(R.string.triggers_cat_ble_scan),
                        detail.trim() + " (Android 12+ permissions)",
                        analyzer.getContext().getString(R.string.triggers_bt_permissions_explanation),                        
                        TriggerInfo.Severity.LOW));
            }
        } catch (Exception e) { AppDebugManager.e(Category.TRIGGERS, FILE_NAME + ": bt permissions check failed", e); }
        return list;
    }

}
