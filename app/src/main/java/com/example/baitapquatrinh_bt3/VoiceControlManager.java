package com.example.baitapquatrinh_bt3;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.Manifest;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Locale;

public class VoiceControlManager implements RecognitionListener {

    private Activity activity;
    private SpeechRecognizer speechRecognizer;
    private VoiceCallback callback;
    private int retryCount = 0;
    private static final int MAX_RETRIES = 2;
    private Handler mainHandler;

    public interface VoiceCallback {
        void onResult(String command);
        void onError(String error);
        void onListening();
        void onRetry(int attemptNumber);
    }

    public VoiceControlManager(Activity activity, VoiceCallback callback) {
        this.activity = activity;
        this.callback = callback;
        this.mainHandler = new Handler(Looper.getMainLooper());

        if (SpeechRecognizer.isRecognitionAvailable(activity)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(activity);
            speechRecognizer.setRecognitionListener(this);
        }
    }

    // Bắt đầu lắng nghe giọng nói
    public void startListening() {
        if (!hasAudioPermission()) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.RECORD_AUDIO}, 101);
            return;
        }

        Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");  // ✅ ĐÃ SỬA
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);

        if (speechRecognizer != null) {
            speechRecognizer.startListening(recognizerIntent);
            if (callback != null) callback.onListening();
        }
    }

    // Dừng lắng nghe
    public void stopListening() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
    }

    // ===== RecognitionListener Implementation =====

    @Override
    public void onReadyForSpeech(android.os.Bundle params) {}

    @Override
    public void onBeginningOfSpeech() {}

    @Override
    public void onRmsChanged(float rmsdB) {}

    @Override
    public void onBufferReceived(byte[] buffer) {}

    @Override
    public void onEndOfSpeech() {}

    @Override
    public void onError(int error) {
        String errorMsg = "Error: ";
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                errorMsg += "Audio error";
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                errorMsg += "Client error";
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                errorMsg += "No permission";
                break;
            case SpeechRecognizer.ERROR_NETWORK:
                errorMsg += "Network error";
                break;
            case SpeechRecognizer.ERROR_NO_MATCH:
                errorMsg += "Not recognized";
                break;
            case SpeechRecognizer.ERROR_SERVER:
                errorMsg += "Server error";
                break;
            default:
                errorMsg += "Unknown error";
        }
        if (callback != null) callback.onError(errorMsg);
    }

    @Override
    public void onResults(android.os.Bundle results) {
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

        if (matches != null && !matches.isEmpty()) {
            String bestMatch = matches.get(0);
            String command = VoiceCommandMatcher.matchCommand(bestMatch);

            if (command != null) {
                // Tìm thấy lệnh - Reset retry count
                retryCount = 0;
                if (callback != null) {
                    callback.onResult(command);
                }
            } else {
                // Không nhận diện được - Thử lại
                if (retryCount < MAX_RETRIES) {
                    retryCount++;
                    if (callback != null) {
                        callback.onRetry(retryCount);
                    }
                    // Lắng nghe lại sau 800ms
                    mainHandler.postDelayed(this::startListening, 800);
                } else {
                    // Hết số lần thử
                    retryCount = 0;
                    if (callback != null) {
                        callback.onError("Not recognized: \"" + bestMatch + "\"");
                    }
                }
            }
        }
    }

    @Override
    public void onPartialResults(android.os.Bundle partialResults) {}

    @Override
    public void onEvent(int eventType, android.os.Bundle params) {}

    private boolean hasAudioPermission() {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    public void destroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
    }
}
