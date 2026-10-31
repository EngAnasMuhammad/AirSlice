package com.anasdev.fluxlink.data

import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.channels.Channels
import javax.inject.Inject
import javax.inject.Singleton

data class ByteRangeSpec(
    val startByte: Long,
    val endByte: Long,
    val contentLength: Long,
    val fileLength: Long,
)

@Singleton
class VideoStreamDataSource @Inject constructor(
    private val serverDispatchers: ServerDispatchers,
    private val directBufferPool: DirectBufferPool
) {

    /**
     * Parses the HTTP Range header (e.g., "bytes=2000000000-" or "bytes=0-1024")
     * against the specified file's total size.
     */
    fun parseRangeHeader(file: File, rangeHeader: String?): ByteRangeSpec {
        val fileLength = file.length()
        if (rangeHeader.isNullOrBlank() || !rangeHeader.startsWith("bytes=")) {
            return ByteRangeSpec(
                startByte = 0L,
                endByte = (fileLength - 1L).coerceAtLeast(0L),
                contentLength = fileLength,
                fileLength = fileLength,
            )
        }

        val rangeValue = rangeHeader.substringAfter("bytes=").trim()
        val parts = rangeValue.split("-")

        var startByte = parts.getOrNull(0)?.toLongOrNull() ?: 0L
        var endByte = parts.getOrNull(1)?.toLongOrNull() ?: (fileLength - 1L)

        if (startByte < 0L) startByte = 0L
        if (endByte >= fileLength) endByte = fileLength - 1L
        if ((startByte > endByte) && fileLength > 0) startByte = endByte

        val contentLength = if (fileLength > 0) (endByte - startByte) + 1L else 0L

        return ByteRangeSpec(
            startByte = startByte,
            endByte = endByte,
            contentLength = contentLength,
            fileLength = fileLength,
        )
    }

    /**
     * Seeks directly to requested start byte offset in the file using [FileInputStream.channel]
     * and streams requested byte content length directly into [outputStream]
     * using a Zero-GC 512 KB Direct Memory Buffer borrowed from [DirectBufferPool] on [serverDispatchers.heavyTransferDispatcher].
     */
    suspend fun streamByteRange(
        file: File,
        rangeSpec: ByteRangeSpec,
        outputStream: OutputStream,
    ) = withContext(serverDispatchers.heavyTransferDispatcher) {
        if (!file.exists() || !file.canRead() || rangeSpec.contentLength <= 0L) return@withContext

        try {
            FileInputStream(file).use { fis ->
                val channel = fis.channel
                channel.position(rangeSpec.startByte)

                val outChannel = Channels.newChannel(outputStream)

                directBufferPool.useBuffer { buffer ->
                    var remainingBytesToRead = rangeSpec.contentLength

                    while (remainingBytesToRead > 0L) {
                        buffer.clear()
                        if (remainingBytesToRead < buffer.capacity()) {
                            buffer.limit(remainingBytesToRead.toInt())
                        }

                        val bytesRead = channel.read(buffer)
                        if (bytesRead <= 0) break

                        buffer.flip()
                        outChannel.write(buffer)
                        remainingBytesToRead -= bytesRead
                    }
                    outputStream.flush()
                }
            }
        } catch (e: IOException) {
            // Gracefully handle client disconnects (tab closed / user scrubbed video)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
