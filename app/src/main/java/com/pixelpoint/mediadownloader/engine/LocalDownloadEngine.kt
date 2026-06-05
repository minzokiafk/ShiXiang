package com.pixelpoint.mediadownloader.engine

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.pixelpoint.mediadownloader.AppLogger
import com.pixelpoint.mediadownloader.QuickJsBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class EngineDownloadResult(
    val ok: Boolean,
    val title: String,
    val filePath: String,
    val error: String,
    val engineVersion: String
)

data class EngineProgress(
    val progress: Float,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSecond: Long,
    val etaSeconds: Long
)

data class DownloadFormatOption(
    val id: String,
    val label: String,
    val detail: String,
    val selector: String,
    val height: Int,
    val requiresMerge: Boolean = false,
    val sourceUrl: String = "",
    val audioBitrateKbps: Int = 0,
    val audioChannels: String = ""
)

data class FormatResolveResult(
    val ok: Boolean,
    val title: String,
    val thumbnailUrl: String,
    val formats: List<DownloadFormatOption>,
    val error: String,
    val engineVersion: String
)

private data class CommandResult(
    val exitCode: Int,
    val logs: String
)

class LocalDownloadEngine(private val context: Context) {
    private val TAG = "LocalDownloadEngine"

    @Volatile
    private var activeCancelFile: File? = null

    @Volatile
    private var ffmpegUsable: Boolean? = null

    private fun getUserAgent(): String {
        return try {
            android.webkit.WebSettings.getDefaultUserAgent(context)
        } catch (e: Exception) {
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
        }
    }

    fun getEngineVersion(): String {
        return try {
            ensurePythonStarted()
            Python.getInstance().getModule("media_engine").callAttr("version").toString()
        } catch (e: Exception) {
            "未知"
        }
    }

    suspend fun download(
        url: String,
        formatSelector: String = "",
        cookieHeader: String = "",
        refererHeader: String = "",
        requireAudio: Boolean = false,
        rateLimitBytes: Long = 0L,
        onProgress: (EngineProgress) -> Unit = {},
        onProcessing: () -> Unit = {}
    ): EngineDownloadResult = coroutineScope {
        withContext(Dispatchers.IO) { ensurePythonStarted() }
        AppLogger.event(
            "engine",
            "downloadStart",
            "url" to url,
            "formatSelector" to formatSelector,
            "cookie" to AppLogger.cookieSummary(cookieHeader),
            "referer" to refererHeader.ifBlank { "blank" },
            "requireAudio" to requireAudio,
            "rateLimitBytes" to rateLimitBytes
        )

        val outputDir = File(context.filesDir, "downloads")
        val cancelFile = File(context.cacheDir, "download_cancel_${System.nanoTime()}")
        val progressFile = File(context.cacheDir, "download_progress_${System.nanoTime()}.json")
        cancelFile.delete()
        progressFile.delete()
        activeCancelFile = cancelFile

        val module = Python.getInstance().getModule("media_engine")
        val ffmpegPath = ffmpegExecutableOrNull()?.absolutePath.orEmpty()
        val effectiveFormatSelector = when {
            ffmpegPath.isBlank() -> combinedOnlySelector(formatSelector)
            else -> formatSelector
        }
        AppLogger.event(
            "engine",
            "downloadPrepared",
            "outputDir" to outputDir.absolutePath,
            "ffmpegPath" to ffmpegPath.ifBlank { "blank" },
            "effectiveFormatSelector" to effectiveFormatSelector,
            "cancelFile" to cancelFile.absolutePath,
            "progressFile" to progressFile.absolutePath
        )

        val progressJob = launch(Dispatchers.IO) {
            var lastProgress = -1f
            while (isActive) {
                progressFile.readProgressOrNull()?.let { progress ->
                    if (progress.progress != lastProgress) {
                        lastProgress = progress.progress
                        AppLogger.event(
                            "engine",
                            "progressRead",
                            "progress" to progress.progress,
                            "downloadedBytes" to progress.downloadedBytes,
                            "totalBytes" to progress.totalBytes,
                            "speedBytesPerSecond" to progress.speedBytesPerSecond
                        )
                        onProgress(progress)
                    }
                }
                delay(500)
            }
        }

        val userAgent = getUserAgent()
        Log.d(TAG, "Starting Python download for: $url, ffmpeg=$ffmpegPath, selector=$effectiveFormatSelector, hasCookie=${cookieHeader.isNotBlank()}, hasReferer=${refererHeader.isNotBlank()}, userAgent=$userAgent, rateLimitBytes=$rateLimitBytes")
        val rawResult = try {
            withContext(Dispatchers.IO) {
                module.callAttr(
                    "download",
                    url,
                    outputDir.absolutePath,
                    cancelFile.absolutePath,
                    progressFile.absolutePath,
                    effectiveFormatSelector,
                    ffmpegPath,
                    cookieHeader,
                    refererHeader,
                    userAgent,
                    rateLimitBytes
                ).toString()
            }
        } finally {
            AppLogger.event("engine", "downloadCleanup", "cancelFile" to cancelFile.absolutePath, "progressFile" to progressFile.absolutePath)
            progressJob.cancel()
            if (activeCancelFile == cancelFile) {
                activeCancelFile = null
            }
            cancelFile.delete()
            progressFile.delete()
        }

        val json = JSONObject(rawResult)
        val ok = json.optBoolean("ok")
        val filePath = json.optString("filepath")
        val error = json.optString("error")
        Log.d(TAG, "Python download finished. ok=$ok, path=$filePath, error=$error")
        AppLogger.event(
            "engine",
            "pythonDownloadResult",
            "ok" to ok,
            "filePath" to filePath,
            "title" to json.optString("title"),
            "engineVersion" to json.optString("engine_version"),
            "error" to error
        )

        var finalFilePath = filePath
        var finalError = error
        if (filePath.isNotBlank()) {
            onProcessing()
            val repaired = runCatching {
                repairDownloadedMedia(File(filePath))
            }.getOrElse { throwable ->
                Log.e(TAG, "Media repair failed: ${throwable.message}")
                AppLogger.error("engine", "mediaRepairFailure", throwable, "filePath" to filePath)
                finalError = "音视频处理失败：${throwable.message ?: "未知错误"}"
                null
            }

            if (repaired != null) {
                val validationError = validateCompletedMedia(File(repaired), requireAudio)
                if (validationError == null) {
                    AppLogger.event("engine", "mediaRepairSuccess", "input" to filePath, "output" to repaired)
                    finalFilePath = repaired
                } else {
                    AppLogger.warn("engine", "mediaValidationRejected", "filePath" to repaired, "reason" to validationError)
                    finalFilePath = ""
                    finalError = validationError
                }
            } else if (finalError.isBlank()) {
                AppLogger.warn("engine", "mediaRepairNoUsableFile", "input" to filePath)
                finalFilePath = ""
                finalError = "下载到了不完整的音视频流，未找到可合并的完整文件"
            }
        }

        val result = EngineDownloadResult(
            ok = ok && finalFilePath.isReadableMediaFile(),
            title = json.optString("title").ifBlank { "媒体文件" },
            filePath = finalFilePath,
            error = if (finalFilePath.isReadableMediaFile()) "" else finalError.ifBlank { "下载失败：没有生成可用文件" },
            engineVersion = json.optString("engine_version")
        )
        AppLogger.event("engine", "downloadResult", "ok" to result.ok, "filePath" to result.filePath, "error" to result.error)
        result
    }

    suspend fun resolveFormats(
        url: String,
        cookieHeader: String = "",
        refererHeader: String = "",
        videoFormatPreference: Int = 1,
        audioFormatPreference: Int = 0,
        audioQualityPreference: Int = 0
    ): FormatResolveResult = withContext(Dispatchers.IO) {
        val startedAt = SystemClock.elapsedRealtime()
        ensurePythonStarted()
        val userAgent = getUserAgent()
        AppLogger.event(
            "engine",
            "resolveFormatsStart",
            "url" to url,
            "cookie" to AppLogger.cookieSummary(cookieHeader),
            "referer" to refererHeader.ifBlank { "blank" },
            "userAgent" to userAgent,
            "videoFormatPreference" to videoFormatPreference,
            "audioFormatPreference" to audioFormatPreference,
            "audioQualityPreference" to audioQualityPreference
        )
        val rawResult = Python.getInstance()
            .getModule("media_engine")
            .callAttr(
                "formats",
                url,
                cookieHeader,
                refererHeader,
                userAgent,
                videoFormatPreference,
                audioFormatPreference,
                audioQualityPreference
            )
            .toString()
        val json = JSONObject(rawResult)
        val formatsArray = json.optJSONArray("formats")
        val formats = buildList {
            if (formatsArray != null) {
                for (index in 0 until formatsArray.length()) {
                    val item = formatsArray.optJSONObject(index) ?: continue
                    add(
                        DownloadFormatOption(
                            id = item.optString("id"),
                            label = item.optString("label"),
                            detail = item.optString("detail"),
                            selector = item.optString("selector"),
                            height = item.optInt("height", 0),
                            requiresMerge = item.optBoolean("requires_merge", false),
                            sourceUrl = item.optString("url"),
                            audioBitrateKbps = item.optInt("audio_bitrate_kbps", 0),
                            audioChannels = item.optString("audio_channels")
                        )
                    )
                }
            }
        }

        val result = FormatResolveResult(
            ok = json.optBoolean("ok"),
            title = json.optString("title"),
            thumbnailUrl = json.optString("thumbnail"),
            formats = formats,
            error = json.optString("error"),
            engineVersion = json.optString("engine_version")
        )
        AppLogger.event(
            "engine",
            "resolveFormatsResult",
            "durationMs" to (SystemClock.elapsedRealtime() - startedAt),
            "ok" to result.ok,
            "title" to result.title,
            "thumbnail" to result.thumbnailUrl.ifBlank { "blank" },
            "count" to result.formats.size,
            "engineVersion" to result.engineVersion,
            "error" to result.error
        )
        result
    }

    fun cancelActiveDownload() {
        AppLogger.event("engine", "cancelActiveDownload", "cancelFile" to activeCancelFile?.absolutePath)
        activeCancelFile?.runCatchingWriteCancelSignal()
    }

    suspend fun canMergeMedia(): Boolean = withContext(Dispatchers.IO) {
        val available = ffmpegExecutableOrNull() != null
        AppLogger.event("engine", "mergeCapabilityChecked", "available" to available)
        available
    }

    private fun repairDownloadedMedia(file: File): String? {
        AppLogger.event("engine", "repairMediaStart", "filePath" to file.absolutePath, "size" to if (file.exists()) file.length() else 0L)
        if (!file.isReadableMediaFile()) return null
        val ffmpeg = ffmpegExecutableOrNull() ?: return repairWithoutFfmpeg(file)

        var candidate = file
        val hasVideo = candidate.hasVideoStream(ffmpeg)
        val hasAudio = candidate.hasAudioStream(ffmpeg)
        Log.d(TAG, "Downloaded stream probe. path=${candidate.absolutePath}, video=$hasVideo, audio=$hasAudio")
        AppLogger.event("engine", "streamProbe", "filePath" to candidate.absolutePath, "hasVideo" to hasVideo, "hasAudio" to hasAudio)

        if (hasVideo && !hasAudio) {
            val mergedPath = tryMergeStreams(candidate, ffmpeg)
            if (mergedPath != null) {
                candidate = File(mergedPath)
            } else {
                Log.w(TAG, "Video-only file returned and no matching audio stream was found.")
                AppLogger.warn("engine", "videoOnlyNoAudioSibling", "filePath" to candidate.absolutePath)
            }
        } else if (!hasVideo && hasAudio) {
            val siblingVideo = findSiblingVideoForAudio(candidate, ffmpeg)
            val mergedPath = siblingVideo?.let { tryMergeStreams(it, ffmpeg) }
            if (mergedPath != null) {
                candidate = File(mergedPath)
            } else {
                Log.w(TAG, "Audio-only file returned and no matching video stream was found.")
                AppLogger.warn("engine", "audioOnlyNoVideoSibling", "filePath" to candidate.absolutePath)
                return null
            }
        } else if (!hasVideo && !hasAudio) {
            return null
        }

        if (!candidate.hasVideoStream(ffmpeg)) return null

        if (!candidate.absolutePath.endsWith(".mp4", ignoreCase = true)) {
            candidate = File(convertToMp4(candidate, ffmpeg) ?: return null)
        }

        return candidate.absolutePath
    }

    private fun repairWithoutFfmpeg(file: File): String? {
        AppLogger.warn("engine", "repairWithoutFfmpeg", "filePath" to file.absolutePath, "extension" to file.extension)
        if (file.isClearlyAudioOnlyByExtension()) return null
        return if (file.extension.lowercase() in setOf("mp4", "m4v", "webm", "mkv")) {
            file.absolutePath
        } else {
            null
        }
    }

    private fun validateCompletedMedia(file: File, requireAudio: Boolean): String? {
        if (!file.isReadableMediaFile()) return "媒体校验失败：没有生成可读文件"

        val durationMs = runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(file.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            }
        }.getOrDefault(0L)
        val ffmpeg = ffmpegExecutableOrNull()
        val hasVideo = ffmpeg?.let { file.hasVideoStream(it) } ?: !file.isClearlyAudioOnlyByExtension()
        val hasAudio = ffmpeg?.let { file.hasAudioStream(it) }
        AppLogger.event(
            "engine",
            "mediaValidationResult",
            "filePath" to file.absolutePath,
            "size" to file.length(),
            "durationMs" to durationMs,
            "hasVideo" to hasVideo,
            "hasAudio" to (hasAudio ?: "unknown"),
            "requireAudio" to requireAudio
        )
        return when {
            durationMs <= 0L -> "媒体校验失败：无法读取视频时长"
            !hasVideo -> "媒体校验失败：文件中没有视频轨道"
            requireAudio && hasAudio == false -> "媒体校验失败：该视频缺少音频轨道"
            else -> null
        }
    }

    private fun tryMergeStreams(videoFile: File, ffmpeg: File): String? {
        val folder = videoFile.parentFile ?: return null
        val audioFile = findSiblingAudioForVideo(videoFile, ffmpeg) ?: return null
        val baseName = videoFile.mediaBaseName()
        val preferredOutput = File(folder, "$baseName.mp4")
        val outputFile = if (preferredOutput.absolutePath == videoFile.absolutePath) {
            File(folder, "$baseName.merged.mp4")
        } else {
            preferredOutput
        }
        val outputPath = outputFile.absolutePath
        Log.d(TAG, "Merging video (${videoFile.absolutePath}) and audio (${audioFile.absolutePath}) to $outputPath")
        AppLogger.event("engine", "mergeStreamsStart", "video" to videoFile.absolutePath, "audio" to audioFile.absolutePath, "output" to outputPath)

        val merged = runFfmpeg(
            ffmpeg = ffmpeg,
            label = "stream-copy merge",
            args = listOf(
                "-i", videoFile.absolutePath,
                "-i", audioFile.absolutePath,
                "-map", "0:v:0",
                "-map", "1:a:0",
                "-c", "copy",
                "-movflags", "+faststart",
                "-y", outputPath
            ),
            outputPath = outputPath
        ) || runFfmpeg(
            ffmpeg = ffmpeg,
            label = "audio-transcode merge",
            args = listOf(
                "-i", videoFile.absolutePath,
                "-i", audioFile.absolutePath,
                "-map", "0:v:0",
                "-map", "1:a:0",
                "-c:v", "copy",
                "-c:a", "aac",
                "-b:a", "160k",
                "-movflags", "+faststart",
                "-y", outputPath
            ),
            outputPath = outputPath
        ) || runFfmpeg(
            ffmpeg = ffmpeg,
            label = "full-transcode merge",
            args = listOf(
                "-i", videoFile.absolutePath,
                "-i", audioFile.absolutePath,
                "-map", "0:v:0",
                "-map", "1:a:0",
                "-c:v", "mpeg4",
                "-q:v", "3",
                "-c:a", "aac",
                "-b:a", "160k",
                "-movflags", "+faststart",
                "-y", outputPath
            ),
            outputPath = outputPath
        )

        return if (merged) {
            AppLogger.event("engine", "mergeStreamsSuccess", "output" to outputPath)
            videoFile.delete()
            audioFile.delete()
            outputPath
        } else {
            AppLogger.warn("engine", "mergeStreamsFailure", "output" to outputPath)
            null
        }
    }

    private fun findSiblingVideoForAudio(audioFile: File, ffmpeg: File): File? {
        val folder = audioFile.parentFile ?: return null
        val baseName = audioFile.mediaBaseName()
        return folder.listFiles()
            ?.filter { it != audioFile && it.mediaBaseName() == baseName && it.hasVideoStream(ffmpeg) }
            ?.maxByOrNull { it.length() }
    }

    private fun findSiblingAudioForVideo(videoFile: File, ffmpeg: File): File? {
        val folder = videoFile.parentFile ?: return null
        val baseName = videoFile.mediaBaseName()
        return folder.listFiles()
            ?.filter { it != videoFile && it.mediaBaseName() == baseName && it.hasAudioStream(ffmpeg) }
            ?.maxByOrNull { it.length() }
    }

    private fun convertToMp4(inputFile: File, ffmpeg: File): String? {
        val outputPath = inputFile.absolutePath.substringBeforeLast(".") + ".mp4"
        if (inputFile.absolutePath == outputPath) return inputFile.absolutePath
        AppLogger.event("engine", "convertToMp4Start", "input" to inputFile.absolutePath, "output" to outputPath)

        val converted = runFfmpeg(
            ffmpeg = ffmpeg,
            label = "stream-copy convert",
            args = listOf("-i", inputFile.absolutePath, "-c", "copy", "-movflags", "+faststart", "-y", outputPath),
            outputPath = outputPath
        ) || runFfmpeg(
            ffmpeg = ffmpeg,
            label = "audio-transcode convert",
            args = listOf(
                "-i", inputFile.absolutePath,
                "-c:v", "copy",
                "-c:a", "aac",
                "-b:a", "160k",
                "-movflags", "+faststart",
                "-y", outputPath
            ),
            outputPath = outputPath
        ) || runFfmpeg(
            ffmpeg = ffmpeg,
            label = "full-transcode convert",
            args = listOf(
                "-i", inputFile.absolutePath,
                "-c:v", "mpeg4",
                "-q:v", "3",
                "-c:a", "aac",
                "-b:a", "160k",
                "-movflags", "+faststart",
                "-y", outputPath
            ),
            outputPath = outputPath
        )

        return if (converted) {
            AppLogger.event("engine", "convertToMp4Success", "output" to outputPath)
            inputFile.delete()
            outputPath
        } else {
            AppLogger.warn("engine", "convertToMp4Failure", "input" to inputFile.absolutePath)
            null
        }
    }

    private fun runFfmpeg(ffmpeg: File, label: String, args: List<String>, outputPath: String): Boolean {
        AppLogger.event("engine", "ffmpegStart", "label" to label, "output" to outputPath)
        File(outputPath).delete()
        val result = executeCommand(listOf(ffmpeg.absolutePath) + args, timeoutMinutes = 30)
        val success = result.exitCode == 0 && File(outputPath).isReadableMediaFile()
        if (!success) {
            File(outputPath).delete()
            Log.w(TAG, "$label failed. exitCode=${result.exitCode}, logs=${result.logs.takeLast(1200)}")
            AppLogger.warn("engine", "ffmpegFailure", "label" to label, "exitCode" to result.exitCode, "logs" to result.logs.takeLast(1200))
        } else {
            AppLogger.event("engine", "ffmpegSuccess", "label" to label, "output" to outputPath)
        }
        return success
    }

    private fun File.hasAudioStream(ffmpeg: File): Boolean {
        return hasStream(ffmpeg, "Audio:")
    }

    private fun File.hasVideoStream(ffmpeg: File): Boolean {
        return hasStream(ffmpeg, "Video:")
    }

    private fun File.hasStream(ffmpeg: File, marker: String): Boolean {
        if (!isReadableMediaFile()) return false
        val result = executeCommand(listOf(ffmpeg.absolutePath, "-hide_banner", "-i", absolutePath), timeoutSeconds = 20)
        return result.logs.contains(marker, ignoreCase = true)
    }

    private fun ffmpegExecutableOrNull(): File? {
        ffmpegUsable?.let { usable ->
            return if (usable) File(context.applicationInfo.nativeLibraryDir, "libffmpeg_exec.so") else null
        }

        val executable = File(context.applicationInfo.nativeLibraryDir, "libffmpeg_exec.so")
        val usable = executable.exists() && executable.canExecute() &&
            executeCommand(listOf(executable.absolutePath, "-version"), timeoutSeconds = 20).exitCode == 0
        ffmpegUsable = usable
        if (!usable) {
            Log.e(TAG, "Bundled ffmpeg is unavailable: ${executable.absolutePath}")
            AppLogger.warn("engine", "ffmpegUnavailable", "path" to executable.absolutePath)
        } else {
            AppLogger.event("engine", "ffmpegUsable", "path" to executable.absolutePath)
        }
        return if (usable) executable else null
    }

    private fun executeCommand(
        command: List<String>,
        timeoutSeconds: Long = 0,
        timeoutMinutes: Long = 0
    ): CommandResult {
        return runCatching {
            AppLogger.event("engine", "executeCommandStart", "command" to command.joinToString(" "), "timeoutSeconds" to timeoutSeconds, "timeoutMinutes" to timeoutMinutes)
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val output = StringBuilder()
            val readerThread = Thread {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line -> output.appendLine(line) }
                }
            }
            readerThread.start()

            val completed = when {
                timeoutMinutes > 0 -> process.waitFor(timeoutMinutes, TimeUnit.MINUTES)
                timeoutSeconds > 0 -> process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
                else -> {
                    process.waitFor()
                    true
                }
            }
            if (!completed) {
                process.destroyForcibly()
                readerThread.join(1000)
                AppLogger.warn("engine", "executeCommandTimeout", "command" to command.firstOrNull(), "outputTail" to output.toString().takeLast(500))
                return CommandResult(-1, output.toString() + "\nTimed out")
            }
            readerThread.join(1000)
            val result = CommandResult(process.exitValue(), output.toString())
            AppLogger.event("engine", "executeCommandDone", "command" to command.firstOrNull(), "exitCode" to result.exitCode, "outputTail" to result.logs.takeLast(500))
            result
        }.getOrElse {
            AppLogger.error("engine", "executeCommandError", it, "command" to command.firstOrNull())
            CommandResult(-1, it.message ?: "Command failed")
        }
    }

    private fun ensurePythonStarted() {
        QuickJsBridge.initialize(context.applicationContext)
        if (!Python.isStarted()) {
            AppLogger.event("engine", "pythonStart")
            Python.start(AndroidPlatform(context.applicationContext))
        } else {
            AppLogger.event("engine", "pythonAlreadyStarted")
        }
    }

    private fun File?.isReadableMediaFile(): Boolean {
        return this != null && exists() && isFile && length() > 0
    }

    private fun String.isReadableMediaFile(): Boolean {
        return isNotBlank() && File(this).isReadableMediaFile()
    }

    private fun File.mediaBaseName(): String {
        return nameWithoutExtension.substringBefore(".f")
    }

    private fun File.isClearlyAudioOnlyByExtension(): Boolean {
        return extension.lowercase() in setOf("m4a", "aac", "mp3", "opus")
    }

    private fun combinedOnlySelector(originalSelector: String): String {
        val height = Regex("height<=([0-9]+)")
            .find(originalSelector)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        val heightFilter = height?.let { "[height<=$it]" }.orEmpty()
        return "best$heightFilter[vcodec!=none][acodec!=none][ext=mp4]/" +
            "best$heightFilter[vcodec!=none][acodec!=none]/" +
            "best[vcodec!=none][acodec!=none][ext=mp4]/" +
            "best[vcodec!=none][acodec!=none]"
    }

    private fun File.runCatchingWriteCancelSignal() {
        runCatching {
            parentFile?.mkdirs()
            writeText("cancel")
            AppLogger.event("engine", "cancelSignalWritten", "path" to absolutePath)
        }.onFailure {
            Log.w(TAG, "Unable to write cancel signal: ${it.message}")
            AppLogger.error("engine", "cancelSignalWriteFailure", it, "path" to absolutePath)
        }
    }

    private fun File.readProgressOrNull(): EngineProgress? {
        if (!exists()) return null
        return runCatching {
            val json = JSONObject(readText())
            EngineProgress(
                progress = json.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f),
                downloadedBytes = json.optLong("downloaded_bytes", 0L),
                totalBytes = json.optLong("total_bytes", 0L),
                speedBytesPerSecond = json.optDouble("speed", 0.0).toLong(),
                etaSeconds = json.optLong("eta", 0L)
            )
        }.getOrNull()
    }
}
