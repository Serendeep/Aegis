package com.serendeep.flick.otp;

import org.junit.Test;

import static org.junit.Assert.*;

public class OtpTest {

    // RFC 6238 test vector: SHA1, 20-byte secret "12345678901234567890"
    private static final byte[] RFC_SECRET_SHA1 = "12345678901234567890".getBytes();

    // RFC 4226 Appendix D test vectors: counter 0-9
    // counter=0 → "755224", counter=1 → "287082", etc.
    @Test
    public void testHotpRfc4226() throws Exception {
        HotpInfo info = new HotpInfo(RFC_SECRET_SHA1, "SHA1", 6, 0);
        assertEquals("755224", info.getOtp());
    }

    @Test
    public void testHotpRfc4226Counter1() throws Exception {
        HotpInfo info = new HotpInfo(RFC_SECRET_SHA1, "SHA1", 6, 1);
        assertEquals("287082", info.getOtp());
    }

    // RFC 6238 test vector: SHA1, time=59, digits=8, period=30 → "94287082"
    @Test
    public void testTotpSha1_8digits() throws Exception {
        TotpInfo info = new TotpInfo(RFC_SECRET_SHA1, "SHA1", 8, 30);
        String otp = info.getOtp(59);
        assertEquals("94287082", otp);
    }

    // 6-digit SHA1 at time=59, period=30 → counter=1 → last 6 of "94287082" = "287082"
    @Test
    public void testTotpSha1_6digits() throws Exception {
        TotpInfo info = new TotpInfo(RFC_SECRET_SHA1, "SHA1", 6, 30);
        String otp = info.getOtp(59);
        assertEquals("287082", otp);
    }

    // RFC 6238: SHA1, time=1111111109, digits=8 → "07081804"
    @Test
    public void testTotpSha1_time1111111109() throws Exception {
        TotpInfo info = new TotpInfo(RFC_SECRET_SHA1, "SHA1", 8, 30);
        String otp = info.getOtp(1111111109);
        assertEquals("07081804", otp);
    }

    @Test
    public void testSteamOtp() throws Exception {
        // Steam uses TOTP with custom 26-char alphabet encoding, 5 digits
        SteamInfo info = new SteamInfo(RFC_SECRET_SHA1);
        String otp = info.getOtp(59);
        assertEquals(5, otp.length());
        String steamAlphabet = "23456789BCDFGHJKMNPQRTVWXY";
        for (char c : otp.toCharArray()) {
            assertTrue("Invalid Steam char: " + c,
                    steamAlphabet.indexOf(c) >= 0);
        }
    }

    @Test
    public void testMillisTillNextRotation() {
        long millis = TotpInfo.getMillisTillNextRotation(30);
        assertTrue("Should be > 0", millis > 0);
        assertTrue("Should be <= 30000", millis <= 30000);
    }

    @Test
    public void testSteamTypeId() {
        SteamInfo info = new SteamInfo(RFC_SECRET_SHA1);
        assertEquals("steam", info.getTypeId());
    }

    @Test
    public void testMotpStubTypeId() {
        MotpInfo info = new MotpInfo(RFC_SECRET_SHA1, "1234");
        assertEquals("motp", info.getTypeId());
    }

    @Test
    public void testYandexStubTypeId() {
        YandexInfo info = new YandexInfo(RFC_SECRET_SHA1, "1234");
        assertEquals("yandex", info.getTypeId());
    }
}
