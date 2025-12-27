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
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
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

import java.io.Serializable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class OverlayService extends Service {

    private WindowManager windowManager;
    private View overlayView;
    private TextView overlayText;
    private WindowManager.LayoutParams params;

    private Map<String, Double> itemPrices = new HashMap<>();
    private Map<String, String> itemTypes = new HashMap<>();
    private Map<String, String> customNames = new HashMap<>();
    private Map<String, Double> quantities = new HashMap<>();

    private static boolean isRunning = false;
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

    @SuppressWarnings("unchecked")
    private void handleUpdateData(Intent intent) {
        Serializable pricesExtra = intent.getSerializableExtra("itemPrices");
        Serializable typesExtra = intent.getSerializableExtra("itemTypes");
        Serializable namesExtra = intent.getSerializableExtra("customNames");
        Serializable quantitiesExtra = intent.getSerializableExtra("quantities");

        if (pricesExtra instanceof Map) {
            itemPrices = (Map<String, Double>) pricesExtra;
        }
        if (typesExtra instanceof Map) {
            itemTypes = (Map<String, String>) typesExtra;
        }
        if (namesExtra instanceof Map) {
            customNames = (Map<String, String>) namesExtra;
        }
        if (quantitiesExtra instanceof Map) {
            quantities = (Map<String, Double>) quantitiesExtra;
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
                }
                registerOverlayReceiver();
                isRunning = true;
            } else {
                stopSelf();
            }
        } else {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void registerOverlayReceiver() {
        IntentFilter filter = new IntentFilter("UPDATE_OVERLAY_DATA");
        ContextCompat.registerReceiver(this, overlayReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void createOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) {
            stopSelf();
            return;
        }

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null);
        overlayText = overlayView.findViewById(R.id.textOverlay);

        if (overlayText == null) {
            Log.e("OverlayService", "textOverlay não encontrado!");
            stopSelf();
            return;
        }

        overlayText.setText(R.string.overlay_loading);
        overlayText.setPadding(8, 4, 8, 4);
        overlayText.setTextColor(0xFFFFFFFF);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;

        windowManager.addView(overlayView, params);
        makeDraggable();
    }

    private void startForegroundServiceWithNotification() {
        String channelId = "overlay_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(channelId, getString(R.string.overlay_active_title), NotificationManager.IMPORTANCE_LOW);
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
            Log.e("OverlayService", "Error starting foreground service", e);
            stopSelf();
        }
    }

    private void refreshOverlay() {
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
                    if (precoUnitario != null && precoUnitario > 0) {
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
            sb.append(getString(R.string.overlay_no_nicks));
        }

        overlayText.post(() -> overlayText.setText(sb.toString()));
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
                }
                return false;
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        try { unregisterReceiver(overlayReceiver); } catch (Exception ignored) {}
        if (overlayView != null && windowManager != null) {
            try { windowManager.removeViewImmediate(overlayView); } catch (Exception ignored) {}
        }
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }
}