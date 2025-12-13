package com.example.csmarketoverlay;

import android.content.Context;
import android.content.SharedPreferences;

public class PriceCache {

    private static final String PREFS_NAME = "PriceCache";
    private static final long CACHE_DURATION = 5 * 60 * 1000; // 5 minutos

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

    public double getPrice(String itemName) {
        if (isPriceValid(itemName)) {
            return prefs.getFloat(itemName, 0.0f);
        }
        return -1; // Indica que o preço não está na cache ou é inválido
    }

    private boolean isPriceValid(String itemName) {
        long timestamp = prefs.getLong(itemName + "_timestamp", 0);
        return System.currentTimeMillis() - timestamp < CACHE_DURATION;
    }
}