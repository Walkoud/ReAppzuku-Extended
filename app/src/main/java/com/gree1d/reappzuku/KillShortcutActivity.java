package com.gree1d.reappzuku;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

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