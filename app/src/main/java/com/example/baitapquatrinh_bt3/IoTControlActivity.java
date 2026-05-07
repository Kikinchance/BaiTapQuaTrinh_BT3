package com.example.baitapquatrinh_bt3;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class IoTControlActivity extends AppCompatActivity {

    private TextView txtIotStatus;
    private LinearLayout btnSamsungTv;
    private LinearLayout btnAirConditioner;
    private ImageView btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_iot_control);

        txtIotStatus = findViewById(R.id.txtIotStatus);
        btnSamsungTv = findViewById(R.id.btnSamsungTv);
        btnAirConditioner = findViewById(R.id.btnAirConditioner);
        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        btnSamsungTv.setOnClickListener(v ->
                startActivity(new Intent(IoTControlActivity.this, TvRemoteActivity.class)));

        btnAirConditioner.setOnClickListener(v ->
                startActivity(new Intent(IoTControlActivity.this, AirConditionerActivity.class)));
    }

    private void sendIotCommand(String command, String deviceName) {
        if (WifiClient.isConnected()) {
            WifiClient.sendCommand(command);
            txtIotStatus.setText("✅ Đã gửi lệnh tới " + deviceName);
            return;
        }

        txtIotStatus.setText("⏳ Đang kết nối server...");
        WifiClient.connect(success -> runOnUiThread(() -> {
            if (success) {
                WifiClient.sendCommand(command);
                txtIotStatus.setText("✅ Đã kết nối và gửi lệnh tới " + deviceName);
            } else {
                txtIotStatus.setText("❌ Kết nối thất bại");
            }
        }));
    }
}
