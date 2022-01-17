package com.smartvue.acballoontimer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import static android.content.Context.ALARM_SERVICE;
import static com.smartvue.acballoontimer.MainActivity.CHANNEL_ID;
import static com.smartvue.acballoontimer.MainActivity.actionStop;
import static com.smartvue.acballoontimer.MainActivity.earlyMinutes;
import static com.smartvue.acballoontimer.MainActivity.earlySeconds;
import static com.smartvue.acballoontimer.MainActivity.getStartAtOffset;
import static com.smartvue.acballoontimer.MainActivity.pendingIntent;
import static com.smartvue.acballoontimer.MainActivity.startAt;
import static com.smartvue.acballoontimer.MainActivity.time;

public class MyBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();
        if (action != null && action.equals("stop")) {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            MainActivity.alarmManager.cancel(pendingIntent);
            notificationManager.cancelAll();
            try {
                MainActivity.wl_cpu.release();
                MainActivity.wl.release();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        } else {
            showNotification(context);
            appNotification(context);
            startAlert(context);
            MainActivity.keepAwake(context);
        }
    }

    void appNotification(Context context) {
        String debugMsg = "At: " + convertDate(System.currentTimeMillis(), "hh:mm:ss") + " Should've been: " + convertDate(getStartAtOffset(), "hh:mm:ss");
        String regularMsg = "Next Present: " + convertDate(getStartAtOffset(), "hh:mm:ss");
        Toast.makeText(context, MainActivity.debug ? debugMsg : regularMsg, Toast.LENGTH_LONG).show();
        MainActivity.playAlertSound(context);
    }

    void showNotification(Context context) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        NotificationCompat.Builder builder = getNotification(context);
        notificationManager.notify(0, builder.build());
    }

    NotificationCompat.Builder getNotification(Context context) {
        String debugMsg = "At: " + convertDate(System.currentTimeMillis(), "hh:mm:ss") + " Should've been: " + convertDate(getStartAtOffset(), "hh:mm:ss");
        String regularMsg = "Next Present: " + convertDate(getStartAtOffset(), "hh:mm:ss");

        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Times up")
                .setContentText(MainActivity.debug ? debugMsg : regularMsg)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(MainActivity.debug ? debugMsg : regularMsg))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .addAction(actionStop)
                .setNotificationSilent()
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
    }

    public void startAlert(Context context) {
        Intent intent = new Intent(context, MyBroadcastReceiver.class);
        MainActivity.pendingIntent = PendingIntent.getBroadcast(context.getApplicationContext(), 234, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        MainActivity.alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);
        startAt += 60000L * time;
        if (startAt > System.currentTimeMillis()) {
            MainActivity.alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(getStartAtOffset(), pendingIntent), pendingIntent);
        } else {
            Log.d("StartAlert", ": system time: "+System.currentTimeMillis()+" lower than start time: "+startAt);
        }
        //MainActivity.alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, startAt - (earlySeconds * 1000) - (earlyMinutes * 60000), MainActivity.pendingIntent);
    }

    public static String convertDate(Long dateInMilliseconds,String dateFormat) {
        return DateFormat.format(dateFormat, dateInMilliseconds).toString();
    }
}
