package com.tbilx98.notificationbadge;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
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

    /*
     * Package thực sự đang cung cấp giao diện Home.
     * Không lấy cứng từ CATEGORY_HOME vì clone iPhone
     * có thể dùng một system package riêng.
     */
    private String detectedHomePackage = null;

    private boolean scanning = false;

    private Runnable scanRunnable;

    @Override
    public void onServiceConnected() {

        super.onServiceConnected();

        windowManager =
                (WindowManager)
                        getSystemService(WINDOW_SERVICE);

        configureAccessibility();

        selectedPackages =
                Prefs.getSelected(this);

        scanRunnable =
                new Runnable() {
                    @Override
                    public void run() {
                        scanning = false;
                        scanCurrentWindow();
                    }
                };

        sendRefreshRequest();

        scheduleScan(500);
    }

    private void configureAccessibility() {

        AccessibilityServiceInfo info =
                getServiceInfo();

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

    private void scheduleScan(long delay) {

        if (scanRunnable == null) {
            return;
        }

        handler.removeCallbacks(scanRunnable);

        handler.postDelayed(
                scanRunnable,
                delay
        );
    }

    private void sendRefreshRequest() {

        try {

            Intent intent =
                    new Intent(
                            ACTION_REFRESH_REQUEST
                    );

            intent.setPackage(
                    getPackageName()
            );

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

        scheduleScan(250);
    }

    /*
     * Không kiểm tra package Home trước nữa.
     *
     * Đây là điểm quan trọng đối với iPhone clone.
     */
    private void scanCurrentWindow() {

        if (windowManager == null) {
            return;
        }

        AccessibilityNodeInfo root =
                getRootInActiveWindow();

        if (root == null) {
            return;
        }

        try {

            selectedPackages =
                    Prefs.getSelected(this);

            if (selectedPackages == null
                    || selectedPackages.isEmpty()) {

                clearAllBadges();

                return;
            }

            Map<String, String> labels =
                    getSelectedLabels();

            if (labels.isEmpty()) {
                return;
            }

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

            /*
             * Không tìm thấy app nào trong cửa sổ hiện tại.
             *
             * Nếu đang ở trong một app khác thì xóa badge.
             * Nếu Home đang animation thì giữ badge lại.
             */
            if (positions.isEmpty()) {

                if (detectedHomePackage != null) {

                    CharSequence pkg =
                            root.getPackageName();

                    String current =
                            pkg == null
                                    ? ""
                                    : pkg.toString();

                    if (!current.equals(
                            detectedHomePackage
                    )) {

                        clearAllBadges();
                    }
                }

                return;
            }

            /*
             * Nếu tìm thấy từ 2 app được chọn trở lên,
             * đây gần như chắc chắn là Home.
             *
             * Lưu package để những lần sau biết
             * package nào là giao diện Home.
             */
            if (positions.size() >= 2) {

                CharSequence pkg =
                        root.getPackageName();

                if (pkg != null) {

                    detectedHomePackage =
                            pkg.toString();
                }
            }

            /*
             * Nếu đã xác định Home package,
             * không tạo badge trong app khác.
             */
            if (detectedHomePackage != null) {

                CharSequence pkg =
                        root.getPackageName();

                String current =
                        pkg == null
                                ? ""
                                : pkg.toString();

                if (!current.equals(
                        detectedHomePackage
                )) {

                    clearAllBadges();

                    return;
                }
            }

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

    private void collectIconPositions(
            AccessibilityNodeInfo node,
            Map<String, String> labels,
            Map<String, Rect> positions,
            Set<String> found) {

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

        Rect bounds =
                new Rect();

        node.getBoundsInScreen(bounds);

        if (!bounds.isEmpty()
                && bounds.width() > 0
                && bounds.height() > 0
                && bounds.width() <= 400
                && bounds.height() <= 400
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

                if (!isSameLabel(
                        value,
                        label
                )) {
                    continue;
                }

                Rect iconBounds =
                        findBestBounds(node);

                if (iconBounds != null
                        && !iconBounds.isEmpty()) {

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

    private boolean isSameLabel(
            String value,
            String label) {

        if (value.equals(label)) {
            return true;
        }

        if (value.startsWith(
                label + ","
        )) {
            return true;
        }

        if (value.equals(
                label + " app"
        )) {
            return true;
        }

        if (value.equals(
                label + " application"
        )) {
            return true;
        }

        if (value.equals(
                label + " button"
        )) {
            return true;
        }

        return false;
    }

    /*
     * Lấy bounds của node clickable bao quanh
     * tên/icon ứng dụng.
     */
    private Rect findBestBounds(
            AccessibilityNodeInfo node) {

        AccessibilityNodeInfo current =
                node;

        for (int level = 0;
                level < 4;
                level++) {

            if (current == null) {
                break;
            }

            Rect r =
                    new Rect();

            current.getBoundsInScreen(r);

            if (current.isClickable()
                    && !r.isEmpty()
                    && r.width() <= 500
                    && r.height() <= 500) {

                if (current != node) {
                    current.recycle();
                }

                return r;
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

        Rect fallback =
                new Rect();

        node.getBoundsInScreen(
                fallback
        );

        return fallback;
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

            missingScans.put(
                    pkg,
                    0
            );

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
         * Cho phép mất icon tối đa vài lần scan
         * trong lúc vuốt Home.
         */
        List<String> existing =
                new ArrayList<>(
                        badgeViews.keySet()
                );

        for (String pkg :
                existing) {

            if (active.contains(pkg)) {
                continue;
            }

            int missing =
                    missingScans.containsKey(pkg)
                            ? missingScans.get(pkg)
                            : 0;

            missing++;

            missingScans.put(
                    pkg,
                    missing
            );

            if (missing >= 4) {

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

        int size =
                dp(20);

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
                Gravity.TOP
                        | Gravity.LEFT;

        /*
         * Góc trên bên phải icon.
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

            int size =
                    dp(20);

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

        handler.removeCallbacksAndMessages(
                null
        );

        clearAllBadges();

        super.onDestroy();
    }
}
