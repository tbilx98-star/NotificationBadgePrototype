package com.example.notificationbadge;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.SeekBar;
import androidx.appcompat.app.AppCompatActivity;

public class CalibrationActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration);

        SharedPreferences prefs = getSharedPreferences("BadgePrefs", MODE_PRIVATE);
        SeekBar seekX = findViewById(R.id.seekbar_offset_x);
        SeekBar seekY = findViewById(R.id.seekbar_offset_y);
        Button btnSave = findViewById(R.id.btn_save_calibration);

        seekX.setProgress(prefs.getInt("offsetX", 50));
        seekY.setProgress(prefs.getInt("offsetY", 50));

        btnSave.setOnClickListener(v -> {
            prefs.edit()
                 .putInt("offsetX", seekX.getProgress())
                 .putInt("offsetY", seekY.getProgress())
                 .apply();
            finish();
        });
    }
}
