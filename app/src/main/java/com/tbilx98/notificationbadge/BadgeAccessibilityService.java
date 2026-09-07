package com.tbilx98.notificationbadge;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
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
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import java.util.HashSet;
import java.util.Set;

public class BadgeAccessibilityService extends AccessibilityService {

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private WindowManager wm;
    private TextView debugView;

    private final Runnable hideRunnable =
            new Runnable() {
                @Override
                public void run() {
                    if (debugView != null) {
                        debugView.setVisibility(View.GONE);
                    }
                }
            };

    @Override
    public void onServiceConnected() {

        super.onServiceConnected();

        wm = (WindowManager)
                getSystemService(WINDOW_SERVICE);

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

        createDebugView();

        handler.postDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        scanScreen();
                    }
                },
                500
        );
    }

    @Override
    public void onAccessibilityEvent(
            AccessibilityEvent event) {

        if (event == null) {
            return;
        }

        handler.removeCallbacks(hideRunnable);

        handler.postDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        scanScreen();
                    }
                },
                100
        );
    }

    private void createDebugView() {

        if (wm == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= 23
                && !Settings.canDrawOverlays(this)) {
            return;
        }

        debugView =
                new TextView(this);

        debugView.setTextColor(
                Color.WHITE
        );

        debugView.setTextSize(
                12
        );

        debugView.setGravity(
                Gravity.CENTER_VERTICAL
        );

        debugView.setPadding(
                16,
                10,
                16,
                10
        );

        debugView.setBackgroundColor(
                Color.argb(
                        220,
                        0,
                        0,
                        0
                )
        );

        debugView.setText(
                "Accessibility debug..."
        );

        WindowManager.LayoutParams lp =
                new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        PixelFormat.TRANSLUCENT
                );

        lp.gravity =
                Gravity.TOP | Gravity.CENTER_HORIZONTAL;

        lp.y = 40;

        try {

            wm.addView(
                    debugView,
                    lp
            );

        } catch (Exception ignored) {

            debugView = null;
        }
    }

    private void scanScreen() {

        if (debugView == null) {
            return;
        }

        AccessibilityNodeInfo root =
                getRootInActiveWindow();

        if (root == null) {

            showDebug(
                    "ROOT = NULL\n"
                            + "Accessibility cannot read current screen"
            );

            return;
        }

        try {

            CharSequence packageName =
                    root.getPackageName();

            String pkg =
                    packageName == null
                            ? "NULL"
                            : packageName.toString();

            ScanResult result =
                    new ScanResult();

            collectNodes(
                    root,
                    result,
                    0
            );

            StringBuilder text =
                    new StringBuilder();

            text.append(
                    "PACKAGE:\n"
            );

            text.append(
                    pkg
            );

            text.append(
                    "\n\nNODES: "
            );

            text.append(
                    result.nodeCount
            );

            text.append(
                    "\nTEXT: "
            );

            text.append(
                    result.textCount
            );

            text.append(
                    "\nCLICKABLE: "
            );

            text.append(
                    result.clickableCount
            );

            text.append(
                    "\n\nNAMES:\n"
            );

            int shown = 0;

            for (String name :
                    result.names) {

                text.append(
                        name
                );

                text.append(
                        "\n"
                );

                shown++;

                if (shown >= 12) {
                    break;
                }
            }

            if (shown == 0) {

                text.append(
                        "(none)"
                );
            }

            showDebug(
                    text.toString()
            );

        } finally {

            root.recycle();
        }
    }

    private void collectNodes(
            AccessibilityNodeInfo node,
            ScanResult result,
            int depth) {

        if (node == null) {
            return;
        }

        if (depth > 20) {
            return;
        }

        if (result.nodeCount >= 2000) {
            return;
        }

        result.nodeCount++;

        CharSequence text =
                node.getText();

        CharSequence description =
                node.getContentDescription();

        if ((text != null
                && text.length() > 0)
                || (description != null
                && description.length() > 0)) {

            result.textCount++;

            String value;

            if (description != null
                    && description.length() > 0) {

                value =
                        description.toString();

            } else {

                value =
                        text.toString();
            }

            value =
                    value.trim();

            if (!value.isEmpty()) {

                result.names.add(
                        value
                );
            }
        }

        if (node.isClickable()) {

            result.clickableCount++;
        }

        int childCount =
                node.getChildCount();

        for (int i = 0;
             i < childCount;
             i++) {

            AccessibilityNodeInfo child =
                    node.getChild(i);

            if (child != null) {

                collectNodes(
                        child,
                        result,
                        depth + 1
                );

                child.recycle();
            }
        }
    }

    private void showDebug(
            String text) {

        if (debugView == null) {
            return;
        }

        debugView.setText(
                text
        );

        debugView.setVisibility(
                View.VISIBLE
        );

        handler.removeCallbacks(
                hideRunnable
        );

        handler.postDelayed(
                hideRunnable,
                5000
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

        if (debugView != null
                && wm != null) {

            try {

                wm.removeView(
                        debugView
                );

            } catch (Exception ignored) {
            }

            debugView = null;
        }

        super.onDestroy();
    }

    private static class ScanResult {

        int nodeCount = 0;

        int textCount = 0;

        int clickableCount = 0;

        Set<String> names =
                new HashSet<>();
    }
}
