package com.example.baitapquatrinh_bt3;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MusicControlActivity extends AppCompatActivity {

    private SensorManager sensorManager;
    private Sensor gyroSensor;

    private TextView txtStatus;

    // ===== CONFIG =====
    private static final float THRESHOLD = 1.5f;     // rad/s (do nhay)
    private static final long COOLDOWN_MS = 800;     // chong spam
    private static final float FILTER_ALPHA = 0.8f;  // loc nhieu

    // ===== STATE =====
    private float filteredX = 0f;
    private long lastTriggerTime = 0;

    private enum State {
        IDLE,
        LOCKED
    }

    private State state = State.IDLE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music_control);

        txtStatus = findViewById(R.id.txtStatus);

        ImageView btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        if (gyroSensor == null) {
            txtStatus.setText("Thiết bị không hỗ trợ Gyroscope!");
        }
    }

    private final SensorEventListener listener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {

            float rawZ = event.values[2]; // truc Z

            // ===== LOW PASS FILTER =====
            filteredX = FILTER_ALPHA * filteredX + (1 - FILTER_ALPHA) * rawZ;

            long now = SystemClock.elapsedRealtime();

            // ===== COOLDOWN =====
            if (now - lastTriggerTime < COOLDOWN_MS) return;

            switch (state) {

                case IDLE:

                    if (filteredX > THRESHOLD) {
                        onPrevious();
                        state = State.LOCKED;
                        lastTriggerTime = now;

                    } else if (filteredX < -THRESHOLD) {
                        onNext();
                        state = State.LOCKED;
                        lastTriggerTime = now;
                    }

                    break;

                case LOCKED:

                    // reset khi toc do quay gan 0
                    if (Math.abs(filteredX) < 0.2f) {
                        state = State.IDLE;
                    }

                    break;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    // ===== ACTION =====

    private void onNext() {
        txtStatus.setText("⏭️ NEXT SONG");
        sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT);
        WifiClient.sendCommand("NEXT");
    }

    private void onPrevious() {
        txtStatus.setText("⏮️ PREVIOUS SONG");
        sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        WifiClient.sendCommand("PREVIOUS");
    }

    private void sendMediaKey(int keyCode) {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am == null) return;

        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    }

    // ===== LIFECYCLE =====

    @Override
    protected void onResume() {
        super.onResume();
        if (gyroSensor != null) {
            sensorManager.registerListener(
                    listener,
                    gyroSensor,
                    SensorManager.SENSOR_DELAY_GAME
            );
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(listener);

        filteredX = 0f;
        state = State.IDLE;
    }
}
