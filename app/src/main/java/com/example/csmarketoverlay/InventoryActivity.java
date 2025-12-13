package com.example.csmarketoverlay;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InventoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private InventoryAdapter adapter;
    private EditText etName, etQty, etCustomName;
    private Button btnAdd;
    private Spinner itemTypeSpinner;

    private List<String> itemNames = new ArrayList<>();
    private Map<String, Double> quantities = new HashMap<>();
    private Map<String, String> itemTypes = new HashMap<>();
    private Map<String, String> customNames = new HashMap<>();
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        prefs = getSharedPreferences("CSOverlayPrefs", MODE_PRIVATE);

        recyclerView = findViewById(R.id.recyclerInventory);
        etName = findViewById(R.id.etItemName);
        etQty = findViewById(R.id.etItemQty);
        etCustomName = findViewById(R.id.etCustomName);
        btnAdd = findViewById(R.id.btnAdd);
        itemTypeSpinner = findViewById(R.id.itemTypeSpinner);

        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(this,
                R.array.item_types, android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        itemTypeSpinner.setAdapter(spinnerAdapter);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new InventoryAdapter(itemNames, quantities, this::saveAndUpdate);
        recyclerView.setAdapter(adapter);

        loadInventory();

        btnAdd.setOnClickListener(v -> addNewItem());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadInventory() {
        itemNames.clear();
        quantities.clear();
        itemTypes.clear();
        customNames.clear();
        String data = prefs.getString("items", "");
        if (data.isEmpty()) {
            addItem("Steam", "Gallery Case", "", 203.0);
            addItem("Steam", "CS20 Case", "", 254.0);
            addItem("Steam", "Dreams & Nightmares Case", "", 102.0);
        } else {
            String[] items = data.split(";");
            for (String s : items) {
                if (s.contains(":")) {
                    String[] p = s.split(":");
                    if (p.length == 4) {
                        addItem(p[0], p[1], p[2], Double.parseDouble(p[3]));
                    } else if (p.length == 3) {
                        addItem(p[0], p[1], "", Double.parseDouble(p[2]));
                    } else if (p.length == 2) {
                        addItem("Steam", p[0], "", Double.parseDouble(p[1]));
                    }
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void addNewItem() {
        String name = etName.getText().toString().trim();
        String customName = etCustomName.getText().toString().trim();
        String qtyStr = etQty.getText().toString().trim();
        String type = itemTypeSpinner.getSelectedItem().toString();

        if (name.isEmpty() || qtyStr.isEmpty()) {
            Toast.makeText(this, R.string.fill_everything, Toast.LENGTH_SHORT).show();
            return;
        }
        double qty = Double.parseDouble(qtyStr);
        addItem(type, name, customName, qty);
        etName.setText("");
        etCustomName.setText("");
        etQty.setText("");
    }

    private void addItem(String type, String name, String customName, double qty) {
        if (!itemNames.contains(name)) {
            itemNames.add(name);
        }
        quantities.put(name, qty);
        itemTypes.put(name, type);
        customNames.put(name, customName);
        saveAndUpdate();
    }

    public void removeItem(String name) {
        itemNames.remove(name);
        quantities.remove(name);
        itemTypes.remove(name);
        customNames.remove(name);
        saveAndUpdate();
    }

    public void showEditDialog(String name) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.edit_quantity);

        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.valueOf(quantities.get(name)));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 40, 60, 20);
        layout.addView(input);

        builder.setView(layout);

        builder.setPositiveButton(R.string.ok, (dialog, which) -> {
            String newQtyStr = input.getText().toString();
            if (!newQtyStr.isEmpty()) {
                double newQty = Double.parseDouble(newQtyStr);
                updateItemQuantity(name, newQty);
            }
        });
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());

        builder.show();
    }

    public void updateItemQuantity(String name, double newQty) {
        quantities.put(name, newQty);
        saveAndUpdate();
    }

    private void saveAndUpdate() {
        StringBuilder sb = new StringBuilder();
        for (String name : itemNames) {
            sb.append(itemTypes.get(name)).append(":").append(name).append(":").append(customNames.get(name)).append(":").append(quantities.get(name)).append(";");
        }
        prefs.edit().putString("items", sb.toString()).apply();
        adapter.notifyDataSetChanged();

        // Avisa a MainActivity
        sendBroadcast(new Intent("INVENTORY_UPDATED"));
    }
}