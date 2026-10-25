package dk.quietsnap;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.Calendar;

final class AlarmScheduler {
    static void schedule(Context c) {
        SharedPreferences p = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        AlarmManager am = (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        set(c, am, 1, p.getInt("t1", 480));
        if (p.getBoolean("second", true)) set(c, am, 2, p.getInt("t2", 1200)); else cancel(c, am, 2);
    }
    private static void set(Context c, AlarmManager am, int id, int minute) {
        Calendar cal = Calendar.getInstance(); cal.set(Calendar.HOUR_OF_DAY, minute / 60); cal.set(Calendar.MINUTE, minute % 60); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1);
        PendingIntent pi = pending(c, id);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
    }
    private static PendingIntent pending(Context c, int id) {
        Intent i = new Intent(c, AlarmReceiver.class).putExtra("alarm_id", id);
        return PendingIntent.getBroadcast(c, id, i, PendingIntent.FLAG_UPDATE_CURRENT);
    }
    private static void cancel(Context c, AlarmManager am, int id) { am.cancel(pending(c,id)); }
    private AlarmScheduler() {}
}
