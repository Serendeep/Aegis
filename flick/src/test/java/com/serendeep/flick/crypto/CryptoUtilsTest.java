package com.serendeep.flick.crypto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@RunWith(RobolectricTestRunner.class)
public class CryptoUtilsTest {

    private JSONObject _vaultJson;

    @Before
    public void setUp() throws Exception {
        InputStream stream = getClass().getResourceAsStream("/aegis_encrypted.json");
        assertNotNull("Test vault file must be on classpath", stream);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = stream.read(buf)) != -1) {
            baos.write(buf, 0, n);
        }
        stream.close();

        _vaultJson = new JSONObject(baos.toString(StandardCharsets.UTF_8.name()));
    }

    @Test
    public void testDecryptMasterKey() throws Exception {
        JSONObject header = _vaultJson.getJSONObject("header");
        JSONArray slots = header.getJSONArray("slots");

        // Find the password slot (type == 1)
        JSONObject passwordSlot = null;
        for (int i = 0; i < slots.length(); i++) {
            JSONObject slot = slots.getJSONObject(i);
            if (slot.getInt("type") == 1) {
                passwordSlot = slot;
                break;
            }
        }
        assertNotNull("Vault must contain a password slot", passwordSlot);

        // Derive key from password using SCrypt
        byte[] salt = CryptoUtils.hexToBytes(passwordSlot.getString("salt"));
        int n = passwordSlot.getInt("n");
        int r = passwordSlot.getInt("r");
        int p = passwordSlot.getInt("p");
        byte[] derivedKey = CryptoUtils.deriveKey("test".toCharArray(), salt, n, r, p);

        // Decrypt the master key
        JSONObject keyParams = passwordSlot.getJSONObject("key_params");
        CryptParameters params = new CryptParameters(
                CryptoUtils.hexToBytes(keyParams.getString("nonce")),
                CryptoUtils.hexToBytes(keyParams.getString("tag"))
        );
        byte[] encryptedKey = CryptoUtils.hexToBytes(passwordSlot.getString("key"));

        byte[] masterKey = CryptoUtils.decrypt(encryptedKey, params, derivedKey);

        assertNotNull("Master key must not be null", masterKey);
        assertEquals("Master key must be 32 bytes", 32, masterKey.length);
    }

    @Test
    public void testDecryptVaultContent() throws Exception {
        JSONObject header = _vaultJson.getJSONObject("header");
        JSONArray slots = header.getJSONArray("slots");

        // Find password slot and decrypt master key
        JSONObject passwordSlot = null;
        for (int i = 0; i < slots.length(); i++) {
            JSONObject slot = slots.getJSONObject(i);
            if (slot.getInt("type") == 1) {
                passwordSlot = slot;
                break;
            }
        }
        assertNotNull(passwordSlot);

        byte[] salt = CryptoUtils.hexToBytes(passwordSlot.getString("salt"));
        byte[] derivedKey = CryptoUtils.deriveKey("test".toCharArray(), salt,
                passwordSlot.getInt("n"), passwordSlot.getInt("r"), passwordSlot.getInt("p"));

        JSONObject slotKeyParams = passwordSlot.getJSONObject("key_params");
        CryptParameters slotParams = new CryptParameters(
                CryptoUtils.hexToBytes(slotKeyParams.getString("nonce")),
                CryptoUtils.hexToBytes(slotKeyParams.getString("tag"))
        );
        byte[] masterKey = CryptoUtils.decrypt(
                CryptoUtils.hexToBytes(passwordSlot.getString("key")), slotParams, derivedKey);

        // Decrypt vault contents using master key
        JSONObject dbParams = header.getJSONObject("params");
        CryptParameters contentParams = new CryptParameters(
                CryptoUtils.hexToBytes(dbParams.getString("nonce")),
                CryptoUtils.hexToBytes(dbParams.getString("tag"))
        );
        byte[] dbContent = Base64.getDecoder().decode(_vaultJson.getString("db"));

        byte[] plaintext = CryptoUtils.decrypt(dbContent, contentParams, masterKey);
        assertNotNull("Decrypted content must not be null", plaintext);

        // Parse as JSON and verify structure
        String json = new String(plaintext, StandardCharsets.UTF_8);
        JSONObject db = new JSONObject(json);
        assertEquals("Vault content version must be 1", 1, db.getInt("version"));

        JSONArray entries = db.getJSONArray("entries");
        assertTrue("Vault must contain at least one entry", entries.length() > 0);
    }

    @Test
    public void testHexToBytes_valid() {
        byte[] result = CryptoUtils.hexToBytes("deadbeef");
        assertEquals(4, result.length);
        assertEquals((byte) 0xDE, result[0]);
        assertEquals((byte) 0xAD, result[1]);
        assertEquals((byte) 0xBE, result[2]);
        assertEquals((byte) 0xEF, result[3]);
    }

    @Test
    public void testHexToBytes_empty() {
        byte[] result = CryptoUtils.hexToBytes("");
        assertEquals(0, result.length);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHexToBytes_null() {
        CryptoUtils.hexToBytes(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHexToBytes_oddLength() {
        CryptoUtils.hexToBytes("abc");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHexToBytes_invalidChar() {
        CryptoUtils.hexToBytes("zz");
    }

    @Test(expected = CryptoException.class)
    public void testWrongPasswordFails() throws Exception {
        JSONObject header = _vaultJson.getJSONObject("header");
        JSONArray slots = header.getJSONArray("slots");

        JSONObject passwordSlot = null;
        for (int i = 0; i < slots.length(); i++) {
            JSONObject slot = slots.getJSONObject(i);
            if (slot.getInt("type") == 1) {
                passwordSlot = slot;
                break;
            }
        }
        assertNotNull(passwordSlot);

        // Derive key with WRONG password
        byte[] salt = CryptoUtils.hexToBytes(passwordSlot.getString("salt"));
        byte[] derivedKey = CryptoUtils.deriveKey("wrong".toCharArray(), salt,
                passwordSlot.getInt("n"), passwordSlot.getInt("r"), passwordSlot.getInt("p"));

        JSONObject keyParams = passwordSlot.getJSONObject("key_params");
        CryptParameters params = new CryptParameters(
                CryptoUtils.hexToBytes(keyParams.getString("nonce")),
                CryptoUtils.hexToBytes(keyParams.getString("tag"))
        );
        byte[] encryptedKey = CryptoUtils.hexToBytes(passwordSlot.getString("key"));

        // This should throw CryptoException because the wrong password produces a wrong key
        CryptoUtils.decrypt(encryptedKey, params, derivedKey);
    }
}
