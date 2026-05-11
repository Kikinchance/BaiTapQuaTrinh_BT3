package com.example.baitapquatrinh_bt3;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraManager;
import android.os.Build;
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
    private ClapDetector clapDetector;
    private ImageView btnMicrophone;
    CameraManager cameraManager;
    String cameraId;
    byte[] lastFrame = null;
    ImageView btnBack;

    private static final int PERMISSION_REQUEST_CODE = 100;
    private boolean isClapDetectionEnabled = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flash_control);

        txtStatus = findViewById(R.id.txtStatus);
        btnBack = findViewById(R.id.btnBack);
        btnMicrophone = findViewById(R.id.btnMicrophone);

        // ===== XIN QUYỀN =====
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                            != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.CAMERA,
                                Manifest.permission.RECORD_AUDIO
                        },
                        PERMISSION_REQUEST_CODE);
                return;
            }
        }

        initializeApp();
    }

    private void initializeApp() {
        // ===== KẾT NỐI WIFI =====
        WifiClient.connect(success -> {
            if (success) {
                txtStatus.post(() -> txtStatus.setText("✅ Connected to Server"));
                android.util.Log.d("FlashControl", "WifiClient connected to server");
            } else {
                txtStatus.post(() -> txtStatus.setText("❌ Connection Failed"));
                android.util.Log.w("FlashControl", "WifiClient failed to connect to server " + WifiClient.isConnected());
            }
        });

        // ===== CLAP DETECTION =====
        clapDetector = new ClapDetector(new ClapDetector.ClapCallback() {
            @Override
            public void onClapDetected(int clapCount) {
                handleClap(clapCount);
            }

            @Override
            public void onError(String error) {
                txtStatus.post(() -> txtStatus.setText("❌ " + error));
            }

            @Override
            public void onListening() {
                txtStatus.post(() -> txtStatus.setText("👂 Listening for claps..."));
            }
        });

        // Nút để bắt đầu/dừng nhận diện vỗ tay
        btnMicrophone.setOnClickListener(v -> {
            if (isClapDetectionEnabled) {
                clapDetector.stopListening();
                isClapDetectionEnabled = false;
                txtStatus.setText("⏸️ Clap detection stopped");
                btnMicrophone.setImageAlpha(128); // Làm mờ icon
            } else {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                clapDetector.startListening();
                isClapDetectionEnabled = true;
                txtStatus.setText("👂 Clap detection active");
                btnMicrophone.setImageAlpha(255); // Sáng bình thường
            }
        });

        btnBack.setOnClickListener(v -> finish());

        // Bắt đầu nhận diện vỗ tay
        clapDetector.startListening();

        // Manual test: long-press status text to toggle torch (helps debug without clap)
        txtStatus.setOnLongClickListener(v -> {
            isLightOn = !isLightOn;
            if (isLightOn) {
                turnOnFlash();
                txtStatus.setText("Long-press test → 🔆 LIGHT ON");
            } else {
                turnOffFlash();
                txtStatus.setText("Long-press test → 🌙 LIGHT OFF");
            }
            return true;
        });

        initFlash();
        startCamera();
    }

    // ===== XỬ LÝ PHÁT HIỆN VỖ TAY =====
    private void handleClap(int clapCount) {
        switch (clapCount) {
            case 1:
                // 1 vỗ = Toggle (bật/tắt xen kẽ)
                isLightOn = !isLightOn;
                if (isLightOn) {
                    turnOnFlash();
                    txtStatus.setText("👏 Single clap → 🔆 LIGHT ON");
                } else {
                    turnOffFlash();
                    txtStatus.setText("👏 Single clap → 🌙 LIGHT OFF");
                }
                break;

            case 2:
                // 2 vỗ = Tắt flash
                turnOffFlash();
                txtStatus.setText("👏👏 Double clap → 🌙 LIGHT OFF");
                break;

            case 3:
                // 3 vỗ = Bật flash
                turnOnFlash();
                txtStatus.setText("👏👏👏 Triple clap → 🔆 LIGHT ON");
                break;

            default:
                txtStatus.setText("👏 " + clapCount + " claps detected");
        }
    }

    // 📷 Mở camera trước
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

    // Detect vẫy tay
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
                txtStatus.setText("✋ Wave detected → 🔆 LIGHT ON");
            } else {
                turnOffFlash();
                txtStatus.setText("✋ Wave detected → 🌙 LIGHT OFF");
            }
        }

        lastFrame = currentFrame;
    }

    void turnOnFlash() {
        try {
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, true);
                android.util.Log.d("FlashControl", "Torch ON, cameraId=" + cameraId);
            } else {
                android.util.Log.w("FlashControl", "No cameraId available to set torch ON");
            }
            WifiClient.sendCommand("LIGHT_ON");
        } catch (Exception e) {
            final String msg = "Error: " + e.getMessage();
            android.util.Log.e("FlashControl", msg, e);
            txtStatus.post(() -> txtStatus.setText("❌ " + msg));
        }
    }

    void turnOffFlash() {
        try {
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, false);
                android.util.Log.d("FlashControl", "Torch OFF, cameraId=" + cameraId);
            } else {
                android.util.Log.w("FlashControl", "No cameraId available to set torch OFF");
            }
            WifiClient.sendCommand("LIGHT_OFF");
        } catch (Exception e) {
            final String msg = "Error: " + e.getMessage();
            android.util.Log.e("FlashControl", msg, e);
            txtStatus.post(() -> txtStatus.setText("❌ " + msg));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                initializeApp();
            } else {
                txtStatus.setText("❌ Permissions required");
            }
        }
    }

    void initFlash() {
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            // Choose the first camera that reports a flash unit (torch)
            for (String id : cameraManager.getCameraIdList()) {
                Boolean hasFlash = cameraManager.getCameraCharacteristics(id)
                        .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (hasFlash != null && hasFlash) {
                    cameraId = id;
                    break;
                }
            }
            // fallback to first camera if none report FLASH_INFO_AVAILABLE
            if (cameraId == null) {
                String[] ids = cameraManager.getCameraIdList();
                if (ids.length > 0) cameraId = ids[0];
            }
        } catch (Exception e) {
            txtStatus.setText("Camera error");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (clapDetector != null && isClapDetectionEnabled) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            clapDetector.startListening();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (clapDetector != null) {
            clapDetector.stopListening();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (clapDetector != null) {
            clapDetector.destroy();
        }
        WifiClient.disconnect();
    }
}
