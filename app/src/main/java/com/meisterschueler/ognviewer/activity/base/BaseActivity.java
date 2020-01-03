package com.meisterschueler.ognviewer.activity.base;

import android.annotation.SuppressLint;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.meisterschueler.ognviewer.common.AppConstants;

@SuppressLint("Registered")
public class BaseActivity extends AppCompatActivity {

    KillBroadcastReceiver killBroadcastReceiver;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        killBroadcastReceiver = new KillBroadcastReceiver(this);
        registerReceiver(killBroadcastReceiver, new IntentFilter(AppConstants.EMERGENCY_EXIT_INTENT_ACTION_NAME));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(killBroadcastReceiver);
    }

}
