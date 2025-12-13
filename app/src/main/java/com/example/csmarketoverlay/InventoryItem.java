package com.example.csmarketoverlay;

public class InventoryItem {
    private final String name;
    private final double quantity;
    private double price;
    private final String type;

    public InventoryItem(String name, double quantity, double price, String type) {
        this.name = name;
        this.quantity = quantity;
        this.price = price;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public double getQuantity() {
        return quantity;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getType() {
        return type;
    }
}