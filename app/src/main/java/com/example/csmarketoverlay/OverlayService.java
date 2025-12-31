package com.example.csmarketoverlay;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class OverlayService extends Service {

    private static final String TAG = "OverlayService";
    private WindowManager windowManager;
    private View overlayView;
    private TextView overlayText;
    private WindowManager.LayoutParams params;
    private SharedPreferences prefs;

    private Map<String, Double> itemPrices = new HashMap<>();
    private Map<String, String> customNames = new HashMap<>();

    private static boolean isRunning = false;
    private boolean receiverRegistered = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public static final String ACTION_OVERLAY_STATUS_CHANGED = "com.example.csmarketoverlay.OVERLAY_STATUS_CHANGED";
    public static final String EXTRA_RESULT_CODE = "RESULT_CODE";
    public static final String EXTRA_RESULT_DATA = "RESULT_DATA";

    public static boolean isRunning() {
        return isRunning;
    }

    private final BroadcastReceiver overlayReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && "UPDATE_OVERLAY_DATA".equals(intent.getAction())) {
                handleUpdateData(intent);
            }
        }
    };

    private final Runnable refreshRunnable = this::buildAndSetText;

    private void handleUpdateData(Intent intent) {
        Bundle pricesBundle = intent.getBundleExtra("itemPrices");
        if (pricesBundle != null) {
            itemPrices.clear();
            for (String key : pricesBundle.keySet()) {
                itemPrices.put(key, pricesBundle.getDouble(key, -1.0));
            }
        }

        Bundle namesBundle = intent.getBundleExtra("customNames");
        if (namesBundle != null) {
            customNames.clear();
            for (String key : namesBundle.keySet()) {
                customNames.put(key, namesBundle.getString(key, ""));
            }
        }
        
        refreshOverlay();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
            Intent resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);

            if (resultCode == Activity.RESULT_OK && resultData != null) {
                startForegroundServiceWithNotification();
                if (overlayView == null) {
                    createOverlay();
                    if (overlayView == null) {
                        stopSelf();
                        return START_NOT_STICKY;
                    }
                }
                registerOverlayReceiver();
                setServiceStatus(true);
            } else {
                stopSelf();
            }
        } else {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void registerOverlayReceiver() {
        if (receiverRegistered) return;
        IntentFilter filter = new IntentFilter("UPDATE_OVERLAY_DATA");
        ContextCompat.registerReceiver(this, overlayReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;
    }

    private void createOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        prefs = getSharedPreferences("CSOverlayPrefs", MODE_PRIVATE);
        if (windowManager == null) {
            stopSelf();
            return;
        }

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null);
        overlayText = overlayView.findViewById(R.id.textOverlay);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = prefs.getInt("overlay_x", 20);
        params.y = prefs.getInt("overlay_y", 100);

        windowManager.addView(overlayView, params);
        makeDraggable();
    }

    private void startForegroundServiceWithNotification() {
        String channelId = "overlay_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(channelId, "Investments Overlay", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }

        Notification n = new NotificationCompat.Builder(this, channelId)
                .setContentTitle(getString(R.string.overlay_active_title))
                .setContentText(getString(R.string.overlay_active_content))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .build();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(1, n);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting foreground service", e);
            stopSelf();
        }
    }

    private void refreshOverlay() {
        handler.removeCallbacks(refreshRunnable);
        handler.postDelayed(refreshRunnable, 100);
    }

    private void buildAndSetText() {
        if (overlayText == null) return;

        StringBuilder sb = new StringBuilder();
        boolean hasItemsToShow = false;

        if (itemPrices != null && !itemPrices.isEmpty()) {
            for (String item : itemPrices.keySet()) {
                String customName = customNames.get(item);

                if (customName != null && !customName.trim().isEmpty()) {
                    hasItemsToShow = true;
                    Double precoUnitario = itemPrices.get(item);

                    sb.append(customName).append(": ");
                    if (precoUnitario != null && precoUnitario >= 0) {
                        sb.append(String.format(Locale.getDefault(), "%.2f€", precoUnitario));
                    } else {
                        sb.append(getString(R.string.overlay_loading));
                    }
                    sb.append("\n");
                }
            }
        }

        if (hasItemsToShow) {
            if (sb.length() > 0) {
                sb.setLength(sb.length() - 1);
            }
        } else {
            sb.append(getString(R.string.overlay_empty));
        }

        overlayText.setText(sb.toString());
    }

    private void makeDraggable() {
        if (overlayView == null) return;

        overlayView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = e.getRawX();
                        initialTouchY = e.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (e.getRawX() - initialTouchX);
                        params.y = initialY + (int) (e.getRawY() - initialTouchY);

                        DisplayMetrics metrics = getResources().getDisplayMetrics();
                        int maxX = metrics.widthPixels - overlayView.getWidth();
                        int maxY = metrics.heightPixels - overlayView.getHeight();

                        params.x = Math.max(0, Math.min(params.x, maxX));
                        params.y = Math.max(0, Math.min(params.y, maxY));

                        windowManager.updateViewLayout(overlayView, params);
                        return true;
                    
                    case MotionEvent.ACTION_UP:
                        prefs.edit()
                            .putInt("overlay_x", params.x)
                            .putInt("overlay_y", params.y)
                            .apply();
                        return true;
                }
                return false;
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        setServiceStatus(false);
        if (receiverRegistered) {
            try {
                unregisterReceiver(overlayReceiver);
            } catch (Exception ignored) {}
            receiverRegistered = false;
        }
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeViewImmediate(overlayView);
            } catch (Exception ignored) {}
        }
    }

    private void setServiceStatus(boolean running) {
        isRunning = running;
        sendBroadcast(new Intent(ACTION_OVERLAY_STATUS_CHANGED));
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }
}