package com.serendeep.flick.otp;

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
    public String getTypeId() {
        return "motp";
    }
}
