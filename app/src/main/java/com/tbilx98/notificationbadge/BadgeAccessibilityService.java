package com.tbilx98.notificationbadge;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

    public static final String ACTION_BADGE_CHANGED =
            "com.tbilx98.notificationbadge.BADGE_CHANGED";

    public static final String EXTRA_PACKAGE =
            "package";

    public static final String EXTRA_COUNT =
            "count";

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private WindowManager windowManager;

    /*
     * package -> notification count
     */
    private final Map<String, Integer> notificationCounts =
            new HashMap<>();

    /*
     * package -> badge view
     */
    private final Map<String, TextView> badgeViews =
            new HashMap<>();

    /*
     * package -> vị trí icon cuối cùng
     */
    private final Map<String, Rect> lastPositions =
            new HashMap<>();

    private Set<String> selectedPackages =
            new HashSet<>();

    private Runnable scanRunnable;

    /*
     * Package của launcher/iOS Home.
     *
     * Chỉ được xác định sau khi Accessibility
     * thực sự tìm thấy icon ứng dụng đã chọn.
     */
    private String homePackage;

    private BroadcastReceiver receiver;

    /*
     * Dùng để tránh tạo quá nhiều scan đồng thời.
     */
    private boolean destroyed = false;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();

        destroyed = false;

        windowManager =
                (WindowManager)
                        getSystemService(WINDOW_SERVICE);

        configureAccessibility();

        selectedPackages =
                Prefs.getSelected(this);

        receiver =
                new BroadcastReceiver() {
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
                         * NotificationListener báo count mới.
                         */
                        if (ACTION_BADGE_CHANGED.equals(action)) {

                            String pkg =
                                    intent.getStringExtra(
                                            EXTRA_PACKAGE
                                    );

                            int count =
                                    intent.getIntExtra(
                                            EXTRA_COUNT,
                                            0
                                    );

                            if (pkg == null) {
                                return;
                            }

                            if (count > 0) {

                                notificationCounts.put(
                                        pkg,
                                        count
                                );

                            } else {

                                notificationCounts.remove(
                                        pkg
                                );

                                removeBadge(pkg);
                            }

                            /*
                             * Scan nhanh để đưa badge
                             * lên icon hiện tại.
                             */
                            scheduleScan(30);

                            return;
                        }

                        /*
                         * Người dùng thay đổi danh sách app.
                         */
                        if (ACTION_SELECTION_CHANGED.equals(action)) {

                            selectedPackages =
                                    Prefs.getSelected(
                                            BadgeAccessibilityService.this
                                    );

                            /*
                             * Home có thể vẫn giữ nguyên,
                             * chỉ cần scan lại.
                             */
                            scheduleScan(30);

                            return;
                        }

                        /*
                         * NotificationListener yêu cầu
                         * Accessibility scan lại.
                         */
                        if (ACTION_REFRESH_REQUEST.equals(action)) {

                            scheduleScan(30);
                        }
                    }
                };

        IntentFilter filter =
                new IntentFilter();

        filter.addAction(
                ACTION_BADGE_CHANGED
        );

        filter.addAction(
                ACTION_SELECTION_CHANGED
        );

        filter.addAction(
                ACTION_REFRESH_REQUEST
        );

        registerReceiver(
                receiver,
                filter
        );

        scanRunnable =
                new Runnable() {
                    @Override
                    public void run() {

                        if (!destroyed) {
                            scanCurrentWindow();
                        }
                    }
                };

        /*
         * Yêu cầu NotificationListener gửi lại
         * notification hiện tại.
         */
        sendRefreshRequest();

        scheduleScan(300);
    }

    private void configureAccessibility() {

        AccessibilityServiceInfo info =
                getServiceInfo();

        if (info == null) {
            return;
        }

        /*
         * Quan trọng:
         *
         * VIEW_SCROLLED giúp phát hiện lúc
         * launcher vuốt chuyển trang.
         */
        info.eventTypes =
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                        | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                        | AccessibilityEvent.TYPE_WINDOWS_CHANGED
                        | AccessibilityEvent.TYPE_VIEW_SCROLLED
                        | AccessibilityEvent.TYPE_VIEW_CLICKED;

        info.feedbackType =
                AccessibilityServiceInfo.FEEDBACK_GENERIC;

        /*
         * Giảm debounce để badge bám theo launcher
         * nhanh hơn.
         */
        info.notificationTimeout = 50;

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

        if (scanRunnable == null
                || destroyed) {
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

    private void sendRefreshRequest() {

        Intent intent =
                new Intent(
                        ACTION_REFRESH_REQUEST
                );

        intent.setPackage(
                getPackageName()
        );

        sendBroadcast(intent);
    }

    @Override
    public void onAccessibilityEvent(
            AccessibilityEvent event) {

        if (event == null
                || destroyed) {
            return;
        }

        int type =
                event.getEventType();

        /*
         * Khi launcher thay đổi trang:
         *
         * TYPE_VIEW_SCROLLED
         * TYPE_WINDOW_CONTENT_CHANGED
         * TYPE_WINDOWS_CHANGED
         *
         * đều kích hoạt scan.
         */
        if (type ==
                AccessibilityEvent.TYPE_VIEW_SCROLLED
                || type ==
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                || type ==
                AccessibilityEvent.TYPE_WINDOWS_CHANGED
                || type ==
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

            /*
             * Scan ngay sau animation một khoảng rất ngắn.
             */
            scheduleScan(35);

            /*
             * Một scan bổ sung sau animation.
             * Điều này giúp launcher clone có thời gian
             * cập nhật cây Accessibility.
             */
            handler.postDelayed(
                    () -> {
                        if (!destroyed) {
                            scanCurrentWindow();
                        }
                    },
                    140
            );
        }
    }

    /*
     * Scan cửa sổ hiện tại.
     */
    private void scanCurrentWindow() {

        if (destroyed
                || windowManager == null) {
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

            /*
             * Lấy package của cửa sổ hiện tại.
             */
            String currentPackage =
                    root.getPackageName() == null
                            ? null
                            : root.getPackageName()
                                    .toString();

            /*
             * Nếu đã biết launcher package
             * và hiện tại đang ở app khác:
             *
             * XÓA BADGE NGAY.
             *
             * Không chờ 4 lần scan nữa.
             */
            if (homePackage != null
                    && currentPackage != null
                    && !homePackage.equals(
                            currentPackage
                    )) {

                clearAllBadges();

                return;
            }

            Map<String, String> labels =
                    getSelectedLabels();

            if (labels.isEmpty()) {
                clearAllBadges();
                return;
            }

            Map<String, Rect> positions =
                    new HashMap<>();

            collectIconPositions(
                    root,
                    labels,
                    positions,
                    new HashSet<String>()
            );

            /*
             * Nếu tìm thấy icon app đã chọn,
             * cửa sổ hiện tại được xem là Home.
             */
            if (!positions.isEmpty()) {

                if (currentPackage != null) {

                    if (homePackage == null) {

                        homePackage =
                                currentPackage;

                    } else if (!homePackage.equals(
                            currentPackage
                    )) {

                        /*
                         * Không bao giờ đổi Home package
                         * sang package của app.
                         */
                        clearAllBadges();
                        return;
                    }
                }

                updateBadges(
                        positions
                );

                return;
            }

            /*
             * Không tìm thấy icon.
             *
             * Nếu current package khác Home,
             * chắc chắn đang ở ứng dụng khác.
             */
            if (homePackage != null
                    && currentPackage != null
                    && !homePackage.equals(
                            currentPackage
                    )) {

                clearAllBadges();
            }

        } catch (Exception ignored) {

        } finally {

            root.recycle();
        }
    }

    /*
     * Lấy label thật của các app được chọn.
     */
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

    /*
     * Tìm icon theo label.
     *
     * Quan trọng:
     * Không lưu vị trí cố định.
     * Mỗi lần scan đều lấy Rect mới.
     */
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

        if (!value.isEmpty()) {

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
                        findBestIconBounds(node);

                if (iconBounds != null
                        && isPlausibleIconBounds(
                                iconBounds
                        )) {

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
     * Tìm bounds tốt nhất cho icon.
     *
     * Bản cũ lấy ancestor clickable đầu tiên.
     * Điều đó dễ lấy nhầm container lớn.
     *
     * Bản này chấm điểm các ancestor:
     * - phải clickable
     * - không quá lớn
     * - ưu tiên hình gần vuông
     * - ưu tiên kích thước giống icon
     */
    private Rect findBestIconBounds(
            AccessibilityNodeInfo node) {

        if (node == null) {
            return null;
        }

        Rect labelBounds =
                new Rect();

        node.getBoundsInScreen(
                labelBounds
        );

        AccessibilityNodeInfo current =
                node;

        Rect best =
                null;

        double bestScore =
                Double.MAX_VALUE;

        for (int level = 0;
                level < 6;
                level++) {

            if (current == null) {
                break;
            }

            Rect r =
                    new Rect();

            current.getBoundsInScreen(
                    r
            );

            if (!r.isEmpty()
                    && r.width() > 0
                    && r.height() > 0
                    && r.width() <= 220
                    && r.height() <= 220
                    && current.isClickable()) {

                float ratio =
                        (float) r.width()
                                / (float) r.height();

                if (ratio < 1f) {
                    ratio = 1f / ratio;
                }

                /*
                 * Hình càng vuông càng tốt.
                 */
                double aspectScore =
                        Math.abs(
                                1.0 - ratio
                        ) * 100.0;

                /*
                 * Ưu tiên icon không quá nhỏ.
                 */
                double sizeScore =
                        Math.abs(
                                Math.max(
                                        r.width(),
                                        r.height()
                                ) - 80
                        );

                /*
                 * Không chọn container nằm quá xa
                 * label.
                 */
                int centerX =
                        r.centerX();

                int centerY =
                        r.centerY();

                int labelCenterX =
                        labelBounds.centerX();

                int labelCenterY =
                        labelBounds.centerY();

                double distance =
                        Math.sqrt(
                                Math.pow(
                                        centerX
                                                - labelCenterX,
                                        2
                                )
                                        +
                                Math.pow(
                                        centerY
                                                - labelCenterY,
                                        2
                                )
                        );

                double score =
                        aspectScore
                                + sizeScore * 0.15
                                + distance * 0.02;

                if (score < bestScore) {

                    bestScore =
                            score;

                    best =
                            new Rect(r);
                }
            }

            AccessibilityNodeInfo parent =
                    current.getParent();

            if (current != node) {
                current.recycle();
            }

            current =
                    parent;
        }

        if (current != null
                && current != node) {

            current.recycle();
        }

        /*
         * Nếu không tìm được clickable container,
         * dùng bounds của node.
         */
        if (best == null
                && !labelBounds.isEmpty()) {

            best =
                    new Rect(
                            labelBounds
                    );
        }

        return best;
    }

    private boolean isPlausibleIconBounds(
            Rect r) {

        if (r == null
                || r.isEmpty()) {
            return false;
        }

        if (r.width() < 25
                || r.height() < 25) {
            return false;
        }

        if (r.width() > 220
                || r.height() > 220) {
            return false;
        }

        return true;
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

    /*
     * Cập nhật badge dựa trên vị trí icon
     * MỚI NHẤT.
     */
    private void updateBadges(
            Map<String, Rect> positions) {

        /*
         * Chỉ giữ badge của app thật sự xuất hiện
         * trên trang hiện tại.
         */
        Set<String> visiblePackages =
                new HashSet<>(
                        positions.keySet()
                );

        /*
         * Tạo / di chuyển badge.
         */
        for (Map.Entry<String, Rect> entry :
                positions.entrySet()) {

            String pkg =
                    entry.getKey();

            Rect position =
                    entry.getValue();

            Integer count =
                    notificationCounts.get(pkg);

            /*
             * Không có notification:
             * không tạo badge.
             */
            if (count == null
                    || count <= 0) {

                removeBadge(pkg);

                continue;
            }

            TextView badge =
                    badgeViews.get(pkg);

            if (badge == null) {

                createBadge(
                        pkg,
                        position,
                        count
                );

            } else {

                /*
                 * LUÔN cập nhật vị trí.
                 *
                 * Không đợi position khác.
                 * Điều này giúp launcher clone
                 * cập nhật animation/page tốt hơn.
                 */
                moveBadge(
                        badge,
                        position
                );

                lastPositions.put(
                        pkg,
                        new Rect(position)
                );

                updateBadgeText(
                        badge,
                        count
                );
            }
        }

        /*
         * Badge của app không còn xuất hiện
         * trên trang hiện tại phải biến mất.
         *
         * Không giữ lại tọa độ cũ.
         */
        List<String> existing =
                new ArrayList<>(
                        badgeViews.keySet()
                );

        for (String pkg :
                existing) {

            if (!visiblePackages.contains(
                    pkg
            )) {

                removeBadge(pkg);
            }
        }
    }

    private void createBadge(
            String pkg,
            Rect iconBounds,
            int count) {

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

        badge.setIncludeFontPadding(
                false
        );

        GradientDrawable bg =
                new GradientDrawable();

        bg.setColor(
                0xFFFF3B30
        );

        bg.setShape(
                GradientDrawable.OVAL
        );

        badge.setBackground(
                bg
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
                                |
                        WindowManager.LayoutParams
                                .FLAG_NOT_TOUCHABLE,
                        PixelFormat.TRANSLUCENT
                );

        lp.gravity =
                Gravity.TOP
                        | Gravity.LEFT;

        /*
         * Badge nằm đè lên góc trên bên phải.
         *
         * Tâm badge nằm ngay tại góc icon.
         */
        lp.x =
                iconBounds.right
                        - (size / 2);

        lp.y =
                iconBounds.top
                        - (size / 2);

        /*
         * Không cho badge ra ngoài vùng màn hình
         * ở phía trái/trên.
         */
        if (lp.x < 0) {
            lp.x = 0;
        }

        if (lp.y < 0) {
            lp.y = 0;
        }

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

        if (badge == null
                || windowManager == null
                || iconBounds == null) {

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
                            - (size / 2);

            lp.y =
                    iconBounds.top
                            - (size / 2);

            if (lp.x < 0) {
                lp.x = 0;
            }

            if (lp.y < 0) {
                lp.y = 0;
            }

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

    /*
     * Xóa badge NGAY.
     */
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

        List<String> list =
                new ArrayList<>(
                        badgeViews.keySet()
                );

        for (String pkg :
                list) {

            removeBadge(pkg);
        }
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

        destroyed = true;

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

        clearAllBadges();

        super.onDestroy();
    }
}
