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

// O adaptador para a lista de inventário.
public class InventoryAdapter extends RecyclerView.Adapter<InventoryAdapter.ViewHolder> {

    // A lista de nomes dos itens.
    private final List<String> names;
    // O mapa de quantidades dos itens.
    private final Map<String, Double> quantities;
    // A ação a ser executada quando o inventário é atualizado.
    private final Runnable onUpdate;

    // Construtor da classe.
    public InventoryAdapter(List<String> names, Map<String, Double> quantities, Runnable onUpdate) {
        this.names = names;
        this.quantities = quantities;
        this.onUpdate = onUpdate;
    }

    // Este método é chamado quando um novo item da lista é criado.
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.inventory_item, parent, false);
        return new ViewHolder(v);
    }

    // Este método é chamado para associar os dados de um item a um item da lista.
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String name = names.get(position);
        holder.itemName.setText(name);
        holder.itemQuantity.setText("× " + quantities.get(name));

        // Define a ação do botão de remover.
        holder.removeItemButton.setOnClickListener(v -> {
            ((InventoryActivity) v.getContext()).removeItem(name);
        });

        // Define a ação de clique no item.
        holder.itemView.setOnClickListener(v -> {
            ((InventoryActivity) v.getContext()).showEditDialog(name);
        });
    }

    // Devolve o número de itens na lista.
    @Override
    public int getItemCount() {
        return names.size();
    }

    // A classe que representa cada item da lista.
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