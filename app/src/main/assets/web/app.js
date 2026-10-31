(function () {
    let currentPath = null;
    let currentFolderData = null;
    let currentFolderItems = [];
    let currentImagesList = [];
    let currentImageIndex = -1;
    let selectedItemsMap = new Map(); // path -> item
    let dragCounter = 0;
    let activeFolderAbortController = null;
    let searchQuery = '';
    let pollingTimer = null;

    let currentSortBy = localStorage.getItem('airslice_sort_by') || 'name';
    let currentSortDir = localStorage.getItem('airslice_sort_dir') || 'asc';

    document.addEventListener('DOMContentLoaded', () => {
        initEventListeners();
        initSortControls();
        initLightbox();
        initDragAndDrop();
        loadInitialData();
    });

    function initEventListeners() {
        const btnLogin = document.getElementById('btn-login');
        const btnRequestApproval = document.getElementById('btn-request-approval');
        const pinInput = document.getElementById('pin-input');
        const btnZipCurrent = document.getElementById('btn-zip-current');
        const btnUploadFile = document.getElementById('btn-upload-file');
        const filePicker = document.getElementById('file-picker');
        const searchInput = document.getElementById('search-input');
        const btnToggleSelectAll = document.getElementById('btn-toggle-select-all');
        const btnBatchDownloadZip = document.getElementById('btn-batch-download-zip');
        const btnBatchClear = document.getElementById('btn-batch-clear');

        if (btnLogin) {
            btnLogin.addEventListener('click', handlePinLogin);
        }

        if (btnRequestApproval) {
            btnRequestApproval.addEventListener('click', handleRequestApproval);
        }

        if (pinInput) {
            pinInput.addEventListener('keypress', (e) => {
                if (e.key === 'Enter') handlePinLogin();
            });
        }

        if (btnUploadFile && filePicker) {
            btnUploadFile.addEventListener('click', () => {
                filePicker.click();
            });

            filePicker.addEventListener('change', (e) => {
                if (e.target.files && e.target.files.length > 0) {
                    handleFilesUploadQueue(e.target.files);
                    filePicker.value = '';
                }
            });
        }

        if (btnZipCurrent) {
            btnZipCurrent.addEventListener('click', (e) => {
                e.preventDefault();
                triggerZipDownload(currentPath);
            });
        }

        if (btnToggleSelectAll) {
            btnToggleSelectAll.addEventListener('click', handleToggleSelectAll);
        }

        if (btnBatchDownloadZip) {
            btnBatchDownloadZip.addEventListener('click', handleBatchZipDownload);
        }

        if (btnBatchClear) {
            btnBatchClear.addEventListener('click', clearAllSelections);
        }

        if (searchInput) {
            searchInput.addEventListener('input', (e) => {
                searchQuery = (e.target.value || '').trim().toLowerCase();
                if (currentFolderData) {
                    renderGrid(currentFolderData);
                }
            });
        }
    }

    function initSortControls() {
        const sortSelect = document.getElementById('sort-select');
        const btnSortDir = document.getElementById('btn-sort-direction');

        if (sortSelect) {
            sortSelect.value = currentSortBy;
            sortSelect.addEventListener('change', (e) => {
                currentSortBy = e.target.value || 'name';
                localStorage.setItem('airslice_sort_by', currentSortBy);
                if (currentFolderData) {
                    renderGrid(currentFolderData);
                }
            });
        }

        if (btnSortDir) {
            btnSortDir.innerText = currentSortDir === 'asc' ? '↑' : '↓';
            btnSortDir.addEventListener('click', () => {
                currentSortDir = currentSortDir === 'asc' ? 'desc' : 'asc';
                localStorage.setItem('airslice_sort_dir', currentSortDir);
                btnSortDir.innerText = currentSortDir === 'asc' ? '↑' : '↓';
                if (currentFolderData) {
                    renderGrid(currentFolderData);
                }
            });
        }
    }

    function getSortedItems(items) {
        if (!items || items.length === 0) return [];

        const dirMult = currentSortDir === 'asc' ? 1 : -1;

        return [...items].sort((a, b) => {
            if (!a && !b) return 0;
            if (!a) return 1;
            if (!b) return -1;

            // Rule 1: Always keep Directories first at top
            if (a.isDirectory && !b.isDirectory) return -1;
            if (!a.isDirectory && b.isDirectory) return 1;

            // Rule 2: Sort among same type (both directories or both files)
            let comp = 0;
            if (currentSortBy === 'name') {
                comp = (a.name || '').localeCompare(b.name || '', undefined, { numeric: true, sensitivity: 'base' });
            } else if (currentSortBy === 'modified') {
                comp = (a.lastModified || 0) - (b.lastModified || 0);
            } else if (currentSortBy === 'accessed') {
                const aTime = (a.lastAccessedMillis || a.lastModified) || 0;
                const bTime = (b.lastAccessedMillis || b.lastModified) || 0;
                comp = aTime - bTime;
            } else if (currentSortBy === 'size') {
                comp = (a.sizeBytes || 0) - (b.sizeBytes || 0);
            }

            return comp * dirMult;
        });
    }

    function getRelativeTimeString(epochMs) {
        if (!epochMs || epochMs <= 0) return 'recently';
        const now = Date.now();
        const diffSec = Math.floor((now - epochMs) / 1000);

        if (diffSec < 60) return 'just now';
        const diffMin = Math.floor(diffSec / 60);
        if (diffMin < 60) return `${diffMin}m ago`;
        const diffHours = Math.floor(diffMin / 60);
        if (diffHours < 24) return `${diffHours}h ago`;
        const diffDays = Math.floor(diffHours / 24);
        if (diffDays < 30) return `${diffDays}d ago`;

        const d = new Date(epochMs);
        return d.toLocaleDateString();
    }

    function isMediaFileName(name, mimeType) {
        if (!name) return false;
        const lowerName = name.toLowerCase();
        const lowerMime = (mimeType || '').toLowerCase();

        if (lowerMime.startsWith('image/') || lowerMime.startsWith('video/')) return true;

        const mediaExts = [
            '.jpg', '.jpeg', '.png', '.webp', '.gif', '.bmp', '.heic', '.heif',
            '.svg', '.ico', '.tiff', '.tif', '.dng',
            '.mp4', '.mkv', '.avi', '.mov', '.webm', '.3gp', '.ts', '.m4v'
        ];

        return mediaExts.some(ext => lowerName.endsWith(ext));
    }

    function handleToggleSelectAll() {
        if (!currentFolderItems || currentFolderItems.length === 0) return;

        if (selectedItemsMap.size === currentFolderItems.length) {
            clearAllSelections();
        } else {
            selectedItemsMap.clear();
            currentFolderItems.forEach(item => {
                selectedItemsMap.set(item.path, item);
            });
            updateBatchBar();
            if (currentFolderData) {
                renderGrid(currentFolderData);
            }
        }
    }

    function clearAllSelections() {
        selectedItemsMap.clear();
        updateBatchBar();
        if (currentFolderData) {
            renderGrid(currentFolderData);
        }
    }

    function updateBatchBar() {
        const batchBar = document.getElementById('batch-bar');
        const batchCount = document.getElementById('batch-count');
        const batchSize = document.getElementById('batch-size');
        const btnToggleSelectAll = document.getElementById('btn-toggle-select-all');
        const grid = document.getElementById('grid');

        const selectedCount = selectedItemsMap.size;

        if (selectedCount === 0) {
            if (batchBar) batchBar.classList.add('hidden');
            if (grid) grid.classList.remove('has-selection');
            if (btnToggleSelectAll) btnToggleSelectAll.innerText = '☑️ Select All';
            return;
        }

        if (batchBar) batchBar.classList.remove('hidden');
        if (grid) grid.classList.add('has-selection');

        if (btnToggleSelectAll) {
            if (selectedCount === currentFolderItems.length && currentFolderItems.length > 0) {
                btnToggleSelectAll.innerText = '☒ Deselect All';
            } else {
                btnToggleSelectAll.innerText = '☑️ Select All';
            }
        }

        let totalBytes = 0;
        selectedItemsMap.forEach((item) => {
            totalBytes += (item.sizeBytes || 0);
        });

        if (batchCount) batchCount.innerText = `${selectedCount} selected`;
        if (batchSize) batchSize.innerText = `(${formatBytes(totalBytes)})`;
    }

    function handleBatchZipDownload() {
        if (selectedItemsMap.size === 0) return;

        const paths = Array.from(selectedItemsMap.keys());
        const pathsParam = paths.map(p => 'paths=' + encodeURIComponent(p)).join('&');
        const url = `/download-zip?${pathsParam}`;

        showInfoToast(`📦 Streaming ZIP archive of ${selectedItemsMap.size} items... Please wait.`, 4000);

        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.setAttribute('download', '');
        document.body.appendChild(anchor);
        anchor.click();
        document.body.removeChild(anchor);
    }

    async function loadServerInfo() {
        const connBadge = document.getElementById('conn-badge');
        if (!connBadge) return;

        try {
            const res = await fetch('/api/server-info');
            if (res.ok) {
                const data = await res.json();
                if (data.connectionMode === 'HOTSPOT_DIRECT') {
                    connBadge.className = 'connection-badge connection-hotspot';
                    connBadge.innerText = '⚡ Hotspot Turbo';
                } else {
                    connBadge.className = 'connection-badge connection-wifi';
                    connBadge.innerText = '📶 Wi-Fi Router';
                }
            }
        } catch (err) {
            console.log('Failed to fetch server info:', err);
        }
    }

    async function handleRequestApproval() {
        const btn = document.getElementById('btn-request-approval');
        const statusText = document.getElementById('approval-status-text');
        const errorText = document.getElementById('login-error');

        if (btn) {
            btn.disabled = true;
            btn.innerText = 'Sending Request...';
        }

        if (errorText) errorText.innerText = '';
        if (statusText) {
            statusText.innerText = 'Waiting for 1-Tap approval on phone... ⏳';
            statusText.classList.remove('hidden');
        }

        try {
            const res = await fetch('/auth/request-approval', { method: 'POST' });
            if (res.ok) {
                const data = await res.json();
                if (data.success && data.requestId) {
                    pollApprovalStatus(data.requestId);
                } else {
                    resetApprovalButton('Failed to send request ❌');
                }
            } else {
                resetApprovalButton('Request failed ❌');
            }
        } catch (err) {
            resetApprovalButton('Connection error: ' + err.message);
        }
    }

    function pollApprovalStatus(requestId) {
        if (pollingTimer) clearInterval(pollingTimer);

        pollingTimer = setInterval(async () => {
            try {
                const res = await fetch(`/auth/check-approval?requestId=${encodeURIComponent(requestId)}`);
                if (res.ok) {
                    const data = await res.json();
                    const statusText = document.getElementById('approval-status-text');

                    if (data.status === 'APPROVED') {
                        clearInterval(pollingTimer);
                        pollingTimer = null;

                        if (statusText) statusText.innerText = 'Connection Approved! ✨';

                        setTimeout(async () => {
                            const modal = document.getElementById('login-modal');
                            if (modal) modal.classList.add('hidden');

                            await loadServerInfo();
                            await loadStorageStats();
                            await loadFolder(currentPath);
                        }, 500);

                    } else if (data.status === 'DENIED') {
                        clearInterval(pollingTimer);
                        pollingTimer = null;
                        resetApprovalButton('Connection declined on phone ❌');
                    }
                }
            } catch (err) {
                console.log('Poll error:', err);
            }
        }, 1000);
    }

    function resetApprovalButton(message) {
        const btn = document.getElementById('btn-request-approval');
        const statusText = document.getElementById('approval-status-text');

        if (btn) {
            btn.disabled = false;
            btn.innerText = '📱 Request 1-Tap Approval on Phone';
        }

        if (statusText && message) {
            statusText.innerText = message;
            statusText.classList.remove('hidden');
        }
    }

    function initLightbox() {
        const lightboxModal = document.getElementById('lightbox-modal');
        const btnClose = document.getElementById('lb-btn-close');
        const btnPrevImg = document.getElementById('lb-btn-prev-img');
        const btnNextImg = document.getElementById('lb-btn-next-img');

        if (btnClose) {
            btnClose.addEventListener('click', () => closeLightboxModal());
        }

        if (btnPrevImg) {
            btnPrevImg.addEventListener('click', (e) => {
                e.stopPropagation();
                showPreviousImage();
            });
        }

        if (btnNextImg) {
            btnNextImg.addEventListener('click', (e) => {
                e.stopPropagation();
                showNextImage();
            });
        }

        if (lightboxModal) {
            lightboxModal.addEventListener('click', (e) => {
                if (e.target === lightboxModal) {
                    closeLightboxModal();
                }
            });
        }

        window.addEventListener('keydown', (e) => {
            const lightboxModal = document.getElementById('lightbox-modal');
            if (!lightboxModal || lightboxModal.classList.contains('hidden')) return;

            if (e.key === 'Escape') {
                closeLightboxModal();
            } else if (e.key === 'ArrowLeft') {
                showPreviousImage();
            } else if (e.key === 'ArrowRight') {
                showNextImage();
            }
        });
    }

    function showNextImage() {
        if (currentImagesList.length <= 1 || currentImageIndex < 0) return;
        if (currentImageIndex < currentImagesList.length - 1) {
            openFilePreviewModal(currentImagesList[currentImageIndex + 1]);
        } else {
            openFilePreviewModal(currentImagesList[0]);
        }
    }

    function showPreviousImage() {
        if (currentImagesList.length <= 1 || currentImageIndex < 0) return;
        if (currentImageIndex > 0) {
            openFilePreviewModal(currentImagesList[currentImageIndex - 1]);
        } else {
            openFilePreviewModal(currentImagesList[currentImagesList.length - 1]);
        }
    }

    function closeLightboxModal() {
        const lightboxModal = document.getElementById('lightbox-modal');
        const videoPlayer = document.getElementById('lb-video-player');
        const audioPlayer = document.getElementById('lb-audio-player');
        const imagePlayer = document.getElementById('lb-image-player');
        const graphicThumb = document.getElementById('lb-graphic-thumb');
        const btnPrevImg = document.getElementById('lb-btn-prev-img');
        const btnNextImg = document.getElementById('lb-btn-next-img');
        const lbImgCounter = document.getElementById('lb-img-counter');

        if (videoPlayer) {
            videoPlayer.pause();
            videoPlayer.removeAttribute('src');
            videoPlayer.load();
        }

        if (audioPlayer) {
            audioPlayer.pause();
            audioPlayer.removeAttribute('src');
            audioPlayer.load();
        }

        if (imagePlayer) {
            imagePlayer.src = '';
        }

        if (graphicThumb) {
            graphicThumb.src = '';
        }

        if (btnPrevImg) btnPrevImg.classList.add('hidden');
        if (btnNextImg) btnNextImg.classList.add('hidden');
        if (lbImgCounter) lbImgCounter.classList.add('hidden');

        if (lightboxModal) {
            lightboxModal.classList.add('hidden');
        }
    }

    function openFilePreviewModal(item) {
        const lightboxModal = document.getElementById('lightbox-modal');
        const lbTitle = document.getElementById('lb-title');
        const lbIcon = document.getElementById('lb-icon');
        const lbBtnDownload = document.getElementById('lb-btn-download');
        const lbBtnNewTab = document.getElementById('lb-btn-newtab');
        const lbImgCounter = document.getElementById('lb-img-counter');

        const btnPrevImg = document.getElementById('lb-btn-prev-img');
        const btnNextImg = document.getElementById('lb-btn-next-img');

        const theaterLayout = document.getElementById('lb-theater-layout');
        const splitLayout = document.getElementById('lb-split-layout');

        const videoPlayer = document.getElementById('lb-video-player');
        const imagePlayer = document.getElementById('lb-image-player');

        const metaName = document.getElementById('lb-meta-name');
        const metaSize = document.getElementById('lb-meta-size');
        const metaType = document.getElementById('lb-meta-type');
        const metaDate = document.getElementById('lb-meta-date');
        const metaPath = document.getElementById('lb-meta-path');
        const splitDownloadBtn = document.getElementById('lb-split-download-btn');

        const graphicIcon = document.getElementById('lb-graphic-icon');
        const graphicThumb = document.getElementById('lb-graphic-thumb');
        const audioContainer = document.getElementById('lb-audio-container');
        const audioPlayer = document.getElementById('lb-audio-player');

        if (!lightboxModal) return;

        // Reset previous media state
        closeLightboxModal();

        const fileIcon = getFileIconEmoji(item);
        if (lbIcon) lbIcon.innerText = fileIcon;
        if (lbTitle) lbTitle.innerText = item.name;

        const downloadUrl = `/api/download?path=${encodeURIComponent(item.path)}`;
        const inlineViewUrl = `/api/view?path=${encodeURIComponent(item.path)}`;
        const videoStreamUrl = `/api/stream?path=${encodeURIComponent(item.path)}`;

        if (lbBtnDownload) lbBtnDownload.href = downloadUrl;

        // Check if file can be opened/viewed inline natively in browser
        const inlineAction = getInlineViewAction(item);
        if (inlineAction && lbBtnNewTab) {
            lbBtnNewTab.innerText = inlineAction.label;
            lbBtnNewTab.href = inlineAction.url;
            lbBtnNewTab.classList.remove('hidden');
        } else if (lbBtnNewTab) {
            lbBtnNewTab.classList.add('hidden');
        }

        // Layout Selection
        if (item.isMediaVideo) {
            // Case A: Video Theater Layout
            theaterLayout.classList.remove('hidden');
            splitLayout.classList.add('hidden');

            if (lbBtnDownload) lbBtnDownload.classList.remove('hidden');

            videoPlayer.classList.remove('hidden');
            imagePlayer.classList.add('hidden');

            videoPlayer.src = videoStreamUrl;
            videoPlayer.play().catch(e => console.log('Autoplay deferred:', e));

        } else if (item.isMediaImage) {
            // Case B: Image Theater Layout with Next / Previous Image Navigation
            theaterLayout.classList.remove('hidden');
            splitLayout.classList.add('hidden');

            if (lbBtnDownload) lbBtnDownload.classList.remove('hidden');

            imagePlayer.classList.remove('hidden');
            videoPlayer.classList.add('hidden');

            imagePlayer.src = inlineViewUrl;

            // Setup Image Next / Previous List & Index
            currentImagesList = currentFolderItems.filter(i => i.isMediaImage);
            currentImageIndex = currentImagesList.findIndex(i => i.path === item.path);

            if (currentImagesList.length > 1) {
                if (btnPrevImg) btnPrevImg.classList.remove('hidden');
                if (btnNextImg) btnNextImg.classList.remove('hidden');
                if (lbImgCounter) {
                    lbImgCounter.innerText = `${currentImageIndex + 1} / ${currentImagesList.length}`;
                    lbImgCounter.classList.remove('hidden');
                }
            } else if (lbImgCounter) {
                lbImgCounter.innerText = `1 / 1`;
                lbImgCounter.classList.remove('hidden');
            }

        } else {
            // Case C: Split Metadata Layout for Documents, Audio, Archives, Code
            theaterLayout.classList.add('hidden');
            splitLayout.classList.remove('hidden');

            // Hide top header download button to avoid duplicate button in split view
            if (lbBtnDownload) lbBtnDownload.classList.add('hidden');

            // Metadata Panel
            if (metaName) metaName.innerText = item.name;
            if (metaSize) metaSize.innerText = `${item.formattedSize} (${item.sizeBytes.toLocaleString()} bytes)`;
            if (metaType) metaType.innerText = item.mimeType || 'application/octet-stream';
            if (metaDate) metaDate.innerText = item.formattedDate;
            if (metaPath) metaPath.innerText = item.path;
            if (splitDownloadBtn) splitDownloadBtn.href = downloadUrl;

            // Graphic / Audio / Thumbnail Panel
            const isAudio = isAudioFile(item);

            if (isAudio) {
                graphicIcon.innerText = '🎵';
                graphicIcon.classList.remove('hidden');
                graphicThumb.classList.add('hidden');
                audioContainer.classList.remove('hidden');

                if (audioPlayer) {
                    audioPlayer.src = inlineViewUrl;
                    audioPlayer.play().catch(e => console.log('Audio autoplay deferred:', e));
                }
            } else {
                audioContainer.classList.add('hidden');

                // Try rendering thumbnail with native async decoding
                const thumbUrl = `/api/thumbnail?path=${encodeURIComponent(item.path)}`;
                graphicThumb.loading = 'lazy';
                graphicThumb.decoding = 'async';
                graphicThumb.src = thumbUrl;
                graphicThumb.onload = () => {
                    graphicThumb.classList.remove('hidden');
                    graphicIcon.classList.add('hidden');
                };
                graphicThumb.onerror = () => {
                    graphicThumb.classList.add('hidden');
                    graphicIcon.innerText = fileIcon;
                    graphicIcon.classList.remove('hidden');
                };
            }
        }

        lightboxModal.classList.remove('hidden');
    }

    function getInlineViewAction(item) {
        if (!item || !item.name) return null;
        const name = item.name.toLowerCase();
        const mime = (item.mimeType || '').toLowerCase();

        if (item.isMediaImage || mime.startsWith('image/')) {
            return { label: '🖼️ View Full Image', url: `/api/view?path=${encodeURIComponent(item.path)}` };
        }
        if (item.isMediaVideo || mime.startsWith('video/')) {
            return { label: '🎬 Open Video Stream', url: `/api/stream?path=${encodeURIComponent(item.path)}` };
        }
        if (isAudioFile(item)) {
            return { label: '🎵 Open Audio Track', url: `/api/view?path=${encodeURIComponent(item.path)}` };
        }
        if (name.endsWith('.pdf') || mime === 'application/pdf') {
            return { label: '📄 Open PDF', url: `/api/view?path=${encodeURIComponent(item.path)}` };
        }
        if (mime.startsWith('text/') || name.endsWith('.txt') || name.endsWith('.log') ||
            name.endsWith('.md') || name.endsWith('.json') || name.endsWith('.html') ||
            name.endsWith('.css') || name.endsWith('.js') || name.endsWith('.kt') ||
            name.endsWith('.java') || name.endsWith('.py') || name.endsWith('.xml') || name.endsWith('.csv')) {
            return { label: '📝 View Text File', url: `/api/view?path=${encodeURIComponent(item.path)}` };
        }

        return null;
    }

    function triggerZipDownload(folderPath) {
        showInfoToast('📦 Streaming ZIP archive download... Please wait.', 4000);
        const url = folderPath ? `/download-zip?paths=${encodeURIComponent(folderPath)}` : '/download-zip?paths=';

        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.setAttribute('download', '');
        document.body.appendChild(anchor);
        anchor.click();
        document.body.removeChild(anchor);
    }

    function showInfoToast(message, durationMs = 3000) {
        const toast = document.getElementById('info-toast');
        const infoText = document.getElementById('info-text');
        if (toast && infoText) {
            infoText.innerText = message;
            toast.classList.remove('hidden');
            setTimeout(() => {
                toast.classList.add('hidden');
            }, durationMs);
        }
    }

    function initDragAndDrop() {
        const dragOverlay = document.getElementById('drag-overlay');

        window.addEventListener('dragenter', (e) => {
            e.preventDefault();
            dragCounter++;
            if (dragOverlay) dragOverlay.classList.remove('hidden');
        });

        window.addEventListener('dragover', (e) => {
            e.preventDefault();
        });

        window.addEventListener('dragleave', (e) => {
            e.preventDefault();
            dragCounter--;
            if (dragCounter <= 0 && dragOverlay) {
                dragOverlay.classList.add('hidden');
                dragCounter = 0;
            }
        });

        window.addEventListener('drop', (e) => {
            e.preventDefault();
            dragCounter = 0;
            if (dragOverlay) dragOverlay.classList.add('hidden');

            const files = e.dataTransfer ? e.dataTransfer.files : null;
            if (files && files.length > 0) {
                handleFilesUploadQueue(files);
            }
        });
    }

    async function handleFilesUploadQueue(files) {
        for (let i = 0; i < files.length; i++) {
            await uploadSingleFile(files[i]);
        }
        await loadStorageStats();
        await loadFolder(currentPath);
    }

    function uploadSingleFile(file) {
        return new Promise((resolve) => {
            const toast = document.getElementById('upload-toast');
            const filenameEl = document.getElementById('upload-filename');
            const speedEl = document.getElementById('upload-speed');
            const barEl = document.getElementById('upload-bar');

            if (toast) toast.classList.remove('hidden');
            if (filenameEl) filenameEl.innerText = file.name;
            if (barEl) barEl.style.width = '0%';
            if (speedEl) speedEl.innerText = 'Starting upload...';

            const xhr = new XMLHttpRequest();
            const targetDirParam = currentPath ? `&dir=${encodeURIComponent(currentPath)}` : '';
            const uploadUrl = `/api/upload?filename=${encodeURIComponent(file.name)}${targetDirParam}`;

            const startTime = Date.now();
            let lastLoaded = 0;
            let lastTime = startTime;

            xhr.upload.onprogress = (e) => {
                if (e.lengthComputable && e.total > 0) {
                    const now = Date.now();
                    const totalElapsedSec = Math.max(0.1, (now - startTime) / 1000);
                    const stepElapsedSec = Math.max(0.1, (now - lastTime) / 1000);

                    // Calculate instant and average transfer speed
                    const stepBytes = e.loaded - lastLoaded;
                    const instantSpeed = stepBytes / stepElapsedSec;
                    const avgSpeed = e.loaded / totalElapsedSec;

                    // Smooth speed metric
                    const currentSpeed = (instantSpeed * 0.7) + (avgSpeed * 0.3);

                    lastLoaded = e.loaded;
                    lastTime = now;

                    const percent = Math.round((e.loaded / e.total) * 100);

                    // Speed formatting
                    let speedText = '';
                    if (currentSpeed >= 1024 * 1024) {
                        speedText = `${(currentSpeed / (1024 * 1024)).toFixed(1)} MB/s`;
                    } else {
                        speedText = `${Math.round(currentSpeed / 1024)} KB/s`;
                    }

                    // ETA formatting
                    const remainingBytes = e.total - e.loaded;
                    let etaText = '';
                    if (currentSpeed > 0) {
                        const etaSec = Math.ceil(remainingBytes / currentSpeed);
                        if (etaSec < 60) {
                            etaText = `ETA: ${etaSec}s`;
                        } else {
                            const mins = Math.floor(etaSec / 60);
                            etaText = `ETA: ${mins}m ${etaSec % 60}s`;
                        }
                    }

                    if (barEl) barEl.style.width = `${percent}%`;
                    if (speedEl) speedEl.innerText = `${speedText} • ${percent}% • ${etaText}`;
                }
            };

            xhr.onload = () => {
                if (barEl) barEl.style.width = '100%';
                if (speedEl) speedEl.innerText = 'Upload complete! ✨';
                setTimeout(() => {
                    if (toast) toast.classList.add('hidden');
                    resolve();
                }, 1000);
            };

            xhr.onerror = () => {
                if (speedEl) speedEl.innerText = 'Upload failed ❌';
                setTimeout(() => {
                    if (toast) toast.classList.add('hidden');
                    resolve();
                }, 2000);
            };

            xhr.open('POST', uploadUrl, true);
            xhr.send(file);
        });
    }

    async function loadInitialData() {
        await loadServerInfo();
        await loadStorageStats();
        await loadFolder(null);
    }

    async function loadStorageStats() {
        try {
            const res = await fetch('/api/storage');
            if (res.status === 401) return;

            const data = await res.json();
            const storageText = document.getElementById('storage-text');
            const storageBar = document.getElementById('storage-bar');

            if (storageText && storageBar) {
                storageText.innerText = `Free: ${data.formattedFree} / Total: ${data.formattedTotal}`;
                storageBar.style.width = `${data.usedPercentage}%`;
            }
        } catch (err) {
            console.error('Failed to load storage stats:', err);
        }
    }

    async function loadFolder(targetPath) {
        // Clear selection on folder navigation
        selectedItemsMap.clear();
        updateBatchBar();

        if (activeFolderAbortController) {
            activeFolderAbortController.abort();
        }
        activeFolderAbortController = new AbortController();
        const signal = activeFolderAbortController.signal;

        const grid = document.getElementById('grid');
        const itemCount = document.getElementById('item-count');
        const modal = document.getElementById('login-modal');

        if (grid) {
            grid.innerHTML = '<div class="loading-spinner">Scanning folder content...</div>';
        }

        const url = targetPath
            ? `/api/files?path=${encodeURIComponent(targetPath)}`
            : '/api/files';

        try {
            const res = await fetch(url, { signal });

            if (res.status === 401) {
                if (modal) modal.classList.remove('hidden');
                return;
            }

            if (modal) modal.classList.add('hidden');

            const data = await res.json();
            currentPath = data.currentPath;
            currentFolderData = data;
            currentFolderItems = data.items || [];

            updateBreadcrumbs(data.currentPath);

            if (itemCount) {
                itemCount.innerText = `${data.totalItems} items`;
            }

            renderGrid(data);

        } catch (err) {
            if (err.name === 'AbortError') return;
            if (grid) {
                grid.innerHTML = `<div class="loading-spinner" style="color: var(--accent-error);">Failed to load folder: ${err.message}</div>`;
            }
        }
    }

    function renderGrid(data) {
        const grid = document.getElementById('grid');
        if (!grid) return;

        grid.innerHTML = '';

        // Filter items if search query exists
        let itemsToRender = data.items || [];
        if (searchQuery) {
            itemsToRender = itemsToRender.filter(item =>
                (item.name || '').toLowerCase().includes(searchQuery)
            );
        }

        // Apply Client-Side Sorting (< 1ms zero-latency)
        itemsToRender = getSortedItems(itemsToRender);

        // Render Parent Folder Link if available and not searching
        if (data.parentPath && !searchQuery) {
            const parentCard = document.createElement('div');
            parentCard.className = 'item-card';
            parentCard.innerHTML = `
                <div class="thumb-wrapper">
                    <span class="icon-large">📁</span>
                </div>
                <div class="item-title">.. (Parent Folder)</div>
                <div class="item-sub">Directory</div>
            `;
            parentCard.addEventListener('click', () => loadFolder(data.parentPath));
            grid.appendChild(parentCard);
        }

        if (itemsToRender.length === 0) {
            grid.innerHTML = searchQuery
                ? `<div class="loading-spinner">No files matching "${escapeHtml(searchQuery)}"</div>`
                : '<div class="loading-spinner">This folder is empty</div>';
            return;
        }

        // Render Folder & File Items
        itemsToRender.forEach((item) => {
            const card = document.createElement('div');
            const isSelected = selectedItemsMap.has(item.path);
            card.className = isSelected ? 'item-card selected-card' : 'item-card';

            const fileIcon = getFileIconEmoji(item);
            let previewHtml = '';

            const isMedia = item.isMediaImage || item.isMediaVideo || isMediaFileName(item.name, item.mimeType);

            if (item.isDirectory) {
                previewHtml = '<span class="icon-large">📁</span>';
            } else if (isMedia) {
                const thumbUrl = `/api/thumbnail?path=${encodeURIComponent(item.path)}`;
                previewHtml = `<img class="thumb-img" src="${thumbUrl}" loading="lazy" decoding="async" alt="thumb" onerror="this.outerHTML='<span class=\\'icon-large\\'>${fileIcon}</span>'">`;
            } else {
                previewHtml = `<span class="icon-large">${fileIcon}</span>`;
            }

            let actionBtnHtml = '';
            if (item.isDirectory) {
                actionBtnHtml = `<a class="btn-download btn-zip-item" href="#">📦 ZIP</a>`;
            } else {
                actionBtnHtml = `
                    <button class="btn-download btn-preview-item">👁️ View</button>
                    <a class="btn-download btn-icon-only btn-file-item" href="/api/download?path=${encodeURIComponent(item.path)}" target="_blank" title="1-Click Download">📥</a>
                `;
            }

            // Dynamic Subtitle Based on Sort Selection
            let subtitleText = '';
            if (currentSortBy === 'accessed') {
                const relativeTime = getRelativeTimeString(item.lastAccessedMillis || item.lastModified);
                subtitleText = item.isDirectory ? `Opened ${relativeTime}` : `${item.formattedSize} • Opened ${relativeTime}`;
            } else if (currentSortBy === 'modified') {
                subtitleText = item.isDirectory ? `Modified ${item.formattedDate}` : `${item.formattedSize} • ${item.formattedDate}`;
            } else if (currentSortBy === 'size') {
                subtitleText = item.isDirectory ? `Folder` : `${item.formattedSize} • ${item.formattedDate}`;
            } else {
                subtitleText = item.isDirectory ? `Folder` : `${item.formattedSize} • ${item.formattedDate}`;
            }

            card.innerHTML = `
                <input type="checkbox" class="card-checkbox" ${isSelected ? 'checked' : ''}>
                <div class="thumb-wrapper">${previewHtml}</div>
                <div class="item-title" title="${escapeHtml(item.name)}">${escapeHtml(item.name)}</div>
                <div class="item-sub">${escapeHtml(subtitleText)}</div>
                <div class="item-actions-row">${actionBtnHtml}</div>
            `;

            // Checkbox Handler
            const checkbox = card.querySelector('.card-checkbox');
            if (checkbox) {
                checkbox.addEventListener('click', (e) => {
                    e.stopPropagation();
                    if (checkbox.checked) {
                        selectedItemsMap.set(item.path, item);
                        card.classList.add('selected-card');
                    } else {
                        selectedItemsMap.delete(item.path);
                        card.classList.remove('selected-card');
                    }
                    updateBatchBar();
                });
            }

            // Card Click Handler
            card.addEventListener('click', () => {
                if (item.isDirectory) {
                    loadFolder(item.path);
                } else {
                    openFilePreviewModal(item);
                }
            });

            const zipItemBtn = card.querySelector('.btn-zip-item');
            if (zipItemBtn) {
                zipItemBtn.addEventListener('click', (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    triggerZipDownload(item.path);
                });
            }

            const previewItemBtn = card.querySelector('.btn-preview-item');
            if (previewItemBtn) {
                previewItemBtn.addEventListener('click', (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    openFilePreviewModal(item);
                });
            }

            const fileItemBtn = card.querySelector('.btn-file-item');
            if (fileItemBtn) {
                fileItemBtn.addEventListener('click', (e) => {
                    e.stopPropagation();
                });
            }

            grid.appendChild(card);
        });
    }

    function formatBytes(size) {
        if (!size || size <= 0) return '0 B';
        const units = ['B', 'KB', 'MB', 'GB', 'TB'];
        const i = Math.floor(Math.log(size) / Math.log(1024));
        return (size / Math.pow(1024, i)).toFixed(1) + ' ' + units[i];
    }

    function updateBreadcrumbs(fullPath) {
        const container = document.getElementById('breadcrumbs');
        if (!container || !fullPath) return;

        container.innerHTML = '';

        const parts = fullPath.split('/').filter(p => p.length > 0);
        let accumulatedPath = '';

        const homeCrumb = document.createElement('span');
        homeCrumb.className = parts.length === 0 ? 'crumb active' : 'crumb';
        homeCrumb.innerText = 'Storage Home';
        homeCrumb.addEventListener('click', () => loadFolder(null));
        container.appendChild(homeCrumb);

        parts.forEach((part, index) => {
            accumulatedPath += '/' + part;

            if (accumulatedPath === '/storage' || accumulatedPath === '/storage/emulated') {
                return;
            }

            const separator = document.createElement('span');
            separator.className = 'crumb-separator';
            separator.innerText = '/';
            container.appendChild(separator);

            const crumb = document.createElement('span');
            const isLast = (index === parts.length - 1);
            crumb.className = isLast ? 'crumb active' : 'crumb';

            let displayName = part;
            if (accumulatedPath === '/storage/emulated/0') {
                displayName = 'Internal Storage';
            }

            crumb.innerText = displayName;

            const targetPath = accumulatedPath;
            if (!isLast) {
                crumb.addEventListener('click', () => loadFolder(targetPath));
            }

            container.appendChild(crumb);
        });
    }

    async function handlePinLogin() {
        const pinInput = document.getElementById('pin-input');
        const loginError = document.getElementById('login-error');
        if (!pinInput || !loginError) return;

        const pin = pinInput.value.trim();
        if (pin.length !== 4) {
            loginError.innerText = 'Please enter a 4-digit PIN code.';
            return;
        }

        loginError.innerText = 'Authenticating...';

        try {
            const res = await fetch('/api/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: `pin=${encodeURIComponent(pin)}`
            });

            const data = await res.json();
            if (data.success) {
                loginError.innerText = '';
                pinInput.value = '';
                const modal = document.getElementById('login-modal');
                if (modal) modal.classList.add('hidden');

                await loadServerInfo();
                await loadStorageStats();
                await loadFolder(currentPath);
            } else {
                loginError.innerText = data.error || 'Invalid 4-digit PIN code.';
                pinInput.value = '';
                pinInput.focus();
            }
        } catch (err) {
            loginError.innerText = 'Connection error: ' + err.message;
        }
    }

    function isAudioFile(item) {
        if (!item || !item.name) return false;
        const name = item.name.toLowerCase();
        const mime = (item.mimeType || '').toLowerCase();
        return mime.startsWith('audio/') ||
            name.endsWith('.mp3') || name.endsWith('.wav') ||
            name.endsWith('.m4a') || name.endsWith('.aac') ||
            name.endsWith('.flac') || name.endsWith('.ogg');
    }

    function getFileIconEmoji(item) {
        if (item.isDirectory) return '📁';
        if (item.isMediaImage) return '🖼️';
        if (item.isMediaVideo) return '🎬';
        if (isAudioFile(item)) return '🎵';

        const name = item.name.toLowerCase();
        if (name.endsWith('.pdf')) return '📑';
        if (name.endsWith('.zip') || name.endsWith('.rar') || name.endsWith('.7z') || name.endsWith('.tar') || name.endsWith('.gz')) return '📦';
        if (name.endsWith('.apk')) return '⚙️';
        if (name.endsWith('.txt') || name.endsWith('.log') || name.endsWith('.md')) return '📝';
        if (name.endsWith('.json') || name.endsWith('.html') || name.endsWith('.css') || name.endsWith('.js') || name.endsWith('.kt') || name.endsWith('.java') || name.endsWith('.py')) return '💻';
        return '📄';
    }

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }
})();
