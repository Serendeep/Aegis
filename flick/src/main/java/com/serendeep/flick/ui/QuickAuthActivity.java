package com.serendeep.flick.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.serendeep.flick.R;
import com.serendeep.flick.vault.VaultAccessManager;
import com.serendeep.flick.vault.VaultFile;
import com.serendeep.flick.vault.VaultHolder;

import org.json.JSONObject;

import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

@AndroidEntryPoint
public class QuickAuthActivity extends FragmentActivity {

    public static final String ACTION_AUTH_CANCELLED =
            "com.serendeep.flick.ACTION_AUTH_CANCELLED";

    @Inject VaultHolder _vaultHolder;
    @Inject VaultAccessManager _vaultAccess;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        if (_vaultHolder.isUnlocked()) {
            finish();
        overridePendingTransition(0, 0);
            return;
        }

        if (_vaultHolder.isLoaded()) {
            showBiometricPrompt();
            return;
        }

        showBiometricPrompt();
    }

    private void showBiometricPrompt() {
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Flick")
            .setSubtitle("Authenticate to access OTP codes")
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
                    sendBroadcast(new Intent(ACTION_AUTH_CANCELLED));
                    finish();
        overridePendingTransition(0, 0);
                }
            });

        prompt.authenticate(promptInfo);
    }

    private void onAuthenticated() {
        if (!_vaultHolder.isLoaded()) {
            try {
                byte[] vaultBytes = _vaultAccess.readVaultFile();
                JSONObject json = new JSONObject(new String(vaultBytes, java.nio.charset.StandardCharsets.UTF_8));
                VaultFile vault = VaultFile.fromJson(json);

                if (!vault.isEncrypted()) {
                    _vaultHolder.setEntries(vault.getEntries());
                }
            } catch (Exception e) {
                Toast.makeText(this, "Failed to load vault", Toast.LENGTH_SHORT).show();
                sendBroadcast(new Intent(ACTION_AUTH_CANCELLED));
                finish();
        overridePendingTransition(0, 0);
                return;
            }
        }

        _vaultHolder.unlock();
        finish();
        overridePendingTransition(0, 0);
    }
}
