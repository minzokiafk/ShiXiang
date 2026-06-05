package com.pixelpoint.mediadownloader

object UrlExtractor {
    private val webUrlPattern = Regex("""https?://[^\s]+""")

    fun firstUrl(text: String): String? {
        return urls(text).firstOrNull()
    }

    fun bestMediaUrl(text: String): String? {
        val urls = urls(text)
        return urls.firstOrNull { it.isTwitterStatusUrl() }
            ?: urls.firstOrNull { it.isLikelyMediaUrl() }
            ?: urls.firstOrNull { !it.isGenericPlatformUrl() }
            ?: urls.firstOrNull()
    }

    fun isWebUrl(value: String): Boolean {
        return value.startsWith("http://") || value.startsWith("https://")
    }

    fun hostLabel(value: String): String {
        return value
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .ifBlank { "媒体链接" }
    }

    fun mainDomainLabel(value: String): String {
        val host = hostLabel(value)
            .substringBefore(":")
            .lowercase()
            .removePrefix("www.")
            .removePrefix("m.")
            .removePrefix("mobile.")
        if (host.isBlank() || host == "媒体链接") return "媒体链接"

        val parts = host.split(".").filter { it.isNotBlank() }
        if (parts.size <= 2) return host

        val secondLevelSuffixes = setOf("com", "net", "org", "gov", "edu", "co")
        val labelCount = if (
            parts.size >= 3 &&
            parts[parts.lastIndex - 1] in secondLevelSuffixes &&
            parts.last().length == 2
        ) {
            3
        } else {
            2
        }
        return parts.takeLast(labelCount).joinToString(".")
    }

    fun prefersDirectDownload(value: String): Boolean {
        return value.isDouyinUrl()
    }

    fun requiresFreshWebSession(value: String): Boolean {
        return value.isDouyinUrl() || value.isPornhubUrl() || value.hasThreadsHost()
    }

    fun isThreadsUrl(value: String): Boolean {
        return value.hasThreadsHost()
    }

    private fun urls(text: String): List<String> {
        return webUrlPattern.findAll(text)
            .map { it.value.cleanUrl() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
    }

    private fun String.cleanUrl(): String {
        return trim()
            .trimEnd('.', ',', ')', ']', '。', '，')
            .substringBefore("?s=")
            .substringBefore("&s=")
    }

    private fun String.isTwitterStatusUrl(): Boolean {
        val value = lowercase()
        val host = value
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
        val isTwitterHost = host == "x.com" ||
            host.endsWith(".x.com") ||
            host == "twitter.com" ||
            host.endsWith(".twitter.com")
        return isTwitterHost && ("/status/" in value || "/i/status/" in value)
    }

    private fun String.isLikelyMediaUrl(): Boolean {
        val value = lowercase()
        return "/video/" in value ||
            "youtube.com/watch" in value ||
            "youtu.be/" in value ||
            "bilibili.com/video/" in value ||
            "b23.tv/" in value ||
            "douyin.com/" in value ||
            "iesdouyin.com/" in value ||
            "threads.net/" in value ||
            "threads.com/" in value ||
            "instagram.com/" in value ||
            "cdninstagram.com/" in value ||
            "fbcdn.net/" in value ||
            "fbsbx.com/" in value ||
            "xiaohongshu.com/" in value ||
            "xhslink.com/" in value ||
            "rednote.com/" in value
    }

    private fun String.isGenericPlatformUrl(): Boolean {
        val host = lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .removePrefix("www.")
        return host in setOf(
            "google.com",
            "baidu.com",
            "bing.com",
            "weixin.qq.com",
            "mp.weixin.qq.com",
            "m.weibo.cn"
        )
    }

    private fun String.isDouyinUrl(): Boolean {
        val host = lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
        return host == "douyin.com" ||
            host.endsWith(".douyin.com") ||
            host == "iesdouyin.com" ||
            host.endsWith(".iesdouyin.com")
    }

    private fun String.isPornhubUrl(): Boolean {
        val host = lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
        return host == "pornhub.com" ||
            host.endsWith(".pornhub.com") ||
            host == "pornhub.org" ||
            host.endsWith(".pornhub.org")
    }

    private fun String.hasThreadsHost(): Boolean {
        val host = lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
        return host == "threads.net" ||
            host.endsWith(".threads.net") ||
            host == "threads.com" ||
            host.endsWith(".threads.com")
    }

}
