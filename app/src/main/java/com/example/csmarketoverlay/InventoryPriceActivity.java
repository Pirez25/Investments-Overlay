package com.example.csmarketoverlay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class InventoryPriceActivity extends AppCompatActivity {

    private static final String TAG = "InventoryPriceActivity";
    private RecyclerView recyclerView;
    private InventoryPriceAdapter adapter;
    private final List<InventoryItem> inventoryItems = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private PriceCache priceCache;

    private final BroadcastReceiver inventoryUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadInventoryAndPrices();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory_price);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Preços do Inventário");

        prefs = getSharedPreferences("CSOverlayPrefs", MODE_PRIVATE);
        priceCache = new PriceCache(this);

        recyclerView = findViewById(R.id.inventoryPriceRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new InventoryPriceAdapter(inventoryItems);
        recyclerView.setAdapter(adapter);

        Button editButton = findViewById(R.id.editButton);
        editButton.setOnClickListener(v -> {
            startActivity(new Intent(this, InventoryActivity.class));
        });

        loadInventoryAndPrices();

        registerReceiver(inventoryUpdateReceiver, new IntentFilter("INVENTORY_UPDATED"));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(inventoryUpdateReceiver);
    }

    private void loadInventoryAndPrices() {
        ApiRequestExecutor.getInstance().execute(() -> {
            List<InventoryItem> tempInventoryItems = new ArrayList<>();
            String data = prefs.getString("items", "");
            if (!data.isEmpty()) {
                String[] items = data.split(";");
                for (String s : items) {
                    if (s.contains(":")) {
                        String[] p = s.split(":");
                        if (p.length == 4) {
                            tempInventoryItems.add(new InventoryItem(p[1], Double.parseDouble(p[3]), 0.0, p[0]));
                        } else if (p.length == 3) {
                            tempInventoryItems.add(new InventoryItem(p[1], Double.parseDouble(p[2]), 0.0, p[0]));
                        } else if (p.length == 2) {
                            tempInventoryItems.add(new InventoryItem(p[0], Double.parseDouble(p[1]), 0.0, "Steam"));
                        }
                    }
                }
            }

            for (InventoryItem item : tempInventoryItems) {
                double price = 0;
                switch (item.getType()) {
                    case "Steam":
                        price = getSteamPrice(item.getName());
                        break;
                    case "Crypto":
                        price = getCryptoPrice(item.getName());
                        break;
                    case "Stock":
                        price = getStockPrice(item.getName());
                        break;
                }
                item.setPrice(price);
            }

            handler.post(() -> {
                synchronized (inventoryItems) {
                    inventoryItems.clear();
                    inventoryItems.addAll(tempInventoryItems);
                    adapter.notifyDataSetChanged();
                }
            });
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