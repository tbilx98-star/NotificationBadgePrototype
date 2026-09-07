package com.tbilx98.notificationbadge;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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

    public static final String ACTION_REFRESH_REQUEST =
            "com.tbilx98.notificationbadge.REFRESH_REQUEST";

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private WindowManager windowManager;

    private final Map<String, Integer> notificationCounts =
            new HashMap<>();

    private final Map<String, Rect> lastPositions =
            new HashMap<>();

    private final Map<String, TextView> badgeViews =
            new HashMap<>();

    private Set<String> selectedPackages =
            new HashSet<>();

    private String homePackage;

    private boolean isHomeVisible = false;

    private boolean scanRunning = false;

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

        findHomePackage();

        scanRunnable =
                new Runnable() {
                    @Override
                    public void run() {
                        scanRunning = false;
                        scanHome();
                    }
                };

        /*
         * Yêu cầu NotificationListener gửi lại
         * toàn bộ notification đang tồn tại.
         */
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

        info.notificationTimeout =
                300;

        info.flags |=
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;

        if (Build.VERSION.SDK_INT >= 21) {

            info.flags |=
                    AccessibilityServiceInfo
                            .FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        }

        setServiceInfo(info);
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

        /*
         * Window state changed:
         * xác định lại có đang ở Home hay không.
         */
        if (event.getEventType()
                == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

            updateHomeState();

            scheduleScan(100);

            return;
        }

        /*
         * Content changed xảy ra liên tục khi
         * vuốt Home, animation, icon refresh...
         *
         * Không quét ngay lập tức từng event.
         * Gom chúng lại thành một lần quét.
         */
        scheduleScan(180);
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

            if (home != isHomeVisible) {

                isHomeVisible = home;

                if (!home) {

                    clearAllBadges();

                } else {

                    scheduleScan(100);
                }
            }

        } finally {

            root.recycle();
        }
    }

    private boolean isHomePackage(
            String packageName) {

        if (packageName == null
                || packageName.isEmpty()) {

            return false;
        }

        if (homePackage != null
                && packageName.equals(homePackage)) {

            return true;
        }

        /*
         * Một số clone iPhone không trả về đúng
         * package của launcher qua resolveActivity.
         *
         * Nếu chưa xác định được Home package,
         * dùng các dấu hiệu thông thường của launcher.
         */
        if (homePackage == null) {

            String p =
                    packageName.toLowerCase();

            return p.contains("launcher")
                    || p.contains("home");
        }

        return false;
    }

    private void scanHome() {

        if (windowManager == null) {
            return;
        }

        AccessibilityNodeInfo root =
                getRootInActiveWindow();

        if (root == null) {

            clearAllBadges();

            return;
        }

        try {

            CharSequence packageName =
                    root.getPackageName();

            String currentPackage =
                    packageName == null
                            ? ""
                            : packageName.toString();

            if (!isHomePackage(
                    currentPackage)) {

                if (isHomeVisible) {

                    isHomeVisible = false;
                    clearAllBadges();
                }

                return;
            }

            isHomeVisible = true;

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

            updateBadges(
                    positions
            );

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

                    String normalized =
                            normalize(
                                    label.toString()
                            );

                    if (!normalized.isEmpty()) {

                        result.put(
                                pkg,
                                normalized
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

        /*
         * Chỉ chấp nhận node có bounds hợp lệ.
         */
        Rect bounds =
                new Rect();

        node.getBoundsInScreen(
                bounds
        );

        if (!bounds.isEmpty()
                && bounds.width() > 0
                && bounds.height() > 0
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

                /*
                 * QUAN TRỌNG:
                 * Không còn contains hai chiều.
                 *
                 * Phải khớp chính xác tên ứng dụng
                 * hoặc dạng "Tên ứng dụng ...".
                 */
                if (isSameAppLabel(
                        value,
                        label)) {

                    /*
                     * Icon Home thường có kích thước
                     * tương đối nhỏ.
                     *
                     * Loại bỏ các node text/container
                     * quá lớn để tránh bắt nhầm.
                     */
                    if (bounds.width() <= 300
                            && bounds.height() <= 300) {

                        positions.put(
                                pkg,
                                new Rect(bounds)
                        );

                        found.add(pkg);
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

    private boolean isSameAppLabel(
            String value,
            String label) {

        if (value.equals(label)) {
            return true;
        }

        /*
         * Một số launcher đọc icon thành:
         * "Facebook, button"
         * hoặc "Facebook app"
         */
        if (value.startsWith(
                label + ","
        )) {
            return true;
        }

        if (value.startsWith(
                label + " "
        )) {

            String remainder =
                    value.substring(
                            label.length()
                    ).trim();

            return remainder.equals("app")
                    || remainder.equals("application")
                    || remainder.equals("button");
        }

        return false;
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
                    notificationCounts.get(
                            pkg
                    );

            if (count == null
                    || count <= 0) {

                continue;
            }

            active.add(pkg);

            TextView existing =
                    badgeViews.get(pkg);

            Rect old =
                    lastPositions.get(pkg);

            if (existing == null) {

                createBadge(
                        pkg,
                        position,
                        count
                );

            } else {

                /*
                 * Chỉ di chuyển badge khi icon
                 * thực sự đổi vị trí.
                 */
                if (old == null
                        || !old.equals(position)) {

                    moveBadge(
                            existing,
                            position
                    );
                }

                updateBadgeText(
                        existing,
                        count
                );
            }

            lastPositions.put(
                    pkg,
                    new Rect(position)
            );
        }

        /*
         * Xóa badge không còn notification
         * hoặc không còn nhìn thấy icon.
         */
        List<String> toRemove =
                new ArrayList<>();

        for (String pkg :
                badgeViews.keySet()) {

            if (!active.contains(pkg)) {
                toRemove.add(pkg);
            }
        }

        for (String pkg :
                toRemove) {

            removeBadge(pkg);
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

        badge.setTextSize(
                9
        );

        badge.setGravity(
                Gravity.CENTER
        );

        GradientDrawable bg =
                new GradientDrawable();

        bg.setColor(
                0xFFFF3B30
        );

        bg.setShape(
                GradientDrawable.OVAL
        );

        badge.setBackground(bg);

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
         *
         * Badge hơi đè vào icon,
         * giống kiểu badge iOS.
         */
        lp.x =
                iconBounds.right
                        - size / 2;

        lp.y =
                iconBounds.top
                        - size / 2;

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

        WindowManager.LayoutParams lp =
                (WindowManager.LayoutParams)
                        badge.getLayoutParams();

        int size =
                badge.getWidth();

        if (size <= 0) {
            size = dp(20);
        }

        lp.x =
                iconBounds.right
                        - size / 2;

        lp.y =
                iconBounds.top
                        - size / 2;

        try {

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

            setBadgeText(
                    badge,
                    count
            );
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
    }

    private int dp(int value) {

        return (int)
                (
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
