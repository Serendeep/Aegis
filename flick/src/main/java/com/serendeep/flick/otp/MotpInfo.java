package com.serendeep.flick.otp;

import org.json.JSONException;
import org.json.JSONObject;

public class MotpInfo extends TotpInfo {
    private final String _pin;

    public MotpInfo(byte[] secret, String pin) {
        super(secret, "MD5", 6, 10);
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
        return "motp";
    }
}
