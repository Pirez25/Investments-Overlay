package com.example.csmarketoverlay;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

public class InventoryPriceAdapter extends ListAdapter<InventoryItem, InventoryPriceAdapter.ViewHolder> {

    public InventoryPriceAdapter() {
        super(DIFF_CALLBACK);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.inventory_price_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InventoryItem item = getItem(position);
        if (item != null) {
            holder.bind(item);
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView itemType, itemName, itemQuantity, itemPrice;
        private final NumberFormat numberFormat;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            itemType = itemView.findViewById(R.id.itemType);
            itemName = itemView.findViewById(R.id.itemName);
            itemQuantity = itemView.findViewById(R.id.itemQuantity);
            itemPrice = itemView.findViewById(R.id.itemPrice);
            this.numberFormat = new DecimalFormat("0.########");
        }

        public void bind(InventoryItem item) {
            itemType.setText(item.getType());
            itemName.setText(item.getName());
            itemQuantity.setText("x" + numberFormat.format(item.getQuantity()));
            itemPrice.setText(String.format(Locale.US, "%.2f€", item.getPrice() * item.getQuantity()));
        }
    }

    private static final DiffUtil.ItemCallback<InventoryItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<InventoryItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull InventoryItem oldItem, @NonNull InventoryItem newItem) {
            return Objects.equals(oldItem.getName(), newItem.getName());
        }

        @Override
        public boolean areContentsTheSame(@NonNull InventoryItem oldItem, @NonNull InventoryItem newItem) {
            return oldItem.getQuantity() == newItem.getQuantity()
                    && oldItem.getPrice() == newItem.getPrice()
                    && Objects.equals(oldItem.getType(), newItem.getType());
        }
    };
}
