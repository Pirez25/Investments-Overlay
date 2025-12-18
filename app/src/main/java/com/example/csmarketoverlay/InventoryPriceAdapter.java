package com.example.csmarketoverlay;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

// O adaptador para a lista de preços do inventário.
public class InventoryPriceAdapter extends RecyclerView.Adapter<InventoryPriceAdapter.ViewHolder> {

    // A lista de itens do inventário.
    private final List<InventoryItem> inventoryItems;

    // Construtor da classe.
    public InventoryPriceAdapter(List<InventoryItem> inventoryItems) {
        this.inventoryItems = inventoryItems;
    }

    // Este método é chamado quando um novo item da lista é criado.
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.inventory_price_item, parent, false);
        return new ViewHolder(view);
    }

    // Este método é chamado para associar os dados de um item a um item da lista.
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InventoryItem item = inventoryItems.get(position);
        holder.itemType.setText(item.getType());
        holder.itemName.setText(item.getName());
        holder.itemQuantity.setText("x" + item.getQuantity());
        holder.itemPrice.setText(String.format("%.2f€", item.getPrice() * item.getQuantity()));
    }

    // Devolve o número de itens na lista.
    @Override
    public int getItemCount() {
        return inventoryItems.size();
    }

    // A classe que representa cada item da lista.
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