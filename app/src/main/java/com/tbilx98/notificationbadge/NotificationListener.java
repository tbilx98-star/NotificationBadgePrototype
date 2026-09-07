package com.tbilx98.notificationbadge;

import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class NotificationListener
        extends NotificationListenerService {

    public static final String ACTION_BADGE_CHANGED =
            "com.tbilx98.notificationbadge.BADGE_CHANGED";

    public static final String EXTRA_PACKAGE =
            "package";

    public static final String EXTRA_COUNT =
            "count";

    @Override
    public void onNotificationPosted(
            StatusBarNotification sbn) {

        publish(
                sbn.getPackageName()
        );
    }

    @Override
    public void onNotificationRemoved(
            StatusBarNotification sbn) {

        publish(
                sbn.getPackageName()
        );
    }

    @Override
    public void onListenerConnected() {

        super.onListenerConnected();

        for (StatusBarNotification sbn :
                getActiveNotifications()) {

            publish(
                    sbn.getPackageName()
            );
        }
    }

    private void publish(
            String pkg) {

        int count = 0;

        try {

            for (StatusBarNotification n :
                    getActiveNotifications()) {

                if (pkg.equals(
                        n.getPackageName())) {

                    count++;
                }
            }

        } catch (Exception ignored) {
        }

        Intent i =
                new Intent(
                        ACTION_BADGE_CHANGED
                );

        i.setPackage(
                getPackageName()
        );

        i.putExtra(
                EXTRA_PACKAGE,
                pkg
        );

        i.putExtra(
                EXTRA_COUNT,
                count
        );

        sendBroadcast(i);
    }
}
