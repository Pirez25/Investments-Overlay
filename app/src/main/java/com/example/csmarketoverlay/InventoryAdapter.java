package com.example.csmarketoverlay;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Map;

public class InventoryAdapter extends RecyclerView.Adapter<InventoryAdapter.ViewHolder> {

    private final List<String> names;
    private final Map<String, Double> quantities;
    private final Runnable onUpdate;

    public InventoryAdapter(List<String> names, Map<String, Double> quantities, Runnable onUpdate) {
        this.names = names;
        this.quantities = quantities;
        this.onUpdate = onUpdate;
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
        String name = names.get(position);
        holder.itemName.setText(name);
        holder.itemQuantity.setText("× " + quantities.get(name));

        holder.removeItemButton.setOnClickListener(v -> {
            ((InventoryActivity) v.getContext()).removeItem(name);
        });

        holder.itemView.setOnClickListener(v -> {
            ((InventoryActivity) v.getContext()).showEditDialog(name);
        });
    }

    @Override
    public int getItemCount() {
        return names.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView itemName, itemQuantity;
        Button removeItemButton;
        ViewHolder(View itemView) {
            super(itemView);
            itemName = itemView.findViewById(R.id.itemName);
            itemQuantity = itemView.findViewById(R.id.itemQuantity);
            removeItemButton = itemView.findViewById(R.id.removeItemButton);
        }
    }
}