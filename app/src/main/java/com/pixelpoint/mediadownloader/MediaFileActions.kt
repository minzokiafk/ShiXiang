package com.pixelpoint.mediadownloader

import android.content.ContentValues
import android.content.ContentUris
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

data class ExportedMediaFile(
    val displayPath: String,
    val contentUri: String = ""
)

data class ShareTarget(
    val label: String,
    val packageName: String,
    val activityName: String,
    val iconRes: Int
)

class MediaFileActions(private val context: Context) {
    private val shareTargetCache = mutableMapOf<String, List<ShareTarget>>()

    fun open(filePath: String): Result<Unit> = runCatching {
        AppLogger.event("file", "openStart", "filePath" to filePath)
        val file = requireReadableFile(filePath)
        val uri = contentUri(file)

        val viewIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType(file))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        context.startActivity(viewIntent)
        AppLogger.event("file", "openSuccess", "filePath" to filePath, "mimeType" to mimeType(file))
    }

    fun share(filePath: String, title: String): Result<Unit> = runCatching {
        AppLogger.event("file", "shareStart", "filePath" to filePath, "title" to title)
        val file = requireReadableFile(filePath)
        val uri = contentUri(file)

        val sendIntent = Intent(Intent.ACTION_SEND)
            .setType(mimeType(file))
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TITLE, title.ifBlank { file.nameWithoutExtension })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        val chooser = Intent.createChooser(sendIntent, "分享文件")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        context.startActivity(chooser)
        AppLogger.event("file", "shareSuccess", "filePath" to filePath, "mimeType" to mimeType(file))
    }

    fun shareTargets(filePath: String, title: String): Result<List<ShareTarget>> = runCatching {
        val file = requireReadableFile(filePath)
        val mimeType = mimeType(file)
        if (!mimeType.startsWith("video/")) {
            AppLogger.event("file", "shareTargetsSkippedNonVideo", "filePath" to filePath, "mimeType" to mimeType)
            return@runCatching emptyList()
        }
        shareTargetCache[mimeType]?.let { cachedTargets ->
            AppLogger.event("file", "shareTargetsCacheHit", "filePath" to filePath, "count" to cachedTargets.size)
            return@runCatching cachedTargets
        }
        val sendIntent = shareIntent(file, contentUri(file), title)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentActivities(
                sendIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(sendIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        val resolvedByPackage = resolved
            .asSequence()
            .mapNotNull { it.activityInfo }
            .filter { it.packageName != BuildConfig.APPLICATION_ID }
            .groupBy { it.packageName }
        val targets = fixedShareApps.mapNotNull { app ->
            val match = app.packageCandidates.firstNotNullOfOrNull { packageName ->
                resolvedByPackage[packageName]
                    ?.firstOrNull()
                    ?.let { packageName to it.name }
            } ?: return@mapNotNull null
            ShareTarget(
                label = app.label,
                packageName = match.first,
                activityName = match.second,
                iconRes = app.iconRes
            )
        }
        AppLogger.event("file", "shareTargetsLoaded", "filePath" to filePath, "count" to targets.size)
        shareTargetCache[mimeType] = targets
        targets
    }

    fun shareToTarget(filePath: String, title: String, target: ShareTarget): Result<Unit> = runCatching {
        AppLogger.event(
            "file",
            "shareToTargetStart",
            "filePath" to filePath,
            "package" to target.packageName,
            "activity" to target.activityName
        )
        val file = requireReadableFile(filePath)
        val uri = contentUri(file)
        val sendIntent = shareIntent(file, uri, title)
            .setComponent(ComponentName(target.packageName, target.activityName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.grantUriPermission(target.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(sendIntent)
        AppLogger.event(
            "file",
            "shareToTargetSuccess",
            "filePath" to filePath,
            "package" to target.packageName,
            "activity" to target.activityName
        )
    }

    fun exportToDownloads(filePath: String): Result<ExportedMediaFile> = runCatching {
        AppLogger.event("file", "exportStart", "filePath" to filePath, "sdk" to Build.VERSION.SDK_INT)
        val file = requireReadableFile(filePath)
        val destination = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportWithMediaStore(file)
        } else {
            exportLegacy(file)
        }
        AppLogger.event(
            "file",
            "exportSuccess",
            "filePath" to filePath,
            "destination" to destination.displayPath,
            "contentUri" to destination.contentUri.ifBlank { "blank" }
        )
        destination
    }

    fun exportToCustomDirectory(filePath: String, treeUriString: String): Result<ExportedMediaFile> = runCatching {
        AppLogger.event("file", "exportToCustomStart", "filePath" to filePath, "treeUri" to treeUriString)
        val file = requireReadableFile(filePath)
        val treeUri = Uri.parse(treeUriString)
        val documentFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
            ?: error("无法解析自定义保存目录")

        val mimeType = mimeType(file)
        val targetFile = documentFile.createFile(mimeType, file.name)
            ?: error("无法在自定义目录中创建文件")

        context.contentResolver.openOutputStream(targetFile.uri)?.use { output ->
            file.inputStream().use { input -> input.copyTo(output) }
        } ?: error("无法写入自定义目录文件")

        val displayPath = targetFile.name ?: file.name
        ExportedMediaFile(
            displayPath = "自定义目录/$displayPath",
            contentUri = targetFile.uri.toString()
        )
    }

    fun deleteExportedFile(exportedPath: String, exportedUri: String): Result<Boolean> = runCatching {
        if (exportedPath.isBlank() && exportedUri.isBlank()) return@runCatching false
        val deleted = when {
            exportedUri.isNotBlank() -> context.contentResolver.delete(Uri.parse(exportedUri), null, null) > 0
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> deleteUniquelyMatchedMediaStoreExport(exportedPath)
            File(exportedPath).isAbsolute -> File(exportedPath).delete()
            else -> false
        }
        AppLogger.event(
            "file",
            "deleteExportedFile",
            "exportedPath" to exportedPath,
            "exportedUri" to exportedUri.ifBlank { "blank" },
            "deleted" to deleted
        )
        deleted
    }

    private fun requireReadableFile(filePath: String): File {
        val file = File(filePath)
        AppLogger.event(
            "file",
            "requireReadableFile",
            "filePath" to filePath,
            "exists" to file.exists(),
            "isFile" to file.isFile,
            "size" to if (file.exists()) file.length() else 0L
        )
        require(file.exists() && file.isFile) { "本地文件不存在" }
        return file
    }

    private fun contentUri(file: File): Uri {
        AppLogger.event("file", "contentUri", "filePath" to file.absolutePath)
        return FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file
        )
    }

    private fun shareIntent(file: File, uri: Uri, title: String): Intent {
        return Intent(Intent.ACTION_SEND)
            .setType(mimeType(file))
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TITLE, title.ifBlank { file.nameWithoutExtension })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun exportWithMediaStore(file: File): ExportedMediaFile {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, mimeType(file))
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Media Downloader")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("无法创建系统下载文件")
        AppLogger.event("file", "mediaStoreInsert", "uri" to uri, "fileName" to file.name)

        try {
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: error("无法写入系统下载文件")

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (error: Throwable) {
            AppLogger.error("file", "mediaStoreExportFailure", error, "uri" to uri, "fileName" to file.name)
            resolver.delete(uri, null, null)
            throw error
        }

        return ExportedMediaFile(
            displayPath = "下载/Media Downloader/${file.name}",
            contentUri = uri.toString()
        )
    }

    private fun exportLegacy(file: File): ExportedMediaFile {
        val dir = Environment
            .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .resolve("Media Downloader")
        dir.mkdirs()
        val output = dir.resolve(file.name)
        file.copyTo(output, overwrite = true)
        AppLogger.event("file", "legacyExport", "output" to output.absolutePath)
        return ExportedMediaFile(displayPath = output.absolutePath)
    }

    private fun deleteUniquelyMatchedMediaStoreExport(exportedPath: String): Boolean {
        val fileName = exportedPath.substringAfterLast('/').takeIf { it.isNotBlank() } ?: return false
        val resolver = context.contentResolver
        val uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val matches = buildList {
            resolver.query(
                uri,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?",
                arrayOf(fileName, "${Environment.DIRECTORY_DOWNLOADS}/Media Downloader/"),
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                while (cursor.moveToNext()) {
                    add(ContentUris.withAppendedId(uri, cursor.getLong(idColumn)))
                }
            }
        }
        if (matches.size != 1) {
            AppLogger.warn(
                "file",
                "deleteLegacyExportSkipped",
                "exportedPath" to exportedPath,
                "matches" to matches.size
            )
            return false
        }
        return resolver.delete(matches.single(), null, null) > 0
    }

    private fun mimeType(file: File): String {
        val extension = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: when (extension) {
                "mp4", "m4v" -> "video/mp4"
                "webm" -> "video/webm"
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                else -> "application/octet-stream"
            }
    }

    private companion object {
        data class FixedShareApp(
            val label: String,
            val packageCandidates: List<String>,
            val iconRes: Int
        )

        val fixedShareApps = listOf(
            FixedShareApp("微信", listOf("com.tencent.mm"), R.drawable.ic_share_wechat),
            FixedShareApp("QQ", listOf("com.tencent.mobileqq"), R.drawable.ic_share_qq),
            FixedShareApp("小红书", listOf("com.xingin.xhs"), R.drawable.ic_share_xiaohongshu),
            FixedShareApp("微博", listOf("com.sina.weibo"), R.drawable.ic_share_weibo),
            FixedShareApp("X", listOf("com.twitter.android"), R.drawable.ic_share_x),
            FixedShareApp("YouTube", listOf("com.google.android.youtube"), R.drawable.ic_share_youtube),
            FixedShareApp("抖音", listOf("com.ss.android.ugc.aweme"), R.drawable.ic_share_douyin),
            FixedShareApp("Instagram", listOf("com.instagram.android"), R.drawable.ic_share_instagram),
            FixedShareApp("Telegram", listOf("org.telegram.messenger"), R.drawable.ic_share_telegram)
        )
    }
}
