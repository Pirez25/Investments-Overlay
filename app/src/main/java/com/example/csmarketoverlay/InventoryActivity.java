package com.example.csmarketoverlay;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.DynamicColors;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InventoryActivity extends AppCompatActivity implements InventoryAdapter.OnItemInteractionListener {

    private InventoryAdapter adapter;
    private AutoCompleteTextView etItemName;
    private EditText etQty, etCustomName;
    private Spinner itemTypeSpinner;

    private final List<InventoryItem> inventoryItems = new ArrayList<>();
    private SharedPreferences prefs;
    private AppDatabase db;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private List<String> csgoItemsSuggestions = new ArrayList<>();
    private List<String> cryptoIdsSuggestions = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivitiesIfAvailable(this.getApplication());
        setContentView(R.layout.activity_inventory);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.inventory_title);
        }

        prefs = getSharedPreferences("CSOverlayPrefs", MODE_PRIVATE);
        db = AppDatabase.getDatabase(this);

        etItemName = findViewById(R.id.etItemName);
        etQty = findViewById(R.id.etItemQty);
        etCustomName = findViewById(R.id.etCustomName);
        Button btnAdd = findViewById(R.id.btnAdd);
        itemTypeSpinner = findViewById(R.id.itemTypeSpinner);

        loadSuggestionData();
        setupSpinnerAndSuggestions();
        setupRecyclerView();

        ApiRequestExecutor.getInstance().execute(this::migrateFromSharedPreferences);

        btnAdd.setOnClickListener(v -> addNewItem());
    }

    private void loadSuggestionData() {
        try {
            AssetManager assetManager = getAssets();
            InputStream is = assetManager.open("csgo_items.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String json = new String(buffer, StandardCharsets.UTF_8);
            JSONObject obj = new JSONObject(json);

            JSONArray csgoArray = obj.getJSONArray("csgo_items");
            for (int i = 0; i < csgoArray.length(); i++) {
                csgoItemsSuggestions.add(csgoArray.getString(i));
            }

            JSONArray cryptoArray = obj.getJSONArray("crypto_ids");
            for (int i = 0; i < cryptoArray.length(); i++) {
                cryptoIdsSuggestions.add(cryptoArray.getString(i));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void setupSpinnerAndSuggestions() {
        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(this,
                R.array.item_types, android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        itemTypeSpinner.setAdapter(spinnerAdapter);

        itemTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateSuggestions(parent.getItemAtPosition(position).toString());
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        updateSuggestions("Steam");
    }

    private void updateSuggestions(String type) {
        List<String> suggestions = type.equals("Crypto") ? cryptoIdsSuggestions : csgoItemsSuggestions;
        ArrayAdapter<String> suggestionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, suggestions);
        etItemName.setAdapter(suggestionAdapter);
    }

    private void setupRecyclerView() {
        RecyclerView recyclerView = findViewById(R.id.recyclerInventory);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new InventoryAdapter(inventoryItems, this);
        recyclerView.setAdapter(adapter);
    }

    private void loadInventoryFromDb() {
        ApiRequestExecutor.getInstance().execute(() -> {
            List<InventoryItem> itemsFromDb = db.inventoryDao().getAll();
            handler.post(() -> {
                inventoryItems.clear();
                inventoryItems.addAll(itemsFromDb);
                adapter.notifyDataSetChanged();
                sendInventoryUpdateBroadcast();
            });
        });
    }

    private void addNewItem() {
        String name = etItemName.getText().toString().trim();
        String customName = etCustomName.getText().toString().trim();
        String qtyStr = etQty.getText().toString().trim();
        String type = itemTypeSpinner.getSelectedItem().toString();

        if (name.isEmpty() || qtyStr.isEmpty()) {
            Toast.makeText(this, R.string.fill_everything_toast, Toast.LENGTH_SHORT).show();
            return;
        }

        ApiRequestExecutor.getInstance().execute(() -> {
            if (db.inventoryDao().getItemByName(name) != null) {
                handler.post(() -> Toast.makeText(this, R.string.item_exists_toast, Toast.LENGTH_SHORT).show());
                return;
            }

            double qty = Double.parseDouble(qtyStr);
            InventoryItem newItem = new InventoryItem(name, qty, 0, type, customName);
            db.inventoryDao().insertAll(newItem);
            
            handler.post(() -> {
                inventoryItems.add(newItem);
                Collections.sort(inventoryItems, (o1, o2) -> o1.getName().compareTo(o2.getName()));
                adapter.notifyDataSetChanged(); 
                etItemName.setText("");
                etCustomName.setText("");
                etQty.setText("");
                sendInventoryUpdateBroadcast();
            });
        });
    }

    @Override
    public void onRemoveItem(InventoryItem item) {
        int position = inventoryItems.indexOf(item);
        if (position != -1) {
            inventoryItems.remove(position);
            adapter.notifyItemRemoved(position);
            ApiRequestExecutor.getInstance().execute(() -> {
                db.inventoryDao().deleteByName(item.getName());
                sendInventoryUpdateBroadcast();
            });
        }
    }

    @Override
    public void onEditItem(InventoryItem item) {
        showEditDialog(item);
    }

    public void showEditDialog(InventoryItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.edit_quantity_title);

        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.valueOf(item.getQuantity()));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 40, 60, 20);
        layout.addView(input);

        builder.setView(layout);

        builder.setPositiveButton(R.string.ok, (dialog, which) -> {
            String newQtyStr = input.getText().toString();
            if (!newQtyStr.isEmpty()) {
                double newQty = Double.parseDouble(newQtyStr);
                int position = inventoryItems.indexOf(item);
                if(position != -1){
                    InventoryItem updatedItem = new InventoryItem(item.getName(), newQty, item.getPrice(), item.getType(), item.getCustomName());
                    inventoryItems.set(position, updatedItem);
                    adapter.notifyItemChanged(position);
                    ApiRequestExecutor.getInstance().execute(() -> {
                        db.inventoryDao().updateQuantity(item.getName(), newQty);
                        sendInventoryUpdateBroadcast();
                    });
                }
            }
        });
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void sendInventoryUpdateBroadcast() {
        sendBroadcast(new Intent("INVENTORY_UPDATED"));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void migrateFromSharedPreferences() {
        boolean migrationDone = prefs.getBoolean("room_migration_done", false);
        if (!migrationDone) {
            String oldData = prefs.getString("items", "");
            if (!oldData.isEmpty()) {
                List<InventoryItem> itemsToMigrate = new ArrayList<>();
                String[] items = oldData.split(";");
                for (String s : items) {
                    if (s.contains(":")) {
                        try {
                            String[] p = s.split(":");
                             if (p.length == 4) {
                                itemsToMigrate.add(new InventoryItem(p[1], Double.parseDouble(p[3]), 0, p[0], p[2]));
                            } else if (p.length == 3) {
                                itemsToMigrate.add(new InventoryItem(p[1], Double.parseDouble(p[2]), 0, p[0], ""));
                            } else if (p.length == 2) {
                                itemsToMigrate.add(new InventoryItem(p[0], Double.parseDouble(p[1]), 0, "Steam", ""));
                            }
                        } catch (Exception e) {
                            // Ignora
                        }
                    }
                }
                db.inventoryDao().insertAll(itemsToMigrate.toArray(new InventoryItem[0]));
            }
            prefs.edit().putBoolean("room_migration_done", true).apply();
        }
        loadInventoryFromDb();
    }
}
