package tv.supermidia.site;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by iuri on 05/01/15.
 */
public class BootUpReceiver extends BroadcastReceiver {
    public static final String TAG = "Boot";
    private final long INTERVAL = 0000;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.w(TAG, "Boot received");
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getActivity(context, 0, new Intent(context, Site.class), PendingIntent.FLAG_UPDATE_CURRENT);
        am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + INTERVAL, pi);
    }
}
