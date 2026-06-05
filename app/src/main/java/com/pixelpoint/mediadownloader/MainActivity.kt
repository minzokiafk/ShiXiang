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
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.InteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Forum
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
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
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
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
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
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
import java.util.UUID
import kotlin.math.roundToInt

val BrandGreen = Color(0xFF035248)
val BrandGreenPressed = Color(0xFF023E36)
val BrandGreenSoft = Color(0xFFEAF1EE)
val BrandGreenSurface = Color(0xFFF8F5EE)
val HeroSurface = Color(0xFFEEF4EF)
val CardSurface = Color(0xFFFFFFFF)
val InputSurface = Color(0xFFFFFAF2)
val BrandBeige = Color(0xFFEABB89)
val BrandBeigeSoft = Color(0xFFFFF2DF)
val LaunchWarmWhite = Color(0xFFFFF6EA)
val NavInactive = Color(0xFF626966)
val HistoryDeleteColor = Color(0xFFE5484D)
val HistoryDeleteRevealWidth = 88.dp
val HistoryDeleteOverlapWidth = 28.dp
const val BottomCapsuleWidthFraction = 0.77f
const val TabSwitchDamping = 0.74f

@Composable
fun brandButtonColors(interactionSource: InteractionSource): ButtonColors {
    val pressed by interactionSource.collectIsPressedAsState()
    return ButtonDefaults.buttonColors(
        containerColor = if (pressed) BrandGreenPressed else BrandGreen,
        contentColor = Color.White
    )
}

@Composable
fun Modifier.elasticPress(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.94f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    var visiblePressed by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (pressed) {
            visiblePressed = true
        } else if (visiblePressed) {
            delay(70)
            visiblePressed = false
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (visiblePressed) pressedScale else 1f,
        animationSpec = if (visiblePressed) {
            tween(durationMillis = 55)
        } else {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "elastic-press"
    )
    return scale(scale)
}

@Composable
fun Modifier.clickPulse(
    trigger: Int,
    pressedScale: Float = 0.94f
): Modifier {
    var pulsing by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        pulsing = true
        delay(80)
        pulsing = false
    }
    val scale by animateFloatAsState(
        targetValue = if (pulsing) pressedScale else 1f,
        animationSpec = if (pulsing) {
            tween(durationMillis = 45)
        } else {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "click-pulse"
    )
    return scale(scale)
}

class MainActivity : ComponentActivity() {
    private val viewModel: MediaDownloaderViewModel by viewModels()
    private var launchOverlayReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        val shouldPlayLaunchTransition = savedInstanceState == null
        if (shouldPlayLaunchTransition) {
            splashScreen.setKeepOnScreenCondition { !launchOverlayReady }
            splashScreen.setOnExitAnimationListener { splashViewProvider ->
                splashViewProvider.remove()
            }
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppLogger.init(applicationContext)
        AppLogger.event("activity", "onCreate", "intentAction" to intent.action)
        requestNotificationPermissionIfNeeded()
        viewModel.handleSharedIntent(intent)

        setContent {
            MediaDownloaderTheme {
                MediaDownloaderApp(
                    viewModel = viewModel,
                    showLaunchTransition = shouldPlayLaunchTransition,
                    onLaunchTransitionReady = { launchOverlayReady = true }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AppLogger.event("activity", "onNewIntent", "intentAction" to intent.action)
        viewModel.handleSharedIntent(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
    }
}

fun DownloadStatus.displayName(): String {
    return when (this) {
        DownloadStatus.Queued -> "等待中"
        DownloadStatus.Downloading -> "下载中"
        DownloadStatus.Processing -> "处理中"
        DownloadStatus.Completed -> "已完成"
        DownloadStatus.Failed -> "失败"
        DownloadStatus.Cancelled -> "已取消"
    }
}

fun DownloadStage.displayName(): String {
    return when (this) {
        DownloadStage.ResolvingFormats -> "识别中"
        DownloadStage.AwaitingFormatSelection -> "待选择"
        DownloadStage.AwaitingMediaCapture -> "待捕获"
        DownloadStage.Queued -> "等待中"
        DownloadStage.Downloading -> "下载中"
        DownloadStage.Validating -> "校验中"
        DownloadStage.Completed -> "已完成"
        DownloadStage.Failed -> "失败"
        DownloadStage.Cancelled -> "已取消"
    }
}

enum class AppTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    Home("首页", Icons.Rounded.Home, Icons.Outlined.HomeOutlined),
    History("媒体库", Icons.Rounded.History, Icons.Rounded.History),
    Settings("设置", Icons.Rounded.Settings, Icons.Outlined.SettingsOutlined)
}

enum class PlayerTransitionPhase {
    Idle,
    Entering,
    Open,
    Exiting
}

enum class PlayerTransitionSource {
    ActiveTask,
    History
}

enum class LinkPlatform(
    val displayName: String,
    val iconRes: Int
) {
    YouTube("YouTube", R.drawable.ic_platform_youtube),
    Instagram("Instagram", R.drawable.ic_platform_instagram),
    Threads("Threads", R.drawable.ic_platform_threads),
    X("X", R.drawable.ic_platform_x),
    Bilibili("Bilibili", R.drawable.ic_platform_bilibili),
    Douyin("抖音", R.drawable.ic_platform_tiktok),
    Weibo("微博", R.drawable.ic_platform_weibo),
    Xiaohongshu("小红书", R.drawable.ic_platform_xiaohongshu),
    Pornhub("Pornhub", R.drawable.ic_platform_pornhub);

    companion object {
        fun fromInput(input: String): LinkPlatform? {
            val url = UrlExtractor.bestMediaUrl(input) ?: input.trim()
            if (!UrlExtractor.isWebUrl(url)) return null
            val host = runCatching { Uri.parse(url).host.orEmpty().lowercase() }
                .getOrDefault("")
                .removePrefix("www.")
                .removePrefix("m.")
                .removePrefix("mobile.")
            return when {
                host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com") -> YouTube
                host == "instagram.com" || host.endsWith(".instagram.com") -> Instagram
                host == "threads.net" || host.endsWith(".threads.net") ||
                    host == "threads.com" || host.endsWith(".threads.com") -> Threads
                host == "x.com" || host.endsWith(".x.com") || host == "twitter.com" || host.endsWith(".twitter.com") -> X
                host == "bilibili.com" || host.endsWith(".bilibili.com") || host == "b23.tv" -> Bilibili
                host == "douyin.com" || host.endsWith(".douyin.com") || host == "iesdouyin.com" || host.endsWith(".iesdouyin.com") ||
                    host == "tiktok.com" || host.endsWith(".tiktok.com") -> Douyin
                host == "weibo.com" || host.endsWith(".weibo.com") -> Weibo
                host == "xiaohongshu.com" || host.endsWith(".xiaohongshu.com") || host == "xhslink.com" || host.endsWith(".xhslink.com") -> Xiaohongshu
                host == "pornhub.com" || host.endsWith(".pornhub.com") || host == "pornhub.org" || host.endsWith(".pornhub.org") -> Pornhub
                else -> null
            }
        }
    }
}

@Composable
fun MediaDownloaderTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = BrandGreen,
        onPrimary = Color.White,
        primaryContainer = BrandBeigeSoft,
        onPrimaryContainer = BrandGreen,
        secondary = BrandBeige,
        onSecondary = BrandGreen,
        secondaryContainer = Color(0xFFF6E6D1),
        onSecondaryContainer = BrandGreen,
        tertiary = BrandGreen,
        background = BrandGreenSurface,
        onBackground = Color(0xFF191C1B),
        surface = BrandGreenSurface,
        onSurface = Color(0xFF191C1B),
        surfaceContainer = CardSurface,
        surfaceContainerHigh = InputSurface,
        surfaceContainerHighest = Color(0xFFE6ECE8),
        outline = Color(0xFF6E7873),
        outlineVariant = Color(0xFFC8D0CB)
    )

    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, content = content)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MediaDownloaderApp(
    viewModel: MediaDownloaderViewModel,
    showLaunchTransition: Boolean = false,
    onLaunchTransitionReady: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val homeListState = rememberLazyListState()
    val historyListState = rememberLazyListState()
    val settingsListState = rememberLazyListState()
    val density = LocalDensity.current
    val brandCollapseRangePx = with(density) { 220.dp.toPx() }
    val homeBrandCollapsePx = remember { mutableFloatStateOf(0f) }
    val historyBrandCollapsePx = remember { mutableFloatStateOf(0f) }
    val settingsBrandCollapsePx = remember { mutableFloatStateOf(0f) }
    val activeBrandCollapsePx = when (state.selectedTab) {
        AppTab.Home -> homeBrandCollapsePx
        AppTab.History -> historyBrandCollapsePx
        AppTab.Settings -> settingsBrandCollapsePx
    }
    fun updateActiveBrandCollapse(value: Float) {
        val coerced = value.coerceIn(0f, brandCollapseRangePx)
        if (activeBrandCollapsePx.floatValue != coerced) {
            activeBrandCollapsePx.floatValue = coerced
        }
    }
    val brandNestedScrollConnection = remember(activeBrandCollapsePx, brandCollapseRangePx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val previous = activeBrandCollapsePx.floatValue
                val next = (previous - available.y).coerceIn(0f, brandCollapseRangePx)
                updateActiveBrandCollapse(next)
                return Offset.Zero
            }
        }
    }
    val noticeMessage = state.message
        ?.takeIf { it.shouldShowBottomNotice() }
        ?.toBottomNoticeText()
    var visibleNotice by remember { mutableStateOf<String?>(null) }
    var launchTransitionVisible by remember { mutableStateOf(showLaunchTransition) }
    var retainedPlayerTask by remember { mutableStateOf<DownloadTask?>(null) }
    var playerVisible by remember { mutableStateOf(false) }
    var playerTransitionSource by remember { mutableStateOf(PlayerTransitionSource.History) }
    var retainedPlayerTransitionSource by remember { mutableStateOf<PlayerTransitionSource?>(null) }
    val activeSharedTaskId = state.playerTask?.id ?: retainedPlayerTask?.id
    val activeTransitionSource = if (state.playerTask != null) {
        playerTransitionSource
    } else {
        retainedPlayerTransitionSource
    }
    val activeTaskSharedTaskId = activeSharedTaskId.takeIf {
        activeTransitionSource == PlayerTransitionSource.ActiveTask
    }
    val historySharedTaskId = activeSharedTaskId.takeIf {
        activeTransitionSource == PlayerTransitionSource.History
    }
    val playerTransitionPhase = when {
        state.playerTask != null && !playerVisible -> PlayerTransitionPhase.Entering
        playerVisible -> PlayerTransitionPhase.Open
        retainedPlayerTask != null -> PlayerTransitionPhase.Exiting
        else -> PlayerTransitionPhase.Idle
    }

    val currentPlayerTask = state.playerTask
    if (currentPlayerTask != null) {
        retainedPlayerTask = currentPlayerTask
        retainedPlayerTransitionSource = playerTransitionSource
    }

    val statePlayerTask = state.playerTask
    LaunchedEffect(statePlayerTask) {
        if (statePlayerTask != null) {
            delay(100)
            playerVisible = true
        } else {
            playerVisible = false
        }
    }

    LaunchedEffect(playerVisible) {
        if (!playerVisible) {
            delay(220)
            retainedPlayerTask = null
            retainedPlayerTransitionSource = null
        }
    }

    LaunchedEffect(noticeMessage) {
        if (noticeMessage.isNullOrBlank()) {
            visibleNotice = null
            return@LaunchedEffect
        }
        visibleNotice = noticeMessage
        if (noticeMessage != "正在识别清晰度") {
            delay(3000)
            if (visibleNotice == noticeMessage) {
                visibleNotice = null
            }
        }
    }

    val hazeState = remember { HazeState() }
    var isSettingsSubPageActive by remember { mutableStateOf(false) }
    val isSubPageActive = state.selectedTab == AppTab.Settings && isSettingsSubPageActive

    var isFilterExpanded by remember { mutableStateOf(false) }
    var selectedFilterKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isMultiSelectActive by remember { mutableStateOf(false) }
    var selectedTaskIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    val availableFilterKeys = remember(state.availableFilterChips) {
        state.availableFilterChips.mapNotNull { it.filterKey }.toSet()
    }

    androidx.activity.compose.BackHandler(enabled = isMultiSelectActive) {
        isMultiSelectActive = false
        selectedTaskIds = emptySet()
    }

    LaunchedEffect(state.selectedTab) {
        isMultiSelectActive = false
        selectedTaskIds = emptySet()
    }

    LaunchedEffect(availableFilterKeys) {
        val nextFilterKeys = selectedFilterKeys intersect availableFilterKeys
        if (nextFilterKeys != selectedFilterKeys) {
            selectedFilterKeys = nextFilterKeys
            viewModel.setHistoryFilterKeys(nextFilterKeys)
        }
    }

    SharedTransitionLayout {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            topBar = {
                if (!isSubPageActive) {
                    CollapsingBrandTopBar(
                        currentTab = state.selectedTab,
                        collapsePx = activeBrandCollapsePx,
                        collapseRangePx = brandCollapseRangePx,
                        isFilterExpanded = isFilterExpanded,
                        onFilterExpandedChange = { isFilterExpanded = it },
                        isFilterActive = selectedFilterKeys.isNotEmpty(),
                        isMultiSelectActive = isMultiSelectActive
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (isSubPageActive) Modifier else Modifier.nestedScroll(brandNestedScrollConnection))
                        .then(if (isSubPageActive) Modifier else Modifier.haze(hazeState))
                ) {
                    AnimatedVisibility(
                        visible = true,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val tabAnimatedVisibilityScope = this
                        val selectedTab = state.selectedTab
                        val selectedIndexFraction by animateFloatAsState(
                            targetValue = selectedTab.ordinal.toFloat(),
                            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                            label = "selected-tab-index"
                        )

                        Box(modifier = Modifier.fillMaxSize()) {
                            // Render Home screen
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(if (selectedTab == AppTab.Home) 1f else 0f)
                                    .graphicsLayer {
                                        val homeOffsetFraction = 0f - selectedIndexFraction
                                        translationX = homeOffsetFraction * size.width
                                        alpha = (1f - kotlin.math.abs(homeOffsetFraction)).coerceIn(0f, 1f)
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = innerPadding.calculateTopPadding())
                                        .fillMaxSize()
                                ) {
                                    DownloadHome(
                                        state = state,
                                        listState = homeListState,
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = tabAnimatedVisibilityScope,
                                        activeTaskSharedTaskId = activeTaskSharedTaskId,
                                        historySharedTaskId = historySharedTaskId,
                                        playerTransitionPhase = playerTransitionPhase,
                                        onInputChange = viewModel::updateInput,
                                        onDownload = viewModel::startPrototypeDownload,
                                        onCancel = viewModel::cancelActiveTask,
                                        onRetry = viewModel::retryTask,
                                        onOpenSource = viewModel::openSourceLink,
                                        onDeleteWithFile = viewModel::deleteHistoryItemAndFile,
                                        onOpenActiveTask = { task ->
                                            playerTransitionSource = PlayerTransitionSource.ActiveTask
                                            viewModel.openTask(task)
                                        },
                                        onOpenHistoryTask = { task ->
                                            playerTransitionSource = PlayerTransitionSource.History
                                            viewModel.openTask(task)
                                        },
                                        onShare = viewModel::openShareMenu
                                    )
                                }
                            }

                            // Render History screen
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(if (selectedTab == AppTab.History) 1f else 0f)
                                    .graphicsLayer {
                                        val historyOffsetFraction = 1f - selectedIndexFraction
                                        translationX = historyOffsetFraction * size.width
                                        alpha = (1f - kotlin.math.abs(historyOffsetFraction)).coerceIn(0f, 1f)
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = innerPadding.calculateTopPadding())
                                        .fillMaxSize()
                                ) {
                                    val currentFilteredSize = state.history.size
                                    HistoryScreen(
                                        history = state.history,
                                        listState = historyListState,
                                        hasMoreHistory = state.hasMoreHistory,
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = tabAnimatedVisibilityScope,
                                        activeSharedTaskId = historySharedTaskId,
                                        playerActive = state.playerTask != null,
                                        playerTransitionPhase = playerTransitionPhase,
                                        onLoadMore = viewModel::loadMoreHistory,
                                        onDeleteWithFile = viewModel::deleteHistoryItemAndFile,
                                        onOpen = { task ->
                                            playerTransitionSource = PlayerTransitionSource.History
                                            viewModel.openTask(task)
                                        },
                                        onShare = viewModel::openShareMenu,
                                        isMultiSelectActive = isMultiSelectActive,
                                        selectedTaskIds = selectedTaskIds,
                                        onSelectToggle = { id ->
                                            selectedTaskIds = if (id in selectedTaskIds) {
                                                selectedTaskIds - id
                                            } else {
                                                selectedTaskIds + id
                                            }
                                        },
                                        onEnterMultiSelect = { id ->
                                            isMultiSelectActive = true
                                            selectedTaskIds = setOf(id)
                                        },
                                        isFilterExpanded = isFilterExpanded,
                                        selectedFilterKeys = selectedFilterKeys,
                                        availableFilterChips = state.availableFilterChips,
                                        onFilterKeySelected = { key ->
                                            val nextFilterKeys = if (key == null) {
                                                emptySet()
                                            } else if (key in selectedFilterKeys) {
                                                selectedFilterKeys - key
                                            } else {
                                                selectedFilterKeys + key
                                            }
                                            selectedFilterKeys = nextFilterKeys
                                            viewModel.setHistoryFilterKeys(nextFilterKeys)
                                        },
                                        onToggleSelectAll = {
                                            selectedTaskIds = if (selectedTaskIds.size == currentFilteredSize) {
                                                emptySet()
                                            } else {
                                                state.history.map { it.id }.toSet()
                                            }
                                        },
                                        onDeleteClick = {
                                            showBatchDeleteConfirm = true
                                        },
                                        onCancelMultiSelect = {
                                            isMultiSelectActive = false
                                            selectedTaskIds = emptySet()
                                        }
                                    )
                                }
                            }

                            // Render Settings screen
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(if (selectedTab == AppTab.Settings) 1f else 0f)
                                    .graphicsLayer {
                                        val settingsOffsetFraction = 2f - selectedIndexFraction
                                        translationX = settingsOffsetFraction * size.width
                                        alpha = (1f - kotlin.math.abs(settingsOffsetFraction)).coerceIn(0f, 1f)
                                    }
                            ) {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    listState = settingsListState,
                                    onSubPageActiveChanged = { isSettingsSubPageActive = it }
                                )
                            }
                        }
                    }
                }

                BottomNotice(
                    message = visibleNotice,
                    hazeState = hazeState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = if (isSubPageActive) 16.dp else 98.dp)
                )

                if (showBatchDeleteConfirm) {
                    BatchDeleteConfirmDialog(
                        selectedCount = selectedTaskIds.size,
                        onConfirm = { deleteFiles ->
                            viewModel.deleteHistoryItemsBatch(selectedTaskIds, deleteFiles)
                            isMultiSelectActive = false
                            selectedTaskIds = emptySet()
                            showBatchDeleteConfirm = false
                        },
                        onDismiss = {
                            showBatchDeleteConfirm = false
                        }
                    )
                }

                if (!isSubPageActive) {
                    TelegramStyleBottomBar(
                        selectedTab = state.selectedTab,
                        onSelectTab = viewModel::selectTab,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        hazeState = hazeState
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = playerVisible,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.fillMaxSize()
        ) {
            retainedPlayerTask?.let { task ->
                InternalPlayerScreen(
                    task = task,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                    sharedElementVisible = playerVisible,
                    transitionPhase = playerTransitionPhase,
                    onBack = viewModel::closePlayer,
                    onShare = { viewModel.openShareMenu(task) },
                    onPlaybackProgress = viewModel::updatePlaybackProgress
                )
            }
        }

        val shareTask = state.shareTask
        AnimatedVisibility(
            visible = shareTask != null,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            shareTask?.let { task ->
                ShareBottomSheetOverlay(
                    task = task,
                    shareTargets = state.shareTargets,
                    isLoadingShareTargets = state.isLoadingShareTargets,
                    onDismiss = viewModel::dismissShareMenu,
                    onTarget = { target ->
                        viewModel.dismissShareMenu()
                        viewModel.shareToTarget(task, target)
                    },
                    onAction = { action ->
                        viewModel.dismissShareMenu()
                        when (action) {
                            ShareLocalAction.SaveToGallery -> viewModel.exportTask(task)
                            ShareLocalAction.SystemShare -> viewModel.shareTask(task)
                            ShareLocalAction.OpenExternally -> viewModel.openTaskExternally(task)
                        }
                    }
                )
            }
        }
    }

    if (state.formatOptions.isNotEmpty()) {
        FormatPickerDialog(
            title = state.pendingTitle.ifBlank { "选择清晰度" },
            thumbnailUrl = state.pendingThumbnailUrl,
            sourceName = UrlExtractor.hostLabel(state.pendingUrl),
            options = state.formatOptions,
            selected = state.selectedFormat,
            warning = state.formatResolveWarning,
            onSelect = viewModel::selectFormat,
            onDismiss = viewModel::dismissFormatPicker,
            onConfirm = viewModel::confirmSelectedFormat
        )
    }

    state.cookieTask?.let { task ->
        WebViewCookieDialog(
            task = task,
            onDismiss = viewModel::dismissCookieDialog,
            onConfirm = viewModel::saveWebViewCookieAndRetry
        )
    }

    if (launchTransitionVisible) {
        LaunchTransitionOverlay(
            onReady = onLaunchTransitionReady,
            onFinished = { launchTransitionVisible = false }
        )
    }
}

@Composable
private fun CollapsingBrandTopBar(
    currentTab: AppTab,
    collapsePx: MutableFloatState,
    collapseRangePx: Float,
    isFilterExpanded: Boolean,
    onFilterExpandedChange: (Boolean) -> Unit,
    isFilterActive: Boolean,
    isMultiSelectActive: Boolean
) {
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var rememberedStatusBarHeight by remember { mutableStateOf(0.dp) }
    if (statusBarPadding > 0.dp && rememberedStatusBarHeight == 0.dp) {
        rememberedStatusBarHeight = statusBarPadding
    }
    val topPadding = if (rememberedStatusBarHeight > 0.dp) rememberedStatusBarHeight else statusBarPadding
    val view = androidx.compose.ui.platform.LocalView.current

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topPadding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                contentAlignment = Alignment.Center
            ) {
                BrandLockup(
                    modifier = Modifier.graphicsLayer {
                        val progress = (collapsePx.floatValue / collapseRangePx).coerceIn(0f, 1f)
                        val scale = 1f - progress * 0.12f
                        scaleX = scale
                        scaleY = scale
                        translationX = -122.dp.toPx() * progress
                    }
                )

                if (currentTab == AppTab.History && !isMultiSelectActive) {
                    val buttonBgColor by animateColorAsState(
                        targetValue = when {
                            isFilterExpanded -> BrandGreenSoft
                            isFilterActive -> BrandGreenSoft.copy(alpha = 0.5f)
                            else -> Color.Transparent
                        },
                        label = "filter-btn-bg"
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(buttonBgColor)
                            .clickable {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                onFilterExpandedChange(!isFilterExpanded)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FilterList,
                            contentDescription = "筛选",
                            tint = if (isFilterExpanded || isFilterActive) BrandGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrandLockup(
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    renderedScale: Float = 1f,
    showText: Boolean = true,
    animateTextReveal: Boolean = false
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(width = 38.dp * renderedScale, height = 33.dp * renderedScale),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.elephant_logo_home),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        AnimatedVisibility(
            visible = showText,
            enter = if (animateTextReveal) {
                expandHorizontally(
                    expandFrom = Alignment.Start,
                    animationSpec = tween(durationMillis = 300)
                ) + fadeIn(tween(durationMillis = 220))
            } else {
                fadeIn(tween(durationMillis = 0))
            }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(7.dp * renderedScale))
                Text(
                    text = "拾象",
                    color = textColor,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = MaterialTheme.typography.titleLarge.fontSize * renderedScale,
                        lineHeight = MaterialTheme.typography.titleLarge.lineHeight * renderedScale,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Black,
                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                            includeFontPadding = false
                        )
                    )
                )
            }
        }
    }
}

@Composable
@Suppress("DEPRECATION")
private fun LaunchTransitionOverlay(
    onReady: () -> Unit,
    onFinished: () -> Unit
) {
    var textVisible by remember { mutableStateOf(false) }
    var moveToHeader by remember { mutableStateOf(false) }
    var revealPage by remember { mutableStateOf(false) }
    val inputBlocker = remember { MutableInteractionSource() }
    val activity = LocalContext.current as? Activity

    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previousStatusBarColor = window?.statusBarColor
        val previousNavigationBarColor = window?.navigationBarColor
        val previousLightStatusBars = controller?.isAppearanceLightStatusBars
        val previousLightNavigationBars = controller?.isAppearanceLightNavigationBars
        window?.statusBarColor = BrandGreen.toArgb()
        window?.navigationBarColor = BrandGreen.toArgb()
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            previousStatusBarColor?.let { window.statusBarColor = it }
            previousNavigationBarColor?.let { window.navigationBarColor = it }
            previousLightStatusBars?.let { controller.isAppearanceLightStatusBars = it }
            previousLightNavigationBars?.let { controller.isAppearanceLightNavigationBars = it }
        }
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        onReady()
        delay(180)
        textVisible = true
        delay(600)
        moveToHeader = true
        delay(390)
        revealPage = true
        delay(170)
        onFinished()
    }

    val backgroundAlpha by animateFloatAsState(
        targetValue = if (revealPage) 0f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "launch-background-alpha"
    )
    val centralScale = 2.05f
    val displayScale by animateFloatAsState(
        targetValue = if (moveToHeader) 1f else centralScale,
        animationSpec = tween(
            durationMillis = 390,
            easing = FastOutSlowInEasing
        ),
        label = "launch-brand-size"
    )
    val positionProgress by animateFloatAsState(
        targetValue = if (moveToHeader) 1f else 0f,
        animationSpec = tween(durationMillis = 390, easing = FastOutSlowInEasing),
        label = "launch-brand-position"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = inputBlocker,
                indication = null,
                onClick = {}
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BrandGreen.copy(alpha = backgroundAlpha))
        )
        val density = LocalDensity.current
        val headerCenterY = with(density) {
            WindowInsets.statusBars.getTop(this).toDp() + 20.dp
        }
        val targetY = with(density) { (headerCenterY - maxHeight / 2).toPx() }
        BrandLockup(
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    alpha = backgroundAlpha
                    val scale = displayScale / centralScale
                    scaleX = scale
                    scaleY = scale
                    translationY = targetY * positionProgress
                },
            textColor = LaunchWarmWhite,
            renderedScale = centralScale,
            showText = textVisible,
            animateTextReveal = true
        )
    }
}

@Composable
private fun BottomNotice(
    message: String?,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    var lastNonNullMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) {
            lastNonNullMessage = message
        }
    }
    val displayMessage = message ?: lastNonNullMessage

    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = slideInVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) { height -> height / 2 } + fadeIn(tween(90)),
        exit = slideOutVertically(tween(140)) { height -> height / 2 } + fadeOut(tween(120)),
        modifier = modifier
    ) {
        if (!displayMessage.isNullOrBlank()) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                val maxCapsuleWidth = maxWidth * BottomCapsuleWidthFraction

                Row(
                    modifier = Modifier
                        .widthIn(max = maxCapsuleWidth)
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(30.dp),
                            clip = false,
                            ambientColor = Color.Black.copy(alpha = 0.03f),
                            spotColor = Color.Black.copy(alpha = 0.08f)
                        )
                        .hazeChild(
                            state = hazeState,
                            shape = RoundedCornerShape(30.dp),
                            style = HazeDefaults.style(
                                tint = Color.White.copy(alpha = 0.82f),
                                blurRadius = 20.dp,
                                noiseFactor = 0.02f
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = Color.Black.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(30.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (displayMessage == "正在识别清晰度") {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = BrandGreen,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (displayMessage.isErrorNotice()) Icons.Rounded.Error else Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = if (displayMessage.isErrorNotice()) MaterialTheme.colorScheme.error else BrandGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = displayMessage,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 22.sp,
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                includeFontPadding = false
                            )
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun TelegramStyleBottomBar(
    selectedTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState
) {
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var rememberedNavBarHeight by remember { mutableStateOf(0.dp) }
    if (navBarPadding > 0.dp && rememberedNavBarHeight == 0.dp) {
        rememberedNavBarHeight = navBarPadding
    }
    val bottomPadding = if (rememberedNavBarHeight > 0.dp) rememberedNavBarHeight else navBarPadding

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(bottom = bottomPadding)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(BottomCapsuleWidthFraction)
                .height(60.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(30.dp),
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.03f),
                    spotColor = Color.Black.copy(alpha = 0.08f)
                )
                .hazeChild(
                    state = hazeState,
                    shape = RoundedCornerShape(30.dp),
                    style = HazeDefaults.style(
                        tint = Color.White.copy(alpha = 0.82f),
                        blurRadius = 20.dp,
                        noiseFactor = 0.02f
                    )
                )
                .border(
                    width = 1.dp,
                    color = Color.Black.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(30.dp)
                )
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 7.dp, vertical = 7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(23.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.12f),
                                    Color.White.copy(alpha = 0.04f),
                                    BrandGreenSoft.copy(alpha = 0.04f)
                                )
                            )
                        )
                )
                val spacing = 3.dp
                val itemWidth = (maxWidth - spacing * (AppTab.entries.size - 1)) / AppTab.entries.size
                val indicatorOffsetX by animateFloatAsState(
                    targetValue = selectedTab.ordinal.toFloat(),
                    animationSpec = tween(durationMillis = 180),
                    label = "bottom-tab-indicator"
                )

                Surface(
                    modifier = Modifier
                        .offset(x = (itemWidth + spacing) * indicatorOffsetX)
                        .width(itemWidth)
                        .height(46.dp)
                        .shadow(
                            elevation = 3.dp,
                            shape = RoundedCornerShape(23.dp),
                            clip = false,
                            ambientColor = Color.Black.copy(alpha = 0.04f),
                            spotColor = Color.Black.copy(alpha = 0.06f)
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(23.dp)
                        ),
                    shape = RoundedCornerShape(23.dp),
                    color = BrandBeigeSoft.copy(alpha = 0.90f)
                ) {}

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppTab.entries.forEach { tab ->
                        TelegramStyleBottomBarItem(
                            tab = tab,
                            selected = selectedTab == tab,
                            onClick = { onSelectTab(tab) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelegramStyleBottomBarItem(
    tab: AppTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) BrandGreen else NavInactive,
        animationSpec = tween(180),
        label = "tab-content-color"
    )
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .height(46.dp)
            .elasticPress(interactionSource)
            .clip(RoundedCornerShape(23.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(23.dp),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (tab == AppTab.History) {
                Icon(
                    painter = painterResource(
                        if (selected) R.drawable.ic_nav_animated_images_filled
                        else R.drawable.ic_nav_animated_images_outlined
                    ),
                    contentDescription = tab.label,
                    tint = contentColor,
                    modifier = Modifier.size(21.dp)
                )
            } else {
                Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = tab.label,
                    tint = contentColor,
                    modifier = Modifier.size(21.dp)
                )
            }
            Spacer(Modifier.height(1.dp))
            Text(
                text = tab.label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 14.sp),
                color = contentColor,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

enum class ShareLocalAction {
    SaveToGallery,
    SystemShare,
    OpenExternally
}

@Composable
fun ShareBottomSheetOverlay(
    task: DownloadTask,
    shareTargets: List<ShareTarget>,
    isLoadingShareTargets: Boolean,
    onDismiss: () -> Unit,
    onTarget: (ShareTarget) -> Unit,
    onAction: (ShareLocalAction) -> Unit
) {
    var animateSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val appSettings = remember(context) { AppSettings(context) }
    LaunchedEffect(Unit) {
        animateSheet = true
    }
    var sheetHeightPx by remember { mutableStateOf(0f) }
    var sheetTargetOffsetPx by remember { mutableStateOf(0f) }
    var sheetDragDistancePx by remember { mutableStateOf(0f) }
    val sheetOffsetPx by animateFloatAsState(
        targetValue = sheetTargetOffsetPx,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = Spring.StiffnessHigh
        ),
        label = "share-sheet-drag-offset"
    )

    val executeDismiss = {
        animateSheet = false
    }
    val settleSheetDrag = {
        val shouldDismiss = sheetHeightPx > 0f && sheetDragDistancePx >= sheetHeightPx * 0.3f
        if (shouldDismiss) {
            executeDismiss()
        } else {
            sheetTargetOffsetPx = 0f
            sheetDragDistancePx = 0f
        }
    }

    androidx.activity.compose.BackHandler(enabled = animateSheet, onBack = executeDismiss)

    LaunchedEffect(animateSheet) {
        if (animateSheet) {
            sheetTargetOffsetPx = 0f
            sheetDragDistancePx = 0f
        }
        if (!animateSheet) {
            kotlinx.coroutines.delay(200)
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {},
        contentAlignment = Alignment.BottomCenter
    ) {
        val scrimAlpha by animateFloatAsState(
            targetValue = if (animateSheet) 0.4f else 0f,
            animationSpec = tween(durationMillis = 200),
            label = "scrim-alpha"
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = executeDismiss
                )
        )

        AnimatedVisibility(
            visible = animateSheet,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 200)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 14.dp, end = 14.dp, bottom = 8.dp)
                    .onSizeChanged { sheetHeightPx = it.height.toFloat() }
                    .offset { IntOffset(0, sheetOffsetPx.roundToInt()) }
                    .clickable(enabled = false, onClick = {})
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(sheetHeightPx) {
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        sheetDragDistancePx = sheetTargetOffsetPx
                                    },
                                    onVerticalDrag = { _, dragAmount ->
                                        val maxDistance = sheetHeightPx.coerceAtLeast(1f)
                                        sheetDragDistancePx = (sheetDragDistancePx + dragAmount)
                                            .coerceIn(0f, maxDistance)
                                        sheetTargetOffsetPx = sheetDragDistancePx
                                    },
                                    onDragEnd = settleSheetDrag,
                                    onDragCancel = settleSheetDrag
                                )
                            },
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(width = 36.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(1.dp, RoundedCornerShape(12.dp), clip = false)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 80.dp, height = 60.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                ) {
                                    ThumbnailPreview(
                                        task = task,
                                        contentScale = ContentScale.Crop,
                                        showLabels = false,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(60.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = task.title,
                                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        minLines = 2,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val platform = remember(task.sourceUrl) {
                                        platformOrSourceName(
                                            task.sourceUrl,
                                            appSettings.storedRefererForUrl(task.sourceUrl)
                                        )
                                    }
                                    val sizeLabel = remember(task.totalBytes) {
                                        if (task.totalBytes > 0) task.totalBytes.toReadableSize() else "未知大小"
                                    }
                                    Text(
                                        text = "$platform · $sizeLabel",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(96.dp)
                    ) {
                        Text(
                            text = "分享到应用",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (shareTargets.isNotEmpty()) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp)
                            ) {
                                items(shareTargets) { target ->
                                    ShareGridItem(
                                        name = target.label,
                                        iconPainter = painterResource(id = target.iconRes),
                                        fullColorIcon = true,
                                        onClick = {
                                            executeDismiss()
                                            onTarget(target)
                                        }
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (isLoadingShareTargets) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "正在加载分享应用",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "未检测到支持的分享应用",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "系统与本地操作",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ShareGridItem(
                                name = "保存到相册",
                                iconPainter = painterResource(id = R.drawable.ic_google_photo_prints),
                                onClick = {
                                    executeDismiss()
                                    onAction(ShareLocalAction.SaveToGallery)
                                }
                            )
                            ShareGridItem(
                                name = "系统分享",
                                iconPainter = painterResource(id = R.drawable.ic_google_share_windows),
                                onClick = {
                                    executeDismiss()
                                    onAction(ShareLocalAction.SystemShare)
                                }
                            )
                            ShareGridItem(
                                name = "外部打开",
                                iconPainter = painterResource(id = R.drawable.ic_google_sound_sampler),
                                onClick = {
                                    executeDismiss()
                                    onAction(ShareLocalAction.OpenExternally)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareGridItem(
    name: String,
    iconVector: ImageVector? = null,
    iconPainter: androidx.compose.ui.graphics.painter.Painter? = null,
    iconBitmap: ImageBitmap? = null,
    fullColorIcon: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .width(60.dp)
            .elasticPress(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        val iconContainerColor = if (iconBitmap != null || fullColorIcon) {
            Color.White
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
        Surface(
            shape = CircleShape,
            color = iconContainerColor,
            modifier = Modifier
                .size(44.dp)
                .shadow(1.dp, CircleShape, clip = false)
                .clip(CircleShape)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                if (iconVector != null) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = name,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                } else if (iconBitmap != null) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                    ) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else if (iconPainter != null && fullColorIcon) {
                    Image(
                        painter = iconPainter,
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else if (iconPainter != null) {
                    Icon(
                        painter = iconPainter,
                        contentDescription = name,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 13.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
