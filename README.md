# ⚡ AirSlice — High-Performance Android Local Web Server & HTTP 206 Media Streamer

![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Engine](https://img.shields.io/badge/Engine-Custom%20NIO%20Http-087CFA?style=for-the-badge)
![Architecture](https://img.shields.io/badge/Architecture-Clean%20%2B%20MVVM-2EA043?style=for-the-badge)
![Platform](https://img.shields.io/badge/Android-API%2026%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)

**AirSlice** (`com.anasdev.fluxlink`) is an offline-first, embedded HTTP file server and low-latency media streaming engine for Android. It transforms any Android device into a zero-configuration cloud workspace and video streaming server accessible from any desktop or mobile browser over local Wi-Fi or Direct Mobile Hotspot—requiring **zero internet connection, zero cables, and zero client software installation**.

---

## ✨ Key Engineering Highlights

* **⚡ Zero-Latency Client-Side Sorting Engine (`< 1ms`):** Features an in-memory sorting toolbar in the Web UI supporting **Name**, **Date Modified**, **Last Accessed**, and **File Size** with ascending/descending toggles. Enforces **Directories First** grouping and **Natural Alphanumeric Sorting** (`Video_2.mp4` before `Video_10.mp4`). Automatically persists preferences in `localStorage` across page reloads and dynamically updates card subtitles (`"Opened 5m ago"`, size, date).
* **⏱️ Thread-Safe File Access Tracking (`FileAccessTracker`):** Tracks last-accessed timestamps in real time whenever files or folders are browsed, viewed, streamed, or downloaded. Exposes `lastAccessedMillis` and `formattedAccessDate` in directory JSON responses.
* **🎬 True HTTP 206 Partial-Content Video Streaming:** Streams multi-gigabyte `4K`/`1080p` videos (`.mp4`, `.mkv`, `.webm`, `.mov`) directly from phone flash storage to an HTML5 `<video>` player with instant timeline scrubbing (`FileChannel` / byte-range seeking in `< 1ms`) and `O(1)` memory footprint.
* **🚀 20+ MB/s Zero-Copy & Pooled NIO Pipeline:** Leverages Zero-Copy Java NIO `FileChannel` transfers, a pre-allocated `512 KB` direct memory buffer pool (`DirectBufferPool`), and `1 MB` TCP socket buffers to sustain **15–22+ MB/s** wireless transfer speeds.
* **🖼️ High-Throughput Subsampled WebP Thumbnail Disk Cache:** Features a 8-permit bounded concurrency pool (`Semaphore(8)`), in-flight request deduplication (`ConcurrentHashMap`), and `NonCancellable` atomic file writes (`$hash.tmp` $\to$ `$hash.webp`). Implements a 4-pass fallback decoder (`ThumbnailUtils`, `ImageDecoder`, `decodeByteArray`, `BufferedInputStream`) handling high-res camera photos, HEIC, PNG, WebP, JPEG, and video frames. Exposes a reactive `StateFlow<Long>` for live disk cache telemetry on the phone app.
* **📦 Zero-Storage On-the-Fly ZIP Streaming:** Allows browser users to select multiple files or entire directory trees and stream them directly over the network as a single `.zip` archive (`ZipOutputStream`) without writing temporary files to phone storage. Uses smart MIME detection (`Deflater.NO_COMPRESSION` for pre-compressed media) to eliminate CPU bottlenecks and maintain full wire speed.
* **⚡ Smart Network Topology & Hotspot Priority Engine:** Automatically inspects system `NetworkInterface` entries (`ap0`, `swlan0`, `wlan0`, `192.168.43.x`) and strictly prioritizes **Direct Hotspot (Turbo Mode)** over standard home routers to bypass wireless half-duplex hairpinning.
* **🔐 Dual-Mode Security & Live Session Control:**
  * **4-Digit PIN & QR Session Token** authentication.
  * **1-Tap Phone Approval:** Browser users can request instant access, triggering a live Material 3 **[Approve / Deny]** prompt on the Android device.
  * **Real-Time Client Activity Monitor:** Displays connected browsers on the phone dashboard with live activity badges (`▶ Streaming`, `⬇ Downloading`, `⬆ Uploading`, `👁️ Viewing`, `📁 Browsing`, `Online • Idle`) and a 1-tap **Session Kick/Revoke** button.
* **🛡️ Material 3 Storage Access Warning Banner & Inspector Dialog:** Displays a prominent top warning banner on the phone dashboard if **All Files Access** (`MANAGE_EXTERNAL_STORAGE`) is missing, complete with a 1-tap **"Grant Access"** button and auto-refresh on return. Includes an adaptive 2-column landscape Server Inspector Dialog (`840.dp`) with live thumbnail disk cache telemetry and an instant **[ Clear ]** button.
* **🖥️ Modern Single-Page Web Workspace (100% Offline):** Bundled inside Android `assets/` with dark-mode glassmorphism UI, client-side live search filter, interactive media theater lightbox modals, image carousels, multi-file selection with a floating batch bar, and a live upload speed/ETA meter (`MB/s` + `ETA`).

---

## 📊 Performance & Android Studio Telemetry

AirSlice is engineered with strict workload isolation (`limitedParallelism` coroutine dispatchers) and zero-allocation direct buffer pooling to ensure multi-gigabyte transfers never degrade UI responsiveness or overheat the device.

| Telemetry Metric                     | Measured Value (`com.anasdev.fluxlink`)           | Architectural Reason                                                                                                                                   |
|:-------------------------------------|:--------------------------------------------------|:-------------------------------------------------------------------------------------------------------------------------------------------------------|
| **App CPU Usage**                    | **`~8% – 10%`**                                   | OS-level NIO Zero-Copy (`FileChannel`) offloads packet transfer directly to the Linux kernel, keeping JVM user-space CPU usage minimal.                |
| **Memory Footprint (3 GB Transfer)** | **`92 MB` (Background) / `135 MB` (UI Active)**   | Strict `O(1)` streaming pipeline. Pre-allocated `DirectBufferPool` (reusable `512 KB` direct buffers) prevents heap expansion regardless of file size. |
| **Garbage Collection (GC) Events**   | **0 GC pauses during video scrub/stream**         | Pre-allocated direct buffer reuse eliminates byte-array allocation churn and JVM GC pauses.                                                            |
| **Background Process Survival**      | **Zero throughput drop when app is backgrounded** | Powered by an Android Foreground Service holding a `PARTIAL_WAKE_LOCK` and `WIFI_MODE_FULL_LOW_LATENCY` hardware lock.                                 |

---

## 🏗️ System Architecture

AirSlice follows **Clean Architecture + MVVM** with reactive Kotlin `StateFlow` state management:

```text
com.anasdev.fluxlink
├── data
│   ├── NetworkInfoProvider.kt        # Hotspot vs Wi-Fi interface scoring & callbacks
│   ├── LocalServerEngine.kt          # Embedded HTTP Server Engine, HTTP 206 Range Streamer, ZIP Streamer
│   ├── SecurityManager.kt            # 4-Digit PIN & Session Cookie Auth
│   ├── ConnectionApprovalManager.kt  # 1-Tap Approval State Machine
│   ├── ConnectedSessionManager.kt    # Live Client Session & Real-Time Activity Tracking
│   ├── FileAccessTracker.kt          # Thread-safe in-memory file & folder access tracker
│   ├── ThumbnailGenerator.kt         # Deduplicated WebP Thumbnail Generator & Reactive StateFlow Cache
│   ├── DirectBufferPool.kt           # Zero-GC 512 KB Reusable Direct Buffer Pool
│   ├── ServerDispatchers.kt          # Workload isolation via dedicated coroutine dispatchers
│   ├── LockManager.kt                # CPU WakeLock + Low-Latency Wi-Fi Lock
│   ├── FileManager.kt                # Directory metadata, MIME mappings, path-traversal validation
│   ├── VideoStreamDataSource.kt      # O(1) RandomAccessFile / FileChannel Range Streamer
│   └── QrCodeGenerator.kt            # Pure Kotlin QR Code bitmap generator
├── domain
│   ├── model                         # NetworkState, DirectoryContent, FileMetadata, ServerStatus
│   └── repository                    # NetworkRepository, ServerRepository, SessionRepository
├── service
│   └── ServerForegroundService.kt    # Foreground Service, Notification, WakeLock & WifiLock lifecycle
├── presentation
│   ├── MainActivity.kt               # Main Activity Entrypoint
│   ├── state/HomeViewModel.kt        # Unidirectional UI State & Intent handling
│   └── ui/screens/HomeScreen.kt      # Minimalist Material 3 Dashboard, Storage Warning Banner, Inspector Dialog
└── assets/web                        # Offline Single-Page Web Application (HTML5 / CSS3 / ES6 Vanilla JS)
```

---

## 🔬 Deep Dive: How the Core Pipelines Work

### 1. Zero-Latency Client-Side Sorting Engine
1. When directory JSON is returned by `/api/files`, `app.js` runs `getSortedItems()`.
2. Folders are strictly grouped at the top.
3. Items are sorted in `< 1ms` using `a.name.localeCompare(b.name, undefined, { numeric: true, sensitivity: 'base' })` so `Video_2.mp4` appears before `Video_10.mp4`.
4. User selections (`name`, `modified`, `accessed`, `size`, `asc`/`desc`) are saved in `localStorage`.
5. Card subtitles adapt dynamically to show relative opened time (`"Opened 5m ago"`) or formatted size + modified date.

### 2. HTTP 206 Video Streaming & Instant Seeking
When a user clicks a 4 GB video in the browser:
1. The browser's `<video>` player sends `GET /api/stream?path=...` with `Range: bytes=0-`.
2. AirSlice responds with `HTTP 206 Partial Content`, `Accept-Ranges: bytes`, and `Content-Range: bytes 0-4294967295/4294967296`.
3. If the user scrubs the video timeline to minute 50, the browser aborts the old TCP stream and sends a new header (`Range: bytes=2147483648-`).
4. AirSlice catches the client-abort exception cleanly, borrows a pre-allocated `512 KB` direct buffer from `DirectBufferPool`, jumps directly to byte `2,147,483,648` in flash storage via `FileChannel.position()` in `< 1ms`, and resumes streaming immediately.

### 3. Workload Isolation (`ServerDispatchers`)
To prevent heavy downloads or batch thumbnail generation from blocking directory navigation, AirSlice isolates route execution across dedicated coroutine pools:
- **`fastUiDispatcher` (`limitedParallelism(8)`)**: Reserved for `/api/files`, `/api/server-info`, `/api/storage`, `/auth/*`, and `/api/view`—guaranteeing 0ms queue delay when browsing folders during active 20+ MB/s transfers.
- **`heavyTransferDispatcher` (`limitedParallelism(6)`)**: Dedicated to `/api/stream`, `/api/download`, `/download-zip`, and `/api/upload`.
- **`thumbnailDispatcher` (`limitedParallelism(8)`)**: Bounds concurrent `BitmapFactory` / `ImageDecoder` subsampling so opening a folder with 1,000 photos never starves server threads.

---

## 🛠️ Tech Stack

* **Language:** Kotlin 2.0+ (Coroutines, Flow, StateFlow, Channels)
* **Android UI:** Jetpack Compose (Material 3, `AnimatedContent`, Adaptive Portrait/Landscape Layouts)
* **Embedded Backend:** Custom High-Throughput HTTP Engine (Partial Content, GZIP Compression, Custom Routing & Auth Interceptors)
* **Concurrency & I/O:** Java NIO `FileChannel`, `DirectBufferPool`, `ZipOutputStream`, Dedicated Coroutine Dispatchers
* **System Integration:** Android Foreground Services, `ConnectivityManager.NetworkCallback`, `WifiManager.WifiLock` (`WIFI_MODE_FULL_LOW_LATENCY`), `PowerManager.WakeLock`
* **Dependency Injection:** Hilt / Dagger
* **Web Frontend:** Semantic HTML5, Modern CSS Grid & Glassmorphism, Vanilla ES6 JavaScript (XMLHttpRequest upload telemetry, Lightbox Modal, Image Carousel, Multi-Select Batch Bar, Client-Side Sorting Engine)

---

## 🚀 Getting Started

1. **Clone the repository:**
   ```bash
   git clone https://github.com/AnasDev/AirSlice.git
   ```
2. **Open in Android Studio** (Ladybug or newer recommended) and sync Gradle.
3. **Run on a physical Android device** (API 26+).
4. **Grant All Files Access permission**, tap **Start Server**, and either:
   - Scan the QR Code / enter the displayed `http://192.168.x.x:8080` URL in any browser on the same Wi-Fi network, OR
   - Tap **⚡ Hotspot Turbo** to open Tethering settings and enable Mobile Hotspot for direct device-to-device transfers at maximum Wi-Fi speed (**15–22+ MB/s**).

---
