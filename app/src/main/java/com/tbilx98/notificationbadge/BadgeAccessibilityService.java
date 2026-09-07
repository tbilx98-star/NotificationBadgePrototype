package com.tbilx98.notificationbadge;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
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

    public static final String ACTION_REFRESH_REQUEST =
            "com.tbilx98.notificationbadge.REFRESH_REQUEST";

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private WindowManager windowManager;

    private final Map<String, Integer> notificationCounts =
            new HashMap<>();

    private final Map<String, TextView> badgeViews =
            new HashMap<>();

    private final Map<String, Rect> lastPositions =
            new HashMap<>();

    private final Map<String, Integer> missingScans =
            new HashMap<>();

    private Set<String> selectedPackages =
            new HashSet<>();

    private String homePackage;

    private boolean homeVisible = false;

    private Runnable scanRunnable;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();

        windowManager =
                (WindowManager) getSystemService(WINDOW_SERVICE);

        configureAccessibility();

        selectedPackages = Prefs.getSelected(this);

        findHomePackage();

        scanRunnable = new Runnable() {
            @Override
            public void run() {
                scanHome();
            }
        };

        sendRefreshRequest();

        scheduleScan(500);
    }

    private void configureAccessibility() {

        AccessibilityServiceInfo info = getServiceInfo();

        if (info == null) {
            return;
        }

        info.eventTypes =
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                        | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                        | AccessibilityEvent.TYPE_WINDOWS_CHANGED;

        info.feedbackType =
                AccessibilityServiceInfo.FEEDBACK_GENERIC;

        info.notificationTimeout = 200;

        info.flags |=
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;

        if (android.os.Build.VERSION.SDK_INT >= 21) {
            info.flags |=
                    AccessibilityServiceInfo
                            .FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        }

        setServiceInfo(info);
    }

    /*
     * Đây là hàm bị thiếu trong file cũ.
     */
    private void scheduleScan(long delay) {

        if (scanRunnable == null) {
            return;
        }

        handler.removeCallbacks(scanRunnable);
        handler.postDelayed(scanRunnable, delay);
    }

    private void findHomePackage() {

        try {

            Intent intent =
                    new Intent(Intent.ACTION_MAIN);

            intent.addCategory(
                    Intent.CATEGORY_HOME
            );

            ComponentName component =
                    intent.resolveActivity(
                            getPackageManager()
                    );

            if (component != null) {
                homePackage =
                        component.getPackageName();
            }

        } catch (Exception ignored) {
        }
    }

    private void sendRefreshRequest() {

        try {

            Intent intent =
                    new Intent(ACTION_REFRESH_REQUEST);

            intent.setPackage(getPackageName());

            sendBroadcast(intent);

        } catch (Exception ignored) {
        }
    }

    @Override
    public void onAccessibilityEvent(
            AccessibilityEvent event) {

        if (event == null) {
            return;
        }

        int type = event.getEventType();

        if (type ==
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

            updateHomeState();

            scheduleScan(150);

            return;
        }

        /*
         * Khi vuốt giữa các trang Home,
         * launcher phát rất nhiều CONTENT_CHANGED.
         *
         * Không quét ngay từng event.
         */
        scheduleScan(250);
    }

    private void updateHomeState() {

        AccessibilityNodeInfo root =
                getRootInActiveWindow();

        if (root == null) {
            return;
        }

        try {

            CharSequence packageName =
                    root.getPackageName();

            String currentPackage =
                    packageName == null
                            ? ""
                            : packageName.toString();

            boolean home =
                    isHomePackage(currentPackage);

            if (home != homeVisible) {

                homeVisible = home;

                if (!home) {
                    clearAllBadges();
                } else {
                    scheduleScan(150);
                }
            }

        } finally {

            root.recycle();
        }
    }

    private boolean isHomePackage(
            String packageName) {

        if (packageName == null
                || packageName.length() == 0) {

            return false;
        }

        if (homePackage != null) {

            return packageName.equals(
                    homePackage
            );
        }

        String p =
                packageName.toLowerCase();

        return p.contains("launcher")
                || p.contains("home");
    }

    private void scanHome() {

        if (windowManager == null) {
            return;
        }

        AccessibilityNodeInfo root =
                getRootInActiveWindow();

        if (root == null) {
            return;
        }

        try {

            CharSequence packageName =
                    root.getPackageName();

            String currentPackage =
                    packageName == null
                            ? ""
                            : packageName.toString();

            /*
             * Tuyệt đối không scan khi đang mở app.
             */
            if (!isHomePackage(currentPackage)) {

                if (homeVisible) {
                    homeVisible = false;
                    clearAllBadges();
                }

                return;
            }

            homeVisible = true;

            selectedPackages =
                    Prefs.getSelected(this);

            if (selectedPackages == null
                    || selectedPackages.isEmpty()) {

                clearAllBadges();
                return;
            }

            Map<String, String> labels =
                    getSelectedLabels();

            Map<String, Rect> positions =
                    new HashMap<>();

            Set<String> found =
                    new HashSet<>();

            collectIconPositions(
                    root,
                    labels,
                    positions,
                    found
            );

            updateBadges(positions);

        } finally {

            root.recycle();
        }
    }

    private Map<String, String>
    getSelectedLabels() {

        Map<String, String> result =
                new HashMap<>();

        PackageManager pm =
                getPackageManager();

        for (String pkg :
                selectedPackages) {

            try {

                CharSequence label =
                        pm.getApplicationLabel(
                                pm.getApplicationInfo(
                                        pkg,
                                        0
                                )
                        );

                if (label != null) {

                    String name =
                            normalize(
                                    label.toString()
                            );

                    if (!name.isEmpty()) {
                        result.put(pkg, name);
                    }
                }

            } catch (Exception ignored) {
            }
        }

        return result;
    }

    private void collectIconPositions(
            AccessibilityNodeInfo node,
            Map<String, String> labels,
            Map<String, Rect> positions,
            Set<String> found) {

        if (node == null) {
            return;
        }

        String value = "";

        CharSequence description =
                node.getContentDescription();

        CharSequence text =
                node.getText();

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

        Rect bounds =
                new Rect();

        node.getBoundsInScreen(bounds);

        if (!bounds.isEmpty()
                && bounds.width() > 0
                && bounds.height() > 0
                && bounds.width() <= 300
                && bounds.height() <= 300
                && !value.isEmpty()) {

            for (Map.Entry<String, String> entry :
                    labels.entrySet()) {

                String pkg =
                        entry.getKey();

                String label =
                        entry.getValue();

                if (found.contains(pkg)) {
                    continue;
                }

                if (value.equals(label)
                        || value.startsWith(label + ",")
                        || value.equals(label + " app")
                        || value.equals(label + " application")
                        || value.equals(label + " button")) {

                    /*
                     * Ưu tiên container clickable.
                     * Đây giúp lấy bounds của cả icon
                     * thay vì chỉ lấy bounds chữ.
                     */
                    Rect iconBounds =
                            findClickableBounds(node);

                    if (iconBounds == null) {
                        iconBounds =
                                new Rect(bounds);
                    }

                    positions.put(
                            pkg,
                            iconBounds
                    );

                    found.add(pkg);
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

                collectIconPositions(
                        child,
                        labels,
                        positions,
                        found
                );

                child.recycle();
            }
        }
    }

    /*
     * Tìm node clickable bao quanh tên app.
     */
    private Rect findClickableBounds(
            AccessibilityNodeInfo node) {

        AccessibilityNodeInfo current =
                node;

        for (int i = 0; i < 4; i++) {

            if (current == null) {
                break;
            }

            if (current.isClickable()) {

                Rect r =
                        new Rect();

                current.getBoundsInScreen(r);

                if (!r.isEmpty()
                        && r.width() <= 400
                        && r.height() <= 400) {

                    if (current != node) {
                        current.recycle();
                    }

                    return r;
                }
            }

            AccessibilityNodeInfo parent =
                    current.getParent();

            if (current != node) {
                current.recycle();
            }

            current = parent;
        }

        if (current != null
                && current != node) {
            current.recycle();
        }

        return null;
    }

    private String normalize(
            String value) {

        return value
                .trim()
                .toLowerCase()
                .replaceAll(
                        "\\s+",
                        " "
                );
    }

    private void updateBadges(
            Map<String, Rect> positions) {

        /*
         * Những badge đã tìm thấy trong lần scan này.
         */
        Set<String> active =
                new HashSet<>();

        for (Map.Entry<String, Rect> entry :
                positions.entrySet()) {

            String pkg =
                    entry.getKey();

            Rect position =
                    entry.getValue();

            Integer count =
                    notificationCounts.get(pkg);

            if (count == null
                    || count <= 0) {

                removeBadge(pkg);
                continue;
            }

            active.add(pkg);

            missingScans.put(pkg, 0);

            TextView badge =
                    badgeViews.get(pkg);

            if (badge == null) {

                createBadge(
                        pkg,
                        position,
                        count
                );

            } else {

                Rect old =
                        lastPositions.get(pkg);

                if (old == null
                        || !old.equals(position)) {

                    moveBadge(
                            badge,
                            position
                    );

                    lastPositions.put(
                            pkg,
                            new Rect(position)
                    );
                }

                updateBadgeText(
                        badge,
                        count
                );
            }
        }

        /*
         * Không xóa badge ngay khi icon biến mất
         * trong một frame animation.
         *
         * Phải mất 3 lần scan liên tiếp mới xóa.
         */
        List<String> existing =
                new ArrayList<>(
                        badgeViews.keySet()
                );

        for (String pkg : existing) {

            if (active.contains(pkg)) {
                continue;
            }

            Integer missing =
                    missingScans.get(pkg);

            if (missing == null) {
                missing = 0;
            }

            missing++;

            missingScans.put(
                    pkg,
                    missing
            );

            if (missing >= 3) {
                removeBadge(pkg);
            }
        }
    }

    private void createBadge(
            String pkg,
            Rect iconBounds,
            int count) {

        if (windowManager == null) {
            return;
        }

        TextView badge =
                new TextView(this);

        badge.setTextColor(
                0xFFFFFFFF
        );

        badge.setTextSize(9);

        badge.setGravity(
                Gravity.CENTER
        );

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

        int size = dp(20);

        WindowManager.LayoutParams lp =
                new WindowManager.LayoutParams(
                        size,
                        size,
                        WindowManager.LayoutParams
                                .TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams
                                .FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams
                                .FLAG_NOT_TOUCHABLE,
                        PixelFormat.TRANSLUCENT
                );

        lp.gravity =
                Gravity.TOP | Gravity.LEFT;

        /*
         * Góc trên-phải của icon.
         */
        lp.x =
                iconBounds.right
                        - size;

        lp.y =
                iconBounds.top;

        setBadgeText(
                badge,
                count
        );

        try {

            windowManager.addView(
                    badge,
                    lp
            );

            badgeViews.put(
                    pkg,
                    badge
            );

            lastPositions.put(
                    pkg,
                    new Rect(iconBounds)
            );

            missingScans.put(
                    pkg,
                    0
            );

        } catch (Exception ignored) {
        }
    }

    private void moveBadge(
            TextView badge,
            Rect iconBounds) {

        if (windowManager == null
                || badge == null) {

            return;
        }

        try {

            WindowManager.LayoutParams lp =
                    (WindowManager.LayoutParams)
                            badge.getLayoutParams();

            int size = dp(20);

            lp.x =
                    iconBounds.right
                            - size;

            lp.y =
                    iconBounds.top;

            windowManager.updateViewLayout(
                    badge,
                    lp
            );

        } catch (Exception ignored) {
        }
    }

    private void updateBadgeText(
            TextView badge,
            int count) {

        if (badge == null) {
            return;
        }

        String text =
                count > 99
                        ? "99+"
                        : String.valueOf(count);

        if (!text.equals(
                badge.getText().toString()
        )) {

            badge.setText(text);
        }
    }

    private void setBadgeText(
            TextView badge,
            int count) {

        badge.setText(
                count > 99
                        ? "99+"
                        : String.valueOf(count)
        );
    }

    private void removeBadge(
            String pkg) {

        TextView badge =
                badgeViews.remove(pkg);

        if (badge != null
                && windowManager != null) {

            try {

                windowManager.removeView(
                        badge
                );

            } catch (Exception ignored) {
            }
        }

        lastPositions.remove(pkg);
        missingScans.remove(pkg);
    }

    private void clearAllBadges() {

        List<String> packages =
                new ArrayList<>(
                        badgeViews.keySet()
                );

        for (String pkg :
                packages) {

            removeBadge(pkg);
        }

        lastPositions.clear();
        missingScans.clear();
    }

    private int dp(int value) {

        return (int) (
                value
                        * getResources()
                                .getDisplayMetrics()
                                .density
                        + 0.5f
        );
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {

        handler.removeCallbacksAndMessages(null);

        clearAllBadges();

        super.onDestroy();
    }
}
