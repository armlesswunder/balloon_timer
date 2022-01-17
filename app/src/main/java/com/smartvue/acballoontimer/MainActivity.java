package com.smartvue.acballoontimer;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static com.smartvue.acballoontimer.MyBroadcastReceiver.convertDate;
import static java.text.DateFormat.getDateTimeInstance;

public class MainActivity extends AppCompatActivity {
    public static final String catalogURL = "https://play.google.com/store/apps/details?id=com.abw4v.accatalog";
    public static final String donateURL = "https://gofund.me/e6fc7138";

    public static final String CHANNEL_ID ="AC_Balloon_Timer";
    static final int WILD_WORLD = 0;
    static final int CITY_FOLK = 1;
    static final int NEW_LEAF = 2;
    static final int NEW_HORIZONS = 3;
    static final boolean debug = false;

    int selectedGame = NEW_HORIZONS;

    List<String> gameDisplay = Arrays.asList("Wild World", "City Folk", "New Leaf", "New Horizons");

    TextView lblHours, lblMinutes, lblSeconds;
    Button btnStart, btnCustomTime;
    EditText txtSeconds, txtMinutes;
    Spinner gameSpinner;

    static int time;
    static int earlySeconds = 30, earlyMinutes = 0;

    static long startAt;

    static PowerManager.WakeLock wl_cpu, wl;

    static Date currentDate;
    static Handler handler = new Handler();

    static AlarmManager alarmManager;
    static PendingIntent pendingIntent;

    static NotificationCompat.Action actionStop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getDefaults();
        linkViewProperties();
        setActions();
        setCurrentDate();
        createNotificationChannel();
        startTicking();
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }

    void getDefaults() {
        SharedPreferences prefs = getSharedPreferences("default", Context.MODE_PRIVATE);
        selectedGame = prefs.getInt("selectedGame", selectedGame);
        earlySeconds = prefs.getInt("earlySeconds", earlySeconds);
        earlyMinutes = prefs.getInt("earlyMinutes", earlyMinutes);
        setTime(selectedGame);
    }

    static void keepAwake(Context context) {
        long timeInMillis = (startAt + 60000L) - System.currentTimeMillis();
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        boolean isScreenOn = pm.isInteractive();
        if (!isScreenOn)
        {
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE,"MyLock:");
            wl.acquire(timeInMillis);
            wl_cpu = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"MyCpuLock:");

            wl_cpu.acquire(timeInMillis);
        }
    }

    void setActions() {
        Intent stopIntent = new Intent(this, MyBroadcastReceiver.class);
        stopIntent.setAction("stop");
        stopIntent.putExtra("stop", "stop");
        PendingIntent stopPendingIntent =
                PendingIntent.getBroadcast(this.getApplicationContext(), 234, stopIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        actionStop = new NotificationCompat.Action(null, "stop", stopPendingIntent);
    }

    public void stop(View view) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        MainActivity.alarmManager.cancel(pendingIntent);
        notificationManager.cancelAll();
        try {
            MainActivity.wl_cpu.release();
            MainActivity.wl.release();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.cancelAll();
        super.onDestroy();
    }

    public void startAlert() {
        setTime(selectedGame);
        setCurrentDate();
        setStartAt();

        Intent intent = new Intent(this, MyBroadcastReceiver.class);
        pendingIntent = PendingIntent.getBroadcast(this.getApplicationContext(), 234, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

        String debugMsg = "At: " + convertDate(System.currentTimeMillis(), "hh:mm:ss") + " Should've been: " + convertDate(getStartAtOffset(), "hh:mm:ss");
        String regularMsg = "Next Present: " + convertDate(getStartAtOffset(), "hh:mm:ss");
        MainActivity.alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(getStartAtOffset(), pendingIntent), pendingIntent);
        Toast.makeText(this, debug ? debugMsg : regularMsg, Toast.LENGTH_LONG).show();
        keepAwake(this);
        playAlertSound(this);
    }

    static void playAlertSound(Context context) {
        MediaPlayer mediaPlayer = MediaPlayer.create(context, R.raw.notification_sound);
        //mediaPlayer.setAudioStreamType(AudioManager.STREAM_NOTIFICATION);
        mediaPlayer.start();
    }

    static long getStartAtOffset() {
        return startAt - (earlySeconds * 1000) - (earlyMinutes * 60000);
    }

    void setStartAt() {
        startAt = currentDate.getTime() - (currentDate.getSeconds() * 1000) + getNextAlertTime(selectedGame);
        startAt = startAt/1000;
        startAt = startAt*1000;
    }

    void setTime(int mode) {
        switch (mode) {
            case WILD_WORLD:
            case CITY_FOLK:
            case NEW_LEAF: {
                MainActivity.time = 10;
                return;
            }
            case NEW_HORIZONS:
            default: {
                MainActivity.time = 5;
            }
        }
    }

    long getNextAlertTime(int mode) {
        switch (mode) {
            case WILD_WORLD: return getNextAlertTimeWildWorld();
            case CITY_FOLK: return getNextAlertTimeWildWorld();
            case NEW_LEAF: return getNextAlertTimeWildWorld();
            case NEW_HORIZONS: return getNextAlertTimeNewHorizons();
            default: return getNextAlertTimeNewHorizons();
        }
    }

    long getNextAlertTimeNewHorizons() {
        long nextAlertTime = 0;
        for (long i = 0; true; i += 5) {
            if (i > currentDate.getMinutes()) {
                nextAlertTime = i - currentDate.getMinutes();
                nextAlertTime *= 60000L;
                break;
            }
        }
        return nextAlertTime;
    }

    long getNextAlertTimeWildWorld() {
        long nextAlertTime = 0;
        for (long i = 4; true; i += 10) {
            if (i > currentDate.getMinutes()) {
                nextAlertTime = i - currentDate.getMinutes();
                nextAlertTime *= 60000L;
                break;
            }
        }
        return nextAlertTime;
    }

    void turnOffDozeMode(Context context){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            String packageName = context.getPackageName();
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm.isIgnoringBatteryOptimizations(packageName))
                intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            else {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
            }
            context.startActivity(intent);
        }
    }

    void linkViewProperties() {
        btnStart = findViewById(R.id.btnStart);
        btnCustomTime = findViewById(R.id.btnCustomTime);

        txtSeconds = findViewById(R.id.txtOffsetSeconds);
        txtMinutes = findViewById(R.id.txtOffsetMinutes);

        lblHours = findViewById(R.id.lblHours);
        lblMinutes = findViewById(R.id.lblMinutes);
        lblSeconds = findViewById(R.id.lblSeconds);

        btnStart.setOnClickListener(v -> startAlert());
        btnCustomTime.setOnClickListener(v -> customTimePressed());
        findViewById(R.id.btnDoze).setOnClickListener(v -> turnOffDozeMode(this));

        gameSpinner = findViewById(R.id.gameSpinner);
        ArrayAdapter<String> gameAdapter = new ArrayAdapter<>(this, R.layout.spinner_layout, gameDisplay);
        gameSpinner.setAdapter(gameAdapter);
        gameAdapter.setDropDownViewResource(R.layout.simple_spinner);
        gameSpinner.setSelection(selectedGame);
        gameSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedGame = position;
                SharedPreferences prefs = getSharedPreferences("default", Context.MODE_PRIVATE);
                prefs.edit().putInt("selected_game", selectedGame).apply();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        txtMinutes.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                String str = txtMinutes.getText().toString().equals("") ? "0" : txtMinutes.getText().toString();
                if (str.length() > 1) {
                    if (str.charAt(0) == '0') {
                        str = str.substring(1);
                        txtMinutes.setText(str);
                    }
                }
                if (!str.isEmpty()) {
                    txtMinutes.setSelection(txtMinutes.getText().length());
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (txtMinutes.getText().toString().equals("")) {
                    txtMinutes.setText("0");
                }
            }
        });

        txtSeconds.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                String str = txtSeconds.getText().toString().equals("") ? "0" : txtSeconds.getText().toString();
                if (str.length() > 1) {
                    if (str.charAt(0) == '0') {
                        str = str.substring(1);
                        txtSeconds.setText(str);
                        return;
                    }
                }
                if (!str.isEmpty()) {
                    txtSeconds.setSelection(txtSeconds.getText().length());
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (txtSeconds.getText().toString().equals("")) {
                    txtSeconds.setText("0");
                }
            }
        });

        txtMinutes.setText(Integer.toString(earlyMinutes));
        txtSeconds.setText(Integer.toString(earlySeconds));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "AC Balloon Timer";
            String description = "Shows alert every 5 or 10 minutes";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    void startTicking() {
        handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateUI();
                handler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    void updateUI() {
        runOnUiThread(() -> {
            setCurrentDate();
            lblHours.setText(getHours());
            lblMinutes.setText(getMinutes());
            lblSeconds.setText(getSeconds());
        });
    }

    void setCurrentDate() {
        currentDate = new Date();
        currentDate.setTime(System.currentTimeMillis());
        /*
        if (offsetDate != null) {
            int offsetHours = offsetDate.getHours();
            int offsetMinutes = offsetDate.getMinutes();
            int currentHours = currentDate.getHours();
            int currentMinutes = currentDate.getMinutes();

            if (offsetHours > 0) {
                currentDate.setHours(currentHours - offsetHours);
                currentDate.setMinutes(currentMinutes - offsetMinutes);
            } else {
                currentDate.setHours(currentHours + offsetHours);
                currentDate.setMinutes(currentMinutes + offsetMinutes);
            }
        }
         */
    }

    void customTimePressed() {
        currentDate = new Date();
        try {
            int earlySeconds = Integer.parseInt(txtSeconds.getText().toString());
            int earlyMinutes = Integer.parseInt(txtMinutes.getText().toString());
            if ((earlySeconds * 1000L) + (earlyMinutes * 60000L) >= 60000L * MainActivity.time) {
                Toast.makeText(this, "Cannot set time earlier than time cycle.", Toast.LENGTH_LONG).show();
            } else {
                MainActivity.earlyMinutes = earlyMinutes;
                MainActivity.earlySeconds = earlySeconds;

                SharedPreferences prefs = getSharedPreferences("default", Context.MODE_PRIVATE);
                prefs.edit().putInt("earlySeconds", earlySeconds).apply();
                prefs.edit().putInt("earlyMinutes", earlyMinutes).apply();
                Toast.makeText(this, "Saved preference", Toast.LENGTH_LONG).show();
            }
        } catch (Throwable e) {
            Toast.makeText(this, "Invalid input.", Toast.LENGTH_LONG).show();
        }

        /*
        offsetHours = Integer.parseInt(txtHours.getText().toString());
        offsetMinutes = Integer.parseInt(txtMinutes.getText().toString());
        if (offsetHours > -1 && offsetHours < 24 && offsetMinutes > -1 && offsetMinutes < 60) {
            Date customDate = new Date();
            customDate.setHours(offsetHours);
            customDate.setMinutes(offsetMinutes);
            offsetDate = new Date(customDate.getTime());
            if (currentDate.getHours() - customDate.getHours() > 0) {
                offsetDate.setHours(currentDate.getHours() - customDate.getHours());
            } else {
                offsetDate.setHours(customDate.getHours() - currentDate.getHours());
            }
            if (currentDate.getMinutes() - customDate.getMinutes() > 0) {
                offsetDate.setMinutes(currentDate.getMinutes() - customDate.getMinutes());
            } else {
                offsetDate.setMinutes(customDate.getMinutes() - currentDate.getMinutes());
            }

            offsetDate.setHours(currentDate.getHours() - customDate.getHours());
            offsetDate.setMinutes(currentDate.getMinutes() - customDate.getMinutes());
        } else {
            Toast.makeText(this, "Invalid time. reverting to systme.", Toast.LENGTH_SHORT).show();
        }
         */
    }

    String getHours() {
        int hours =  currentDate.getHours();
        if (hours < 10) {
            return "0" + hours;
        }
        else {
            return Integer.toString(hours);
        }
    }

    String getMinutes() {
        int minutes =  currentDate.getMinutes();
        if (minutes < 10) {
            return "0" + minutes;
        }
        else {
            return Integer.toString(minutes);
        }
    }

    String getSeconds() {
        int seconds = currentDate.getSeconds();
        if (seconds < 10) {
            return "0" + seconds;
        }
        else {
            return Integer.toString(seconds);
        }
    }

    public void donatePressed(View view) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(donateURL));
        startActivity(intent);
    }

    public void catalogPressed(View view) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(catalogURL));
        startActivity(intent);
    }
}