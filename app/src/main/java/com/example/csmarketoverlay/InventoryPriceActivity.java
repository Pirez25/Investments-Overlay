package com.example.csmarketoverlay;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class InventoryPriceActivity extends AppCompatActivity {

    private InventoryPriceAdapter adapter;
    private TextView totalInventoryValueTextView;
    private final List<InventoryItem> inventoryItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory_price);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.prices_title);
        }

        totalInventoryValueTextView = findViewById(R.id.totalInventoryValueTextView);
        setupRecyclerView();

        Button editButton = findViewById(R.id.editButton);
        editButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, InventoryActivity.class);
            startActivity(intent);
        });

        if (getIntent().hasExtra("inventory_items")) {
            List<InventoryItem> items = (List<InventoryItem>) getIntent().getSerializableExtra("inventory_items");
            if (items != null) {
                this.inventoryItems.addAll(items);
                sortAndDisplayItems();
            }
        }
    }

    private void setupRecyclerView() {
        RecyclerView recyclerView = findViewById(R.id.inventoryPriceRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new InventoryPriceAdapter();
        recyclerView.setAdapter(adapter);
    }

    private void sortAndDisplayItems() {
        Collections.sort(inventoryItems, (item1, item2) -> {
            double value1 = item1.getPrice() * item1.getQuantity();
            double value2 = item2.getPrice() * item2.getQuantity();
            return Double.compare(value2, value1);
        });

        adapter.submitList(this.inventoryItems);
        updateTotalValue();
    }

    private void updateTotalValue() {
        double totalValue = 0;
        for (InventoryItem item : inventoryItems) {
            if (item.getPrice() >= 0) {
                totalValue += item.getPrice() * item.getQuantity();
            }
        }
        totalInventoryValueTextView.setText(getString(R.string.total_label) + String.format(Locale.getDefault(), " %.2f€", totalValue));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
