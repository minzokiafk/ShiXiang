package com.pixelpoint.mediadownloader

object DownloadFailureClassifier {
    fun classify(rawError: String): String {
        val error = rawError.ifBlank { "下载失败" }
        val lower = error.lowercase()
        return when {
            "drm" in lower || "widevine" in lower -> "该视频可能受到 DRM 保护，暂不支持下载"
            "requested format is not available" in lower -> "当前清晰度或媒体格式不可用，请重新识别并选择其他清晰度"
            "fresh cookies" in lower && "douyin" in lower -> "抖音需要刷新网页登录态，请打开辅助页面后重试"
            isExpiredXiaohongshuLink(lower) -> "小红书链接已失效或不可访问"
            "unsupported url" in lower -> "暂不支持这个平台或链接格式"
            "no video formats found" in lower -> "该链接可能是小红书或其他平台的图文笔记（仅包含图片），当前本地引擎仅支持下载视频"
            "n challenge" in lower ||
                "javascript runtime" in lower ||
                "js runtime" in lower ||
                "ejs" in lower ||
                "po token" in lower ||
                "only images are available" in lower -> "YouTube 需要额外的 JS 解码组件，当前本地引擎暂不能解析可下载视频"
            "http error 410" in lower || "410: gone" in lower -> "页面媒体接口已失效，请通过页面播放并捕获视频流"
            "login" in lower ||
                "sign in" in lower ||
                "cookie" in lower ||
                "authentication" in lower ||
                "unauthorized" in lower ||
                "private" in lower ||
                "age-restricted" in lower ||
                "registered users" in lower ||
                "会员" in error ||
                "权限" in error ||
                "授权" in error ||
                "vip" in lower -> "需要登录或会员权限，当前本地引擎没有可用授权"
            "403" in lower || "forbidden" in lower -> "平台拒绝访问，可能需要登录态、地区权限或请求头"
            "404" in lower || "not found" in lower || "unavailable" in lower -> "视频不可用，可能已删除、下架或链接过期"
            "ssl" in lower || "tls" in lower || "certificate" in lower || "unexpected_eof" in lower || "eof occurred" in lower -> "网络 TLS 连接失败，可能是站点拦截、地区网络限制或证书握手失败"
            "timed out" in lower || "timeout" in lower || "network" in lower || "connection" in lower -> "网络连接失败，请检查网络后重试"
            "处理组件不可用" in error -> error
            "媒体校验失败" in error -> error
            "ffmpeg" in lower || "合并" in error || "音频" in error -> "媒体合并失败，可能是音视频分片不完整或格式不兼容"
            else -> error
        }
    }

    fun isExpiredXiaohongshuLink(rawError: String): Boolean {
        val lower = rawError.lowercase()
        val isXiaohongshu = "xiaohongshu.com" in lower || "xhslink.com" in lower || "rednote.com" in lower
        val isUnavailable = "/404" in lower || "errorcode=-510001" in lower || "not found" in lower
        return isXiaohongshu && isUnavailable
    }
}
