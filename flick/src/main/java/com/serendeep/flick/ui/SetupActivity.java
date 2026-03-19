package com.serendeep.flick.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.serendeep.flick.R;
import com.serendeep.flick.vault.VaultAccessManager;
import com.serendeep.flick.vault.VaultEntry;
import com.serendeep.flick.vault.VaultFile;
import com.serendeep.flick.vault.VaultHolder;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

@AndroidEntryPoint
public class SetupActivity extends AppCompatActivity {
    @Inject VaultHolder _vaultHolder;
    @Inject VaultAccessManager _vaultAccess;

    private TextView _txtStatus;

    private final ActivityResultLauncher<String[]> _filePicker =
        registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onVaultFileSelected);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        _txtStatus = findViewById(R.id.txt_status);

        findViewById(R.id.btn_select_vault).setOnClickListener(v ->
            _filePicker.launch(new String[]{"application/json", "application/octet-stream", "*/*"})
        );

        findViewById(R.id.btn_enable_accessibility).setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        findViewById(R.id.btn_manage_urls).setOnClickListener(v -> {
            Intent intent = new Intent(this, UrlMappingsActivity.class);
            startActivity(intent);
        });

        if (_vaultAccess.hasVaultUri()) {
            showAccessibilityStep();
        }
    }

    private void onVaultFileSelected(Uri uri) {
        if (uri == null) return;
        _vaultAccess.saveVaultUri(uri);
        showPasswordDialog();
    }

    private void showPasswordDialog() {
        TextInputEditText input = new TextInputEditText(this);
        input.setHint("Vault password");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        FrameLayout container = new FrameLayout(this);
        int margin = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = margin;
        lp.rightMargin = margin;
        container.addView(input, lp);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Enter Password")
                .setView(container)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String password = input.getText() != null ? input.getText().toString() : "";
                    decryptVault(password);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void decryptVault(String password) {
        try {
            byte[] vaultBytes = _vaultAccess.readVaultFile();
            JSONObject json = new JSONObject(new String(vaultBytes, StandardCharsets.UTF_8));
            VaultFile vault = VaultFile.fromJson(json);

            List<VaultEntry> entries;
            if (vault.isEncrypted()) {
                entries = vault.decrypt(password.toCharArray());
            } else {
                entries = vault.getEntries();
            }

            _vaultHolder.setEntries(entries);
            _vaultHolder.unlock();
            _txtStatus.setText(getString(R.string.setup_vault_loaded, entries.size()));
            showAccessibilityStep();
        } catch (Exception e) {
            _txtStatus.setText(getString(R.string.setup_vault_error));
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showAccessibilityStep() {
        findViewById(R.id.card_accessibility).setVisibility(View.VISIBLE);
        findViewById(R.id.card_url_mappings).setVisibility(View.VISIBLE);
    }
}
