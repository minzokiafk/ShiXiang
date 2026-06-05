package com.pixelpoint.mediadownloader

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.LruCache
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.outlined.Home as HomeOutlined
import androidx.compose.material.icons.outlined.Settings as SettingsOutlined
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pixelpoint.mediadownloader.engine.DownloadFormatOption
import com.pixelpoint.mediadownloader.engine.LocalDownloadEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@Composable
fun WebViewCookieDialog(
    task: DownloadTask,
    onDismiss: () -> Unit,
    onConfirm: (DownloadTask, String, String, String) -> Unit,
    titleOverride: String? = null,
    guidanceOverride: String? = null,
    confirmTextOverride: String? = null,
    requireCapturedMedia: Boolean = true
) {
    val confirmInteractionSource = remember { MutableInteractionSource() }
    val dismissInteractionSource = remember { MutableInteractionSource() }
    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    var currentUrl by remember(task.id) { mutableStateOf(task.sourceUrl) }
    var capturedMediaUrls by remember(task.id) { mutableStateOf<List<String>>(emptyList()) }
    var selectedMediaUrl by remember(task.id) { mutableStateOf("") }
    var manuallySelectedMedia by remember(task.id) { mutableStateOf(false) }
    var capturedPageTitle by remember(task.id) { mutableStateOf("") }
    var pageStatus by remember(task.id) { mutableStateOf<String?>("正在加载页面") }
    val allowMediaCapture = requireCapturedMedia
    val sortedCapturedMediaUrls = remember(capturedMediaUrls) {
        capturedMediaUrls
            .distinct()
            .sortedByDescending { it.mediaCandidateScore(task.sourceUrl) }
            .take(8)
    }
    fun addMediaCandidate(url: String, source: String) {
        if (!allowMediaCapture || !url.isLikelyPlayableMediaRequest()) return
        if (url in capturedMediaUrls) return
        val next = (capturedMediaUrls + url)
            .distinct()
            .sortedByDescending { it.mediaCandidateScore(task.sourceUrl) }
            .take(20)
        capturedMediaUrls = next
        if (!manuallySelectedMedia) {
            selectedMediaUrl = next.firstOrNull().orEmpty()
        }
        AppLogger.event(
            "cookie",
            "mediaCandidateCaptured",
            "source" to source,
            "score" to url.mediaCandidateScore(task.sourceUrl),
            "url" to url
        )
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                titleOverride ?: if (UrlExtractor.requiresFreshWebSession(task.sourceUrl)) "视频捕获" else "登录态辅助"
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = UrlExtractor.hostLabel(task.sourceUrl),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = guidanceOverride ?: if (task.sourceUrl.isDouyinUrl()) {
                        "抖音页面可能不会完整展示。检测到当前视频流后即可下载。"
                    } else if (task.sourceUrl.isThreadsUrl()) {
                        "请在下方页面打开并播放目标视频。检测到视频流后即可下载。"
                    } else if (task.sourceUrl.isPornhubLikeUrl()) {
                        "请在下方页面播放目标视频。检测到主视频流后即可下载。"
                    } else if (allowMediaCapture) {
                        "在下方页面完成登录、年龄验证或权限确认，并尽量让目标视频开始播放。检测到多个媒体流时可手动选择。"
                    } else {
                        "在下方页面完成登录、年龄验证或权限确认，然后使用原始 YouTube 链接重试。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                pageStatus?.let { status ->
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (sortedCapturedMediaUrls.isNotEmpty()) {
                    Text(
                        text = "已检测到 ${sortedCapturedMediaUrls.size} 个媒体流，默认选择最可能的正片",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(330.dp)
                        .clip(RoundedCornerShape(15.dp)),
                    factory = { context ->
                        CookieManager.getInstance().setAcceptCookie(true)
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            if (task.sourceUrl.isPornhubLikeUrl() || task.sourceUrl.isThreadsUrl()) {
                                settings.userAgentString = settings.userAgentString.browserLikeUserAgent()
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                AppLogger.event(
                                    "cookie",
                                    "captureBrowserProfileApplied",
                                    "platform" to when {
                                        task.sourceUrl.isThreadsUrl() -> "threads"
                                        else -> "pornhub"
                                    }
                                )
                            }
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webChromeClient = object : WebChromeClient() {
                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    val pageTitle = title.orEmpty().usableCapturedPageTitle()
                                    if (pageTitle.isNotBlank()) {
                                        capturedPageTitle = pageTitle
                                        AppLogger.event("cookie", "assistPageTitleCaptured", "title" to pageTitle, "url" to view?.url.orEmpty())
                                    }
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    pageStatus = "正在加载页面"
                                    AppLogger.event("cookie", "assistPageStarted", "url" to url.orEmpty())
                                    super.onPageStarted(view, url, favicon)
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val requestUrl = request?.url?.toString().orEmpty()
                                    if (task.sourceUrl.isDouyinUrl() && requestUrl.isDouyinAppDeepLink()) {
                                        AppLogger.event("cookie", "blockDouyinAppDeepLink", "url" to requestUrl)
                                        view?.post {
                                            view.collectDomVideoCandidates { url -> addMediaCandidate(url, "dom") }
                                            view.requestDouyinVideoPlaybackAndCollect { url -> addMediaCandidate(url, "dom_video") }
                                        }
                                        return true
                                    }
                                    return false
                                }

                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): android.webkit.WebResourceResponse? {
                                    val requestUrl = request?.url?.toString().orEmpty()
                                    if (allowMediaCapture && requestUrl.isLikelyPlayableMediaRequest()) {
                                        view?.post { addMediaCandidate(requestUrl, "network") }
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: android.webkit.WebResourceError?
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        pageStatus = "页面加载失败，请关闭后重试"
                                        AppLogger.warn(
                                            "cookie",
                                            "assistPageLoadFailed",
                                            "url" to request.url.toString(),
                                            "code" to error?.errorCode,
                                            "description" to error?.description
                                        )
                                    }
                                    super.onReceivedError(view, request, error)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    currentUrl = url ?: currentUrl
                                    pageStatus = null
                                    AppLogger.event("cookie", "assistPageFinished", "url" to currentUrl)
                                    CookieManager.getInstance().flush()
                                    view?.collectDomVideoCandidates { mediaUrl -> addMediaCandidate(mediaUrl, "dom") }
                                    if (task.sourceUrl.isDouyinUrl()) {
                                        view?.requestDouyinVideoPlaybackAndCollect { mediaUrl -> addMediaCandidate(mediaUrl, "dom_video") }
                                    }
                                }
                            }
                            loadUrl(task.sourceUrl)
                        }
                    },
                    update = { webView ->
                        if (webView.url.isNullOrBlank()) {
                            webView.loadUrl(task.sourceUrl)
                        } else {
                            webView.collectDomVideoCandidates { mediaUrl -> addMediaCandidate(mediaUrl, "dom") }
                        }
                    }
                )
                if (sortedCapturedMediaUrls.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        sortedCapturedMediaUrls.forEach { mediaUrl ->
                            val selected = mediaUrl == selectedMediaUrl
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            manuallySelectedMedia = true
                                            selectedMediaUrl = mediaUrl
                                        }
                                    ),
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) BrandBeigeSoft else MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = {
                                            manuallySelectedMedia = true
                                            selectedMediaUrl = mediaUrl
                                        }
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = mediaUrl.mediaCandidateTitle(),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = mediaUrl,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    CookieManager.getInstance().flush()
                    val manager = CookieManager.getInstance()
                    val cookie = context.webViewCookieFileForTask(task.sourceUrl, currentUrl)
                        .ifBlank { manager.mergedCookiesForTask(task.sourceUrl, currentUrl) }
                    onConfirm(task, cookie, if (allowMediaCapture) selectedMediaUrl else "", capturedPageTitle)
                },
                enabled = !requireCapturedMedia || !UrlExtractor.requiresFreshWebSession(task.sourceUrl) || selectedMediaUrl.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .elasticPress(confirmInteractionSource),
                colors = brandButtonColors(confirmInteractionSource),
                interactionSource = confirmInteractionSource,
                shape = RoundedCornerShape(23.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Rounded.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(confirmTextOverride ?: "开始下载")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .elasticPress(dismissInteractionSource),
                interactionSource = dismissInteractionSource
            ) {
                Text(
                    text = "取消",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}


fun String.isLikelyPlayableMediaRequest(): Boolean {
    val value = lowercase()
    if (!startsWith("http://") && !startsWith("https://")) return false
    if (listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".svg", ".css", ".js", ".woff", ".woff2", ".ttf", ".otf", "/rsrc.php").any { it in value }) {
        return false
    }
    if ("static.cdninstagram.com" in value) return false
    return listOf(
        ".m3u8",
        ".mp4",
        ".m4s",
        ".mpd",
        "/hls/",
        "master.m3u8",
        "playlist.m3u8",
        "videoplayback",
        "douyinvod.com",
        "scontent.cdninstagram.com",
        "fbcdn.net",
        "fbsbx.com",
        "/video/tos/",
        "/aweme/v1/play",
        "media"
    ).any { marker -> marker in value }
}

fun String.isYouTubeUrl(): Boolean {
    val host = runCatching { Uri.parse(this).host.orEmpty().lowercase() }.getOrDefault("")
        .removePrefix("www.")
        .removePrefix("m.")
        .removePrefix("music.")
    return host == "youtube.com" || host == "youtu.be" || host.endsWith(".youtube.com")
}

fun String.isDouyinUrl(): Boolean {
    val host = runCatching { Uri.parse(this).host.orEmpty().lowercase() }.getOrDefault("")
        .removePrefix("www.")
        .removePrefix("m.")
    return host == "douyin.com" ||
        host.endsWith(".douyin.com") ||
        host == "iesdouyin.com" ||
        host.endsWith(".iesdouyin.com")
}

fun String.isPornhubLikeUrl(): Boolean {
    val host = runCatching { Uri.parse(this).host.orEmpty().lowercase() }.getOrDefault("")
    return host == "pornhub.com" ||
        host.endsWith(".pornhub.com") ||
        host == "pornhub.org" ||
        host.endsWith(".pornhub.org")
}

fun String.isThreadsUrl(): Boolean {
    val host = runCatching { Uri.parse(this).host.orEmpty().lowercase() }.getOrDefault("")
        .removePrefix("www.")
        .removePrefix("m.")
    return host == "threads.net" ||
        host.endsWith(".threads.net") ||
        host == "threads.com" ||
        host.endsWith(".threads.com")
}

fun String.isPhnCdnUrl(): Boolean {
    val host = runCatching { Uri.parse(this).host.orEmpty().lowercase() }.getOrDefault("")
    return host.endsWith(".phncdn.com") || host.endsWith(".phncdn.net")
}

fun String.isMetaMediaCdnUrl(): Boolean {
    val host = runCatching { Uri.parse(this).host.orEmpty().lowercase() }.getOrDefault("")
    return host.endsWith(".cdninstagram.com") ||
        host.endsWith(".fbcdn.net") ||
        host.endsWith(".fbsbx.com")
}

fun String.isDouyinAppDeepLink(): Boolean {
    val scheme = runCatching { Uri.parse(this).scheme.orEmpty().lowercase() }.getOrDefault("")
    return scheme == "snssdk1128" || scheme == "snssdk2329" || scheme == "aweme"
}

fun String.usableCapturedPageTitle(): String {
    val title = trim().replace(Regex("""\s+"""), " ").take(180)
    return when {
        title.isBlank() -> ""
        title.equals("网页无法打开", ignoreCase = true) -> ""
        title.startsWith("http://", ignoreCase = true) -> ""
        title.startsWith("https://", ignoreCase = true) -> ""
        else -> title
    }
}

fun String.browserLikeUserAgent(): String {
    return replace("; wv", "")
        .replace("Version/4.0 ", "")
}

fun WebView.collectDomVideoCandidates(onCandidate: (String) -> Unit) {
    evaluateJavascript(
        """
        (function() {
          const result = [];
          const add = (url) => {
            if (typeof url !== 'string' || !/^https?:/i.test(url)) return;
            if (!result.includes(url)) result.push(url);
          };
          Array.from(document.querySelectorAll('video')).forEach(video => {
            add(video.currentSrc);
            add(video.src);
            Array.from(video.querySelectorAll('source')).forEach(source => add(source.src));
          });
          performance.getEntriesByType('resource').forEach(entry => {
            const name = entry && entry.name ? entry.name : '';
            if (/(\.m3u8|\.mp4|\.m4s|\.mpd|douyinvod|\/video\/tos\/|\/aweme\/v1\/play|scontent\.cdninstagram|fbcdn|fbsbx)/i.test(name)) add(name);
          });
          return JSON.stringify(result.slice(0, 40));
        })();
        """.trimIndent()
    ) { raw ->
        val value = raw.orEmpty()
            .removeSurrounding("\"")
            .replace("\\\"", "\"")
            .replace("\\\\/", "/")
        runCatching {
            val array = JSONArray(value)
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let(onCandidate)
            }
        }
    }
}

fun WebView.requestDouyinVideoPlaybackAndCollect(onCandidate: (String) -> Unit) {
    listOf(500L, 1_500L, 3_000L).forEach { delayMillis ->
        postDelayed({
            evaluateJavascript(
                """
                (function() {
                  Array.from(document.querySelectorAll('video')).forEach(video => {
                    video.muted = true;
                    const attempt = video.play();
                    if (attempt && attempt.catch) attempt.catch(() => {});
                  });
                })();
                """.trimIndent()
            ) {}
            collectDomVideoCandidates(onCandidate)
        }, delayMillis)
    }
}

fun CookieManager.mergedCookiesForTask(sourceUrl: String, currentUrl: String): String {
    if (sourceUrl.isYouTubeUrl() || currentUrl.isYouTubeUrl()) {
        return youtubeCookieFileForTask(sourceUrl, currentUrl)
    }

    val urls = buildList {
        add(currentUrl)
        add(sourceUrl)
        if (sourceUrl.isDouyinUrl() || currentUrl.isDouyinUrl()) {
            add("https://www.douyin.com/")
            add("https://douyin.com/")
        }
        if (sourceUrl.isThreadsUrl() || currentUrl.isThreadsUrl()) {
            add("https://www.threads.net/")
            add("https://threads.net/")
            add("https://www.instagram.com/")
            add("https://instagram.com/")
        }
    }
    val values = linkedMapOf<String, String>()
    urls.forEach { url ->
        getCookie(url).orEmpty()
            .split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .forEach { item ->
                val name = item.substringBefore("=").trim()
                if (name.isNotBlank()) {
                    values[name] = item
                }
            }
    }
    return values.values.joinToString("; ")
}

fun Context.webViewCookieFileForTask(sourceUrl: String, currentUrl: String): String {
    val domains = cookieExportDomainsForTask(sourceUrl, currentUrl)
    if (domains.isEmpty()) return ""
    val cookies = readWebViewCookies()
        .filter { cookie -> domains.any { domain -> cookie.matchesDomain(domain) } }
        .distinctBy { "${it.domain}|${it.path}|${it.name}|${it.value}" }
    if (cookies.isEmpty()) return ""
    AppLogger.event(
        "cookie",
        "webViewCookieDatabaseExported",
        "cookieCount" to cookies.size,
        "domainCount" to domains.size,
        "sourceHost" to UrlExtractor.hostLabel(sourceUrl)
    )
    return buildString {
        append("# Netscape HTTP Cookie File\n")
        append("# Auto-generated by ShiXiang Web verification\n")
        cookies.forEach { cookie ->
            append(cookie.toNetscapeLine())
            append('\n')
        }
    }
}

private fun Context.readWebViewCookies(): List<WebViewStoredCookie> {
    val cookieDatabase = dataDir.resolve("app_webview/Default/Cookies")
    if (!cookieDatabase.exists()) return emptyList()
    return runCatching {
        SQLiteDatabase.openDatabase(cookieDatabase.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.query(
                "cookies",
                arrayOf("host_key", "expires_utc", "path", "name", "value", "is_secure"),
                null,
                null,
                null,
                null,
                null
            ).use { cursor ->
                val result = mutableListOf<WebViewStoredCookie>()
                val hostIndex = cursor.getColumnIndexOrThrow("host_key")
                val expiryIndex = cursor.getColumnIndexOrThrow("expires_utc")
                val pathIndex = cursor.getColumnIndexOrThrow("path")
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val valueIndex = cursor.getColumnIndexOrThrow("value")
                val secureIndex = cursor.getColumnIndexOrThrow("is_secure")
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex).orEmpty()
                    val value = cursor.getString(valueIndex).orEmpty()
                    val rawHost = cursor.getString(hostIndex).orEmpty()
                    if (name.isBlank() || value.isBlank() || rawHost.isBlank()) continue
                    val domain = if (rawHost.startsWith(".")) rawHost else ".$rawHost"
                    result += WebViewStoredCookie(
                        domain = domain.lowercase(),
                        path = cursor.getString(pathIndex).orEmpty().ifBlank { "/" },
                        name = name,
                        value = value,
                        secure = cursor.getLong(secureIndex) == 1L,
                        expiresAt = chromeCookieExpiryToUnixSeconds(cursor.getLong(expiryIndex))
                    )
                }
                result
            }
        }
    }.onFailure { error ->
        AppLogger.warn("cookie", "webViewCookieDatabaseReadFailed", "error" to error.message)
    }.getOrDefault(emptyList())
}

private data class WebViewStoredCookie(
    val domain: String,
    val path: String,
    val name: String,
    val value: String,
    val secure: Boolean,
    val expiresAt: Long
) {
    fun matchesDomain(targetDomain: String): Boolean {
        val normalizedCookieDomain = domain.removePrefix(".").lowercase()
        val normalizedTarget = targetDomain.removePrefix(".").lowercase()
        return normalizedCookieDomain == normalizedTarget ||
            normalizedCookieDomain.endsWith(".$normalizedTarget") ||
            normalizedTarget.endsWith(".$normalizedCookieDomain")
    }

    fun toNetscapeLine(): String {
        val includeSubdomains = domain.startsWith(".")
        return listOf(
            domain,
            includeSubdomains.toString().uppercase(),
            path.ifBlank { "/" },
            secure.toString().uppercase(),
            expiresAt.toString(),
            name,
            value
        ).joinToString("\t")
    }
}

private fun cookieExportDomainsForTask(sourceUrl: String, currentUrl: String): Set<String> {
    val urls = buildList {
        add(sourceUrl)
        add(currentUrl)
        if (sourceUrl.isYouTubeUrl() || currentUrl.isYouTubeUrl()) {
            add("https://youtube.com/")
            add("https://google.com/")
            add("https://accounts.google.com/")
        }
        if (sourceUrl.isDouyinUrl() || currentUrl.isDouyinUrl()) {
            add("https://douyin.com/")
            add("https://iesdouyin.com/")
        }
        if (sourceUrl.isThreadsUrl() || currentUrl.isThreadsUrl()) {
            add("https://threads.net/")
            add("https://threads.com/")
            add("https://instagram.com/")
            add("https://cdninstagram.com/")
            add("https://fbcdn.net/")
            add("https://fbsbx.com/")
        }
        if (sourceUrl.isPornhubLikeUrl() || currentUrl.isPornhubLikeUrl()) {
            add("https://pornhub.com/")
            add("https://pornhub.org/")
            add("https://phncdn.com/")
            add("https://phncdn.net/")
        }
    }
    return urls
        .mapNotNull { url ->
            Uri.parse(url).host
                ?.lowercase()
                ?.removePrefix("www.")
                ?.removePrefix("m.")
                ?.takeIf { it.isNotBlank() }
        }
        .flatMap { host ->
            when {
                host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com") -> listOf("youtube.com", "google.com")
                host == "b23.tv" || host.endsWith(".b23.tv") || host == "bilibili.com" || host.endsWith(".bilibili.com") -> listOf("bilibili.com", "b23.tv")
                host == "xiaohongshu.com" || host.endsWith(".xiaohongshu.com") || host == "xhslink.com" || host.endsWith(".xhslink.com") || host == "rednote.com" || host.endsWith(".rednote.com") -> listOf("xiaohongshu.com", "xhslink.com", "rednote.com")
                host == "x.com" || host.endsWith(".x.com") || host == "twitter.com" || host.endsWith(".twitter.com") -> listOf("x.com", "twitter.com")
                host == "weibo.com" || host.endsWith(".weibo.com") || host == "weibo.cn" || host.endsWith(".weibo.cn") -> listOf("weibo.com", "weibo.cn", "sina.com.cn")
                host == "douyin.com" || host.endsWith(".douyin.com") || host == "iesdouyin.com" || host.endsWith(".iesdouyin.com") -> listOf("douyin.com", "iesdouyin.com")
                host == "threads.net" || host.endsWith(".threads.net") || host == "threads.com" || host.endsWith(".threads.com") -> listOf("threads.net", "threads.com", "instagram.com", "cdninstagram.com", "fbcdn.net", "fbsbx.com")
                host == "pornhub.com" || host.endsWith(".pornhub.com") || host == "pornhub.org" || host.endsWith(".pornhub.org") -> listOf("pornhub.com", "pornhub.org", "phncdn.com", "phncdn.net")
                else -> listOf(host)
            }
        }
        .toSet()
}

private fun chromeCookieExpiryToUnixSeconds(expiresUtc: Long): Long {
    if (expiresUtc <= 0L) return 0L
    val unixSeconds = (expiresUtc / 1_000_000L) - 11_644_473_600L
    return unixSeconds.coerceAtLeast(0L)
}

fun CookieManager.youtubeCookieFileForTask(sourceUrl: String, currentUrl: String): String {
    val lines = mutableListOf("# Netscape HTTP Cookie File")
    val seen = linkedSetOf<String>()
    val sources = listOf(
        CookieExportSource(currentUrl, cookieDomainForExport(currentUrl)),
        CookieExportSource(sourceUrl, cookieDomainForExport(sourceUrl)),
        CookieExportSource("https://www.youtube.com/", ".youtube.com"),
        CookieExportSource("https://m.youtube.com/", ".youtube.com"),
        CookieExportSource("https://youtube.com/", ".youtube.com"),
        CookieExportSource("https://accounts.google.com/", ".google.com"),
        CookieExportSource("https://myaccount.google.com/", ".google.com"),
        CookieExportSource("https://google.com/", ".google.com")
    )

    sources.forEach { source ->
        val domain = source.domain.ifBlank { return@forEach }
        getCookie(source.url).orEmpty()
            .split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .forEach { item ->
                val name = item.substringBefore("=").trim()
                val value = item.substringAfter("=", "").trim()
                if (name.isNotBlank()) {
                    val key = "$domain|$name|$value"
                    if (seen.add(key)) {
                        lines += "$domain\tTRUE\t/\tFALSE\t0\t$name\t$value"
                    }
                }
            }
    }
    return if (lines.size > 1) lines.joinToString("\n") else ""
}

private data class CookieExportSource(
    val url: String,
    val domain: String
)

fun cookieDomainForExport(url: String): String {
    val host = runCatching { Uri.parse(url).host.orEmpty().lowercase() }.getOrDefault("")
        .removePrefix("www.")
        .removePrefix("m.")
    return when {
        host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com") -> ".youtube.com"
        host == "google.com" || host.endsWith(".google.com") -> ".google.com"
        host.isNotBlank() -> ".$host"
        else -> ""
    }
}

fun String.mediaCandidateScore(pageUrl: String): Int {
    val value = lowercase()
    val isPornhubContext = pageUrl.isPornhubLikeUrl() || this.isPornhubLikeUrl() || this.isPhnCdnUrl()
    val isThreadsContext = pageUrl.isThreadsUrl() || this.isMetaMediaCdnUrl()
    var score = 0
    if (".m3u8" in value || "master.m3u8" in value || "playlist.m3u8" in value) score += 160
    if (".mp4" in value) score += 90
    if (".mpd" in value) score += 70
    if ("videoplayback" in value) score += 60
    if ("/hls/" in value || "hls" in value) score += 80
    if (".m4s" in value) score += 20
    if (UrlExtractor.hostLabel(this).removePrefix("www.") == UrlExtractor.hostLabel(pageUrl).removePrefix("www.")) {
        score += 25
    }
    if (isPornhubContext) {
        if (".m3u8" in value) score += 520
        if ("/hls/" in value || "hls" in value) score += 260
        if ("urlset" in value || "get_media" in value) score += 180
        if ("index-f" in value) score += 180
        if (this.isPhnCdnUrl()) score += 80
        if (".mp4" in value) score -= 70
        if ("original_" in value) score -= 180
    }
    if (isThreadsContext) {
        if (this.isMetaMediaCdnUrl()) score += 180
        if (".mp4" in value) score += 140
        if (".m3u8" in value || "hls" in value) score += 120
        if ("video" in value) score += 70
    }
    if (listOf("preview", "thumb", "thumbnail", "sprite", "poster", "webm_intro").any { it in value }) score -= 120
    if (listOf("ads", "adserver", "doubleclick", "ima", "vast", "promo", "adtng", "creative").any { it in value }) score -= 220
    return score
}

fun String.mediaCandidateTitle(): String {
    val value = lowercase()
    val type = when {
        ".m3u8" in value -> "HLS 播放列表"
        ".mp4" in value -> "MP4 视频"
        ".mpd" in value -> "DASH 播放列表"
        ".m4s" in value -> "视频分片"
        else -> "媒体流"
    }
    val host = UrlExtractor.hostLabel(this).removePrefix("www.")
    return "$type · $host"
}
