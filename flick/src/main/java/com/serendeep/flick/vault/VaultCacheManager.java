package com.serendeep.flick.vault;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VaultCacheManager {
    private static final String PREFS_NAME = "flick_vault_cache";
    private static final String KEY_ENTRIES = "cached_entries";
    private static final String KEY_TIMESTAMP = "cache_timestamp";
    private static final long DEFAULT_CACHE_TIMEOUT_MS = 5 * 60 * 1000;

    private final SharedPreferences _prefs;
    private long _cacheTimeoutMs = DEFAULT_CACHE_TIMEOUT_MS;

    public VaultCacheManager(Context context) {
        _prefs = createPrefs(context);
    }

    VaultCacheManager(SharedPreferences prefs) {
        _prefs = prefs;
    }

    private static SharedPreferences createPrefs(Context context) {
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            return EncryptedSharedPreferences.create(
                    PREFS_NAME,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    public synchronized void cacheEntries(List<VaultEntry> entries) {
        try {
            JSONArray arr = new JSONArray();
            for (VaultEntry entry : entries) {
                arr.put(entry.toJson());
            }
            _prefs.edit()
                    .putString(KEY_ENTRIES, arr.toString())
                    .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                    .apply();
        } catch (Exception e) {
            Log.w("VaultCacheManager", "Failed to cache entries", e);
        }
    }

    public synchronized List<VaultEntry> getCachedEntries() {
        long timestamp = _prefs.getLong(KEY_TIMESTAMP, 0);
        if (timestamp == 0) {
            return null;
        }

        if (System.currentTimeMillis() - timestamp > _cacheTimeoutMs) {
            clearCache();
            return null;
        }

        String json = _prefs.getString(KEY_ENTRIES, null);
        if (json == null) {
            return null;
        }

        try {
            JSONArray arr = new JSONArray(json);
            List<VaultEntry> entries = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                try {
                    entries.add(VaultEntry.fromJson(arr.getJSONObject(i)));
                } catch (VaultParseException ignored) {
                }
            }
            return Collections.unmodifiableList(entries);
        } catch (Exception e) {
            return null;
        }
    }

    public synchronized boolean hasCachedEntries() {
        long timestamp = _prefs.getLong(KEY_TIMESTAMP, 0);
        return timestamp != 0
                && (System.currentTimeMillis() - timestamp <= _cacheTimeoutMs)
                && _prefs.contains(KEY_ENTRIES);
    }

    public synchronized void clearCache() {
        _prefs.edit()
                .remove(KEY_ENTRIES)
                .remove(KEY_TIMESTAMP)
                .apply();
    }

    public void setCacheTimeoutMs(long timeoutMs) {
        _cacheTimeoutMs = timeoutMs;
    }
}
