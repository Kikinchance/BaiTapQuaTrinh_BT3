package com.example.baitapquatrinh_bt3;

import java.io.OutputStream;
import java.net.Socket;
import android.util.Log;

public class WifiClient {

    // ===== CẤU HÌNH =====
    private static final String SERVER_IP = "192.168.28.92"; //  IP laptop
    private static final int SERVER_PORT = 6000; //  port

    // ===== TRẠNG THÁI =====
    private static Socket socket;
    private static boolean isConnected = false;

    // ===== INTERFACE CALLBACK =====
    public interface ServerCallback {
        void onResult(boolean success);
    }

    // ===== CONNECT =====
    public static void connect(ServerCallback callback) {
        new Thread(() -> {
            try {
                socket = new Socket(SERVER_IP, SERVER_PORT);
                isConnected = true;

                if (callback != null) callback.onResult(true);

            } catch (Exception e) {
                isConnected = false;
                if (callback != null) callback.onResult(false);
            }
        }).start();
    }

    // ===== GỬI LỆNH =====
    public static void sendCommand(String command) {
        new Thread(() -> {
            try {
                Log.d("WifiClient", "sendCommand: " + command + " connected=" + isConnected);
                if (!isConnected || socket == null) {
                    Log.w("WifiClient", "Not connected, cannot send: " + command);
                    return;
                }

                OutputStream os = socket.getOutputStream();
                os.write((command + "\n").getBytes("UTF-8"));
                os.flush();
                Log.d("WifiClient", "Command sent: " + command);

            } catch (Exception e) {
                Log.e("WifiClient", "sendCommand error", e);
                isConnected = false;
            }
        }).start();
    }
    public static void disconnect() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Exception e) {}

        socket = null;
        isConnected = false;
    }

    // ===== KIỂM TRA =====
    public static boolean isConnected() {
        return isConnected;
    }
}