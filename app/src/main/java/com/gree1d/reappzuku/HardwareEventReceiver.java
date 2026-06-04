package com.gree1d.reappzuku;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
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

        switch (action) {
            case Intent.ACTION_HEADSET_PLUG:
                relevant = true;
                break;
            case "android.hardware.usb.action.USB_STATE":
            case "android.hardware.usb.action.USB_DEVICE_ATTACHED":
                relevant = true;
                break;
            case Intent.ACTION_POWER_CONNECTED:
            case Intent.ACTION_POWER_DISCONNECTED:
                relevant = true;
                break;
        }

        if (!relevant) return;

        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            ShellManager shellManager = new ShellManager(context.getApplicationContext(), handler, executor);
            BackgroundAppManager appManager = new BackgroundAppManager(
                    context.getApplicationContext(), handler, executor, shellManager);
            AutoKillManager autoKillManager = new AutoKillManager(
                    context.getApplicationContext(), handler, executor, shellManager,
                    appManager.getCurrentAppsList());

            Log.d(TAG, "Triggering performAutoKill from hardware event: " + action);
            autoKillManager.performAutoKill(() -> executor.shutdown());
        }, TRIGGER_DELAY_MS);
    }
}
