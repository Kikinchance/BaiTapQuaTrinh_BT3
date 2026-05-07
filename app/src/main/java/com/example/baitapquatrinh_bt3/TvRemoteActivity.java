package com.example.baitapquatrinh_bt3;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.appcompat.app.AppCompatActivity;

public class TvRemoteActivity extends AppCompatActivity {

    private static final String TAG = "TV_REMOTE";

    private TextView txtRemoteStatus;
    private ImageView btnBack;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_remote);

        txtRemoteStatus = findViewById(R.id.txtRemoteStatus);
        btnBack = findViewById(R.id.btnBack);
        mainHandler = new Handler(Looper.getMainLooper());

        btnBack.setOnClickListener(v -> finish());

        // ===== Bind buttons =====
        bindButton(R.id.btnPower, "POWER");
        bindButton(R.id.btnMute, "MUTE");
        bindButton(R.id.btnUp, "UP");
        bindButton(R.id.btnDown, "DOWN");
        bindButton(R.id.btnLeft, "LEFT");
        bindButton(R.id.btnRight, "RIGHT");
        bindButton(R.id.btnOk, "OK");
        bindButton(R.id.btnSource, "SOURCE");

        // Kết nối server qua WifiClient
        connectToServer();
    }

    private void bindButton(@IdRes int buttonId, String command) {
        Button button = findViewById(buttonId);
        if (button == null) return;

        button.setOnClickListener(v -> sendCommand(command));
    }

    // ================= CONNECT TO SERVER =================

    private void connectToServer() {
        WifiClient.connect(success -> runOnUiThread(() -> {
            if (success) {
                txtRemoteStatus.setText("✓ Da ket noi Server");
                Log.d(TAG, "Connected to server");
            } else {
                txtRemoteStatus.setText("✗ Ket noi that bai");
                Log.e(TAG, "Connect failed");
            }
        }));
    }

    // ================= SEND COMMAND =================

    private void sendCommand(String command) {
        if (!WifiClient.isConnected()) {
            txtRemoteStatus.setText("Dang ket noi...");
            connectToServer();
            return;
        }

        WifiClient.sendCommand(command);
        mainHandler.post(() ->
                txtRemoteStatus.setText("✓ Da gui: " + command)
        );
        Log.d(TAG, "Sent: " + command);
    }

    // ================= CLEANUP =================

    @Override
    protected void onDestroy() {
        super.onDestroy();
        WifiClient.disconnect();
    }
}
