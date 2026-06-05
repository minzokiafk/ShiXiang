package com.pixelpoint.mediadownloader

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.OutputStreamWriter

class PendingQueueStore(context: Context) {
    private val queueFile = File(context.filesDir, "pending_queue.json")
    private val atomicQueueFile = AtomicFile(queueFile)

    fun load(fallbackCreatedAt: () -> String): List<DownloadTask> {
        if (!queueFile.exists()) {
            AppLogger.event("queue", "loadMissingFile", "path" to queueFile.absolutePath)
            return emptyList()
        }
        return synchronized(lock) {
            runCatching {
                val content = atomicQueueFile.openRead().bufferedReader().use { it.readText() }
                parseDownloadQueue(content, fallbackCreatedAt)
            }.onSuccess {
                AppLogger.event("queue", "loadSuccess", "count" to it.size, "path" to queueFile.absolutePath)
            }.onFailure { error ->
                AppLogger.error("queue", "loadFailure", error, "path" to queueFile.absolutePath)
            }.getOrDefault(emptyList())
        }
    }

    fun save(queue: List<DownloadTask>) {
        synchronized(lock) {
            AppLogger.event("queue", "saveStart", "count" to queue.size, "path" to queueFile.absolutePath)
            val stream = atomicQueueFile.startWrite()
            runCatching {
                val writer = OutputStreamWriter(stream, Charsets.UTF_8)
                writer.write(queue.toDownloadQueueJson())
                writer.flush()
                atomicQueueFile.finishWrite(stream)
                AppLogger.event("queue", "saveSuccess", "count" to queue.size, "path" to queueFile.absolutePath)
            }.onFailure { error ->
                atomicQueueFile.failWrite(stream)
                AppLogger.error("queue", "saveFailure", error, "path" to queueFile.absolutePath)
            }
        }
    }

    companion object {
        private val lock = Any()
    }
}
