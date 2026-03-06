package com.serendeep.flick.crypto;

import org.bouncycastle.crypto.generators.SCrypt;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtils {
    private CryptoUtils() {
    }

    public static byte[] deriveKey(char[] password, byte[] salt, int n, int r, int p) {
        ByteBuffer buf = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));
        byte[] passwordBytes = new byte[buf.limit()];
        buf.get(passwordBytes);
        try {
            return SCrypt.generate(passwordBytes, salt, n, r, p, 32);
        } finally {
            Arrays.fill(passwordBytes, (byte) 0);
            if (buf.hasArray()) {
                Arrays.fill(buf.array(), (byte) 0);
            }
        }
    }

    public static byte[] decrypt(byte[] ciphertext, CryptParameters params, byte[] key)
            throws CryptoException {
        try {
            // JCE GCM expects ciphertext||tag
            byte[] tag = params.getTag();
            byte[] combined = new byte[ciphertext.length + tag.length];
            System.arraycopy(ciphertext, 0, combined, 0, ciphertext.length);
            System.arraycopy(tag, 0, combined, ciphertext.length, tag.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, params.getNonce());
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
            return cipher.doFinal(combined);
        } catch (GeneralSecurityException e) {
            throw new CryptoException(e);
        }
    }

    public static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string must be non-null and have even length");
        }
        int len = hex.length();
        byte[] bytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int high = Character.digit(hex.charAt(i), 16);
            int low = Character.digit(hex.charAt(i + 1), 16);
            if (high == -1 || low == -1) {
                throw new IllegalArgumentException(
                        String.format("Invalid hex character at index %d", high == -1 ? i : i + 1));
            }
            bytes[i / 2] = (byte) ((high << 4) + low);
        }
        return bytes;
    }
}
