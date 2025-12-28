package com.example.csmarketoverlay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.DynamicColors;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class InventoryPriceActivity extends AppCompatActivity {

    private static final String TAG = "InventoryPriceActivity";
    private InventoryPriceAdapter adapter;
    private TextView totalInventoryValueTextView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AppDatabase db;
    private PriceCache priceCache;
    private final List<InventoryItem> inventoryItems = new ArrayList<>();

    private final BroadcastReceiver inventoryUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadInitialDataFromDb();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivitiesIfAvailable(this.getApplication());
        setContentView(R.layout.activity_inventory_price);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.prices_title);
        }

        db = AppDatabase.getDatabase(this);
        priceCache = new PriceCache(this);

        totalInventoryValueTextView = findViewById(R.id.totalInventoryValueTextView);
        setupRecyclerView();

        Button editButton = findViewById(R.id.editButton);
        editButton.setOnClickListener(v -> startActivity(new Intent(this, InventoryActivity.class)));

        ContextCompat.registerReceiver(this, inventoryUpdateReceiver, new IntentFilter("INVENTORY_UPDATED"), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void setupRecyclerView() {
        RecyclerView recyclerView = findViewById(R.id.inventoryPriceRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new InventoryPriceAdapter();
        recyclerView.setAdapter(adapter);
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
    protected void onResume() {
        super.onResume();
        loadInitialDataFromDb();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(inventoryUpdateReceiver);
    }

    private void loadInitialDataFromDb() {
        ApiRequestExecutor.getInstance().execute(() -> {
            List<InventoryItem> itemsFromDb = db.inventoryDao().getAll();
            inventoryItems.clear();
            inventoryItems.addAll(itemsFromDb);

            for (InventoryItem item : inventoryItems) {
                item.setPrice(priceCache.getAnyPrice(item.getName()));
            }

            handler.post(() -> {
                adapter.submitList(new ArrayList<>(inventoryItems));
                updateTotalValue();
                fetchLatestPrices(); 
            });
        });
    }

    private void fetchLatestPrices() {
        if (inventoryItems.isEmpty()) return;

        AtomicInteger pendingRequests = new AtomicInteger(inventoryItems.size());

        for (int i = 0; i < inventoryItems.size(); i++) {
            final int index = i;
            ApiRequestExecutor.getInstance().execute(() -> {
                InventoryItem item = inventoryItems.get(index);
                double freshPrice = priceCache.getFreshPrice(item.getName());

                if (freshPrice == -1) { 
                    double newPrice;
                    switch (Objects.requireNonNull(item.getType())) {
                        case "Steam" -> newPrice = fetchSteamPrice(item.getName());
                        case "Crypto" -> newPrice = fetchCryptoPrice(item.getName());
                        default -> newPrice = 0.0;
                    }
                    item.setPrice(newPrice);

                    handler.post(() -> adapter.notifyItemChanged(index));
                }
                
                if (pendingRequests.decrementAndGet() == 0) {
                    handler.post(this::sortAndFinalizeUpdate);
                }
            });
        }
    }

    private void sortAndFinalizeUpdate() {
        Collections.sort(inventoryItems, (item1, item2) -> {
            double value1 = item1.getPrice() * item1.getQuantity();
            double value2 = item2.getPrice() * item2.getQuantity();
            return Double.compare(value2, value1);
        });
        adapter.submitList(new ArrayList<>(inventoryItems));
        updateTotalValue();
    }
    
    private void updateTotalValue(){
        double totalValue = 0;
        for(InventoryItem item : inventoryItems){
            if(item.getPrice() > 0){
                totalValue += item.getPrice() * item.getQuantity();
            }
        }
        totalInventoryValueTextView.setText(getString(R.string.total_label) + String.format(Locale.getDefault(), " %.2f€", totalValue));
    }

    private double fetchSteamPrice(String item) {
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

    private double fetchCryptoPrice(String cryptoId) {
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
        String cleaned = value.replaceAll("[^,.]", "").replace(",", ".");
        try {
            return Double.parseDouble(cleaned);
        } catch (Exception e) {
            return 0.0;
        }
    }
}