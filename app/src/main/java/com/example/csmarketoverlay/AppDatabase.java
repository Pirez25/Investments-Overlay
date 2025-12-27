package com.example.csmarketoverlay;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {InventoryItem.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {

    public abstract InventoryDao inventoryDao();

    private static volatile AppDatabase INSTANCE;

    // Usamos um padrão Singleton para garantir que só existe uma instância da base de dados.
    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "inventory_database")
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
