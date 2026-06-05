package com.pixelpoint.mediadownloader

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.pixelpoint.mediadownloader.engine.EngineProgress
import com.pixelpoint.mediadownloader.engine.LocalDownloadEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MediaDownloadService : Service() {
    private val TAG = "MediaDownloadService"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var appSettings: AppSettings
    private lateinit var mediaFileActions: MediaFileActions
    private lateinit var downloadEngine: LocalDownloadEngine
    private lateinit var historyStore: HistoryStore
    private lateinit var pendingQueueStore: PendingQueueStore
    private lateinit var notificationController: DownloadNotificationController
    private var activeTask: DownloadTask? = null
    private val queue = ArrayDeque<DownloadTask>()
    private var cancelRequested = false

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(applicationContext)
        AppLogger.event("service", "onCreate")
        appSettings = AppSettings(this)
        mediaFileActions = MediaFileActions(this)
        downloadEngine = LocalDownloadEngine(this)
        historyStore = HistoryStore(this)
        pendingQueueStore = PendingQueueStore(this)
        notificationController = DownloadNotificationController(this)
        notificationController.ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.event(
            "service",
            "onStartCommand",
            "action" to intent?.action,
            "startId" to startId,
            "queueSize" to queue.size,
            "activeTaskId" to activeTask?.id
        )
        when (intent?.action) {
            DownloadServiceContract.ACTION_CANCEL -> cancelDownload()
            DownloadServiceContract.ACTION_START -> startDownload(intent)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        AppLogger.event("service", "onDestroy", "activeTaskId" to activeTask?.id, "queueSize" to queue.size)
        if (activeTask != null) {
            AppLogger.warn("service", "cancelActiveDownloadOnDestroy", "activeTaskId" to activeTask?.id)
            downloadEngine.cancelActiveDownload()
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun startDownload(intent: Intent) {
        val url = intent.getStringExtra(DownloadServiceContract.EXTRA_URL).orEmpty()
        val taskId = intent.getStringExtra(DownloadServiceContract.EXTRA_TASK_ID).orEmpty()
        val taskTitle = intent.getStringExtra(DownloadServiceContract.EXTRA_TITLE).orEmpty()
        val thumbnailUrl = intent.getStringExtra(DownloadServiceContract.EXTRA_THUMBNAIL_URL).orEmpty()
        val formatSelector = intent.getStringExtra(DownloadServiceContract.EXTRA_FORMAT_SELECTOR).orEmpty()
        val formatLabel = intent.getStringExtra(DownloadServiceContract.EXTRA_FORMAT_LABEL).orEmpty()
        val requiresMediaMerge = intent.getBooleanExtra(DownloadServiceContract.EXTRA_REQUIRES_MEDIA_MERGE, false)
        if (url.isBlank() || taskId.isBlank()) {
            AppLogger.warn("service", "invalidStartRequest", "taskId" to taskId, "url" to url)
            Log.w(TAG, "Ignoring invalid start request. taskId=$taskId, url=$url")
            stopSelf()
            return
        }
        AppLogger.event(
            "service",
            "startRequest",
            "taskId" to taskId,
            "url" to url,
            "title" to taskTitle,
            "thumbnail" to thumbnailUrl.ifBlank { "blank" },
            "formatSelector" to formatSelector,
            "formatLabel" to formatLabel,
            "requiresMediaMerge" to requiresMediaMerge
        )
        Log.i(TAG, "Start request. taskId=$taskId, url=$url, selector=$formatSelector, label=$formatLabel")

        val incoming = DownloadTask(
            id = taskId,
            title = taskTitle.ifBlank { "媒体链接" },
            sourceUrl = url,
            progress = 0f,
            status = DownloadStatus.Queued,
            stage = DownloadStage.Queued,
            createdAt = nowLabel(),
            formatSelector = formatSelector,
            formatLabel = formatLabel,
            thumbnailUrl = thumbnailUrl,
            requiresMediaMerge = requiresMediaMerge
        )

        if (activeTask != null) {
            queue.addLast(incoming)
            pendingQueueStore.save(queue.toList())
            AppLogger.event("service", "taskQueued", "taskId" to incoming.id, "queueSize" to queue.size)
            sendQueueUpdate("已加入等待队列")
            return
        }

        runTask(incoming)
    }

    private fun runTask(incoming: DownloadTask) {
        Log.i(TAG, "Running task. taskId=${incoming.id}, url=${incoming.sourceUrl}, selector=${incoming.formatSelector}")
        AppLogger.event(
            "service",
            "runTask",
            "taskId" to incoming.id,
            "url" to incoming.sourceUrl,
            "formatSelector" to incoming.formatSelector,
            "requiresMediaMerge" to incoming.requiresMediaMerge,
            "queueSize" to queue.size
        )
        val task = incoming.copy(
            progress = 0.02f,
            status = DownloadStatus.Downloading,
            stage = DownloadStage.Downloading
        )
        activeTask = task
        cancelRequested = false

        notificationController.startForeground(task, "正在下载媒体流", indeterminate = true)
        sendTaskUpdate(DownloadServiceContract.ACTION_TASK_STARTED, task, "后台下载已开始")

        scope.launch {
            if (!appSettings.cellularDownload) {
                val connManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                if (connManager.isActiveNetworkMetered) {
                    AppLogger.warn("service", "cellularBlocked", "taskId" to task.id)
                    failTask(task, "当前处于移动数据网络，已在设置中禁用移动数据下载")
                    activeTask = null
                    runNextOrStop()
                    return@launch
                }
            }

            if (task.requiresMediaMerge) {
                AppLogger.event("service", "mergeCapabilityCheckStart", "taskId" to task.id, "formatSelector" to task.formatSelector)
                if (!downloadEngine.canMergeMedia()) {
                    AppLogger.warn("service", "mergeCapabilityUnavailable", "taskId" to task.id, "formatSelector" to task.formatSelector)
                    failTask(task, "该清晰度需要合并音视频，但当前设备处理组件不可用")
                    activeTask = null
                    runNextOrStop()
                    return@launch
                }
                AppLogger.event("service", "mergeCapabilityAvailable", "taskId" to task.id)
            }
            val cookieHeader = appSettings.cookieForUrl(task.sourceUrl)
            val refererHeader = appSettings.refererForUrl(task.sourceUrl)
            val rateLimitBytes = if (appSettings.rateLimitEnabled) {
                appSettings.rateLimitValue.toLong() * 1024L
            } else {
                0L
            }
            AppLogger.event(
                "service",
                "downloadEngineStart",
                "taskId" to task.id,
                "cookie" to AppLogger.cookieSummary(cookieHeader),
                "referer" to refererHeader.ifBlank { "blank" },
                "rateLimitBytes" to rateLimitBytes
            )
            val result = downloadEngine.download(
                url = task.sourceUrl,
                formatSelector = task.formatSelector,
                cookieHeader = cookieHeader,
                refererHeader = refererHeader,
                requireAudio = task.requiresAudioValidation(),
                rateLimitBytes = rateLimitBytes,
                onProgress = { progress ->
                    if (!cancelRequested) {
                        publishProgress(task, progress)
                    }
                },
                onProcessing = {
                    if (!cancelRequested) {
                        publishProcessing(task)
                    }
                }
            )
            Log.i(TAG, "Task result. taskId=${task.id}, ok=${result.ok}, file=${result.filePath}, error=${result.error}")
            AppLogger.event(
                "service",
                "downloadEngineResult",
                "taskId" to task.id,
                "ok" to result.ok,
                "filePath" to result.filePath,
                "engineVersion" to result.engineVersion,
                "error" to result.error
            )
            if (result.ok) {
                val completedFileSize = File(result.filePath).takeIf { it.exists() && it.isFile }?.length() ?: 0L
                var completed = task.copy(
                    title = completedTaskTitle(task, result.title),
                    filePath = result.filePath,
                    progress = 1f,
                    status = DownloadStatus.Completed,
                    stage = DownloadStage.Completed,
                    downloadedBytes = completedFileSize,
                    totalBytes = completedFileSize,
                    speedBytesPerSecond = 0L,
                    engineVersion = result.engineVersion
                )
                var completedMessage = "下载完成，本地引擎版本：${result.engineVersion}"
                if (appSettings.defaultStorageLocation == StorageLocation.Downloads) {
                    AppLogger.event("service", "autoExportStart", "taskId" to completed.id, "filePath" to completed.filePath)
                    mediaFileActions.exportToDownloads(completed.filePath)
                        .onSuccess { exportedFile ->
                            AppLogger.event(
                                "service",
                                "autoExportSuccess",
                                "taskId" to completed.id,
                                "exportedPath" to exportedFile.displayPath,
                                "exportedUri" to exportedFile.contentUri.ifBlank { "blank" }
                            )
                            completed = completed.copy(
                                exportedPath = exportedFile.displayPath,
                                exportedUri = exportedFile.contentUri
                            )
                            completedMessage = "下载完成，已保存到 ${exportedFile.displayPath}"
                        }
                        .onFailure { error ->
                            AppLogger.error("service", "autoExportFailure", error, "taskId" to completed.id, "filePath" to completed.filePath)
                            completedMessage = "下载完成，但自动保存到系统下载目录失败：${error.message ?: "未知错误"}"
                        }
                } else if (appSettings.defaultStorageLocation == StorageLocation.Custom) {
                    val customUri = appSettings.customStorageUri
                    if (customUri.isNotBlank()) {
                        AppLogger.event("service", "autoExportCustomStart", "taskId" to completed.id, "filePath" to completed.filePath, "customUri" to customUri)
                        mediaFileActions.exportToCustomDirectory(completed.filePath, customUri)
                            .onSuccess { exportedFile ->
                                AppLogger.event(
                                    "service",
                                    "autoExportCustomSuccess",
                                    "taskId" to completed.id,
                                    "exportedPath" to exportedFile.displayPath,
                                    "exportedUri" to exportedFile.contentUri.ifBlank { "blank" }
                                )
                                completed = completed.copy(
                                    exportedPath = exportedFile.displayPath,
                                    exportedUri = exportedFile.contentUri
                                )
                                completedMessage = "下载完成，已保存到自定义目录"
                            }
                            .onFailure { error ->
                                AppLogger.error("service", "autoExportCustomFailure", error, "taskId" to completed.id, "filePath" to completed.filePath)
                                completedMessage = "下载完成，但保存到自定义目录失败：${error.message ?: "未知错误"}"
                            }
                    }
                }
                saveCompletedTask(completed)
                activeTask = completed
                AppLogger.event("service", "taskCompleted", "taskId" to completed.id, "filePath" to completed.filePath)
                notificationController.update(completed, "下载完成", indeterminate = false)
                sendTaskUpdate(DownloadServiceContract.ACTION_TASK_COMPLETED, completed, completedMessage)
            } else if (cancelRequested || result.error == "下载已取消") {
                val cancelled = task.copy(
                    progress = 0f,
                    status = DownloadStatus.Cancelled,
                    stage = DownloadStage.Cancelled,
                    errorMessage = "下载已取消"
                )
                activeTask = cancelled
                AppLogger.warn("service", "taskCancelled", "taskId" to cancelled.id, "error" to result.error)
                notificationController.update(cancelled, "下载已取消", indeterminate = false)
                sendTaskUpdate(DownloadServiceContract.ACTION_TASK_CANCELLED, cancelled, "下载已取消")
            } else {
                failTask(task, result.error)
            }

            activeTask = null
            runNextOrStop()
        }
    }

    private fun failTask(task: DownloadTask, rawError: String) {
        val friendlyError = DownloadFailureClassifier.classify(rawError)
        AppLogger.warn("service", "taskFailed", "taskId" to task.id, "rawError" to rawError, "friendlyError" to friendlyError)
        Log.w(TAG, "Task failed. taskId=${task.id}, raw=$rawError, friendly=$friendlyError")
        val failed = task.copy(
            progress = activeTask?.progress ?: task.progress,
            status = DownloadStatus.Failed,
            stage = DownloadStage.Failed,
            errorMessage = friendlyError
        )
        activeTask = failed
        notificationController.update(failed, failed.errorMessage, indeterminate = false)
        sendTaskUpdate(DownloadServiceContract.ACTION_TASK_FAILED, failed, failed.errorMessage)
    }

    private fun cancelDownload() {
        val cancelled = activeTask?.copy(status = DownloadStatus.Cancelled, stage = DownloadStage.Cancelled, progress = 0f)
        cancelRequested = true
        AppLogger.event("service", "cancelRequested", "activeTaskId" to activeTask?.id, "queueSize" to queue.size)
        downloadEngine.cancelActiveDownload()
        activeTask = cancelled
        if (cancelled != null) {
            notificationController.update(cancelled, "下载已取消", indeterminate = false)
            sendTaskUpdate(DownloadServiceContract.ACTION_TASK_CANCELLED, cancelled, "下载已取消")
        }
    }

    private fun runNextOrStop() {
        val next = queue.removeFirstOrNull()
        pendingQueueStore.save(queue.toList())
        if (next == null) {
            AppLogger.event("service", "queueFinished")
            sendQueueUpdate("队列已完成")
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
            return
        }

        AppLogger.event("service", "runNextTask", "taskId" to next.id, "remainingQueueSize" to queue.size)
        sendQueueUpdate("开始下一个下载任务")
        runTask(next)
    }

    private fun publishProgress(baseTask: DownloadTask, progress: EngineProgress) {
        val previousValue = activeTask?.takeIf { it.id == baseTask.id }?.progress ?: 0f
        val measuredProgress = if (progress.totalBytes > 0 && progress.downloadedBytes in 0 until progress.totalBytes) {
            progress.downloadedBytes.toFloat() / progress.totalBytes.toFloat()
        } else {
            progress.progress
        }
        val value = maxOf(
            previousValue,
            measuredProgress.coerceIn(0.02f, 0.98f)
        )
        val task = baseTask.copy(
            progress = value,
            status = DownloadStatus.Downloading,
            stage = DownloadStage.Downloading,
            downloadedBytes = progress.downloadedBytes,
            totalBytes = progress.totalBytes,
            speedBytesPerSecond = progress.speedBytesPerSecond
        )
        activeTask = task
        val message = DownloadProgressText.format(progress)
        AppLogger.event(
            "service",
            "progress",
            "taskId" to task.id,
            "progress" to task.progress,
            "downloadedBytes" to task.downloadedBytes,
            "totalBytes" to task.totalBytes,
            "speedBytesPerSecond" to task.speedBytesPerSecond
        )
        notificationController.update(task, message, indeterminate = progress.totalBytes <= 0)
        sendTaskUpdate(DownloadServiceContract.ACTION_TASK_PROGRESS, task, message)
    }

    private fun publishProcessing(baseTask: DownloadTask) {
        val current = activeTask?.takeIf { it.id == baseTask.id } ?: baseTask
        val task = current.copy(status = DownloadStatus.Processing, stage = DownloadStage.Validating)
        activeTask = task
        AppLogger.event("service", "validating", "taskId" to task.id, "stage" to task.stage.name, "progress" to task.progress)
        notificationController.update(task, "正在校验媒体文件", indeterminate = true)
        sendTaskUpdate(DownloadServiceContract.ACTION_TASK_PROGRESS, task, "正在校验媒体文件")
    }

    private fun saveCompletedTask(task: DownloadTask) {
        val history = listOf(task) + historyStore.load().filterNot { it.id == task.id }
        AppLogger.event("service", "saveCompletedTask", "taskId" to task.id, "historyCount" to history.size)
        historyStore.save(history)
    }

    private fun sendTaskUpdate(action: String, task: DownloadTask, message: String) {
        AppLogger.event("service", "sendTaskUpdate", "action" to action, "taskId" to task.id, "status" to task.status.name, "stage" to task.stage.name, "message" to message)
        val intent = Intent(action)
            .setPackage(packageName)
            .putDownloadTask(task, message)
        sendBroadcast(intent)
    }

    private fun sendQueueUpdate(message: String) {
        AppLogger.event("service", "sendQueueUpdate", "queueSize" to queue.size, "message" to message)
        val intent = Intent(DownloadServiceContract.ACTION_QUEUE_UPDATED)
            .setPackage(packageName)
            .putExtra(DownloadServiceContract.EXTRA_QUEUE, queue.toDownloadQueueJson())
            .putExtra(DownloadServiceContract.EXTRA_MESSAGE, message)
        sendBroadcast(intent)
    }

    private fun nowLabel(): String {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }

    private fun completedTaskTitle(task: DownloadTask, engineTitle: String): String {
        if (task.sourceUrl.isCapturedMediaStreamUrl() && task.title.isNotBlank()) {
            return task.title
        }
        return engineTitle.ifBlank { task.title }
    }

    private fun String.isCapturedMediaStreamUrl(): Boolean {
        val value = lowercase()
        return listOf(
            ".m3u8",
            ".mp4",
            ".mpd",
            ".m4s",
            "douyinvod.com",
            "phncdn.com",
            "phncdn.net",
            "/aweme/v1/play",
            "googlevideo.com/videoplayback"
        ).any { marker -> marker in value }
    }

    private fun DownloadTask.requiresAudioValidation(): Boolean {
        val value = sourceUrl.lowercase()
        return requiresMediaMerge ||
            ("googlevideo.com/videoplayback" in value && "mime=video" in value)
    }

    companion object {
        fun start(
            context: Context,
            taskId: String,
            url: String,
            title: String,
            thumbnailUrl: String,
            formatSelector: String,
            formatLabel: String,
            requiresMediaMerge: Boolean
        ) {
            val intent = Intent(context, MediaDownloadService::class.java)
                .setAction(DownloadServiceContract.ACTION_START)
                .putExtra(DownloadServiceContract.EXTRA_TASK_ID, taskId)
                .putExtra(DownloadServiceContract.EXTRA_URL, url)
                .putExtra(DownloadServiceContract.EXTRA_TITLE, title)
                .putExtra(DownloadServiceContract.EXTRA_THUMBNAIL_URL, thumbnailUrl)
                .putExtra(DownloadServiceContract.EXTRA_FORMAT_SELECTOR, formatSelector)
                .putExtra(DownloadServiceContract.EXTRA_FORMAT_LABEL, formatLabel)
                .putExtra(DownloadServiceContract.EXTRA_REQUIRES_MEDIA_MERGE, requiresMediaMerge)
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            val intent = Intent(context, MediaDownloadService::class.java).setAction(DownloadServiceContract.ACTION_CANCEL)
            context.startService(intent)
        }
    }
}
