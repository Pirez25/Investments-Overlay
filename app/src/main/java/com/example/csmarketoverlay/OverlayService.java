package com.example.csmarketoverlay;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

    private static boolean isRunning = false;

    public static boolean isRunning() {
        return isRunning;
    }

    private final BroadcastReceiver overlayReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;

            String action = intent.getAction();

            if ("UPDATE_OVERLAY_DATA".equals(action)) {
                itemPrices = (Map<String, Double>) intent.getSerializableExtra("itemPrices");
                itemTypes = (Map<String, String>) intent.getSerializableExtra("itemTypes");
                customNames = (Map<String, String>) intent.getSerializableExtra("customNames");
                refreshOverlay();
            }
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (overlayView != null && windowManager != null) {
                try { windowManager.removeViewImmediate(overlayView); } catch (Exception ignored) {}
                overlayView = null;
            }

            createOverlay();

            IntentFilter filter = new IntentFilter("UPDATE_OVERLAY_DATA");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(overlayReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(overlayReceiver, filter);
            }

            isRunning = true;

        } catch (Exception e) {
            Log.e("OverlayService", "Erro ao iniciar", e);
            stopSelf();
        }
        return START_STICKY;
    }

    private void createOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) return;

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null);
        overlayText = overlayView.findViewById(R.id.textOverlay);

        if (overlayText == null) {
            Log.e("OverlayService", "textOverlay não encontrado!");
            stopSelf();
            return;
        }

        overlayText.setText("A carregar...");
        overlayText.setPadding(18, 14, 18, 14);
        overlayText.setTextColor(0xFFFFFFFF);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;

        windowManager.addView(overlayView, params);
        makeDraggable();
        startForegroundNotification();
    }

    private void refreshOverlay() {
        if (overlayText == null) return;

        StringBuilder sb = new StringBuilder();

        if (itemPrices == null || itemPrices.isEmpty()) {
            sb.append("A carregar inventário...");
        } else {
            for (String item : itemPrices.keySet()) {
                String customName = customNames.get(item);
                String displayName = (customName != null && !customName.isEmpty()) ? customName : item.split(" \\|")[0];
                Double preco = itemPrices.get(item);

                sb.append(displayName).append(": ");
                if (preco != null && preco > 0) {
                    sb.append(String.format(Locale.getDefault(), "%.2f€", preco));
                } else {
                    sb.append("a carregar...");
                }
                sb.append("\n");
            }
        }

        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
            sb.setLength(sb.length() - 1);
        }

        overlayText.post(() -> overlayText.setText(sb.toString()));
    }

    private void makeDraggable() {
        if (overlayView == null) return;

        overlayView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

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

    private void startForegroundNotification() {
        String channelId = "overlay_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(channelId, "CS2 Overlay", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }

        Notification n = new Notification.Builder(this, channelId)
                .setContentTitle("CS Market Overlay")
                .setContentText("Steam • Bitcoin • Tesla")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build();

        startForeground(1, n);
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