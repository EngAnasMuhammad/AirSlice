package com.anasdev.fluxlink.data

import kotlinx.coroutines.channels.Channel
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DirectBufferPool @Inject constructor() {
    private val bufferCapacity = 512 * 1024 // 512 KB Direct Memory Buffer
    private val poolChannel = Channel<ByteBuffer>(capacity = 4)
    private val arrayPoolChannel = Channel<ByteArray>(capacity = 4)

    /**
     * Warm-up function: Pre-allocates direct buffers into the pool on server startup.
     */
    fun warmup(count: Int = 2) {
        repeat(count) {
            val buffer = ByteBuffer.allocateDirect(bufferCapacity)
            poolChannel.trySend(buffer)
            val array = ByteArray(bufferCapacity)
            arrayPoolChannel.trySend(array)
        }
    }

    /**
     * Borrows a 512 KB Direct Memory ByteBuffer from pool or allocates a fallback.
     */
    fun acquireBuffer(): ByteBuffer {
        val pooled = poolChannel.tryReceive().getOrNull()
        if (pooled != null) {
            pooled.clear()
            return pooled
        }
        return ByteBuffer.allocateDirect(bufferCapacity)
    }

    /**
     * Returns the 512 KB Direct Memory ByteBuffer back to the pool for reuse.
     */
    fun releaseBuffer(buffer: ByteBuffer) {
        if (buffer.capacity() == bufferCapacity) {
            buffer.clear()
            poolChannel.trySend(buffer)
        }
    }

    /**
     * Executes the block with a borrowed 512 KB Direct Memory ByteBuffer and returns it in a finally block.
     */
    inline fun <R> useBuffer(block: (ByteBuffer) -> R): R {
        val buffer = acquireBuffer()
        return try {
            block(buffer)
        } finally {
            releaseBuffer(buffer)
        }
    }

    /**
     * Borrows a 512 KB ByteArray from pool for zero-allocation stream writing.
     */
    fun acquireByteArray(): ByteArray {
        val pooled = arrayPoolChannel.tryReceive().getOrNull()
        if (pooled != null) return pooled
        return ByteArray(bufferCapacity)
    }

    /**
     * Returns a 512 KB ByteArray back to the pool.
     */
    fun releaseByteArray(array: ByteArray) {
        if (array.size == bufferCapacity) {
            arrayPoolChannel.trySend(array)
        }
    }

    /**
     * Executes the block with a borrowed 512 KB ByteArray and returns it in a finally block.
     */
    inline fun <R> useByteArray(block: (ByteArray) -> R): R {
        val array = acquireByteArray()
        return try {
            block(array)
        } finally {
            releaseByteArray(array)
        }
    }
}
