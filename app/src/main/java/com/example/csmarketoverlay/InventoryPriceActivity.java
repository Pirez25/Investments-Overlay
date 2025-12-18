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

import com.google.android.material.color.DynamicColors;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// A tela que mostra os preços do inventário.
public class InventoryPriceActivity extends AppCompatActivity {

    // Etiqueta para os logs.
    private static final String TAG = "InventoryPriceActivity";
    // Elementos da interface.
    private RecyclerView recyclerView;
    private InventoryPriceAdapter adapter;
    // Lista de itens do inventário.
    private final List<InventoryItem> inventoryItems = new ArrayList<>();
    // Handler para executar tarefas na thread principal.
    private final Handler handler = new Handler(Looper.getMainLooper());
    // Objeto para aceder aos dados guardados no dispositivo.
    private SharedPreferences prefs;
    private PriceCache priceCache;

    // Receiver para ouvir as atualizações do inventário.
    private final BroadcastReceiver inventoryUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadInventoryAndPrices(); // Carrega o inventário e os preços.
        }
    };

    // Este método é chamado quando a atividade é criada.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivitiesIfAvailable(this.getApplication()); // Aplica as cores dinâmicas.
        setContentView(R.layout.activity_inventory_price); // Define o layout da atividade.

        // Configura a barra de ferramentas.
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Preços do Inventário");

        // Inicializa os objetos para aceder aos dados guardados.
        prefs = getSharedPreferences("CSOverlayPrefs", MODE_PRIVATE);
        priceCache = new PriceCache(this);

        // Configura a lista de itens.
        recyclerView = findViewById(R.id.inventoryPriceRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new InventoryPriceAdapter(inventoryItems);
        recyclerView.setAdapter(adapter);

        // Define a ação do botão de editar.
        Button editButton = findViewById(R.id.editButton);
        editButton.setOnClickListener(v -> {
            startActivity(new Intent(this, InventoryActivity.class));
        });

        // Carrega o inventário e os preços.
        loadInventoryAndPrices();

        // Regista o receiver para ouvir as atualizações do inventário.
        registerReceiver(inventoryUpdateReceiver, new IntentFilter("INVENTORY_UPDATED"));
    }

    // Este método é chamado quando um item do menu é selecionado.
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish(); // Fecha a atividade quando a seta de retrocesso é pressionada.
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Este método é chamado quando a atividade é destruída.
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(inventoryUpdateReceiver); // Remove o registo do receiver.
    }

    // Carrega o inventário e os preços a partir dos dados guardados.
    private void loadInventoryAndPrices() {
        ApiRequestExecutor.getInstance().execute(() -> {
            List<InventoryItem> tempInventoryItems = new ArrayList<>();
            String data = prefs.getString("items", "");
            if (!data.isEmpty()) { // Se houver dados, carrega-os.
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

            // Atualiza a interface com os itens carregados.
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

            // Atualiza a lista de itens na thread principal.
            handler.post(() -> {
                synchronized (inventoryItems) {
                    inventoryItems.clear();
                    inventoryItems.addAll(tempInventoryItems);
                    adapter.notifyDataSetChanged();
                }
            });
        });
    }

    // Obtém o preço de um item da Steam.
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

    // Obtém o preço de uma criptomoeda.
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

    // Obtém o preço de uma ação.
    private double getStockPrice(String stockTicker) {
        Log.d(TAG, "Buscando o preço da ação para: " + stockTicker + " (não implementado)");
        return 0.0;
    }

    // Converte um texto para um número decimal.
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