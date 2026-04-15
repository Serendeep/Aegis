package com.serendeep.flick.vault;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class VaultCacheManagerTest {
    private VaultCacheManager _cacheManager;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        // Use plain SharedPreferences for testing (EncryptedSharedPreferences
        // requires real Android Keystore which is unavailable in Robolectric)
        android.content.SharedPreferences prefs = context.getSharedPreferences(
                "flick_vault_cache_test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        _cacheManager = new VaultCacheManager(prefs);
    }

    @Test
    public void testCacheHit() throws Exception {
        List<VaultEntry> entries = Arrays.asList(
                makeEntry("GitHub", "user@github.com"),
                makeEntry("Google", "user@gmail.com")
        );

        _cacheManager.cacheEntries(entries);
        List<VaultEntry> cached = _cacheManager.getCachedEntries();

        assertNotNull(cached);
        assertEquals(2, cached.size());
        assertEquals("GitHub", cached.get(0).getIssuer());
        assertEquals("Google", cached.get(1).getIssuer());
    }

    @Test
    public void testCacheMissWhenEmpty() {
        List<VaultEntry> cached = _cacheManager.getCachedEntries();
        assertNull(cached);
    }

    @Test
    public void testCacheMissWhenExpired() throws Exception {
        List<VaultEntry> entries = Collections.singletonList(
                makeEntry("GitHub", "user@github.com")
        );

        _cacheManager.cacheEntries(entries);
        // Simulate expiry by setting timeout to 0
        _cacheManager.setCacheTimeoutMs(0);

        List<VaultEntry> cached = _cacheManager.getCachedEntries();
        assertNull(cached);
    }

    @Test
    public void testClearCache() throws Exception {
        List<VaultEntry> entries = Collections.singletonList(
                makeEntry("GitHub", "user@github.com")
        );

        _cacheManager.cacheEntries(entries);
        assertNotNull(_cacheManager.getCachedEntries());

        _cacheManager.clearCache();
        assertNull(_cacheManager.getCachedEntries());
    }

    @Test
    public void testHasCachedEntries() throws Exception {
        assertFalse(_cacheManager.hasCachedEntries());

        _cacheManager.cacheEntries(Collections.singletonList(
                makeEntry("GitHub", "user@github.com")
        ));

        assertTrue(_cacheManager.hasCachedEntries());
    }

    @Test
    public void testCachePreservesEntryFields() throws Exception {
        VaultEntry original = makeEntry("Dropbox", "myaccount");
        _cacheManager.cacheEntries(Collections.singletonList(original));

        List<VaultEntry> cached = _cacheManager.getCachedEntries();
        assertNotNull(cached);
        assertEquals(1, cached.size());

        VaultEntry restored = cached.get(0);
        assertEquals(original.getUuid(), restored.getUuid());
        assertEquals(original.getIssuer(), restored.getIssuer());
        assertEquals(original.getName(), restored.getName());
        assertEquals(original.getType(), restored.getType());
    }

    private VaultEntry makeEntry(String issuer, String name) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("type", "totp");
        obj.put("uuid", java.util.UUID.randomUUID().toString());
        obj.put("name", name);
        obj.put("issuer", issuer);
        obj.put("note", "");
        obj.put("favorite", false);
        JSONObject info = new JSONObject();
        info.put("secret", "JBSWY3DPEHPK3PXP");
        info.put("algo", "SHA1");
        info.put("digits", 6);
        info.put("period", 30);
        obj.put("info", info);
        obj.put("groups", new JSONArray());
        return VaultEntry.fromJson(obj);
    }
}
