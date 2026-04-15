package com.serendeep.flick.otp;

import org.json.JSONException;
import org.json.JSONObject;

public class TotpInfo extends OtpInfo {
    public static final int DEFAULT_PERIOD = 30;

    private final int _period;

    public TotpInfo(byte[] secret, String algorithm, int digits, int period) {
        super(secret, algorithm, digits);
        _period = period;
    }

    @Override
    public String getOtp() throws OtpException {
        return getOtp(System.currentTimeMillis() / 1000);
    }

    public String getOtp(long time) throws OtpException {
        long counter = (long) Math.floor((double) time / _period);
        return HotpInfo.generateOtp(_secret, getHmacAlgorithm(), _digits, counter);
    }

    @Override
    public JSONObject toJson() throws JSONException {
        JSONObject obj = super.toJson();
        obj.put("period", _period);
        return obj;
    }

    @Override
    public String getTypeId() {
        return "totp";
    }

    public int getPeriod() {
        return _period;
    }

    public long getMillisTillNextRotation() {
        return getMillisTillNextRotation(_period);
    }

    public static long getMillisTillNextRotation(int period) {
        long p = period * 1000L;
        return p - (System.currentTimeMillis() % p);
    }
}
