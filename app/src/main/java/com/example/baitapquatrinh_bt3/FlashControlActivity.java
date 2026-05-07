package com.example.baitapquatrinh_bt3;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

public class FlashControlActivity extends AppCompatActivity {

    TextView txtStatus;

    boolean isLightOn = false;
    long lastTriggerTime = 0;

    CameraManager cameraManager;
    String cameraId;

    byte[] lastFrame = null;
    ImageView btnBack;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flash_control);

        txtStatus = findViewById(R.id.txtStatus);
        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());
        // xin quyền
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, 1);
            return;
        }

        initFlash();
        startCamera();
    }

    // 🔦 lấy flash
    void initFlash() {
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            for (String id : cameraManager.getCameraIdList()) {
                cameraId = id;
                break;
            }
        } catch (Exception e) {
            txtStatus.setText("Lỗi camera");
        }
    }

    // 📷 mở camera trước
    void startCamera() {

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), image -> {

                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] currentFrame = new byte[buffer.remaining()];
                    buffer.get(currentFrame);

                    detectMotion(currentFrame);

                    image.close();
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // detect vẫy tay
    void detectMotion(byte[] currentFrame) {

        if (lastFrame == null) {
            lastFrame = currentFrame;
            return;
        }

        int diff = 0;

        for (int i = 0; i < currentFrame.length; i += 300) {
            diff += Math.abs((currentFrame[i] & 0xFF) - (lastFrame[i] & 0xFF));
        }

        long now = System.currentTimeMillis();

        if (now - lastTriggerTime < 800) {
            lastFrame = currentFrame;
            return;
        }

        if (diff > 50000) {

            lastTriggerTime = now;

            isLightOn = !isLightOn;

            if (isLightOn) {
                turnOnFlash();
                txtStatus.setText("🔆 ĐÈN ĐANG BẬT");
            } else {
                turnOffFlash();
                txtStatus.setText("🌙 ĐÈN ĐANG TẮT");
            }
        }

        lastFrame = currentFrame;
    }

    void turnOnFlash() {
        try {
            cameraManager.setTorchMode(cameraId, true);
            txtStatus.setText("🔆 BẬT FLASH");
            WifiClient.sendCommand("LIGHT_ON");
        } catch (Exception e) {}
    }

    void turnOffFlash() {
        try {
            cameraManager.setTorchMode(cameraId, false);
            txtStatus.setText("🌙 TẮT FLASH");
            WifiClient.sendCommand("LIGHT_OFF");
        } catch (Exception e) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            cameraManager.setTorchMode(cameraId, false);
        } catch (Exception ignored) {}
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 1 && grantResults.length > 0) {
            initFlash();
            startCamera();
        }
    }
}