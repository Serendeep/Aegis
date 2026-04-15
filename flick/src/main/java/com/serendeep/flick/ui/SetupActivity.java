package com.serendeep.flick.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.autofill.AutofillManager;
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
import com.serendeep.flick.vault.VaultCacheManager;
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
    @Inject VaultCacheManager _vaultCache;

    private TextView _txtStatus;
    private TextView _txtAccessibilityStatus;
    private TextView _txtAutofillStatus;
    private TextView _txtBatteryStatus;

    private final ActivityResultLauncher<String[]> _filePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onVaultFileSelected);

    private final ActivityResultLauncher<Intent> _autofillLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                updateAutofillStatus();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        _txtStatus = findViewById(R.id.txt_status);
        _txtAccessibilityStatus = findViewById(R.id.txt_accessibility_status);
        _txtAutofillStatus = findViewById(R.id.txt_autofill_status);
        _txtBatteryStatus = findViewById(R.id.txt_battery_status);

        findViewById(R.id.btn_select_vault).setOnClickListener(v ->
                _filePicker.launch(new String[]{"application/json", "application/octet-stream", "*/*"})
        );

        findViewById(R.id.btn_enable_autofill).setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE);
            intent.setData(Uri.parse("package:" + getPackageName()));
            _autofillLauncher.launch(intent);
        });

        findViewById(R.id.btn_enable_battery).setOnClickListener(v -> {
            Intent batteryIntent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            batteryIntent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(batteryIntent);
        });

        findViewById(R.id.btn_enable_accessibility).setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        findViewById(R.id.btn_manage_urls).setOnClickListener(v -> {
            Intent intent = new Intent(this, UrlMappingsActivity.class);
            startActivity(intent);
        });

        if (_vaultAccess.hasVaultUri()) {
            showPostVaultSteps();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccessibilityStatus();
        updateBatteryStatus();
        updateAutofillStatus();
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
            _vaultCache.cacheEntries(entries);
            _vaultHolder.unlock();
            _txtStatus.setText(getString(R.string.setup_vault_loaded, entries.size()));
            showPostVaultSteps();
        } catch (Exception e) {
            _txtStatus.setText(getString(R.string.setup_vault_error));
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showPostVaultSteps() {
        findViewById(R.id.card_autofill).setVisibility(View.VISIBLE);
        findViewById(R.id.card_accessibility).setVisibility(View.VISIBLE);
        findViewById(R.id.card_battery).setVisibility(View.VISIBLE);
        findViewById(R.id.card_url_mappings).setVisibility(View.VISIBLE);
        updateAutofillStatus();
        updateBatteryStatus();
    }

    private void updateAccessibilityStatus() {
        String enabledServices = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String myService = getPackageName() + "/"
                + "com.serendeep.flick.services.OtpAccessibilityService";
        boolean enabled = false;
        if (enabledServices != null) {
            android.text.TextUtils.SimpleStringSplitter splitter =
                    new android.text.TextUtils.SimpleStringSplitter(':');
            splitter.setString(enabledServices);
            while (splitter.hasNext()) {
                if (splitter.next().equals(myService)) {
                    enabled = true;
                    break;
                }
            }
        }
        if (enabled) {
            _txtAccessibilityStatus.setText(R.string.setup_accessibility_enabled);
            _txtAccessibilityStatus.setVisibility(View.VISIBLE);
        } else {
            _txtAccessibilityStatus.setVisibility(View.GONE);
        }
    }

    private void updateBatteryStatus() {
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
            _txtBatteryStatus.setText(R.string.setup_battery_enabled);
            _txtBatteryStatus.setVisibility(View.VISIBLE);
        } else {
            _txtBatteryStatus.setVisibility(View.GONE);
        }
    }

    private void updateAutofillStatus() {
        AutofillManager afm = getSystemService(AutofillManager.class);
        if (afm != null && afm.hasEnabledAutofillServices()) {
            _txtAutofillStatus.setText(R.string.setup_autofill_enabled);
            _txtAutofillStatus.setVisibility(View.VISIBLE);
        } else {
            _txtAutofillStatus.setVisibility(View.GONE);
        }
    }
}
