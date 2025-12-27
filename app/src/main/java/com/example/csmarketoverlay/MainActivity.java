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

import com.google.android.material.color.DynamicColors;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView invPriceTextView;
    private Button btnToggle, btnManageInventory;
    private PriceCache priceCache;
    private AppDatabase db;
    private SharedPreferences prefs;

    private ActivityResultLauncher<Intent> overlayPermissionLauncher;
    private ActivityResultLauncher<Intent> mediaProjectionLauncher;

    private final BroadcastReceiver inventoryUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (OverlayService.isRunning()) {
                updatePrices();
            }
        }
    };

    @SuppressLint({"MissingInflatedId"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        invPriceTextView = findViewById(R.id.invprice);
        btnToggle = findViewById(R.id.btnToggle);
        btnManageInventory = findViewById(R.id.btnManageInventory);

        db = AppDatabase.getDatabase(this);
        priceCache = new PriceCache(this);
        prefs = getSharedPreferences("CSOverlayPrefs", MODE_PRIVATE);

        btnManageInventory.setOnClickListener(v -> startActivity(new Intent(this, InventoryPriceActivity.class)));
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
                    handler.postDelayed(this::updatePrices, 600);
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
        unregisterReceiver(inventoryUpdateReceiver);
    }

    private void toggleOverlay() {
        if (OverlayService.isRunning()) {
            stopService(new Intent(this, OverlayService.class));
            handler.removeCallbacksAndMessages(null);
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
            List<InventoryItem> itemsFromDb = db.inventoryDao().getAll();
            Map<String, Double> pricesSnapshot = new HashMap<>();
            Map<String, String> itemTypesSnapshot = new HashMap<>();
            Map<String, String> customNamesSnapshot = new HashMap<>();
            Map<String, Double> quantitiesSnapshot = new HashMap<>();

            double total = 0;
            for (InventoryItem item : itemsFromDb) {
                double price = 0;
                switch (item.getType()) {
                    case "Steam" -> price = getSteamPrice(item.getName());
                    case "Crypto" -> price = getCryptoPrice(item.getName());
                }
                pricesSnapshot.put(item.getName(), price);
                itemTypesSnapshot.put(item.getName(), item.getType());
                customNamesSnapshot.put(item.getName(), item.getCustomName());
                quantitiesSnapshot.put(item.getName(), item.getQuantity());

                if (price > 0) { 
                    total += price * item.getQuantity();
                }
            }

            Intent intent = new Intent("UPDATE_OVERLAY_DATA");
            intent.putExtra("itemPrices", (Serializable) pricesSnapshot);
            intent.putExtra("itemTypes", (Serializable) itemTypesSnapshot);
            intent.putExtra("customNames", (Serializable) customNamesSnapshot);
            intent.putExtra("quantities", (Serializable) quantitiesSnapshot);
            sendBroadcast(intent);

            double finalTotal = total;
            handler.post(() -> invPriceTextView.setText(String.format(Locale.getDefault(), "%.2f€", finalTotal)));

            if (OverlayService.isRunning()) {
                handler.postDelayed(this::updatePrices, 300000);
            }
        });
    }

    private double getSteamPrice(String item) {
        double freshPrice = priceCache.getFreshPrice(item);
        if (freshPrice != -1) {
            return freshPrice;
        }
        try {
            String url = "https://steamcommunity.com/market/priceoverview/?currency=3&appid=730&market_hash_name=" + URLEncoder.encode(item, StandardCharsets.UTF_8.name());
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            JSONObject json = new JSONObject(sb.toString());
            String priceStr = json.optString("lowest_price", json.optString("median_price", "0"));
            double price = parsePrice(priceStr);
            if (price > 0) {
                priceCache.savePrice(item, price);
            }
            return price;
        } catch (Exception e) {
            Log.w(TAG, "Erro Steam, a usar o cache para " + item);
            double stalePrice = priceCache.getAnyPrice(item);
            return (stalePrice != -1) ? stalePrice : 0.0;
        }
    }

    private double getCryptoPrice(String cryptoId) {
        double freshPrice = priceCache.getFreshPrice(cryptoId);
        if (freshPrice != -1) {
            return freshPrice;
        }
        try {
            String url = "https://api.coingecko.com/api/v3/simple/price?ids=" + cryptoId.toLowerCase() + "&vs_currencies=eur";
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            JSONObject json = new JSONObject(sb.toString());
            double price = json.getJSONObject(cryptoId.toLowerCase()).getDouble("eur");
            if (price > 0) {
                priceCache.savePrice(cryptoId, price);
            }
            return price;
        } catch (Exception e) {
            Log.w(TAG, "Erro Crypto, a usar o cache para " + cryptoId);
            double stalePrice = priceCache.getAnyPrice(cryptoId);
            return (stalePrice != -1) ? stalePrice : 0.0;
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
