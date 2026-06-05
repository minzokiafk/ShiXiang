package com.pixelpoint.mediadownloader

data class DownloadTask(
    val id: String,
    val title: String,
    val sourceUrl: String,
    val progress: Float,
    val status: DownloadStatus,
    val stage: DownloadStage = status.defaultStage(),
    val createdAt: String,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSecond: Long = 0L,
    val filePath: String = "",
    val engineVersion: String = "",
    val errorMessage: String = "",
    val formatSelector: String = "",
    val formatLabel: String = "",
    val thumbnailUrl: String = "",
    val requiresMediaMerge: Boolean = false,
    val exportedPath: String = "",
    val exportedUri: String = "",
    val playbackPositionMs: Long = 0L,
    val playbackDurationMs: Long = 0L,
    val playbackUpdatedAt: Long = 0L
)

enum class DownloadStatus {
    Queued,
    Downloading,
    Processing,
    Completed,
    Failed,
    Cancelled
}

enum class DownloadStage {
    ResolvingFormats,
    AwaitingFormatSelection,
    AwaitingMediaCapture,
    Queued,
    Downloading,
    Validating,
    Completed,
    Failed,
    Cancelled
}

fun DownloadStatus.defaultStage(): DownloadStage {
    return when (this) {
        DownloadStatus.Queued -> DownloadStage.Queued
        DownloadStatus.Downloading -> DownloadStage.Downloading
        DownloadStatus.Processing -> DownloadStage.Validating
        DownloadStatus.Completed -> DownloadStage.Completed
        DownloadStatus.Failed -> DownloadStage.Failed
        DownloadStatus.Cancelled -> DownloadStage.Cancelled
    }
}
