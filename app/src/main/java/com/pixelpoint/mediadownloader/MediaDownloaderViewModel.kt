package com.pixelpoint.mediadownloader

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pixelpoint.mediadownloader.engine.DownloadFormatOption
import com.pixelpoint.mediadownloader.engine.LocalDownloadEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class MediaDownloaderViewModel(application: Application) : AndroidViewModel(application) {
    private val appSettings = AppSettings(application)
    private val mediaFileActions = MediaFileActions(application)
    private val historyStore = HistoryStore(application)
    private val pendingQueueStore = PendingQueueStore(application)
    private val downloadEngine = LocalDownloadEngine(application)

    private var allHistory: List<DownloadTask> = emptyList()
    private var currentFilterKeys: Set<String> = emptySet()
    private val playbackMinSavePositionMs = 3_000L
    private val playbackCompletionClearThresholdMs = 5_000L
    private val playbackCompletionClearRatio = 0.95

    private val _uiState = MutableStateFlow(
        MediaDownloaderUiState(
            defaultStorageLocation = appSettings.defaultStorageLocation,
            audioFormatPreferred = appSettings.audioFormatPreferred,
            audioQuality = appSettings.audioQuality,
            videoFormat = appSettings.videoFormat,
            videoQuality = appSettings.videoQuality,
            rateLimitEnabled = appSettings.rateLimitEnabled,
            rateLimitValue = appSettings.rateLimitValue,
            cellularDownload = appSettings.cellularDownload,
            ytDlpVersion = appSettings.ytDlpVersion.ifBlank { "读取中" }
        )
    )
    val uiState: StateFlow<MediaDownloaderUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            delay(ENGINE_VERSION_STARTUP_DELAY_MS)
            val currentVersion = downloadEngine.getEngineVersion()
            if (appSettings.ytDlpVersion != currentVersion) {
                appSettings.ytDlpVersion = currentVersion
            }
            _uiState.update { it.copy(ytDlpVersion = currentVersion) }
        }
    }

    private fun getPlatformOrDomainKey(task: DownloadTask, referer: String): String {
        val url = referer.ifBlank { task.sourceUrl }
        val platform = LinkPlatform.fromInput(url)
            ?: LinkPlatform.fromInput(task.sourceUrl)
        return if (platform != null) {
            "platform:${platform.name}"
        } else {
            val targetUrl = url.ifBlank { task.sourceUrl }
            val host = UrlExtractor.hostLabel(targetUrl)
                .removePrefix("www.")
                .ifBlank { "媒体来源" }
            "domain:$host"
        }
    }

    private fun getAvailableFilterChips(): List<HistoryFilterChip> {
        val platformsAndDomains = allHistory.map { task ->
            val referer = appSettings.storedRefererForUrl(task.sourceUrl)
            getPlatformOrDomainKey(task, referer)
        }.distinct()

        val chips = mutableListOf<HistoryFilterChip>()
        chips.add(HistoryFilterChip(null, "全部", null))

        LinkPlatform.values().forEach { platform ->
            val key = "platform:${platform.name}"
            if (key in platformsAndDomains) {
                chips.add(HistoryFilterChip(key, platform.displayName, platform.iconRes))
            }
        }

        platformsAndDomains.filter { it.startsWith("domain:") }
            .map { it.removePrefix("domain:") }
            .sorted()
            .forEach { domain ->
                chips.add(HistoryFilterChip("domain:$domain", domain, null))
            }

        return chips
    }

    private fun getFilteredHistory(): List<DownloadTask> {
        return if (currentFilterKeys.isEmpty()) {
            allHistory
        } else {
            allHistory.filter { task ->
                val referer = appSettings.storedRefererForUrl(task.sourceUrl)
                getPlatformOrDomainKey(task, referer) in currentFilterKeys
            }
        }
    }

    private fun updateHistoryState() {
        _uiState.update { state ->
            val visibleHistory = getFilteredHistory()
            state.copy(
                history = visibleHistory,
                hasMoreHistory = false,
                availableFilterChips = getAvailableFilterChips()
            )
        }
    }

    fun setHistoryFilterKeys(filterKeys: Set<String>) {
        currentFilterKeys = filterKeys
        updateHistoryState()
    }

    fun loadMoreHistory() {
        // No-op since all history is loaded by default
    }

    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            handleDownloadServiceUpdate(intent)
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            delay(STARTUP_DATA_LOAD_DELAY_MS)
            val loadedHistory = historyStore.load()
            val loadedQueue = pendingQueueStore.load(::nowLabel)
            withContext(Dispatchers.Main) {
                allHistory = loadedHistory
                _uiState.update { state ->
                    state.copy(
                        queuedTasks = if (state.queuedTasks.isEmpty()) loadedQueue else state.queuedTasks
                    )
                }
                updateHistoryState()
                AppLogger.event(
                    "viewmodel",
                    "initAsync",
                    "historyCount" to allHistory.size,
                    "pendingQueueCount" to loadedQueue.size,
                    "defaultStorage" to _uiState.value.defaultStorageLocation.value
                )
            }
            loadedHistory.firstOrNull {
                it.status == DownloadStatus.Completed && it.filePath.isNotBlank()
            }?.let { task ->
                mediaFileActions.shareTargets(task.filePath, task.title)
                    .onFailure { error ->
                        AppLogger.warn(
                            "file",
                            "shareTargetsPreloadFailed",
                            "taskId" to task.id,
                            "error" to (error.message ?: "unknown")
                        )
                    }
            }
        }
        val filter = IntentFilter().apply {
            addAction(DownloadServiceContract.ACTION_TASK_STARTED)
            addAction(DownloadServiceContract.ACTION_TASK_PROGRESS)
            addAction(DownloadServiceContract.ACTION_TASK_COMPLETED)
            addAction(DownloadServiceContract.ACTION_TASK_FAILED)
            addAction(DownloadServiceContract.ACTION_TASK_CANCELLED)
            addAction(DownloadServiceContract.ACTION_QUEUE_UPDATED)
        }
        ContextCompat.registerReceiver(
            application,
            serviceReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        AppLogger.event("viewmodel", "serviceReceiverRegistered")
    }

    fun handleSharedIntent(intent: Intent?) {
        val action = intent?.action
        AppLogger.event("user", "handleSharedIntent", "action" to action)
        if (action != Intent.ACTION_SEND) return

        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val url = UrlExtractor.bestMediaUrl(text) ?: text.trim()
        if (url.isBlank()) {
            AppLogger.warn("user", "sharedIntentWithoutUrl", "textLength" to text.length)
            _uiState.update {
                it.copy(
                    selectedTab = AppTab.Home,
                    message = "已接收分享内容，但没有识别到链接"
                )
            }
            return
        }

        AppLogger.event("user", "sharedUrlAccepted", "url" to url, "host" to UrlExtractor.hostLabel(url))
        _uiState.update {
            it.copy(
                inputUrl = url,
                selectedTab = AppTab.Home,
                message = "已从系统分享菜单接收链接"
            )
        }
    }

    fun updateInput(value: String) {
        AppLogger.event(
            "user",
            "inputUpdated",
            "length" to value.length,
            "detectedUrl" to (UrlExtractor.bestMediaUrl(value) ?: "").ifBlank { "none" }
        )
        _uiState.update { it.copy(inputUrl = value, message = null) }
    }

    fun startPrototypeDownload() {
        if (_uiState.value.isResolvingFormats) {
            AppLogger.warn("user", "startIgnoredWhileResolving", "pendingUrl" to _uiState.value.pendingUrl)
            _uiState.update { it.copy(message = "正在识别当前链接，请稍等") }
            return
        }
        val cleanUrl = UrlExtractor.bestMediaUrl(_uiState.value.inputUrl) ?: _uiState.value.inputUrl.trim()
        AppLogger.event("user", "startDownloadClicked", "url" to cleanUrl, "host" to UrlExtractor.hostLabel(cleanUrl))
        if (!UrlExtractor.isWebUrl(cleanUrl)) {
            AppLogger.warn("download", "invalidUrlRejected", "inputLength" to _uiState.value.inputUrl.length)
            _uiState.update { it.copy(message = "请输入或分享一个有效网页链接") }
            return
        }

        if (UrlExtractor.requiresFreshWebSession(cleanUrl)) {
            val assistTask = DownloadTask(
                id = UUID.randomUUID().toString(),
                title = UrlExtractor.hostLabel(cleanUrl),
                sourceUrl = cleanUrl,
                progress = 0f,
                status = DownloadStatus.Failed,
                stage = DownloadStage.AwaitingMediaCapture,
                createdAt = nowLabel(),
                errorMessage = "该页面需要捕获当前视频流"
            )
            AppLogger.event("cookie", "freshMediaCaptureRequired", "url" to cleanUrl, "platform" to UrlExtractor.hostLabel(cleanUrl))
            _uiState.update {
                it.copy(
                    cookieTask = assistTask,
                    message = "正在获取页面视频流"
                )
            }
            return
        }

        if (UrlExtractor.prefersDirectDownload(cleanUrl)) {
            AppLogger.event("download", "directDownloadPreferred", "url" to cleanUrl)
            enqueueDownload(
                cleanUrl = cleanUrl,
                title = UrlExtractor.hostLabel(cleanUrl),
                format = null
            )
            return
        }

        _uiState.update {
            it.copy(
                isResolvingFormats = true,
                pendingStage = DownloadStage.ResolvingFormats,
                pendingUrl = cleanUrl,
                pendingTitle = UrlExtractor.hostLabel(cleanUrl),
                pendingThumbnailUrl = "",
                message = "正在识别可用清晰度"
            )
        }
        viewModelScope.launch {
            val resolveStartedAt = SystemClock.elapsedRealtime()
            val cookieHeader = appSettings.cookieForUrl(cleanUrl)
            val refererHeader = appSettings.refererForUrl(cleanUrl)
            runCatching {
                AppLogger.event(
                    "formats",
                    "resolveStart",
                    "url" to cleanUrl,
                    "cookie" to AppLogger.cookieSummary(cookieHeader),
                    "referer" to refererHeader.ifBlank { "blank" }
                )
                withTimeout(45_000) {
                    downloadEngine.resolveFormats(
                        cleanUrl,
                        cookieHeader,
                        refererHeader,
                        appSettings.videoFormat,
                        appSettings.audioFormatPreferred,
                        appSettings.audioQuality
                    )
                }
            }.onSuccess { result ->
                AppLogger.event(
                    "formats",
                    "resolveResult",
                    "durationMs" to (SystemClock.elapsedRealtime() - resolveStartedAt),
                    "ok" to result.ok,
                    "title" to result.title,
                    "thumbnail" to result.thumbnailUrl.ifBlank { "blank" },
                    "count" to result.formats.size,
                    "engineVersion" to result.engineVersion,
                    "error" to result.error
                )
                if (!result.ok) {
                    val friendlyError = DownloadFailureClassifier.classify(result.error)
                    if (shouldFailWithoutDirectFallback(result.error)) {
                        AppLogger.warn(
                            "formats",
                            "resolveFailedWithoutFallback",
                            "url" to cleanUrl,
                            "error" to result.error.ifBlank { "blank" },
                            "friendlyError" to friendlyError
                        )
                        _uiState.update {
                            it.copy(
                                isResolvingFormats = false,
                                pendingStage = null,
                                pendingUrl = "",
                                pendingTitle = "",
                                pendingThumbnailUrl = "",
                                formatOptions = emptyList(),
                                selectedFormat = null,
                                formatResolveWarning = null,
                                message = friendlyError
                            )
                        }
                        return@onSuccess
                    }
                    val fallbackFormat = directDownloadFallbackFormat()
                    AppLogger.warn(
                        "formats",
                        "resolveFailedShowFallbackPicker",
                        "url" to cleanUrl,
                        "error" to result.error.ifBlank { "blank" },
                        "friendlyError" to friendlyError
                    )
                    _uiState.update {
                        it.copy(
                            isResolvingFormats = false,
                            pendingStage = DownloadStage.AwaitingFormatSelection,
                            pendingUrl = cleanUrl,
                            pendingTitle = result.title.ifBlank { it.pendingTitle.ifBlank { UrlExtractor.hostLabel(cleanUrl) } },
                            pendingThumbnailUrl = result.thumbnailUrl,
                            formatOptions = emptyList(),
                            selectedFormat = null,
                            formatResolveWarning = "未识别到下载版本，可尝试直接下载",
                            message = "识别成功"
                        )
                    }
                    delay(600)
                    if (_uiState.value.pendingStage == DownloadStage.AwaitingFormatSelection) {
                        _uiState.update {
                            it.copy(
                                formatOptions = listOf(fallbackFormat),
                                selectedFormat = fallbackFormat,
                                message = null
                            )
                        }
                    }
                } else if (result.formats.isNotEmpty()) {
                    val defaultFormat = chooseDefaultFormat(result.formats)
                    AppLogger.event(
                        "formats",
                        "showPicker",
                        "optionCount" to result.formats.size,
                        "default" to defaultFormat?.label
                    )
                    _uiState.update {
                        it.copy(
                            isResolvingFormats = false,
                            pendingStage = DownloadStage.AwaitingFormatSelection,
                            pendingTitle = result.title.ifBlank { it.pendingTitle },
                            pendingThumbnailUrl = result.thumbnailUrl,
                            formatOptions = emptyList(),
                            selectedFormat = null,
                            formatResolveWarning = null,
                            message = "识别成功"
                        )
                    }
                    delay(600)
                    if (_uiState.value.pendingStage == DownloadStage.AwaitingFormatSelection) {
                        _uiState.update {
                            it.copy(
                                formatOptions = result.formats,
                                selectedFormat = defaultFormat,
                                message = null
                            )
                        }
                    }
                } else {
                    val fallbackFormat = directDownloadFallbackFormat()
                    AppLogger.event("formats", "noFormatShowFallbackPicker")
                    _uiState.update {
                        it.copy(
                            isResolvingFormats = false,
                            pendingStage = DownloadStage.AwaitingFormatSelection,
                            pendingTitle = result.title.ifBlank { it.pendingTitle },
                            pendingThumbnailUrl = result.thumbnailUrl,
                            formatOptions = emptyList(),
                            selectedFormat = null,
                            formatResolveWarning = "未识别到下载版本，可尝试直接下载",
                            message = "识别成功"
                        )
                    }
                    delay(600)
                    if (_uiState.value.pendingStage == DownloadStage.AwaitingFormatSelection) {
                        _uiState.update {
                            it.copy(
                                formatOptions = listOf(fallbackFormat),
                                selectedFormat = fallbackFormat,
                                message = null
                            )
                        }
                    }
                }
            }.onFailure { error ->
                AppLogger.error(
                    "formats",
                    "resolveFailure",
                    error,
                    "url" to cleanUrl,
                    "durationMs" to (SystemClock.elapsedRealtime() - resolveStartedAt)
                )
                val message = if (error is kotlinx.coroutines.TimeoutCancellationException) {
                    "清晰度识别超时，可能是平台反爬、网络较慢或该链接需要登录"
                } else {
                    error.message ?: "清晰度解析失败"
                }
                _uiState.update {
                    it.copy(
                        isResolvingFormats = false,
                        pendingStage = null,
                        pendingUrl = "",
                        pendingTitle = "",
                        pendingThumbnailUrl = "",
                        formatOptions = emptyList(),
                        selectedFormat = null,
                        formatResolveWarning = null,
                        message = "清晰度识别失败：$message"
                    )
                }
                AppLogger.warn("formats", "resolveExceptionStopped", "url" to cleanUrl, "message" to message)
            }
        }
    }

    fun selectFormat(format: DownloadFormatOption) {
        AppLogger.event("user", "formatSelected", "id" to format.id, "label" to format.label, "selector" to format.selector)
        _uiState.update { it.copy(selectedFormat = format) }
    }

    fun dismissFormatPicker() {
        AppLogger.event("user", "formatPickerDismissed", "pendingUrl" to _uiState.value.pendingUrl)
        _uiState.update {
            it.copy(
                isResolvingFormats = false,
                pendingStage = null,
                formatOptions = emptyList(),
                selectedFormat = null,
                pendingUrl = "",
                pendingTitle = "",
                pendingThumbnailUrl = "",
                formatResolveWarning = null,
                message = null
            )
        }
    }

    fun confirmSelectedFormat() {
        val state = _uiState.value
        val cleanUrl = state.pendingUrl
        AppLogger.event(
            "user",
            "formatConfirmed",
            "url" to cleanUrl,
            "selected" to state.selectedFormat?.label,
            "selector" to state.selectedFormat?.selector,
            "sourceUrl" to state.selectedFormat?.sourceUrl
        )
        if (cleanUrl.isBlank()) {
            AppLogger.warn("formats", "confirmWithoutPendingUrl")
            dismissFormatPicker()
            return
        }
        val selectedSourceUrl = state.selectedFormat?.sourceUrl.orEmpty()
        val downloadUrl = selectedSourceUrl.ifBlank { cleanUrl }
        if (selectedSourceUrl.isNotBlank()) {
            val cookie = appSettings.cookieForUrl(cleanUrl)
            if (cookie.isNotBlank()) {
                appSettings.setCookieForUrl(selectedSourceUrl, cookie)
            }
            appSettings.setRefererForUrl(selectedSourceUrl, cleanUrl)
            AppLogger.event(
                "formats",
                "selectedResolvedMediaUrl",
                "pageUrl" to cleanUrl,
                "mediaUrl" to selectedSourceUrl,
                "cookie" to AppLogger.cookieSummary(cookie)
            )
        }
        enqueueDownload(
            cleanUrl = downloadUrl,
            title = state.pendingTitle.ifBlank { UrlExtractor.hostLabel(cleanUrl) },
            thumbnailUrl = state.pendingThumbnailUrl,
            format = state.selectedFormat
        )
    }

    private fun enqueueDownload(
        cleanUrl: String,
        title: String,
        thumbnailUrl: String = "",
        format: DownloadFormatOption?
    ) {
        val formatSelector = format?.selector.orEmpty()
        val formatLabel = format?.label.orEmpty()
        val requiresMediaMerge = format?.requiresMerge == true

        val hasActiveTask = _uiState.value.activeTask?.status in setOf(
            DownloadStatus.Downloading,
            DownloadStatus.Processing
        )
        val task = DownloadTask(
            id = UUID.randomUUID().toString(),
            title = title.ifBlank { UrlExtractor.hostLabel(cleanUrl) },
            sourceUrl = cleanUrl,
            progress = 0f,
            status = if (hasActiveTask) DownloadStatus.Queued else DownloadStatus.Downloading,
            stage = if (hasActiveTask) DownloadStage.Queued else DownloadStage.Downloading,
            createdAt = nowLabel(),
            formatSelector = formatSelector,
            formatLabel = formatLabel,
            thumbnailUrl = thumbnailUrl,
            requiresMediaMerge = requiresMediaMerge
        )

        AppLogger.event(
            "download",
            "enqueue",
            "taskId" to task.id,
            "url" to cleanUrl,
            "title" to task.title,
            "status" to task.status.name,
            "stage" to task.stage.name,
            "formatLabel" to formatLabel,
            "formatSelector" to formatSelector,
            "requiresMediaMerge" to requiresMediaMerge,
            "hasActiveTask" to hasActiveTask
        )
        var updatedQueue: List<DownloadTask>? = null
        _uiState.update {
            updatedQueue = if (hasActiveTask) it.queuedTasks + task else it.queuedTasks
            it.copy(
                inputUrl = "",
                isResolvingFormats = false,
                pendingStage = null,
                formatOptions = emptyList(),
                selectedFormat = null,
                pendingUrl = "",
                pendingTitle = "",
                pendingThumbnailUrl = "",
                formatResolveWarning = null,
                activeTask = if (hasActiveTask) it.activeTask else task,
                queuedTasks = updatedQueue.orEmpty(),
                message = if (hasActiveTask) "已加入等待队列" else "后台下载任务已启动"
            )
        }
        updatedQueue?.let { pendingQueueStore.save(it) }
        MediaDownloadService.start(
            context = getApplication(),
            taskId = task.id,
            url = cleanUrl,
            title = task.title,
            thumbnailUrl = task.thumbnailUrl,
            formatSelector = formatSelector,
            formatLabel = formatLabel,
            requiresMediaMerge = requiresMediaMerge
        )
    }

    private fun chooseDefaultFormat(formats: List<DownloadFormatOption>): DownloadFormatOption? {
        if (formats.isEmpty()) return null

        val videoQuality = appSettings.videoQuality
        if (videoQuality == 1) {
            return formats.filter { it.height > 0 }.minByOrNull { it.height }
                ?: formats.firstOrNull()
        }
        if (videoQuality <= 0) {
            return formats.filter { it.height > 0 }.maxByOrNull { it.height }
                ?: formats.firstOrNull()
        }
        return formats.filter { it.height in 1..videoQuality }
            .maxByOrNull { it.height }
            ?: formats.filter { it.height > 0 }.minByOrNull { it.height }
            ?: formats.firstOrNull()
    }

    private fun directDownloadFallbackFormat(): DownloadFormatOption {
        return DownloadFormatOption(
            id = "direct_fallback",
            label = "尝试直接下载",
            detail = "未识别到具体版本，使用默认方式尝试",
            selector = "",
            height = 0,
            requiresMerge = false
        )
    }

    private fun shouldFailWithoutDirectFallback(error: String): Boolean {
        return DownloadFailureClassifier.isExpiredXiaohongshuLink(error)
    }

    fun cancelActiveTask() {
        AppLogger.event("user", "cancelActiveTask", "taskId" to _uiState.value.activeTask?.id)
        MediaDownloadService.cancel(getApplication())
        _uiState.update {
            it.copy(
                activeTask = it.activeTask?.copy(status = DownloadStatus.Cancelled, stage = DownloadStage.Cancelled, progress = 0f),
                message = "正在取消后台下载任务"
            )
        }
    }

    fun retryTask(task: DownloadTask) {
        val cleanUrl = task.sourceUrl.trim()
        AppLogger.event("user", "retryTask", "taskId" to task.id, "url" to cleanUrl, "status" to task.status.name)
        if (!UrlExtractor.isWebUrl(cleanUrl)) {
            AppLogger.warn("download", "retryRejectedInvalidUrl", "taskId" to task.id)
            _uiState.update { it.copy(message = "无法重试，原始链接不可用") }
            return
        }
        val format = if (task.formatSelector.isNotBlank() || task.formatLabel.isNotBlank()) {
            DownloadFormatOption(
                id = task.formatSelector.ifBlank { "retry" },
                label = task.formatLabel,
                detail = "",
                selector = task.formatSelector,
                height = 0,
                requiresMerge = task.requiresMediaMerge
            )
        } else {
            null
        }
        enqueueDownload(
            cleanUrl = cleanUrl,
            title = task.title.ifBlank { UrlExtractor.hostLabel(cleanUrl) },
            format = format
        )
    }

    fun openSourceLink(task: DownloadTask) {
        val cleanUrl = task.sourceUrl.trim()
        AppLogger.event("user", "openSourceLink", "taskId" to task.id, "url" to cleanUrl)
        if (!UrlExtractor.isWebUrl(cleanUrl)) {
            AppLogger.warn("user", "openSourceRejectedInvalidUrl", "taskId" to task.id)
            _uiState.update { it.copy(message = "原始链接不可用") }
            return
        }
        if (UrlExtractor.requiresFreshWebSession(cleanUrl)) {
            AppLogger.event("cookie", "alwaysUsesFreshMediaCapture", "taskId" to task.id, "url" to cleanUrl)
            _uiState.update { it.copy(cookieTask = task) }
            return
        }
        if (!task.errorMessage.isYouTubeJsChallengeFailure() && appSettings.hasCookieForUrl(cleanUrl)) {
            AppLogger.event("cookie", "manualRetryUsingSavedCookie", "taskId" to task.id, "url" to cleanUrl)
            retryWithSavedCookie(task)
            return
        }
        _uiState.update { it.copy(cookieTask = task) }
    }

    fun requestManualCookie(task: DownloadTask) {
        AppLogger.event("user", "requestManualCookie", "taskId" to task.id, "url" to task.sourceUrl)
        _uiState.update { it.copy(cookieTask = task) }
    }

    fun updateCookieInput(value: String) {
        AppLogger.event("user", "cookieInputUpdated", "summary" to AppLogger.cookieSummary(value))
        _uiState.update { it.copy(cookieInput = value) }
    }

    fun dismissCookieDialog() {
        AppLogger.event("user", "cookieDialogDismissed", "taskId" to _uiState.value.cookieTask?.id)
        _uiState.update { it.copy(cookieTask = null, cookieInput = "") }
    }

    fun saveCookieAndRetry() {
        val state = _uiState.value
        val task = state.cookieTask ?: return
        val cookie = state.cookieInput.trim()
        AppLogger.event("user", "saveManualCookie", "taskId" to task.id, "cookie" to AppLogger.cookieSummary(cookie))
        if (cookie.isBlank()) {
            AppLogger.warn("cookie", "blankManualCookieRejected", "taskId" to task.id)
            _uiState.update { it.copy(message = "请先粘贴 Cookie") }
            return
        }
        appSettings.setCookieForUrl(task.sourceUrl, cookie)
        _uiState.update {
            it.copy(
                cookieTask = null,
                cookieInput = "",
                message = "Cookie 已保存，将自动带登录态重试"
            )
        }
        retryTask(task)
    }

    fun retryWithSavedCookie(task: DownloadTask) {
        AppLogger.event("user", "retryWithSavedCookie", "taskId" to task.id, "url" to task.sourceUrl)
        _uiState.update {
            it.copy(
                cookieTask = null,
                cookieInput = "",
                message = "将使用已保存的登录态重试"
            )
        }
        retryTask(task)
    }

    fun saveWebViewCookieAndRetry(task: DownloadTask, cookie: String, capturedMediaUrl: String, capturedPageTitle: String) {
        val cleaned = cookie.trim()
        val captured = capturedMediaUrl.trim()
        val pageTitle = capturedPageTitle.trim()
        AppLogger.event(
            "user",
            "saveWebViewCookie",
            "taskId" to task.id,
            "cookie" to AppLogger.cookieSummary(cleaned),
            "capturedMediaUrl" to captured.ifBlank { "blank" },
            "capturedPageTitle" to pageTitle.ifBlank { "blank" }
        )
        if (UrlExtractor.requiresFreshWebSession(task.sourceUrl) && captured.isBlank()) {
            AppLogger.warn("cookie", "mediaNotCaptured", "taskId" to task.id, "cookie" to AppLogger.cookieSummary(cleaned))
            _uiState.update { it.copy(message = "还未检测到视频流，请播放目标视频后重试") }
            return
        }
        if (cleaned.isBlank() && captured.isBlank()) {
            AppLogger.warn("cookie", "blankWebViewCookieRejected", "taskId" to task.id)
            _uiState.update { it.copy(message = "还没有读取到 Cookie，请先在页面中登录或完成验证") }
            return
        }
        if (cleaned.isNotBlank()) {
            appSettings.setCookieForUrl(task.sourceUrl, cleaned)
            if (task.sourceUrl.isTwitterLikeUrl()) {
                appSettings.setCookieForUrl("https://twitter.com/", cleaned)
                appSettings.setCookieForUrl("https://x.com/", cleaned)
            }
        }
        val retryUrl = captured.ifBlank { task.sourceUrl }
        if (captured.isNotBlank()) {
            if (cleaned.isNotBlank()) {
                appSettings.setCookieForUrl(captured, cleaned)
            }
            appSettings.setRefererForUrl(captured, task.sourceUrl)
            AppLogger.event(
                "cookie",
                "capturedMediaRetryWithoutCookieAllowed",
                "taskId" to task.id,
                "cookieAvailable" to cleaned.isNotBlank(),
                "capturedMediaUrl" to captured,
                "capturedPageTitle" to pageTitle.ifBlank { "blank" }
            )
        }
        _uiState.update {
            it.copy(
                cookieTask = null,
                cookieInput = "",
                message = if (captured.isNotBlank()) {
                    if (cleaned.isNotBlank()) "已检测到媒体流，将带登录态重试" else "已检测到媒体流，正在直接下载"
                } else {
                    "已读取并保存 Cookie，将自动重试"
                }
            )
        }
        retryTask(
            task.copy(
                sourceUrl = retryUrl,
                title = if (captured.isNotBlank()) pageTitle.ifBlank { task.title } else task.title
            )
        )
    }

    fun selectTab(tab: AppTab) {
        AppLogger.event("user", "selectTab", "tab" to tab.name)
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun deleteHistoryItem(id: String) {
        AppLogger.event("user", "deleteHistoryItem", "taskId" to id)
        allHistory = allHistory.filterNot { item -> item.id == id }
        historyStore.save(allHistory)
        deleteThumbnailForTask(id)
        updateHistoryState()
    }

    fun showYtDlpUpdatePlaceholder() {
        AppLogger.event("user", "showYtDlpUpdatePlaceholder")
        _uiState.update { it.copy(message = "yt-dlp 引擎更新功能正在开发中，敬请期待") }
    }

    fun deleteHistoryItemAndFile(id: String) {
        AppLogger.event("user", "deleteHistoryItemAndFile", "taskId" to id)
        val target = allHistory.firstOrNull { item -> item.id == id }
        allHistory = allHistory.filterNot { item -> item.id == id }
        deleteThumbnailForTask(id)
        var fileStillReferenced = false
        var exportedFileStillReferenced = false
        var exportedFileDeleted = false
        if (target?.filePath?.isNotBlank() == true) {
            fileStillReferenced = allHistory.any { item -> item.filePath == target.filePath }
            val localFileDeleted = if (fileStillReferenced) false else File(target.filePath).delete()
            AppLogger.event(
                "history",
                "deleteLocalFile",
                "taskId" to id,
                "filePath" to target.filePath,
                "deleted" to localFileDeleted,
                "stillReferenced" to fileStillReferenced
            )
        }
        if (target != null && (target.exportedPath.isNotBlank() || target.exportedUri.isNotBlank())) {
            exportedFileStillReferenced = allHistory.any { item ->
                if (target.exportedUri.isNotBlank()) {
                    item.exportedUri == target.exportedUri
                } else {
                    item.exportedUri.isBlank() && item.exportedPath == target.exportedPath
                }
            }
            if (!exportedFileStillReferenced) {
                exportedFileDeleted = mediaFileActions
                    .deleteExportedFile(target.exportedPath, target.exportedUri)
                    .onFailure { error ->
                        AppLogger.error(
                            "history",
                            "deleteExportedFileFailed",
                            error,
                            "taskId" to id,
                            "exportedPath" to target.exportedPath,
                            "exportedUri" to target.exportedUri.ifBlank { "blank" }
                        )
                    }
                    .getOrDefault(false)
            }
            AppLogger.event(
                "history",
                "deleteExportedFileResult",
                "taskId" to id,
                "exportedPath" to target.exportedPath,
                "exportedUri" to target.exportedUri.ifBlank { "blank" },
                "deleted" to exportedFileDeleted,
                "stillReferenced" to exportedFileStillReferenced
            )
        }
        historyStore.save(allHistory)
        _uiState.update { state ->
            val visibleHistory = getFilteredHistory()
            val message = when {
                fileStillReferenced || exportedFileStillReferenced -> "已删除记录，共用文件仍由其他记录保留"
                target?.exportedPath?.isNotBlank() == true && !exportedFileDeleted -> "已删除记录和应用内文件，系统下载文件未能删除"
                else -> "已删除记录和本地文件"
            }
            state.copy(
                history = visibleHistory,
                hasMoreHistory = false,
                message = message,
                availableFilterChips = getAvailableFilterChips()
            )
        }
    }

    fun deleteHistoryItemsBatch(ids: Set<String>, deleteFiles: Boolean) {
        AppLogger.event("user", "deleteHistoryItemsBatch", "count" to ids.size, "deleteFiles" to deleteFiles)
        
        val targets = allHistory.filter { item -> item.id in ids }
        allHistory = allHistory.filterNot { item -> item.id in ids }
        
        ids.forEach { id ->
            deleteThumbnailForTask(id)
        }
        
        var filesStillReferenced = false
        var exportedFilesStillReferenced = false
        var anyExportedFilesDeleted = false
        var anyExportedFilesFailed = false
        
        targets.forEach { target ->
            if (deleteFiles && target.filePath.isNotBlank()) {
                val stillReferenced = allHistory.any { item -> item.filePath == target.filePath }
                if (stillReferenced) {
                    filesStillReferenced = true
                } else {
                    File(target.filePath).delete()
                }
            }
            
            if (deleteFiles && (target.exportedPath.isNotBlank() || target.exportedUri.isNotBlank())) {
                val exportedStillReferenced = allHistory.any { item ->
                    if (target.exportedUri.isNotBlank()) {
                        item.exportedUri == target.exportedUri
                    } else {
                        item.exportedUri.isBlank() && item.exportedPath == target.exportedPath
                    }
                }
                if (exportedStillReferenced) {
                    exportedFilesStillReferenced = true
                } else {
                    val deleted = mediaFileActions.deleteExportedFile(target.exportedPath, target.exportedUri).getOrDefault(false)
                    if (deleted) {
                        anyExportedFilesDeleted = true
                    } else {
                        anyExportedFilesFailed = true
                    }
                }
            }
        }
        
        historyStore.save(allHistory)
        
        _uiState.update { state ->
            val visibleHistory = getFilteredHistory()
            val message = if (deleteFiles) {
                when {
                    filesStillReferenced || exportedFilesStillReferenced -> "已批量删除记录，部分共用文件仍保留"
                    anyExportedFilesFailed && !anyExportedFilesDeleted -> "已删除记录和应用内文件，部分系统下载文件删除失败"
                    else -> "已成功批量删除选中的记录及文件"
                }
            } else {
                "已删除选中记录"
            }
            
            state.copy(
                history = visibleHistory,
                hasMoreHistory = false,
                message = message,
                availableFilterChips = getAvailableFilterChips()
            )
        }
    }

    fun updateDefaultStorageLocation(location: StorageLocation) {
        AppLogger.event("user", "updateDefaultStorageLocation", "location" to location.value)
        appSettings.defaultStorageLocation = location
        _uiState.update {
            it.copy(
                defaultStorageLocation = location,
                message = "默认存储位置已改为：${location.label}"
            )
        }
    }

    fun updateAudioFormatPreferred(value: Int) {
        AppLogger.event("user", "updateAudioFormatPreferred", "value" to value)
        appSettings.audioFormatPreferred = value
        _uiState.update { it.copy(audioFormatPreferred = value) }
    }

    fun updateAudioQuality(value: Int) {
        AppLogger.event("user", "updateAudioQuality", "value" to value)
        appSettings.audioQuality = value
        _uiState.update { it.copy(audioQuality = value) }
    }

    fun updateVideoFormat(value: Int) {
        AppLogger.event("user", "updateVideoFormat", "value" to value)
        appSettings.videoFormat = value
        _uiState.update { it.copy(videoFormat = value) }
    }

    fun updateVideoQuality(value: Int) {
        AppLogger.event("user", "updateVideoQuality", "value" to value)
        appSettings.videoQuality = value
        _uiState.update { it.copy(videoQuality = value) }
    }

    fun updateRateLimitEnabled(enabled: Boolean) {
        AppLogger.event("user", "updateRateLimitEnabled", "enabled" to enabled)
        appSettings.rateLimitEnabled = enabled
        _uiState.update { it.copy(rateLimitEnabled = enabled) }
    }

    fun updateRateLimitValue(value: Int) {
        AppLogger.event("user", "updateRateLimitValue", "value" to value)
        appSettings.rateLimitValue = value
        _uiState.update { it.copy(rateLimitValue = value) }
    }

    fun updateCellularDownload(allowed: Boolean) {
        AppLogger.event("user", "updateCellularDownload", "allowed" to allowed)
        appSettings.cellularDownload = allowed
        _uiState.update { it.copy(cellularDownload = allowed) }
    }

    fun startYtDlpUpdate() {
        if (_uiState.value.ytDlpUpdateStatus == YtDlpUpdateStatus.Checking ||
            _uiState.value.ytDlpUpdateStatus == YtDlpUpdateStatus.Downloading ||
            _uiState.value.ytDlpUpdateStatus == YtDlpUpdateStatus.Extracting
        ) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    ytDlpUpdateStatus = YtDlpUpdateStatus.Checking,
                    ytDlpUpdateProgress = 0,
                    ytDlpUpdateMessage = "正在检查更新..."
                )
            }
            AppLogger.event("updater", "checkStart")

            try {
                val url = java.net.URL("https://pypi.org/pypi/yt-dlp/json")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.requestMethod = "GET"
                connection.connect()

                if (connection.responseCode != 200) {
                    throw Exception("HTTP error code: ${connection.responseCode}")
                }

                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = org.json.JSONObject(responseText)
                val info = json.getJSONObject("info")
                val latestVersion = info.getString("version")
                val currentVersion = downloadEngine.getEngineVersion()

                AppLogger.event("updater", "checkDone", "latest" to latestVersion, "current" to currentVersion)

                if (latestVersion == currentVersion && appSettings.ytDlpVersion.isNotBlank()) {
                    _uiState.update {
                        it.copy(
                            ytDlpUpdateStatus = YtDlpUpdateStatus.Success,
                            ytDlpUpdateMessage = "已是最新版本 ($currentVersion)",
                            message = "当前已是最新版本"
                        )
                    }
                    delay(3000)
                    _uiState.update { it.copy(ytDlpUpdateStatus = YtDlpUpdateStatus.Idle) }
                    return@launch
                }

                val urls = json.getJSONArray("urls")
                var wheelUrl: String? = null
                for (i in 0 until urls.length()) {
                    val urlObj = urls.getJSONObject(i)
                    if (urlObj.optString("packagetype") == "bdist_wheel") {
                        wheelUrl = urlObj.getString("url")
                        break
                    }
                }

                if (wheelUrl == null) {
                    throw Exception("未找到适用于 Python 的预编译 wheel 包")
                }

                _uiState.update {
                    it.copy(
                        ytDlpUpdateStatus = YtDlpUpdateStatus.Downloading,
                        ytDlpUpdateProgress = 0,
                        ytDlpUpdateMessage = "正在下载新版本 ($latestVersion)..."
                    )
                }
                AppLogger.event("updater", "downloadStart", "url" to wheelUrl)

                val downloadUrl = java.net.URL(wheelUrl)
                val dlConnection = downloadUrl.openConnection() as java.net.HttpURLConnection
                dlConnection.connectTimeout = 20000
                dlConnection.readTimeout = 20000
                dlConnection.connect()

                if (dlConnection.responseCode != 200) {
                    throw Exception("下载失败，HTTP 状态码: ${dlConnection.responseCode}")
                }

                val contentLength = dlConnection.contentLength
                val cacheFile = File(getApplication<Application>().cacheDir, "yt_dlp_update.whl")
                cacheFile.delete()

                dlConnection.inputStream.use { input ->
                    cacheFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesRead = 0L
                        var lastProgressUpdate = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            if (contentLength > 0) {
                                val progress = ((totalBytesRead * 100) / contentLength).toInt()
                                val now = SystemClock.elapsedRealtime()
                                if (now - lastProgressUpdate > 200 || progress == 100) {
                                    lastProgressUpdate = now
                                    _uiState.update {
                                        it.copy(
                                            ytDlpUpdateProgress = progress,
                                            ytDlpUpdateMessage = "正在下载... $progress%"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                _uiState.update {
                    it.copy(
                        ytDlpUpdateStatus = YtDlpUpdateStatus.Extracting,
                        ytDlpUpdateMessage = "正在解压与合并依赖包..."
                    )
                }
                AppLogger.event("updater", "extractStart")

                val tempDir = File(getApplication<Application>().cacheDir, "yt_dlp_temp_${System.nanoTime()}")
                tempDir.deleteRecursively()
                tempDir.mkdirs()

                java.util.zip.ZipInputStream(cacheFile.inputStream()).use { zipInput ->
                    var entry = zipInput.nextEntry
                    while (entry != null) {
                        val file = File(tempDir, entry.name)
                        if (entry.isDirectory) {
                            file.mkdirs()
                        } else {
                            file.parentFile?.mkdirs()
                            file.outputStream().use { out ->
                                zipInput.copyTo(out)
                            }
                        }
                        zipInput.closeEntry()
                        entry = zipInput.nextEntry
                    }
                }

                val extractedYtDlpDir = File(tempDir, "yt_dlp")
                if (!extractedYtDlpDir.exists() || !extractedYtDlpDir.isDirectory) {
                    throw Exception("解压失败，未找到 yt_dlp 核心包")
                }

                val targetUpdatesDir = File(getApplication<Application>().filesDir, "python_updates")
                targetUpdatesDir.mkdirs()
                val targetYtDlpDir = File(targetUpdatesDir, "yt_dlp")
                targetYtDlpDir.deleteRecursively()

                val renameSuccess = extractedYtDlpDir.renameTo(targetYtDlpDir)
                if (!renameSuccess) {
                    extractedYtDlpDir.copyRecursively(targetYtDlpDir, overwrite = true)
                }

                tempDir.deleteRecursively()
                cacheFile.delete()

                AppLogger.event("updater", "success", "version" to latestVersion)
                appSettings.ytDlpVersion = latestVersion
                appSettings.ytDlpLastUpdate = System.currentTimeMillis()

                _uiState.update {
                    it.copy(
                        ytDlpUpdateStatus = YtDlpUpdateStatus.Success,
                        ytDlpVersion = latestVersion,
                        ytDlpUpdateMessage = "更新成功！当前版本: $latestVersion",
                        message = "yt-dlp 引擎更新成功！重启应用后生效"
                    )
                }

                delay(5000)
                _uiState.update { it.copy(ytDlpUpdateStatus = YtDlpUpdateStatus.Idle) }

            } catch (e: Exception) {
                AppLogger.error("updater", "failure", e)
                val errorMsg = e.message ?: "未知网络错误"
                _uiState.update {
                    it.copy(
                        ytDlpUpdateStatus = YtDlpUpdateStatus.Error,
                        ytDlpUpdateMessage = "更新失败: $errorMsg",
                        message = "更新失败: $errorMsg"
                    )
                }
                delay(5000)
                _uiState.update { it.copy(ytDlpUpdateStatus = YtDlpUpdateStatus.Idle) }
            }
        }
    }

    private fun deleteThumbnailForTask(taskId: String) {
        val thumbnailDir = File(getApplication<Application>().filesDir, "thumbnails")
        val legacyDeleted = File(thumbnailDir, "$taskId.jpg").delete()
        val sixteenByNineDeleted = File(thumbnailDir, "${taskId}_16x9.jpg").delete()
        val historyThumbnailDeleted = File(thumbnailDir, "${taskId}_104x73.jpg").delete()
        val historyMetadataDeleted = File(thumbnailDir, "${taskId}_104x73.json").delete()
        val v3ThumbnailDeleted = File(thumbnailDir, "${taskId}_full_v3.jpg").delete()
        val v3MetadataDeleted = File(thumbnailDir, "${taskId}_full_v3.json").delete()
        val fullMetaDeleted = File(thumbnailDir, "${taskId}_full_meta.json").delete()
        val legacyV2ThumbnailDeleted = File(thumbnailDir, "${taskId}_full_v2.jpg").delete()
        val legacyV2MetadataDeleted = File(thumbnailDir, "${taskId}_full_v2.json").delete()
        AppLogger.event(
            "history",
            "deleteThumbnail",
            "taskId" to taskId,
            "legacyDeleted" to legacyDeleted,
            "sixteenByNineDeleted" to sixteenByNineDeleted,
            "historyThumbnailDeleted" to historyThumbnailDeleted,
            "historyMetadataDeleted" to historyMetadataDeleted,
            "v3ThumbnailDeleted" to v3ThumbnailDeleted,
            "v3MetadataDeleted" to v3MetadataDeleted,
            "fullMetaDeleted" to fullMetaDeleted,
            "legacyV2ThumbnailDeleted" to legacyV2ThumbnailDeleted,
            "legacyV2MetadataDeleted" to legacyV2MetadataDeleted
        )
    }

    fun openTask(task: DownloadTask) {
        AppLogger.event("user", "openInternalPlayer", "taskId" to task.id, "filePath" to task.filePath)
        if (task.status != DownloadStatus.Completed || task.filePath.isBlank()) {
            _uiState.update { it.copy(message = "文件还不可播放") }
            return
        }
        val mediaFile = File(task.filePath)
        if (!mediaFile.exists() || !mediaFile.isFile || !mediaFile.canRead()) {
            AppLogger.warn(
                "file",
                "openInternalPlayerMissingFile",
                "taskId" to task.id,
                "filePath" to task.filePath,
                "exists" to mediaFile.exists(),
                "isFile" to mediaFile.isFile,
                "canRead" to mediaFile.canRead()
            )
            _uiState.update { it.copy(message = "本地文件不存在") }
            return
        }
        _uiState.update { it.copy(playerTask = task, message = null) }
    }

    fun closePlayer() {
        AppLogger.event("user", "closeInternalPlayer", "taskId" to _uiState.value.playerTask?.id)
        _uiState.update { it.copy(playerTask = null) }
    }

    fun updatePlaybackProgress(taskId: String, positionMs: Long, durationMs: Long) {
        val existing = allHistory.firstOrNull { item -> item.id == taskId } ?: return
        val normalizedDuration = durationMs.coerceAtLeast(0L)
        val boundedPosition = when {
            normalizedDuration > 0L -> positionMs.coerceIn(0L, normalizedDuration)
            else -> positionMs.coerceAtLeast(0L)
        }
        val shouldClear = normalizedDuration > 0L &&
            (normalizedDuration - boundedPosition <= playbackCompletionClearThresholdMs ||
                boundedPosition >= (normalizedDuration * playbackCompletionClearRatio).toLong())
        val savedPosition = when {
            shouldClear -> 0L
            boundedPosition < playbackMinSavePositionMs -> 0L
            else -> boundedPosition
        }
        if (
            existing.playbackPositionMs == savedPosition &&
            existing.playbackDurationMs == normalizedDuration
        ) {
            return
        }

        val updatedAt = if (savedPosition > 0L) System.currentTimeMillis() else 0L
        allHistory = allHistory.map { item ->
            if (item.id == taskId) {
                item.copy(
                    playbackPositionMs = savedPosition,
                    playbackDurationMs = normalizedDuration,
                    playbackUpdatedAt = updatedAt
                )
            } else {
                item
            }
        }
        historyStore.save(allHistory)
        _uiState.update { state ->
            val visibleHistory = getFilteredHistory()
            state.copy(
                history = visibleHistory,
                hasMoreHistory = false,
                playerTask = state.playerTask?.let { playerTask ->
                    if (playerTask.id == taskId) {
                        playerTask.copy(
                            playbackPositionMs = savedPosition,
                            playbackDurationMs = normalizedDuration,
                            playbackUpdatedAt = updatedAt
                        )
                    } else {
                        playerTask
                    }
                },
                availableFilterChips = getAvailableFilterChips()
            )
        }
        AppLogger.event(
            "player",
            "progressSaved",
            "taskId" to taskId,
            "positionMs" to savedPosition,
            "durationMs" to normalizedDuration
        )
    }

    fun openShareMenu(task: DownloadTask) {
        AppLogger.event("user", "openShareMenu", "taskId" to task.id)
        _uiState.update {
            it.copy(
                shareTask = task,
                shareTargets = emptyList(),
                isLoadingShareTargets = true
            )
        }
        viewModelScope.launch {
            val fileSize = withContext(Dispatchers.IO) {
                File(task.filePath).takeIf { it.exists() && it.isFile }?.length() ?: 0L
            }
            val displayTask = if (fileSize > 0L && task.totalBytes <= 0L) {
                task.copy(downloadedBytes = fileSize, totalBytes = fileSize)
            } else {
                task
            }
            if (displayTask !== task) {
                allHistory = allHistory.map { item ->
                    if (item.id == task.id) displayTask else item
                }
                _uiState.update { state ->
                    state.copy(shareTask = displayTask)
                }
                withContext(Dispatchers.IO) {
                    historyStore.save(allHistory)
                }
                updateHistoryState()
            }
            withContext(Dispatchers.IO) {
                mediaFileActions.shareTargets(displayTask.filePath, displayTask.title)
            }
                .onSuccess { targets ->
                    _uiState.update {
                        it.copy(
                            shareTask = displayTask,
                            shareTargets = targets,
                            isLoadingShareTargets = false
                        )
                    }
                }
                .onFailure { error ->
                    AppLogger.error("file", "openShareMenuFailed", error, "taskId" to task.id, "filePath" to task.filePath)
                    _uiState.update {
                        it.copy(
                            isLoadingShareTargets = false,
                            message = error.message ?: "无法加载分享应用"
                        )
                    }
                }
        }
    }

    fun dismissShareMenu() {
        AppLogger.event("user", "dismissShareMenu")
        _uiState.update { it.copy(shareTask = null, shareTargets = emptyList(), isLoadingShareTargets = false) }
    }

    fun showToast(msg: String) {
        _uiState.update { it.copy(message = msg) }
    }

    fun shareToTarget(task: DownloadTask, target: ShareTarget) {
        AppLogger.event(
            "user",
            "shareToTarget",
            "taskId" to task.id,
            "package" to target.packageName,
            "activity" to target.activityName
        )
        mediaFileActions.shareToTarget(task.filePath, task.title, target)
            .onFailure { error ->
                AppLogger.error(
                    "file",
                    "shareToTargetFailed",
                    error,
                    "taskId" to task.id,
                    "package" to target.packageName,
                    "activity" to target.activityName
                )
                mediaFileActions.share(task.filePath, task.title)
                    .onFailure { fallbackError ->
                        AppLogger.error(
                            "file",
                            "shareToTargetFallbackFailed",
                            fallbackError,
                            "taskId" to task.id,
                            "package" to target.packageName
                        )
                        _uiState.update { it.copy(message = fallbackError.message ?: error.message ?: "分享失败") }
                    }
            }
    }

    fun openTaskExternally(task: DownloadTask) {
        AppLogger.event("user", "openDownloadedFile", "taskId" to task.id, "filePath" to task.filePath)
        mediaFileActions.open(task.filePath)
            .onFailure { error ->
                AppLogger.error("file", "openDownloadedFileFailed", error, "taskId" to task.id, "filePath" to task.filePath)
                _uiState.update { it.copy(message = error.message ?: "无法打开文件") }
            }
    }

    fun shareTask(task: DownloadTask) {
        AppLogger.event("user", "shareDownloadedFile", "taskId" to task.id, "filePath" to task.filePath)
        mediaFileActions.share(task.filePath, task.title)
            .onFailure { error ->
                AppLogger.error("file", "shareDownloadedFileFailed", error, "taskId" to task.id, "filePath" to task.filePath)
                _uiState.update { it.copy(message = error.message ?: "无法分享文件") }
            }
    }

    fun exportTask(task: DownloadTask) {
        AppLogger.event("user", "exportDownloadedFile", "taskId" to task.id, "filePath" to task.filePath)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                mediaFileActions.exportToDownloads(task.filePath)
            }
                .onSuccess { exportedFile ->
                    AppLogger.event(
                        "file",
                        "exportDownloadedFileSuccess",
                        "taskId" to task.id,
                        "destination" to exportedFile.displayPath,
                        "exportedUri" to exportedFile.contentUri.ifBlank { "blank" }
                    )
                    allHistory = allHistory.map { item ->
                        if (item.id == task.id) {
                            item.copy(
                                exportedPath = exportedFile.displayPath,
                                exportedUri = exportedFile.contentUri
                            )
                        } else {
                            item
                        }
                    }
                    historyStore.save(allHistory)
                    _uiState.update { state ->
                        val visibleHistory = getFilteredHistory()
                        state.copy(
                            history = visibleHistory,
                            hasMoreHistory = false,
                            message = "已保存到 ${exportedFile.displayPath}",
                            availableFilterChips = getAvailableFilterChips()
                        )
                    }
                }
                .onFailure { error ->
                    AppLogger.error("file", "exportDownloadedFileFailed", error, "taskId" to task.id, "filePath" to task.filePath)
                    _uiState.update { it.copy(message = error.message ?: "无法保存到系统下载目录") }
                }
        }
    }

    private fun nowLabel(): String {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }

    private fun handleDownloadServiceUpdate(intent: Intent?) {
        if (intent == null) {
            AppLogger.warn("serviceUpdate", "nullIntent")
            return
        }
        AppLogger.event("serviceUpdate", "received", "action" to intent.action)

        if (intent.action == DownloadServiceContract.ACTION_QUEUE_UPDATED) {
            val message = intent.getStringExtra(DownloadServiceContract.EXTRA_MESSAGE)
            val queue = parseDownloadQueue(
                intent.getStringExtra(DownloadServiceContract.EXTRA_QUEUE).orEmpty(),
                ::nowLabel
            )
            AppLogger.event("serviceUpdate", "queueUpdated", "count" to queue.size, "message" to message)
            var queueToSave: List<DownloadTask>? = null
            _uiState.update { state ->
                val activeDownloadStillRunning = state.activeTask?.status in setOf(
                    DownloadStatus.Downloading,
                    DownloadStatus.Processing
                )
                val ignoreStaleFinishedQueue = queue.isEmpty() &&
                    message == "队列已完成" &&
                    state.queuedTasks.isNotEmpty() &&
                    activeDownloadStillRunning
                if (ignoreStaleFinishedQueue) {
                    AppLogger.warn(
                        "serviceUpdate",
                        "ignoreStaleQueueFinished",
                        "localQueueCount" to state.queuedTasks.size,
                        "activeTaskId" to state.activeTask?.id
                    )
                    return@update state
                }
                queueToSave = queue
                state.copy(
                    queuedTasks = queue,
                    message = if (queue.isEmpty() && message == "队列已完成") state.message else message
                )
            }
            queueToSave?.let { pendingQueueStore.save(it) }
            return
        }

        val task = intent.toDownloadTaskOrNull(::nowLabel) ?: return
        val message = intent.getStringExtra(DownloadServiceContract.EXTRA_MESSAGE)
        AppLogger.event(
            "serviceUpdate",
            "taskUpdated",
            "taskId" to task.id,
            "status" to task.status.name,
            "stage" to task.stage.name,
            "progress" to task.progress,
            "message" to message
        )
        var updatedQueueToSave: List<DownloadTask>? = null
        _uiState.update { state ->
            val history = if (task.status == DownloadStatus.Completed) {
                allHistory = historyStore.load()
                getFilteredHistory()
            } else {
                state.history
            }
            val hasMore = false
            val activeTask = when (task.status) {
                DownloadStatus.Queued -> state.activeTask
                else -> task
            }
            val updatedQueue = state.queuedTasks.filterNot { it.id == task.id }
            updatedQueueToSave = updatedQueue
            state.copy(
                activeTask = activeTask,
                queuedTasks = updatedQueue,
                history = history,
                hasMoreHistory = hasMore,
                availableFilterChips = if (task.status == DownloadStatus.Completed) {
                    getAvailableFilterChips()
                } else {
                    state.availableFilterChips
                },
                message = message
            )
        }
        updatedQueueToSave?.let { pendingQueueStore.save(it) }
        if (task.status == DownloadStatus.Failed && appSettings.hasCookieForUrl(task.sourceUrl)) {
            AppLogger.event(
                "cookie",
                "savedCookieAlreadyAppliedNoAutomaticRetry",
                "taskId" to task.id,
                "url" to task.sourceUrl,
                "error" to task.errorMessage
            )
        }
    }

    private fun String.isYouTubeJsChallengeFailure(): Boolean {
        val lower = lowercase()
        return ("youtube" in lower && "js" in lower && "解码" in lower) ||
            "n challenge" in lower ||
            "javascript runtime" in lower ||
            "ejs" in lower
    }

    private fun String.isYouTubeLikeUrl(): Boolean {
        val host = runCatching { android.net.Uri.parse(this).host.orEmpty().lowercase() }.getOrDefault("")
            .removePrefix("www.")
            .removePrefix("m.")
            .removePrefix("music.")
        return host == "youtube.com" || host == "youtu.be" || host.endsWith(".youtube.com")
    }

    private fun String.isTwitterLikeUrl(): Boolean {
        val host = runCatching { android.net.Uri.parse(this).host.orEmpty().lowercase() }.getOrDefault("")
            .removePrefix("www.")
            .removePrefix("m.")
        return host == "x.com" ||
            host.endsWith(".x.com") ||
            host == "twitter.com" ||
            host.endsWith(".twitter.com")
    }

    override fun onCleared() {
        AppLogger.event("viewmodel", "onCleared")
        getApplication<Application>().unregisterReceiver(serviceReceiver)
        super.onCleared()
    }
}

enum class YtDlpUpdateStatus {
    Idle, Checking, Downloading, Extracting, Success, Error
}

@Immutable
data class MediaDownloaderUiState(
    val inputUrl: String = "",
    val selectedTab: AppTab = AppTab.Home,
    val activeTask: DownloadTask? = null,
    val queuedTasks: List<DownloadTask> = emptyList(),
    val history: List<DownloadTask> = emptyList(),
    val hasMoreHistory: Boolean = false,
    val isResolvingFormats: Boolean = false,
    val pendingStage: DownloadStage? = null,
    val formatOptions: List<DownloadFormatOption> = emptyList(),
    val selectedFormat: DownloadFormatOption? = null,
    val pendingUrl: String = "",
    val pendingTitle: String = "",
    val pendingThumbnailUrl: String = "",
    val formatResolveWarning: String? = null,
    val defaultStorageLocation: StorageLocation = StorageLocation.AppPrivate,
    val audioFormatPreferred: Int = 0,
    val audioQuality: Int = 0,
    val videoFormat: Int = 1,
    val videoQuality: Int = 0,
    val rateLimitEnabled: Boolean = false,
    val rateLimitValue: Int = 500,
    val cellularDownload: Boolean = true,
    val ytDlpVersion: String = "",
    val ytDlpUpdateStatus: YtDlpUpdateStatus = YtDlpUpdateStatus.Idle,
    val ytDlpUpdateProgress: Int = 0, // 0-100
    val ytDlpUpdateMessage: String = "",
    val cookieTask: DownloadTask? = null,
    val cookieInput: String = "",
    val playerTask: DownloadTask? = null,
    val shareTask: DownloadTask? = null,
    val shareTargets: List<ShareTarget> = emptyList(),
    val isLoadingShareTargets: Boolean = false,
    val message: String? = null,
    val availableFilterChips: List<HistoryFilterChip> = emptyList()
)

data class HistoryFilterChip(
    val filterKey: String?,
    val displayName: String,
    val iconRes: Int?
)

private const val STARTUP_DATA_LOAD_DELAY_MS = 2_200L
private const val ENGINE_VERSION_STARTUP_DELAY_MS = 3_000L
