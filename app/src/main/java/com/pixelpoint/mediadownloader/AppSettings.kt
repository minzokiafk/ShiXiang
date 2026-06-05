package com.pixelpoint.mediadownloader

import android.content.Context
import android.net.Uri

class AppSettings(context: Context) {
    private val preferences = context.getSharedPreferences("media_downloader_settings", Context.MODE_PRIVATE)

    var defaultStorageLocation: StorageLocation
        get() = StorageLocation.fromValue(
            preferences.getString(KEY_DEFAULT_STORAGE, StorageLocation.AppPrivate.value)
        )
        set(value) {
            AppLogger.event("settings", "setDefaultStorageLocation", "location" to value.value)
            preferences.edit().putString(KEY_DEFAULT_STORAGE, value.value).apply()
        }

    var audioFormatPreferred: Int
        get() = preferences.getInt(KEY_AUDIO_FORMAT, 0)
        set(value) {
            preferences.edit().putInt(KEY_AUDIO_FORMAT, value).apply()
        }

    var audioQuality: Int
        get() = preferences.getInt(KEY_AUDIO_QUALITY, 0)
        set(value) {
            preferences.edit().putInt(KEY_AUDIO_QUALITY, value).apply()
        }

    var videoFormat: Int
        get() = preferences.getInt(KEY_VIDEO_FORMAT, 1) // 1 = Compatibility
        set(value) {
            preferences.edit().putInt(KEY_VIDEO_FORMAT, value).apply()
        }

    var videoQuality: Int
        get() = preferences.getInt(KEY_VIDEO_QUALITY, 0) // 0 = Best
        set(value) {
            preferences.edit().putInt(KEY_VIDEO_QUALITY, value).apply()
        }

    var rateLimitEnabled: Boolean
        get() = preferences.getBoolean(KEY_RATE_LIMIT_ENABLED, false)
        set(value) {
            preferences.edit().putBoolean(KEY_RATE_LIMIT_ENABLED, value).apply()
        }

    var rateLimitValue: Int
        get() = preferences.getInt(KEY_RATE_LIMIT_VALUE, 500)
        set(value) {
            preferences.edit().putInt(KEY_RATE_LIMIT_VALUE, value).apply()
        }

    var cellularDownload: Boolean
        get() = preferences.getBoolean(KEY_CELLULAR_DOWNLOAD, true)
        set(value) {
            preferences.edit().putBoolean(KEY_CELLULAR_DOWNLOAD, value).apply()
        }

    var ytDlpLastUpdate: Long
        get() = preferences.getLong(KEY_YT_DLP_LAST_UPDATE, 0L)
        set(value) {
            preferences.edit().putLong(KEY_YT_DLP_LAST_UPDATE, value).apply()
        }

    var ytDlpVersion: String
        get() = preferences.getString(KEY_YT_DLP_VERSION, "").orEmpty()
        set(value) {
            preferences.edit().putString(KEY_YT_DLP_VERSION, value).apply()
        }

    var customStorageUri: String
        get() = preferences.getString(KEY_CUSTOM_STORAGE_URI, "").orEmpty()
        set(value) {
            preferences.edit().putString(KEY_CUSTOM_STORAGE_URI, value).apply()
        }

    var customStoragePath: String
        get() = preferences.getString(KEY_CUSTOM_STORAGE_PATH, "").orEmpty()
        set(value) {
            preferences.edit().putString(KEY_CUSTOM_STORAGE_PATH, value).apply()
        }

    fun cookieForUrl(url: String): String {
        val host = normalizedHost(url).ifBlank { return "" }
        val candidates = cookieStorageHosts(host)
        val cookie = candidates
            .asSequence()
            .map { it to preferences.getString(cookieKey(it), "").orEmpty() }
            .firstOrNull { it.second.isNotBlank() }
            ?.second
            .orEmpty()
        AppLogger.event(
            "settings",
            "readCookie",
            "host" to host,
            "candidateHosts" to candidates.joinToString(","),
            "cookie" to AppLogger.cookieSummary(cookie),
            "cookieNames" to AppLogger.cookieNamesSummary(cookie)
        )
        return cookie
    }

    fun hasCookieForUrl(url: String): Boolean {
        val hasCookie = cookieForUrl(url).isNotBlank()
        AppLogger.event("settings", "hasCookie", "url" to url, "hasCookie" to hasCookie)
        return hasCookie
    }

    fun setCookieForUrl(url: String, cookie: String) {
        val host = normalizedHost(url).ifBlank { return }
        val storageHosts = cookieStorageHosts(host)
        AppLogger.event(
            "settings",
            "setCookie",
            "host" to host,
            "storageHosts" to storageHosts.joinToString(","),
            "cookie" to AppLogger.cookieSummary(cookie),
            "cookieNames" to AppLogger.cookieNamesSummary(cookie)
        )
        preferences.edit().apply {
            storageHosts.forEach { putString(cookieKey(it), cookie.trim()) }
        }.apply()
    }

    fun markLoginStateVisibleForUrl(url: String) {
        val host = displayLoginHostForUrl(url).ifBlank { return }
        val hosts = visibleLoginHosts().toMutableSet()
        if (hosts.add(host)) {
            AppLogger.event("settings", "markVisibleLoginState", "host" to host)
            preferences.edit().putStringSet(KEY_VISIBLE_LOGIN_HOSTS, hosts).apply()
        }
    }

    fun savedLoginStates(): List<WebsiteLoginState> {
        return visibleLoginHosts()
            .asSequence()
            .filter { host -> isVisibleLoginHost(host) }
            .mapNotNull { host ->
                val cookie = cookieForUrl("https://$host/")
                if (cookie.isBlank()) return@mapNotNull null
                WebsiteLoginState(
                    host = host,
                    url = "https://$host/",
                    cookieSummary = AppLogger.cookieNamesSummary(cookie)
                )
            }
            .sortedBy { it.host }
            .toList()
    }

    fun deleteCookieForUrl(url: String) {
        val host = normalizedHost(url).ifBlank { return }
        val storageHosts = cookieStorageHosts(host)
        AppLogger.event("settings", "deleteCookie", "host" to host, "storageHosts" to storageHosts.joinToString(","))
        preferences.edit().apply {
            storageHosts.forEach { remove(cookieKey(it)) }
        }.apply()
    }

    fun deleteCookieForHost(host: String) {
        val normalized = normalizedHost("https://$host/").ifBlank { host.lowercase().removePrefix("www.").removePrefix("m.") }
        val storageHosts = cookieStorageHosts(normalized)
        AppLogger.event("settings", "deleteCookieHost", "host" to normalized, "storageHosts" to storageHosts.joinToString(","))
        val visibleHosts = visibleLoginHosts() - normalized
        preferences.edit().apply {
            storageHosts.forEach { remove(cookieKey(it)) }
            remove(cookieKey(host))
            putStringSet(KEY_VISIBLE_LOGIN_HOSTS, visibleHosts)
        }.apply()
    }

    fun refererForUrl(url: String): String {
        val host = normalizedHost(url).ifBlank { return "" }
        if (host == "instagram.com") {
            AppLogger.event("settings", "readReferer", "host" to host, "referer" to "blank")
            return ""
        }
        val referer = preferences.getString(refererKey(host), "").orEmpty()
        AppLogger.event("settings", "readReferer", "host" to host, "referer" to referer.ifBlank { "blank" })
        return referer
    }

    fun storedRefererForUrl(url: String): String {
        val host = normalizedHost(url).ifBlank { return "" }
        return preferences.getString(refererKey(host), "").orEmpty()
    }

    fun setRefererForUrl(url: String, referer: String) {
        val host = normalizedHost(url).ifBlank { return }
        AppLogger.event("settings", "setReferer", "host" to host, "referer" to referer.ifBlank { "blank" })
        preferences.edit().putString(refererKey(host), referer.trim()).apply()
    }

    companion object {
        private const val KEY_DEFAULT_STORAGE = "default_storage"
        private const val KEY_COOKIE_PREFIX = "cookie_for_host_"
        private const val KEY_REFERER_PREFIX = "referer_for_host_"
        private const val KEY_VISIBLE_LOGIN_HOSTS = "visible_login_hosts"

        private const val KEY_AUDIO_FORMAT = "audio_format_preferred"
        private const val KEY_AUDIO_QUALITY = "audio_quality"
        private const val KEY_VIDEO_FORMAT = "video_format"
        private const val KEY_VIDEO_QUALITY = "video_quality"
        private const val KEY_RATE_LIMIT_ENABLED = "rate_limit_enabled"
        private const val KEY_RATE_LIMIT_VALUE = "rate_limit_value"
        private const val KEY_CELLULAR_DOWNLOAD = "cellular_download"
        private const val KEY_YT_DLP_LAST_UPDATE = "yt_dlp_last_update"
        private const val KEY_YT_DLP_VERSION = "yt_dlp_version"
        private const val KEY_CUSTOM_STORAGE_URI = "custom_storage_uri"
        private const val KEY_CUSTOM_STORAGE_PATH = "custom_storage_path"

        private fun cookieKey(host: String): String = KEY_COOKIE_PREFIX + host
        private fun refererKey(host: String): String = KEY_REFERER_PREFIX + host

        private fun cookieStorageHosts(host: String): List<String> {
            return when (host) {
                "twitter.com" -> listOf("twitter.com", "x.com", "mobile.twitter.com")
                "weibo.com" -> listOf("weibo.com", "video.weibo.com", "m.weibo.cn")
                "douyin.com" -> listOf("douyin.com", "iesdouyin.com")
                "instagram.com" -> listOf("instagram.com")
                "threads.net" -> listOf("threads.net", "threads.com")
                else -> listOf(host)
            }
        }

        private fun isVisibleLoginHost(host: String): Boolean {
            return host in visibleLoginHostPriority()
        }

        private fun visibleLoginHostPriority(): List<String> {
            return listOf(
                "youtube.com",
                "douyin.com",
                "xiaohongshu.com",
                "bilibili.com",
                "weibo.com",
                "twitter.com",
                "instagram.com",
                "threads.net"
            )
        }

        private fun normalizedHost(url: String): String {
            return runCatching {
                val host = Uri.parse(url).host.orEmpty()
                    .lowercase()
                    .removePrefix("www.")
                    .removePrefix("m.")
                when {
                    host == "youtu.be" || host.endsWith(".youtube.com") -> "youtube.com"
                    host == "x.com" || host.endsWith(".x.com") -> "twitter.com"
                    host == "twitter.com" || host.endsWith(".twitter.com") -> "twitter.com"
                    host == "weibo.com" || host.endsWith(".weibo.com") || host == "weibo.cn" || host.endsWith(".weibo.cn") -> "weibo.com"
                    host == "douyin.com" || host.endsWith(".douyin.com") || host == "iesdouyin.com" || host.endsWith(".iesdouyin.com") -> "douyin.com"
                    host == "instagram.com" || host.endsWith(".instagram.com") -> "instagram.com"
                    host == "threads.net" || host.endsWith(".threads.net") || host == "threads.com" || host.endsWith(".threads.com") -> "threads.net"
                    host == "bilibili.com" || host.endsWith(".bilibili.com") -> "bilibili.com"
                    host == "xiaohongshu.com" || host.endsWith(".xiaohongshu.com") -> "xiaohongshu.com"
                    else -> host
                }
            }.getOrDefault("")
        }
    }

    private fun visibleLoginHosts(): Set<String> {
        if (!preferences.contains(KEY_VISIBLE_LOGIN_HOSTS)) {
            val inferred = inferVisibleLoginHostsFromCookieKeys()
            preferences.edit().putStringSet(KEY_VISIBLE_LOGIN_HOSTS, inferred).apply()
            AppLogger.event("settings", "migrateVisibleLoginStates", "hosts" to inferred.joinToString(","))
            return inferred
        }
        return preferences.getStringSet(KEY_VISIBLE_LOGIN_HOSTS, emptySet()).orEmpty()
            .mapNotNull { displayLoginHostForUrl("https://$it/").ifBlank { null } }
            .toSet()
    }

    private fun inferVisibleLoginHostsFromCookieKeys(): Set<String> {
        return preferences.all
            .mapNotNull { (key, value) ->
                if (!key.startsWith(KEY_COOKIE_PREFIX)) return@mapNotNull null
                if ((value as? String).isNullOrBlank()) return@mapNotNull null
                val host = key.removePrefix(KEY_COOKIE_PREFIX)
                displayLoginHostForUrl("https://$host/")
            }
            .filter { it.isNotBlank() && isVisibleLoginHost(it) }
            .toSet()
    }

    private fun displayLoginHostForUrl(url: String): String {
        val host = normalizedHost(url)
        return if (isVisibleLoginHost(host)) host else ""
    }
}

data class WebsiteLoginState(
    val host: String,
    val url: String,
    val cookieSummary: String
)

enum class StorageLocation(val value: String, val label: String, val description: String) {
    AppPrivate(
        value = "app_private",
        label = "应用内保存",
        description = "下载完成后保存在应用内，需要时手动保存到系统下载目录"
    ),
    Downloads(
        value = "downloads",
        label = "系统下载目录",
        description = "下载完成后自动复制到系统下载目录，应用内仍保留一份用于打开和分享"
    ),
    Custom(
        value = "custom",
        label = "自定义保存目录",
        description = "下载完成后自动保存到您指定的文件夹目录"
    );

    companion object {
        fun fromValue(value: String?): StorageLocation {
            return entries.firstOrNull { it.value == value } ?: AppPrivate
        }
    }
}
