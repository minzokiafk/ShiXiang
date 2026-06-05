package com.pixelpoint.mediadownloader

import com.pixelpoint.mediadownloader.engine.EngineProgress

object DownloadProgressText {
    fun format(progress: EngineProgress): String {
        val measuredProgress = if (progress.totalBytes > 0 && progress.downloadedBytes in 0 until progress.totalBytes) {
            progress.downloadedBytes.toFloat() / progress.totalBytes.toFloat()
        } else {
            progress.progress
        }
        val percent = (measuredProgress * 100).toInt().coerceIn(0, 99)
        val total = if (progress.totalBytes > 0) " / ${formatBytes(progress.totalBytes)}" else ""
        val speed = if (progress.speedBytesPerSecond > 0) " · ${formatBytes(progress.speedBytesPerSecond)}/s" else ""
        return "$percent% · ${formatBytes(progress.downloadedBytes)}$total$speed"
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = listOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024
            unitIndex += 1
        }
        return if (unitIndex == 0) {
            "${value.toLong()} ${units[unitIndex]}"
        } else {
            String.format("%.1f %s", value, units[unitIndex])
        }
    }
}
