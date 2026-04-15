package com.serendeep.flick.otp;

import com.google.common.io.BaseEncoding;
import com.serendeep.flick.vault.VaultParseException;

import org.json.JSONException;
import org.json.JSONObject;

public abstract class OtpInfo {
    public static final String DEFAULT_ALGORITHM = "SHA1";
    public static final int DEFAULT_DIGITS = 6;

    protected final byte[] _secret;
    protected final String _algorithm;
    protected final int _digits;

    protected OtpInfo(byte[] secret, String algorithm, int digits) {
        _secret = secret;
        _algorithm = algorithm;
        _digits = digits;
    }

    public abstract String getOtp() throws OtpException;

    public abstract String getTypeId();

    public byte[] getSecret() {
        return _secret;
    }

    public String getAlgorithm() {
        return _algorithm;
    }

    public String getHmacAlgorithm() {
        return "Hmac" + _algorithm;
    }

    public int getDigits() {
        return _digits;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("secret", BaseEncoding.base32().encode(_secret));
        obj.put("algo", _algorithm);
        obj.put("digits", _digits);
        return obj;
    }

    public static OtpInfo fromJson(String type, JSONObject obj) throws VaultParseException {
        try {
            byte[] secret = BaseEncoding.base32().decode(
                    obj.getString("secret").toUpperCase());
            String algo = obj.getString("algo");
            int digits = obj.getInt("digits");

            switch (type) {
                case "totp":
                    return new TotpInfo(secret, algo, digits, obj.getInt("period"));
                case "hotp":
                    return new HotpInfo(secret, algo, digits, obj.getLong("counter"));
                case "steam":
                    return new SteamInfo(secret, algo, digits, obj.getInt("period"));
                case "motp":
                    return new MotpInfo(secret, obj.optString("pin", ""));
                case "yandex":
                    return new YandexInfo(secret, obj.optString("pin", ""));
                default:
                    throw new VaultParseException("Unknown OTP type: " + type);
            }
        } catch (VaultParseException e) {
            throw e;
        } catch (Exception e) {
            throw new VaultParseException("Failed to parse OTP info", e);
        }
    }
}
