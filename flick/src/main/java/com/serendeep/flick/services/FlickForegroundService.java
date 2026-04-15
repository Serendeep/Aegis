package com.serendeep.flick.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import com.serendeep.flick.R;
import com.serendeep.flick.ui.SetupActivity;
import com.serendeep.flick.vault.VaultCacheManager;
import com.serendeep.flick.vault.VaultHolder;

import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

@AndroidEntryPoint
public class FlickForegroundService extends Service {

    private static final String CHANNEL_ID = "flick_foreground";
    private static final int NOTIFICATION_ID = 1;
    private static final long CACHE_REFRESH_INTERVAL_MS = 2 * 60 * 1000;

    @Inject VaultHolder _vaultHolder;
    @Inject VaultCacheManager _vaultCache;

    private final Handler _handler = new Handler(Looper.getMainLooper());
    private Runnable _cacheRefreshRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        startCacheKeepalive();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopCacheKeepalive();
        super.onDestroy();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent tapIntent = new Intent(this, SetupActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, tapIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_aegis_shield)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    private void startCacheKeepalive() {
        _cacheRefreshRunnable = () -> {
            if (_vaultHolder.isLoaded()) {
                _vaultCache.cacheEntries(_vaultHolder.getEntries());
            }
            _handler.postDelayed(_cacheRefreshRunnable, CACHE_REFRESH_INTERVAL_MS);
        };
        _handler.postDelayed(_cacheRefreshRunnable, CACHE_REFRESH_INTERVAL_MS);
    }

    private void stopCacheKeepalive() {
        if (_cacheRefreshRunnable != null) {
            _handler.removeCallbacks(_cacheRefreshRunnable);
        }
    }
}
