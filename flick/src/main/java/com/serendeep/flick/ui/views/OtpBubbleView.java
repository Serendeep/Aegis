package com.serendeep.flick.ui.views;

import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.serendeep.flick.R;
import com.serendeep.flick.otp.OtpException;
import com.serendeep.flick.otp.TotpInfo;
import com.serendeep.flick.vault.VaultEntry;

public class OtpBubbleView {

    public interface AuthCallback {
        void onAuthRequired();
        void onCodeCopied();
    }

    private final Context _context;
    private final WindowManager _windowManager;
    private final Handler _handler = new Handler(Looper.getMainLooper());
    private final AuthCallback _authCallback;

    private View _collapsedView;
    private View _expandedView;
    private boolean _expanded = false;
    private boolean _showing = false;
    private boolean _isUnlocked = false;
    private boolean _authPending = false;

    private VaultEntry _currentEntry;
    private Runnable _refreshRunnable;
    private Runnable _autoHideRunnable;
    private ValueAnimator _countdownAnimator;

    private static final long AUTO_HIDE_MS = 30_000;
    private static final long COPY_COLLAPSE_MS = 2_000;
    private static final int FLING_OFF_THRESHOLD_DP = 20;

    private int _initialX, _initialY;
    private float _initialTouchX, _initialTouchY;
    private WindowManager.LayoutParams _collapsedParams;
    private int _screenWidth;

    public OtpBubbleView(Context context, WindowManager windowManager, AuthCallback authCallback) {
        _context = new ContextThemeWrapper(context, R.style.Theme_Flick);
        _windowManager = windowManager;
        _authCallback = authCallback;

        DisplayMetrics dm = _context.getResources().getDisplayMetrics();
        _screenWidth = dm.widthPixels;

        initViews();
    }

    private void initViews() {
        LayoutInflater inflater = LayoutInflater.from(_context);
        _collapsedView = inflater.inflate(R.layout.view_otp_bubble_collapsed, null);
        _expandedView = inflater.inflate(R.layout.view_otp_bubble_expanded, null);

        _collapsedView.setOnClickListener(v -> expand());
        _collapsedView.setOnTouchListener(this::onBubbleTouched);

        _expandedView.findViewById(R.id.btn_copy).setOnClickListener(v -> copyAndCollapse());
        _expandedView.findViewById(R.id.btn_close).setOnClickListener(v -> hide());
        _expandedView.setOnClickListener(v -> collapse());
    }

    public void show(VaultEntry entry) {
        _currentEntry = entry;

        if (!_showing) {
            _collapsedParams = createOverlayParams(56, Gravity.TOP | Gravity.END);
            _collapsedParams.x = 16;
            _collapsedParams.y = 200;
            _windowManager.addView(_collapsedView, _collapsedParams);
            _collapsedView.setVisibility(View.VISIBLE);
            _showing = true;
        }

        _expanded = false;
        _expandedView.setVisibility(View.GONE);
        _collapsedView.setVisibility(View.VISIBLE);
        _collapsedView.setAlpha(_isUnlocked ? 1.0f : 0.5f);

        scheduleAutoHide();
    }

    public void hide() {
        if (!_showing) return;
        cancelCodeRefresh();
        cancelAutoHide();
        cancelCountdownAnimation();

        if (_collapsedView.getParent() != null) _windowManager.removeView(_collapsedView);
        if (_expandedView.getParent() != null) _windowManager.removeView(_expandedView);

        _showing = false;
        _expanded = false;
        _currentEntry = null;
    }

    public boolean isShowing() {
        return _showing;
    }

    public void setUnlocked(boolean unlocked) {
        _isUnlocked = unlocked;
        if (!unlocked) {
            _authPending = false;
            if (_expanded) {
                collapse();
            }
            if (_showing) {
                _collapsedView.setAlpha(0.5f);
            }
        } else if (_showing) {
            _collapsedView.setAlpha(1.0f);
        }
    }

    public void onAuthSucceeded() {
        _authPending = false;
        _isUnlocked = true;
        if (_showing) {
            _collapsedView.setAlpha(1.0f);
            expand();
        }
    }

    public void resetAuthPending() {
        _authPending = false;
    }

    private void expand() {
        if (_expanded || _currentEntry == null) return;

        if (!_isUnlocked) {
            if (!_authPending) {
                _authPending = true;
                _authCallback.onAuthRequired();
            }
            return;
        }

        _expanded = true;
        refreshCode();

        _collapsedView.setVisibility(View.GONE);
        if (_expandedView.getParent() == null) {
            WindowManager.LayoutParams params = createOverlayParams(
                    WindowManager.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
            _windowManager.addView(_expandedView, params);
        }
        _expandedView.setVisibility(View.VISIBLE);

        scheduleCodeRefresh();
        scheduleAutoHide();
    }

    private void collapse() {
        if (!_expanded) return;
        _expanded = false;

        _expandedView.setVisibility(View.GONE);
        _collapsedView.setVisibility(View.VISIBLE);
        cancelCodeRefresh();
        cancelCountdownAnimation();
    }

    private void refreshCode() {
        if (_currentEntry == null) return;
        try {
            String code = _currentEntry.getOtpInfo().getOtp();
            String grouped = groupCode(code);

            TextView issuerView = _expandedView.findViewById(R.id.txt_issuer);
            TextView codeView = _expandedView.findViewById(R.id.txt_otp_code);
            issuerView.setText(_currentEntry.getIssuer());
            codeView.setText(grouped);

            if (_currentEntry.getOtpInfo() instanceof TotpInfo) {
                TotpInfo totp = (TotpInfo) _currentEntry.getOtpInfo();
                long millis = totp.getMillisTillNextRotation();
                long periodMs = totp.getPeriod() * 1000L;
                int startProgress = (int) (millis * 10000 / periodMs);

                LinearProgressIndicator pb = _expandedView.findViewById(R.id.progress_countdown);
                pb.setProgressCompat(startProgress, false);
                startCountdownAnimation(pb, startProgress, millis);
            }
        } catch (OtpException ignored) {
        }
    }

    private void startCountdownAnimation(LinearProgressIndicator pb, int fromProgress, long durationMs) {
        cancelCountdownAnimation();
        _countdownAnimator = ValueAnimator.ofInt(fromProgress, 0);
        _countdownAnimator.setDuration(durationMs);
        _countdownAnimator.setInterpolator(new LinearInterpolator());
        _countdownAnimator.addUpdateListener(a ->
                pb.setProgressCompat((int) a.getAnimatedValue(), false));
        _countdownAnimator.start();
    }

    private void cancelCountdownAnimation() {
        if (_countdownAnimator != null) {
            _countdownAnimator.cancel();
            _countdownAnimator = null;
        }
    }

    private void copyAndCollapse() {
        if (_currentEntry == null) return;
        try {
            String code = _currentEntry.getOtpInfo().getOtp();
            ClipboardManager cm = (ClipboardManager) _context.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("OTP", code));

            _authCallback.onCodeCopied();

            TextView btn = _expandedView.findViewById(R.id.btn_copy);
            CharSequence original = btn.getText();
            btn.setText("\u2713");
            _handler.postDelayed(() -> {
                btn.setText(original);
                collapse();
            }, COPY_COLLAPSE_MS);

            _handler.postDelayed(() -> {
                cm.setPrimaryClip(ClipData.newPlainText("", ""));
            }, 30_000);
        } catch (OtpException ignored) {
        }
    }

    private void scheduleCodeRefresh() {
        if (_currentEntry == null) return;
        if (!(_currentEntry.getOtpInfo() instanceof TotpInfo)) return;
        TotpInfo totp = (TotpInfo) _currentEntry.getOtpInfo();

        _refreshRunnable = () -> {
            refreshCode();
            scheduleCodeRefresh();
        };
        _handler.postDelayed(_refreshRunnable, totp.getMillisTillNextRotation());
    }

    private void cancelCodeRefresh() {
        if (_refreshRunnable != null) _handler.removeCallbacks(_refreshRunnable);
    }

    private void scheduleAutoHide() {
        cancelAutoHide();
        _autoHideRunnable = this::hide;
        _handler.postDelayed(_autoHideRunnable, AUTO_HIDE_MS);
    }

    private void cancelAutoHide() {
        if (_autoHideRunnable != null) _handler.removeCallbacks(_autoHideRunnable);
    }

    private boolean onBubbleTouched(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                _initialX = _collapsedParams.x;
                _initialY = _collapsedParams.y;
                _initialTouchX = event.getRawX();
                _initialTouchY = event.getRawY();
                return true;
            case MotionEvent.ACTION_MOVE:
                _collapsedParams.x = _initialX + (int) (event.getRawX() - _initialTouchX);
                _collapsedParams.y = _initialY + (int) (event.getRawY() - _initialTouchY);
                _windowManager.updateViewLayout(_collapsedView, _collapsedParams);
                return true;
            case MotionEvent.ACTION_UP:
                float dx = event.getRawX() - _initialTouchX;
                float dy = event.getRawY() - _initialTouchY;
                int thresholdPx = dpToPx(FLING_OFF_THRESHOLD_DP);
                float finalX = event.getRawX();
                if (finalX < -thresholdPx || finalX > _screenWidth + thresholdPx) {
                    hide();
                } else if (Math.abs(dx) < 10 && Math.abs(dy) < 10) {
                    v.performClick();
                }
                return true;
        }
        return false;
    }

    private WindowManager.LayoutParams createOverlayParams(int size, int gravity) {
        int width = size == WindowManager.LayoutParams.WRAP_CONTENT
                ? WindowManager.LayoutParams.WRAP_CONTENT
                : dpToPx(size);
        int height = size == WindowManager.LayoutParams.WRAP_CONTENT
                ? WindowManager.LayoutParams.WRAP_CONTENT
                : dpToPx(size);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width, height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_SECURE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = gravity;
        return params;
    }

    private int dpToPx(int dp) {
        return (int) (dp * _context.getResources().getDisplayMetrics().density);
    }

    private String groupCode(String code) {
        if (code.length() <= 3) return code;
        int mid = code.length() / 2;
        return code.substring(0, mid) + " " + code.substring(mid);
    }
}
