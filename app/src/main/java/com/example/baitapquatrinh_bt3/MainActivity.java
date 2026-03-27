package com.example.baitapquatrinh_bt3;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public class MainActivity extends AppCompatActivity {

    LinearLayout btnA, btnB, btnC, btnD, btnDarkMode;
    TextView txtMode, txtTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 🔥 LOAD TRẠNG THÁI DARK MODE TRƯỚC
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        boolean isDark = prefs.getBoolean("dark_mode", false);

        if (isDark) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        setContentView(R.layout.activity_main);

        // ánh xạ button
        btnA = findViewById(R.id.btnA);
        btnB = findViewById(R.id.btnB);
        btnC = findViewById(R.id.btnC);

        // 🔥 ánh xạ dark mode
        btnDarkMode = findViewById(R.id.btnDarkMode);
        txtMode = findViewById(R.id.txtMode);
        txtTitle = findViewById(R.id.txtTitle);
        if (isDark) {
            txtTitle.setTextColor(Color.WHITE);
        } else {
            txtTitle.setTextColor(Color.BLACK);
        }
        // set text ban đầu
        if (isDark) {
            txtMode.setText("Chế độ sáng");
        } else {
            txtMode.setText("Chế độ tối");
        }

        // click đổi mode
        btnDarkMode.setOnClickListener(v -> {
            boolean current = prefs.getBoolean("dark_mode", false);
            SharedPreferences.Editor editor = prefs.edit();

            if (current) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                editor.putBoolean("dark_mode", false);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                editor.putBoolean("dark_mode", true);
            }

            editor.apply();
        });

        // chuyển màn
        btnA.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, FlashControlActivity.class));
        });

        btnB.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, VolumeControlctivity.class));
        });

        btnC.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, MusicControlActivity.class));
        });
    }
}