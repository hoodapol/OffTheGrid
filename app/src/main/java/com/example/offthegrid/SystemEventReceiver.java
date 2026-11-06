package com.example.offthegrid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.widget.Toast;

public class SystemEventReceiver extends BroadcastReceiver {

    private static final long RESTART_DELAY_MS = 4000;

    public static IntentFilter filter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        filter.addAction(Intent.ACTION_BATTERY_OKAY);
        filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        return filter;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        Context app = context.getApplicationContext();
        if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
            boolean on = intent.getBooleanExtra("state", false);
            toast(app, on ? R.string.event_airplane_on : R.string.event_airplane_off);
            if (!on) {
                new Handler(Looper.getMainLooper()).postDelayed(
                        () -> ListenerService.restartDiscovery(app), RESTART_DELAY_MS);
            }
        } else if (Intent.ACTION_BATTERY_LOW.equals(action)) {
            toast(app, R.string.event_battery_low);
        } else if (Intent.ACTION_BATTERY_OKAY.equals(action)) {
            toast(app, R.string.event_battery_ok);
        } else if (PowerManager.ACTION_POWER_SAVE_MODE_CHANGED.equals(action)) {
            PowerManager manager = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
            boolean on = manager != null && manager.isPowerSaveMode();
            toast(app, on ? R.string.event_power_save_on : R.string.event_power_save_off);
        }
    }

    private static void toast(Context app, int message) {
        Toast.makeText(app, message, Toast.LENGTH_LONG).show();
    }
}