package com.autonavi.companion;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "AmapCompanion";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null) {
            return;
        }
        String action = intent == null ? "" : intent.getAction();
        boolean isAutoStartEvent = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || Intent.ACTION_SCREEN_ON.equals(action)
                || Intent.ACTION_USER_PRESENT.equals(action);
        if (!isAutoStartEvent) {
            return;
        }
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            Log.d(TAG, "defer overlay service start until app launch or next screen event after package replacement");
            return;
        }
        if (!AppPrefs.isAutoStartEnabled(context)) {
            Log.d(TAG, "skip auto starting overlay service after " + action);
            return;
        }
        Log.d(TAG, "auto start overlay service after " + action);
        MainActivity.startOverlayService(context);
    }
}
