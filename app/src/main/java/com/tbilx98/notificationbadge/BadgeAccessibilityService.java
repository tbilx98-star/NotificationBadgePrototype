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
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityEvent;
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

    private boolean scanPosted = false;

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

        receiver = new BroadcastReceiver() {

            @Override
            public void onReceive(
                    Context context,
                    Intent intent) {

                if (intent == null) {
                    return;
                }

                String action =
                        intent.getAction();

                /*
                 * Có thông báo mới / thông báo bị xoá.
                 *
                 * QUAN TRỌNG:
                 * Không gọi refreshBadge() ngay tại đây.
                 *
                 * Phải quét Launcher trước để lấy vị trí
                 * icon mới nhất.
                 */
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

                        /*
                         * Bắt buộc quét lại Launcher.
                         */
                        scheduleScan(0);
                    }

                } else if (
                        ACTION_SELECTION_CHANGED.equals(action)) {

                    reloadSelectedLabels();

                    clearUnselectedBadges();

                    /*
                     * App được chọn thay đổi,
                     * quét lại Launcher ngay.
                     */
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

        if (Build.VERSION.SDK_INT >= 33) {

            registerReceiver(
                    receiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
            );

        } else {

            registerReceiver(
                    receiver,
                    filter
            );
        }

        /*
         * Quét lần đầu sau khi Accessibility Service bật.
         */
        scheduleScan(300);
    }

    @Override
    public void onAccessibilityEvent(
            AccessibilityEvent event) {

        if (event == null) {
            return;
        }

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

            /*
             * Launcher vừa được mở / chuyển về màn hình chính.
             */
            if (event.getEventType()
                    == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

                launcherPackage =
                        packageName;
            }

            /*
             * Quét lại icon.
             */
            scheduleScan(80);
        }
    }

    @Override
    public void onInterrupt() {
        // Không cần xử lý.
    }

    private boolean isLauncherPackage(
            String packageName) {

        if (packageName == null) {
            return false;
        }

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
                Math.max(0, delayMs)
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

        /*
         * Fallback cho Launcher 3.
         */
        return "com.android.launcher3";
    }

    private void reloadSelectedLabels() {

        selectedLabels.clear();

        Set<String> selected =
                Prefs.getSelected(this);

        if (selected == null
                || selected.isEmpty()) {

            return;
        }

        PackageManager pm =
                getPackageManager();

        for (String pkg : selected) {

            if (pkg == null) {
                continue;
            }

            try {

                ApplicationInfo ai =
                        pm.getApplicationInfo(
                                pkg,
                                0
                        );

                CharSequence label =
                        ai.loadLabel(pm);

                if (label != null) {

                    String normalized =
                            normalize(
                                    label.toString()
                            );

                    if (!normalized.isEmpty()) {

                        selectedLabels.put(
                                pkg,
                                normalized
                        );
                    }
                }

            } catch (Exception ignored) {
            }
        }
    }

    private void scanLauncher() {

        /*
         * Không có app nào được chọn.
         */
        if (selectedLabels.isEmpty()) {

            hideAllBadges();

            return;
        }

        /*
         * Overlay chưa được cấp quyền.
         */
        if (wm == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= 23
                && !Settings.canDrawOverlays(this)) {

            return;
        }

        AccessibilityNodeInfo root =
                getRootInActiveWindow();

        if (root == null) {
            return;
        }

        try {

            CharSequence rootPkg =
                    root.getPackageName();

            if (rootPkg == null) {
                return;
            }

            String rootPackage =
                    rootPkg.toString();

            /*
             * Nếu root không phải Launcher,
             * không cố đặt badge vào ứng dụng đang mở.
             */
            if (!isLauncherPackage(rootPackage)) {
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

            /*
             * Cập nhật vị trí icon.
             */
            positions.clear();

            positions.putAll(found);

            /*
             * Cập nhật tất cả badge đang có count.
             */
            Set<String> packages =
                    new HashSet<>();

            packages.addAll(counts.keySet());
            packages.addAll(selectedLabels.keySet());

            for (String pkg : packages) {

                refreshBadge(pkg);
            }

        } finally {

            root.recycle();
        }
    }

    private void traverse(
            AccessibilityNodeInfo node,
            Map<String, Rect> found,
            int depth,
            int[] visited) {

        if (node == null) {
            return;
        }

        /*
         * Giới hạn để tránh quét quá sâu / quá nhiều node.
         */
        if (depth > 20
                || visited[0] > 3000) {

            return;
        }

        visited[0]++;

        Rect r =
                new Rect();

        node.getBoundsInScreen(r);

        if (!r.isEmpty()
                && node.isVisibleToUser()) {

            String label =
                    nodeLabel(node);

            if (node.isClickable()
                    && label != null
                    && !label.isEmpty()) {

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

        int childCount =
                node.getChildCount();

        for (int i = 0;
             i < childCount;
             i++) {

            AccessibilityNodeInfo child =
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
            AccessibilityNodeInfo node) {

        if (node == null) {
            return null;
        }

        /*
         * Ưu tiên contentDescription.
         */
        CharSequence cd =
                node.getContentDescription();

        if (cd != null
                && cd.length() > 0) {

            return cd.toString();
        }

        /*
         * Sau đó lấy text.
         */
        CharSequence text =
                node.getText();

        if (text != null
                && text.length() > 0) {

            return text.toString();
        }

        /*
         * Một số Launcher 3 đặt tên app
         * trong node con.
         */
        int children =
                Math.min(
                        node.getChildCount(),
                        6
                );

        for (int i = 0;
             i < children;
             i++) {

            AccessibilityNodeInfo child =
                    node.getChild(i);

            if (child == null) {
                continue;
            }

            try {

                CharSequence c =
                        child.getContentDescription();

                if (c == null
                        || c.length() == 0) {

                    c = child.getText();
                }

                if (c != null
                        && c.length() > 0) {

                    return c.toString();
                }

            } finally {

                child.recycle();
            }
        }

        return null;
    }

    private String packageForLabel(
            String label) {

        String normalized =
                normalize(label);

        if (normalized.isEmpty()) {
            return null;
        }

        String match =
                null;

        for (Map.Entry<String, String> e :
                selectedLabels.entrySet()) {

            String selectedLabel =
                    e.getValue();

            /*
             * So sánh tên app.
             */
            if (normalized.equals(
                    selectedLabel)) {

                /*
                 * Nếu có hai app cùng tên,
                 * không đoán bừa.
                 */
                if (match != null) {
                    return null;
                }

                match =
                        e.getKey();
            }
        }

        return match;
    }

    private String normalize(String s) {

        if (s == null) {
            return "";
        }

        return s.trim()
                .replaceAll(
                        "\\s+",
                        " "
                )
                .toLowerCase();
    }

    private void refreshBadge(
            String pkg) {

        if (pkg == null) {
            return;
        }

        /*
         * App không còn được chọn.
         */
        if (!selectedLabels.containsKey(pkg)) {

            removeBadge(pkg);

            return;
        }

        Integer count =
                counts.get(pkg);

        Rect r =
                positions.get(pkg);

        /*
         * Không có thông báo hoặc chưa tìm thấy icon.
         */
        if (count == null
                || count <= 0
                || r == null
                || r.isEmpty()) {

            TextView old =
                    badges.get(pkg);

            if (old != null) {

                old.setVisibility(
                        View.GONE
                );
            }

            return;
        }

        if (wm == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= 23
                && !Settings.canDrawOverlays(this)) {

            return;
        }

        /*
         * Tính kích thước badge dựa trên icon.
         */
        int size =
                Math.max(
                        18,
                        Math.min(
                                28,
                                (int)
                                        (
                                                Math.min(
                                                        r.width(),
                                                        r.height()
                                                ) * 0.36f
                                        )
                        )
                );

        TextView badge =
                badges.get(pkg);

        if (badge == null) {

            badge =
                    new TextView(this);

            badge.setTextColor(
                    Color.WHITE
            );

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

            lp.width =
                    size;

            lp.height =
                    size;

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

        /*
         * Hiển thị số lượng.
         */
        if (count > 99) {

            badge.setText("99+");

            badge.setTextSize(
                    8
            );

        } else {

            badge.setText(
                    String.valueOf(count)
            );

            badge.setTextSize(
                    10
            );
        }

        badge.setVisibility(
                View.VISIBLE
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

        /*
         * Đặt badge ở góc trên bên phải icon.
         */
        lp.x =
                r.right
                        - (int)
                        (size * 0.72f);

        lp.y =
                r.top
                        - (int)
                        (size * 0.25f);
    }

    private void clearUnselectedBadges() {

        for (String pkg :
                new HashSet<>(
                        badges.keySet()
                )) {

            if (!selectedLabels.containsKey(pkg)) {

                removeBadge(pkg);
            }
        }
    }

    private void hideAllBadges() {

        for (TextView v :
                badges.values()) {

            if (v != null) {

                v.setVisibility(
                        View.GONE
                );
            }
        }
    }

    private void removeBadge(
            String pkg) {

        if (pkg == null) {
            return;
        }

        TextView v =
                badges.remove(pkg);

        if (v != null
                && wm != null) {

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

                unregisterReceiver(
                        receiver
                );

            } catch (Exception ignored) {
            }

            receiver = null;
        }

        for (String pkg :
                new HashSet<>(
                        badges.keySet()
                )) {

            removeBadge(pkg);
        }

        badges.clear();
        counts.clear();
        positions.clear();
        selectedLabels.clear();

        super.onDestroy();
    }
}
