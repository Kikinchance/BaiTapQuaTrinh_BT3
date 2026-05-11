package com.example.baitapquatrinh_bt3;

import android.Manifest;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.RequiresPermission;

public class ClapDetector {

    private AudioRecord audioRecord;
    private boolean isListening = false;
    private ClapCallback callback;
    private Handler mainHandler;

    private static final int SAMPLE_RATE = 44100;
    // getMinBufferSize returns bytes for PCM_16. We will convert to short[] length when creating buffer.
    private static final int MIN_BUFFER_BYTES = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

    // Detection tuning - you can adjust these if your device is noisy
    // Lowered threshold to be more sensitive on quieter devices. If you get false positives, increase this.
    private static final int CLAP_THRESHOLD = 1200;  // peak amplitude threshold (short sample absolute)
    private static final int CLAP_COOLDOWN = 200;    // ms, minimum time between detected peaks
    private static final int CLAP_RESET_MS = 800;    // ms, wait time to group claps before reporting

    private long lastClapTime = 0;
    private int clapCount = 0;
    private Runnable clapResetRunnable = null;

    public interface ClapCallback {
        void onClapDetected(int clapCount);
        void onError(String error);
        void onListening();
    }

    public ClapDetector(ClapCallback callback) {
        this.callback = callback;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    public void startListening() {
        if (isListening) return;

        try {
            int bufferBytes = MIN_BUFFER_BYTES > 0 ? MIN_BUFFER_BYTES : 4096;
            // AudioRecord expects buffer size in bytes. We already computed MIN_BUFFER_BYTES.
            audioRecord = new AudioRecord(
                    // Use VOICE_RECOGNITION audio source which often provides better handling for
                    // transient sounds on many devices (and may disable AGC/echo cancellation).
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferBytes
            );

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e("ClapDetector", "AudioRecord not initialized, state=" + audioRecord.getState());
                if (callback != null) callback.onError("Microphone not available (AudioRecord init failed)");
                return;
            }

            audioRecord.startRecording();
            Log.d("ClapDetector", "AudioRecord started, bufferBytes=" + bufferBytes + " sampleRate=" + SAMPLE_RATE);
            isListening = true;

            if (callback != null) callback.onListening();

            // Bắt đầu thread nhận diện
            new Thread(this::detectClap).start();

        } catch (Exception e) {
            if (callback != null) callback.onError("Error: " + e.getMessage());
        }
    }

    public void stopListening() {
        isListening = false;
        
        // Hủy callback nếu có
        if (clapResetRunnable != null) {
            mainHandler.removeCallbacks(clapResetRunnable);
            clapResetRunnable = null;
        }
        
        clapCount = 0;
        lastClapTime = 0;
        
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
    }

    private void detectClap() {
        // Ensure buffer size is reasonable (convert bytes -> shorts). Fallback to 2048 shorts if min unknown.
        int shortBufferSize = 2048;
        if (MIN_BUFFER_BYTES > 0) shortBufferSize = Math.max( MIN_BUFFER_BYTES / 2, 1024 );

        short[] buffer = new short[shortBufferSize];

        Log.d("ClapDetector", "detectClap started, shortBufferSize=" + shortBufferSize + " minBytes=" + MIN_BUFFER_BYTES);

        while (isListening) {
            try {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0) {
                    // compute peak and RMS
                    long sum = 0;
                    int peak = 0;
                    for (int i = 0; i < read; i++) {
                        int v = buffer[i];
                        int abs = Math.abs(v);
                        if (abs > peak) peak = abs;
                        sum += (long) v * v;
                    }
                    double rms = Math.sqrt((double) sum / read);

                    long now = System.currentTimeMillis();

                    // Debug: occasionally log values (only in debug builds you can remove later)
                    Log.d("ClapDetector", "peak=" + peak + " rms=" + (int) rms + " read=" + read);

                    // Detect clap by peak (more reliable for short transient)
                    if (peak > CLAP_THRESHOLD) {
                        if (now - lastClapTime > CLAP_COOLDOWN) {
                            lastClapTime = now;
                            clapCount = 1;

                            if (clapResetRunnable != null) {
                                mainHandler.removeCallbacks(clapResetRunnable);
                            }

                            clapResetRunnable = () -> {
                                if (clapCount > 0 && callback != null) {
                                    callback.onClapDetected(clapCount);
                                }
                                clapCount = 0;
                                clapResetRunnable = null;
                            };
                            mainHandler.postDelayed(clapResetRunnable, CLAP_RESET_MS);

                        } else if (now - lastClapTime <= 500) {
                            // second clap within short window
                            clapCount++;
                            lastClapTime = now;

                            if (clapResetRunnable != null) {
                                mainHandler.removeCallbacks(clapResetRunnable);
                            }
                            clapResetRunnable = () -> {
                                if (clapCount > 0 && callback != null) {
                                    callback.onClapDetected(clapCount);
                                }
                                clapCount = 0;
                                clapResetRunnable = null;
                            };
                            mainHandler.postDelayed(clapResetRunnable, CLAP_RESET_MS);
                        }
                    }
                }
                else {
                    // No data read; sleep briefly to avoid busy spin and help debugging
                    try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                    continue;
                }
            } catch (Exception e) {
                Log.e("ClapDetector", "Detection error", e);
                if (callback != null) callback.onError("Detection error: " + e.getMessage());
                break;
            }
        }
    }

    public void destroy() {
        stopListening();
    }
}
