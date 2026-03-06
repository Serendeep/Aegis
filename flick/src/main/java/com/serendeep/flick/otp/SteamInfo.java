package com.serendeep.flick.otp;

public class SteamInfo extends TotpInfo {
    private static final String STEAM_ALPHABET = "23456789BCDFGHJKMNPQRTVWXY";

    public SteamInfo(byte[] secret) {
        super(secret, "SHA1", 5, DEFAULT_PERIOD);
    }

    public SteamInfo(byte[] secret, String algorithm, int digits, int period) {
        super(secret, algorithm, digits, period);
    }

    @Override
    public String getOtp(long time) throws OtpException {
        long counter = (long) Math.floor((double) time / getPeriod());
        int code = HotpInfo.generateRawCode(_secret, getHmacAlgorithm(), counter);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < getDigits(); i++) {
            sb.append(STEAM_ALPHABET.charAt(code % STEAM_ALPHABET.length()));
            code /= STEAM_ALPHABET.length();
        }
        return sb.toString();
    }

    @Override
    public String getTypeId() {
        return "steam";
    }
}
