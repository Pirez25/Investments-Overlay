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

import java.util.HashMap;

public class OverlayService extends Service {

    private WindowManager windowManager;
    private View overlayView;
    private TextView overlayText;
    private WindowManager.LayoutParams params;
    private final HashMap<String, Double> itemPrices = new HashMap<>();
    private final HashMap<String, String> itemCustomNames = new HashMap<>();

    private final BroadcastReceiver overlayReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !"UPDATE_OVERLAY_ITEM".equals(intent.getAction())) return;

            String item = intent.getStringExtra("item_name");
            double preco = intent.getDoubleExtra("item_price", 0.0);

            if (item != null) {
                itemPrices.put(item, preco);
                refreshOverlay();
            }
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            initializeCustomNames();

            if (overlayView != null && windowManager != null) {
                try {
                    windowManager.removeViewImmediate(overlayView);
                } catch (Exception ignored) {}
                overlayView = null;
            }

            createOverlay();

            registerReceiver(overlayReceiver, new IntentFilter("UPDATE_OVERLAY_ITEM"));
        } catch (Exception e) {
            Log.e("OverlayService", "Erro ao iniciar serviço", e);
            stopSelf();
        }
        return START_STICKY;
    }

    private void initializeCustomNames() {
        itemCustomNames.put("Gallery Case", "Galry");
        itemCustomNames.put("CS20 Case", "CS20");
        itemCustomNames.put("Dreams & Nightmares Case", "D&N");
        itemCustomNames.put("M4A1-S | Vaporwave (Field-Tested)", "Vprwv");
        itemCustomNames.put("Number K | The Professionals", "_K");
    }

    private void createOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) {
            Log.e("OverlayService", "WindowManager é null");
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        overlayView = inflater.inflate(R.layout.overlay_layout, null);
        if (overlayView == null) {
            Log.e("OverlayService", "overlayView é null - verifica overlay_layout.xml");
            return;
        }

        overlayText = overlayView.findViewById(R.id.textOverlay);
        if (overlayText == null) {
            Log.e("OverlayService", "textOverlay não encontrado - verifica R.id.textOverlay");
            return;
        }

        overlayText.setText("Loading...");
        overlayText.setPadding(0, 0, 0, 0);
        overlayText.setIncludeFontPadding(false);

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 50;
        params.y = 200;

        windowManager.addView(overlayView, params);

        makeDraggable();

        startForegroundNotification();
    }

    public void setCustomItemName(String itemKey, String customName) {
        if (itemKey != null && customName != null) {
            itemCustomNames.put(itemKey, customName);
            refreshOverlay();
        }
    }

    private void refreshOverlay() {
        if (overlayText == null) return;

        StringBuilder sb = new StringBuilder();

        int count = 0;
        int size = itemPrices.size();

        for (String item : itemPrices.keySet()) {
            String displayName = itemCustomNames.getOrDefault(item, item);
            Double price = itemPrices.get(item);

            if (displayName != null && price != null) {
                sb.append(displayName)
                        .append(": ")
                        .append(String.format("%.2f€", price));

                // Adiciona '\n' somente se não for o último item
                if (count < size - 1) {
                    sb.append("\n");
                }
                count++;
            }
        }

        overlayText.post(() -> overlayText.setText(sb.toString()));
    }


    private void makeDraggable() {
        if (overlayView == null) return;

        windowManager.getDefaultDisplay().getMetrics(new DisplayMetrics()); // To avoid createMetrics error

        overlayView.setOnTouchListener(new View.OnTouchListener() {

            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private int screenWidth;
            private int screenHeight;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (params == null || windowManager == null) return false;

                if (screenWidth == 0 || screenHeight == 0) {
                    DisplayMetrics metrics = new DisplayMetrics();
                    windowManager.getDefaultDisplay().getMetrics(metrics);
                    screenWidth = metrics.widthPixels;
                    screenHeight = metrics.heightPixels;
                }

                int overlayWidth = overlayView.getWidth();
                int overlayHeight = overlayView.getHeight();

                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = e.getRawX();
                        initialTouchY = e.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int newX = initialX + (int) (e.getRawX() - initialTouchX);
                        int newY = initialY + (int) (e.getRawY() - initialTouchY);

                        // Limita a posição à borda da tela
                        newX = Math.max(0, Math.min(newX, screenWidth - overlayWidth));
                        newY = Math.max(0, Math.min(newY, screenHeight - overlayHeight));

                        params.x = newX;
                        params.y = newY;
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
            NotificationChannel ch = new NotificationChannel(channelId,
                    "Overlay Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }

        Notification notification = new Notification.Builder(this, channelId)
                .setContentTitle("Overlay Ativo")
                .setContentText("A mostrar preços personalizados")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();

        startForeground(1, notification);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(overlayReceiver);
        } catch (Exception e) {
            Log.e("OverlayService", "Erro ao desregistar receiver", e);
        }
        if (overlayView != null && windowManager != null) {
            windowManager.removeViewImmediate(overlayView);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
