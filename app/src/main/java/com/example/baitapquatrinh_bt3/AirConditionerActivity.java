package com.example.baitapquatrinh_bt3;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class AirConditionerActivity extends AppCompatActivity {

    private static final String KEY_POWER_ON = "key_power_on";

    private TextView txtPowerStatus;
    private Button btnAcPower;

    private boolean isPowerOn = false;
    private boolean isSending = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_air_conditioner);

        ImageView btnBack = findViewById(R.id.btnBack);
        txtPowerStatus = findViewById(R.id.txtPowerStatus);
        btnAcPower = findViewById(R.id.btnAcPower);

        if (savedInstanceState != null) {
            isPowerOn = savedInstanceState.getBoolean(KEY_POWER_ON, false);
        }

        updatePowerUi();

        btnBack.setOnClickListener(v -> finish());

        btnAcPower.setOnClickListener(v -> {
            if (isSending) return;
            boolean targetState = !isPowerOn;
            sendPowerCommand(targetState);
        });
    }

    private void updatePowerUi() {
        if (isPowerOn) {
            txtPowerStatus.setText("Trang thai: BAT");
            btnAcPower.setText("TAT MAY LANH");
            btnAcPower.setBackgroundTintList(getColorStateList(android.R.color.holo_red_dark));
        } else {
            txtPowerStatus.setText("Trang thai: TAT");
            btnAcPower.setText("BAT MAY LANH");
            btnAcPower.setBackgroundTintList(getColorStateList(android.R.color.holo_green_dark));
        }
    }

    private void setSendingState(boolean sending) {
        isSending = sending;
        btnAcPower.setEnabled(!sending);
        btnAcPower.setAlpha(sending ? 0.7f : 1f);
    }

    private void sendPowerCommand(boolean powerOn) {
        final String command = powerOn ? "AC_POWER_ON" : "AC_POWER_OFF";

        if (WifiClient.isConnected()) {
            WifiClient.sendCommand(command);
            isPowerOn = powerOn;
            updatePowerUi();
            txtPowerStatus.setText("Da gui lenh len server: " + command);
            return;
        }

        setSendingState(true);
        txtPowerStatus.setText("Dang ket noi server...");

        WifiClient.connect(success -> runOnUiThread(() -> {
            setSendingState(false);

            if (success) {
                WifiClient.sendCommand(command);
                isPowerOn = powerOn;
                updatePowerUi();
                txtPowerStatus.setText("Da ket noi va gui: " + command);
            } else {
                txtPowerStatus.setText("Khong ket noi duoc server");
            }
        }));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_POWER_ON, isPowerOn);
    }
}
