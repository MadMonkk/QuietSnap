package dk.quietsnap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        int id = intent.getIntExtra("alarm_id", 1);
        Intent service = new Intent(context, CaptureService.class).putExtra("alarm_id", id);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(service); else context.startService(service);
        AlarmScheduler.schedule(context);
    }
}
