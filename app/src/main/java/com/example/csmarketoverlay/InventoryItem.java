package com.example.csmarketoverlay;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.io.Serializable;

@Entity(tableName = "inventory_items")
public class InventoryItem implements Serializable {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "name")
    private final String name;

    @ColumnInfo(name = "quantity")
    private final double quantity;

    @ColumnInfo(name = "price")
    private double price;

    @ColumnInfo(name = "type")
    private final String type;

    @ColumnInfo(name = "custom_name")
    private final String customName;

    public InventoryItem(@NonNull String name, double quantity, double price, String type, String customName) {
        this.name = name;
        this.quantity = quantity;
        this.price = price;
        this.type = type;
        this.customName = customName;
    }

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

    public void setPrice(double price) {
        this.price = price;
    }
}
