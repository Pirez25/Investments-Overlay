package com.example.csmarketoverlay;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView invPriceTextView;
    private Button btnToggle;
    private PriceCache priceCache;
    private AppDatabase db;
    private SharedPreferences prefs;

    private ActivityResultLauncher<Intent> overlayPermissionLauncher;
    private ActivityResultLauncher<Intent> mediaProjectionLauncher;

    private final List<InventoryItem> inventoryItems = Collections.synchronizedList(new ArrayList<>());

    private final BroadcastReceiver inventoryUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updatePrices();
        }
    };

    @SuppressLint({"MissingInflatedId"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        invPriceTextView = findViewById(R.id.invprice);
        btnToggle = findViewById(R.id.btnToggle);
        Button btnManageInventory = findViewById(R.id.btnManageInventory);

        db = AppDatabase.getDatabase(this);
        priceCache = new PriceCache(this);
        prefs = getSharedPreferences("CSOverlayPrefs", MODE_PRIVATE);

        btnManageInventory.setOnClickListener(v -> {
            Intent intent = new Intent(this, InventoryPriceActivity.class);
            synchronized (inventoryItems) {
                intent.putExtra("inventory_items", (Serializable) new ArrayList<>(inventoryItems));
            }
            startActivity(intent);
        });
        btnToggle.setOnClickListener(v -> toggleOverlay());

        ContextCompat.registerReceiver(this, inventoryUpdateReceiver, new IntentFilter("INVENTORY_UPDATED"), ContextCompat.RECEIVER_NOT_EXPORTED);

        setupActivityLaunchers();
    }

    private void setupActivityLaunchers() {
        overlayPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (Settings.canDrawOverlays(this)) {
                    requestMediaProjection();
                } else {
                    Toast.makeText(this, R.string.overlay_permission_needed, Toast.LENGTH_SHORT).show();
                }
            });

        mediaProjectionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Intent intent = new Intent(this, OverlayService.class);
                    intent.putExtra(OverlayService.EXTRA_RESULT_CODE, result.getResultCode());
                    intent.putExtra(OverlayService.EXTRA_RESULT_DATA, result.getData());
                    startService(intent);
                    updateButtonText();
                } else {
                    Toast.makeText(this, R.string.screen_capture_permission_needed, Toast.LENGTH_SHORT).show();
                }
            });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateButtonText();
        updatePrices();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(inventoryUpdateReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver não estava registado
        }
    }

    private void toggleOverlay() {
        if (OverlayService.isRunning()) {
            stopService(new Intent(this, OverlayService.class));
            updateButtonText();
        } else {
            checkAndShowHyperOsInfo();
        }
    }

    private void checkAndShowHyperOsInfo() {
        boolean miuiInfoShown = prefs.getBoolean("hyperos_info_shown", false);
        if (!miuiInfoShown && Build.MANUFACTURER.equalsIgnoreCase("Xiaomi")) {
            new AlertDialog.Builder(this)
                .setTitle(R.string.hyperos_title)
                .setMessage(R.string.hyperos_message)
                .setPositiveButton(R.string.hyperos_open_settings, (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", getPackageName(), null);
                        intent.setData(uri);
                        startActivity(intent);
                    } catch (Exception e) {
                        // Ignora
                    }
                })
                .setNegativeButton(R.string.dismiss, null)
                .show();
            prefs.edit().putBoolean("hyperos_info_shown", true).apply();
        } else {
            checkPermissionsAndStartOverlay();
        }
    }

    private void updateButtonText() {
        btnToggle.setText(OverlayService.isRunning() ? R.string.close_overlay : R.string.open_overlay);
    }

    private void checkPermissionsAndStartOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            overlayPermissionLauncher.launch(intent);
        } else {
            requestMediaProjection();
        }
    }

    private void requestMediaProjection() {
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mediaProjectionManager != null) {
            mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent());
        }
    }

    private void updatePrices() {
        ApiRequestExecutor.getInstance().execute(() -> {
            List<InventoryItem> localItems = db.inventoryDao().getAll();

            for (InventoryItem item : localItems) {
                if (priceCache.getFreshPrice(item.getName()) == -1) {
                    double newPrice;
                    if ("Steam".equals(item.getType())) {
                        newPrice = fetchSteamPrice(item.getName());
                    } else {
                        newPrice = fetchCryptoPrice(item.getName());
                    }
                    item.setPrice(newPrice);
                } else {
                    item.setPrice(priceCache.getAnyPrice(item.getName()));
                }
            }

            synchronized (inventoryItems) {
                inventoryItems.clear();
                inventoryItems.addAll(localItems);
            }

            updateTotalValueAndBroadcast();
        });
    }

    private void updateTotalValueAndBroadcast() {
        handler.post(() -> {
            double total = 0;
            synchronized (inventoryItems) {
                for (InventoryItem item : inventoryItems) {
                    double price = item.getPrice();
                    if (price >= 0) {
                        total += price * item.getQuantity();
                    }
                }
            }
            invPriceTextView.setText(String.format(Locale.getDefault(), "%.2f€", total));

            Intent intent = new Intent("UPDATE_OVERLAY_DATA");
            Bundle pricesBundle = new Bundle();
            Bundle typesBundle = new Bundle();
            Bundle namesBundle = new Bundle();
            Bundle quantitiesBundle = new Bundle();

            synchronized (inventoryItems) {
                for (InventoryItem item : inventoryItems) {
                    pricesBundle.putDouble(item.getName(), item.getPrice());
                    typesBundle.putString(item.getName(), item.getType());
                    namesBundle.putString(item.getName(), item.getCustomName());
                    quantitiesBundle.putDouble(item.getName(), item.getQuantity());
                }
            }

            intent.putExtra("itemPrices", pricesBundle);
            intent.putExtra("itemTypes", typesBundle);
            intent.putExtra("customNames", namesBundle);
            intent.putExtra("quantities", quantitiesBundle);
            sendBroadcast(intent);
        });
    }

    private double fetchSteamPrice(String item) {
        try {
            Thread.sleep(400); // Delay para não sobrecarregar a API
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return priceCache.getAnyPrice(item);
        }
        
        String urlStr = "https://steamcommunity.com/market/priceoverview/?currency=3&appid=730&market_hash_name=" + URLEncoder.encode(item, StandardCharsets.UTF_8);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new URL(urlStr).openStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
            }
            JSONObject json = new JSONObject(sb.toString());
            String priceStr = json.optString("lowest_price", json.optString("median_price", "0"));
            double price = parsePrice(priceStr);
            if (price > 0) {
                priceCache.savePrice(item, price);
            }
            return price;
        } catch (Exception e) {
            Log.w(TAG, "Erro Steam para " + item, e);
            return priceCache.getAnyPrice(item);
        }
    }

    private double fetchCryptoPrice(String cryptoId) {
        String urlStr = "https://api.coingecko.com/api/v3/simple/price?ids=" + cryptoId.toLowerCase() + "&vs_currencies=eur";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new URL(urlStr).openStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
            }
            JSONObject json = new JSONObject(sb.toString());
            double price = json.getJSONObject(cryptoId.toLowerCase()).getDouble("eur");
            priceCache.savePrice(cryptoId, price);
            return price;
        } catch (Exception e) {
            Log.w(TAG, "Erro Crypto para " + cryptoId, e);
            return priceCache.getAnyPrice(cryptoId);
        }
    }
    
    private double parsePrice(String value) {
        if (value == null || value.isEmpty()) return 0.0;
        String cleaned = value.replaceAll("[^0-9,.]", "").replace(",", ".");
        try {
            return Double.parseDouble(cleaned);
        } catch (Exception e) {
            return 0.0;
        }
    }
}
