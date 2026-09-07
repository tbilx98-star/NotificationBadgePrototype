package com.tbilx98.notificationbadge;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.HashSet;
import java.util.Set;

public class NotificationListener
        extends NotificationListenerService {

    public static final String ACTION_BADGE_CHANGED =
            "com.tbilx98.notificationbadge.BADGE_CHANGED";

    public static final String ACTION_REFRESH_REQUEST =
            "com.tbilx98.notificationbadge.REFRESH_REQUEST";

    public static final String EXTRA_PACKAGE =
            "package";

    public static final String EXTRA_COUNT =
            "count";

    private BroadcastReceiver refreshReceiver;

    @Override
    public void onCreate() {

        super.onCreate();

        refreshReceiver =
                new BroadcastReceiver() {

                    @Override
                    public void onReceive(
                            Context context,
                            Intent intent) {

                        if (intent == null) {
                            return;
                        }

                        if (ACTION_REFRESH_REQUEST.equals(
                                intent.getAction()
                        )) {

                            publishAll();
                        }
                    }
                };

        IntentFilter filter =
                new IntentFilter(
                        ACTION_REFRESH_REQUEST
                );

        registerReceiver(
                refreshReceiver,
                filter
        );
    }

    @Override
    public void onListenerConnected() {

        super.onListenerConnected();

        /*
         * Khi service vừa được kết nối,
         * gửi lại toàn bộ notification hiện có.
         *
         * Đây là phần quan trọng để xử lý:
         * Mail đang có sẵn 99+ nhưng app badge
         * service khởi động sau đó.
         */
        publishAll();
    }

    @Override
    public void onNotificationPosted(
            StatusBarNotification sbn) {

        if (sbn == null) {
            return;
        }

        publishPackage(
                sbn.getPackageName()
        );
    }

    @Override
    public void onNotificationRemoved(
            StatusBarNotification sbn) {

        if (sbn == null) {
            return;
        }

        publishPackage(
                sbn.getPackageName()
        );
    }

    private void publishAll() {

        if (!isConnected()) {
            return;
        }

        Set<String> packages =
                new HashSet<>();

        try {

            StatusBarNotification[] notifications =
                    getActiveNotifications();

            if (notifications != null) {

                for (StatusBarNotification sbn :
                        notifications) {

                    if (sbn != null
                            && sbn.getPackageName() != null) {

                        packages.add(
                                sbn.getPackageName()
                        );
                    }
                }
            }

        } catch (Exception ignored) {
        }

        /*
         * Gửi package đang có notification.
         */
        for (String pkg :
                packages) {

            publishPackage(pkg);
        }
    }

    private void publishPackage(
            String pkg) {

        if (pkg == null
                || pkg.isEmpty()) {

            return;
        }

        int count =
                getNotificationCount(pkg);

        Intent intent =
                new Intent(
                        ACTION_BADGE_CHANGED
                );

        intent.setPackage(
                getPackageName()
        );

        intent.putExtra(
                EXTRA_PACKAGE,
                pkg
        );

        intent.putExtra(
                EXTRA_COUNT,
                count
        );

        sendBroadcast(intent);
    }

    private int getNotificationCount(
            String pkg) {

        int notificationCount = 0;

        int largestNumber = 0;

        try {

            StatusBarNotification[] notifications =
                    getActiveNotifications();

            if (notifications == null) {
                return 0;
            }

            for (StatusBarNotification sbn :
                    notifications) {

                if (sbn == null) {
                    continue;
                }

                if (!pkg.equals(
                        sbn.getPackageName()
                )) {
                    continue;
                }

                notificationCount++;

                Notification notification =
                        sbn.getNotification();

                if (notification != null) {

                    /*
                     * Notification.number thường được
                     * các app dùng để báo số lượng:
                     *
                     * Messenger: 2
                     * Mail: 99
                     * ...
                     */
                    if (notification.number > largestNumber) {

                        largestNumber =
                                notification.number;
                    }

                    /*
                     * Một số app Android đặt số lượng
                     * trong extras thay vì Notification.number.
                     */
                    try {

                        int extraNumber =
                                notification.extras.getInt(
                                        Notification.EXTRA_SUMMARY_TEXT,
                                        0
                                );

                        /*
                         * EXTRA_SUMMARY_TEXT thường là String,
                         * nên phần này chỉ là fallback an toàn.
                         */
                        if (extraNumber > largestNumber) {

                            largestNumber =
                                    extraNumber;
                        }

                    } catch (Exception ignored) {
                    }
                }
            }

        } catch (Exception ignored) {
        }

        /*
         * Nếu app cung cấp số lượng lớn hơn số
         * notification thực tế thì dùng số đó.
         *
         * Ví dụ:
         *
         * 1 notification đại diện 99 email
         * → 99
         *
         * 2 notification Messenger
         * → 2
         */
        if (largestNumber > notificationCount) {

            return largestNumber;
        }

        return notificationCount;
    }

    private boolean isConnected() {

        try {

            return getActiveNotifications() != null;

        } catch (Exception e) {

            return false;
        }
    }

    @Override
    public void onDestroy() {

        if (refreshReceiver != null) {

            try {

                unregisterReceiver(
                        refreshReceiver
                );

            } catch (Exception ignored) {
            }

            refreshReceiver = null;
        }

        super.onDestroy();
    }
}
