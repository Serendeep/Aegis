package com.serendeep.flick.vault;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import java.io.InputStream;

public class VaultAccessManager {
    private static final String PREFS_NAME = "flick_prefs";
    private static final String KEY_VAULT_URI = "vault_uri";
    private final Context _context;

    public VaultAccessManager(Context context) { _context = context; }

    public void saveVaultUri(Uri uri) {
        _context.getContentResolver().takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        _context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_VAULT_URI, uri.toString()).apply();
    }

    public Uri getSavedVaultUri() {
        String uriStr = _context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_VAULT_URI, null);
        return uriStr != null ? Uri.parse(uriStr) : null;
    }

    public boolean hasVaultUri() { return getSavedVaultUri() != null; }

    public byte[] readVaultFile() throws Exception {
        Uri uri = getSavedVaultUri();
        if (uri == null) throw new IllegalStateException("No vault URI configured");
        try (InputStream is = _context.getContentResolver().openInputStream(uri)) {
            return is.readAllBytes();
        }
    }

    public void clearVaultUri() {
        _context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_VAULT_URI).apply();
    }
}
