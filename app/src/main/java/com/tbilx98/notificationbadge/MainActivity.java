package com.tbilx98.notificationbadge;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView status;

    @Override
    public void onCreate(Bundle b) {

        super.onCreate(b);

        setContentView(
                R.layout.activity_main
        );

        status =
                findViewById(R.id.status);

        findViewById(
                R.id.notificationAccess
        ).setOnClickListener(v ->
                startActivity(
                        new Intent(
                                "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
                        )
                )
        );

        findViewById(
                R.id.overlayAccess
        ).setOnClickListener(v -> {

            if (android.os.Build.VERSION.SDK_INT >= 23) {

                Intent i =
                        new Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse(
                                        "package:"
                                                + getPackageName()
                                )
                        );

                startActivity(i);
            }
        });

        findViewById(
                R.id.accessibilityAccess
        ).setOnClickListener(v ->
                startActivity(
                        new Intent(
                                Settings.ACTION_ACCESSIBILITY_SETTINGS
                        )
                )
        );

        findViewById(
                R.id.appList
        ).setOnClickListener(v ->
                startActivity(
                        new Intent(
                                this,
                                AppListActivity.class
                        )
                )
        );
    }

    @Override
    protected void onResume() {

        super.onResume();

        updateStatus();
    }

    private void updateStatus() {

        boolean overlay =
                android.os.Build.VERSION.SDK_INT < 23
                        || Settings.canDrawOverlays(this);

        ComponentName cn =
                new ComponentName(
                        this,
                        NotificationListener.class
                );

        String enabled =
                Settings.Secure.getString(
                        getContentResolver(),
                        "enabled_notification_listeners"
                );

        boolean listener =
                enabled != null
                        && enabled.contains(
                                cn.flattenToString()
                        );

        ComponentName ac =
                new ComponentName(
                        this,
                        BadgeAccessibilityService.class
                );

        String accessibility =
                Settings.Secure.getString(
                        getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                );

        boolean access =
                accessibility != null
                        && accessibility.contains(
                                ac.flattenToString()
                        );

        status.setText(
                "Notification access: "
                        + (listener ? "ON" : "OFF")
                        + "\nOverlay permission: "
                        + (overlay ? "ON" : "OFF")
                        + "\nAccessibility: "
                        + (access ? "ON" : "OFF")
                        + "\nSelected apps: "
                        + Prefs.getSelected(this).size()
        );
    }
}
