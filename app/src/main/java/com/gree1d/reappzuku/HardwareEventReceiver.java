package com.gree1d.reappzuku;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.gree1d.reappzuku.AppConstants.*;

public class HardwareEventReceiver extends BroadcastReceiver {

    private static final String TAG = "HardwareEventReceiver";
    private static final long TRIGGER_DELAY_MS = 10_000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();
        Log.d(TAG, "onReceive: " + action);

        boolean relevant = false;
        String eventDescription = "";

        switch (action) {
            case Intent.ACTION_HEADSET_PLUG:
                int headsetState = intent.getIntExtra("state", -1);
                eventDescription = "Headset " + (headsetState == 1 ? "connected" : "disconnected");
                relevant = true;
                break;
            case "android.hardware.usb.action.USB_STATE":
                boolean usbConnected = intent.getBooleanExtra("connected", false);
                eventDescription = "USB " + (usbConnected ? "connected" : "disconnected");
                relevant = true;
                break;
            case Intent.ACTION_POWER_CONNECTED:
                eventDescription = "Charger connected";
                relevant = true;
                break;
            case Intent.ACTION_POWER_DISCONNECTED:
                eventDescription = "Charger disconnected";
                relevant = true;
                break;
            case WifiManager.WIFI_STATE_CHANGED_ACTION:
                int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, -1);
                String wifiStateStr;
                switch (wifiState) {
                    case WifiManager.WIFI_STATE_ENABLED:  wifiStateStr = "enabled";   break;
                    case WifiManager.WIFI_STATE_DISABLED: wifiStateStr = "disabled";  break;
                    case WifiManager.WIFI_STATE_ENABLING: wifiStateStr = "enabling";  break;
                    case WifiManager.WIFI_STATE_DISABLING:wifiStateStr = "disabling"; break;
                    default:                               wifiStateStr = "unknown(" + wifiState + ")"; break;
                }
                eventDescription = "WiFi state: " + wifiStateStr;
                relevant = wifiState == WifiManager.WIFI_STATE_ENABLED || wifiState == WifiManager.WIFI_STATE_DISABLED;
                break;
            case "android.net.conn.CONNECTIVITY_ACTION":
                eventDescription = "Connectivity changed";
                relevant = true;
                break;
            case "android.bluetooth.adapter.action.STATE_CHANGED":
                int btState = intent.getIntExtra("android.bluetooth.adapter.extra.STATE", -1);
                String btStateStr;
                switch (btState) {
                    case 12: btStateStr = "on";       break;
                    case 10: btStateStr = "off";      break;
                    case 11: btStateStr = "turning on";  break;
                    case 13: btStateStr = "turning off"; break;
                    default: btStateStr = "unknown(" + btState + ")"; break;
                }
                eventDescription = "Bluetooth state: " + btStateStr;
                relevant = btState == 12 || btState == 10;
                break;
            case "android.location.PROVIDERS_CHANGED":
                eventDescription = "GPS/Location providers changed";
                relevant = true;
                break;
            case "android.net.wifi.WIFI_AP_STATE_CHANGED":
                int apState = intent.getIntExtra("wifi_state", -1);
                String apStateStr;
                switch (apState) {
                    case 13: apStateStr = "enabled";   break;
                    case 11: apStateStr = "disabled";  break;
                    case 12: apStateStr = "enabling";  break;
                    case 10: apStateStr = "disabling"; break;
                    default: apStateStr = "unknown(" + apState + ")"; break;
                }
                eventDescription = "Hotspot state: " + apStateStr;
                relevant = apState == 13 || apState == 11;
                break;
        }

        if (!relevant) {
            Log.d(TAG, "Event ignored (intermediate state): " + action);
            return;
        }
        
        Log.i(TAG, "Hardware event triggered: " + eventDescription);
        
        Log.i(TAG, "Scheduling Auto-Kill in " + (TRIGGER_DELAY_MS / 1000) + "s after: " + eventDescription);
        
        final String eventDesc = eventDescription;
        
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            Log.i(TAG, "Executing Auto-Kill triggered by: " + eventDesc);
            
            ExecutorService executor = Executors.newSingleThreadExecutor();
            ShellManager shellManager = new ShellManager(context.getApplicationContext(), handler, executor);
            BackgroundAppManager appManager = new BackgroundAppManager(
                    context.getApplicationContext(), handler, executor, shellManager);
            AutoKillManager autoKillManager = new AutoKillManager(
                    context.getApplicationContext(), handler, executor, shellManager,
                    appManager.getCurrentAppsList());
        
            autoKillManager.performAutoKill(() -> {
                Log.i(TAG, "Auto-Kill completed for event: " + eventDesc);
                executor.shutdown();
            });
        }, TRIGGER_DELAY_MS);
    }
}
