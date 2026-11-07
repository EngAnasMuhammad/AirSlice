(function () {
    // Internationalization (i18n) Translations Dictionary
    const I18N = {
        en: {
            brandSubtitle: 'Cloud',
            wifiRouter: '📶 Wi-Fi Router',
            hotspotTurbo: '⚡ Hotspot Turbo',
            checking: '📶 Checking...',
            upload: '📤 Upload',
            downloadFolder: '📦 Download Folder',
            downloadFolderTooltip: 'Download current folder as a compressed ZIP file',
            searchPlaceholder: '🔍 Filter files in current folder...',
            storageLoading: 'Loading Storage...',
            storageFormat: (free, total) => `Free: ${free} / Total: ${total}`,
            storageHome: 'Storage Home',
            internalStorage: 'Internal Storage',
            parentFolder: '.. (Parent Folder)',
            directory: 'Directory',
            folder: 'Folder',
            emptyFolder: 'This folder is empty',
            noMatchQuery: (q) => `No files matching "${q}"`,
            scanningFolder: 'Scanning folder content...',
            sortName: 'Sort by Name',
            sortModified: 'Sort by Modified',
            sortAccessed: 'Sort by Accessed',
            sortSize: 'Sort by Size',
            selectAll: '☑️ Select All',
            deselectAll: '☒ Deselect All',
            itemsBadge: (n) => `${n} items`,
            selectedBadge: (n) => `${n} selected`,
            downloadSelectedZip: '📦 Download Selected as ZIP',
            clearSelection: '✕ Clear',
            view: '👁️ View',
            download: '📥',
            downloadFile: '📥 Download File',
            confirmDownloadZipTitle: '📦 Download Folder as ZIP?',
            confirmDownloadZipPrompt: (name) => `Are you sure you want to download <strong>"${name}"</strong>?<br><br><div class="confirm-warning-box">⚠️ <strong>Folder Archive:</strong> All files inside this folder will be packaged into a single ZIP file. If this folder contains large videos or photos, this download may be large and take time.</div>`,
            cancel: 'Cancel',
            downloadZipBtn: 'Download ZIP',
            preparingDownloadToast: '📦 Preparing download...',
            streamingZipToast: '📦 Streaming ZIP archive download... Please wait.',
            streamingBatchToast: (n) => `📦 Streaming ZIP archive of ${n} items... Please wait.`,
            uploadingFile: 'Uploading file...',
            startingUpload: 'Starting upload...',
            uploadComplete: 'Upload complete! ✨',
            uploadFailed: 'Upload failed ❌',
            dropOverlayTitle: 'Drop files here to upload',
            dropOverlaySubtitle: 'Files stream directly into mobile flash storage at maximum Wi-Fi speed',
            pinTitle: '🔐 AirSlice Security Pairing',
            pinSubtitle: 'Request 1-Tap approval on phone OR enter the 4-digit PIN code displayed on screen.',
            requestApproval: '📱 Request 1-Tap Approval on Phone',
            sendingRequest: 'Sending Request...',
            waitingApproval: 'Waiting for 1-Tap approval on phone... ⏳',
            approved: 'Connection Approved! ✨',
            declined: 'Connection declined on phone ❌',
            submitPin: 'Submit PIN',
            enter4DigitPin: 'Please enter a 4-digit PIN code.',
            invalidPin: 'Invalid 4-digit PIN code.',
            authenticating: 'Authenticating...',
            openTab: '↗ Open Tab',
            filePreview: 'File Preview',
            downloadBtnText: '📥 Download',
            footerInfo: 'AirSlice Mobile Cloud Storage • Fast Direct Transfer',
            metaHeading: 'File Metadata & Info',
            metaName: 'File Name:',
            metaSize: 'Exact Size:',
            metaMime: 'MIME Type:',
            metaModified: 'Last Modified:',
            metaPath: 'Storage Path:',
            audioTrackStream: '🎵 Audio Track Stream',
            openedAgo: (t) => `Opened ${t}`,
            modifiedOn: (d) => `Modified ${d}`,
            justNow: 'just now',
            minutesAgo: (m) => `${m}m ago`,
            hoursAgo: (h) => `${h}h ago`,
            daysAgo: (d) => `${d}d ago`,
            recently: 'recently'
        },
        ar: {
            brandSubtitle: 'سحابي',
            wifiRouter: '📶 راوتر Wi-Fi',
            hotspotTurbo: '⚡ نقطة اتصال سريعة',
            checking: '📶 جارٍ الفحص...',
            upload: '📤 رفع',
            downloadFolder: '📦 تحميل المجلد',
            downloadFolderTooltip: 'تحميل المجلد الحالي كملف مضغوط ZIP',
            searchPlaceholder: '🔍 ابحث في المجلد الحالي...',
            storageLoading: 'جارٍ تحميل السعة التخزينية...',
            storageFormat: (free, total) => `المتاح: ${free} / الإجمالي: ${total}`,
            storageHome: 'الرئيسية',
            internalStorage: 'وحدة التخزين الداخلية',
            parentFolder: '.. (المجلد السابق)',
            directory: 'مجلد',
            folder: 'مجلد',
            emptyFolder: 'هذا المجلد فارغ',
            noMatchQuery: (q) => `لا توجد ملفات تطابق "${q}"`,
            scanningFolder: 'جارٍ قراءة محتوى المجلد...',
            sortName: 'الترتيب بالاسم',
            sortModified: 'الترتيب بتاريخ التعديل',
            sortAccessed: 'الترتيب بآخر فتح',
            sortSize: 'الترتيب بالحجم',
            selectAll: '☑️ تحديد الكل',
            deselectAll: '☒ إلغاء تحديد الكل',
            itemsBadge: (n) => `${n} عناصر`,
            selectedBadge: (n) => `${n} محدد`,
            downloadSelectedZip: '📦 تحميل المحدد كملف ZIP',
            clearSelection: '✕ إلغاء',
            view: '👁️ عرض',
            download: '📥',
            downloadFile: '📥 تحميل الملف',
            confirmDownloadZipTitle: '📦 تحميل المجلد كملف ZIP؟',
            confirmDownloadZipPrompt: (name) => `هل أنت متأكد من رغبتك في تحميل <strong>"${name}"</strong>؟<br><br><div class="confirm-warning-box">⚠️ <strong>أرشيف المجلد:</strong> سيتم تجميع كافة الملفات داخل هذا المجلد في ملف مضغوط ZIP واحد. إذا كان المجلد يحتوي على صور أو فيديوهات كبيرة، فقد يستغرق التحميل وقتاً.</div>`,
            cancel: 'إلغاء',
            downloadZipBtn: 'تحميل ZIP',
            preparingDownloadToast: '📦 جارٍ تحضير التحميل...',
            streamingZipToast: '📦 جارٍ ضغط وبث ملف ZIP... يرجى الانتظار.',
            streamingBatchToast: (n) => `📦 جارٍ ضغط وبث ${n} عناصر كملف ZIP... يرجى الانتظار.`,
            uploadingFile: 'جارٍ رفع الملف...',
            startingUpload: 'بدء الرفع...',
            uploadComplete: 'تم الرفع بنجاح! ✨',
            uploadFailed: 'فشل الرفع ❌',
            dropOverlayTitle: 'أفلت الملفات هنا للرفع',
            dropOverlaySubtitle: 'يتم نقل الملفات مباشرة إلى ذاكرة الهاتف بأقصى سرعة Wi-Fi',
            pinTitle: '🔐 اقتران الأمان لـ AirSlice',
            pinSubtitle: 'اطلب الموافقة بضغطة واحدة من الهاتف أو أدخل رمز PIN المكون من 4 أرقام.',
            requestApproval: '📱 طلب الموافقة بضغطة واحدة على الهاتف',
            sendingRequest: 'جارٍ إرسال الطلب...',
            waitingApproval: 'في انتظار الموافقة من الهاتف... ⏳',
            approved: 'تمت الموافقة على الاتصال! ✨',
            declined: 'تم رفض الاتصال من الهاتف ❌',
            submitPin: 'تأكيد الرمز',
            enter4DigitPin: 'يرجى إدخال رمز PIN المكون من 4 أرقام.',
            invalidPin: 'رمز PIN غير صحيح.',
            authenticating: 'جارٍ التحقق...',
            openTab: '↗ فتح في نافذة',
            filePreview: 'معاينة الملف',
            downloadBtnText: '📥 تنزيل',
            footerInfo: 'خادم التخزين السحابي المحمول AirSlice • نقل مباشر وسريع',
            metaHeading: 'معلومات وتفاصيل الملف',
            metaName: 'اسم الملف:',
            metaSize: 'الحجم الدقيق:',
            metaMime: 'نوع الملف:',
            metaModified: 'تاريخ التعديل:',
            metaPath: 'مسار التخزين:',
            audioTrackStream: '🎵 تشغيل المقطع الصوتي',
            openedAgo: (t) => `تم الفتح ${t}`,
            modifiedOn: (d) => `عُدّل ${d}`,
            justNow: 'الآن',
            minutesAgo: (m) => `منذ ${m} دقيقة`,
            hoursAgo: (h) => `منذ ${h} ساعة`,
            daysAgo: (d) => `منذ ${d} يوم`,
            recently: 'مؤخراً'
        }
    };

    let currentLang = 'en';
    let t = I18N.en;

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
    let pendingZipAction = null; // Holds callback for confirmed ZIP download

    function applyLanguage(langTag) {
        currentLang = (langTag === 'ar') ? 'ar' : 'en';
        t = I18N[currentLang];

        const isRtl = (currentLang === 'ar');
        document.documentElement.lang = currentLang;
        document.documentElement.dir = isRtl ? 'rtl' : 'ltr';

        // Translate static navbar buttons and placeholders
        const btnUploadFile = document.getElementById('btn-upload-file');
        const btnZipCurrent = document.getElementById('btn-zip-current');
        const searchInput = document.getElementById('search-input');
        const btnToggleSelectAll = document.getElementById('btn-toggle-select-all');
        const btnBatchDownloadZip = document.getElementById('btn-batch-download-zip');
        const btnBatchClear = document.getElementById('btn-batch-clear');
        const sortSelect = document.getElementById('sort-select');

        if (btnUploadFile) btnUploadFile.innerText = t.upload;
        if (btnZipCurrent) {
            btnZipCurrent.innerText = t.downloadFolder;
            btnZipCurrent.title = t.downloadFolderTooltip;
        }
        if (searchInput) searchInput.placeholder = t.searchPlaceholder;

        if (btnToggleSelectAll) {
            const isAllSelected = selectedItemsMap.size > 0 && selectedItemsMap.size === currentFolderItems.length;
            btnToggleSelectAll.innerText = isAllSelected ? t.deselectAll : t.selectAll;
        }
        if (btnBatchDownloadZip) btnBatchDownloadZip.innerText = t.downloadSelectedZip;
        if (btnBatchClear) btnBatchClear.innerText = t.clearSelection;

        if (sortSelect) {
            sortSelect.options[0].text = t.sortName;
            sortSelect.options[1].text = t.sortModified;
            sortSelect.options[2].text = t.sortAccessed;
            sortSelect.options[3].text = t.sortSize;
        }

        // Translate modal texts if present
        const confirmTitle = document.getElementById('confirm-zip-title');
        const confirmCancelBtn = document.getElementById('btn-confirm-zip-cancel');
        const confirmProceedBtn = document.getElementById('btn-confirm-zip-proceed');
        if (confirmTitle) confirmTitle.innerText = t.confirmDownloadZipTitle;
        if (confirmCancelBtn) confirmCancelBtn.innerText = t.cancel;
        if (confirmProceedBtn) confirmProceedBtn.innerText = t.downloadZipBtn;

        // Translate Lightbox static elements if present
        const lbFooterInfo = document.getElementById('lb-footer-info');
        const lbMetaHeading = document.getElementById('lb-meta-heading');
        const lbLblName = document.getElementById('lb-lbl-name');
        const lbLblSize = document.getElementById('lb-lbl-size');
        const lbLblType = document.getElementById('lb-lbl-type');
        const lbLblDate = document.getElementById('lb-lbl-date');
        const lbLblPath = document.getElementById('lb-lbl-path');
        const lbBtnDownload = document.getElementById('lb-btn-download');

        if (lbFooterInfo) lbFooterInfo.innerText = t.footerInfo;
        if (lbMetaHeading) lbMetaHeading.innerText = t.metaHeading;
        if (lbLblName) lbLblName.innerText = t.metaName;
        if (lbLblSize) lbLblSize.innerText = t.metaSize;
        if (lbLblType) lbLblType.innerText = t.metaMime;
        if (lbLblDate) lbLblDate.innerText = t.metaModified;
        if (lbLblPath) lbLblPath.innerText = t.metaPath;
        if (lbBtnDownload) lbBtnDownload.innerText = t.downloadBtnText;

        const btnRequestApproval = document.getElementById('btn-request-approval');
        const btnLogin = document.getElementById('btn-login');
        if (btnRequestApproval && !btnRequestApproval.disabled) btnRequestApproval.innerText = t.requestApproval;
        if (btnLogin) btnLogin.innerText = t.submitPin;
    }

    document.addEventListener('DOMContentLoaded', () => {
        initEventListeners();
        initHistoryNavigation();
        initSortControls();
        initLightbox();
        initDragAndDrop();
        initConfirmZipModal();
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
        const btnNavBack = document.getElementById('btn-nav-back');

        if (btnNavBack) {
            btnNavBack.addEventListener('click', () => {
                // If history state exists and isn't root, use browser history.back()
                if (window.history.length > 1 && window.history.state && !window.history.state.isRoot) {
                    window.history.back();
                } else if (currentFolderData && currentFolderData.parentPath) {
                    loadFolder(currentFolderData.parentPath);
                }
            });
        }

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
                const folderName = (currentFolderData && currentFolderData.currentPath)
                    ? (currentFolderData.currentPath.split('/').filter(Boolean).pop() || t.storageHome)
                    : t.storageHome;
                showConfirmZipModal(folderName, () => {
                    triggerZipDownload(currentPath);
                });
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

    function initHistoryNavigation() {
        window.addEventListener('popstate', (e) => {
            const confirmModal = document.getElementById('confirm-zip-modal');
            const lightboxModal = document.getElementById('lightbox-modal');

            let modalDismissed = false;

            // If Confirm ZIP modal is open, back gesture dismisses the modal
            if (confirmModal && !confirmModal.classList.contains('hidden')) {
                confirmModal.classList.add('hidden');
                pendingZipAction = null;
                modalDismissed = true;
            }

            // If Lightbox preview modal is open, back gesture closes preview
            if (lightboxModal && !lightboxModal.classList.contains('hidden')) {
                closeLightboxModal(true); // true = popstate already occurred
                modalDismissed = true;
            }

            // If a modal was dismissed, stop here so user stays in their current folder
            if (modalDismissed) {
                return;
            }

            // Normal folder navigation on back/forward
            const targetPath = (e.state && e.state.folderPath) ? e.state.folderPath : null;
            loadFolder(targetPath, false); // false = don't push state again
        });
    }

    function initConfirmZipModal() {
        const modal = document.getElementById('confirm-zip-modal');
        const btnCancel = document.getElementById('btn-confirm-zip-cancel');
        const btnProceed = document.getElementById('btn-confirm-zip-proceed');

        function dismissModal() {
            if (modal) modal.classList.add('hidden');
            pendingZipAction = null;
            if (window.history.state && window.history.state.isModal) {
                window.history.back();
            }
        }

        if (btnCancel) {
            btnCancel.addEventListener('click', dismissModal);
        }

        if (btnProceed) {
            btnProceed.addEventListener('click', () => {
                const action = pendingZipAction;
                if (modal) modal.classList.add('hidden');
                pendingZipAction = null;
                if (window.history.state && window.history.state.isModal) {
                    window.history.back();
                }
                if (typeof action === 'function') {
                    action();
                }
            });
        }

        if (modal) {
            modal.addEventListener('click', (e) => {
                if (e.target === modal) {
                    dismissModal();
                }
            });
        }
    }

    function showConfirmZipModal(targetName, onProceed) {
        const modal = document.getElementById('confirm-zip-modal');
        const title = document.getElementById('confirm-zip-title');
        const desc = document.getElementById('confirm-zip-desc');
        const confirmCancelBtn = document.getElementById('btn-confirm-zip-cancel');
        const confirmProceedBtn = document.getElementById('btn-confirm-zip-proceed');

        if (!modal) {
            if (typeof onProceed === 'function') onProceed();
            return;
        }

        pendingZipAction = onProceed;
        if (title) {
            title.innerText = t.confirmDownloadZipTitle;
        }
        if (desc) {
            desc.innerHTML = t.confirmDownloadZipPrompt(escapeHtml(targetName));
        }
        if (confirmCancelBtn) {
            confirmCancelBtn.innerText = t.cancel;
        }
        if (confirmProceedBtn) {
            confirmProceedBtn.innerText = t.downloadZipBtn;
        }
        modal.classList.remove('hidden');

        // Push modal state so mobile Android back button dismisses modal instead of exiting
        if (!window.history.state || !window.history.state.isModal) {
            window.history.pushState({ folderPath: currentPath, isModal: true, modalType: 'confirm-zip' }, '', window.location.href);
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
        if (!epochMs || epochMs <= 0) return t.recently;
        const now = Date.now();
        const diffSec = Math.floor((now - epochMs) / 1000);

        if (diffSec < 60) return t.justNow;
        const diffMin = Math.floor(diffSec / 60);
        if (diffMin < 60) return t.minutesAgo(diffMin);
        const diffHours = Math.floor(diffMin / 60);
        if (diffHours < 24) return t.hoursAgo(diffHours);
        const diffDays = Math.floor(diffHours / 24);
        if (diffDays < 30) return t.daysAgo(diffDays);

        const d = new Date(epochMs);
        return d.toLocaleDateString(currentLang === 'ar' ? 'ar' : 'en');
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
            if (btnToggleSelectAll) btnToggleSelectAll.innerText = t.selectAll;
            return;
        }

        if (batchBar) batchBar.classList.remove('hidden');
        if (grid) grid.classList.add('has-selection');

        if (btnToggleSelectAll) {
            if (selectedCount === currentFolderItems.length && currentFolderItems.length > 0) {
                btnToggleSelectAll.innerText = t.deselectAll;
            } else {
                btnToggleSelectAll.innerText = t.selectAll;
            }
        }

        let totalBytes = 0;
        selectedItemsMap.forEach((item) => {
            totalBytes += (item.sizeBytes || 0);
        });

        if (batchCount) batchCount.innerText = t.selectedBadge(selectedCount);
        if (batchSize) batchSize.innerText = `(${formatBytes(totalBytes)})`;
    }

    function handleBatchZipDownload() {
        if (selectedItemsMap.size === 0) return;

        const count = selectedItemsMap.size;
        showConfirmZipModal(t.selectedBadge(count), () => {
            const paths = Array.from(selectedItemsMap.keys());
            const pathsParam = paths.map(p => 'paths=' + encodeURIComponent(p)).join('&');
            const url = `/download-zip?${pathsParam}`;

            showInfoToast(t.streamingBatchToast(count), 4000);

            const anchor = document.createElement('a');
            anchor.href = url;
            anchor.setAttribute('download', '');
            document.body.appendChild(anchor);
            anchor.click();
            document.body.removeChild(anchor);
        });
    }

    async function loadServerInfo() {
        const connBadge = document.getElementById('conn-badge');

        try {
            const res = await fetch('/api/server-info');
            if (res.ok) {
                const data = await res.json();

                // Apply language and text direction from server/app preference
                if (data.language) {
                    applyLanguage(data.language);
                }

                if (connBadge) {
                    if (data.connectionMode === 'HOTSPOT_DIRECT') {
                        connBadge.className = 'connection-badge connection-hotspot';
                        connBadge.innerText = t.hotspotTurbo;
                    } else {
                        connBadge.className = 'connection-badge connection-wifi';
                        connBadge.innerText = t.wifiRouter;
                    }
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
            btn.innerText = t.sendingRequest;
        }

        if (errorText) errorText.innerText = '';
        if (statusText) {
            statusText.innerText = t.waitingApproval;
            statusText.classList.remove('hidden');
        }

        try {
            const res = await fetch('/auth/request-approval', { method: 'POST' });
            if (res.ok) {
                const data = await res.json();
                if (data.success && data.requestId) {
                    pollApprovalStatus(data.requestId);
                } else {
                    resetApprovalButton(t.uploadFailed);
                }
            } else {
                resetApprovalButton(t.uploadFailed);
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

                        if (statusText) statusText.innerText = t.approved;

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

    function resetMediaPlayers() {
        const videoPlayer = document.getElementById('lb-video-player');
        const audioPlayer = document.getElementById('lb-audio-player');
        const imagePlayer = document.getElementById('lb-image-player');
        const graphicThumb = document.getElementById('lb-graphic-thumb');

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
    }

    function closeLightboxModal(triggeredByPopstate = false) {
        const lightboxModal = document.getElementById('lightbox-modal');
        if (!lightboxModal || lightboxModal.classList.contains('hidden')) return;

        // If closed directly by user clicking close button or background (not via browser popstate)
        if (!triggeredByPopstate && window.history.state && window.history.state.isModal) {
            window.history.back();
            return;
        }

        resetMediaPlayers();

        const btnPrevImg = document.getElementById('lb-btn-prev-img');
        const btnNextImg = document.getElementById('lb-btn-next-img');
        const lbImgCounter = document.getElementById('lb-img-counter');

        if (btnPrevImg) btnPrevImg.classList.add('hidden');
        if (btnNextImg) btnNextImg.classList.add('hidden');
        if (lbImgCounter) lbImgCounter.classList.add('hidden');

        lightboxModal.classList.add('hidden');
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

        // Reset previous media state without closing the modal or triggering history.back()
        resetMediaPlayers();

        const fileIcon = getFileIconEmoji(item);
        if (lbIcon) lbIcon.innerText = fileIcon;
        if (lbTitle) lbTitle.innerText = item.name;

        const downloadUrl = `/api/download?path=${encodeURIComponent(item.path)}`;
        const inlineViewUrl = `/api/view?path=${encodeURIComponent(item.path)}`;
        const videoStreamUrl = `/api/stream?path=${encodeURIComponent(item.path)}`;

        if (lbBtnDownload) {
            lbBtnDownload.href = downloadUrl;
            lbBtnDownload.innerText = t.downloadBtnText;
        }

        const lbFooterInfo = document.getElementById('lb-footer-info');
        if (lbFooterInfo) lbFooterInfo.innerText = t.footerInfo;

        const lbMetaHeading = document.getElementById('lb-meta-heading');
        const lbLblName = document.getElementById('lb-lbl-name');
        const lbLblSize = document.getElementById('lb-lbl-size');
        const lbLblType = document.getElementById('lb-lbl-type');
        const lbLblDate = document.getElementById('lb-lbl-date');
        const lbLblPath = document.getElementById('lb-lbl-path');

        if (lbMetaHeading) lbMetaHeading.innerText = t.metaHeading;
        if (lbLblName) lbLblName.innerText = t.metaName;
        if (lbLblSize) lbLblSize.innerText = t.metaSize;
        if (lbLblType) lbLblType.innerText = t.metaMime;
        if (lbLblDate) lbLblDate.innerText = t.metaModified;
        if (lbLblPath) lbLblPath.innerText = t.metaPath;

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

            // Setup Image Next / Previous List & Index based on current sorted view
            const sortedItems = getSortedItems(currentFolderItems);
            currentImagesList = sortedItems.filter(i => i.isMediaImage || (i.mimeType && i.mimeType.startsWith('image/')));
            currentImageIndex = currentImagesList.findIndex(i => i.path === item.path);

            if (currentImagesList.length > 1) {
                if (btnPrevImg) btnPrevImg.classList.remove('hidden');
                if (btnNextImg) btnNextImg.classList.remove('hidden');
                if (lbImgCounter) {
                    lbImgCounter.innerText = `${currentImageIndex + 1} / ${currentImagesList.length}`;
                    lbImgCounter.classList.remove('hidden');
                }
            } else {
                if (btnPrevImg) btnPrevImg.classList.add('hidden');
                if (btnNextImg) btnNextImg.classList.add('hidden');
                if (lbImgCounter) {
                    lbImgCounter.innerText = `1 / 1`;
                    lbImgCounter.classList.remove('hidden');
                }
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
            if (splitDownloadBtn) {
                splitDownloadBtn.href = downloadUrl;
                splitDownloadBtn.innerText = t.downloadFile;
            }

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

        // Push modal state so hardware/gesture Back button on mobile closes lightbox without exiting browser
        if (!window.history.state || !window.history.state.isModal) {
            window.history.pushState({ folderPath: currentPath, isModal: true, modalType: 'lightbox' }, '', window.location.href);
        }
    }

    function getInlineViewAction(item) {
        if (!item || !item.name) return null;
        const name = item.name.toLowerCase();
        const mime = (item.mimeType || '').toLowerCase();

        if (item.isMediaImage || mime.startsWith('image/')) {
            return { label: currentLang === 'ar' ? '🖼️ عرض الصورة كاملة' : '🖼️ View Full Image', url: `/api/view?path=${encodeURIComponent(item.path)}` };
        }
        if (item.isMediaVideo || mime.startsWith('video/')) {
            return { label: currentLang === 'ar' ? '🎬 فتح بث الفيديو' : '🎬 Open Video Stream', url: `/api/stream?path=${encodeURIComponent(item.path)}` };
        }
        if (isAudioFile(item)) {
            return { label: currentLang === 'ar' ? '🎵 تشغيل المقطع الصوتي' : '🎵 Open Audio Track', url: `/api/view?path=${encodeURIComponent(item.path)}` };
        }
        if (name.endsWith('.pdf') || mime === 'application/pdf') {
            return { label: currentLang === 'ar' ? '📄 فتح ملف PDF' : '📄 Open PDF', url: `/api/view?path=${encodeURIComponent(item.path)}` };
        }
        if (mime.startsWith('text/') || name.endsWith('.txt') || name.endsWith('.log') ||
            name.endsWith('.md') || name.endsWith('.json') || name.endsWith('.html') ||
            name.endsWith('.css') || name.endsWith('.js') || name.endsWith('.kt') ||
            name.endsWith('.java') || name.endsWith('.py') || name.endsWith('.xml') || name.endsWith('.csv')) {
            return { label: currentLang === 'ar' ? '📝 فتح الملف النصي' : '📝 View Text File', url: `/api/view?path=${encodeURIComponent(item.path)}` };
        }

        return null;
    }

    function triggerZipDownload(folderPath) {
        showInfoToast(t.streamingZipToast, 4000);
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
                if (speedEl) speedEl.innerText = t.uploadComplete;
                setTimeout(() => {
                    if (toast) toast.classList.add('hidden');
                    resolve();
                }, 1000);
            };

            xhr.onerror = () => {
                if (speedEl) speedEl.innerText = t.uploadFailed;
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
        // Check if browser was loaded or refreshed with a specific ?dir= URL
        const urlParams = new URLSearchParams(window.location.search);
        const initialDir = urlParams.get('dir') || null;
        window.history.replaceState({ folderPath: initialDir, isRoot: !initialDir }, '', window.location.href);
        await loadFolder(initialDir, false);
    }

    async function loadStorageStats() {
        try {
            const res = await fetch('/api/storage');
            if (res.status === 401) return;

            const data = await res.json();
            const storageText = document.getElementById('storage-text');
            const storageBar = document.getElementById('storage-bar');

            if (storageText && storageBar) {
                storageText.innerText = t.storageFormat(data.formattedFree, data.formattedTotal);
                storageBar.style.width = `${data.usedPercentage}%`;
            }
        } catch (err) {
            console.error('Failed to load storage stats:', err);
        }
    }

    async function loadFolder(targetPath, pushHistory = true) {
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
        const btnNavBack = document.getElementById('btn-nav-back');

        if (grid) {
            grid.innerHTML = `<div class="loading-spinner">${t.scanningFolder}</div>`;
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

            // Update Back button visibility (hidden only at storage root)
            if (btnNavBack) {
                if (data.parentPath) {
                    btnNavBack.classList.remove('hidden');
                } else {
                    btnNavBack.classList.add('hidden');
                }
            }

            // Sync with browser history state so mobile hardware back button navigates up
            if (pushHistory) {
                const stateObj = { folderPath: data.currentPath, isRoot: !data.parentPath };
                const displayUrl = data.currentPath ? `?dir=${encodeURIComponent(data.currentPath)}` : window.location.pathname;
                window.history.pushState(stateObj, '', displayUrl);
            }

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
                <div class="item-title">${escapeHtml(t.parentFolder)}</div>
                <div class="item-sub">${escapeHtml(t.directory)}</div>
            `;
            parentCard.addEventListener('click', () => loadFolder(data.parentPath));
            grid.appendChild(parentCard);
        }

        if (itemsToRender.length === 0) {
            grid.innerHTML = searchQuery
                ? `<div class="loading-spinner">${t.noMatchQuery(escapeHtml(searchQuery))}</div>`
                : `<div class="loading-spinner">${t.emptyFolder}</div>`;
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
                actionBtnHtml = `<button class="btn-download btn-zip-item" title="${escapeHtml(t.downloadFolderTooltip)}">${escapeHtml(t.downloadFolder)}</button>`;
            } else {
                actionBtnHtml = `
                    <button class="btn-download btn-preview-item">${escapeHtml(t.view)}</button>
                    <a class="btn-download btn-icon-only btn-file-item" href="/api/download?path=${encodeURIComponent(item.path)}" target="_blank" title="${escapeHtml(t.downloadFile)}">${escapeHtml(t.download)}</a>
                `;
            }

            // Dynamic Subtitle Based on Sort Selection
            let subtitleText = '';
            if (currentSortBy === 'accessed') {
                const relativeTime = getRelativeTimeString(item.lastAccessedMillis || item.lastModified);
                subtitleText = item.isDirectory ? t.openedAgo(relativeTime) : `${item.formattedSize} • ${t.openedAgo(relativeTime)}`;
            } else if (currentSortBy === 'modified') {
                subtitleText = item.isDirectory ? t.modifiedOn(item.formattedDate) : `${item.formattedSize} • ${t.modifiedOn(item.formattedDate)}`;
            } else if (currentSortBy === 'size') {
                subtitleText = item.isDirectory ? t.folder : `${item.formattedSize} • ${t.modifiedOn(item.formattedDate)}`;
            } else {
                subtitleText = item.isDirectory ? t.folder : `${item.formattedSize} • ${t.modifiedOn(item.formattedDate)}`;
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
                    showConfirmZipModal(item.name, () => {
                        triggerZipDownload(item.path);
                    });
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
        homeCrumb.innerText = t.storageHome;
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
                displayName = t.internalStorage;
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
            loginError.innerText = t.enter4DigitPin;
            return;
        }

        loginError.innerText = t.authenticating;

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
