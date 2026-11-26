package com.anasdev.fluxlink.data

import android.content.Context
import android.util.Log
import com.anasdev.fluxlink.domain.model.ApprovalStatus
import com.anasdev.fluxlink.domain.model.ClientActivityType
import com.anasdev.fluxlink.domain.model.ServerStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.nio.channels.Channels
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import com.anasdev.fluxlink.domain.model.AppLanguage
import com.anasdev.fluxlink.domain.model.ClipboardItem
import com.anasdev.fluxlink.domain.model.ClipboardMode
import com.anasdev.fluxlink.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first

@Singleton
class LocalServerEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val fileManager: FileManager,
    private val thumbnailGenerator: ThumbnailGenerator,
    private val securityManager: SecurityManager,
    private val videoStreamDataSource: VideoStreamDataSource,
    private val networkInfoProvider: NetworkInfoProvider,
    private val connectionApprovalManager: ConnectionApprovalManager,
    private val connectedSessionManager: ConnectedSessionManager,
    private val serverDispatchers: ServerDispatchers,
    private val directBufferPool: DirectBufferPool,
    private val fileAccessTracker: FileAccessTracker,
    private val settingsRepository: SettingsRepository,
    private val clipboardSessionStore: ClipboardSessionStore,
    private val clipboardRateLimiter: ClipboardRateLimiter,
    private val hotspotClientTracker: HotspotClientTracker
) {

    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var serverJob: Job? = null

    private val _serverStatus = MutableStateFlow(ServerStatus.STOPPED)
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()

    private val _activePort = MutableStateFlow<Int?>(null)
    val activePort: StateFlow<Int?> = _activePort.asStateFlow()

    private var _serverStartTimeMillis: Long = 0L
    val serverStartTimeMillis: Long
        get() = _serverStartTimeMillis

    fun setReconnectingStatus(isReconnecting: Boolean) {
        if (isReconnecting && _serverStatus.value == ServerStatus.RUNNING) {
            _serverStatus.value = ServerStatus.RECONNECTING
        } else if (!isReconnecting && _serverStatus.value == ServerStatus.RECONNECTING) {
            _serverStatus.value = ServerStatus.RUNNING
        }
    }

    private val activeConnections: MutableSet<Closeable> =
        Collections.synchronizedSet(HashSet())

    val activeClientSockets: MutableSet<Socket> =
        Collections.newSetFromMap(ConcurrentHashMap())

    private val hostUrlChangeHandler = HostUrlChangeHandler(
        securityManager = securityManager,
        connectedSessionManager = connectedSessionManager,
        connectionApprovalManager = connectionApprovalManager,
        hotspotClientTracker = hotspotClientTracker,
        clipboardRateLimiter = clipboardRateLimiter,
        activeClientSockets = activeClientSockets
    )

    @Synchronized
    fun startServer(port: Int): Boolean {
        if (_serverStatus.value == ServerStatus.RUNNING) {
            return true
        }

        _serverStatus.value = ServerStatus.STARTING
        return try {
            // Bind ServerSocket to 0.0.0.0 (wildcard) with 1 MB Receive Buffer
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.receiveBufferSize = 1024 * 1024 // 1 MB Receive Buffer
            socket.bind(InetSocketAddress("0.0.0.0", port))
            serverSocket = socket
            _activePort.value = port

            // Silent Warm-Up: Pre-allocate 512 KB Direct Memory Buffers on background IO
            scope.launch {
                directBufferPool.warmup(2)
            }

            // Generate 4-digit PIN & Session Token for Stage 4 pairing
            securityManager.generateNewPairingSession()

            _serverStartTimeMillis = System.currentTimeMillis()
            _serverStatus.value = ServerStatus.RUNNING

            serverJob = scope.launch {
                listenForClients(socket)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            stopServer("Failed to bind server socket on port $port")
            _serverStatus.value = ServerStatus.ERROR
            false
        }
    }

    private fun listenForClients(socket: ServerSocket) {
        while ((_serverStatus.value == ServerStatus.RUNNING || _serverStatus.value == ServerStatus.RECONNECTING) && !socket.isClosed) {
            try {
                val clientSocket = socket.accept()

                // High-Throughput Socket Tuning: 1 MB Buffers + No Nagle's delay
                clientSocket.tcpNoDelay = true
                clientSocket.sendBufferSize = 1024 * 1024 // 1 MB Send Buffer
                clientSocket.receiveBufferSize = 1024 * 1024 // 1 MB Receive Buffer

                activeClientSockets.add(clientSocket)
                activeConnections.add(clientSocket)

                scope.launch {
                    try {
                        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_MORE_FAVORABLE)
                    } catch (_: Exception) {
                        // Ignore process priority permissions if restricted
                    }
                    handleClientRequest(clientSocket)
                }
            } catch (_: SocketException) {
                break
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun handleClientRequest(clientSocket: Socket) {
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null

        try {
            inputStream = withContext(serverDispatchers.fastUiDispatcher) {
                clientSocket.getInputStream()
            }
            outputStream = withContext(serverDispatchers.fastUiDispatcher) {
                clientSocket.getOutputStream()
            }

            activeConnections.add(inputStream)
            activeConnections.add(outputStream)

            // Read HTTP headers line by line manually from socket stream
            val headersMap = mutableMapOf<String, String>()
            val requestLine = readHeaderLine(inputStream) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val fullUrl = parts[1]

            val rawIp = clientSocket.inetAddress?.hostAddress ?: "127.0.0.1"
            val clientIp = rawIp.trim().removePrefix("::ffff:")
            hotspotClientTracker.recordClient(clientIp)

            Log.e("HTTP_TRACE", "[RAW HTTP INCOMING] $method $fullUrl from $clientIp")

            while (true) {
                val line = readHeaderLine(inputStream) ?: break
                if (line.isEmpty()) break // Empty line separates headers from body
                val colonIdx = line.indexOf(":")
                if (colonIdx > 0) {
                    val key = line.substring(0, colonIdx).trim().lowercase()
                    val value = line.substring(colonIdx + 1).trim()
                    headersMap[key] = value
                }
            }

            val cookieHeader = headersMap["cookie"]
            val acceptEncoding = headersMap["accept-encoding"]
            val userAgent = headersMap["user-agent"] ?: "Web Browser"
            val contentLength = headersMap["content-length"]?.toLongOrNull() ?: 0L

            val rawPath = fullUrl.substringBefore("?")
            val queryString = fullUrl.substringAfter("?", "")
            val queryParamsList = parseQueryParamsList(queryString)
            val queryParams = parseQueryParams(queryString)

            // 1. QR Code Token Auto-Auth or URL PIN Auto-Auth
            val urlToken = queryParams["token"]
            val urlPin = queryParams["pin"]

            if (!urlToken.isNullOrBlank() && securityManager.validateToken(urlToken)) {
                connectedSessionManager.registerSession(securityManager.sessionToken, clientIp, userAgent)
                sendRedirectWithCookie(outputStream, "/", securityManager.sessionToken)
                return
            } else if (!urlPin.isNullOrBlank() && securityManager.validatePin(urlPin)) {
                connectedSessionManager.registerSession(securityManager.sessionToken, clientIp, userAgent)
                sendRedirectWithCookie(outputStream, "/", securityManager.sessionToken)
                return
            }

            // 2. Handle Public Static Assets & Authentication Endpoints
            when (rawPath) {
                "/style.css" -> {
                    val cssBytes = readAssetFile("web/style.css")
                    if (cssBytes != null) {
                        sendCompressibleBytesResponse(outputStream, cssBytes, "text/css", acceptEncoding, cacheControl = "no-cache, no-store, must-revalidate, max-age=0")
                    } else {
                        send404Response(outputStream, "Asset style.css missing")
                    }
                    return
                }

                "/app.js" -> {
                    val jsBytes = readAssetFile("web/app.js")
                    if (jsBytes != null) {
                        sendCompressibleBytesResponse(outputStream, jsBytes, "application/javascript", acceptEncoding, cacheControl = "no-cache, no-store, must-revalidate, max-age=0")
                    } else {
                        send404Response(outputStream, "Asset app.js missing")
                    }
                    return
                }

                // Public Server Info Endpoint (Active Connection Mode, Device Model, PIN Auth & Active App Language)
                "/api/server-info" -> {
                    val netState = networkInfoProvider.getNetworkState()
                    val modeName = netState.connectionMode.name
                    val modelName = android.os.Build.MODEL
                    val reqPin = securityManager.requirePinAuth

                    val configuredLang = runCatching { settingsRepository.getAppLanguage().first() }.getOrDefault(AppLanguage.SYSTEM)
                    val langTag = when (configuredLang) {
                        AppLanguage.ENGLISH -> "en"
                        AppLanguage.ARABIC -> "ar"
                        AppLanguage.SYSTEM -> {
                            val sysLang = java.util.Locale.getDefault().language.lowercase(java.util.Locale.ROOT)
                            if (sysLang == "ar") "ar" else "en"
                        }
                    }
                    val isRtl = (langTag == "ar")

                    val json = "{\"connectionMode\":\"$modeName\",\"deviceName\":\"$modelName\",\"requirePin\":$reqPin,\"authenticated\":true,\"language\":\"$langTag\",\"isRtl\":$isRtl}"
                    sendJsonResponse(outputStream, json, acceptEncoding = acceptEncoding)
                    return
                }

                // 1-Tap Connection Request Approval Endpoint
                "/auth/request-approval" -> {
                    val req = connectionApprovalManager.createApprovalRequest(clientIp, userAgent)
                    val json = "{\"success\":true,\"requestId\":\"${req.requestId}\"}"
                    sendJsonResponse(outputStream, json, acceptEncoding = acceptEncoding)
                    return
                }

                // 1-Tap Connection Poll Status Check
                "/auth/check-approval" -> {
                    if (!securityManager.requirePinAuth) {
                        connectedSessionManager.registerSession(securityManager.sessionToken, clientIp, userAgent)
                        sendJsonResponseWithCookie(outputStream, "{\"status\":\"APPROVED\",\"requirePin\":false}", securityManager.sessionToken, acceptEncoding)
                    } else {
                        val requestId = queryParams["requestId"]
                        if (requestId.isNullOrBlank()) {
                            sendJsonResponse(outputStream, "{\"status\":\"DENIED\"}", acceptEncoding = acceptEncoding)
                        } else {
                            val status = connectionApprovalManager.checkApprovalStatus(requestId)
                            if (status == ApprovalStatus.APPROVED) {
                                connectedSessionManager.registerSession(securityManager.sessionToken, clientIp, userAgent)
                                sendJsonResponseWithCookie(outputStream, "{\"status\":\"APPROVED\"}", securityManager.sessionToken, acceptEncoding)
                            } else {
                                sendJsonResponse(outputStream, "{\"status\":\"${status.name}\"}", acceptEncoding = acceptEncoding)
                            }
                        }
                    }
                    return
                }

                "/api/login" -> {
                    if (method.equals("POST", ignoreCase = true)) {
                        val bodyBytes = readExactBytes(inputStream, contentLength.toInt())
                        val bodyText = String(bodyBytes, Charsets.UTF_8)
                        val bodyParams = parseQueryParams(bodyText)
                        val inputPin = bodyParams["pin"] ?: extractJsonField(bodyText, "pin")

                        if (!securityManager.requirePinAuth || (inputPin != null && securityManager.validatePin(inputPin))) {
                            connectedSessionManager.registerSession(securityManager.sessionToken, clientIp, userAgent)
                            val jsonSuccess = "{\"success\":true,\"message\":\"Pairing successful\"}"
                            sendJsonResponseWithCookie(outputStream, jsonSuccess, securityManager.sessionToken, acceptEncoding)
                        } else {
                            val jsonFail = "{\"success\":false,\"error\":\"Invalid 4-digit PIN code\"}"
                            sendJsonResponse(outputStream, jsonFail, 401, "Unauthorized", acceptEncoding)
                        }
                    }
                    return
                }

                "/api/storage" -> {
                    val storageJson = fileManager.getStorageStatsJson()
                    sendJsonResponse(outputStream, storageJson, acceptEncoding = acceptEncoding)
                    return
                }
            }

            // 3. Security Interceptor: Check Dynamic PIN Auth Requirement & Session Registration
            val extractedToken = securityManager.extractSessionTokenFromCookie(cookieHeader ?: "")
            val isPinRequired = securityManager.requirePinAuth

            val isAuthenticated = !isPinRequired || extractedToken != null &&
                    securityManager.isCookieAuthenticated(cookieHeader) &&
                    connectedSessionManager.isSessionValid(extractedToken, clientIp)

            if (isAuthenticated) {
                val activeToken = if (!extractedToken.isNullOrBlank()) extractedToken else securityManager.sessionToken
                connectedSessionManager.registerSession(activeToken, clientIp, userAgent)
            }

            if (rawPath == "/" || rawPath == "/index.html") {
                val htmlBytes = readAssetFile("web/index.html")
                if (htmlBytes != null) {
                    sendCompressibleBytesResponse(outputStream, htmlBytes, "text/html; charset=UTF-8", acceptEncoding, cacheControl = "no-cache, no-store, must-revalidate, max-age=0")
                } else {
                    send404Response(outputStream, "Asset index.html missing")
                }
                return
            }

            if (!isAuthenticated) {
                Log.e("HTTP_TRACE", "[AUTH REJECTED 401] Token missing or invalid for $method $fullUrl from $clientIp")
                val json401 = "{\"success\":false,\"error\":\"Authentication required\"}"
                sendJsonResponse(outputStream, json401, 401, "Unauthorized", acceptEncoding)
                return
            }

            // 4. Authenticated Request Routing with Real-Time Activity Reporting & File Access Tracking
            when (rawPath) {
                "/api/files" -> withContext(serverDispatchers.fastUiDispatcher) {
                    val requestedPath = queryParams["path"]
                    val directoryContent = fileManager.getDirectoryContent(requestedPath)
                    fileAccessTracker.recordAccess(directoryContent.currentPath)
                    connectedSessionManager.updateClientActivity(
                        clientIp,
                        ClientActivityType.BROWSING,
                        directoryContent.folderName
                    )
                    Log.e("HTTP_TRACE", "[/api/files SUCCESS] Folder '${directoryContent.folderName}' returned ${directoryContent.totalItems} items")
                    val jsonResponse = fileManager.toJson(directoryContent)
                    sendJsonResponse(outputStream, jsonResponse, acceptEncoding = acceptEncoding)
                }

                "/api/thumbnail" -> withContext(serverDispatchers.thumbnailDispatcher) {
                    val filePath = queryParams["path"]
                    if (filePath.isNullOrBlank()) {
                        send404Response(outputStream, "Thumbnail path missing")
                    } else {
                        val cachedThumbFile = thumbnailGenerator.getOrGenerateWebpThumbnail(filePath)
                        if (cachedThumbFile != null && cachedThumbFile.exists() && cachedThumbFile.length() > 0L) {
                            sendFileNioChannelResponse(
                                outputStream = outputStream,
                                file = cachedThumbFile,
                                mimeType = "image/webp",
                                isInline = true,
                                cacheControl = "public, max-age=86400, immutable"
                            )
                        } else {
                            send404Response(outputStream, "Thumbnail not available")
                        }
                    }
                }

                // HTTP 206 Partial Content Video Streaming Engine
                "/api/stream" -> withContext(serverDispatchers.heavyTransferDispatcher) {
                    val filePath = queryParams["path"]
                    if (filePath.isNullOrBlank()) {
                        send404Response(outputStream, "Video path missing")
                    } else {
                        val videoFile = File(filePath)
                        if (videoFile.exists() && videoFile.isFile && videoFile.canRead()) {
                            val rangeHeader = headersMap["range"]
                            val rangeSpec = videoStreamDataSource.parseRangeHeader(videoFile, rangeHeader)
                            val mimeType = fileManager.getMimeType(videoFile)

                            fileAccessTracker.recordAccess(videoFile.absolutePath)
                            connectedSessionManager.updateClientActivity(
                                clientIp,
                                ClientActivityType.STREAMING_VIDEO,
                                videoFile.name
                            )
                            sendPartialContentVideoResponse(
                                outputStream = outputStream,
                                file = videoFile,
                                rangeSpec = rangeSpec,
                                mimeType = mimeType
                            )
                        } else {
                            send404Response(outputStream, "Video file not found or unreadable")
                        }
                    }
                }

                // Inline File Stream Viewing with Background Thumbnail Pre-Caching
                "/api/view" -> withContext(serverDispatchers.fastUiDispatcher) {
                    val filePath = queryParams["path"]
                    if (filePath.isNullOrBlank()) {
                        send404Response(outputStream, "File path missing")
                    } else {
                        val viewFile = File(filePath)
                        if (viewFile.exists() && viewFile.isFile && viewFile.canRead()) {
                            val mimeType = fileManager.getMimeType(viewFile)
                            fileAccessTracker.recordAccess(viewFile.absolutePath)
                            connectedSessionManager.updateClientActivity(
                                clientIp,
                                ClientActivityType.VIEWING_MEDIA,
                                viewFile.name
                            )

                            // Async background pre-caching of WebP thumbnail when media file is viewed
                            if (mimeType.startsWith("image/") || mimeType.startsWith("video/")) {
                                scope.launch(serverDispatchers.thumbnailDispatcher) {
                                    try {
                                        thumbnailGenerator.getOrCreateThumbnail(viewFile)
                                    } catch (_: Exception) {
                                        // Non-blocking background pre-cache
                                    }
                                }
                            }

                            sendFileNioChannelResponse(outputStream, viewFile, mimeType, isInline = true)
                        } else {
                            send404Response(outputStream, "File not found or unreadable")
                        }
                    }
                }

                // High-Performance File Download
                "/api/download" -> withContext(serverDispatchers.heavyTransferDispatcher) {
                    val filePath = queryParams["path"]
                    if (filePath.isNullOrBlank()) {
                        send404Response(outputStream, "File path missing")
                    } else {
                        val downloadFile = File(filePath)
                        if (downloadFile.exists() && downloadFile.isFile && downloadFile.canRead()) {
                            val mimeType = fileManager.getMimeType(downloadFile)
                            fileAccessTracker.recordAccess(downloadFile.absolutePath)
                            connectedSessionManager.updateClientActivity(
                                clientIp,
                                ClientActivityType.DOWNLOADING,
                                downloadFile.name
                            )
                            sendFileNioChannelResponse(outputStream, downloadFile, mimeType, isInline = false)
                        } else {
                            send404Response(outputStream, "File not found or unreadable")
                        }
                    }
                }

                // On-the-Fly ZIP Streaming
                "/download-zip", "/api/zip" -> withContext(serverDispatchers.heavyTransferDispatcher) {
                    val rawPaths = queryParamsList["paths"] ?: queryParamsList["path"] ?: listOfNotNull(queryParams["path"])
                    val validatedFiles = fileManager.validateAndResolvePaths(rawPaths)

                    if (validatedFiles.isEmpty()) {
                        send404Response(outputStream, "Valid files or folders missing for ZIP download")
                    } else {
                        val zipName = if (validatedFiles.size == 1) {
                            "${validatedFiles[0].name.ifEmpty { "Storage" }}.zip"
                        } else {
                            "AirSlice_Batch_${System.currentTimeMillis()}.zip"
                        }

                        validatedFiles.forEach { fileAccessTracker.recordAccess(it.absolutePath) }
                        connectedSessionManager.updateClientActivity(
                            clientIp,
                            ClientActivityType.DOWNLOADING,
                            zipName
                        )
                        sendZipStreamResponse(outputStream, validatedFiles, zipName)
                    }
                }

                // Chunked Streaming File Upload
                "/api/upload" -> withContext(serverDispatchers.heavyTransferDispatcher) {
                    if (method.equals("POST", ignoreCase = true)) {
                        val targetDir = queryParams["dir"]
                        val fileName = queryParams["filename"] ?: "uploaded_file"
                        connectedSessionManager.updateClientActivity(
                            clientIp,
                            ClientActivityType.UPLOADING,
                            fileName
                        )
                        handleStreamingUpload(inputStream, outputStream, targetDir, fileName, contentLength)
                    }
                }

                // Shared Clipboard Manager Endpoints
                "/api/clipboard/status" -> withContext(serverDispatchers.fastUiDispatcher) {
                    val currentMode = when (clipboardSessionStore.mode.value) {
                        ClipboardMode.OFF -> "OFF"
                        ClipboardMode.RECEIVE_ONLY -> "RECEIVE_ONLY"
                        ClipboardMode.TWO_WAY -> "TWO_WAY"
                    }
                    val latestId = clipboardSessionStore.getLatestId()
                    val json = "{\"mode\":\"$currentMode\",\"latestId\":$latestId}"
                    sendJsonResponse(outputStream, json, acceptEncoding = acceptEncoding)
                }

                "/api/clipboard" -> withContext(serverDispatchers.fastUiDispatcher) {
                    handleClipboardEndpoint(
                        method = method,
                        inputStream = inputStream,
                        outputStream = outputStream,
                        contentLength = contentLength,
                        queryParams = queryParams,
                        acceptEncoding = acceptEncoding,
                        clientIp = clientIp
                    )
                }

                else -> {
                    if (rawPath.startsWith("/api/clipboard/")) {
                        withContext(serverDispatchers.fastUiDispatcher) {
                            handleClipboardItemDelete(
                                method = method,
                                rawPath = rawPath,
                                outputStream = outputStream,
                                acceptEncoding = acceptEncoding
                            )
                        }
                    } else {
                        send404Response(outputStream, "Endpoint not found")
                    }
                }
            }

        } catch (e: Exception) {
            if (e !is SocketException && e.message?.contains("Socket closed", ignoreCase = true) != true) {
                e.printStackTrace()
            }
        } finally {
            closeQuietly(inputStream)
            closeQuietly(outputStream)
            closeQuietly(clientSocket)

            inputStream?.let { activeConnections.remove(it) }
            outputStream?.let { activeConnections.remove(it) }
            activeConnections.remove(clientSocket)
            activeClientSockets.remove(clientSocket)
        }
    }

    private suspend fun sendPartialContentVideoResponse(
        outputStream: OutputStream,
        file: File,
        rangeSpec: ByteRangeSpec,
        mimeType: String
    ) {
        try {
            val headers = """
                HTTP/1.1 206 Partial Content
                Accept-Ranges: bytes
                Content-Range: bytes ${rangeSpec.startByte}-${rangeSpec.endByte}/${rangeSpec.fileLength}
                Content-Length: ${rangeSpec.contentLength}
                Content-Type: $mimeType
                Cache-Control: no-cache, no-store, must-revalidate, max-age=0
                Connection: close

            """.trimIndent() + "\r\n"

            withContext(Dispatchers.IO) {
                outputStream.write(headers.toByteArray(Charsets.UTF_8))
            }

            videoStreamDataSource.streamByteRange(file, rangeSpec, outputStream)
        } catch (_: Exception) {
            // Graceful handling of client-abort during video seeking
        }
    }

    private suspend fun sendFileNioChannelResponse(
        outputStream: OutputStream,
        file: File,
        mimeType: String,
        isInline: Boolean,
        cacheControl: String = "no-cache, no-store, must-revalidate, max-age=0"
    ) = withContext(serverDispatchers.heavyTransferDispatcher) {
        try {
            val fileName = file.name
            val encodedFileName = java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
            val dispositionType = if (isInline) "inline" else "attachment"
            val headers = """
                HTTP/1.1 200 OK
                Content-Type: $mimeType
                Content-Length: ${file.length()}
                Content-Disposition: $dispositionType; filename="$fileName"; filename*=UTF-8''$encodedFileName
                Cache-Control: $cacheControl
                Connection: close

            """.trimIndent() + "\r\n"

            outputStream.write(headers.toByteArray(Charsets.UTF_8))

            // Zero-GC Direct Memory Mapped NIO FileChannel stream transfer with 512 KB Reusable Direct Buffer
            FileInputStream(file).use { fis ->
                val channel = fis.channel
                val outChannel = Channels.newChannel(outputStream)

                directBufferPool.useBuffer { buffer ->
                    while (channel.read(buffer) != -1) {
                        buffer.flip()
                        outChannel.write(buffer)
                        buffer.clear()
                    }
                    outputStream.flush()
                }
            }
        } catch (_: Exception) {
            // Graceful handling of client disconnects
        }
    }

    private fun sendZipStreamResponse(outputStream: OutputStream, targets: List<File>, zipName: String) {
        try {
            val encodedZipName = java.net.URLEncoder.encode(zipName, "UTF-8").replace("+", "%20")
            val sb = StringBuilder()
            sb.append("HTTP/1.1 200 OK\r\n")
            sb.append("Content-Type: application/zip\r\n")
            sb.append("Content-Disposition: attachment; filename=\"").append(zipName).append("\"; filename*=UTF-8''").append(encodedZipName).append("\r\n")
            sb.append("Cache-Control: no-cache, no-store, must-revalidate, max-age=0\r\n")
            sb.append("Connection: close\r\n\r\n")

            outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))

            if (targets.size == 1 && targets[0].isDirectory) {
                fileManager.streamDirectoryAsZip(targets[0].absolutePath, outputStream)
            } else {
                fileManager.streamPathsAsZip(targets, outputStream)
            }
            outputStream.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleStreamingUpload(
        inputStream: InputStream,
        outputStream: OutputStream,
        targetDirPath: String?,
        fileName: String,
        totalBytesToRead: Long
    ) {
        if (totalBytesToRead <= 0L) {
            val jsonError = "{\"success\":false,\"error\":\"Invalid or missing Content-Length header\"}"
            sendJsonResponse(outputStream, jsonError, 400, "Bad Request")
            return
        }

        val rootPath = fileManager.getDefaultStoragePath()
        val destFolder = if (targetDirPath.isNullOrBlank()) File(rootPath) else File(targetDirPath)

        if (!destFolder.exists()) {
            destFolder.mkdirs()
        }

        val destFile = File(destFolder, fileName)

        try {
            FileOutputStream(destFile).use { fos ->
                val channel = fos.channel
                val buffer = directBufferPool.acquireBuffer()

                try {
                    val tempArray = ByteArray(buffer.capacity())
                    var totalRead = 0L

                    while (totalRead < totalBytesToRead) {
                        val remaining = totalBytesToRead - totalRead
                        val bytesToRead = tempArray.size.toLong().coerceAtMost(remaining).toInt()

                        val read = inputStream.read(tempArray, 0, bytesToRead)
                        if (read == -1) break

                        buffer.clear()
                        buffer.put(tempArray, 0, read)
                        buffer.flip()
                        channel.write(buffer)

                        totalRead += read
                    }
                    fos.flush()
                } finally {
                    directBufferPool.releaseBuffer(buffer)
                }
            }

            val jsonSuccess = "{\"success\":true,\"message\":\"File uploaded successfully\"}"
            sendJsonResponse(outputStream, jsonSuccess)

        } catch (e: Exception) {
            e.printStackTrace()
            val jsonError = "{\"success\":false,\"error\":\"Upload failed: ${e.message}\"}"
            sendJsonResponse(outputStream, jsonError, 500, "Internal Server Error")
        }
    }

    private fun readHeaderLine(inputStream: InputStream): String? {
        val sb = java.lang.StringBuilder()
        while (true) {
            val b = inputStream.read()
            if (b == -1) {
                if (sb.isEmpty()) return null
                break
            }
            if (b == '\r'.code) {
                val next = inputStream.read()
                if (next == '\n'.code) break
                else if (next != -1) sb.append(next.toChar())
            } else if (b == '\n'.code) {
                break
            } else {
                sb.append(b.toChar())
            }
        }
        return sb.toString()
    }

    private fun readExactBytes(inputStream: InputStream, count: Int): ByteArray {
        if (count <= 0) return ByteArray(0)
        val result = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = inputStream.read(result, offset, count - offset)
            if (read == -1) break
            offset += read
        }
        return result
    }

    private fun readAssetFile(assetPath: String): ByteArray? {
        return try {
            context.assets.open(assetPath).use { it.readBytes() }
        } catch (_: Exception) {
            null
        }
    }

    private fun sendCompressibleBytesResponse(
        outputStream: OutputStream,
        bytes: ByteArray,
        mimeType: String,
        acceptEncoding: String?,
        cacheControl: String = "no-cache, no-store, must-revalidate, max-age=0"
    ) {
        val isTextOrJson = mimeType.startsWith("text/") ||
                mimeType.contains("json") ||
                mimeType.contains("javascript") ||
                mimeType.contains("css") ||
                mimeType.contains("html")

        if (isTextOrJson && acceptEncoding?.contains("gzip", ignoreCase = true) == true && bytes.size > 256) {
            sendGzipBytesResponse(outputStream, bytes, mimeType, cacheControl = cacheControl)
        } else {
            sendRawBytesResponse(outputStream, bytes, mimeType, cacheControl = cacheControl)
        }
    }

    private fun sendGzipBytesResponse(
        outputStream: OutputStream,
        bytes: ByteArray,
        mimeType: String,
        statusCode: Int = 200,
        statusText: String = "OK",
        cookieToken: String? = null,
        cacheControl: String = "no-cache, no-store, must-revalidate, max-age=0"
    ) {
        try {
            val baos = ByteArrayOutputStream()
            GZIPOutputStream(baos).use { gzip ->
                gzip.write(bytes)
                gzip.finish()
            }
            val compressedBytes = baos.toByteArray()

            val sb = StringBuilder()
            sb.append("HTTP/1.1 ").append(statusCode).append(" ").append(statusText).append("\r\n")
            sb.append("Content-Type: ").append(mimeType).append("\r\n")
            sb.append("Content-Encoding: gzip\r\n")
            sb.append("Content-Length: ").append(compressedBytes.size).append("\r\n")
            if (cookieToken != null) {
                sb.append("Set-Cookie: ").append(SecurityManager.COOKIE_NAME).append("=").append(cookieToken).append("; Path=/; SameSite=Lax\r\n")
            }
            sb.append("Access-Control-Allow-Origin: *\r\n")
            sb.append("Cache-Control: ").append(cacheControl).append("\r\n")
            sb.append("Pragma: no-cache\r\n")
            sb.append("Expires: 0\r\n")
            sb.append("Connection: close\r\n\r\n")

            outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
            outputStream.write(compressedBytes)
            outputStream.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendRawBytesResponse(
        outputStream: OutputStream,
        bytes: ByteArray,
        mimeType: String,
        cacheControl: String = "no-cache, no-store, must-revalidate, max-age=0"
    ) {
        val sb = StringBuilder()
        sb.append("HTTP/1.1 200 OK\r\n")
        sb.append("Content-Type: ").append(mimeType).append("\r\n")
        sb.append("Content-Length: ").append(bytes.size).append("\r\n")
        sb.append("Cache-Control: ").append(cacheControl).append("\r\n")
        sb.append("Pragma: no-cache\r\n")
        sb.append("Expires: 0\r\n")
        sb.append("Connection: close\r\n\r\n")

        outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
        outputStream.write(bytes)
        outputStream.flush()
    }

    private fun sendRedirectWithCookie(outputStream: OutputStream, redirectUrl: String, sessionToken: String) {
        val sb = StringBuilder()
        sb.append("HTTP/1.1 302 Found\r\n")
        sb.append("Location: ").append(redirectUrl).append("\r\n")
        sb.append("Set-Cookie: ").append(SecurityManager.COOKIE_NAME).append("=").append(sessionToken).append("; Path=/; SameSite=Lax\r\n")
        sb.append("Content-Length: 0\r\n")
        sb.append("Connection: close\r\n\r\n")

        outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
        outputStream.flush()
    }

    private fun sendJsonResponseWithCookie(
        outputStream: OutputStream,
        json: String,
        sessionToken: String,
        acceptEncoding: String? = null
    ) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        if (acceptEncoding?.contains("gzip", ignoreCase = true) == true && bytes.size > 256) {
            sendGzipBytesResponse(outputStream, bytes, "application/json; charset=UTF-8", 200, "OK", sessionToken)
        } else {
            val sb = StringBuilder()
            sb.append("HTTP/1.1 200 OK\r\n")
            sb.append("Content-Type: application/json; charset=UTF-8\r\n")
            sb.append("Set-Cookie: ").append(SecurityManager.COOKIE_NAME).append("=").append(sessionToken).append("; Path=/; SameSite=Lax\r\n")
            sb.append("Content-Length: ").append(bytes.size).append("\r\n")
            sb.append("Access-Control-Allow-Origin: *\r\n")
            sb.append("Cache-Control: no-cache, no-store, must-revalidate, max-age=0\r\n")
            sb.append("Pragma: no-cache\r\n")
            sb.append("Expires: 0\r\n")
            sb.append("Connection: close\r\n\r\n")

            outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
            outputStream.write(bytes)
            outputStream.flush()
        }
    }

    private fun sendJsonResponse(
        outputStream: OutputStream,
        json: String,
        statusCode: Int = 200,
        statusText: String = "OK",
        acceptEncoding: String? = null
    ) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        if (acceptEncoding?.contains("gzip", ignoreCase = true) == true && bytes.size > 256) {
            sendGzipBytesResponse(outputStream, bytes, "application/json; charset=UTF-8", statusCode, statusText)
        } else {
            val sb = StringBuilder()
            sb.append("HTTP/1.1 ").append(statusCode).append(" ").append(statusText).append("\r\n")
            sb.append("Content-Type: application/json; charset=UTF-8\r\n")
            sb.append("Content-Length: ").append(bytes.size).append("\r\n")
            sb.append("Access-Control-Allow-Origin: *\r\n")
            sb.append("Cache-Control: no-cache, no-store, must-revalidate, max-age=0\r\n")
            sb.append("Pragma: no-cache\r\n")
            sb.append("Expires: 0\r\n")
            sb.append("Connection: close\r\n\r\n")

            outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
            outputStream.write(bytes)
            outputStream.flush()
        }
    }

    private fun send404Response(outputStream: OutputStream, message: String) {
        val html = "<html><body><h1>404 Not Found</h1><p>$message</p></body></html>"
        val bytes = html.toByteArray(Charsets.UTF_8)
        val sb = StringBuilder()
        sb.append("HTTP/1.1 404 Not Found\r\n")
        sb.append("Content-Type: text/html; charset=UTF-8\r\n")
        sb.append("Content-Length: ").append(bytes.size).append("\r\n")
        sb.append("Cache-Control: no-cache, no-store, must-revalidate, max-age=0\r\n")
        sb.append("Pragma: no-cache\r\n")
        sb.append("Expires: 0\r\n")
        sb.append("Connection: close\r\n\r\n")

        outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
        outputStream.write(bytes)
        outputStream.flush()
    }

    private fun parseQueryParamsList(queryString: String): Map<String, List<String>> {
        if (queryString.isBlank()) return emptyMap()
        val params = mutableMapOf<String, MutableList<String>>()
        for (pair in queryString.split("&")) {
            val parts = pair.split("=")
            if (parts.size == 2) {
                val key = parseUrlComponent(parts[0])
                val rawVal = parseUrlComponent(parts[1])
                val values = rawVal.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                params.getOrPut(key) { mutableListOf() }.addAll(values)
            }
        }
        return params
    }

    private fun parseQueryParams(queryString: String): Map<String, String> {
        if (queryString.isBlank()) return emptyMap()
        val params = mutableMapOf<String, String>()
        for (pair in queryString.split("&")) {
            val parts = pair.split("=")
            if (parts.size == 2) {
                val key = parseUrlComponent(parts[0])
                val value = parseUrlComponent(parts[1])
                params[key] = value
            }
        }
        return params
    }

    private fun parseUrlComponent(component: String): String {
        return try {
            val percentEncoded = component.replace("+", "%2B")
            URLDecoder.decode(percentEncoded, "UTF-8")
        } catch (_: Exception) {
            component
        }
    }

    private fun extractJsonField(json: String, fieldName: String): String? {
        return extractJsonStringField(json, fieldName)
    }

    fun extractJsonStringField(json: String, fieldName: String): String? {
        val keyPattern = "\"$fieldName\""
        val keyIndex = json.indexOf(keyPattern)
        if (keyIndex == -1) return null

        var index = keyIndex + keyPattern.length

        // Skip whitespace up to ':'
        while (index < json.length && json[index].isWhitespace()) {
            index++
        }
        if (index >= json.length || json[index] != ':') return null
        index++ // skip ':'

        // Skip whitespace up to opening quote '"'
        while (index < json.length && json[index].isWhitespace()) {
            index++
        }
        if (index >= json.length || json[index] != '"') return null
        index++ // skip opening quote

        // Read string content handling RFC 8259 escape sequences
        val sb = StringBuilder()
        var isEscaped = false

        while (index < json.length) {
            val ch = json[index]
            if (isEscaped) {
                when (ch) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> {
                        if (index + 4 < json.length) {
                            val hex = json.substring(index + 1, index + 5)
                            try {
                                val codePoint = hex.toInt(16)
                                sb.append(codePoint.toChar())
                                index += 4
                            } catch (_: NumberFormatException) {
                                sb.append("\\u")
                            }
                        } else {
                            sb.append("\\u")
                        }
                    }
                    else -> sb.append(ch)
                }
                isEscaped = false
            } else {
                if (ch == '\\') {
                    isEscaped = true
                } else if (ch == '"') {
                    // Reached terminating unescaped quote
                    return sb.toString()
                } else {
                    sb.append(ch)
                }
            }
            index++
        }

        return null // Unterminated string
    }

    @Synchronized
    fun stopServer(reason: String = "Normal Shutdown") {
        if (_serverStatus.value == ServerStatus.STOPPED) return

        _serverStatus.value = ServerStatus.STOPPING

        // 1. Wipe all active session tokens, PINs, and clipboard state
        securityManager.clearSessions()
        connectionApprovalManager.clearPendingRequest()
        connectedSessionManager.clearAllSessions()
        clipboardSessionStore.reset()
        clipboardRateLimiter.reset()
        hotspotClientTracker.reset()

        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            serverSocket = null
        }

        synchronized(activeConnections) {
            val connectionsCopy = ArrayList(activeConnections)
            for (conn in connectionsCopy) {
                closeQuietly(conn)
            }
            activeConnections.clear()
        }
        activeClientSockets.clear()

        serverJob?.cancel()
        serverJob = null

        _serverStartTimeMillis = 0L
        _activePort.value = null
        _serverStatus.value = ServerStatus.STOPPED
    }

    @Synchronized
    fun onHostUrlChanged(newHostIp: String) {
        if (_serverStatus.value != ServerStatus.RUNNING && _serverStatus.value != ServerStatus.RECONNECTING) {
            return
        }
        hostUrlChangeHandler.handleHostUrlChanged(newHostIp)
    }

    fun hasConnectedHotspotClient(hotspotIp: String?, hotspotInterfaceName: String?): Boolean {
        return hotspotClientTracker.hasConnectedHotspotClient(hotspotIp, hotspotInterfaceName)
    }

    private fun handleClipboardEndpoint(
        method: String,
        inputStream: InputStream,
        outputStream: OutputStream,
        contentLength: Long,
        queryParams: Map<String, String>,
        acceptEncoding: String?,
        clientIp: String?
    ) {
        val currentMode = clipboardSessionStore.mode.value

        when {
            method.equals("GET", ignoreCase = true) -> {
                if (currentMode == ClipboardMode.OFF) {
                    val jsonError = "{\"success\":false,\"error\":\"Clipboard sharing is OFF on the host device\"}"
                    sendJsonResponse(outputStream, jsonError, 403, "Forbidden", acceptEncoding)
                    return
                }

                val sinceId = queryParams["since"]?.toLongOrNull()
                val items = clipboardSessionStore.getItems(sinceId)
                val json = formatClipboardItemsJson(items)
                sendJsonResponse(outputStream, json, acceptEncoding = acceptEncoding)
            }

            method.equals("POST", ignoreCase = true) -> {
                if (currentMode == ClipboardMode.OFF) {
                    val jsonError = "{\"success\":false,\"error\":\"Clipboard sharing is OFF on the host device\"}"
                    sendJsonResponse(outputStream, jsonError, 403, "Forbidden", acceptEncoding)
                    return
                }

                // Length Required check
                if (contentLength <= 0) {
                    val jsonError = "{\"success\":false,\"error\":\"Length Required\"}"
                    sendJsonResponse(outputStream, jsonError, 411, "Length Required", acceptEncoding)
                    return
                }

                // Rate limiting (12 req / 10s per client)
                val ipKey = clientIp ?: "unknown"
                if (!clipboardRateLimiter.isAllowed(ipKey)) {
                    val jsonError = "{\"success\":false,\"error\":\"Too Many Requests\"}"
                    sendJsonResponse(outputStream, jsonError, 429, "Too Many Requests", acceptEncoding)
                    return
                }

                // Hard byte cap to prevent OOM
                if (contentLength > ClipboardSessionStore.MAX_ITEM_BYTES + 1024L) {
                    val jsonError = "{\"success\":false,\"error\":\"Payload exceeds 64 KB limit\"}"
                    sendJsonResponse(outputStream, jsonError, 413, "Payload Too Large", acceptEncoding)
                    return
                }

                val bodyBytes = readExactBytes(inputStream, contentLength.toInt())
                val bodyText = String(bodyBytes, Charsets.UTF_8)
                val text = extractJsonField(bodyText, "text")

                if (text.isNullOrBlank()) {
                    val jsonError = "{\"success\":false,\"error\":\"Missing or blank 'text' in JSON body\"}"
                    sendJsonResponse(outputStream, jsonError, 400, "Bad Request", acceptEncoding)
                    return
                }

                if (text.toByteArray(Charsets.UTF_8).size > ClipboardSessionStore.MAX_ITEM_BYTES) {
                    val jsonError = "{\"success\":false,\"error\":\"Text exceeds 64 KB limit\"}"
                    sendJsonResponse(outputStream, jsonError, 413, "Payload Too Large", acceptEncoding)
                    return
                }

                val createdItem = clipboardSessionStore.addItem(text, ClipboardSessionStore.SOURCE_WEB)
                if (createdItem != null) {
                    val json = "{\"success\":true,\"item\":${formatSingleClipboardItemJson(createdItem)}}"
                    sendJsonResponse(outputStream, json, 201, "Created", acceptEncoding)
                } else {
                    val jsonError = "{\"success\":false,\"error\":\"Failed to store clipboard item\"}"
                    sendJsonResponse(outputStream, jsonError, 400, "Bad Request", acceptEncoding)
                }
            }

            method.equals("DELETE", ignoreCase = true) -> {
                if (currentMode == ClipboardMode.OFF) {
                    val jsonError = "{\"success\":false,\"error\":\"Clipboard sharing is OFF on the host device\"}"
                    sendJsonResponse(outputStream, jsonError, 403, "Forbidden", acceptEncoding)
                    return
                }
                clipboardSessionStore.clearFromWeb()
                val jsonSuccess = "{\"success\":true,\"message\":\"Clipboard history cleared\"}"
                sendJsonResponse(outputStream, jsonSuccess, acceptEncoding = acceptEncoding)
            }

            else -> {
                sendJsonResponse(outputStream, "{\"error\":\"Method Not Allowed\"}", 405, "Method Not Allowed", acceptEncoding)
            }
        }
    }

    private fun handleClipboardItemDelete(
        method: String,
        rawPath: String,
        outputStream: OutputStream,
        acceptEncoding: String?
    ) {
        val currentMode = clipboardSessionStore.mode.value
        if (currentMode == ClipboardMode.OFF) {
            val jsonError = "{\"success\":false,\"error\":\"Clipboard sharing is OFF on the host device\"}"
            sendJsonResponse(outputStream, jsonError, 403, "Forbidden", acceptEncoding)
            return
        }

        if (method.equals("DELETE", ignoreCase = true)) {
            val idStr = rawPath.substringAfter("/api/clipboard/").trim()
            val id = idStr.toLongOrNull()
            if (id == null) {
                sendJsonResponse(outputStream, "{\"success\":false,\"error\":\"Invalid clipboard item ID\"}", 400, "Bad Request", acceptEncoding)
                return
            }

            val removed = clipboardSessionStore.removeItem(id)
            if (removed) {
                sendJsonResponse(outputStream, "{\"success\":true,\"message\":\"Item removed\"}", acceptEncoding = acceptEncoding)
            } else {
                sendJsonResponse(outputStream, "{\"success\":false,\"error\":\"Item not found\"}", 404, "Not Found", acceptEncoding)
            }
        } else {
            sendJsonResponse(outputStream, "{\"error\":\"Method Not Allowed\"}", 405, "Method Not Allowed", acceptEncoding)
        }
    }

    private fun formatClipboardItemsJson(items: List<ClipboardItem>): String {
        val sb = StringBuilder()
        sb.append("{\"items\":[")
        for (i in items.indices) {
            sb.append(formatSingleClipboardItemJson(items[i]))
            if (i < items.size - 1) {
                sb.append(",")
            }
        }
        sb.append("]}")
        return sb.toString()
    }

    private fun formatSingleClipboardItemJson(item: ClipboardItem): String {
        return "{\"id\":${item.id},\"text\":\"${escapeJson(item.text)}\",\"source\":\"${item.source}\",\"timestamp\":${item.timestamp}}"
    }

    fun escapeJson(str: String): String {
        val sb = StringBuilder(str.length + 16)
        for (i in 0 until str.length) {
            val c = str[i]
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (c.code < 0x20) {
                        val hex = Integer.toHexString(c.code)
                        sb.append("\\u")
                        repeat(4 - hex.length) {
                            sb.append('0')
                        }
                        sb.append(hex)
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun closeQuietly(closeable: Closeable?) {
        try {
            closeable?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
