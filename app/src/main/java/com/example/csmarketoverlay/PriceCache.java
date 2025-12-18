package com.example.csmarketoverlay;

import android.content.Context;
import android.content.SharedPreferences;

// Esta classe gere um sistema de cache para os preços dos itens.
// Guarda os preços localmente para evitar fazer pedidos repetidos à API.
public class PriceCache {

    // Nome do ficheiro onde a cache é guardada.
    private static final String PREFS_NAME = "PriceCache";
    // Duração da cache em milissegundos (5 minutos).
    private static final long CACHE_DURATION = 5 * 60 * 1000;

    // Objeto para aceder à cache guardada.
    private final SharedPreferences prefs;

    // Construtor da classe, que é chamado quando a classe é criada.
    public PriceCache(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // Guarda um preço na cache.
    public void savePrice(String itemName, double price) {
        prefs.edit()
             .putFloat(itemName, (float) price) // Guarda o preço do item.
             .putLong(itemName + "_timestamp", System.currentTimeMillis()) // Guarda a hora a que o preço foi guardado.
             .apply(); // Aplica as alterações.
    }

    // Obtém um preço da cache, se ele for válido.
    public double getPrice(String itemName) {
        if (isPriceValid(itemName)) {
            return prefs.getFloat(itemName, 0.0f); // Devolve o preço guardado.
        }
        return -1; // Devolve -1 se o preço não for válido.
    }

    // Verifica se um preço na cache ainda é válido.
    private boolean isPriceValid(String itemName) {
        long timestamp = prefs.getLong(itemName + "_timestamp", 0);
        // Devolve 'true' se o preço tiver sido guardado há menos de 5 minutos.
        return System.currentTimeMillis() - timestamp < CACHE_DURATION;
    }
}