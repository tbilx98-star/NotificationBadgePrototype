package com.example.notificationbadge;

import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class NotificationListener extends NotificationListenerService {
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        sendBadgeUpdateBroadcast();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        sendBadgeUpdateBroadcast();
    }

    private void sendBadgeUpdateBroadcast() {
        Intent intent = new Intent("com.example.notificationbadge.UPDATE_BADGES");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}
