package com.gree1d.reappzuku.utils;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.gree1d.reappzuku.service.ShappkyService;

public class KillShortcutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent service = new Intent(this, ShappkyService.class);
        service.setAction("WIDGET_KILL");
        startService(service);
        finish();
    }
}
