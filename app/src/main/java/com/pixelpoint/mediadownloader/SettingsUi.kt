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
import android.view.View
import android.view.HapticFeedbackConstants
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Switch
import androidx.compose.material3.HorizontalDivider
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.platform.LocalView
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.pixelpoint.mediadownloader.engine.DownloadFormatOption
import com.pixelpoint.mediadownloader.engine.LocalDownloadEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
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
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: MediaDownloaderViewModel,
    listState: LazyListState,
    onSubPageActiveChanged: (Boolean) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appSettings = remember(context) { AppSettings(context) }
    var activePage by remember { mutableStateOf(SettingsSubPage.Main) }
    var loginStates by remember { mutableStateOf(appSettings.savedLoginStates()) }
    var pendingLoginTask by remember { mutableStateOf<DownloadTask?>(null) }
    fun refreshLoginStates() {
        loginStates = appSettings.savedLoginStates()
    }

    val websiteLoginLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val sourceUrl = data?.getStringExtra(WebsiteLoginActivity.EXTRA_SOURCE_URL)
                ?: pendingLoginTask?.sourceUrl.orEmpty()
            val cookie = data?.getStringExtra(WebsiteLoginActivity.EXTRA_COOKIE).orEmpty().trim()
            if (sourceUrl.isNotBlank() && cookie.isNotBlank()) {
                appSettings.setCookieForUrl(sourceUrl, cookie)
                appSettings.markLoginStateVisibleForUrl(sourceUrl)
                refreshLoginStates()
            }
        }
        pendingLoginTask = null
    }

    var customStoragePath by remember { mutableStateOf(appSettings.customStoragePath) }
    val documentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, flags)
                val path = getFriendlyPathFromUri(uri)
                appSettings.customStorageUri = uri.toString()
                appSettings.customStoragePath = path
                customStoragePath = path
                viewModel.updateDefaultStorageLocation(StorageLocation.Custom)
            }.onFailure { error ->
                AppLogger.error("settings", "takePersistablePermissionFailed", error, "uri" to uri.toString())
            }
        }
    }

    fun startWebsiteLogin(url: String) {
        val task = websiteLoginTask(url)
        pendingLoginTask = task
        websiteLoginLauncher.launch(WebsiteLoginActivity.intent(context, task.sourceUrl, task.title))
    }

    LaunchedEffect(activePage) {
        onSubPageActiveChanged(activePage != SettingsSubPage.Main)
    }

    BackHandler(enabled = activePage != SettingsSubPage.Main) {
        activePage = SettingsSubPage.Main
    }

    AnimatedContent(
        targetState = activePage,
        transitionSpec = {
            if (targetState != SettingsSubPage.Main) {
                (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { width -> width } togetherWith
                    slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { width -> -width / 5 } + fadeOut(tween(300)))
                    .apply { targetContentZIndex = 1f }
            } else {
                (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { width -> -width / 5 } + fadeIn(tween(300)) togetherWith
                    slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { width -> width })
                    .apply { targetContentZIndex = -1f }
            }
        },
        label = "settings-sub-page",
        modifier = Modifier.fillMaxSize()
    ) { page ->
        when (page) {
            SettingsSubPage.Main -> SettingsMainPage(
                state = state,
                viewModel = viewModel,
                loginStateCount = loginStates.size,
                listState = listState,
                onStorageClick = { activePage = SettingsSubPage.Storage },
                onFormatPreferenceClick = { activePage = SettingsSubPage.FormatPreference },
                onNetworkPreferenceClick = { activePage = SettingsSubPage.NetworkPreference },
                onSupportedSitesClick = { activePage = SettingsSubPage.SupportedSites },
                onWebsiteLoginsClick = {
                    refreshLoginStates()
                    activePage = SettingsSubPage.WebsiteLogins
                }
            )
            SettingsSubPage.Storage -> StorageLocationPage(
                selected = state.defaultStorageLocation,
                customPath = customStoragePath,
                onSelect = { location ->
                    viewModel.updateDefaultStorageLocation(location)
                },
                onPickCustomFolder = {
                    documentTreeLauncher.launch(null)
                },
                onBack = { activePage = SettingsSubPage.Main }
            )
            SettingsSubPage.FormatPreference -> FormatPreferencePage(
                state = state,
                viewModel = viewModel,
                onBack = { activePage = SettingsSubPage.Main }
            )
            SettingsSubPage.NetworkPreference -> NetworkPreferencePage(
                state = state,
                viewModel = viewModel,
                onBack = { activePage = SettingsSubPage.Main }
            )
            SettingsSubPage.SupportedSites -> SupportedSitesPage(
                onBack = { activePage = SettingsSubPage.Main }
            )
            SettingsSubPage.WebsiteLogins -> WebsiteLoginStatesPage(
                loginStates = loginStates,
                onStartLogin = ::startWebsiteLogin,
                onDelete = { state ->
                    appSettings.deleteCookieForHost(state.host)
                    refreshLoginStates()
                },
                onBack = { activePage = SettingsSubPage.Main }
            )
        }
    }
}

enum class SettingsSubPage {
    Main,
    Storage,
    SupportedSites,
    WebsiteLogins,
    FormatPreference,
    NetworkPreference
}

private val SettingsPageHorizontalPadding = 16.dp

@Composable
fun SettingsGroupCard(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
            )
        }
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun GoogleSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailingText: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val modifier = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailingText != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = trailingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailingContent()
        }
    }
}

@Composable
fun SettingsMainPage(
    state: MediaDownloaderUiState,
    viewModel: MediaDownloaderViewModel,
    loginStateCount: Int,
    listState: LazyListState,
    onStorageClick: () -> Unit,
    onFormatPreferenceClick: () -> Unit,
    onNetworkPreferenceClick: () -> Unit,
    onSupportedSitesClick: () -> Unit,
    onWebsiteLoginsClick: () -> Unit
) {
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBarPadding = statusBarPadding + 40.dp + 20.dp
    val context = LocalContext.current
    
    val lifecycleOwner = LocalLifecycleOwner.current
    var isBatteryIgnoring by remember {
        mutableStateOf(checkBatteryIgnoring(context))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isBatteryIgnoring = checkBatteryIgnoring(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SettingsPageHorizontalPadding,
            top = topBarPadding,
            end = SettingsPageHorizontalPadding,
            bottom = 132.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsGroupCard(title = "下载偏好") {
                GoogleSettingsRow(
                    icon = Icons.Rounded.FolderOpen,
                    title = "默认存储位置",
                    subtitle = state.defaultStorageLocation.label,
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = onStorageClick
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 54.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 0.5.dp
                )
                GoogleSettingsRow(
                    icon = Icons.Rounded.Tune,
                    title = "格式与质量偏好",
                    subtitle = "视频质量: ${videoQualityLabel(state.videoQuality)}，音频质量: ${audioQualityLabel(state.audioQuality)}",
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = onFormatPreferenceClick
                )
            }
        }

        item {
            SettingsGroupCard(title = "网络与系统") {
                GoogleSettingsRow(
                    icon = Icons.Rounded.SignalCellularAlt,
                    title = "网络与限速",
                    subtitle = if (state.rateLimitEnabled) "已限速 ${state.rateLimitValue} KB/s" else "未限速，移动数据: ${if (state.cellularDownload) "允许" else "禁用"}",
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = onNetworkPreferenceClick
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 54.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 0.5.dp
                )
                GoogleSettingsRow(
                    icon = Icons.Rounded.BatteryChargingFull,
                    title = "后台下载设置",
                    subtitle = if (isBatteryIgnoring) "已免除电池优化，支持稳定后台下载" else "未免除电池优化，后台下载可能被系统中断",
                    trailingText = if (isBatteryIgnoring) "已允许" else "去设置",
                    onClick = {
                        if (isBatteryIgnoring) {
                            Toast.makeText(context, "已免除电池优化", Toast.LENGTH_SHORT).show()
                        } else {
                            try {
                                val intent = Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val intent = Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                } catch (ex: Exception) {
                                    Toast.makeText(context, "无法打开系统电池设置，请手动开启后台白名单", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 54.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 0.5.dp
                )
                GoogleSettingsRow(
                    icon = Icons.Rounded.Lock,
                    title = "网站登录状态",
                    subtitle = if (loginStateCount == 0) "新增、删除或重新登录网站账号" else "已保存 $loginStateCount 个网站登录状态",
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = onWebsiteLoginsClick
                )
            }
        }

        item {
            SettingsGroupCard(title = "信息与更新") {
                GoogleSettingsRow(
                    icon = Icons.Rounded.Info,
                    title = "支持的平台",
                    subtitle = "查看已支持的视频与媒体平台",
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = onSupportedSitesClick
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 54.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 0.5.dp
                )
                val isUpdating = state.ytDlpUpdateStatus == YtDlpUpdateStatus.Checking ||
                        state.ytDlpUpdateStatus == YtDlpUpdateStatus.Downloading ||
                        state.ytDlpUpdateStatus == YtDlpUpdateStatus.Extracting
                val versionStr = if (state.ytDlpVersion.isNotBlank()) " (当前版本: ${state.ytDlpVersion})" else ""
                val updateSubtitle = when (state.ytDlpUpdateStatus) {
                    YtDlpUpdateStatus.Idle -> "检查最新 yt-dlp 引擎版本$versionStr"
                    YtDlpUpdateStatus.Checking -> "正在检查更新..."
                    YtDlpUpdateStatus.Downloading -> "正在下载更新: ${state.ytDlpUpdateProgress}%"
                    YtDlpUpdateStatus.Extracting -> "正在解压并应用更新..."
                    YtDlpUpdateStatus.Success -> "更新成功！重启应用后生效$versionStr"
                    YtDlpUpdateStatus.Error -> "更新失败: ${state.ytDlpUpdateMessage}"
                }
                GoogleSettingsRow(
                    icon = Icons.Rounded.SystemUpdate,
                    title = "手动更新 yt-dlp",
                    subtitle = updateSubtitle,
                    trailingContent = {
                        if (isUpdating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "检查更新",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    onClick = if (isUpdating) null else {
                        { viewModel.startYtDlpUpdate() }
                    }
                )
            }
        }
        settingsFooterSpacer()
    }
}

@Composable
fun FormatPreferencePage(
    state: MediaDownloaderUiState,
    viewModel: MediaDownloaderViewModel,
    onBack: () -> Unit
) {
    var activeDialog by remember { mutableStateOf<FormatDialogType?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = SettingsPageHorizontalPadding,
            end = SettingsPageHorizontalPadding,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsPageHeader(
                title = "格式与质量偏好",
                subtitle = "设置默认下载音视频格式和清晰度",
                onBack = onBack
            )
        }
        item {
            SettingsGroupCard(title = "视频偏好") {
                GoogleSettingsRow(
                    icon = Icons.Rounded.Tune,
                    title = "视频质量偏好",
                    subtitle = videoQualityLabel(state.videoQuality),
                    onClick = { activeDialog = FormatDialogType.VideoQuality }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 54.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 0.5.dp
                )
                GoogleSettingsRow(
                    icon = Icons.Rounded.Tune,
                    title = "视频格式偏好",
                    subtitle = videoFormatLabel(state.videoFormat),
                    onClick = { activeDialog = FormatDialogType.VideoFormat }
                )
            }
        }
        item {
            SettingsGroupCard(title = "音频偏好") {
                GoogleSettingsRow(
                    icon = Icons.Rounded.Tune,
                    title = "音频格式偏好",
                    subtitle = audioFormatLabel(state.audioFormatPreferred),
                    onClick = { activeDialog = FormatDialogType.AudioFormat }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 54.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 0.5.dp
                )
                GoogleSettingsRow(
                    icon = Icons.Rounded.Tune,
                    title = "音频质量偏好",
                    subtitle = audioQualityLabel(state.audioQuality),
                    onClick = { activeDialog = FormatDialogType.AudioQuality }
                )
            }
        }
    }

    when (activeDialog) {
        FormatDialogType.VideoQuality -> OptionSelectDialog(
            title = "选择视频质量偏好",
            options = listOf(
                0 to "最佳",
                2160 to "4K (2160p)",
                1440 to "2K (1440p)",
                1080 to "1080p",
                720 to "720p",
                480 to "480p",
                360 to "360p",
                1 to "最低"
            ),
            selected = state.videoQuality,
            onSelect = viewModel::updateVideoQuality,
            onDismiss = { activeDialog = null }
        )
        FormatDialogType.VideoFormat -> OptionSelectDialog(
            title = "选择视频格式偏好",
            options = listOf(
                1 to "兼容优先 (MP4 / H.264)",
                0 to "画质优先 (VP9 / AV1)"
            ),
            selected = state.videoFormat,
            onSelect = viewModel::updateVideoFormat,
            onDismiss = { activeDialog = null }
        )
        FormatDialogType.AudioFormat -> OptionSelectDialog(
            title = "选择音频格式偏好",
            options = listOf(
                0 to "最佳",
                1 to "M4A (AAC)",
                2 to "OPUS"
            ),
            selected = state.audioFormatPreferred,
            onSelect = viewModel::updateAudioFormatPreferred,
            onDismiss = { activeDialog = null }
        )
        FormatDialogType.AudioQuality -> OptionSelectDialog(
            title = "选择音频质量偏好",
            options = listOf(
                0 to "最佳",
                192 to "高 (192 Kbps)",
                128 to "中 (128 Kbps)",
                64 to "低 (64 Kbps)"
            ),
            selected = state.audioQuality,
            onSelect = viewModel::updateAudioQuality,
            onDismiss = { activeDialog = null }
        )
        null -> {}
    }
}

enum class FormatDialogType {
    VideoQuality, VideoFormat, AudioFormat, AudioQuality
}

@Composable
fun NetworkPreferencePage(
    state: MediaDownloaderUiState,
    viewModel: MediaDownloaderViewModel,
    onBack: () -> Unit
) {
    var showRateLimitDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = SettingsPageHorizontalPadding,
            end = SettingsPageHorizontalPadding,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsPageHeader(
                title = "网络与限速设置",
                subtitle = "管理允许下载的网络状态与下载限速",
                onBack = onBack
            )
        }
        item {
            SettingsGroupCard(title = "移动网络偏好") {
                GoogleSettingsRow(
                    icon = Icons.Rounded.SignalCellularAlt,
                    title = "允许使用移动数据下载",
                    subtitle = "关闭后仅在连接 Wi-Fi 时执行下载",
                    trailingContent = {
                        Switch(
                            checked = state.cellularDownload,
                            onCheckedChange = viewModel::updateCellularDownload
                        )
                    }
                )
            }
        }
        item {
            SettingsGroupCard(title = "限速设置") {
                GoogleSettingsRow(
                    icon = Icons.Rounded.Speed,
                    title = "限速下载",
                    subtitle = "启用以限制单任务最大下载速率",
                    trailingContent = {
                        Switch(
                            checked = state.rateLimitEnabled,
                            onCheckedChange = viewModel::updateRateLimitEnabled
                        )
                    }
                )
                if (state.rateLimitEnabled) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 54.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 0.5.dp
                    )
                    GoogleSettingsRow(
                        icon = Icons.Rounded.Speed,
                        title = "最大下载速度",
                        subtitle = "${state.rateLimitValue} KB/s",
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = { showRateLimitDialog = true }
                    )
                }
            }
        }
    }

    if (showRateLimitDialog) {
        RateLimitInputDialog(
            initialValue = state.rateLimitValue,
            onConfirm = viewModel::updateRateLimitValue,
            onDismiss = { showRateLimitDialog = false }
        )
    }
}

@Composable
fun RateLimitInputDialog(
    initialValue: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var textValue by remember { mutableStateOf(initialValue.toString()) }
    var errorText by remember { mutableStateOf<String?>(null) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("最大下载速度", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "设置限制的最大下载速度 (单位: KB/s)。最小值为 10 KB/s。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = textValue,
                    onValueChange = {
                        textValue = it
                        errorText = null
                    },
                    label = { Text("限速值 (KB/s)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    isError = errorText != null
                )
                errorText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val intVal = textValue.toIntOrNull()
                    if (intVal == null || intVal < 10) {
                        errorText = "请输入大于等于 10 的有效数字"
                    } else {
                        onConfirm(intVal)
                        onDismiss()
                    }
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun <T> OptionSelectDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                options.forEach { (value, label) ->
                    val isSelected = value == selected
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                        onClick = {
                            onSelect(value)
                            onDismiss()
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onSelect(value)
                                    onDismiss()
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

fun checkBatteryIgnoring(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } else {
        true
    }
}

fun videoQualityLabel(value: Int): String {
    return when (value) {
        0 -> "最佳"
        2160 -> "4K (2160p)"
        1440 -> "2K (1440p)"
        1080 -> "1080p"
        720 -> "720p"
        480 -> "480p"
        360 -> "360p"
        1 -> "最低"
        else -> "${value}p"
    }
}

fun audioQualityLabel(value: Int): String {
    return when (value) {
        0 -> "最佳"
        192 -> "高 (192 Kbps)"
        128 -> "中 (128 Kbps)"
        64 -> "低 (64 Kbps)"
        else -> "${value} Kbps"
    }
}

fun videoFormatLabel(value: Int): String {
    return when (value) {
        1 -> "兼容优先 (MP4 / H.264)"
        0 -> "画质优先 (VP9 / AV1)"
        else -> "未知"
    }
}

fun audioFormatLabel(value: Int): String {
    return when (value) {
        0 -> "最佳"
        1 -> "M4A (AAC)"
        2 -> "OPUS"
        else -> "未知"
    }
}

fun LazyListScope.settingsFooterSpacer() {
    item {
        Spacer(Modifier.height(36.dp))
    }
    item {
        SettingsFooter()
    }
}

@Composable
fun SettingsFooter() {
    val footerTextStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 11.sp,
        lineHeight = 15.sp
    )
    val footerTextColor = MaterialTheme.colorScheme.outline
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "本地引擎：Chaquopy + yt-dlp + ffmpeg，下载与处理均在本机完成。",
            style = footerTextStyle,
            color = footerTextColor,
            textAlign = TextAlign.Center
        )
        Text(
            text = "© 2026 拾象 Media Downloader",
            style = footerTextStyle,
            color = footerTextColor,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SettingsPageHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun StorageLocationPage(
    selected: StorageLocation,
    customPath: String,
    onSelect: (StorageLocation) -> Unit,
    onPickCustomFolder: () -> Unit,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = SettingsPageHorizontalPadding,
            end = SettingsPageHorizontalPadding,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsPageHeader(
                title = "默认存储位置",
                subtitle = "选择下载完成后的默认保存方式",
                onBack = onBack
            )
        }
        item {
            SettingsGroupCard {
                StorageLocation.entries.forEachIndexed { index, location ->
                    val isSelected = location == selected
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        } else {
                            Color.Transparent
                        },
                        onClick = {
                            if (location == StorageLocation.Custom) {
                                if (customPath.isBlank()) {
                                    onPickCustomFolder()
                                } else {
                                    onSelect(location)
                                }
                            } else {
                                onSelect(location)
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    if (location == StorageLocation.Custom) {
                                        if (customPath.isBlank()) {
                                            onPickCustomFolder()
                                        } else {
                                            onSelect(location)
                                        }
                                    } else {
                                        onSelect(location)
                                    }
                                }
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = location.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val description = if (location == StorageLocation.Custom && customPath.isNotBlank()) {
                                    "${location.description}\n当前目录：$customPath"
                                } else {
                                    location.description
                                }
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (location == StorageLocation.Custom && customPath.isNotBlank()) {
                                Spacer(Modifier.width(8.dp))
                                TextButton(
                                    onClick = onPickCustomFolder,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("更改", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    if (index < StorageLocation.entries.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 54.dp, end = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SupportedSitesPage(onBack: () -> Unit) {
    val platforms = remember { supportedSitePlatforms() }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = SettingsPageHorizontalPadding,
            end = SettingsPageHorizontalPadding,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsPageHeader(
                title = "支持的平台",
                subtitle = "目前已支持的主流视频与媒体平台",
                onBack = onBack
            )
        }
        item {
            SettingsGroupCard {
                platforms.forEachIndexed { index, (platform, displayName) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = platform.iconRes),
                                    contentDescription = displayName,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (index < platforms.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 68.dp, end = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}

fun supportedSitePlatforms(): List<Pair<LinkPlatform, String>> {
    return listOf(
        LinkPlatform.YouTube to "YouTube",
        LinkPlatform.Bilibili to "哔哩哔哩",
        LinkPlatform.Douyin to "TikTok",
        LinkPlatform.Xiaohongshu to "小红书",
        LinkPlatform.Weibo to "微博",
        LinkPlatform.Instagram to "Instagram",
        LinkPlatform.X to "X / Twitter",
        LinkPlatform.Threads to "Threads",
        LinkPlatform.Pornhub to "Pornhub"
    )
}

@Composable
fun WebsiteLoginStatesPage(
    loginStates: List<WebsiteLoginState>,
    onStartLogin: (String) -> Unit,
    onDelete: (WebsiteLoginState) -> Unit,
    onBack: () -> Unit
) {
    var draftUrl by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var revealedHost by remember { mutableStateOf<String?>(null) }

    fun submit(url: String) {
        val normalized = normalizeWebsiteLoginUrl(url)
        if (!UrlExtractor.isWebUrl(normalized)) {
            errorText = "请输入有效的网站地址"
            return
        }
        errorText = null
        onStartLogin(normalized)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = SettingsPageHorizontalPadding,
            end = SettingsPageHorizontalPadding,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SettingsPageHeader(
                title = "网站登录状态",
                subtitle = "保存网页登录状态，下载时自动使用",
                onBack = onBack
            )
        }
        item {
            WebsiteLoginInputPanel(
                input = draftUrl,
                errorText = errorText,
                onInputChange = {
                    draftUrl = it
                    errorText = null
                },
                onSubmit = { submit(draftUrl) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (loginStates.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "还没有保存的网站登录状态",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else {
            items(loginStates, key = { it.host }) { state ->
                WebsiteLoginStateRow(
                    state = state,
                    deleteRevealed = revealedHost == state.host,
                    onClick = {
                        revealedHost = null
                        onStartLogin(state.url)
                    },
                    onStartDeleteDrag = { revealedHost = state.host },
                    onCloseDelete = { revealedHost = null },
                    onDelete = {
                        revealedHost = null
                        onDelete(state)
                    },
                    modifier = Modifier
                )
            }
        }
    }
}

@Composable
fun WebsiteLoginInputPanel(
    input: String,
    errorText: String?,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val view = LocalView.current
    val pasteInteractionSource = remember { MutableInteractionSource() }
    val submitInteractionSource = remember { MutableInteractionSource() }
    var submitClickTrigger by remember { mutableStateOf(0) }

    val textFieldState = rememberTextFieldState(input)
    var lastPropagatedText by remember { mutableStateOf(textFieldState.text.toString()) }

    LaunchedEffect(input) {
        if (input != lastPropagatedText) {
            textFieldState.setTextAndPlaceCursorAtEnd(input)
            lastPropagatedText = input
        }
    }

    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .distinctUntilChanged()
            .collectLatest { text ->
                if (text != lastPropagatedText) {
                    lastPropagatedText = text
                    onInputChange(text)
                }
            }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(22.dp)
                )
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(alpha = 0.86f))
                .padding(start = 12.dp, top = 5.dp, end = 5.dp, bottom = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
            BasicTextField(
                state = textFieldState,
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = MaterialTheme.typography.titleMedium.merge(
                    TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                onKeyboardAction = { onSubmit() },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                decorator = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (textFieldState.text.isBlank()) {
                            Text(
                                text = "输入网站地址 (例如: weibo.com)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )

            Button(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    val text = clipboard.getText()?.text.orEmpty()
                    if (text.isNotBlank()) {
                        textFieldState.setTextAndPlaceCursorAtEnd(text)
                    }
                },
                modifier = Modifier
                    .elasticPress(pasteInteractionSource)
                    .size(40.dp),
                shape = CircleShape,
                interactionSource = pasteInteractionSource,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandBeigeSoft,
                    contentColor = BrandGreen
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    Icons.Rounded.ContentPaste,
                    contentDescription = "粘贴",
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        errorText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Button(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                submitClickTrigger += 1
                onSubmit()
            },
            enabled = input.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .clickPulse(submitClickTrigger)
                .height(46.dp),
            shape = RoundedCornerShape(23.dp),
            colors = brandButtonColors(submitInteractionSource),
            interactionSource = submitInteractionSource,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "打开登录",
                    maxLines = 1,
                    softWrap = false,
                    style = MaterialTheme.typography.labelLarge.copy(
                        lineHeight = 22.sp,
                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                    )
                )
            }
        }
    }
}

@Composable
fun StorageLocationDialog(
    selected: StorageLocation,
    onSave: (StorageLocation) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(selected) { mutableStateOf(selected) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("默认存储位置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StorageLocation.entries.forEach { location ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(15.dp),
                        color = if (location == draft) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        onClick = { draft = location }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = location == draft,
                                onClick = { draft = location }
                            )
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Text(location.label, fontWeight = FontWeight.SemiBold)
                                Text(
                                    location.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun WebsiteLoginStatesDialog(
    loginStates: List<WebsiteLoginState>,
    onStartLogin: (String) -> Unit,
    onDelete: (WebsiteLoginState) -> Unit,
    onDismiss: () -> Unit
) {
    var draftUrl by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var revealedHost by remember { mutableStateOf<String?>(null) }

    fun submit(url: String) {
        val normalized = normalizeWebsiteLoginUrl(url)
        if (!UrlExtractor.isWebUrl(normalized)) {
            errorText = "请输入有效的网站地址"
            return
        }
        errorText = null
        onStartLogin(normalized)
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("网站登录状态") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "管理保存到本机的网页登录状态。下载需要登录或验证的网站时，应用会自动使用对应状态。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = draftUrl,
                        onValueChange = {
                            draftUrl = it
                            errorText = null
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("网站地址") },
                        placeholder = { Text("example.com") },
                        isError = errorText != null
                    )
                    val loginInteractionSource = remember { MutableInteractionSource() }
                    Button(
                        onClick = { submit(draftUrl) },
                        enabled = draftUrl.isNotBlank(),
                        colors = brandButtonColors(loginInteractionSource),
                        interactionSource = loginInteractionSource
                    ) {
                        Text("登录")
                    }
                }
                errorText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (loginStates.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Text(
                            text = "还没有保存的网站登录状态",
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(loginStates, key = { it.host }) { state ->
                            WebsiteLoginStateRow(
                                state = state,
                                deleteRevealed = revealedHost == state.host,
                                onClick = {
                                    revealedHost = null
                                    onStartLogin(state.url)
                                },
                                onStartDeleteDrag = { revealedHost = state.host },
                                onCloseDelete = { revealedHost = null },
                                onDelete = {
                                    revealedHost = null
                                    onDelete(state)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        }
    )
}

@Composable
fun WebsiteLoginStateRow(
    state: WebsiteLoginState,
    deleteRevealed: Boolean,
    onClick: () -> Unit,
    onStartDeleteDrag: () -> Unit,
    onCloseDelete: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val deleteWidthPx = with(density) { HistoryDeleteRevealWidth.toPx() }
    var targetOffsetPx by remember(state.host) { mutableStateOf(0f) }
    var dragDistancePx by remember(state.host) { mutableStateOf(0f) }
    val animatedOffsetPx by animateFloatAsState(
        targetValue = targetOffsetPx,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
        label = "website-login-delete-offset"
    )
    val interactionSource = remember { MutableInteractionSource() }

    fun settleDeleteDrag() {
        val shouldReveal = dragDistancePx >= deleteWidthPx * 0.42f
        targetOffsetPx = if (shouldReveal) -deleteWidthPx else 0f
        dragDistancePx = if (shouldReveal) deleteWidthPx else 0f
        if (!shouldReveal) onCloseDelete()
    }

    LaunchedEffect(deleteRevealed, deleteWidthPx) {
        targetOffsetPx = if (deleteRevealed) -deleteWidthPx else 0f
        dragDistancePx = if (deleteRevealed) deleteWidthPx else 0f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .shadow(1.dp, RoundedCornerShape(16.dp), clip = false)
            .clip(RoundedCornerShape(16.dp))
    ) {
        DeleteSwipeBackground(
            visible = targetOffsetPx < -0.5f,
            onDelete = onDelete,
            shape = RoundedCornerShape(16.dp),
            color = HistoryDeleteColor,
            tint = Color.White
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .offset { IntOffset(animatedOffsetPx.roundToInt(), 0) }
                .pointerInput(deleteWidthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            onStartDeleteDrag()
                            dragDistancePx = -targetOffsetPx
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val proposed = (targetOffsetPx + dragAmount).coerceIn(-deleteWidthPx, 0f)
                            targetOffsetPx = proposed
                            dragDistancePx = -proposed
                        },
                        onDragEnd = { settleDeleteDrag() },
                        onDragCancel = { settleDeleteDrag() }
                    )
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick
                ),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = BrandBeigeSoft.copy(alpha = 0.78f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = BrandGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.host,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "已保存登录状态",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BrandGreen,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

fun normalizeWebsiteLoginUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return ""
    return if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        trimmed
    } else {
        "https://$trimmed"
    }
}

fun websiteLoginTask(url: String): DownloadTask {
    return DownloadTask(
        id = "login-${System.currentTimeMillis()}",
        title = UrlExtractor.hostLabel(url).ifBlank { "网站登录" },
        sourceUrl = url,
        progress = 0f,
        status = DownloadStatus.Failed,
        stage = DownloadStage.AwaitingMediaCapture,
        createdAt = ""
    )
}

@Composable
fun WebsiteLoginWebViewPage(
    task: DownloadTask,
    onDismiss: () -> Unit,
    onSave: (DownloadTask, String) -> Unit
) {
    val context = LocalContext.current
    var currentUrl by remember(task.id) { mutableStateOf(task.sourceUrl) }
    var pageTitle by remember(task.id) { mutableStateOf(task.title) }
    var pageStatus by remember(task.id) { mutableStateOf("正在加载页面") }
    val webViewHolder = remember(task.id) { arrayOfNulls<WebView>(1) }
    val saveInteractionSource = remember { MutableInteractionSource() }
    val closeInteractionSource = remember { MutableInteractionSource() }

    BackHandler {
        val view = webViewHolder[0]
        if (view?.canGoBack() == true) {
            view.goBack()
        } else {
            onDismiss()
        }
    }

    DisposableEffect(task.id) {
        onDispose {
            webViewHolder[0]?.destroy()
            webViewHolder[0] = null
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(44.dp)
                        .elasticPress(closeInteractionSource),
                    interactionSource = closeInteractionSource
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回"
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = pageTitle.ifBlank { "网站登录" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = {
                        CookieManager.getInstance().flush()
                        val manager = CookieManager.getInstance()
                        val cookie = context.webViewCookieFileForTask(task.sourceUrl, currentUrl)
                            .ifBlank { manager.mergedCookiesForTask(task.sourceUrl, currentUrl) }
                        onSave(task, cookie)
                    },
                    modifier = Modifier.elasticPress(saveInteractionSource),
                    interactionSource = saveInteractionSource
                ) {
                    Text("保存")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            if (pageStatus.isNotBlank()) {
                Text(
                    text = pageStatus,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                factory = { viewContext ->
                    CookieManager.getInstance().setAcceptCookie(true)
                    WebView(viewContext).apply {
                        webViewHolder[0] = this
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        isFocusable = true
                        isFocusableInTouchMode = true
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                val cleaned = title.orEmpty().usableCapturedPageTitle()
                                if (cleaned.isNotBlank()) {
                                    pageTitle = cleaned
                                }
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val scheme = request?.url?.scheme.orEmpty()
                                return if (scheme == "http" || scheme == "https") {
                                    false
                                } else {
                                    AppLogger.event("cookie", "websiteLoginNonHttpNavigationBlocked", "url" to request?.url.toString())
                                    true
                                }
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                currentUrl = url.orEmpty().ifBlank { currentUrl }
                                pageStatus = "正在加载页面"
                                AppLogger.event("cookie", "websiteLoginPageStarted", "url" to currentUrl)
                                super.onPageStarted(view, url, favicon)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                currentUrl = url.orEmpty().ifBlank { currentUrl }
                                pageStatus = ""
                                CookieManager.getInstance().flush()
                                AppLogger.event("cookie", "websiteLoginPageFinished", "url" to currentUrl)
                                view?.post {
                                    view.requestFocus()
                                    view.requestLayout()
                                    view.invalidate()
                                }
                                view?.evaluateJavascript(
                                    """
                                    (() => JSON.stringify({
                                      href: location.href,
                                      title: document.title,
                                      readyState: document.readyState,
                                      bodyTextLength: document.body ? document.body.innerText.length : -1,
                                      bodyTextSample: document.body ? document.body.innerText.slice(0, 160) : '',
                                      bodyChildCount: document.body ? document.body.children.length : -1,
                                      bodyBackground: document.body ? getComputedStyle(document.body).backgroundColor : '',
                                      htmlBackground: document.documentElement ? getComputedStyle(document.documentElement).backgroundColor : ''
                                    }))()
                                    """.trimIndent()
                                ) { result ->
                                    AppLogger.event(
                                        "cookie",
                                        "websiteLoginDomSnapshot",
                                        "url" to currentUrl,
                                        "snapshot" to result
                                    )
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: android.webkit.WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    pageStatus = "页面加载失败，请返回后重试"
                                    AppLogger.warn(
                                        "cookie",
                                        "websiteLoginPageFailed",
                                        "url" to request.url.toString(),
                                        "code" to error?.errorCode,
                                        "description" to error?.description
                                    )
                                }
                                super.onReceivedError(view, request, error)
                            }
                        }
                        loadUrl(task.sourceUrl)
                    }
                },
                update = { webViewHolder[0] = it }
            )
        }
    }
}

@Composable
fun SupportedSitesDialog(onDismiss: () -> Unit) {
    val platforms = remember {
        listOf(
            LinkPlatform.YouTube to "YouTube",
            LinkPlatform.Bilibili to "哔哩哔哩",
            LinkPlatform.Douyin to "TikTok",
            LinkPlatform.Xiaohongshu to "小红书",
            LinkPlatform.Weibo to "微博",
            LinkPlatform.Instagram to "Instagram",
            LinkPlatform.X to "X / Twitter",
            LinkPlatform.Threads to "Threads",
            LinkPlatform.Pornhub to "Pornhub"
        )
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("支持的平台") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "目前已支持以下主流视频与媒体平台，其他平台我们也在努力支持中。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val chunks = platforms.chunked(2)
                    items(chunks) { rowPlatforms ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowPlatforms.forEach { (platform, displayName) ->
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = platform.iconRes),
                                                    contentDescription = displayName,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            if (rowPlatforms.size < 2) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
fun SectionHeader(title: String, action: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (action != null) {
            Text(
                text = action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun StatusBadge(status: DownloadStatus) {
    val color = when (status) {
        DownloadStatus.Completed -> MaterialTheme.colorScheme.primary
        DownloadStatus.Failed -> BrandGreen
        DownloadStatus.Cancelled -> MaterialTheme.colorScheme.outline
        DownloadStatus.Queued -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.tertiary
    }

    Surface(shape = CircleShape, color = color, modifier = Modifier.size(44.dp)) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = status,
                transitionSpec = {
                    (fadeIn(tween(140)) + scaleIn(tween(180), initialScale = 0.82f)) togetherWith
                        (fadeOut(tween(110)) + scaleOut(tween(130), targetScale = 0.82f))
                },
                label = "status-badge-content"
            ) { targetStatus ->
                when (targetStatus) {
                    DownloadStatus.Downloading,
                    DownloadStatus.Processing -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(25.dp),
                            color = Color.White,
                            strokeWidth = 2.6.dp,
                            trackColor = Color.White.copy(alpha = 0.22f)
                        )
                    }

                    else -> {
                        val icon = when (targetStatus) {
                            DownloadStatus.Completed -> Icons.Rounded.CheckCircle
                            DownloadStatus.Failed -> Icons.Rounded.Error
                            DownloadStatus.Cancelled -> Icons.Rounded.Stop
                            DownloadStatus.Queued -> Icons.Rounded.History
                            else -> Icons.Rounded.Download
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                }
            }
        }
    }
}

fun getFriendlyPathFromUri(uri: Uri): String {
    return runCatching {
        val documentId = android.provider.DocumentsContract.getTreeDocumentId(uri)
        val parts = documentId.split(":")
        if (parts.size >= 2) {
            val storageId = parts[0]
            val path = parts[1]
            val storageLabel = if (storageId == "primary") "内部存储" else storageId
            return "$storageLabel/$path"
        }
        documentId
    }.getOrDefault(uri.path ?: "自定义目录")
}
