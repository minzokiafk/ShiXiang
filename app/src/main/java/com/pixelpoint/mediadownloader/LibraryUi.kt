package com.pixelpoint.mediadownloader

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import coil.request.ImageRequest
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

fun compactMediaInfo(task: DownloadTask): String {
    val source = UrlExtractor.hostLabel(task.sourceUrl)
        .removePrefix("www.")
        .ifBlank { "媒体来源" }
    return listOfNotNull(
        task.formatLabel.ifBlank { null },
        source
    ).joinToString(" · ")
}

fun historyMediaSource(task: DownloadTask): String {
    return platformOrSourceName(task.sourceUrl)
}

fun historyMediaSource(task: DownloadTask, referer: String): String {
    return platformOrSourceName(task.sourceUrl, referer)
}

fun platformOrSourceName(url: String): String {
    return platformOrSourceName(url, "")
}

fun platformOrSourceName(url: String, referer: String): String {
    val sourceUrl = url.ifBlank { referer }
    val refererUrl = referer.ifBlank { sourceUrl }
    return LinkPlatform.fromInput(refererUrl)?.displayName
        ?: LinkPlatform.fromInput(sourceUrl)?.displayName
        ?: UrlExtractor.mainDomainLabel(refererUrl.ifBlank { sourceUrl })
        .ifBlank { "媒体来源" }
}

fun Long.toReadableSize(): String {
    if (this <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var value = toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex += 1
    }
    return if (unitIndex == 0) {
        "${value.toLong()} ${units[unitIndex]}"
    } else {
        "%.1f %s".format(value, units[unitIndex])
    }
}

fun historyTimeLabel(rawCreatedAt: String): String {
    val now = LocalDateTime.now()
    val createdAt = parseHistoryCreatedAt(rawCreatedAt, now)
    val time = createdAt.format(DateTimeFormatter.ofPattern("HH:mm"))
    val daysAgo = java.time.temporal.ChronoUnit.DAYS.between(createdAt.toLocalDate(), now.toLocalDate())

    return when (daysAgo) {
        0L -> "今天 $time"
        1L -> "昨天 $time"
        2L -> "前天 $time"
        else -> {
            val pattern = if (createdAt.year == now.year) "MM月dd日 HH:mm" else "yyyy年MM月dd日 HH:mm"
            createdAt.format(DateTimeFormatter.ofPattern(pattern))
        }
    }
}

fun parseHistoryCreatedAt(rawCreatedAt: String, fallbackNow: LocalDateTime): LocalDateTime {
    val raw = rawCreatedAt.trim()
    if (raw.isBlank()) return fallbackNow

    runCatching {
        return LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }
    runCatching {
        val time = java.time.LocalTime.parse(raw, DateTimeFormatter.ofPattern("HH:mm"))
        return LocalDate.now().atTime(time)
    }
    return fallbackNow
}

fun String.shouldShowBottomNotice(): Boolean {
    val text = trim()
    if (text.isBlank()) return false
    val taskSignals = listOf(
        "后台下载",
        "下载完成",
        "下载已开始",
        "正在下载",
        "正在准备",
        "已保存在应用内",
        "媒体合并失败",
        "视频不可用",
        "需要登录",
        "平台拒绝访问",
        "网络连接失败",
        "下载失败",
        "已取消",
        "正在取消",
        "请选择下载清晰度",
        "该视频可能受到 DRM",
        "Requested format",
        "ERROR:",
        "B/s",
        "KB/s",
        "MB/s",
        "GB/s",
        " / ",
        "% ·"
    )
    return taskSignals.none { text.contains(it, ignoreCase = true) }
}

fun String.toBottomNoticeText(): String {
    val text = trim()
    val shortened = when {
        text == "已接收分享内容，但没有识别到链接" -> "未识别到链接"
        text == "已从系统分享菜单接收链接" -> "已接收链接"
        text == "正在识别当前链接，请稍等" -> "正在识别链接"
        text == "请输入或分享一个有效网页链接" -> "请输入有效链接"
        text == "正在获取页面视频流" -> "正在获取视频"
        text == "正在识别可用清晰度" -> "正在识别清晰度"
        text.startsWith("清晰度识别失败") -> "清晰度识别失败"
        text == "请选择下载清晰度" -> "选择清晰度"
        text == "正在取消后台下载任务" -> "正在取消"
        text == "无法重试，原始链接不可用" -> "无法重试"
        text == "原始链接不可用" -> "链接不可用"
        text == "请先粘贴 Cookie" -> "请先粘贴Cookie"
        text == "Cookie 已保存，将自动带登录态重试" -> "Cookie已保存"
        text == "将使用已保存的登录态重试" -> "将使用登录态"
        text == "还未检测到视频流，请播放目标视频后重试" -> "未检测到视频流"
        text == "还没有读取到 Cookie，请先在页面中登录或完成验证" -> "未读取到Cookie"
        text.startsWith("默认存储位置已改为") -> "存储位置已更新"
        text == "手动更新 yt-dlp 的入口已预留，后续版本接入" -> "功能暂未开放"
        text == "已删除记录，共用文件仍由其他记录保留" -> "记录已删，文件保留"
        text == "已删除记录和应用内文件，系统下载文件未能删除" -> "下载目录未删除"
        text == "已删除记录和本地文件" -> "已删除"
        text.startsWith("已保存到") -> "已保存"
        text.contains("无法保存到系统下载目录") -> "保存失败"
        text.length > 14 -> text.take(13) + "…"
        else -> text
    }
    return if (shortened.length > 14) shortened.take(13) + "…" else shortened
}

fun String?.isErrorNotice(): Boolean {
    val text = this.orEmpty()
    val errorSignals = listOf(
        "失败",
        "错误",
        "无法",
        "没有",
        "请输入",
        "超时",
        "不可用",
        "不支持",
        "拒绝",
        "权限"
    )
    return errorSignals.any { text.contains(it, ignoreCase = true) }
}


@OptIn(ExperimentalSharedTransitionApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    history: List<DownloadTask>,
    listState: LazyListState,
    hasMoreHistory: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    activeSharedTaskId: String?,
    playerActive: Boolean,
    playerTransitionPhase: PlayerTransitionPhase,
    onLoadMore: () -> Unit,
    onDeleteWithFile: (String) -> Unit,
    onOpen: (DownloadTask) -> Unit,
    onShare: (DownloadTask) -> Unit,
    isMultiSelectActive: Boolean = false,
    selectedTaskIds: Set<String> = emptySet(),
    onSelectToggle: (String) -> Unit = {},
    onEnterMultiSelect: (String) -> Unit = {},
    isFilterExpanded: Boolean = false,
    selectedFilterKeys: Set<String> = emptySet(),
    availableFilterChips: List<HistoryFilterChip> = emptyList(),
    onFilterKeySelected: (String?) -> Unit = {},
    onToggleSelectAll: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onCancelMultiSelect: () -> Unit = {}
) {
    var revealedDeleteTaskId by remember { mutableStateOf<String?>(null) }


    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf false
            lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 3
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && hasMoreHistory) {
            onLoadMore()
        }
    }

    val showTopControls = isFilterExpanded || isMultiSelectActive
    val animationDuration = if (isMultiSelectActive) 70 else 150
    val stickyHeaderHeight by animateDpAsState(
        targetValue = if (showTopControls) 48.dp else 0.dp,
        animationSpec = tween(durationMillis = animationDuration),
        label = "sticky-header-height"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 132.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            stickyHeader(key = "history-top-controls-header") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(stickyHeaderHeight)
                        .clipToBounds(),
                    color = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp
                ) {
                    if (stickyHeaderHeight > 0.dp) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            androidx.compose.animation.AnimatedContent(
                                targetState = if (isMultiSelectActive) 1 else if (isFilterExpanded) 2 else 0,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(animationDuration)) togetherWith fadeOut(animationSpec = tween(animationDuration))
                                },
                                label = "history-top-controls"
                            ) { state ->
                                when (state) {
                                    1 -> {
                                        val currentFilteredSize = history.size
                                        val isAllSelected = currentFilteredSize > 0 && selectedTaskIds.size == currentFilteredSize
                                        BatchOperationBar(
                                            selectedCount = selectedTaskIds.size,
                                            isAllSelected = isAllSelected,
                                            onToggleSelectAll = onToggleSelectAll,
                                            onDelete = onDeleteClick,
                                            onCancel = onCancelMultiSelect
                                        )
                                    }
                                    2 -> {
                                        PlatformChipRow(
                                            selectedFilterKeys = selectedFilterKeys,
                                            availableFilterChips = availableFilterChips,
                                            onFilterKeySelected = onFilterKeySelected
                                        )
                                    }
                                    else -> {
                                        Box(modifier = Modifier.fillMaxWidth().height(48.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }


            if (history.isEmpty()) {
                item(key = "media-library-empty") {
                    Box(modifier = Modifier.padding(horizontal = 14.dp)) {
                        MediaLibraryEmptyState()
                    }
                }
            } else {
                items(history, key = { it.id }) { task ->
                    HistoryRow(
                        modifier = Modifier
                            .padding(horizontal = 14.dp)
                            .animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null,
                                placementSpec = tween(durationMillis = animationDuration)
                            ),
                        task = task,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        activeSharedTaskId = activeSharedTaskId,
                        playerActive = playerActive,
                        playerTransitionPhase = playerTransitionPhase,
                        deleteRevealed = revealedDeleteTaskId == task.id,
                        onStartDeleteDrag = {
                            if (revealedDeleteTaskId != task.id) {
                                revealedDeleteTaskId = null
                            }
                        },
                        onRevealDelete = { revealedDeleteTaskId = task.id },
                        onCloseDelete = {
                            if (revealedDeleteTaskId == task.id) {
                                revealedDeleteTaskId = null
                            }
                        },
                        onDeleteWithFile = { onDeleteWithFile(task.id) },
                        onOpen = { onOpen(task) },
                        onShare = { onShare(task) },
                        allowDelete = !isMultiSelectActive,
                        thumbnailsEnabled = true,
                        isMultiSelectActive = isMultiSelectActive,
                        isSelected = selectedTaskIds.contains(task.id),
                        onSelectToggle = { onSelectToggle(task.id) },
                        onEnterMultiSelect = { onEnterMultiSelect(task.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun PlatformChipRow(
    selectedFilterKeys: Set<String>,
    availableFilterChips: List<HistoryFilterChip>,
    onFilterKeySelected: (String?) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(availableFilterChips) { chip ->
            PlatformChip(
                name = chip.displayName,
                iconRes = chip.iconRes,
                isSelected = chip.filterKey?.let { it in selectedFilterKeys } ?: selectedFilterKeys.isEmpty(),
                onClick = { onFilterKeySelected(chip.filterKey) }
            )
        }
    }
}

@Composable
fun PlatformChip(
    name: String,
    iconRes: Int?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) BrandGreen else MaterialTheme.colorScheme.surfaceContainer,
        label = "chip-bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
        label = "chip-text"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "chip-icon"
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = name,
                tint = iconTint,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
fun BatchOperationBar(
    selectedCount: Int,
    isAllSelected: Boolean,
    onToggleSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "取消",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "已选择 ${selectedCount} 项",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = onToggleSelectAll,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = if (isAllSelected) "取消全选" else "全选",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = BrandGreen
                )
            }
            
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = HistoryDeleteColor,
                    contentColor = Color.White,
                    disabledContainerColor = HistoryDeleteColor.copy(alpha = 0.38f),
                    disabledContentColor = Color.White.copy(alpha = 0.72f)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp),
                enabled = selectedCount > 0
            ) {
                Text(
                    text = "删除",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

@Composable
fun MediaLibraryEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 520.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.media_library_empty),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(0.48f)
                .aspectRatio(749f / 701f)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "媒体库还是空的",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "粘贴或分享链接，下载完成后会出现在这里",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HistoryRow(
    modifier: Modifier = Modifier,
    task: DownloadTask,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    activeSharedTaskId: String?,
    playerActive: Boolean,
    playerTransitionPhase: PlayerTransitionPhase,
    deleteRevealed: Boolean,
    onStartDeleteDrag: () -> Unit,
    onRevealDelete: () -> Unit,
    onCloseDelete: () -> Unit,
    onDeleteWithFile: () -> Unit = {},
    onOpen: () -> Unit,
    onShare: () -> Unit,
    allowDelete: Boolean,
    thumbnailsEnabled: Boolean = true,
    isMultiSelectActive: Boolean = false,
    isSelected: Boolean = false,
    onSelectToggle: () -> Unit = {},
    onEnterMultiSelect: () -> Unit = {}
) {
    val view = androidx.compose.ui.platform.LocalView.current
    val density = LocalDensity.current
    val deleteWidthPx = with(density) { HistoryDeleteRevealWidth.toPx() }
    val snapThresholdPx = deleteWidthPx * 0.5f
    var targetOffsetPx by remember { mutableStateOf(0f) }
    var dragDistancePx by remember { mutableStateOf(0f) }
    val animatedOffsetPx by animateFloatAsState(
        targetValue = targetOffsetPx,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = Spring.StiffnessHigh
        ),
        label = "history-row-offset"
    )
    val isSharedTarget = activeSharedTaskId == task.id
    val cardInteractionSource = remember { MutableInteractionSource() }
    val cardPressed by cardInteractionSource.collectIsPressedAsState()
    val sharedTargetHiddenInList = isSharedTarget && playerTransitionPhase == PlayerTransitionPhase.Open
    val sharedElementVisibleInList = playerTransitionPhase != PlayerTransitionPhase.Open
    val cardColor by animateColorAsState(
        targetValue = when {
            cardPressed -> BrandGreenSoft
            sharedTargetHiddenInList -> Color.Transparent
            isMultiSelectActive && isSelected -> BrandGreenSoft
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(durationMillis = 90),
        label = "history-card-pressed-color"
    )
    val cardShadow by animateDpAsState(
        targetValue = if (sharedTargetHiddenInList) 0.dp else 1.dp,
        animationSpec = if (sharedTargetHiddenInList) snap() else snap(delayMillis = 100),
        label = "history-card-shadow"
    )
    val context = LocalContext.current
    val sourceText = remember(task.sourceUrl, context) {
        historyMediaSource(task, AppSettings(context).storedRefererForUrl(task.sourceUrl))
    }

    LaunchedEffect(deleteRevealed, deleteWidthPx) {
        targetOffsetPx = if (deleteRevealed) -deleteWidthPx else 0f
        dragDistancePx = if (deleteRevealed) deleteWidthPx else 0f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(cardShadow, RoundedCornerShape(10.dp), clip = false)
            .clip(RoundedCornerShape(10.dp))
    ) {
        DeleteSwipeBackground(
            visible = allowDelete && targetOffsetPx < -0.5f,
            onDelete = {
                onCloseDelete()
                onDeleteWithFile()
            }
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .offset { IntOffset(animatedOffsetPx.roundToInt(), 0) }
                .then(
                    if (allowDelete) {
                        Modifier.pointerInput(deleteWidthPx) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    onStartDeleteDrag()
                                    dragDistancePx = -targetOffsetPx
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    dragDistancePx = (dragDistancePx - dragAmount)
                                        .coerceIn(0f, deleteWidthPx)
                                    val resistedOffset = if (dragDistancePx <= snapThresholdPx) {
                                        dragDistancePx * 0.36f
                                    } else {
                                        val switchBase = snapThresholdPx * 0.36f
                                        switchBase + (dragDistancePx - snapThresholdPx) * 1.42f
                                    }
                                    targetOffsetPx = -resistedOffset.coerceAtMost(deleteWidthPx)
                                },
                                onDragEnd = {
                                    val shouldOpen = dragDistancePx >= snapThresholdPx
                                    if (shouldOpen) {
                                        targetOffsetPx = -deleteWidthPx
                                        dragDistancePx = deleteWidthPx
                                        onRevealDelete()
                                    } else {
                                        targetOffsetPx = 0f
                                        dragDistancePx = 0f
                                        onCloseDelete()
                                    }
                                },
                                onDragCancel = {
                                    val shouldOpen = dragDistancePx >= snapThresholdPx
                                    if (shouldOpen) {
                                        targetOffsetPx = -deleteWidthPx
                                        dragDistancePx = deleteWidthPx
                                        onRevealDelete()
                                    } else {
                                        targetOffsetPx = 0f
                                        dragDistancePx = 0f
                                        onCloseDelete()
                                    }
                                }
                            )
                        }
                    } else {
                        Modifier
                    }
                )
                .clip(RoundedCornerShape(10.dp))
                .combinedClickable(
                    interactionSource = cardInteractionSource,
                    indication = LocalIndication.current,
                    enabled = task.status == DownloadStatus.Completed && task.filePath.isNotBlank(),
                    onLongClick = {
                        if (!isMultiSelectActive) {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            onEnterMultiSelect()
                        }
                    },
                    onClick = {
                        if (isMultiSelectActive) {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                            onSelectToggle()
                        } else if (targetOffsetPx < 0f) {
                            onCloseDelete()
                        } else {
                            onOpen()
                        }
                    }
                ),
            shape = RoundedCornerShape(10.dp),
            color = cardColor
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                AnimatedVisibility(
                    visible = isMultiSelectActive,
                    modifier = Modifier.align(Alignment.CenterVertically),
                    enter = expandHorizontally(expandFrom = Alignment.Start, animationSpec = tween(70)) + fadeIn(animationSpec = tween(70)),
                    exit = shrinkHorizontally(shrinkTowards = Alignment.Start, animationSpec = tween(70)) + fadeOut(animationSpec = tween(70))
                ) {
                    Box(
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .size(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val checkBgColor by animateColorAsState(
                            targetValue = if (isSelected) BrandGreen else Color.Transparent,
                            animationSpec = tween(60),
                            label = "check-bg"
                        )
                        val checkBorderColor by animateColorAsState(
                            targetValue = if (isSelected) BrandGreen else MaterialTheme.colorScheme.outline,
                            animationSpec = tween(60),
                            label = "check-border"
                        )
                        
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(2.dp, checkBorderColor, CircleShape)
                                .background(checkBgColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "已选择",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                ThumbnailPreview(
                    task = task,
                    loadEnabled = thumbnailsEnabled,
                    modifier = Modifier
                        .size(width = 131.dp, height = 92.dp)
                        .then(
                            if (isSharedTarget) {
                                with(sharedTransitionScope) {
                                    Modifier.sharedElementWithCallerManagedVisibility(
                                        sharedContentState = rememberSharedContentState("player-thumb-${task.id}"),
                                        visible = sharedElementVisibleInList,
                                        boundsTransform = BoundsTransform { _: Rect, _: Rect ->
                                            if (playerTransitionPhase == PlayerTransitionPhase.Exiting) {
                                                tween(durationMillis = 240)
                                            } else {
                                                spring(dampingRatio = 0.88f, stiffness = 650f)
                                            }
                                        }
                                    )
                                }
                            } else {
                                Modifier
                            }
                        ),
                    showLabels = !isSharedTarget || playerTransitionPhase == PlayerTransitionPhase.Idle
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleSmall.copy(lineHeight = 17.sp),
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = sourceText,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 13.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = historyTimeLabel(task.createdAt),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (!isMultiSelectActive && task.status == DownloadStatus.Completed && task.filePath.isNotBlank()) {
                        IconButton(
                            onClick = {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                onShare()
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Reply,
                                contentDescription = "分享",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .scale(scaleX = -1f, scaleY = 1f)
                                    .size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompactFileActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    contentDescription: String,
    mirrorIcon: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.82f else 1f,
        animationSpec = tween(durationMillis = 80),
        label = "compact-file-action-scale"
    )
    Surface(
        modifier = modifier
            .size(38.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = CircleShape,
        color = BrandBeigeSoft.copy(alpha = 0.72f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = BrandGreen,
                modifier = Modifier
                    .size(21.dp)
                    .then(if (mirrorIcon) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier)
            )
        }
    }
}

@Composable
fun BoxScope.DeleteSwipeBackground(
    visible: Boolean,
    onDelete: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(10.dp),
    color: Color = HistoryDeleteColor,
    tint: Color = Color.White
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .matchParentSize()
            .clip(shape)
            .background(Color.Transparent),
        contentAlignment = Alignment.CenterEnd
    ) {
        if (visible) {
            Surface(
                modifier = Modifier
                    .width(HistoryDeleteRevealWidth + HistoryDeleteOverlapWidth)
                    .fillMaxHeight()
                    .elasticPress(interactionSource)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onDelete
                ),
                color = color,
                shape = shape
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(HistoryDeleteRevealWidth),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, tint = tint)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "删除",
                            style = MaterialTheme.typography.labelMedium.copy(
                                lineHeight = 16.sp,
                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                    includeFontPadding = false
                                )
                            ),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

data class VideoMetadata(
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val durationLabel: String = ""
)

val videoMetadataCache = LruCache<String, VideoMetadata>(100)

fun loadVideoMetadata(context: Context, taskId: String, filePath: String): VideoMetadata {
    if (filePath.isBlank()) return VideoMetadata()
    val mediaFile = File(filePath)
    if (!mediaFile.exists()) return VideoMetadata()

    val fileLength = mediaFile.length()
    val fileLastModified = mediaFile.lastModified()
    val memoryKey = "$taskId|$filePath|$fileLength|$fileLastModified|meta"
    videoMetadataCache.get(memoryKey)?.let { return it }

    val cacheDir = File(context.filesDir, "thumbnails")
    if (!cacheDir.exists()) cacheDir.mkdirs()
    val metadataFile = File(cacheDir, "${taskId}_full_meta.json")

    if (metadataFile.exists()) {
        runCatching {
            val data = JSONObject(metadataFile.readText())
            if (
                data.optLong("fileLength", -1L) == fileLength &&
                data.optLong("fileLastModified", -1L) == fileLastModified
            ) {
                VideoMetadata(
                    videoWidth = data.optInt("videoWidth", 0),
                    videoHeight = data.optInt("videoHeight", 0),
                    durationLabel = data.optString("durationLabel")
                )
            } else {
                null
            }
        }.getOrNull()?.let {
            videoMetadataCache.put(memoryKey, it)
            return it
        }
    }

    return runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(filePath)
            val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val videoWidth = if (rotation == 90 || rotation == 270) rawHeight else rawWidth
            val videoHeight = if (rotation == 90 || rotation == 270) rawWidth else rawHeight
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val result = VideoMetadata(
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                durationLabel = durationMs.thumbnailDurationLabel()
            )
            runCatching {
                metadataFile.writeText(
                    JSONObject()
                        .put("videoWidth", result.videoWidth)
                        .put("videoHeight", result.videoHeight)
                        .put("durationLabel", result.durationLabel)
                        .put("fileLength", fileLength)
                        .put("fileLastModified", fileLastModified)
                        .toString()
                )
            }
            result
        }
    }.getOrDefault(VideoMetadata()).also {
        videoMetadataCache.put(memoryKey, it)
    }
}

@Composable
fun ThumbnailPreview(
    task: DownloadTask,
    modifier: Modifier = Modifier,
    loadEnabled: Boolean = true,
    showLabels: Boolean = true,
    contentScale: ContentScale = ContentScale.Crop,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(4.dp)
) {
    val context = LocalContext.current
    val shouldLoadMetadata = loadEnabled && showLabels
    val metadata by produceState(initialValue = VideoMetadata(), task.id, task.filePath, shouldLoadMetadata) {
        if (!shouldLoadMetadata) {
            value = VideoMetadata()
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            loadVideoMetadata(context, task.id, task.filePath)
        }
    }

    val color = if (!showLabels) {
        Color.Transparent
    } else {
        when (task.status) {
            DownloadStatus.Completed -> Color(0xFFE2EEE9)
            DownloadStatus.Queued -> Color(0xFFF6E6D1)
            DownloadStatus.Downloading -> Color(0xFFEAF1EE)
            DownloadStatus.Processing -> BrandBeigeSoft
            DownloadStatus.Failed -> BrandBeigeSoft
            DownloadStatus.Cancelled -> Color(0xFFE4E4E4)
        }
    }

    val containerModifier = modifier.clip(shape).background(color)
    val thumbnailModel = remember(context, loadEnabled, task.filePath) {
        if (loadEnabled && task.filePath.isNotBlank()) {
            ImageRequest.Builder(context)
                .data(File(task.filePath))
                .crossfade(false)
                .build()
        } else {
            null
        }
    }

    Box(
        modifier = containerModifier,
        contentAlignment = Alignment.Center
    ) {
        if (thumbnailModel != null) {
            coil.compose.AsyncImage(
                model = thumbnailModel,
                contentDescription = null,
                imageLoader = coil.Coil.imageLoader(context),
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                placeholder = painterResource(id = R.drawable.default_video_thumbnail),
                error = painterResource(id = R.drawable.default_video_thumbnail)
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.default_video_thumbnail),
                contentDescription = "默认视频封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (showLabels) {
            val qualityLabel = task.thumbnailQualityLabel(metadata.videoHeight)
            if (qualityLabel.isNotBlank()) {
                ThumbnailLabel(
                    text = qualityLabel,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(3.dp)
                )
            }
            if (metadata.durationLabel.isNotBlank()) {
                ThumbnailLabel(
                    text = metadata.durationLabel,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                )
            }
        }
    }
}

@Composable
fun ThumbnailLabel(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(3.dp),
        color = Color.Black.copy(alpha = 0.68f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, lineHeight = 10.sp),
            color = Color.White,
            maxLines = 1
        )
    }
}

fun DownloadTask.thumbnailQualityLabel(videoHeight: Int): String {
    val knownHeight = Regex("""(\d{3,4})\s*[pP]""")
        .find(formatLabel)
        ?.groupValues
        ?.getOrNull(1)
    return knownHeight?.let { "${it}P" }
        ?: videoHeight.takeIf { it > 0 }?.let { "${it}P" }
        .orEmpty()
}

fun Long.thumbnailDurationLabel(): String {
    if (this <= 0L) return ""
    val totalSeconds = this / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

@Composable
fun BatchDeleteConfirmDialog(
    selectedCount: Int,
    onConfirm: (deleteFiles: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var deleteFiles by remember { mutableStateOf(true) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "确认删除",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "确认要删除选中的 ${selectedCount} 项下载记录吗？",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { deleteFiles = !deleteFiles }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val checkBgColor by animateColorAsState(
                        targetValue = if (deleteFiles) BrandGreen else Color.Transparent,
                        label = "dialog-check-bg"
                    )
                    val checkBorderColor by animateColorAsState(
                        targetValue = if (deleteFiles) BrandGreen else MaterialTheme.colorScheme.outline,
                        label = "dialog-check-border"
                    )
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .border(2.dp, checkBorderColor, RoundedCornerShape(4.dp))
                            .background(checkBgColor, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (deleteFiles) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "同时删除本地视频文件",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(deleteFiles) },
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = HistoryDeleteColor
                )
            ) {
                Text("删除", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
