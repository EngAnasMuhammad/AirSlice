package com.anasdev.fluxlink.domain.repository

import com.anasdev.fluxlink.domain.model.NetworkState
import kotlinx.coroutines.flow.Flow

interface NetworkRepository {
    fun getNetworkState(overridePort: Int? = null): NetworkState
    fun observeNetworkState(): Flow<NetworkState>
    fun refreshNetworkState()
}
