package com.pixelpoint.mediadownloader

import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

fun Intent.putDownloadTask(task: DownloadTask, message: String): Intent {
    AppLogger.event("payload", "putDownloadTask", "taskId" to task.id, "status" to task.status.name, "message" to message)
    return putExtra(DownloadServiceContract.EXTRA_TASK_ID, task.id)
        .putExtra(DownloadServiceContract.EXTRA_TITLE, task.title)
        .putExtra(DownloadServiceContract.EXTRA_URL, task.sourceUrl)
        .putExtra(DownloadServiceContract.EXTRA_FILE_PATH, task.filePath)
        .putExtra(DownloadServiceContract.EXTRA_ENGINE_VERSION, task.engineVersion)
        .putExtra(DownloadServiceContract.EXTRA_ERROR, task.errorMessage)
        .putExtra(DownloadServiceContract.EXTRA_STATUS, task.status.name)
        .putExtra(DownloadServiceContract.EXTRA_STAGE, task.stage.name)
        .putExtra(DownloadServiceContract.EXTRA_CREATED_AT, task.createdAt)
        .putExtra(DownloadServiceContract.EXTRA_PROGRESS, task.progress)
        .putExtra(DownloadServiceContract.EXTRA_DOWNLOADED_BYTES, task.downloadedBytes)
        .putExtra(DownloadServiceContract.EXTRA_TOTAL_BYTES, task.totalBytes)
        .putExtra(DownloadServiceContract.EXTRA_SPEED_BYTES_PER_SECOND, task.speedBytesPerSecond)
        .putExtra(DownloadServiceContract.EXTRA_FORMAT_SELECTOR, task.formatSelector)
        .putExtra(DownloadServiceContract.EXTRA_FORMAT_LABEL, task.formatLabel)
        .putExtra(DownloadServiceContract.EXTRA_THUMBNAIL_URL, task.thumbnailUrl)
        .putExtra(DownloadServiceContract.EXTRA_REQUIRES_MEDIA_MERGE, task.requiresMediaMerge)
        .putExtra(DownloadServiceContract.EXTRA_EXPORTED_PATH, task.exportedPath)
        .putExtra(DownloadServiceContract.EXTRA_EXPORTED_URI, task.exportedUri)
        .putExtra(DownloadServiceContract.EXTRA_MESSAGE, message)
}

fun Intent.toDownloadTaskOrNull(fallbackCreatedAt: () -> String): DownloadTask? {
    val id = getStringExtra(DownloadServiceContract.EXTRA_TASK_ID).orEmpty()
    if (id.isBlank()) {
        AppLogger.warn("payload", "missingTaskId", "action" to action)
        return null
    }
    AppLogger.event("payload", "parseIntentTask", "taskId" to id, "action" to action)
    val status = runCatching {
        DownloadStatus.valueOf(getStringExtra(DownloadServiceContract.EXTRA_STATUS).orEmpty())
    }.getOrDefault(DownloadStatus.Downloading)
    return DownloadTask(
        id = id,
        title = getStringExtra(DownloadServiceContract.EXTRA_TITLE).orEmpty().ifBlank { "媒体文件" },
        sourceUrl = getStringExtra(DownloadServiceContract.EXTRA_URL).orEmpty(),
        progress = getFloatExtra(DownloadServiceContract.EXTRA_PROGRESS, 0f),
        status = status,
        stage = runCatching {
            DownloadStage.valueOf(getStringExtra(DownloadServiceContract.EXTRA_STAGE).orEmpty())
        }.getOrDefault(status.defaultStage()),
        createdAt = getStringExtra(DownloadServiceContract.EXTRA_CREATED_AT).orEmpty().ifBlank { fallbackCreatedAt() },
        downloadedBytes = getLongExtra(DownloadServiceContract.EXTRA_DOWNLOADED_BYTES, 0L),
        totalBytes = getLongExtra(DownloadServiceContract.EXTRA_TOTAL_BYTES, 0L),
        speedBytesPerSecond = getLongExtra(DownloadServiceContract.EXTRA_SPEED_BYTES_PER_SECOND, 0L),
        filePath = getStringExtra(DownloadServiceContract.EXTRA_FILE_PATH).orEmpty(),
        engineVersion = getStringExtra(DownloadServiceContract.EXTRA_ENGINE_VERSION).orEmpty(),
        errorMessage = getStringExtra(DownloadServiceContract.EXTRA_ERROR).orEmpty(),
        formatSelector = getStringExtra(DownloadServiceContract.EXTRA_FORMAT_SELECTOR).orEmpty(),
        formatLabel = getStringExtra(DownloadServiceContract.EXTRA_FORMAT_LABEL).orEmpty(),
        thumbnailUrl = getStringExtra(DownloadServiceContract.EXTRA_THUMBNAIL_URL).orEmpty(),
        requiresMediaMerge = getBooleanExtra(DownloadServiceContract.EXTRA_REQUIRES_MEDIA_MERGE, false),
        exportedPath = getStringExtra(DownloadServiceContract.EXTRA_EXPORTED_PATH).orEmpty(),
        exportedUri = getStringExtra(DownloadServiceContract.EXTRA_EXPORTED_URI).orEmpty()
    )
}

fun Collection<DownloadTask>.toDownloadQueueJson(): String {
    AppLogger.event("payload", "queueToJson", "count" to size)
    val array = JSONArray()
    forEach { task -> array.put(task.toQueueJson()) }
    return array.toString()
}

fun parseDownloadQueue(raw: String, fallbackCreatedAt: () -> String): List<DownloadTask> {
    if (raw.isBlank()) {
        AppLogger.event("payload", "parseBlankQueue")
        return emptyList()
    }
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val id = json.optString(DownloadServiceContract.EXTRA_TASK_ID)
                if (id.isBlank()) continue
                add(json.toQueuedDownloadTask(id, fallbackCreatedAt))
            }
        }
    }.onSuccess {
        AppLogger.event("payload", "parseQueueSuccess", "count" to it.size)
    }.onFailure { error ->
        AppLogger.error("payload", "parseQueueFailure", error, "rawLength" to raw.length)
    }.getOrDefault(emptyList())
}

private fun DownloadTask.toQueueJson(): JSONObject {
    return JSONObject()
        .put(DownloadServiceContract.EXTRA_TASK_ID, id)
        .put(DownloadServiceContract.EXTRA_TITLE, title)
        .put(DownloadServiceContract.EXTRA_URL, sourceUrl)
        .put(DownloadServiceContract.EXTRA_STATUS, status.name)
        .put(DownloadServiceContract.EXTRA_STAGE, stage.name)
        .put(DownloadServiceContract.EXTRA_CREATED_AT, createdAt)
        .put(DownloadServiceContract.EXTRA_PROGRESS, progress.toDouble())
        .put(DownloadServiceContract.EXTRA_DOWNLOADED_BYTES, downloadedBytes)
        .put(DownloadServiceContract.EXTRA_TOTAL_BYTES, totalBytes)
        .put(DownloadServiceContract.EXTRA_SPEED_BYTES_PER_SECOND, speedBytesPerSecond)
        .put(DownloadServiceContract.EXTRA_FORMAT_SELECTOR, formatSelector)
        .put(DownloadServiceContract.EXTRA_FORMAT_LABEL, formatLabel)
        .put(DownloadServiceContract.EXTRA_THUMBNAIL_URL, thumbnailUrl)
        .put(DownloadServiceContract.EXTRA_REQUIRES_MEDIA_MERGE, requiresMediaMerge)
}

private fun JSONObject.toQueuedDownloadTask(id: String, fallbackCreatedAt: () -> String): DownloadTask {
    val status = runCatching {
        DownloadStatus.valueOf(optString(DownloadServiceContract.EXTRA_STATUS))
    }.getOrDefault(DownloadStatus.Queued)
    return DownloadTask(
        id = id,
        title = optString(DownloadServiceContract.EXTRA_TITLE).ifBlank { "媒体文件" },
        sourceUrl = optString(DownloadServiceContract.EXTRA_URL),
        progress = optDouble(DownloadServiceContract.EXTRA_PROGRESS, 0.0).toFloat(),
        status = status,
        stage = runCatching {
            DownloadStage.valueOf(optString(DownloadServiceContract.EXTRA_STAGE))
        }.getOrDefault(status.defaultStage()),
        createdAt = optString(DownloadServiceContract.EXTRA_CREATED_AT).ifBlank { fallbackCreatedAt() },
        downloadedBytes = optLong(DownloadServiceContract.EXTRA_DOWNLOADED_BYTES, 0L),
        totalBytes = optLong(DownloadServiceContract.EXTRA_TOTAL_BYTES, 0L),
        speedBytesPerSecond = optLong(DownloadServiceContract.EXTRA_SPEED_BYTES_PER_SECOND, 0L),
        formatSelector = optString(DownloadServiceContract.EXTRA_FORMAT_SELECTOR),
        formatLabel = optString(DownloadServiceContract.EXTRA_FORMAT_LABEL),
        thumbnailUrl = optString(DownloadServiceContract.EXTRA_THUMBNAIL_URL),
        requiresMediaMerge = optBoolean(DownloadServiceContract.EXTRA_REQUIRES_MEDIA_MERGE, false)
    )
}
