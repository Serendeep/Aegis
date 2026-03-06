package com.serendeep.flick.otp;

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
    public String getTypeId() {
        return "yandex";
    }
}
