package com.example.csmarketoverlay;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class InventoryPriceAdapter extends RecyclerView.Adapter<InventoryPriceAdapter.ViewHolder> {

    private final List<InventoryItem> inventoryItems;

    public InventoryPriceAdapter(List<InventoryItem> inventoryItems) {
        this.inventoryItems = inventoryItems;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.inventory_price_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InventoryItem item = inventoryItems.get(position);
        holder.itemType.setText(item.getType());
        holder.itemName.setText(item.getName());
        holder.itemQuantity.setText("x" + item.getQuantity());
        holder.itemPrice.setText(String.format("%.2f€", item.getPrice() * item.getQuantity()));
    }

    @Override
    public int getItemCount() {
        return inventoryItems.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView itemType, itemName, itemQuantity, itemPrice;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            itemType = itemView.findViewById(R.id.itemType);
            itemName = itemView.findViewById(R.id.itemName);
            itemQuantity = itemView.findViewById(R.id.itemQuantity);
            itemPrice = itemView.findViewById(R.id.itemPrice);
        }
    }
}