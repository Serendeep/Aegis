package com.serendeep.flick;

import android.content.Context;

import com.serendeep.flick.vault.UrlMappingStore;
import com.serendeep.flick.vault.VaultAccessManager;
import com.serendeep.flick.vault.VaultHolder;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public class FlickModule {
    @Provides
    @Singleton
    public VaultHolder provideVaultHolder() {
        return new VaultHolder();
    }

    @Provides
    @Singleton
    public VaultAccessManager provideVaultAccessManager(@ApplicationContext Context context) {
        return new VaultAccessManager(context);
    }

    @Provides
    @Singleton
    public UrlMappingStore provideUrlMappingStore(@ApplicationContext Context context) {
        return new UrlMappingStore(context);
    }
}
