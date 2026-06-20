package com.gree1d.reappzuku.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.gree1d.reappzuku.service.ShappkyService;
import com.gree1d.reappzuku.manager.RestrictionsScheduler;
import com.gree1d.reappzuku.service.AutoKillWorker;
import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_AUTO_KILL_ENABLED;
import static com.gree1d.reappzuku.core.PreferenceKeys.PREFERENCES_NAME;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.LOCKED_BOOT_COMPLETED".equals(action)) {

            Intent serviceIntent = new Intent(context, ShappkyService.class);
            ContextCompat.startForegroundService(context, serviceIntent);

            RestrictionsScheduler.scheduleNextStatic(context);

            SharedPreferences prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
            boolean autoKillEnabled = prefs.getBoolean(KEY_AUTO_KILL_ENABLED, false);
            if (autoKillEnabled) {
                AutoKillWorker.schedule(context, "Periodic Kill");
                Log.d(TAG, "Boot complete (" + action + "): service started, worker scheduled");
            } else {
                AutoKillWorker.cancel(context);
                Log.d(TAG, "Boot complete (" + action + "): service started, worker skipped");
            }
        }
    }
}
