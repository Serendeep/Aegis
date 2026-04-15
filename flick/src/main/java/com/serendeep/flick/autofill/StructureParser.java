package com.serendeep.flick.autofill;

import android.app.assist.AssistStructure;
import android.text.InputType;
import android.view.View;
import android.view.autofill.AutofillId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class StructureParser {

    private static final String[] OTP_HINTS = {
            "otp", "code", "token", "2fa", "verification", "one-time", "onetime",
            "passcode", "pin", "mfa"
    };

    private static final String[] OTP_ID_KEYWORDS = {
            "otp", "code", "token", "verification", "2fa", "passcode", "pin"
    };

    private StructureParser() {
    }

    public static ParsedStructure parse(AssistStructure structure) {
        String packageName = structure.getActivityComponent().getPackageName();
        String webDomain = null;
        List<AutofillId> otpFields = new ArrayList<>();
        List<AutofillId> allTextFields = new ArrayList<>();

        for (int i = 0; i < structure.getWindowNodeCount(); i++) {
            AssistStructure.WindowNode windowNode = structure.getWindowNodeAt(i);
            AssistStructure.ViewNode rootNode = windowNode.getRootViewNode();
            if (rootNode != null) {
                webDomain = traverseNode(rootNode, otpFields, allTextFields, webDomain);
            }
        }

        List<AutofillId> resultIds = otpFields.isEmpty() ? allTextFields : otpFields;

        return new ParsedStructure(
                packageName,
                webDomain,
                Collections.unmodifiableList(resultIds)
        );
    }

    private static String traverseNode(AssistStructure.ViewNode node,
                                        List<AutofillId> otpFields,
                                        List<AutofillId> allTextFields,
                                        String webDomain) {
        // Collect web domain
        String nodeDomain = node.getWebDomain();
        if (nodeDomain != null && !nodeDomain.isEmpty()) {
            webDomain = nodeDomain;
        }

        AutofillId autofillId = node.getAutofillId();
        if (autofillId != null && isInputField(node)) {
            if (isOtpField(node)) {
                otpFields.add(autofillId);
            } else if (isTextField(node)) {
                allTextFields.add(autofillId);
            }
        }

        // Recurse into children
        for (int i = 0; i < node.getChildCount(); i++) {
            AssistStructure.ViewNode child = node.getChildAt(i);
            if (child != null) {
                webDomain = traverseNode(child, otpFields, allTextFields, webDomain);
            }
        }

        return webDomain;
    }

    private static boolean isInputField(AssistStructure.ViewNode node) {
        if (node.getVisibility() != View.VISIBLE) {
            return false;
        }

        String className = node.getClassName();
        if (className == null) {
            return false;
        }

        int inputType = node.getInputType();
        return className.contains("EditText") || inputType != 0;
    }

    private static boolean isTextField(AssistStructure.ViewNode node) {
        int inputType = node.getInputType();
        int textClass = inputType & InputType.TYPE_MASK_CLASS;
        return textClass == InputType.TYPE_CLASS_TEXT
                || textClass == InputType.TYPE_CLASS_NUMBER;
    }

    private static boolean isOtpField(AssistStructure.ViewNode node) {
        // Check autofill hints
        String[] hints = node.getAutofillHints();
        if (hints != null) {
            for (String hint : hints) {
                String lower = hint.toLowerCase(Locale.ROOT);
                for (String otpHint : OTP_HINTS) {
                    if (lower.contains(otpHint)) {
                        return true;
                    }
                }
            }
        }

        // Check view ID entry
        String idEntry = node.getIdEntry();
        if (idEntry != null) {
            String lower = idEntry.toLowerCase(Locale.ROOT);
            for (String keyword : OTP_ID_KEYWORDS) {
                if (lower.contains(keyword)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static class ParsedStructure {
        private final String _packageName;
        private final String _webDomain;
        private final List<AutofillId> _autofillIds;

        ParsedStructure(String packageName, String webDomain, List<AutofillId> autofillIds) {
            _packageName = packageName;
            _webDomain = webDomain;
            _autofillIds = autofillIds;
        }

        public String getPackageName() {
            return _packageName;
        }

        public String getWebDomain() {
            return _webDomain;
        }

        public List<AutofillId> getAutofillIds() {
            return _autofillIds;
        }
    }
}
