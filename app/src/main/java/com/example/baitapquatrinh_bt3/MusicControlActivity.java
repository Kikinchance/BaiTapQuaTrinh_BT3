package com.example.baitapquatrinh_bt3;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MusicControlActivity extends AppCompatActivity {

    SensorManager sensorManager;
    Sensor accelerometer;

    TextView txtStatus;

    long lastTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music_control);

        txtStatus = findViewById(R.id.txtStatus);

        // nút back
        ImageView btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    SensorEventListener listener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {

            float x = event.values[0];

            long now = System.currentTimeMillis();

            // chống spam
            if (now - lastTime < 1000) return;

            if (x > 6) {
                txtStatus.setText("⏭️ NEXT (Spotify)");
                nextTrack();
                lastTime = now;

            } else if (x < -6) {
                txtStatus.setText("⏮️ PREVIOUS (Spotify)");
                prevTrack();
                lastTime = now;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    // 👉 Spotify nhận media key
    void nextTrack() {
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        audioManager.dispatchMediaKeyEvent(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT));
        audioManager.dispatchMediaKeyEvent(
                new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT));
    }

    void prevTrack() {
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        audioManager.dispatchMediaKeyEvent(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS));
        audioManager.dispatchMediaKeyEvent(
                new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS));
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(listener);
    }
}