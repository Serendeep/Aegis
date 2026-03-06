package com.serendeep.flick.vault;

import com.serendeep.flick.crypto.CryptParameters;
import com.serendeep.flick.crypto.CryptoException;
import com.serendeep.flick.crypto.CryptoUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

public class VaultFile {
    private static final int FILE_VERSION = 1;

    private final boolean _encrypted;
    private final List<VaultSlot> _slots;
    private final CryptParameters _params;
    private final String _encryptedDb;
    private final List<VaultEntry> _entries;

    private VaultFile(boolean encrypted, List<VaultSlot> slots,
                      CryptParameters params, String encryptedDb,
                      List<VaultEntry> entries) {
        _encrypted = encrypted;
        _slots = slots;
        _params = params;
        _encryptedDb = encryptedDb;
        _entries = entries;
    }

    public static VaultFile fromJson(JSONObject obj) throws VaultParseException {
        try {
            int version = obj.getInt("version");
            if (version > FILE_VERSION) {
                throw new VaultParseException("Unsupported vault version: " + version);
            }

            JSONObject header = obj.getJSONObject("header");
            boolean encrypted = !header.isNull("slots") && !header.isNull("params");

            if (encrypted) {
                List<VaultSlot> slots = new ArrayList<>();
                JSONArray slotsArr = header.getJSONArray("slots");
                for (int i = 0; i < slotsArr.length(); i++) {
                    slots.add(VaultSlot.fromJson(slotsArr.getJSONObject(i)));
                }

                JSONObject p = header.getJSONObject("params");
                CryptParameters params = new CryptParameters(
                        CryptoUtils.hexToBytes(p.getString("nonce")),
                        CryptoUtils.hexToBytes(p.getString("tag"))
                );

                return new VaultFile(true, Collections.unmodifiableList(slots), params, obj.getString("db"), null);
            } else {
                List<VaultEntry> entries = parseEntries(obj.getJSONObject("db"));
                return new VaultFile(false, null, null, null, Collections.unmodifiableList(entries));
            }
        } catch (VaultParseException e) {
            throw e;
        } catch (Exception e) {
            throw new VaultParseException("Failed to parse vault file", e);
        }
    }

    public List<VaultEntry> decrypt(char[] password) throws VaultParseException {
        if (!_encrypted) {
            throw new VaultParseException("Vault is not encrypted");
        }

        for (VaultSlot slot : _slots) {
            if (slot.getType() != VaultSlot.TYPE_PASSWORD) {
                continue;
            }

            byte[] derivedKey = null;
            byte[] masterKey = null;
            byte[] plaintext = null;
            try {
                derivedKey = CryptoUtils.deriveKey(
                        password, slot.getSalt(), slot.getN(), slot.getR(), slot.getP());

                masterKey = CryptoUtils.decrypt(
                        slot.getEncryptedKey(), slot.getKeyParams(), derivedKey);

                byte[] dbCiphertext = Base64.getDecoder().decode(_encryptedDb);
                plaintext = CryptoUtils.decrypt(dbCiphertext, _params, masterKey);

                // GCM tag verified, parse content
                try {
                    JSONObject db = new JSONObject(new String(plaintext, StandardCharsets.UTF_8));
                    return parseEntries(db);
                } catch (Exception e) {
                    throw new VaultParseException("Decryption succeeded but content is invalid", e);
                }
            } catch (CryptoException e) {
                // Wrong password, try next slot
            } finally {
                if (derivedKey != null) Arrays.fill(derivedKey, (byte) 0);
                if (masterKey != null) Arrays.fill(masterKey, (byte) 0);
                if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
            }
        }

        throw new VaultParseException("Unable to decrypt vault with the given password");
    }

    private static List<VaultEntry> parseEntries(JSONObject db) throws VaultParseException {
        try {
            List<VaultEntry> entries = new ArrayList<>();
            JSONArray arr = db.getJSONArray("entries");
            for (int i = 0; i < arr.length(); i++) {
                try {
                    entries.add(VaultEntry.fromJson(arr.getJSONObject(i)));
                } catch (VaultParseException ignored) {
                    // Skip unparseable entries for forward compatibility
                }
            }
            return entries;
        } catch (Exception e) {
            throw new VaultParseException("Failed to parse vault content", e);
        }
    }

    public boolean isEncrypted() {
        return _encrypted;
    }

    public List<VaultSlot> getSlots() {
        return _slots;
    }

    public List<VaultEntry> getEntries() {
        return _entries;
    }

    public VaultSlot findPasswordSlot() {
        if (_slots == null) {
            return null;
        }
        for (VaultSlot slot : _slots) {
            if (slot.getType() == VaultSlot.TYPE_PASSWORD) {
                return slot;
            }
        }
        return null;
    }
}
