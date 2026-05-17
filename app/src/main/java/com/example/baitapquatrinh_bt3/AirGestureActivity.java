package com.example.baitapquatrinh_bt3;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Nhận diện cử chỉ tay tĩnh (không cần vẽ trên không trung):
 *
 *   👍  THUMBS_UP   → Mở Music Player
 *   👌  OK          → Mở Camera / Google Lens
 *   ☝️  POINTING_UP → Chụp màn hình
 *
 * Cử chỉ phải giữ ổn định trong HOLD_MS mili-giây mới kích hoạt hành động.
 * Sau khi kích hoạt có COOLDOWN_MS mili-giây nghỉ.
 */
public class AirGestureActivity extends AppCompatActivity {

    private static final String TAG = "AirGesture";
    private static final int CAMERA_PERMISSION_REQUEST = 100;
    private static final int SCREEN_CAPTURE_REQUEST    = 101;

    /** Thời gian giữ nguyên cử chỉ để xác nhận (ms) */
    private static final long HOLD_MS     = 1000;
    /** Thời gian nghỉ sau khi thực thi hành động (ms) */
    private static final long COOLDOWN_MS = 2500;

    // ── MediaPipe landmark index ──────────────────────────────────────────
    // Wrist
    private static final int WRIST        = 0;
    // Ngón cái
    private static final int THUMB_CMC    = 1;
    private static final int THUMB_MCP    = 2;
    private static final int THUMB_IP     = 3;
    private static final int THUMB_TIP    = 4;
    // Ngón trỏ
    private static final int INDEX_MCP    = 5;
    private static final int INDEX_PIP    = 6;
    private static final int INDEX_DIP    = 7;
    private static final int INDEX_TIP    = 8;
    // Ngón giữa
    private static final int MIDDLE_MCP   = 9;
    private static final int MIDDLE_PIP   = 10;
    private static final int MIDDLE_DIP   = 11;
    private static final int MIDDLE_TIP   = 12;
    // Ngón áp út
    private static final int RING_MCP     = 13;
    private static final int RING_PIP     = 14;
    private static final int RING_DIP     = 15;
    private static final int RING_TIP     = 16;
    // Ngón út
    private static final int PINKY_MCP    = 17;
    private static final int PINKY_PIP    = 18;
    private static final int PINKY_DIP    = 19;
    private static final int PINKY_TIP    = 20;

    // ── Views ─────────────────────────────────────────────────────────────
    private PreviewView previewView;
    private GestureOverlayView overlayView;
    private TextView txtGestureHint;
    private TextView txtGestureResult;
    private ImageView btnBack;

    // ── MediaPipe / Camera ────────────────────────────────────────────────
    private HandLandmarker handLandmarker;
    private ExecutorService cameraExecutor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ── Trạng thái cử chỉ ────────────────────────────────────────────────
    /** Cử chỉ đang được giữ hiện tại */
    private String currentGesture = null;
    /** Thời điểm bắt đầu giữ cử chỉ hiện tại */
    private long gestureStartTime = 0;
    /** Thời điểm thực thi hành động cuối (để cooldown) */
    private long lastActionTime   = 0;

    // ═════════════════════════════════════════════════════════════════════
    //  onCreate
    // ═════════════════════════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_air_gesture);

        previewView      = findViewById(R.id.previewView);
        overlayView      = findViewById(R.id.overlayView);
        txtGestureHint   = findViewById(R.id.txtGestureHint);
        txtGestureResult = findViewById(R.id.txtGestureResult);
        btnBack          = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        cameraExecutor = Executors.newSingleThreadExecutor();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            initMediaPipe();
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST);
        }

        txtGestureHint.setText("👍 Nhạc  |  👌 Lens  |  ✌️ Google");
    }

    // ═════════════════════════════════════════════════════════════════════
    //  MediaPipe setup
    // ═════════════════════════════════════════════════════════════════════
    private void initMediaPipe() {
        try {
            BaseOptions baseOptions = BaseOptions.builder()
                    .setModelAssetPath("hand_landmarker.task")
                    .build();

            HandLandmarker.HandLandmarkerOptions options =
                    HandLandmarker.HandLandmarkerOptions.builder()
                            .setBaseOptions(baseOptions)
                            .setNumHands(1)
                            .setRunningMode(RunningMode.IMAGE)
                            .build();

            handLandmarker = HandLandmarker.createFromOptions(this, options);
        } catch (Exception e) {
            Log.e(TAG, "MediaPipe init failed: " + e.getMessage());
            showToast("Không tải được model MediaPipe");
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  CameraX setup
    // ═════════════════════════════════════════════════════════════════════
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                analysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

                provider.unbindAll();
                provider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        analysis);

            } catch (Exception e) {
                Log.e(TAG, "Camera start failed: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Phân tích từng frame
    // ═════════════════════════════════════════════════════════════════════
    private void analyzeFrame(@NonNull ImageProxy imageProxy) {
        if (handLandmarker == null) { imageProxy.close(); return; }

        try {
            Bitmap bitmap = imageProxy.toBitmap();
            if (bitmap == null) { imageProxy.close(); return; }

            MPImage mpImage = new BitmapImageBuilder(bitmap).build();
            HandLandmarkerResult result = handLandmarker.detect(mpImage);

            if (result != null && !result.landmarks().isEmpty()) {
                List<NormalizedLandmark> lm = result.landmarks().get(0);
                String detected = classifyGesture(lm);
                mainHandler.post(() -> onGestureDetected(detected));
            } else {
                // Không thấy tay → reset cử chỉ
                mainHandler.post(() -> onGestureDetected(null));
            }

        } catch (Exception e) {
            Log.e(TAG, "Frame analysis error: " + e.getMessage());
        } finally {
            imageProxy.close();
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Xử lý cử chỉ liên tục
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Gọi trên main thread sau mỗi frame.
     * Nếu cùng cử chỉ được giữ đủ HOLD_MS → thực thi hành động.
     */
    private void onGestureDetected(String gesture) {
        long now = System.currentTimeMillis();

        // Đang trong cooldown → bỏ qua
        if (now - lastActionTime < COOLDOWN_MS) return;

        if (gesture == null) {
            // Không thấy tay hoặc không nhận ra
            currentGesture  = null;
            gestureStartTime = 0;
            showLiveHint(null);
            return;
        }

        if (!gesture.equals(currentGesture)) {
            // Cử chỉ mới
            currentGesture   = gesture;
            gestureStartTime = now;
            showLiveHint(gesture);
            return;
        }

        // Cùng cử chỉ — kiểm tra đã giữ đủ lâu chưa
        long held = now - gestureStartTime;
        if (held >= HOLD_MS) {
            lastActionTime   = now;
            currentGesture   = null;
            gestureStartTime = 0;
            triggerAction(gesture);
        }
    }

    /**
     * Hiển thị cử chỉ đang nhận diện + progress bar đơn giản bằng text
     */
    private void showLiveHint(String gesture) {
        if (gesture == null) {
            txtGestureResult.setVisibility(View.GONE);
            return;
        }
        txtGestureResult.setText("Giữ: " + emojiFor(gesture) + " " + gesture);
        txtGestureResult.setVisibility(View.VISIBLE);
    }

    private String emojiFor(String g) {
        switch (g) {
            case "THUMBS_UP":    return "👍";
            case "OK":           return "👌";
            case "PEACE":            return "✌️";
            default:             return "✋";
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Phân loại cử chỉ từ 21 landmarks
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Trả về tên cử chỉ hoặc null nếu không khớp.
     *
     * Trục toạ độ MediaPipe (normalized 0-1):
     *   x: 0 = trái ảnh, 1 = phải ảnh
     *   y: 0 = trên ảnh,  1 = dưới ảnh
     *
     * Ngón tay "duỗi" khi tip.y < pip.y (tip ở TRÊN pip trong ảnh = ngón giơ lên).
     * Ngón cái đặc biệt: duỗi sang trái/phải, kiểm tra x thay vì y.
     */
    private String classifyGesture(List<NormalizedLandmark> lm) {

        // ── Trạng thái từng ngón ─────────────────────────────────────────
        boolean thumbUp   = isThumbUp(lm);
        boolean indexExt  = isFingerExtended(lm, INDEX_TIP,  INDEX_PIP);
        boolean middleExt = isFingerExtended(lm, MIDDLE_TIP, MIDDLE_PIP);
        boolean ringExt   = isFingerExtended(lm, RING_TIP,   RING_PIP);
        boolean pinkyExt  = isFingerExtended(lm, PINKY_TIP,  PINKY_PIP);

        // ── 👍 THUMBS UP ─────────────────────────────────────────────────
        // Ngón cái giơ lên, 4 ngón còn lại gập
        if (thumbUp && !indexExt && !middleExt && !ringExt && !pinkyExt) {
            return "THUMBS_UP";
        }

        // ── ✌️ PEACE ──────────────────────────────────────────────────────
        // Ngón trỏ + ngón giữa duỗi, ngón cái + áp út + út gập
        if (!thumbUp && indexExt && middleExt && !ringExt && !pinkyExt) {
            return "PEACE";
        }

        // ── 👌 OK ─────────────────────────────────────────────────────────
        // Ngón cái + ngón trỏ tạo vòng tròn (chạm nhau), ngón giữa/áp út/út duỗi
        if (isOkGesture(lm) && middleExt && ringExt && pinkyExt) {
            return "OK";
        }

        return null;
    }

    /**
     * Ngón cái "giơ lên" khi tip.y < ip.y (trong ảnh tip phải TRÊN ip).
     * Vì camera trước bị mirror, ta kiểm tra thêm tip.y < mcp.y.
     */
    private boolean isThumbUp(List<NormalizedLandmark> lm) {
        float tipY  = lm.get(THUMB_TIP).y();
        float ipY   = lm.get(THUMB_IP).y();
        float mcpY  = lm.get(THUMB_MCP).y();
        float wristY = lm.get(WRIST).y();

        // Tip phải cao hơn IP và MCP; cũng phải cao hơn wrist để tránh nhầm
        return tipY < ipY && tipY < mcpY && tipY < wristY - 0.05f;
    }

    /**
     * Ngón tay duỗi khi tip ở cao hơn (y nhỏ hơn) pip một ngưỡng nhất định.
     */
    private boolean isFingerExtended(List<NormalizedLandmark> lm, int tipIdx, int pipIdx) {
        return lm.get(tipIdx).y() < lm.get(pipIdx).y() - 0.04f;
    }

    /**
     * Cử chỉ OK: ngón cái và ngón trỏ tạo vòng — khoảng cách tip-to-tip nhỏ.
     */
    private boolean isOkGesture(List<NormalizedLandmark> lm) {
        NormalizedLandmark thumbTip = lm.get(THUMB_TIP);
        NormalizedLandmark indexTip = lm.get(INDEX_TIP);
        float dx = thumbTip.x() - indexTip.x();
        float dy = thumbTip.y() - indexTip.y();
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        return dist < 0.07f; // khoảng 7% chiều rộng ảnh
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Thực thi hành động
    // ═════════════════════════════════════════════════════════════════════
    private void triggerAction(String gesture) {
        showResult(emojiFor(gesture) + " " + gesture);
        Log.d(TAG, "Action triggered: " + gesture);

        switch (gesture) {
            case "THUMBS_UP":
                // 👍 → Mở Music Player
                Intent musicIntent = new Intent(Intent.ACTION_MAIN);
                musicIntent.addCategory(Intent.CATEGORY_APP_MUSIC);
                musicIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(musicIntent);
                } catch (Exception e) {
                    startActivity(new Intent(this, MusicControlActivity.class));
                }
                break;

            case "OK":
                // 👌 → Mở Google Lens / Camera
                try {
                    Intent lensIntent = new Intent(Intent.ACTION_VIEW);
                    lensIntent.setData(Uri.parse("googlelens://"));
                    lensIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(lensIntent);
                } catch (Exception e) {
                    Intent camIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    camIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { startActivity(camIntent); } catch (Exception ignored) {}
                }
                break;

            case "PEACE":
                // ☝️ → Mở Google
                try {
                    Intent googleIntent = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://www.google.com"));
                    googleIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(googleIntent);
                } catch (Exception e) {
                    showToast("Không mở được Google");
                }
                break;
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  UI helpers
    // ═════════════════════════════════════════════════════════════════════
    private void showResult(String msg) {
        mainHandler.post(() -> {
            txtGestureResult.setText("✅ " + msg);
            txtGestureResult.setVisibility(View.VISIBLE);
            mainHandler.postDelayed(
                    () -> txtGestureResult.setVisibility(View.GONE), 2000);
        });
    }

    private void showToast(String msg) {
        mainHandler.post(() ->
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Permissions
    // ═════════════════════════════════════════════════════════════════════
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initMediaPipe();
            startCamera();
        } else {
            showToast("Cần cấp quyền Camera");
            finish();
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Lifecycle
    // ═════════════════════════════════════════════════════════════════════
    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        try { cameraExecutor.awaitTermination(2, TimeUnit.SECONDS); }
        catch (InterruptedException ignored) {}
        if (handLandmarker != null) handLandmarker.close();
    }
}