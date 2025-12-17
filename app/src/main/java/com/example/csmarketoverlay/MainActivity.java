package com.example.csmarketoverlay;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.color.DynamicColors;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private final Handler handler = new Handler(Looper.getMainLooper());

    private SharedPreferences prefs;
    private final List<String> itemNames = new ArrayList<>();
    private final Map<String, Double> quantities = new HashMap<>();
    private final Map<String, String> itemTypes = new HashMap<>();
    private final Map<String, String> customNames = new HashMap<>();

    private TextView invPriceTextView;
    private Button btnToggle;
    private boolean overlayAtivo = false;
    private PriceCache priceCache;


    private final BroadcastReceiver inventoryUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadInventory();
            if (overlayAtivo) {
                updatePrices();
            }
        }
    };

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivitiesIfAvailable(this.getApplication());
        setContentView(R.layout.activity_main);

        invPriceTextView = findViewById(R.id.invprice);
        btnToggle = findViewById(R.id.btnToggle);
        Button btnManageInventory = findViewById(R.id.btnManageInventory);

        prefs = getSharedPreferences("CSOverlayPrefs", MODE_PRIVATE);
        priceCache = new PriceCache(this);

        loadInventory();

        btnManageInventory.setOnClickListener(v -> startActivity(new Intent(this, InventoryPriceActivity.class)));
        btnToggle.setOnClickListener(v -> toggleOverlay());

        registerReceiver(inventoryUpdateReceiver, new IntentFilter("INVENTORY_UPDATED"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadInventory();
        overlayAtivo = OverlayService.isRunning();
        updateButtonText();
        if (overlayAtivo) {
            updatePrices();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(inventoryUpdateReceiver);
    }

    private void loadInventory() {
        itemNames.clear();
        quantities.clear();
        itemTypes.clear();
        customNames.clear();
        String data = prefs.getString("items", "");
        if (data.isEmpty()) {
            addItem("Steam", "Gallery Case", "", 203.0);
            addItem("Steam", "CS20 Case", "", 254.0);
            addItem("Steam", "Dreams & Nightmares Case", "", 102.0);
        } else {
            for (String s : data.split(";")) {
                if (s.contains(":")) {
                    String[] p = s.split(":");
                    if (p.length == 4) {
                        addItem(p[0], p[1], p[2], Double.parseDouble(p[3]));
                    } else if (p.length == 3) {
                        addItem(p[0], p[1], "", Double.parseDouble(p[2]));
                    } else if (p.length == 2) {
                        addItem("Steam", p[0], "", Double.parseDouble(p[1]));
                    }
                }
            }
        }
    }

    private void saveInventory() {
        StringBuilder sb = new StringBuilder();
        List<String> itemNamesSnapshot;
        synchronized (itemNames) {
            itemNamesSnapshot = new ArrayList<>(itemNames);
        }
        for (String name : itemNamesSnapshot) {
            sb.append(itemTypes.get(name)).append(":")
              .append(name).append(":")
              .append(customNames.get(name)).append(":")
              .append(quantities.get(name)).append(";");
        }
        prefs.edit().putString("items", sb.toString()).apply();
    }

    private void addItem(String type, String name, String customName, double qty) {
        if (!itemNames.contains(name)) {
             itemNames.add(name);
        }
        quantities.put(name, qty);
        itemTypes.put(name, type);
        customNames.put(name, customName);
        saveInventory();
    }

    private void toggleOverlay() {
        if (overlayAtivo) {
            stopService(new Intent(this, OverlayService.class));
            handler.removeCallbacksAndMessages(null);
            overlayAtivo = false;
        } else {
            startOverlayWithPermission();
            overlayAtivo = true;
        }
        updateButtonText();
    }

    private void updateButtonText() {
        btnToggle.setText(overlayAtivo ? R.string.close_overlay : R.string.open_overlay);
    }

    private void startOverlayWithPermission() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            return;
        }
        startService(new Intent(this, OverlayService.class));
        handler.postDelayed(this::updatePrices, 600);
    }

    private void updatePrices() {
        ApiRequestExecutor.getInstance().execute(() -> {
            Map<String, String> itemTypesSnapshot;
            Map<String, Double> quantitiesSnapshot;
            Map<String, String> customNamesSnapshot;
            Map<String, Double> pricesSnapshot = new HashMap<>();

            synchronized (itemNames) {
                 itemTypesSnapshot = new HashMap<>(itemTypes);
                 quantitiesSnapshot = new HashMap<>(quantities);
                 customNamesSnapshot = new HashMap<>(customNames);
            }

            double total = 0;
            for (String item : itemTypesSnapshot.keySet()) {
                double price = 0;
                String type = itemTypesSnapshot.get(item);

                if (type != null) {
                    switch (type) {
                        case "Steam":
                            price = getSteamPrice(item);
                            break;
                        case "Crypto":
                            price = getCryptoPrice(item);
                            break;
                        case "Stock":
                            price = getStockPrice(item);
                            break;
                    }
                }
                pricesSnapshot.put(item, price);
                double qty = quantitiesSnapshot.getOrDefault(item, 1.0);
                total += price * qty;
            }

            Intent intent = new Intent("UPDATE_OVERLAY_DATA");
            intent.putExtra("itemPrices", (Serializable) pricesSnapshot);
            intent.putExtra("itemTypes", (Serializable) itemTypesSnapshot);
            intent.putExtra("customNames", (Serializable) customNamesSnapshot);
            sendBroadcast(intent);

            double finalTotal = total;
            handler.post(() -> invPriceTextView.setText(String.format(Locale.getDefault(), "%.2f€", finalTotal)));

            if (overlayAtivo) {
                handler.postDelayed(this::updatePrices, 300000);
            }
        });
    }

    private double getSteamPrice(String item) {
        double cachedPrice = priceCache.getPrice(item);
        if (cachedPrice != -1) {
            return cachedPrice;
        }

        try {
            String url = "https://steamcommunity.com/market/priceoverview/?currency=3&appid=730&market_hash_name=" + URLEncoder.encode(item, StandardCharsets.UTF_8.toString());
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
            priceCache.savePrice(item, price);
            return price;
        } catch (Exception e) {
            Log.e(TAG, "Erro Steam", e);
            return 0.0;
        }
    }

    private double getCryptoPrice(String cryptoId) {
        double cachedPrice = priceCache.getPrice(cryptoId);
        if (cachedPrice != -1) {
            return cachedPrice;
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
            priceCache.savePrice(cryptoId, price);
            return price;
        } catch (Exception e) {
            Log.e(TAG, "Erro Crypto para " + cryptoId, e);
            return 0.0;
        }
    }

    private double getStockPrice(String stockTicker) {
        Log.d(TAG, "Buscando o preço da ação para: " + stockTicker + " (não implementado)");
        return 0.0;
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