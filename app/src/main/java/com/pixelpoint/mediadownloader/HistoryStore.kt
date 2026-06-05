package com.pixelpoint.mediadownloader

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter

class HistoryStore(context: Context) {
    private val historyFile = File(context.filesDir, "history.json")
    private val atomicHistoryFile = AtomicFile(historyFile)

    fun getThumbnailFile(taskId: String): File {
        val dir = File(historyFile.parentFile, "thumbnails")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "$taskId.jpg")
    }

    fun load(): List<DownloadTask> {
        if (!historyFile.exists()) {
            AppLogger.event("history", "loadMissingFile", "path" to historyFile.absolutePath)
            return emptyList()
        }

        return synchronized(lock) {
            runCatching {
                val content = atomicHistoryFile.openRead().bufferedReader().use { it.readText() }
                val array = JSONArray(content)
                val history = buildList {
                    for (index in 0 until array.length()) {
                        add(array.getJSONObject(index).toDownloadTask())
                    }
                }
                AppLogger.event("history", "loadSuccess", "count" to history.size, "path" to historyFile.absolutePath)
                history
            }.onFailure { error ->
                AppLogger.error("history", "loadFailure", error, "path" to historyFile.absolutePath)
            }.getOrDefault(emptyList())
        }
    }

    fun save(history: List<DownloadTask>) {
        synchronized(lock) {
            AppLogger.event("history", "saveStart", "count" to history.size, "path" to historyFile.absolutePath)
            val array = JSONArray()
            history.forEach { array.put(it.toJson()) }
            val stream = atomicHistoryFile.startWrite()
            runCatching {
                val writer = OutputStreamWriter(stream, Charsets.UTF_8)
                writer.write(array.toString(2))
                writer.flush()
                atomicHistoryFile.finishWrite(stream)
                AppLogger.event("history", "saveSuccess", "count" to history.size, "path" to historyFile.absolutePath)
            }.onFailure { error ->
                atomicHistoryFile.failWrite(stream)
                AppLogger.error("history", "saveFailure", error, "path" to historyFile.absolutePath)
            }
        }
    }

    companion object {
        private val lock = Any()
    }
}

private fun DownloadTask.toJson(): JSONObject {
    return JSONObject()
        .put("id", id)
        .put("title", title)
        .put("sourceUrl", sourceUrl)
        .put("progress", progress.toDouble())
        .put("status", status.name)
        .put("stage", stage.name)
        .put("createdAt", createdAt)
        .put("downloadedBytes", downloadedBytes)
        .put("totalBytes", totalBytes)
        .put("speedBytesPerSecond", speedBytesPerSecond)
        .put("filePath", filePath)
        .put("engineVersion", engineVersion)
        .put("errorMessage", errorMessage)
        .put("formatSelector", formatSelector)
        .put("formatLabel", formatLabel)
        .put("thumbnailUrl", thumbnailUrl)
        .put("requiresMediaMerge", requiresMediaMerge)
        .put("exportedPath", exportedPath)
        .put("exportedUri", exportedUri)
        .put("playbackPositionMs", playbackPositionMs)
        .put("playbackDurationMs", playbackDurationMs)
        .put("playbackUpdatedAt", playbackUpdatedAt)
}

private fun JSONObject.toDownloadTask(): DownloadTask {
    val status = runCatching { DownloadStatus.valueOf(optString("status")) }.getOrDefault(DownloadStatus.Completed)
    return DownloadTask(
        id = optString("id"),
        title = optString("title"),
        sourceUrl = optString("sourceUrl"),
        progress = optDouble("progress", 1.0).toFloat(),
        status = status,
        stage = runCatching { DownloadStage.valueOf(optString("stage")) }.getOrDefault(status.defaultStage()),
        createdAt = optString("createdAt"),
        downloadedBytes = optLong("downloadedBytes", 0L),
        totalBytes = optLong("totalBytes", 0L),
        speedBytesPerSecond = optLong("speedBytesPerSecond", 0L),
        filePath = optString("filePath"),
        engineVersion = optString("engineVersion"),
        errorMessage = optString("errorMessage"),
        formatSelector = optString("formatSelector"),
        formatLabel = optString("formatLabel"),
        thumbnailUrl = optString("thumbnailUrl"),
        requiresMediaMerge = optBoolean("requiresMediaMerge", false),
        exportedPath = optString("exportedPath"),
        exportedUri = optString("exportedUri"),
        playbackPositionMs = optLong("playbackPositionMs", 0L),
        playbackDurationMs = optLong("playbackDurationMs", 0L),
        playbackUpdatedAt = optLong("playbackUpdatedAt", 0L)
    )
}
