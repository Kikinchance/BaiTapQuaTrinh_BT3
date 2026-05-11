package com.example.baitapquatrinh_bt3;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class VolumeControlActivity extends AppCompatActivity {

    private SensorManager sensorManager;
    private Sensor rotationVectorSensor;

    private TextView txtStatus;
    private ImageView btnBack;

    private long lastTime = 0; // chống spam

    // Ngưỡng góc xoay quanh trục Y (roll) để kích hoạt
    private static final float ROLL_THRESHOLD_DEG = 15f;
    private static final long COOLDOWN_MS = 500L;

    private final float[] rotationMatrix = new float[9];
    private final float[] orientation = new float[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_volume_control);

        btnBack = findViewById(R.id.btnBack);
        txtStatus = findViewById(R.id.txtStatus);

        btnBack.setOnClickListener(v -> finish());

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    private final SensorEventListener listener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;

            long now = System.currentTimeMillis();
            if (now - lastTime < COOLDOWN_MS) return;

            AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (audioManager == null) return;

            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientation);

            // orientation[2] = roll (rad) ~ xoay quanh trục Y thiết bị
            float rollDeg = (float) Math.toDegrees(orientation[2]);

            if (rollDeg > -ROLL_THRESHOLD_DEG) {
                audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI
                );
                txtStatus.setText("🔊 TĂNG ÂM LƯỢNG");
                WifiClient.sendCommand("VOLUME_UP");
                lastTime = now;

            } else if (rollDeg < ROLL_THRESHOLD_DEG) {
                audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_SHOW_UI
                );
                txtStatus.setText("🔉 GIẢM ÂM LƯỢNG");
                WifiClient.sendCommand("VOLUME_DOWN");
                lastTime = now;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // no-op
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(
                    listener,
                    rotationVectorSensor,
                    SensorManager.SENSOR_DELAY_NORMAL
            );
        } else {
            txtStatus.setText("Thiết bị không hỗ trợ Rotation Vector");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(listener);
    }
}
