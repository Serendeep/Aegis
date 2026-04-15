package com.serendeep.flick.autofill;

import android.app.assist.AssistStructure;
import android.content.ComponentName;
import android.text.InputType;
import android.view.View;
import android.view.autofill.AutofillId;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
public class StructureParserTest {

    @Test
    public void testExtractsPackageName() {
        AssistStructure structure = mockStructure("com.github.android", null, null, 0);
        StructureParser.ParsedStructure result = StructureParser.parse(structure);

        assertEquals("com.github.android", result.getPackageName());
        assertNull(result.getWebDomain());
    }

    @Test
    public void testExtractsWebDomain() {
        AssistStructure structure = mockStructure(
                "com.android.chrome", "github.com", null, 0);
        StructureParser.ParsedStructure result = StructureParser.parse(structure);

        assertEquals("com.android.chrome", result.getPackageName());
        assertEquals("github.com", result.getWebDomain());
    }

    @Test
    public void testDetectsOtpFieldByHint() {
        AssistStructure structure = mockStructure(
                "com.example.app", null, new String[]{"otp"}, InputType.TYPE_CLASS_NUMBER);
        StructureParser.ParsedStructure result = StructureParser.parse(structure);

        assertFalse(result.getAutofillIds().isEmpty());
    }

    @Test
    public void testDetectsOtpFieldByIdEntry() {
        AssistStructure structure = mockStructureWithIdEntry(
                "com.example.app", "otp_input");
        StructureParser.ParsedStructure result = StructureParser.parse(structure);

        assertFalse(result.getAutofillIds().isEmpty());
    }

    @Test
    public void testFallsBackToAllTextFields() {
        // Node with text input type but no OTP hints
        AssistStructure structure = mockStructure(
                "com.example.app", null, null,
                InputType.TYPE_CLASS_TEXT);
        StructureParser.ParsedStructure result = StructureParser.parse(structure);

        // Should fall back to collecting text input fields
        assertFalse(result.getAutofillIds().isEmpty());
    }

    @Test
    public void testEmptyStructureReturnsEmptyIds() {
        AssistStructure structure = mock(AssistStructure.class);
        when(structure.getWindowNodeCount()).thenReturn(0);
        when(structure.getActivityComponent())
                .thenReturn(new ComponentName("com.example.app", "com.example.app.MainActivity"));

        StructureParser.ParsedStructure result = StructureParser.parse(structure);

        assertEquals("com.example.app", result.getPackageName());
        assertTrue(result.getAutofillIds().isEmpty());
    }

    private AssistStructure mockStructure(String packageName, String webDomain,
                                          String[] autofillHints, int inputType) {
        AssistStructure structure = mock(AssistStructure.class);
        when(structure.getActivityComponent())
                .thenReturn(new ComponentName(packageName, packageName + ".MainActivity"));

        AssistStructure.WindowNode windowNode = mock(AssistStructure.WindowNode.class);
        when(structure.getWindowNodeCount()).thenReturn(1);
        when(structure.getWindowNodeAt(0)).thenReturn(windowNode);

        AssistStructure.ViewNode viewNode = mock(AssistStructure.ViewNode.class);
        when(windowNode.getRootViewNode()).thenReturn(viewNode);
        when(viewNode.getWebDomain()).thenReturn(webDomain);
        when(viewNode.getAutofillHints()).thenReturn(autofillHints);
        when(viewNode.getInputType()).thenReturn(inputType);
        when(viewNode.getAutofillId()).thenReturn(mock(AutofillId.class));
        when(viewNode.getClassName()).thenReturn("android.widget.EditText");
        when(viewNode.getChildCount()).thenReturn(0);
        when(viewNode.getVisibility()).thenReturn(View.VISIBLE);

        return structure;
    }

    private AssistStructure mockStructureWithIdEntry(String packageName, String idEntry) {
        AssistStructure structure = mock(AssistStructure.class);
        when(structure.getActivityComponent())
                .thenReturn(new ComponentName(packageName, packageName + ".MainActivity"));

        AssistStructure.WindowNode windowNode = mock(AssistStructure.WindowNode.class);
        when(structure.getWindowNodeCount()).thenReturn(1);
        when(structure.getWindowNodeAt(0)).thenReturn(windowNode);

        AssistStructure.ViewNode viewNode = mock(AssistStructure.ViewNode.class);
        when(windowNode.getRootViewNode()).thenReturn(viewNode);
        when(viewNode.getIdEntry()).thenReturn(idEntry);
        when(viewNode.getInputType()).thenReturn(InputType.TYPE_CLASS_NUMBER);
        when(viewNode.getAutofillId()).thenReturn(mock(AutofillId.class));
        when(viewNode.getClassName()).thenReturn("android.widget.EditText");
        when(viewNode.getChildCount()).thenReturn(0);
        when(viewNode.getVisibility()).thenReturn(View.VISIBLE);

        return structure;
    }
}
