package com.tbilx98.notificationbadge;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BadgeAccessibilityService extends AccessibilityService {

    public static final String ACTION_SELECTION_CHANGED =
            "com.tbilx98.notificationbadge.SELECTION_CHANGED";

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private WindowManager windowManager;

    private final Map<String, Integer> notificationCounts =
            new HashMap<>();

    private final List<TextView> badgeViews =
            new ArrayList<>();

    private BroadcastReceiver receiver;

    private Runnable scanRunnable;

    @Override
    public void onServiceConnected() {

        super.onServiceConnected();

        windowManager =
                (WindowManager) getSystemService(WINDOW_SERVICE);

        AccessibilityServiceInfo info = getServiceInfo();

        if (info != null) {

            info.eventTypes =
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                            | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                            | AccessibilityEvent.TYPE_WINDOWS_CHANGED;

            info.feedbackType =
                    AccessibilityServiceInfo.FEEDBACK_GENERIC;

            info.notificationTimeout = 100;

            info.flags |=
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;

            if (Build.VERSION.SDK_INT >= 21) {

                info.flags |=
                        AccessibilityServiceInfo
                                .FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            }

            setServiceInfo(info);
        }

        registerBadgeReceiver();

        scanRunnable = new Runnable() {
            @Override
            public void run() {
                scanAndDrawBadges();
            }
        };

        scheduleScan(500);
    }

    private void registerBadgeReceiver() {

        receiver = new BroadcastReceiver() {

            @Override
            public void onReceive(
                    Context context,
                    Intent intent) {

                if (intent == null) {
                    return;
                }

                String action = intent.getAction();

                if (ACTION_SELECTION_CHANGED.equals(action)) {

                    scheduleScan(100);

                    return;
                }

                if (NotificationListener.ACTION_BADGE_CHANGED
                        .equals(action)) {

                    String pkg =
                            intent.getStringExtra(
                                    NotificationListener.EXTRA_PACKAGE
                            );

                    int count =
                            intent.getIntExtra(
                                    NotificationListener.EXTRA_COUNT,
                                    0
                            );

                    if (pkg != null) {

                        if (count > 0) {

                            notificationCounts.put(
                                    pkg,
                                    count
                            );

                        } else {

                            notificationCounts.remove(
                                    pkg
                            );
                        }
                    }

                    scheduleScan(100);
                }
            }
        };

        IntentFilter filter = new IntentFilter();

        filter.addAction(
                ACTION_SELECTION_CHANGED
        );

        filter.addAction(
                NotificationListener.ACTION_BADGE_CHANGED
        );

        registerReceiver(
                receiver,
                filter
        );
    }

    @Override
    public void onAccessibilityEvent(
            AccessibilityEvent event) {

        if (event == null) {
            return;
        }

        scheduleScan(150);
    }

    private void scheduleScan(long delay) {

        if (scanRunnable == null) {
            return;
        }

        handler.removeCallbacks(
                scanRunnable
        );

        handler.postDelayed(
                scanRunnable,
                delay
        );
    }

    private void scanAndDrawBadges() {

        clearBadges();

        AccessibilityNodeInfo root =
                getRootInActiveWindow();

        if (root == null) {
            return;
        }

        try {

            Set<String> selected =
                    Prefs.getSelected(this);

            if (selected == null
                    || selected.isEmpty()) {

                return;
            }

            Map<String, String> labels =
                    getSelectedLabels(selected);

            if (labels.isEmpty()) {
                return;
            }

            Set<String> alreadyFound =
                    new HashSet<>();

            findIcons(
                    root,
                    labels,
                    alreadyFound
            );

        } finally {

            root.recycle();
        }
    }

    private Map<String, String> getSelectedLabels(
            Set<String> selected) {

        Map<String, String> result =
                new HashMap<>();

        PackageManager pm =
                getPackageManager();

        for (String pkg : selected) {

            try {

                ApplicationInfo ai =
                        pm.getApplicationInfo(
                                pkg,
                                0
                        );

                CharSequence label =
                        pm.getApplicationLabel(ai);

                if (label != null) {

                    String name =
                            normalize(
                                    label.toString()
                            );

                    if (!name.isEmpty()) {

                        result.put(
                                pkg,
                                name
                        );
                    }
                }

            } catch (Exception ignored) {
            }
        }

        return result;
    }

    private void findIcons(
            AccessibilityNodeInfo node,
            Map<String, String> labels,
            Set<String> alreadyFound) {

        if (node == null) {
            return;
        }

        CharSequence text =
                node.getText();

        CharSequence description =
                node.getContentDescription();

        String value = "";

        if (description != null
                && description.length() > 0) {

            value =
                    normalize(
                            description.toString()
                    );

        } else if (text != null
                && text.length() > 0) {

            value =
                    normalize(
                            text.toString()
                    );
        }

        if (!value.isEmpty()) {

            for (Map.Entry<String, String> entry :
                    labels.entrySet()) {

                String pkg =
                        entry.getKey();

                String label =
                        entry.getValue();

                if (alreadyFound.contains(pkg)) {
                    continue;
                }

                if (value.equals(label)
                        || value.contains(label)
                        || label.contains(value)) {

                    Rect bounds =
                            new Rect();

                    node.getBoundsInScreen(
                            bounds
                    );

                    if (bounds.width() > 0
                            && bounds.height() > 0) {

                        Integer count =
                                notificationCounts.get(
                                        pkg
                                );

                        if (count != null
                                && count > 0) {

                            addBadge(
                                    bounds,
                                    count
                            );

                            alreadyFound.add(
                                    pkg
                            );
                        }
                    }
                }
            }
        }

        int childCount =
                node.getChildCount();

        for (int i = 0;
             i < childCount;
             i++) {

            AccessibilityNodeInfo child =
                    node.getChild(i);

            if (child != null) {

                findIcons(
                        child,
                        labels,
                        alreadyFound
                );

                child.recycle();
            }
        }
    }

    private String normalize(
            String text) {

        return text
                .trim()
                .toLowerCase()
                .replaceAll("\\s+", " ");
    }

    private void addBadge(
            Rect bounds,
            int count) {

        if (windowManager == null) {
            return;
        }

        TextView badge =
                new TextView(this);

        badge.setText(
                count > 99
                        ? "99+"
                        : String.valueOf(count)
        );

        badge.setTextColor(
                0xFFFFFFFF
        );

        badge.setTextSize(
                9
        );

        badge.setGravity(
                Gravity.CENTER
        );

        int size =
                dp(20);

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(
                0xFFFF3B30
        );

        background.setShape(
                GradientDrawable.OVAL
        );

        badge.setBackground(
                background
        );

        WindowManager.LayoutParams lp =
                new WindowManager.LayoutParams(
                        size,
                        size,
                        WindowManager.LayoutParams
                                .TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams
                                .FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams
                                .FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams
                                .FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT
                );

        lp.gravity =
                Gravity.TOP | Gravity.LEFT;

        lp.x =
                Math.max(
                        0,
                        bounds.right - size
                );

        lp.y =
                Math.max(
                        0,
                        bounds.top - size / 3
                );

        try {

            windowManager.addView(
                    badge,
                    lp
            );

            badgeViews.add(
                    badge
            );

        } catch (Exception ignored) {
        }
    }

    private int dp(int value) {

        return (int)
                (value
                        * getResources()
                                .getDisplayMetrics()
                                .density
                        + 0.5f);
    }

    private void clearBadges() {

        if (windowManager == null) {
            return;
        }

        for (TextView view :
                badgeViews) {

            try {

                windowManager.removeView(
                        view
                );

            } catch (Exception ignored) {
            }
        }

        badgeViews.clear();
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {

        handler.removeCallbacksAndMessages(
                null
        );

        clearBadges();

        if (receiver != null) {

            try {

                unregisterReceiver(
                        receiver
                );

            } catch (Exception ignored) {
            }

            receiver = null;
        }

        super.onDestroy();
    }
}
