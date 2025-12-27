package com.example.csmarketoverlay;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Locale;
import java.util.Objects;

// Adaptador otimizado para a lista de preços do inventário usando ListAdapter.
public class InventoryPriceAdapter extends ListAdapter<InventoryItem, InventoryPriceAdapter.ViewHolder> {

    // Construtor. Passa o DIFF_CALLBACK para o ListAdapter saber como comparar itens.
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
        // Usa getItem() do ListAdapter, que é mais seguro.
        InventoryItem item = getItem(position);
        if (item != null) {
            holder.bind(item);
        }
    }

    // ViewHolder que representa a UI de cada item na lista.
    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView itemType, itemName, itemQuantity, itemPrice;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            itemType = itemView.findViewById(R.id.itemType);
            itemName = itemView.findViewById(R.id.itemName);
            itemQuantity = itemView.findViewById(R.id.itemQuantity);
            itemPrice = itemView.findViewById(R.id.itemPrice);
        }

        // Associa os dados do item à UI.
        public void bind(InventoryItem item) {
            itemType.setText(item.getType());
            itemName.setText(item.getName());
            itemQuantity.setText(String.format(Locale.US, "x%.0f", item.getQuantity()));
            itemPrice.setText(String.format(Locale.US, "%.2f€", item.getPrice() * item.getQuantity()));
        }
    }

    // O callback que o ListAdapter usa para detetar mudanças na lista de forma eficiente.
    private static final DiffUtil.ItemCallback<InventoryItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<InventoryItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull InventoryItem oldItem, @NonNull InventoryItem newItem) {
            // O nome é o nosso identificador único. Se for o mesmo, o item é o mesmo.
            return Objects.equals(oldItem.getName(), newItem.getName());
        }

        @Override
        public boolean areContentsTheSame(@NonNull InventoryItem oldItem, @NonNull InventoryItem newItem) {
            // Agora verifica se o conteúdo mudou para decidir se precisa de redesenhar o item.
            return oldItem.getQuantity() == newItem.getQuantity()
                    && oldItem.getPrice() == newItem.getPrice()
                    && Objects.equals(oldItem.getType(), newItem.getType());
        }
    };
}
