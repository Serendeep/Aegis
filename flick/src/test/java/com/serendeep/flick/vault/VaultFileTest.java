package com.serendeep.flick.vault;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class VaultFileTest {

    @Test
    public void testParsePlainVault() throws Exception {
        JSONObject json = loadTestVault("aegis_plain.json");
        VaultFile vault = VaultFile.fromJson(json);

        assertFalse(vault.isEncrypted());
        List<VaultEntry> entries = vault.getEntries();
        assertFalse(entries.isEmpty());

        // Verify first entry matches known fixture values
        VaultEntry entry = entries.get(0);
        assertEquals("3ae6f1ad-2e65-4ed2-a953-1ec0dff2386d", entry.getUuid());
        assertEquals("Deno", entry.getIssuer());
        assertEquals("Mason", entry.getName());
        assertEquals("totp", entry.getType());
        assertNotNull(entry.getOtpInfo());
    }

    @Test
    public void testParseEncryptedVault() throws Exception {
        JSONObject json = loadTestVault("aegis_encrypted.json");
        VaultFile vault = VaultFile.fromJson(json);

        assertTrue(vault.isEncrypted());
        assertNotNull(vault.getSlots());
        assertFalse(vault.getSlots().isEmpty());

        // Find password slot
        VaultSlot passwordSlot = vault.findPasswordSlot();
        assertNotNull(passwordSlot);
        assertEquals(1, passwordSlot.getType());

        // Decrypt and parse entries
        List<VaultEntry> entries = vault.decrypt("test".toCharArray());
        assertFalse(entries.isEmpty());

        // Verify decrypted entry has correct fields
        VaultEntry first = entries.get(0);
        assertNotNull(first.getUuid());
        assertNotNull(first.getIssuer());
        assertFalse(first.getIssuer().isEmpty());
    }

    @Test
    public void testEntryOtpTypes() throws Exception {
        JSONObject json = loadTestVault("aegis_plain.json");
        VaultFile vault = VaultFile.fromJson(json);

        // Verify we can parse all OTP types present in the test file
        for (VaultEntry entry : vault.getEntries()) {
            assertNotNull("Entry " + entry.getIssuer() + " has null OTP info",
                entry.getOtpInfo());
            assertTrue("Unknown type: " + entry.getType(),
                List.of("totp", "hotp", "steam").contains(entry.getType()));
        }
    }

    @Test
    public void testDecryptWithWrongPassword() throws Exception {
        JSONObject json = loadTestVault("aegis_encrypted.json");
        VaultFile vault = VaultFile.fromJson(json);

        try {
            vault.decrypt("wrong_password".toCharArray());
            fail("Should throw on wrong password");
        } catch (VaultParseException e) {
            // Expected
        }
    }

    private JSONObject loadTestVault(String name) throws Exception {
        InputStream is = getClass().getResourceAsStream("/" + name);
        assertNotNull("Test vault file must be on classpath: " + name, is);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) {
            baos.write(buf, 0, n);
        }
        is.close();
        return new JSONObject(baos.toString(StandardCharsets.UTF_8.name()));
    }
}
