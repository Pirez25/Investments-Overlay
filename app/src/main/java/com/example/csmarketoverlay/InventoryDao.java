package com.example.csmarketoverlay;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface InventoryDao {

    @Query("SELECT * FROM inventory_items ORDER BY name ASC")
    List<InventoryItem> getAll();

    @Query("SELECT * FROM inventory_items WHERE name = :itemName LIMIT 1")
    InventoryItem getItemByName(String itemName);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(InventoryItem... items);

    @Query("DELETE FROM inventory_items WHERE name = :itemName")
    void deleteByName(String itemName);

    @Query("DELETE FROM inventory_items")
    void deleteAll();

    @Query("UPDATE inventory_items SET quantity = :newQuantity WHERE name = :itemName")
    void updateQuantity(String itemName, double newQuantity);
}
