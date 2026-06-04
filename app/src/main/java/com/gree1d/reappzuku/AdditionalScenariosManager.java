package com.gree1d.reappzuku;

import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

import static com.gree1d.reappzuku.PreferenceKeys.*;
import static com.gree1d.reappzuku.AppConstants.*;

public class AdditionalScenariosManager {

    private static final String TAG = "AdditionalScenariosManager";

    private final Context context;
    private final SharedPreferences prefs;
    private HardwareEventReceiver hardwareEventReceiver;
    private boolean receiverRegistered = false;

    public AdditionalScenariosManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public void updateHardwareReceiverState() {
        boolean headset = prefs.getBoolean(KEY_HW_TRIGGER_HEADSET, false);
        boolean usb = prefs.getBoolean(KEY_HW_TRIGGER_USB, false);
        boolean charger = prefs.getBoolean(KEY_HW_TRIGGER_CHARGER, false);

        boolean anyEnabled = headset || usb || charger;

        if (anyEnabled && !receiverRegistered) {
            registerReceiver(headset, usb, charger);
        } else if (!anyEnabled && receiverRegistered) {
            unregisterReceiver();
        } else if (anyEnabled && receiverRegistered) {
            unregisterReceiver();
            registerReceiver(headset, usb, charger);
        }
    }

    private void registerReceiver(boolean headset, boolean usb, boolean charger) {
        hardwareEventReceiver = new HardwareEventReceiver();
        IntentFilter filter = new IntentFilter();

        if (headset) {
            filter.addAction(android.content.Intent.ACTION_HEADSET_PLUG);
        }
        if (usb) {
            filter.addAction("android.hardware.usb.action.USB_STATE");
            filter.addAction("android.hardware.usb.action.USB_DEVICE_ATTACHED");
        }
        if (charger) {
            filter.addAction(android.content.Intent.ACTION_POWER_CONNECTED);
            filter.addAction(android.content.Intent.ACTION_POWER_DISCONNECTED);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(hardwareEventReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(hardwareEventReceiver, filter);
        }

        receiverRegistered = true;
        Log.d(TAG, "HardwareEventReceiver registered (headset=" + headset + ", usb=" + usb + ", charger=" + charger + ")");
    }

    private void unregisterReceiver() {
        if (hardwareEventReceiver != null) {
            try {
                context.unregisterReceiver(hardwareEventReceiver);
                Log.d(TAG, "HardwareEventReceiver unregistered");
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Receiver was not registered: " + e.getMessage());
            }
            hardwareEventReceiver = null;
        }
        receiverRegistered = false;
    }

    public void stop() {
        unregisterReceiver();
    }

    public boolean isHeadsetTriggerEnabled() {
        return prefs.getBoolean(KEY_HW_TRIGGER_HEADSET, false);
    }

    public boolean isUsbTriggerEnabled() {
        return prefs.getBoolean(KEY_HW_TRIGGER_USB, false);
    }

    public boolean isChargerTriggerEnabled() {
        return prefs.getBoolean(KEY_HW_TRIGGER_CHARGER, false);
    }

    public void setHeadsetTriggerEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_HW_TRIGGER_HEADSET, enabled).apply();
    }

    public void setUsbTriggerEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_HW_TRIGGER_USB, enabled).apply();
    }

    public void setChargerTriggerEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_HW_TRIGGER_CHARGER, enabled).apply();
    }

    public boolean isAppLaunchTriggerEnabled() {
        return prefs.getBoolean(KEY_APP_LAUNCH_TRIGGER_ENABLED, false);
    }

    public void setAppLaunchTriggerEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_APP_LAUNCH_TRIGGER_ENABLED, enabled).apply();
    }

    public Set<String> getAppLaunchTriggerPackages() {
        return new HashSet<>(prefs.getStringSet(KEY_APP_LAUNCH_TRIGGER_PACKAGES, new HashSet<>()));
    }

    public void saveAppLaunchTriggerPackages(Set<String> packages) {
        prefs.edit().putStringSet(KEY_APP_LAUNCH_TRIGGER_PACKAGES, new HashSet<>(packages)).apply();
    }
}
