package com.serendeep.flick.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.serendeep.flick.vault.VaultCacheManager;
import com.serendeep.flick.vault.VaultEntry;
import com.serendeep.flick.vault.VaultHolder;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
public class FlickForegroundServiceTest {

    private Context _context;

    @Before
    public void setUp() {
        _context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void testOnStartCommandReturnsStartSticky() {
        ServiceController<FlickForegroundService> controller =
                Robolectric.buildService(FlickForegroundService.class);
        FlickForegroundService service = controller.create().get();

        injectMocks(service);

        int result = service.onStartCommand(new Intent(), 0, 1);
        assertEquals(android.app.Service.START_STICKY, result);
    }

    @Test
    public void testNotificationChannelCreatedWithLowImportance() {
        ServiceController<FlickForegroundService> controller =
                Robolectric.buildService(FlickForegroundService.class);
        FlickForegroundService service = controller.create().get();

        injectMocks(service);
        service.onStartCommand(new Intent(), 0, 1);

        NotificationManager nm = _context.getSystemService(NotificationManager.class);
        NotificationChannel channel = nm.getNotificationChannel("flick_foreground");
        assertNotNull(channel);
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.getImportance());
    }

    @Test
    public void testCacheKeepaliveRefreshesCacheWhenVaultLoaded() throws Exception {
        ServiceController<FlickForegroundService> controller =
                Robolectric.buildService(FlickForegroundService.class);
        FlickForegroundService service = controller.create().get();

        VaultHolder mockHolder = mock(VaultHolder.class);
        VaultCacheManager mockCache = mock(VaultCacheManager.class);
        when(mockHolder.isLoaded()).thenReturn(true);
        when(mockHolder.getEntries()).thenReturn(Collections.emptyList());

        injectField(service, "_vaultHolder", mockHolder);
        injectField(service, "_vaultCache", mockCache);

        service.onStartCommand(new Intent(), 0, 1);

        // Advance the looper past the 2-minute interval
        org.robolectric.shadows.ShadowLooper.runMainLooperToNextTask();
        org.robolectric.shadows.ShadowLooper.idleMainLooper(2 * 60 * 1000 + 100, java.util.concurrent.TimeUnit.MILLISECONDS);

        verify(mockCache, atLeastOnce()).cacheEntries(any());
    }

    @Test
    public void testCacheKeepaliveSkipsWhenVaultNotLoaded() throws Exception {
        ServiceController<FlickForegroundService> controller =
                Robolectric.buildService(FlickForegroundService.class);
        FlickForegroundService service = controller.create().get();

        VaultHolder mockHolder = mock(VaultHolder.class);
        VaultCacheManager mockCache = mock(VaultCacheManager.class);
        when(mockHolder.isLoaded()).thenReturn(false);

        injectField(service, "_vaultHolder", mockHolder);
        injectField(service, "_vaultCache", mockCache);

        service.onStartCommand(new Intent(), 0, 1);

        org.robolectric.shadows.ShadowLooper.idleMainLooper(2 * 60 * 1000 + 100, java.util.concurrent.TimeUnit.MILLISECONDS);

        verify(mockCache, never()).cacheEntries(any());
    }

    private void injectMocks(FlickForegroundService service) {
        try {
            injectField(service, "_vaultHolder", new VaultHolder());
            injectField(service, "_vaultCache", mock(VaultCacheManager.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
