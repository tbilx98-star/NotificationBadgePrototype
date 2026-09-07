package com.tbilx98.notificationbadge;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BadgeAccessibilityService extends AccessibilityService {

    public static final String ACTION_SELECTION_CHANGED =
            "com.tbilx98.notificationbadge.SELECTION_CHANGED";

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private final Map<String, TextView> badges =
            new HashMap<>();

    private final Map<String, Integer> counts =
            new HashMap<>();

    private final Map<String, String> selectedLabels =
            new HashMap<>();

    private final Map<String, Rect> positions =
            new HashMap<>();

    private WindowManager wm;

    private String launcherPackage;

    private BroadcastReceiver receiver;

    private boolean scanPosted;

    private final Runnable scanRunnable =
            new Runnable() {
                @Override
                public void run() {
                    scanPosted = false;
                    scanLauncher();
                }
            };

    @Override
    public void onServiceConnected() {

        super.onServiceConnected();

        wm = (WindowManager)
                getSystemService(WINDOW_SERVICE);

        launcherPackage =
                findDefaultLauncherPackage();

        reloadSelectedLabels();

        AccessibilityServiceInfo info =
                getServiceInfo();

        if (info != null) {

            info.eventTypes =
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                            | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                            | AccessibilityEvent.TYPE_WINDOWS_CHANGED;

            info.feedbackType =
                    AccessibilityServiceInfo.FEEDBACK_GENERIC;

            info.notificationTimeout = 120;

            info.flags |=
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;

            if (Build.VERSION.SDK_INT >= 21) {
                info.flags |=
                        AccessibilityServiceInfo
                                .FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            }

            setServiceInfo(info);
        }

        receiver = new BroadcastReceiver() {

            @Override
            public void onReceive(
                    Context context,
                    Intent intent) {

                String action =
                        intent.getAction();

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

                        counts.put(pkg, count);

                        refreshBadge(pkg);
                    }

                } else if (
                        ACTION_SELECTION_CHANGED.equals(action)) {

                    reloadSelectedLabels();

                    clearUnselectedBadges();

                    scheduleScan(0);
                }
            }
        };

        IntentFilter filter =
                new IntentFilter();

        filter.addAction(
                NotificationListener.ACTION_BADGE_CHANGED
        );

        filter.addAction(
                ACTION_SELECTION_CHANGED
        );

        registerReceiver(
                receiver,
                filter
        );

        scheduleScan(150);
    }

    @Override
    public void onAccessibilityEvent(
            AccessibilityEvent event) {

        CharSequence pkg =
                event.getPackageName();

        if (pkg == null) {
            return;
        }

        String packageName =
                pkg.toString();

        if (launcherPackage == null) {
            launcherPackage =
                    findDefaultLauncherPackage();
        }

        if (isLauncherPackage(packageName)) {

            if (event.getEventType()
                    == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

                launcherPackage =
                        packageName;
            }

            scheduleScan(90);
        }
    }

    @Override
    public void onInterrupt() {
    }

    private boolean isLauncherPackage(
            String packageName) {

        if (packageName.equals(
                launcherPackage)) {

            return true;
        }

        String lower =
                packageName.toLowerCase();

        return lower.contains("launcher")
                || lower.endsWith(".home");
    }

    private void scheduleScan(long delayMs) {

        if (scanPosted) {
            return;
        }

        scanPosted = true;

        handler.postDelayed(
                scanRunnable,
                delayMs
        );
    }

    private String findDefaultLauncherPackage() {

        try {

            Intent home =
                    new Intent(Intent.ACTION_MAIN);

            home.addCategory(
                    Intent.CATEGORY_HOME
            );

            home.addCategory(
                    Intent.CATEGORY_DEFAULT
            );

            android.content.pm.ResolveInfo ri =
                    getPackageManager()
                            .resolveActivity(
                                    home,
                                    PackageManager.MATCH_DEFAULT_ONLY
                            );

            if (ri != null
                    && ri.activityInfo != null) {

                return ri.activityInfo.packageName;
            }

        } catch (Exception ignored) {
        }

        return "com.android.launcher3";
    }

    private void reloadSelectedLabels() {

        selectedLabels.clear();

        Set<String> selected =
                Prefs.getSelected(this);

        PackageManager pm =
                getPackageManager();

        for (String pkg : selected) {

            try {

                ApplicationInfo ai =
                        pm.getApplicationInfo(pkg, 0);

                CharSequence label =
                        ai.loadLabel(pm);

                if (label != null) {

                    selectedLabels.put(
                            pkg,
                            normalize(label.toString())
                    );
                }

            } catch (Exception ignored) {
            }
        }
    }

    private void scanLauncher() {

        if (wm == null
                || selectedLabels.isEmpty()) {

            hideAllBadges();

            return;
        }

        if (Build.VERSION.SDK_INT >= 23
                && !Settings.canDrawOverlays(this)) {

            return;
        }

        android.view.accessibility.AccessibilityNodeInfo root =
                getRootInActiveWindow();

        if (root == null) {
            return;
        }

        CharSequence rootPkg =
                root.getPackageName();

        if (rootPkg == null
                || !isLauncherPackage(
                        rootPkg.toString())) {

            root.recycle();

            return;
        }

        final Map<String, Rect> found =
                new HashMap<>();

        traverse(
                root,
                found,
                0,
                new int[]{0}
        );

        positions.clear();

        positions.putAll(found);

        for (String pkg :
                new HashSet<>(counts.keySet())) {

            refreshBadge(pkg);
        }

        root.recycle();
    }

    private void traverse(
            android.view.accessibility.AccessibilityNodeInfo node,
            Map<String, Rect> found,
            int depth,
            int[] visited) {

        if (node == null
                || depth > 16
                || visited[0] > 1800) {

            return;
        }

        visited[0]++;

        Rect r = new Rect();

        node.getBoundsInScreen(r);

        if (!r.isEmpty()
                && node.isVisibleToUser()) {

            String label =
                    nodeLabel(node);

            if (node.isClickable()
                    && label != null) {

                String pkg =
                        packageForLabel(label);

                if (pkg != null
                        && !found.containsKey(pkg)) {

                    found.put(
                            pkg,
                            new Rect(r)
                    );
                }
            }
        }

        for (int i = 0;
             i < node.getChildCount();
             i++) {

            android.view.accessibility.AccessibilityNodeInfo child =
                    node.getChild(i);

            if (child != null) {

                traverse(
                        child,
                        found,
                        depth + 1,
                        visited
                );

                child.recycle();
            }
        }
    }

    private String nodeLabel(
            android.view.accessibility.AccessibilityNodeInfo node) {

        CharSequence cd =
                node.getContentDescription();

        if (cd != null && cd.length() > 0) {
            return cd.toString();
        }

        CharSequence text =
                node.getText();

        if (text != null && text.length() > 0) {
            return text.toString();
        }

        int children =
                Math.min(
                        node.getChildCount(),
                        4
                );

        for (int i = 0;
             i < children;
             i++) {

            android.view.accessibility.AccessibilityNodeInfo child =
                    node.getChild(i);

            if (child == null) {
                continue;
            }

            CharSequence c =
                    child.getContentDescription();

            if (c == null || c.length() == 0) {
                c = child.getText();
            }

            child.recycle();

            if (c != null && c.length() > 0) {
                return c.toString();
            }
        }

        return null;
    }

    private String packageForLabel(
            String label) {

        String normalized =
                normalize(label);

        String match = null;

        for (Map.Entry<String, String> e :
                selectedLabels.entrySet()) {

            if (normalized.equals(e.getValue())) {

                if (match != null) {
                    return null;
                }

                match = e.getKey();
            }
        }

        return match;
    }

    private String normalize(String s) {

        return s == null
                ? ""
                : s.trim()
                        .replaceAll("\\s+", " ")
                        .toLowerCase();
    }

    private void refreshBadge(
            String pkg) {

        if (!selectedLabels.containsKey(pkg)) {

            removeBadge(pkg);

            return;
        }

        Integer c =
                counts.get(pkg);

        Rect r =
                positions.get(pkg);

        if (c == null
                || c <= 0
                || r == null
                || r.isEmpty()) {

            TextView old =
                    badges.get(pkg);

            if (old != null) {
                old.setVisibility(View.GONE);
            }

            return;
        }

        if (wm == null
                || (Build.VERSION.SDK_INT >= 23
                && !Settings.canDrawOverlays(this))) {

            return;
        }

        TextView badge =
                badges.get(pkg);

        int size =
                Math.max(
                        18,
                        Math.min(
                                28,
                                (int)
                                        (Math.min(
                                                r.width(),
                                                r.height()
                                        ) * 0.36f)
                        )
                );

        if (badge == null) {

            badge = new TextView(this);

            badge.setTextColor(
                    Color.WHITE
            );

            badge.setTextSize(10);

            badge.setGravity(
                    Gravity.CENTER
            );

            badge.setIncludeFontPadding(
                    false
            );

            badge.setTypeface(
                    android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD
            );

            badge.setBackgroundResource(
                    R.drawable.badge_background
            );

            badges.put(
                    pkg,
                    badge
            );

            WindowManager.LayoutParams lp =
                    makeLayoutParams(
                            size,
                            r
                    );

            try {

                wm.addView(
                        badge,
                        lp
                );

            } catch (Exception e) {

                badges.remove(pkg);

                return;
            }

        } else {

            badge.setVisibility(
                    View.VISIBLE
            );

            WindowManager.LayoutParams lp =
                    (WindowManager.LayoutParams)
                            badge.getLayoutParams();

            lp.width = size;
            lp.height = size;

            place(
                    lp,
                    size,
                    r
            );

            try {

                wm.updateViewLayout(
                        badge,
                        lp
                );

            } catch (Exception ignored) {
            }
        }

        badge.setText(
                c > 99
                        ? "99+"
                        : String.valueOf(c)
        );

        badge.setTextSize(
                c > 99
                        ? 8
                        : 10
        );
    }

    private WindowManager.LayoutParams makeLayoutParams(
            int size,
            Rect r) {

        WindowManager.LayoutParams lp =
                new WindowManager.LayoutParams(
                        size,
                        size,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT
                );

        lp.gravity =
                Gravity.TOP | Gravity.LEFT;

        place(
                lp,
                size,
                r
        );

        return lp;
    }

    private void place(
            WindowManager.LayoutParams lp,
            int size,
            Rect r) {

        lp.x =
                r.right
                        - (int) (size * 0.72f);

        lp.y =
                r.top
                        - (int) (size * 0.25f);
    }

    private void clearUnselectedBadges() {

        for (String pkg :
                new HashSet<>(badges.keySet())) {

            if (!selectedLabels.containsKey(pkg)) {
                removeBadge(pkg);
            }
        }
    }

    private void hideAllBadges() {

        for (TextView v :
                badges.values()) {

            v.setVisibility(
                    View.GONE
            );
        }
    }

    private void removeBadge(
            String pkg) {

        TextView v =
                badges.remove(pkg);

        if (v != null && wm != null) {

            try {
                wm.removeView(v);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onDestroy() {

        handler.removeCallbacksAndMessages(
                null
        );

        if (receiver != null) {

            try {
                unregisterReceiver(receiver);
            } catch (Exception ignored) {
            }

            receiver = null;
        }

        for (String pkg :
                new HashSet<>(badges.keySet())) {

            removeBadge(pkg);
        }

        badges.clear();
        counts.clear();
        positions.clear();

        super.onDestroy();
    }
}
