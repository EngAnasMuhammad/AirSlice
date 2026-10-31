package com.anasdev.fluxlink.data.repository

import com.anasdev.fluxlink.data.NetworkInfoProvider
import com.anasdev.fluxlink.domain.model.NetworkState
import com.anasdev.fluxlink.domain.repository.NetworkRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkRepositoryImpl @Inject constructor(
    private val networkInfoProvider: NetworkInfoProvider
) : NetworkRepository {

    override fun getNetworkState(overridePort: Int?): NetworkState {
        return networkInfoProvider.getNetworkState(overridePort)
    }

    override fun observeNetworkState(): Flow<NetworkState> {
        return networkInfoProvider.observeNetworkState()
    }

    override fun refreshNetworkState() {
        // No-op or trigger network state emission
    }
}
