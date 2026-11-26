package com.anasdev.fluxlink.di

import android.content.Context
import com.anasdev.fluxlink.data.ConnectedSessionManager
import com.anasdev.fluxlink.data.HotspotClientTracker
import com.anasdev.fluxlink.data.NetworkInfoProvider
import com.anasdev.fluxlink.data.PortManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun providePortManager(): PortManager {
        return PortManager()
    }

    @Provides
    @Singleton
    fun provideHotspotClientTracker(): HotspotClientTracker {
        return HotspotClientTracker()
    }

    @Provides
    @Singleton
    fun provideNetworkInfoProvider(
        @ApplicationContext context: Context,
        portManager: PortManager,
        connectedSessionManager: ConnectedSessionManager,
        hotspotClientTracker: HotspotClientTracker
    ): NetworkInfoProvider {
        return NetworkInfoProvider(context, portManager, connectedSessionManager, hotspotClientTracker)
    }
}
