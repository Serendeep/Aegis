package com.serendeep.flick.ui;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

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
public class QuickAuthActivity extends FragmentActivity {

    public static final String ACTION_AUTH_CANCELLED =
            "com.serendeep.flick.ACTION_AUTH_CANCELLED";
    public static final String EXTRA_AUTOFILL_MODE = "autofill_mode";

    private static final int REQUEST_CODE_AUTOFILL = 1001;

    @Inject VaultHolder _vaultHolder;
    @Inject VaultAccessManager _vaultAccess;
    @Inject VaultCacheManager _vaultCache;

    private boolean _isAutofillMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        _isAutofillMode = getIntent().getBooleanExtra(EXTRA_AUTOFILL_MODE, false);

        if (_vaultHolder.isUnlocked()) {
            onSuccess();
            return;
        }

        showBiometricPrompt();
    }

    public static IntentSender createIntentSender(Context context) {
        Intent intent = new Intent(context, QuickAuthActivity.class);
        intent.putExtra(EXTRA_AUTOFILL_MODE, true);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, REQUEST_CODE_AUTOFILL, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return pendingIntent.getIntentSender();
    }

    private void showBiometricPrompt() {
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.auth_title))
                .setSubtitle(getString(R.string.auth_subtitle))
                .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG
                                | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        BiometricPrompt prompt = new BiometricPrompt(this,
                ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        onAuthenticated();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED
                                && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                                && errorCode != BiometricPrompt.ERROR_CANCELED) {
                            Toast.makeText(QuickAuthActivity.this, errString, Toast.LENGTH_SHORT).show();
                        }
                        onCancelled();
                    }
                });

        prompt.authenticate(promptInfo);
    }

    private void onAuthenticated() {
        // Already loaded — just unlock
        if (_vaultHolder.isLoaded()) {
            _vaultHolder.unlock();
            onSuccess();
            return;
        }

        // Try cache first
        List<VaultEntry> cached = _vaultCache.getCachedEntries();
        if (cached != null) {
            _vaultHolder.setEntries(cached);
            _vaultHolder.unlock();
            onSuccess();
            return;
        }

        // Load from vault file
        try {
            byte[] vaultBytes = _vaultAccess.readVaultFile();
            JSONObject json = new JSONObject(new String(vaultBytes, StandardCharsets.UTF_8));
            VaultFile vault = VaultFile.fromJson(json);

            if (!vault.isEncrypted()) {
                List<VaultEntry> entries = vault.getEntries();
                _vaultHolder.setEntries(entries);
                _vaultCache.cacheEntries(entries);
                _vaultHolder.unlock();
                onSuccess();
            } else {
                showPasswordDialog(vault);
            }
        } catch (Exception e) {
            Toast.makeText(this, R.string.auth_vault_load_failed, Toast.LENGTH_SHORT).show();
            onCancelled();
        }
    }

    private void showPasswordDialog(VaultFile vault) {
        TextInputEditText input = new TextInputEditText(this);
        input.setHint(R.string.auth_password_hint);
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
                .setTitle(getString(R.string.auth_password_title))
                .setView(container)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String password = input.getText() != null ? input.getText().toString() : "";
                    decryptVault(vault, password);
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> onCancelled())
                .show();
    }

    private void decryptVault(VaultFile vault, String password) {
        try {
            List<VaultEntry> entries = vault.decrypt(password.toCharArray());
            _vaultHolder.setEntries(entries);
            _vaultCache.cacheEntries(entries);
            _vaultHolder.unlock();
            onSuccess();
        } catch (Exception e) {
            Toast.makeText(this, R.string.auth_wrong_password, Toast.LENGTH_SHORT).show();
            showPasswordDialog(vault);
        }
    }

    private void onSuccess() {
        if (_isAutofillMode) {
            setResult(RESULT_OK);
        }
        finish();
        overridePendingTransition(0, 0);
    }

    private void onCancelled() {
        if (_isAutofillMode) {
            setResult(RESULT_CANCELED);
        } else {
            sendBroadcast(new Intent(ACTION_AUTH_CANCELLED));
        }
        finish();
        overridePendingTransition(0, 0);
    }
}
