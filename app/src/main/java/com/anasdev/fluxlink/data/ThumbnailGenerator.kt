package com.anasdev.fluxlink.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThumbnailGenerator @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    // Dedicated, enforced subfolder for AirSlice thumbnails
    private val thumbDir = File(context.cacheDir, "airslice_thumbs").apply {
        if (!exists()) mkdirs()
    }

    // Reactive StateFlow emitting current thumbnail disk cache size in bytes
    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytesFlow: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    // High-throughput 8-permit Semaphore
    private val thumbnailSemaphore = Semaphore(permits = 8)

    init {
        try {
            thumbDir.mkdirs()
            val legacyCache1 = File(context.cacheDir, "thumbnails")
            if (legacyCache1.exists()) legacyCache1.deleteRecursively()
            val legacyCache2 = File(context.filesDir, "thumbnails")
            if (legacyCache2.exists()) legacyCache2.deleteRecursively()
        } catch (_: Exception) {
            // Non-blocking cleanup
        }

        recalculateCacheSize()
    }

    /**
     * Directly scans [thumbDir] filesystem, calculates total bytes, updates [_cacheSizeBytes].
     * Called on startup, dialog launch, and cache clearing.
     */
    fun recalculateCacheSize(): Long {
        val sizeBytes = thumbDir.walkTopDown().filter { (it.isFile && it.extension == "webp") }.sumOf { it.length() }
        _cacheSizeBytes.value = sizeBytes
        return sizeBytes
    }

    fun getCacheSizeBytes(): Long {
        return recalculateCacheSize()
    }

    /**
     * Clears all cached thumbnail files in [thumbDir] and updates [_cacheSizeBytes].
     */
    suspend fun clearThumbnailCache() = withContext(Dispatchers.IO) {
        val files = thumbDir.listFiles() ?: return@withContext
        for (file in files) {
            try {
                file.delete()
            } catch (_: Exception) {
                // Non-blocking catch
            }
        }
        recalculateCacheSize()
    }

    /**
     * Generates a 256x256 WebP thumbnail file, saves it synchronously & atomically to [thumbDir],
     * and returns the saved [File] instance.
     */
    suspend fun getOrCreateThumbnail(targetFile: File): File? = withContext(Dispatchers.IO) {
        if (!targetFile.exists() || targetFile.isDirectory || targetFile.length() <= 0L) {
            return@withContext null
        }

        if (!thumbDir.exists()) {
            thumbDir.mkdirs()
        }

        // Fast & lightweight cache key
        val cacheKey = "${targetFile.absolutePath}_${targetFile.lastModified()}".hashCode().toUInt().toString(16) + ".webp"
        val cachedFile = File(thumbDir, cacheKey)

        // 1. FAST-PATH: Valid Disk Cache Lookup (< 1ms)
        if (cachedFile.exists()) {
            if (cachedFile.length() > 100L) {
                return@withContext cachedFile
            } else {
                cachedFile.delete()
            }
        }

        // 2. High-throughput concurrency-limited decoding
        thumbnailSemaphore.withPermit {
            if (cachedFile.exists() && cachedFile.length() > 100L) {
                return@withPermit cachedFile
            }

            generateThumbnailInternal(targetFile, cachedFile, cacheKey)
        }
    }

    suspend fun getOrGenerateWebpThumbnail(filePath: String): File? {
        return getOrCreateThumbnail(File(filePath))
    }

    private suspend fun generateThumbnailInternal(
        sourceFile: File,
        cachedFile: File,
        cacheKey: String,
    ): File? {
        val ext = sourceFile.extension.lowercase(Locale.ROOT)
        val isVideo = ext in setOf("mp4", "mkv", "mov", "avi", "3gp", "webm")
        val isImage = ext in setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp", "gif", "dng", "tiff", "tif")

        if (!isVideo && !isImage) {
            return null
        }

        var bitmap: Bitmap? = null
        try {
            bitmap = if (isVideo) {
                generateVideoThumbnail(sourceFile)
            } else {
                generateImageThumbnail(sourceFile)
            }

            if (bitmap == null) {
                return null
            }

            // 3. NonCancellable Atomic Disk Write
            return withContext(NonCancellable) {
                val tempFile = File(thumbDir, "$cacheKey.tmp")

                try {
                    val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    }

                    FileOutputStream(tempFile).use { fos ->
                        bitmap.compress(format, 70, fos)
                        fos.flush()
                    }

                    val tempLen = tempFile.length()

                    if (tempFile.exists() && tempLen > 100L) {
                        if (cachedFile.exists()) {
                            cachedFile.delete()
                        }
                        if (!tempFile.renameTo(cachedFile)) {
                            tempFile.copyTo(cachedFile, overwrite = true)
                        }
                        if (tempFile.exists()) tempFile.delete()

                        val finalLen = cachedFile.length()

                        // O(1) Fast Atomic Addition to Cache Telemetry
                        _cacheSizeBytes.value += finalLen

                        cachedFile
                    } else {
                        if (tempFile.exists()) tempFile.delete()
                        null
                    }
                } catch (_: Throwable) {
                    if (tempFile.exists()) tempFile.delete()
                    if (cachedFile.exists()) cachedFile.delete()
                    null
                }
            }

        } catch (_: Throwable) {
            return null
        } finally {
            bitmap?.recycle()
        }
    }

    private fun generateImageThumbnail(file: File): Bitmap? {
        if (file.length() <= 0L) return null

        // Method 1: Fast & Reliable ByteArray decoding for files <= 20 MB
        if (file.length() in 1L..(20L * 1024 * 1024)) {
            try {
                val bytes = file.readBytes()
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

                if (options.outWidth > 0 && options.outHeight > 0) {
                    options.inSampleSize = calculateInSampleSize(options, 256, 256)
                    options.inJustDecodeBounds = false
                    options.inPreferredConfig = Bitmap.Config.RGB_565
                    val bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    if (bm != null) {
                        return bm
                    }
                }
            } catch (_: Exception) {
                // Fallback
            }
        }

        // Method 2: Try MediaStore / ThumbnailUtils on Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val size = android.util.Size(256, 256)
                val bm = ThumbnailUtils.createImageThumbnail(file, size, null)
                return bm
            } catch (_: Exception) {
                // Fallback
            }
        }

        // Method 3: ImageDecoder on API 28+ for High-Res Camera JPEGs / HEIC
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(file)
                val bm = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                return bm
            } catch (_: Exception) {
                // Fallback
            }
        }

        // Method 4: FileInputStream Stream Fallback
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            FileInputStream(file).use { fis ->
                BitmapFactory.decodeStream(fis, null, options)
            }

            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            options.inSampleSize = calculateInSampleSize(options, 256, 256)
            options.inJustDecodeBounds = false

            FileInputStream(file).use { fis ->
                BitmapFactory.decodeStream(fis, null, options)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun generateVideoThumbnail(file: File): Bitmap? {
        if (file.length() <= 0L) return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val size = android.util.Size(256, 256)
                val bm = ThumbnailUtils.createVideoThumbnail(file, size, null)
                return bm
            } catch (_: Exception) {
                // Fallback
            }
        }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            if (Build.VERSION.SDK_INT >= 27) {
                retriever.getScaledFrameAtTime(-1L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 256, 256)
                    ?: retriever.getFrameAtTime(1000000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } else {
                retriever.getFrameAtTime(1000000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
