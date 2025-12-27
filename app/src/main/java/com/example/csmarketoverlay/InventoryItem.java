package com.example.csmarketoverlay;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

// Define a tabela "inventory_items" para a base de dados Room.
@Entity(tableName = "inventory_items")
public class InventoryItem {

    // O nome completo do item (ex: "AK-47 | Redline (Field-Tested)") é a chave primária.
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "name")
    private final String name;

    @ColumnInfo(name = "quantity")
    private final double quantity;

    // O preço não é final, pois é atualizado com frequência.
    @ColumnInfo(name = "price")
    private double price;

    // O tipo do item (ex: "Steam", "Crypto").
    @ColumnInfo(name = "type")
    private final String type;

    // A alcunha do item, pode ser nula ou vazia.
    @ColumnInfo(name = "custom_name")
    private final String customName;

    public InventoryItem(@NonNull String name, double quantity, double price, String type, String customName) {
        this.name = name;
        this.quantity = quantity;
        this.price = price;
        this.type = type;
        this.customName = customName;
    }

    // Getters
    @NonNull
    public String getName() {
        return name;
    }

    public double getQuantity() {
        return quantity;
    }

    public double getPrice() {
        return price;
    }

    public String getType() {
        return type;
    }

    public String getCustomName() {
        return customName;
    }

    // Setter
    public void setPrice(double price) {
        this.price = price;
    }
}
