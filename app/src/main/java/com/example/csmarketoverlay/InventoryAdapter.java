package com.example.csmarketoverlay;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

public class InventoryAdapter extends RecyclerView.Adapter<InventoryAdapter.ViewHolder> {

    public interface OnItemInteractionListener {
        void onRemoveItem(InventoryItem item);
        void onEditItem(InventoryItem item);
    }

    private final List<InventoryItem> items;
    private final OnItemInteractionListener listener;

    public InventoryAdapter(List<InventoryItem> items, OnItemInteractionListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.inventory_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InventoryItem item = items.get(position);

        holder.itemName.setText(item.getName());
        // Usa um método robusto para formatar o número
        holder.itemQuantity.setText("x" + formatQuantity(item.getQuantity()));

        holder.removeItemButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRemoveItem(item);
            }
        });

        View.OnClickListener editClickListener = v -> {
            if (listener != null) {
                listener.onEditItem(item);
            }
        };

        holder.itemQuantity.setOnClickListener(editClickListener);
        holder.editIcon.setOnClickListener(editClickListener);
    }

    private String formatQuantity(double quantity) {
        if (quantity == (long) quantity) {
            return String.format(Locale.US, "%d", (long) quantity);
        }
        return String.valueOf(quantity);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView itemName, itemQuantity;
        ImageView editIcon;
        Button removeItemButton;

        ViewHolder(View itemView) {
            super(itemView);
            itemName = itemView.findViewById(R.id.itemName);
            itemQuantity = itemView.findViewById(R.id.itemQuantity);
            editIcon = itemView.findViewById(R.id.edit_icon);
            removeItemButton = itemView.findViewById(R.id.removeItemButton);
        }
    }
}