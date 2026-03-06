package com.serendeep.flick.services;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.serendeep.flick.otp.EntryMatcher;
import com.serendeep.flick.ui.QuickAuthActivity;
import com.serendeep.flick.ui.views.OtpBubbleView;
import com.serendeep.flick.vault.VaultEntry;
import com.serendeep.flick.vault.VaultHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

@AndroidEntryPoint
public class OtpAccessibilityService extends AccessibilityService
        implements OtpBubbleView.AuthCallback, VaultHolder.LockStateListener {

    @Inject VaultHolder _vaultHolder;

    private OtpBubbleView _bubbleView;
    private String _lastMatchedPkg = "";
    private long _lastEventTime = 0;
    private static final long DEBOUNCE_MS = 500;
    private static final long SCREEN_OFF_GAP_MS = 3_000;

    private static final Map<String, String> BROWSER_URL_IDS = new HashMap<>();
    static {
        BROWSER_URL_IDS.put("com.android.chrome", "com.android.chrome:id/url_bar");
        BROWSER_URL_IDS.put("org.mozilla.firefox", "org.mozilla.firefox:id/mozac_browser_toolbar_url_view");
        BROWSER_URL_IDS.put("org.mozilla.firefox_beta", "org.mozilla.firefox_beta:id/mozac_browser_toolbar_url_view");
        BROWSER_URL_IDS.put("com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser:id/location_bar_edit_text");
        BROWSER_URL_IDS.put("com.microsoft.emmx", "com.microsoft.emmx:id/url_bar");
        BROWSER_URL_IDS.put("com.brave.browser", "com.brave.browser:id/url_bar");
    }

    private final BroadcastReceiver _screenOffReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                _vaultHolder.lock();
                if (_bubbleView != null) {
                    _bubbleView.hide();
                }
            }
        }
    };

    private final BroadcastReceiver _authCancelledReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (QuickAuthActivity.ACTION_AUTH_CANCELLED.equals(intent.getAction())) {
                if (_bubbleView != null) {
                    _bubbleView.resetAuthPending();
                }
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        _bubbleView = new OtpBubbleView(this, wm, this);
        _bubbleView.setUnlocked(_vaultHolder.isUnlocked());

        _vaultHolder.addLockStateListener(this);

        registerReceiver(_screenOffReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF),
                Context.RECEIVER_NOT_EXPORTED);
        registerReceiver(_authCancelledReceiver, new IntentFilter(QuickAuthActivity.ACTION_AUTH_CANCELLED),
                Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onAuthRequired() {
        Intent intent = new Intent(this, QuickAuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
    }

    @Override
    public void onCodeCopied() {
        _vaultHolder.refreshUnlock();
    }

    @Override
    public void onUnlocked() {
        if (_bubbleView != null) {
            _bubbleView.onAuthSucceeded();
        }
    }

    @Override
    public void onLocked() {
        if (_bubbleView != null) {
            _bubbleView.setUnlocked(false);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!_vaultHolder.isLoaded()) return;

        long now = System.currentTimeMillis();
        if (_lastEventTime > 0 && (now - _lastEventTime) > SCREEN_OFF_GAP_MS
                && _vaultHolder.isUnlocked()) {
            _vaultHolder.lock();
            if (_bubbleView != null && _bubbleView.isShowing()) {
                _bubbleView.hide();
            }
        }
        if (now - _lastEventTime < DEBOUNCE_MS) return;
        _lastEventTime = now;

        CharSequence pkgName = event.getPackageName();
        if (pkgName == null) return;
        String pkg = pkgName.toString();

        if (pkg.equals(getPackageName())) return;

        boolean isBrowser = BROWSER_URL_IDS.containsKey(pkg);

        if (pkg.equals(_lastMatchedPkg) && !isBrowser) return;

        EntryMatcher matcher = new EntryMatcher(_vaultHolder.getEntries());
        List<VaultEntry> matches;

        if (isBrowser) {
            String url = readBrowserUrl(pkg);
            matches = url != null ? matcher.matchByDomain(url) : matcher.matchByPackage(pkg);
        } else {
            matches = matcher.matchByPackage(pkg);
        }

        if (!matches.isEmpty()) {
            VaultEntry best = matches.get(0);
            _lastMatchedPkg = pkg;
            _bubbleView.show(best);
        } else {
            if (_bubbleView != null && _bubbleView.isShowing()) {
                _bubbleView.hide();
            }
            _lastMatchedPkg = "";
        }
    }

    private String readBrowserUrl(String browserPackage) {
        String urlBarId = BROWSER_URL_IDS.get(browserPackage);
        if (urlBarId == null) return null;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;

        try {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(urlBarId);
            if (nodes != null && !nodes.isEmpty()) {
                CharSequence text = nodes.get(0).getText();
                if (text != null) return text.toString();
            }
        } finally {
            root.recycle();
        }
        return null;
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        if (_bubbleView != null) {
            _bubbleView.hide();
        }
        _vaultHolder.removeLockStateListener(this);
        try {
            unregisterReceiver(_screenOffReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            unregisterReceiver(_authCancelledReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        return super.onUnbind(intent);
    }

    @Override
    public void onInterrupt() {
    }
}
