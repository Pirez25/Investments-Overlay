package com.example.csmarketoverlay;

// Esta classe representa um item no inventário.
public class InventoryItem {
    // O nome do item.
    private final String name;
    // A quantidade do item.
    private final double quantity;
    // O preço do item.
    private double price;
    // O tipo do item (ex: "Steam", "Crypto", "Stock").
    private final String type;

    // Construtor da classe.
    public InventoryItem(String name, double quantity, double price, String type) {
        this.name = name;
        this.quantity = quantity;
        this.price = price;
        this.type = type;
    }

    // Devolve o nome do item.
    public String getName() {
        return name;
    }

    // Devolve a quantidade do item.
    public double getQuantity() {
        return quantity;
    }

    // Devolve o preço do item.
    public double getPrice() {
        return price;
    }

    // Define o preço do item.
    public void setPrice(double price) {
        this.price = price;
    }

    // Devolve o tipo do item.
    public String getType() {
        return type;
    }
}
