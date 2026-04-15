package com.serendeep.flick.otp;

import org.json.JSONException;
import org.json.JSONObject;

public class YandexInfo extends TotpInfo {
    private final String _pin;

    public YandexInfo(byte[] secret, String pin) {
        super(secret, "SHA256", 8, 30);
        _pin = pin;
    }

    public String getPin() {
        return _pin;
    }

    @Override
    public JSONObject toJson() throws JSONException {
        JSONObject obj = super.toJson();
        obj.put("pin", _pin);
        return obj;
    }

    @Override
    public String getTypeId() {
        return "yandex";
    }
}
