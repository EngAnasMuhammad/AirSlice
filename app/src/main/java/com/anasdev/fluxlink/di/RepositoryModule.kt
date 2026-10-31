package com.anasdev.fluxlink.di

import com.anasdev.fluxlink.data.repository.NetworkRepositoryImpl
import com.anasdev.fluxlink.data.repository.ServerRepositoryImpl
import com.anasdev.fluxlink.data.repository.SessionRepositoryImpl
import com.anasdev.fluxlink.domain.repository.NetworkRepository
import com.anasdev.fluxlink.domain.repository.ServerRepository
import com.anasdev.fluxlink.domain.repository.SessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindServerRepository(
        impl: ServerRepositoryImpl
    ): ServerRepository

    @Binds
    @Singleton
    abstract fun bindNetworkRepository(
        impl: NetworkRepositoryImpl
    ): NetworkRepository

    @Binds
    @Singleton
    abstract fun bindSessionRepository(
        impl: SessionRepositoryImpl
    ): SessionRepository
}
