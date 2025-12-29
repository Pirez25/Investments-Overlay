package com.example.csmarketoverlay;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {InventoryItem.class}, version = 2) // Versão incrementada para 2
public abstract class AppDatabase extends RoomDatabase {

    public abstract InventoryDao inventoryDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "inventory_database")
                            // Se a migração falhar, destrói e recria a base de dados.
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
