package com.serendeep.flick.vault;

import com.serendeep.flick.crypto.CryptParameters;
import com.serendeep.flick.crypto.CryptoUtils;

import org.json.JSONObject;

public class VaultSlot {
    public static final int TYPE_RAW = 0x00;
    public static final int TYPE_PASSWORD = 0x01;
    public static final int TYPE_BIOMETRIC = 0x02;

    private final int _type;
    private final String _uuid;
    private final byte[] _encryptedKey;
    private final CryptParameters _keyParams;

    // SCrypt params (password slots only)
    private final int _n;
    private final int _r;
    private final int _p;
    private final byte[] _salt;

    private VaultSlot(int type, String uuid, byte[] encryptedKey,
                      CryptParameters keyParams, int n, int r, int p, byte[] salt) {
        _type = type;
        _uuid = uuid;
        _encryptedKey = encryptedKey;
        _keyParams = keyParams;
        _n = n;
        _r = r;
        _p = p;
        _salt = salt;
    }

    public static VaultSlot fromJson(JSONObject obj) throws VaultParseException {
        try {
            int type = obj.getInt("type");
            String uuid = obj.getString("uuid");
            byte[] key = CryptoUtils.hexToBytes(obj.getString("key"));

            JSONObject kp = obj.getJSONObject("key_params");
            CryptParameters keyParams = new CryptParameters(
                    CryptoUtils.hexToBytes(kp.getString("nonce")),
                    CryptoUtils.hexToBytes(kp.getString("tag"))
            );

            int n = 0, r = 0, p = 0;
            byte[] salt = null;
            if (type == TYPE_PASSWORD) {
                n = obj.getInt("n");
                r = obj.getInt("r");
                p = obj.getInt("p");
                salt = CryptoUtils.hexToBytes(obj.getString("salt"));
            }

            return new VaultSlot(type, uuid, key, keyParams, n, r, p, salt);
        } catch (Exception e) {
            throw new VaultParseException("Failed to parse slot", e);
        }
    }

    public int getType() {
        return _type;
    }

    public String getUuid() {
        return _uuid;
    }

    public byte[] getEncryptedKey() {
        return _encryptedKey;
    }

    public CryptParameters getKeyParams() {
        return _keyParams;
    }

    public int getN() {
        return _n;
    }

    public int getR() {
        return _r;
    }

    public int getP() {
        return _p;
    }

    public byte[] getSalt() {
        return _salt;
    }
}
