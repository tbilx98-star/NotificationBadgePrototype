package com.example.notificationbadge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BadgeOverlayService extends Service {
    private WindowManager windowManager;
    private Map<String, View> activeBadges = new HashMap<>();
    private BroadcastReceiver badgeReceiver;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, "BadgeServiceChannel")
                .setContentTitle("iOS Badge Service")
                .setContentText("Running to display custom badges")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
        startForeground(1, notification);

        badgeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateBadges();
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(badgeReceiver,
                new IntentFilter("com.example.notificationbadge.UPDATE_BADGES"));
        updateBadges();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (badgeReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(badgeReceiver);
        }
        removeAllBadges();
    }

    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    "BadgeServiceChannel",
                    "Badge Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private void updateBadges() {
        // Clean up old badges
        removeAllBadges();

        SharedPreferences prefs = getSharedPreferences("BadgePrefs", MODE_PRIVATE);
        Set<String> selectedApps = prefs.getStringSet("selectedApps", new HashSet<>());

        // Access active notifications via a helper or direct listener check if possible
        // For simplicity in overlay bounds, we simulate drawing sample counters for selected apps
        int i = 0;
        for (String pkg : selectedApps) {
            try {
                TextView badge = new TextView(this);
                badge.setText("1");
                badge.setTextColor(0xFFFFFFFF);
                badge.setBackgroundResource(R.drawable.badge_background);
                badge.setGravity(Gravity.CENTER);
                badge.setTextSize(10);

                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        40, 40,
                        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O ?
                                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                                WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        PixelFormat.TRANSLUCENT
                );
                params.gravity = Gravity.TOP | Gravity.START;
                params.x = 100 + (i * 120);
                params.y = 200;

                windowManager.addView(badge, params);
                activeBadges.put(pkg, badge);
                i++;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void removeAllBadges() {
        for (View badge : activeBadges.values()) {
            try {
                windowManager.removeView(badge);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        activeBadges.clear();
    }
}
