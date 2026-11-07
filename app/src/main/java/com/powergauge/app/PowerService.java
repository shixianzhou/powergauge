package com.powergauge.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * 前台服务：持续监听 ACTION_BATTERY_CHANGED，刷新通知栏读数。
 * 这样退出界面、锁屏后依然能看到实时功率。
 */
public class PowerService extends Service {

    public static final String ACTION_STOP = "com.powergauge.app.action.STOP";
    /** 界面改动设置后请服务立刻重读一次，保证界面和通知栏是同一份读数。 */
    public static final String ACTION_REFRESH = "com.powergauge.app.action.REFRESH";

    private static final String CHANNEL_ID = "power_live";
    private static final int NOTIF_ID = 1001;

    private BatteryManager batteryManager;
    private Prefs prefs;
    private BroadcastReceiver receiver;
    private NotificationManager notificationManager;
    private long lastNotifyAt;
    private OverlayWindow overlay;
    private boolean inForeground;
    private final Handler handler = new Handler(Looper.getMainLooper());

    /**
     * 放电时系统只在电量等状态变化时才发 ACTION_BATTERY_CHANGED，
     * 光靠广播会出现「界面数字几分钟不动、切出去回来才变」的现象，
     * 所以这里按刷新间隔主动重读一次粘性广播。
     */
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, Math.max(500, prefs.refreshMs()));
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        batteryManager = (BatteryManager) getSystemService(BATTERY_SERVICE);
        prefs = Prefs.get(this);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createChannel();

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onBatteryChanged(intent);
            }
        };
        registerReceiver(receiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        Sampler.Reading sticky = Sampler.read(
                registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)),
                batteryManager, prefs);
        BatteryStats.get(this).update(sticky);
        Bus.resetRange();
        Bus.publish(sticky);
        startForegroundCompat(buildNotification(sticky));
        handler.postDelayed(tick, Math.max(500, prefs.refreshMs()));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 每次被 startForegroundService 唤起都保证一次 startForeground，
        // 否则「服务已在运行」时会踩 ForegroundServiceDidNotStartInTimeException
        if (!inForeground) {
            Sampler.Reading sticky = Sampler.read(
                    registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)),
                    batteryManager, prefs);
            startForegroundCompat(buildNotification(sticky));
        }
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_REFRESH.equals(intent.getAction())) {
            refresh();
            return START_STICKY;
        }
        return START_STICKY;
    }

    /** 强制读一次粘性广播并同步界面与通知。 */
    private void refresh() {
        Sampler.Reading reading = Sampler.read(
                registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)),
                batteryManager, prefs);
        publish(reading, true);
    }

    private void publish(Sampler.Reading reading, boolean force) {
        BatteryStats.get(this).update(reading);
        Bus.publish(reading);
        syncOverlay(reading);
        long now = System.currentTimeMillis();
        // 只做防抖（400ms），不按刷新间隔节流：否则界面的数字会比通知栏新一截
        if (!force && now - lastNotifyAt < 400) {
            return;
        }
        lastNotifyAt = now;
        notificationManager.notify(NOTIF_ID, buildNotification(reading));
    }

    /** 悬浮窗跟着同一份读数走：开关打开且已授权就显示，否则收起。 */
    private void syncOverlay(Sampler.Reading reading) {
        boolean want = prefs.overlayEnabled() && OverlayWindow.canShow(this);
        if (!want) {
            if (overlay != null) {
                overlay.hide();
            }
            return;
        }
        if (overlay == null) {
            overlay = new OverlayWindow(this);
        }
        overlay.show();
        overlay.update(reading);
    }

    private void onBatteryChanged(Intent intent) {
        publish(Sampler.read(intent, batteryManager, prefs), false);
    }

    private void startForegroundCompat(Notification notification) {
        inForeground = true;
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        channel.setSound(null, null);
        notificationManager.createNotificationChannel(channel);
    }

    private Notification buildNotification(Sampler.Reading reading) {
        String title;
        String text;
        if (reading == null || !reading.valid) {
            title = getString(R.string.app_name);
            text = getString(R.string.notif_waiting);
        } else {
            String direction = reading.charging() ? "充电" : "放电";
            title = Fmt.wattsShort(reading.watts) + " W · " + direction;
            text = Fmt.volts(reading.volts) + " V / " + Fmt.amps(Math.abs(reading.amps))
                    + " A · 电量 " + Fmt.percent(reading.percent) + "%";
        }

        PendingIntent open = PendingIntent.getActivity(
                this, 1,
                new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent stop = PendingIntent.getService(
                this, 2,
                new Intent(this, PowerService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_power)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setContentIntent(open)
                .addAction(0, getString(R.string.notif_stop), stop)
                .build();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        if (overlay != null) {
            overlay.hide();
            overlay = null;
        }
        if (receiver != null) {
            try {
                unregisterReceiver(receiver);
            } catch (IllegalArgumentException ignored) {
                // 已经注销过，忽略
            }
            receiver = null;
        }
        stopForeground(true);
        notificationManager.cancel(NOTIF_ID);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
