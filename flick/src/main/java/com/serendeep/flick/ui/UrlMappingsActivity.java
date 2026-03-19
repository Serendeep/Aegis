package com.serendeep.flick.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.serendeep.flick.R;
import com.serendeep.flick.vault.UrlMappingStore;
import com.serendeep.flick.vault.VaultEntry;
import com.serendeep.flick.vault.VaultHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

@AndroidEntryPoint
public class UrlMappingsActivity extends AppCompatActivity {
    @Inject UrlMappingStore _urlMappingStore;
    @Inject VaultHolder _vaultHolder;

    private MappingAdapter _adapter;
    private TextView _txtEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_url_mappings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        _txtEmpty = findViewById(R.id.txt_empty);

        RecyclerView recycler = findViewById(R.id.recycler_mappings);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        _adapter = new MappingAdapter();
        recycler.setAdapter(_adapter);

        FloatingActionButton fab = findViewById(R.id.fab_add);
        fab.setOnClickListener(v -> showAddDialog());

        applyWindowInsets(recycler, fab);
        refreshList();
    }

    private void applyWindowInsets(RecyclerView recycler, FloatingActionButton fab) {
        ViewCompat.setOnApplyWindowInsetsListener(fab, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.rightMargin = insets.right + dpToPx(16);
            lp.bottomMargin = insets.bottom + dpToPx(16);
            v.setLayoutParams(lp);
            return windowInsets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(recycler, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    v.getPaddingLeft(),
                    v.getPaddingTop(),
                    v.getPaddingRight(),
                    insets.bottom + dpToPx(88)
            );
            return windowInsets;
        });
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void refreshList() {
        Map<String, String> mappings = _urlMappingStore.getAllMappings();
        List<MappingItem> items = new ArrayList<>();

        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            String entryName = resolveEntryName(entry.getValue());
            items.add(new MappingItem(entry.getKey(), entry.getValue(), entryName));
        }

        _adapter.setItems(items);
        _txtEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private String resolveEntryName(String uuid) {
        for (VaultEntry entry : _vaultHolder.getEntries()) {
            if (entry.getUuid().equals(uuid)) {
                String issuer = entry.getIssuer();
                String name = entry.getName();
                if (!issuer.isEmpty() && !name.isEmpty()) {
                    return issuer + " — " + name;
                }
                return !issuer.isEmpty() ? issuer : name;
            }
        }
        return uuid;
    }

    private void showAddDialog() {
        List<VaultEntry> entries = _vaultHolder.getEntries();
        if (entries.isEmpty()) return;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_mapping, null);
        TextInputEditText inputUrl = dialogView.findViewById(R.id.input_url);
        Spinner spinner = dialogView.findViewById(R.id.spinner_entry);

        List<String> labels = new ArrayList<>();
        for (VaultEntry entry : entries) {
            String issuer = entry.getIssuer();
            String name = entry.getName();
            if (!issuer.isEmpty() && !name.isEmpty()) {
                labels.add(issuer + " — " + name);
            } else {
                labels.add(!issuer.isEmpty() ? issuer : name);
            }
        }

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerAdapter);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.url_mappings_add)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String url = inputUrl.getText() != null
                            ? inputUrl.getText().toString().trim() : "";
                    int pos = spinner.getSelectedItemPosition();
                    if (!url.isEmpty() && pos >= 0 && pos < entries.size()) {
                        _urlMappingStore.setMapping(url, entries.get(pos).getUuid());
                        refreshList();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static class MappingItem {
        final String urlPattern;
        final String entryUuid;
        final String entryName;

        MappingItem(String urlPattern, String entryUuid, String entryName) {
            this.urlPattern = urlPattern;
            this.entryUuid = entryUuid;
            this.entryName = entryName;
        }
    }

    private class MappingAdapter extends RecyclerView.Adapter<MappingAdapter.ViewHolder> {
        private List<MappingItem> _items = new ArrayList<>();

        void setItems(List<MappingItem> items) {
            _items = items;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_url_mapping, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            MappingItem item = _items.get(position);
            holder.txtUrl.setText(item.urlPattern);
            holder.txtEntry.setText(item.entryName);
            holder.btnDelete.setOnClickListener(v -> {
                _urlMappingStore.removeMapping(item.urlPattern);
                refreshList();
            });
        }

        @Override
        public int getItemCount() {
            return _items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView txtUrl;
            final TextView txtEntry;
            final ImageButton btnDelete;

            ViewHolder(View itemView) {
                super(itemView);
                txtUrl = itemView.findViewById(R.id.txt_url_pattern);
                txtEntry = itemView.findViewById(R.id.txt_entry_name);
                btnDelete = itemView.findViewById(R.id.btn_delete);
            }
        }
    }
}
