package com.serendeep.flick.otp;

import com.serendeep.flick.vault.VaultEntry;
import com.serendeep.flick.vault.VaultParseException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class EntryMatcherTest {
    private EntryMatcher _matcher;

    @Before
    public void setUp() throws Exception {
        List<VaultEntry> entries = Arrays.asList(
            makeEntry("GitHub", "user@github.com"),
            makeEntry("Google", "myemail@gmail.com"),
            makeEntry("Amazon", "shopper"),
            makeEntry("Discord", "gamer#1234"),
            makeEntry("Twitter", "handle")
        );
        _matcher = new EntryMatcher(entries);
    }

    @Test public void testMatchByPackageName() {
        List<VaultEntry> matches = _matcher.matchByPackage("com.github.android");
        assertEquals(1, matches.size());
        assertEquals("GitHub", matches.get(0).getIssuer());
    }

    @Test public void testMatchByDomain() {
        assertEquals(1, _matcher.matchByDomain("github.com").size());
        assertEquals("GitHub", _matcher.matchByDomain("github.com").get(0).getIssuer());
    }

    @Test public void testMatchByDomainWithSubdomain() {
        assertEquals(1, _matcher.matchByDomain("accounts.google.com").size());
        assertEquals("Google", _matcher.matchByDomain("accounts.google.com").get(0).getIssuer());
    }

    @Test public void testMatchByDomainWithPath() {
        assertEquals(1, _matcher.matchByDomain("https://github.com/login/2fa").size());
    }

    @Test public void testNoMatch() {
        assertTrue(_matcher.matchByPackage("com.unknown.app").isEmpty());
    }

    @Test public void testCaseInsensitive() {
        assertEquals(1, _matcher.matchByDomain("GITHUB.COM").size());
    }

    @Test public void testBuiltInMapping() {
        assertEquals(1, _matcher.matchByPackage("com.amazon.mShop.android").size());
        assertEquals("Amazon", _matcher.matchByPackage("com.amazon.mShop.android").get(0).getIssuer());
    }

    @Test public void testTwitterXMapping() {
        assertEquals(1, _matcher.matchByDomain("x.com").size());
        assertEquals(1, _matcher.matchByDomain("twitter.com").size());
    }

    private VaultEntry makeEntry(String issuer, String name) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("type", "totp");
        obj.put("uuid", java.util.UUID.randomUUID().toString());
        obj.put("name", name);
        obj.put("issuer", issuer);
        obj.put("note", "");
        obj.put("favorite", false);
        JSONObject info = new JSONObject();
        info.put("secret", "JBSWY3DPEHPK3PXP");
        info.put("algo", "SHA1");
        info.put("digits", 6);
        info.put("period", 30);
        obj.put("info", info);
        obj.put("groups", new JSONArray());
        return VaultEntry.fromJson(obj);
    }
}
