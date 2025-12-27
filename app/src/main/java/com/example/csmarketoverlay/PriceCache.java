package com.example.csmarketoverlay;

import android.content.Context;
import android.content.SharedPreferences;

public class PriceCache {

    private static final String PREFS_NAME = "PriceCache";
    private static final long CACHE_DURATION = 5 * 60 * 1000;

    private final SharedPreferences prefs;

    public PriceCache(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void savePrice(String itemName, double price) {
        prefs.edit()
             .putFloat(itemName, (float) price)
             .putLong(itemName + "_timestamp", System.currentTimeMillis())
             .apply();
    }

    // Obtém um preço da cache, se ele for "fresco" (menos de 5 minutos).
    public double getFreshPrice(String itemName) {
        if (isPriceFresh(itemName)) {
            return prefs.getFloat(itemName, -1f);
        }
        return -1; // Devolve -1 se o preço não for válido.
    }

    // Obtém o último preço guardado, independentemente da sua idade.
    public double getAnyPrice(String itemName) {
        return prefs.getFloat(itemName, -1f); // Devolve -1 se nunca foi guardado.
    }

    private boolean isPriceFresh(String itemName) {
        long timestamp = prefs.getLong(itemName + "_timestamp", 0);
        return System.currentTimeMillis() - timestamp < CACHE_DURATION;
    }
}