package com.pixelpoint.mediadownloader

import android.content.Context
import android.util.Log
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object AppLogger {
    private const val TAG_PREFIX = "ShiXiang"
    private const val MAX_LOG_BYTES = 1_000_000L
    private val lock = Any()
    private var logFile: File? = null

    fun init(context: Context) {
        synchronized(lock) {
            val dir = File(context.filesDir, "logs").apply { mkdirs() }
            logFile = File(dir, "operations.log")
            event("logger", "initialized", "path" to logFile?.absolutePath.orEmpty())
        }
    }

    fun event(area: String, action: String, vararg fields: Pair<String, Any?>) {
        write(Log.INFO, area, action, fields.toMap(), null)
    }

    fun warn(area: String, action: String, vararg fields: Pair<String, Any?>) {
        write(Log.WARN, area, action, fields.toMap(), null)
    }

    fun error(area: String, action: String, throwable: Throwable? = null, vararg fields: Pair<String, Any?>) {
        write(Log.ERROR, area, action, fields.toMap(), throwable)
    }

    fun cookieSummary(value: String): String {
        return if (value.isBlank()) "blank" else "present(length=${value.length})"
    }

    fun cookieNamesSummary(value: String): String {
        val count = value.split("\n", ";")
            .asSequence()
            .map { it.trim() }
            .filter { it.contains("=") }
            .map { line ->
                val fields = line.split("\t")
                if (fields.size >= 7) fields[5] else line.substringBefore("=").trim()
            }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .distinct()
            .count()
        return if (count == 0) "none" else "present(count=$count)"
    }

    private fun write(
        level: Int,
        area: String,
        action: String,
        fields: Map<String, Any?>,
        throwable: Throwable?
    ) {
        val line = buildLine(level, area, action, fields, throwable)
        when (level) {
            Log.ERROR -> Log.e("$TAG_PREFIX/$area", line, throwable)
            Log.WARN -> Log.w("$TAG_PREFIX/$area", line, throwable)
            else -> Log.i("$TAG_PREFIX/$area", line)
        }
        synchronized(lock) {
            val file = logFile ?: return
            runCatching {
                rotateIfNeeded(file)
                file.appendText(line + "\n")
            }.onFailure {
                Log.w("$TAG_PREFIX/logger", "Failed to write operation log: ${it.message}")
            }
        }
    }

    private fun buildLine(
        level: Int,
        area: String,
        action: String,
        fields: Map<String, Any?>,
        throwable: Throwable?
    ): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val levelLabel = when (level) {
            Log.ERROR -> "ERROR"
            Log.WARN -> "WARN"
            else -> "INFO"
        }
        val fieldText = fields.entries.joinToString(" ") { entry ->
            "${entry.key}=${entry.value.toLogValue()}"
        }
        val throwableText = throwable?.let { " error=${it.message.toLogValue()}" }.orEmpty()
        return "$timestamp $levelLabel area=$area action=$action $fieldText$throwableText".trim()
    }

    private fun Any?.toLogValue(): String {
        return when (this) {
            null -> "null"
            is String -> replace("\n", "\\n").take(500)
            else -> toString().replace("\n", "\\n").take(500)
        }
    }

    private fun rotateIfNeeded(file: File) {
        if (!file.exists() || file.length() < MAX_LOG_BYTES) return
        val rotated = File(file.parentFile, "operations.previous.log")
        if (rotated.exists()) rotated.delete()
        file.renameTo(rotated)
    }
}
