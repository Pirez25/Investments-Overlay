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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

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

    private final List<InventoryItem> inventoryItems = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean isUpdating = new AtomicBoolean(false);

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
        btnManageInventory = findViewById(R.id.btnManageInventory);

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
            // Receiver não registado
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
        if (!isUpdating.compareAndSet(false, true)) {
            return; // Já existe uma atualização em curso
        }

        ApiRequestExecutor.getInstance().execute(() -> {
            try {
                List<InventoryItem> localItems = db.inventoryDao().getAll();

                synchronized (inventoryItems) {
                    inventoryItems.clear();
                    inventoryItems.addAll(localItems);
                    for (InventoryItem item : inventoryItems) {
                        item.setPrice(priceCache.getAnyPrice(item.getName()));
                    }
                }
                updateTotalValueAndBroadcast();

                ExecutorService executor = ApiRequestExecutor.getInstance();
                List<Callable<Void>> tasks = new ArrayList<>();

                List<InventoryItem> itemsToFetch = localItems.stream()
                    .filter(i -> priceCache.getFreshPrice(i.getName()) == -1)
                    .collect(Collectors.toList());

                for (InventoryItem item : itemsToFetch) {
                    tasks.add(() -> {
                        if ("Steam".equals(item.getType())) {
                            item.setPrice(fetchSteamPrice(item.getName()));
                        } else if ("Crypto".equals(item.getType())) {
                            item.setPrice(fetchCryptoPrice(item.getName()));
                        }
                        return null;
                    });
                }

                if (tasks.isEmpty()) return;

                executor.invokeAll(tasks);

                synchronized (inventoryItems) {
                    inventoryItems.clear();
                    inventoryItems.addAll(localItems);
                }
                updateTotalValueAndBroadcast();

            } catch (Exception e) {
                Log.e(TAG, "Erro durante a atualização de preços", e);
            } finally {
                isUpdating.set(false);
            }
        });
    }

    private void updateTotalValueAndBroadcast() {
        handler.post(() -> {
            double total = 0;
            Map<String, Double> pricesMap = new HashMap<>();
            Map<String, String> itemTypesMap = new HashMap<>();
            Map<String, String> customNamesMap = new HashMap<>();
            Map<String, Double> quantitiesMap = new HashMap<>();

            synchronized (inventoryItems) {
                for (InventoryItem item : inventoryItems) {
                    double price = item.getPrice();
                    if (price >= 0) {
                        total += price * item.getQuantity();
                    }
                    pricesMap.put(item.getName(), price);
                    itemTypesMap.put(item.getName(), item.getType());
                    customNamesMap.put(item.getName(), item.getCustomName());
                    quantitiesMap.put(item.getName(), item.getQuantity());
                }
            }

            invPriceTextView.setText(String.format(Locale.getDefault(), "%.2f€", total));

            Intent intent = new Intent("UPDATE_OVERLAY_DATA");
            Bundle pricesBundle = new Bundle();
            for (Map.Entry<String, Double> entry : pricesMap.entrySet()) {
                pricesBundle.putDouble(entry.getKey(), entry.getValue());
            }
            intent.putExtra("itemPrices", pricesBundle);

            Bundle typesBundle = new Bundle();
            for (Map.Entry<String, String> entry : itemTypesMap.entrySet()) {
                typesBundle.putString(entry.getKey(), entry.getValue());
            }
            intent.putExtra("itemTypes", typesBundle);

            Bundle namesBundle = new Bundle();
            for (Map.Entry<String, String> entry : customNamesMap.entrySet()) {
                namesBundle.putString(entry.getKey(), entry.getValue());
            }
            intent.putExtra("customNames", namesBundle);

            Bundle quantitiesBundle = new Bundle();
            for (Map.Entry<String, Double> entry : quantitiesMap.entrySet()) {
                quantitiesBundle.putDouble(entry.getKey(), entry.getValue());
            }
            intent.putExtra("quantities", quantitiesBundle);
            
            sendBroadcast(intent);
        });
    }

    private double fetchSteamPrice(String item) {
        HttpURLConnection c = null;
        try {
            Thread.sleep(400);
            String urlStr = "https://steamcommunity.com/market/priceoverview/?currency=3&appid=730&market_hash_name=" + URLEncoder.encode(item, StandardCharsets.UTF_8);
            URL url = new URL(urlStr);
            c = (HttpURLConnection) url.openConnection();
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
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
            }
        } catch (Exception e) {
            Log.w(TAG, "Erro Steam para " + item, e);
            return priceCache.getAnyPrice(item);
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    private double fetchCryptoPrice(String cryptoId) {
        HttpURLConnection c = null;
        try {
            String urlStr = "https://api.coingecko.com/api/v3/simple/price?ids=" + cryptoId.toLowerCase() + "&vs_currencies=eur";
            URL url = new URL(urlStr);
            c = (HttpURLConnection) url.openConnection();
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line);
                }
                JSONObject json = new JSONObject(sb.toString());
                double price = json.getJSONObject(cryptoId.toLowerCase()).getDouble("eur");
                priceCache.savePrice(cryptoId, price);
                return price;
            }
        } catch (Exception e) {
            Log.w(TAG, "Erro Crypto para " + cryptoId, e);
            return priceCache.getAnyPrice(cryptoId);
        } finally {
            if (c != null) {
                c.disconnect();
            }
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