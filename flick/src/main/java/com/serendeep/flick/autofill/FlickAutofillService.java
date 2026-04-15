package com.serendeep.flick.autofill;

import android.os.CancellationSignal;
import android.service.autofill.AutofillService;
import android.service.autofill.Dataset;
import android.service.autofill.FillCallback;
import android.service.autofill.FillRequest;
import android.service.autofill.FillResponse;
import android.service.autofill.SaveCallback;
import android.service.autofill.SaveRequest;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.widget.RemoteViews;

import com.serendeep.flick.R;
import com.serendeep.flick.otp.EntryMatcher;
import com.serendeep.flick.otp.OtpException;
import com.serendeep.flick.ui.QuickAuthActivity;
import com.serendeep.flick.vault.UrlMappingStore;
import com.serendeep.flick.vault.VaultCacheManager;
import com.serendeep.flick.vault.VaultEntry;
import com.serendeep.flick.vault.VaultHolder;

import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

@AndroidEntryPoint
public class FlickAutofillService extends AutofillService {

    private static final int MAX_DATASETS = 3;

    @Inject VaultHolder _vaultHolder;
    @Inject VaultCacheManager _vaultCache;
    @Inject UrlMappingStore _urlMappingStore;

    @Override
    public void onFillRequest(FillRequest request, CancellationSignal cancellationSignal,
                              FillCallback callback) {
        List<android.service.autofill.FillContext> contexts = request.getFillContexts();
        android.app.assist.AssistStructure structure =
                contexts.get(contexts.size() - 1).getStructure();

        StructureParser.ParsedStructure parsed = StructureParser.parse(structure);

        if (parsed.getAutofillIds().isEmpty()) {
            callback.onSuccess(null);
            return;
        }

        // Skip our own package
        if (getPackageName().equals(parsed.getPackageName())) {
            callback.onSuccess(null);
            return;
        }

        // Try loading vault from cache if not loaded
        if (!_vaultHolder.isLoaded()) {
            List<VaultEntry> cached = _vaultCache.getCachedEntries();
            if (cached != null) {
                _vaultHolder.setEntries(cached);
                _vaultHolder.unlock();
            }
        }

        // If still not loaded, request authentication
        if (!_vaultHolder.isLoaded() || _vaultHolder.getEntries().isEmpty()) {
            try {
                FillResponse authResponse = new FillResponse.Builder()
                        .setAuthentication(
                                parsed.getAutofillIds().toArray(new AutofillId[0]),
                                QuickAuthActivity.createIntentSender(this),
                                buildAuthPresentation())
                        .build();
                callback.onSuccess(authResponse);
            } catch (Exception e) {
                callback.onSuccess(null);
            }
            return;
        }

        FillResponse response = buildFillResponse(
                parsed, _vaultHolder, _urlMappingStore, getPackageName());
        callback.onSuccess(response);
    }

    @Override
    public void onSaveRequest(SaveRequest request, SaveCallback callback) {
        // No-op — Flick doesn't save OTP entries from other apps
        callback.onSuccess();
    }

    static FillResponse buildFillResponse(StructureParser.ParsedStructure parsed,
                                           VaultHolder vaultHolder,
                                           UrlMappingStore urlMappingStore,
                                           String ownPackageName) {
        if (!vaultHolder.isLoaded() || vaultHolder.getEntries().isEmpty()) {
            return null;
        }

        if (parsed.getAutofillIds().isEmpty()) {
            return null;
        }

        EntryMatcher matcher = new EntryMatcher(
                vaultHolder.getEntries(), urlMappingStore.getAllMappings());

        List<VaultEntry> matches;
        if (parsed.getWebDomain() != null) {
            matches = matcher.matchByDomain(parsed.getWebDomain());
            if (matches.isEmpty()) {
                matches = matcher.matchByPackage(parsed.getPackageName());
            }
        } else {
            matches = matcher.matchByPackage(parsed.getPackageName());
        }

        if (matches.isEmpty()) {
            return null;
        }

        FillResponse.Builder responseBuilder = new FillResponse.Builder();
        int count = Math.min(matches.size(), MAX_DATASETS);

        for (int i = 0; i < count; i++) {
            VaultEntry entry = matches.get(i);
            Dataset dataset = buildDataset(entry, parsed.getAutofillIds(), ownPackageName);
            if (dataset != null) {
                responseBuilder.addDataset(dataset);
            }
        }

        try {
            return responseBuilder.build();
        } catch (Exception e) {
            // FillResponse.build() throws if no datasets were added
            return null;
        }
    }

    private static Dataset buildDataset(VaultEntry entry, List<AutofillId> autofillIds,
                                         String packageName) {
        String code;
        try {
            code = entry.getOtpInfo().getOtp();
        } catch (OtpException e) {
            return null;
        }

        String displayText = entry.getIssuer().isEmpty() ? entry.getName() : entry.getIssuer();
        RemoteViews presentation = new RemoteViews(packageName, R.layout.autofill_dataset);
        presentation.setTextViewText(R.id.txt_issuer, displayText);
        presentation.setTextViewText(R.id.txt_code, formatCode(code));

        Dataset.Builder builder = new Dataset.Builder(presentation);
        for (AutofillId id : autofillIds) {
            builder.setValue(id, AutofillValue.forText(code));
        }

        try {
            return builder.build();
        } catch (Exception e) {
            return null;
        }
    }

    private RemoteViews buildAuthPresentation() {
        RemoteViews presentation = new RemoteViews(getPackageName(), R.layout.autofill_dataset);
        presentation.setTextViewText(R.id.txt_issuer, getString(R.string.autofill_auth_label));
        presentation.setTextViewText(R.id.txt_code, getString(R.string.autofill_auth_tap));
        return presentation;
    }

    private static String formatCode(String code) {
        if (code.length() <= 3) return code;
        int mid = code.length() / 2;
        return code.substring(0, mid) + " " + code.substring(mid);
    }
}
