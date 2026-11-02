# ⚡ AirSlice v1.1.1 — High-Performance Android Local Web Server & HTTP 206 Media Streamer

![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Release](https://img.shields.io/badge/Release-v1.1.1-10B981?style=for-the-badge)
![Platform](https://img.shields.io/badge/Android-API%2026%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![APK Size](https://img.shields.io/badge/APK%20Size-3.5%20MB-00C853?style=for-the-badge)

**AirSlice** (`com.anasdev.fluxlink`) is an offline-first, embedded HTTP file server and low-latency media streaming engine for Android. It transforms any Android device into a zero-configuration cloud workspace and video streaming server accessible from any desktop or mobile browser over local Wi-Fi or Direct Mobile Hotspot—requiring **zero internet connection, zero cables, and zero client software installation**.

---

## 🌟 What's New in Release v1.1.1

* **🚀 Ultra-Slim 3.5 MB Release APK (95% Size Reduction):** Replaced heavy extended icon libraries with lightweight standalone vector assets and implemented crash-proof R8 resource/code shrinking (`-dontobfuscate`), dropping the Release APK size from 75 MB down to **3.51 MB**.
* **⚡ Draw-Phase Animation & Recomposition Isolation:** Isolated the status dot pulse animation into `Modifier.graphicsLayer` (Draw-phase execution with 0 recompositions) and encapsulated the 1-second server uptime ticker into an isolated `@Composable` element.
* **🛡️ Subsampled Thumbnail Memory Safety:** Enforced `RGB_565` bitmap configuration for subsampled image thumbnail decoding with explicit bitmap recycling to prevent GC pauses and memory spikes.

---

## 🌟 Previous Highlights (Release v1.1.0)

* **🔴 Interactive Morphing Power Control & Glassmorphic Top Bar:** Completely redesigned power button featuring a large circular Emerald Green control (`148.dp`) with a pulsing halo ring in Stopped mode that smoothly morphs (`AnimatedContent` + `spring`) into a top-docked **Translucent Glassmorphic Status Bar** with a 1-tap **Circular Red Stop Button (`48.dp`)**, live **Server Uptime Timer (`MM:SS`)**, and interactive **`PIN` Badge**.
* **⚙️ Expanded Material 3 Settings Workspace & Preferences:** Categorized preferences organized into **Appearance & Language** (Theme Mode, App Language), **Security & Access** (Instant PIN Auth Toggle), **Server & Files** (Show Hidden `.files`, Custom Server Port `1024..65535`, Keep Screen On), and an **About** section with developer & GitHub repository links.
* **⚡ Full Bilingual RTL & LTR Support (Arabic & English):** Native per-app language switching (`AppCompatDelegate.setApplicationLocales`) with automatic RTL/LTR layout flipping, Western Arabic numeral enforcement (`0-9`), and auto-mirrored vector icons (`Icons.AutoMirrored`).
* **📐 Responsive Portrait & Landscape 2-Column Layouts:** Adaptive two-column row layout in Landscape mode featuring a horizontal QR + URL card with basic marquee scrolling on long IP addresses to prevent text clipping.
* **⚡ Zero-Latency Client-Side Sorting & File Access Tracking:** Sorting toolbar in the Web UI for **Name**, **Date Modified**, **Last Accessed**, and **File Size** with ascending/descending toggles, directories-first grouping, and natural alphanumeric sorting (`Video_2.mp4` before `Video_10.mp4`).
* **🖼️ Zero-Footprint On-the-Fly WebP Thumbnails:** Concurrency-bounded (`Semaphore(8)`) WebP thumbnail generation with deduplicated requests and atomic writes.

---

## ✨ Engineering Highlights

* **🎬 True HTTP 206 Partial-Content Video Streaming:** Streams multi-gigabyte `4K`/`1080p` videos (`.mp4`, `.mkv`, `.webm`, `.mov`) directly from phone flash storage to an HTML5 `<video>` player with instant timeline scrubbing (`FileChannel` / byte-range seeking in `< 1ms`) and `O(1)` memory footprint.
* **🚀 20+ MB/s Zero-Copy & Pooled NIO Pipeline:** Leverages Zero-Copy Java NIO `FileChannel` transfers, a pre-allocated `512 KB` direct memory buffer pool (`DirectBufferPool`), and `1 MB` TCP socket buffers to sustain **15–22+ MB/s** wireless transfer speeds.
* **📦 Zero-Storage On-the-Fly ZIP Streaming:** Streams multiple files or entire directory trees directly over the network as a single `.zip` archive (`ZipOutputStream`) without writing temporary files to phone storage.
* **⚡ Smart Network Topology & Hotspot Priority Engine:** Inspects system `NetworkInterface` entries (`ap0`, `swlan0`, `wlan0`, `192.168.43.x`) and prioritizes **Direct Hotspot (Turbo Mode)** over standard home routers to bypass wireless half-duplex hairpinning.
* **🔐 Dual-Mode Security & Live Session Control:**
  * **4-Digit PIN & QR Session Token** authentication with 1-tap PIN bypass toggle in Settings.
  * **1-Tap Phone Approval:** Browser users can request instant access, triggering a live Material 3 **[Approve / Deny]** prompt on the Android device.
  * **Real-Time Client Activity Monitor:** Displays connected browsers on the phone dashboard with live activity badges (`▶ Streaming`, `⬇ Downloading`, `⬆ Uploading`, `👁️ Viewing`, `📁 Browsing`, `Online • Idle`) and a 1-tap **Session Kick/Revoke** button.
* **🖥️ Modern Single-Page Web Workspace (100% Offline):** Bundled inside Android `assets/` with dark-mode glassmorphism UI, client-side live search filter, interactive media theater lightbox modals, image carousels, multi-file selection with a floating batch bar, and a live upload speed/ETA meter (`MB/s` + `ETA`).

---

## 📊 Performance & Android Studio Telemetry

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
│   ├── model                         # NetworkState, DirectoryContent, FileMetadata, ServerStatus, AppTheme, AppLanguage
│   └── repository                    # NetworkRepository, ServerRepository, SessionRepository, SettingsRepository
├── service
│   └── ServerForegroundService.kt    # Foreground Service, Notification, WakeLock & WifiLock lifecycle
├── presentation
│   ├── MainActivity.kt               # Main Activity Entrypoint
│   ├── navigation/AppNavigation.kt   # NavHost with custom slide + fade transitions
│   ├── state/HomeViewModel.kt        # Unidirectional UI State & Intent handling
│   ├── state/SettingsViewModel.kt    # Settings StateFlows & Preferences Mutators
│   ├── ui/screens/HomeScreen.kt      # Morphing Power Control, Inspector Dialog, Connected Devices
│   └── ui/screens/SettingsScreen.kt  # Material 3 Categorized Settings & About Section
└── assets/web                        # Offline Single-Page Web Application (HTML5 / CSS3 / ES6 Vanilla JS)
```

---

## 👤 Developer & Maintainer

* **Developer:** Anas Muhammad
* **GitHub Repository:** [EngAnasMuhammad/AirSlice](https://github.com/EngAnasMuhammad/AirSlice)

---
