package com.anasdev.fluxlink.data

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.webkit.MimeTypeMap
import com.anasdev.fluxlink.domain.model.DirectoryContent
import com.anasdev.fluxlink.domain.model.FileMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.pow

@Singleton
class FileManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val directBufferPool: DirectBufferPool,
    private val fileAccessTracker: FileAccessTracker,
) {

    fun getDefaultStoragePath(): String {
        return Environment.getExternalStorageDirectory().absolutePath
    }

    fun getDirectoryContent(requestedPath: String? = null): DirectoryContent {
        val rootPath = getDefaultStoragePath()
        val targetPath = if (requestedPath.isNullOrBlank()) rootPath else requestedPath

        val targetDir = File(targetPath)
        if (!targetDir.exists() || !targetDir.isDirectory || !targetDir.canRead()) {
            return DirectoryContent(
                currentPath = rootPath,
                parentPath = null,
                folderName = "Storage",
                totalItems = 0,
                items = emptyList(),
            )
        }

        val canonicalRoot = File(rootPath).canonicalFile
        val canonicalTarget = targetDir.canonicalFile

        val parentPath = if (canonicalTarget.path != canonicalRoot.path && canonicalTarget.parentFile != null) {
            canonicalTarget.parentFile?.absolutePath
        } else {
            null
        }

        val files = targetDir.listFiles() ?: emptyArray()
        val itemsList = mutableListOf<FileMetadata>()

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        for (file in files) {
            val isDir = file.isDirectory
            val name = file.name
            val size = if (isDir) 0L else file.length()
            val formattedSize = if (isDir) "Folder" else formatFileSize(size)
            val date = dateFormat.format(Date(file.lastModified()))
            val ext = file.extension.lowercase(Locale.ROOT)

            val accessedMillis = fileAccessTracker.getLastAccess(file.absolutePath) ?: file.lastModified()
            val accessDateStr = dateFormat.format(Date(accessedMillis))

            val mimeType = getMimeType(file)
            val isImg = isImageExtension(ext) || mimeType.startsWith("image/")
            val isVid = isVideoExtension(ext) || mimeType.startsWith("video/")

            itemsList.add(
                FileMetadata(
                    name = name,
                    path = file.absolutePath,
                    isDirectory = isDir,
                    sizeBytes = size,
                    formattedSize = formattedSize,
                    lastModified = file.lastModified(),
                    formattedDate = date,
                    lastAccessedMillis = accessedMillis,
                    formattedAccessDate = accessDateStr,
                    mimeType = mimeType,
                    isMediaImage = isImg,
                    isMediaVideo = isVid,
                )
            )
        }

        // Default Sort: Directories first, then files alphabetically
        itemsList.sortWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))

        return DirectoryContent(
            currentPath = targetDir.absolutePath,
            parentPath = parentPath,
            folderName = targetDir.name.ifEmpty { "Storage" },
            totalItems = itemsList.size,
            items = itemsList,
        )
    }

    fun formatFileSize(size: Long, addSpace: Boolean = true): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
        val space = if (addSpace) " " else ""
        return DecimalFormat("#,##0.#").format(size / 1024.0.pow(digitGroups.toDouble())) + space + units[digitGroups]
    }

    fun toJson(directoryContent: DirectoryContent): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"currentPath\":\"").append(escapeJson(directoryContent.currentPath)).append("\",")
        sb.append("\"parentPath\":").append(if (directoryContent.parentPath != null) "\"${escapeJson(directoryContent.parentPath)}\"" else "null").append(",")
        sb.append("\"folderName\":\"").append(escapeJson(directoryContent.folderName)).append("\",")
        sb.append("\"totalItems\":").append(directoryContent.totalItems).append(",")
        sb.append("\"items\":[")

        for (i in directoryContent.items.indices) {
            val item = directoryContent.items[i]
            sb.append("{")
            sb.append("\"name\":\"").append(escapeJson(item.name)).append("\",")
            sb.append("\"path\":\"").append(escapeJson(item.path)).append("\",")
            sb.append("\"isDirectory\":").append(item.isDirectory).append(",")
            sb.append("\"sizeBytes\":").append(item.sizeBytes).append(",")
            sb.append("\"formattedSize\":\"").append(escapeJson(item.formattedSize)).append("\",")
            sb.append("\"lastModified\":").append(item.lastModified).append(",")
            sb.append("\"formattedDate\":\"").append(escapeJson(item.formattedDate)).append("\",")
            sb.append("\"lastAccessedMillis\":").append(item.lastAccessedMillis).append(",")
            sb.append("\"formattedAccessDate\":\"").append(escapeJson(item.formattedAccessDate)).append("\",")
            sb.append("\"mimeType\":\"").append(escapeJson(item.mimeType)).append("\",")
            sb.append("\"isMediaImage\":").append(item.isMediaImage).append(",")
            sb.append("\"isMediaVideo\":").append(item.isMediaVideo)
            sb.append("}")
            if (i < directoryContent.items.size - 1) {
                sb.append(",")
            }
        }
        sb.append("]")
        sb.append("}")
        return sb.toString()
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\u000C", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    fun getStorageStatsJson(): String {
        return try {
            val path = Environment.getExternalStorageDirectory().absolutePath
            val stat = StatFs(path)

            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalBytes = totalBlocks * blockSize
            val freeBytes = availableBlocks * blockSize
            val usedBytes = totalBytes - freeBytes

            val usedPercent = if (totalBytes > 0) ((usedBytes.toDouble() / totalBytes) * 100).toInt() else 0

            val formattedFree = formatFileSize(freeBytes, addSpace = false)
            val formattedTotal = formatFileSize(totalBytes, addSpace = false)
            val formattedUsed = formatFileSize(usedBytes, addSpace = false)

            """
            {
                "totalBytes": $totalBytes,
                "freeBytes": $freeBytes,
                "usedBytes": $usedBytes,
                "formattedFree": "$formattedFree",
                "formattedTotal": "$formattedTotal",
                "formattedUsed": "$formattedUsed",
                "usedPercentage": $usedPercent
            }
            """.trimIndent()
        } catch (_: Exception) {
            """
            {
                "totalBytes": 0,
                "freeBytes": 0,
                "usedBytes": 0,
                "formattedFree": "N/A",
                "formattedTotal": "N/A",
                "formattedUsed": "N/A",
                "usedPercentage": 0
            }
            """.trimIndent()
        }
    }

    /**
     * Validates input path strings against the shared storage root to prevent
     * path-traversal attacks (`..`).
     */
    fun validateAndResolvePaths(inputPaths: List<String>): List<File> {
        val rootDir = File(getDefaultStoragePath()).canonicalFile
        val resolvedList = mutableListOf<File>()

        for (p in inputPaths) {
            if (p.isBlank()) continue
            try {
                val target = File(p).canonicalFile
                if (target.exists() && target.canRead() && target.path.startsWith(rootDir.path)) {
                    resolvedList.add(target)
                }
            } catch (_: Exception) {
                // Ignore invalid or malicious paths
            }
        }
        return resolvedList
    }

    /**
     * Streams single or multiple files/folders into network socket as `.zip` archive
     * using `ZipOutputStream` with zero-allocation pooled 512 KB buffers.
     */
    fun streamPathsAsZip(targets: List<File>, outputStream: OutputStream) {
        if (targets.isEmpty()) return

        val bufferedOut = BufferedOutputStream(outputStream, 512 * 1024)
        val zipOut = ZipOutputStream(bufferedOut)

        try {
            if (targets.size == 1 && targets[0].isDirectory) {
                val singleDir = targets[0]
                val rootUri = File(singleDir.absolutePath + "/").toURI()
                compressFolderToZip(rootUri, singleDir, zipOut)
            } else {
                val commonParent = findCommonParentDirectory(targets)
                val rootUri = File(commonParent.absolutePath + "/").toURI()
                for (target in targets) {
                    if (target.isDirectory) {
                        compressFolderToZip(rootUri, target, zipOut)
                    } else {
                        compressSingleFileToZip(rootUri, target, zipOut)
                    }
                }
            }
            zipOut.finish()
            zipOut.flush()
            bufferedOut.flush()
        } catch (_: Exception) {
            // Handle client disconnect gracefully
        }
    }

    fun streamDirectoryAsZip(dirPath: String, outputStream: OutputStream) {
        val targetDir = File(dirPath)
        if (!targetDir.exists() || !targetDir.isDirectory) return
        streamPathsAsZip(listOf(targetDir), outputStream)
    }

    private fun compressFolderToZip(rootUri: java.net.URI, currentDir: File, zipOut: ZipOutputStream) {
        val files = currentDir.listFiles() ?: return

        for (file in files) {
            if (!file.canRead()) continue
            if (file.isDirectory) {
                compressFolderToZip(rootUri, file, zipOut)
            } else {
                compressSingleFileToZip(rootUri, file, zipOut)
            }
        }
    }

    private fun compressSingleFileToZip(rootUri: java.net.URI, file: File, zipOut: ZipOutputStream) {
        if (!file.exists() || !file.canRead() || file.isDirectory) return

        val relativePath = rootUri.relativize(file.toURI()).path
        val zipEntry = ZipEntry(relativePath.ifEmpty { file.name })

        val ext = file.extension.lowercase(Locale.ROOT)
        val isAlreadyCompressed = isUncompressibleExtension(ext)

        if (isAlreadyCompressed) {
            zipOut.setLevel(Deflater.NO_COMPRESSION)
        } else {
            zipOut.setLevel(Deflater.BEST_SPEED)
        }

        try {
            zipOut.putNextEntry(zipEntry)
            FileInputStream(file).use { fis ->
                directBufferPool.useByteArray { tempArray ->
                    var bytesRead: Int
                    while (fis.read(tempArray).also { bytesRead = it } != -1) {
                        zipOut.write(tempArray, 0, bytesRead)
                    }
                }
            }
            zipOut.closeEntry()
        } catch (_: Exception) {
            // Handle client disconnects
        }
    }

    private fun findCommonParentDirectory(files: List<File>): File {
        if (files.isEmpty()) return File(getDefaultStoragePath())
        var parent = files[0].parentFile ?: File(getDefaultStoragePath())
        while (parent.parentFile != null) {
            if (files.all { it.absolutePath.startsWith(parent.absolutePath) }) {
                return parent
            }
            parent = parent.parentFile ?: break
        }
        return File(getDefaultStoragePath())
    }

    private fun isUncompressibleExtension(ext: String): Boolean {
        return ext in setOf(
            "mp4", "mkv", "webm", "avi", "mov", "3gp", "ts",
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "svg", "ico", "tiff", "tif", "dng",
            "mp3", "aac", "m4a", "flac", "ogg",
            "zip", "rar", "7z", "tar", "gz", "apk",
        )
    }

    fun getFileInputStream(filePath: String): Pair<InputStream, Long>? {
        val file = File(filePath)
        if (!file.exists() || !file.isFile || !file.canRead()) return null
        return try {
            Pair(FileInputStream(file), file.length())
        } catch (_: Exception) {
            null
        }
    }

    fun getMimeType(file: File): String {
        if (file.isDirectory) return "folder"
        val extension = file.extension.lowercase(Locale.ROOT)
        val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        if (type != null) return type

        return when (extension) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "3gp" -> "video/3gpp"
            "ts" -> "video/mp2t"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "heic", "heif" -> "image/heic"
            "svg" -> "image/svg+xml"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "pdf" -> "application/pdf"
            "txt", "log" -> "text/plain; charset=UTF-8"
            "md" -> "text/markdown; charset=UTF-8"
            "json" -> "application/json; charset=UTF-8"
            "html", "htm" -> "text/html; charset=UTF-8"
            "css" -> "text/css"
            "js" -> "application/javascript"
            else -> "application/octet-stream"
        }
    }

    private fun isImageExtension(ext: String): Boolean {
        return ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "svg", "ico", "tiff", "tif", "dng")
    }

    private fun isVideoExtension(ext: String): Boolean {
        return ext in setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "ts")
    }
}
