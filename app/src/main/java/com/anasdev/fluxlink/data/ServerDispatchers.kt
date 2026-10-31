package com.anasdev.fluxlink.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerDispatchers @Inject constructor() {
    // 8 threads dedicated for instant folder listings, server info, and metadata
    val fastUiDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(8)

    // 6 threads dedicated for high-speed 22+ MB/s file downloads, video streaming, and uploads
    val heavyTransferDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(6)

    // 8 threads dedicated for high-throughput thumbnail generation
    val thumbnailDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(8)
}
