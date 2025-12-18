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

// A tela principal da aplicação.
public class MainActivity extends AppCompatActivity {

    // Etiqueta para os logs.
    private static final String TAG = "MainActivity";

    // Handler para executar tarefas na thread principal.
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Objeto para aceder aos dados guardados no dispositivo.
    private SharedPreferences prefs;
    // Listas e mapas para guardar os dados do inventário.
    private final List<String> itemNames = new ArrayList<>();
    private final Map<String, Double> quantities = new HashMap<>();
    private final Map<String, String> itemTypes = new HashMap<>();
    private final Map<String, String> customNames = new HashMap<>();

    // Elementos da interface.
    private TextView invPriceTextView;
    private Button btnToggle;
    private boolean overlayAtivo = false;
    private PriceCache priceCache;

    // Receiver para ouvir as atualizações do inventário.
    private final BroadcastReceiver inventoryUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadInventory(); // Carrega o inventário.
            if (overlayAtivo) {
                updatePrices(); // Atualiza os preços se o overlay estiver ativo.
            }
        }
    };

    // Este mét0do é chamado quando a atividade é criada.
    @SuppressLint({"MissingInflatedId", "UnspecifiedRegisterReceiverFlag"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivitiesIfAvailable(this.getApplication()); // Aplica as cores dinâmicas.
        setContentView(R.layout.activity_main); // Define o layout da atividade.

        // Inicializa os elementos da interface.
        invPriceTextView = findViewById(R.id.invprice);
        btnToggle = findViewById(R.id.btnToggle);
        Button btnManageInventory = findViewById(R.id.btnManageInventory);

        // Inicializa os objetos para aceder aos dados guardados.
        prefs = getSharedPreferences("CSOverlayPrefs", MODE_PRIVATE);
        priceCache = new PriceCache(this);

        // Carrega o inventário.
        loadInventory();

        // Define as ações dos botões.
        btnManageInventory.setOnClickListener(v -> startActivity(new Intent(this, InventoryPriceActivity.class)));
        btnToggle.setOnClickListener(v -> toggleOverlay());

        // Regista o receiver para ouvir as atualizações do inventário.
        registerReceiver(inventoryUpdateReceiver, new IntentFilter("INVENTORY_UPDATED"));
    }

    // Este método é chamado quando a atividade volta a ser visível.
    @Override
    protected void onResume() {
        super.onResume();
        loadInventory(); // Carrega o inventário.
        overlayAtivo = OverlayService.isRunning(); // Verifica se o overlay está a correr.
        updateButtonText(); // Atualiza o texto do botão.
        if (overlayAtivo) {
            updatePrices(); // Atualiza os preços se o overlay estiver ativo.
        }
    }

    // Este método é chamado quando a atividade é destruída.
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(inventoryUpdateReceiver); // Remove o registo do receiver.
    }

    // Carrega o inventário a partir dos dados guardados.
    private void loadInventory() {
        itemNames.clear();
        quantities.clear();
        itemTypes.clear();
        customNames.clear();
        String data = prefs.getString("items", "");
        if (data.isEmpty()) { // Se não houver dados, adiciona alguns itens de exemplo.
            addItem("Steam", "Gallery Case", "", 203.0);
            addItem("Steam", "CS20 Case", "", 254.0);
            addItem("Steam", "Dreams & Nightmares Case", "", 102.0);
        } else { // Se houver dados, carrega-os.
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

    // Guarda o inventário nos dados guardados.
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

    // Adiciona um item ao inventário.
    private void addItem(String type, String name, String customName, double qty) {
        if (!itemNames.contains(name)) {
             itemNames.add(name);
        }
        quantities.put(name, qty);
        itemTypes.put(name, type);
        customNames.put(name, customName);
        saveInventory();
    }

    // Ativa ou desativa o overlay.
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

    // Atualiza o texto do botão do overlay.
    private void updateButtonText() {
        btnToggle.setText(overlayAtivo ? R.string.close_overlay : R.string.open_overlay);
    }

    // Inicia o overlay, pedindo permissão se necessário.
    private void startOverlayWithPermission() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            return;
        }
        startService(new Intent(this, OverlayService.class));
        handler.postDelayed(this::updatePrices, 600);
    }

    // Atualiza os preços dos itens.
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
            Log.e(TAG, "Erro Steam", e);
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