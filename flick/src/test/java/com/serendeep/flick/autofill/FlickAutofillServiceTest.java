package com.serendeep.flick.autofill;

import android.app.assist.AssistStructure;
import android.content.ComponentName;
import android.os.CancellationSignal;
import android.service.autofill.FillCallback;
import android.service.autofill.FillContext;
import android.service.autofill.FillRequest;
import android.service.autofill.FillResponse;
import android.text.InputType;
import android.view.View;
import android.view.autofill.AutofillId;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.serendeep.flick.otp.OtpException;
import com.serendeep.flick.otp.TotpInfo;
import com.serendeep.flick.vault.VaultCacheManager;
import com.serendeep.flick.vault.VaultEntry;
import com.serendeep.flick.vault.VaultHolder;
import com.serendeep.flick.vault.UrlMappingStore;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
public class FlickAutofillServiceTest {

    private VaultHolder _vaultHolder;
    private VaultCacheManager _vaultCache;
    private UrlMappingStore _urlMappingStore;
    private String _testPackageName;

    @Before
    public void setUp() {
        _vaultHolder = new VaultHolder();
        _vaultCache = mock(VaultCacheManager.class);
        _urlMappingStore = mock(UrlMappingStore.class);
        when(_urlMappingStore.getAllMappings()).thenReturn(Collections.emptyMap());
        Context context = ApplicationProvider.getApplicationContext();
        _testPackageName = context.getPackageName();
    }

    @Test
    public void testBuildFillResponseWithLoadedVault() throws Exception {
        List<VaultEntry> entries = Arrays.asList(
                makeEntry("GitHub", "user@github.com")
        );
        _vaultHolder.setEntries(entries);
        _vaultHolder.unlock();

        AssistStructure structure = mockStructure("com.github.android");
        StructureParser.ParsedStructure parsed = StructureParser.parse(structure);

        FillResponse response = FlickAutofillService.buildFillResponse(
                parsed, _vaultHolder, _urlMappingStore, _testPackageName);

        assertNotNull(response);
    }

    @Test
    public void testBuildFillResponseNoMatchesReturnsNull() throws Exception {
        List<VaultEntry> entries = Arrays.asList(
                makeEntry("GitHub", "user@github.com")
        );
        _vaultHolder.setEntries(entries);
        _vaultHolder.unlock();

        AssistStructure structure = mockStructure("com.unknown.app");
        StructureParser.ParsedStructure parsed = StructureParser.parse(structure);

        FillResponse response = FlickAutofillService.buildFillResponse(
                parsed, _vaultHolder, _urlMappingStore, _testPackageName);

        assertNull(response);
    }

    @Test
    public void testBuildFillResponseEmptyVaultReturnsNull() {
        // Vault has no entries
        AssistStructure structure = mockStructure("com.github.android");
        StructureParser.ParsedStructure parsed = StructureParser.parse(structure);

        FillResponse response = FlickAutofillService.buildFillResponse(
                parsed, _vaultHolder, _urlMappingStore, _testPackageName);

        assertNull(response);
    }

    @Test
    public void testBuildFillResponseNoAutofillIdsReturnsNull() throws Exception {
        List<VaultEntry> entries = Arrays.asList(
                makeEntry("GitHub", "user@github.com")
        );
        _vaultHolder.setEntries(entries);
        _vaultHolder.unlock();

        // Empty structure with no input fields
        AssistStructure structure = mock(AssistStructure.class);
        when(structure.getWindowNodeCount()).thenReturn(0);
        when(structure.getActivityComponent())
                .thenReturn(new ComponentName("com.github.android", "com.github.android.MainActivity"));

        StructureParser.ParsedStructure parsed = StructureParser.parse(structure);

        FillResponse response = FlickAutofillService.buildFillResponse(
                parsed, _vaultHolder, _urlMappingStore, _testPackageName);

        assertNull(response);
    }

    private AssistStructure mockStructure(String packageName) {
        AssistStructure structure = mock(AssistStructure.class);
        when(structure.getActivityComponent())
                .thenReturn(new ComponentName(packageName, packageName + ".MainActivity"));

        AssistStructure.WindowNode windowNode = mock(AssistStructure.WindowNode.class);
        when(structure.getWindowNodeCount()).thenReturn(1);
        when(structure.getWindowNodeAt(0)).thenReturn(windowNode);

        AssistStructure.ViewNode viewNode = mock(AssistStructure.ViewNode.class);
        when(windowNode.getRootViewNode()).thenReturn(viewNode);
        when(viewNode.getInputType()).thenReturn(InputType.TYPE_CLASS_TEXT);
        when(viewNode.getAutofillId()).thenReturn(mock(AutofillId.class));
        when(viewNode.getClassName()).thenReturn("android.widget.EditText");
        when(viewNode.getChildCount()).thenReturn(0);
        when(viewNode.getVisibility()).thenReturn(View.VISIBLE);

        return structure;
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
