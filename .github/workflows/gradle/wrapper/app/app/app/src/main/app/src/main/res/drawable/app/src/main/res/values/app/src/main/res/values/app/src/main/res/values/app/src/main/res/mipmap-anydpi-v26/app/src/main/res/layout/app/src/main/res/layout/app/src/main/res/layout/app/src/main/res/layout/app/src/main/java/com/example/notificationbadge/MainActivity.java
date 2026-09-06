package com.example.notificationbadge;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnNotif = findViewById(R.id.btn_permission_notification);
        Button btnOverlay = findViewById(R.id.btn_permission_overlay);
        Button btnSelectApps = findViewById(R.id.btn_select_apps);
        Button btnCalibration = findViewById(R.id.btn_calibration);
        Button btnToggleService = findViewById(R.id.btn_toggle_service);

        btnNotif.setOnClickListener(v -> {
            Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            startActivity(intent);
        });

        btnOverlay.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });

        btnSelectApps.setOnClickListener(v -> {
            startActivity(new Intent(this, AppListActivity.class));
        });

        btnCalibration.setOnClickListener(v -> {
            startActivity(new Intent(this, CalibrationActivity.class));
        });

        btnToggleService.setOnClickListener(v -> {
            Intent serviceIntent = new Intent(this, BadgeOverlayService.class);
            startForegroundService(serviceIntent);
            Toast.makeText(this, "Badge Service Started", Toast.LENGTH_SHORT).show();
        });
    }
}
