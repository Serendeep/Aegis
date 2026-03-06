package com.serendeep.flick.otp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class HotpInfo extends OtpInfo {
    private final long _counter;

    public HotpInfo(byte[] secret, String algorithm, int digits, long counter) {
        super(secret, algorithm, digits);
        _counter = counter;
    }

    @Override
    public String getOtp() throws OtpException {
        return generateOtp(_secret, getHmacAlgorithm(), _digits, _counter);
    }

    @Override
    public String getTypeId() {
        return "hotp";
    }

    public long getCounter() {
        return _counter;
    }

    static String generateOtp(byte[] secret, String hmacAlgo, int digits, long counter)
            throws OtpException {
        int code = generateRawCode(secret, hmacAlgo, counter);
        int truncated = code % (int) Math.pow(10, digits);

        StringBuilder sb = new StringBuilder(Integer.toString(truncated));
        while (sb.length() < digits) {
            sb.insert(0, "0");
        }
        return sb.toString();
    }

    static int generateRawCode(byte[] secret, String hmacAlgo, long counter)
            throws OtpException {
        try {
            byte[] counterBytes = ByteBuffer.allocate(8)
                    .order(ByteOrder.BIG_ENDIAN)
                    .putLong(counter)
                    .array();

            Mac mac = Mac.getInstance(hmacAlgo);
            mac.init(new SecretKeySpec(secret, "RAW"));
            byte[] hash = mac.doFinal(counterBytes);

            int offset = hash[hash.length - 1] & 0xf;
            return ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
        } catch (Exception e) {
            throw new OtpException(e);
        }
    }
}
