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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ScreenSearchDesktop
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.layout.onSizeChanged
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
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DownloadHome(
    state: MediaDownloaderUiState,
    listState: LazyListState,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    activeTaskSharedTaskId: String?,
    historySharedTaskId: String?,
    playerTransitionPhase: PlayerTransitionPhase,
    onInputChange: (String) -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRetry: (DownloadTask) -> Unit,
    onOpenSource: (DownloadTask) -> Unit,
    onDeleteWithFile: (String) -> Unit,
    onOpenActiveTask: (DownloadTask) -> Unit,
    onOpenHistoryTask: (DownloadTask) -> Unit,
    onShare: (DownloadTask) -> Unit
) {
    var revealedDeleteTaskId by remember { mutableStateOf<String?>(null) }
    val recentHistory = state.history.take(3)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, top = 20.dp, end = 14.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            LinkInputPanel(
                input = state.inputUrl,
                onInputChange = onInputChange,
                onDownload = onDownload
            )
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            ActiveTaskPanel(
                task = state.activeTask,
                sharedTransitionScope = sharedTransitionScope,
                activeSharedTaskId = activeTaskSharedTaskId,
                playerTransitionPhase = playerTransitionPhase,
                onCancel = onCancel,
                onRetry = onRetry,
                onOpenSource = onOpenSource,
                onOpen = onOpenActiveTask,
                onShare = onShare
            )
        }

        if (state.queuedTasks.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                QueuePanel(queue = state.queuedTasks)
            }
        }

        if (recentHistory.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SectionHeader(title = "最近文件", action = null)
            }

            items(recentHistory, key = { it.id }) { task ->
                HistoryRow(
                    modifier = Modifier.animateItem(
                        fadeInSpec = null,
                        fadeOutSpec = null,
                        placementSpec = tween(durationMillis = 180)
                    ),
                    task = task,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    activeSharedTaskId = historySharedTaskId,
                    playerActive = state.playerTask != null,
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
                    onOpen = { onOpenHistoryTask(task) },
                    onShare = { onShare(task) },
                    allowDelete = true
                )
            }
        }
    }
}

@Composable
fun LinkInputPanel(
    input: String,
    onInputChange: (String) -> Unit,
    onDownload: () -> Unit
) {
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

    val clipboard = LocalClipboardManager.current
    val downloadInteractionSource = remember { MutableInteractionSource() }
    val pasteInteractionSource = remember { MutableInteractionSource() }
    val view = androidx.compose.ui.platform.LocalView.current
    var downloadClickTrigger by remember { mutableStateOf(0) }
    val linkPlatform = remember(input) { LinkPlatform.fromInput(input) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.5.dp, RoundedCornerShape(10.dp), clip = false)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    listOf(HeroSurface, InputSurface)
                )
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = linkPlatform,
                        transitionSpec = {
                            (fadeIn(tween(600)) + scaleIn(tween(600), initialScale = 0.82f)) togetherWith
                                (fadeOut(tween(500)) + scaleOut(tween(600), targetScale = 0.82f))
                        },
                        label = "link-platform-icon"
                    ) { platform ->
                        if (platform == null) {
                            Icon(
                                Icons.Rounded.Link,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = platform.iconRes),
                                contentDescription = platform.displayName,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column {
                Text(
                    text = "粘贴媒体链接",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "支持从其他应用分享，或直接粘贴网址",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(alpha = 0.86f))
                .padding(start = 12.dp, top = 5.dp, end = 5.dp, bottom = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                onKeyboardAction = { onDownload() },
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
                                text = "输入视频链接",
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
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
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
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Button(
            onClick = {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                downloadClickTrigger += 1
                onDownload()
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickPulse(downloadClickTrigger)
                .height(44.dp),
            shape = RoundedCornerShape(22.dp),
            colors = brandButtonColors(downloadInteractionSource),
            interactionSource = downloadInteractionSource,
            contentPadding = PaddingValues(horizontal = 24.dp)
        ) {
            Icon(
                Icons.Rounded.ScreenSearchDesktop,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "识别视频信息",
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.offset(y = (-1).dp),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 15.sp,
                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                )
            )
        }

    }
}

@Composable
fun FormatPickerDialog(
    title: String,
    thumbnailUrl: String,
    sourceName: String,
    options: List<DownloadFormatOption>,
    selected: DownloadFormatOption?,
    warning: String?,
    onSelect: (DownloadFormatOption) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val confirmInteractionSource = remember { MutableInteractionSource() }
    val view = androidx.compose.ui.platform.LocalView.current
    var animateSheet by remember { mutableStateOf(false) }
    var sheetHeightPx by remember { mutableStateOf(0f) }
    var sheetTargetOffsetPx by remember { mutableStateOf(0f) }
    var sheetDragDistancePx by remember { mutableStateOf(0f) }
    val sheetOffsetPx by animateFloatAsState(
        targetValue = sheetTargetOffsetPx,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = Spring.StiffnessHigh
        ),
        label = "format-sheet-drag-offset"
    )
    fun executeDismiss() {
        animateSheet = false
    }
    fun settleSheetDrag() {
        val shouldDismiss = sheetHeightPx > 0f && sheetDragDistancePx >= sheetHeightPx * 0.3f
        if (shouldDismiss) {
            executeDismiss()
        } else {
            sheetTargetOffsetPx = 0f
            sheetDragDistancePx = 0f
        }
    }

    BackHandler(enabled = animateSheet, onBack = { executeDismiss() })

    LaunchedEffect(Unit) {
        animateSheet = true
    }
    LaunchedEffect(animateSheet) {
        if (animateSheet) {
            sheetTargetOffsetPx = 0f
            sheetDragDistancePx = 0f
        } else {
            delay(200)
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
            label = "format-scrim-alpha"
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { executeDismiss() }
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
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { sheetHeightPx = it.height.toFloat() }
                    .offset { IntOffset(0, sheetOffsetPx.roundToInt()) }
                    .clickable(enabled = false, onClick = {})
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
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
                                    onDragEnd = { settleSheetDrag() },
                                    onDragCancel = { settleSheetDrag() }
                                )
                            },
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(width = 36.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                        Text(
                            text = "选择下载版本",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        MediaPreviewHeader(
                            title = title,
                            sourceName = sourceName,
                            thumbnailUrl = thumbnailUrl,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (!warning.isNullOrBlank()) {
                            Text(
                                text = warning,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(options, key = { it.selector + it.label }) { option ->
                            val isSelected = option == selected
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(15.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                onClick = { onSelect(option) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { onSelect(option) }
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            option.label,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (option.detail.isNotBlank()) {
                                            Text(
                                                option.detail,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            animateSheet = false
                            onConfirm()
                        },
                        enabled = selected != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .elasticPress(confirmInteractionSource),
                        colors = brandButtonColors(confirmInteractionSource),
                        interactionSource = confirmInteractionSource,
                        shape = RoundedCornerShape(23.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Archive,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "开始下载",
                            modifier = Modifier.offset(y = (-1).dp),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 15.sp,
                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MediaPreviewHeader(
    title: String,
    sourceName: String,
    thumbnailUrl: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RemoteThumbnail(
            thumbnailUrl = thumbnailUrl,
            modifier = Modifier
                .size(width = 131.dp, height = 92.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .height(92.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title.ifBlank { "媒体链接" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = sourceName.ifBlank { "网页媒体" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun RemoteThumbnail(
    thumbnailUrl: String,
    modifier: Modifier = Modifier
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, thumbnailUrl) {
        value = null
        if (thumbnailUrl.isBlank()) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching { loadRemoteBitmap(thumbnailUrl) }.getOrNull()
        }
    }

    Surface(
        modifier = modifier,
        color = BrandBeigeSoft.copy(alpha = 0.86f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "视频封面",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.default_video_thumbnail),
                    contentDescription = "默认视频封面",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

fun loadRemoteBitmap(url: String): Bitmap? {
    val normalizedUrl = url.preferHttpsThumbnailUrl()
    remoteThumbnailMemoryCache.get(normalizedUrl)?.let { return it }
    val connection = (URL(normalizedUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8_000
        readTimeout = 8_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "Mozilla/5.0")
        if (normalizedUrl.contains("sinaimg.cn") || normalizedUrl.contains("weibocdn") || normalizedUrl.contains("weibo.com")) {
            setRequestProperty("Referer", "https://weibo.com/")
        }
    }
    return try {
        connection.inputStream.use { input ->
            BitmapFactory.decodeStream(input)?.also { bitmap ->
                remoteThumbnailMemoryCache.put(normalizedUrl, bitmap)
            }
        }
    } finally {
        connection.disconnect()
    }
}

fun String.preferHttpsThumbnailUrl(): String {
    return if (startsWith("http://", ignoreCase = true)) {
        "https://" + drop("http://".length)
    } else {
        this
    }
}

val remoteThumbnailMemoryCache = LruCache<String, Bitmap>(12)


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ActiveTaskPanel(
    task: DownloadTask?,
    sharedTransitionScope: SharedTransitionScope,
    activeSharedTaskId: String?,
    playerTransitionPhase: PlayerTransitionPhase,
    onCancel: () -> Unit,
    onRetry: (DownloadTask) -> Unit,
    onOpenSource: (DownloadTask) -> Unit,
    onOpen: (DownloadTask) -> Unit,
    onShare: (DownloadTask) -> Unit
) {
    val view = androidx.compose.ui.platform.LocalView.current
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title = "当前任务", action = null)

        val taskPanelHeight by animateDpAsState(
            targetValue = 208.dp,
            animationSpec = tween(durationMillis = 240),
            label = "active-task-height"
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(taskPanelHeight)
        ) {
            val currentTask = task ?: run {
                EmptyState()
                return@Box
            }
            val cancelInteractionSource = remember { MutableInteractionSource() }
            val retryInteractionSource = remember { MutableInteractionSource() }
            val sourceInteractionSource = remember { MutableInteractionSource() }
            val openInteractionSource = remember { MutableInteractionSource() }
            val shareInteractionSource = remember { MutableInteractionSource() }
            val sourceText = remember(currentTask.sourceUrl, context) {
                historyMediaSource(currentTask, AppSettings(context).storedRefererForUrl(currentTask.sourceUrl))
            }
            val isSharedTarget = activeSharedTaskId == currentTask.id
            val sharedElementVisibleInActiveTask = playerTransitionPhase != PlayerTransitionPhase.Open
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(taskPanelHeight)
                    .shadow(1.5.dp, RoundedCornerShape(10.dp), clip = false),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(92.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        RemoteThumbnail(
                            thumbnailUrl = currentTask.thumbnailUrl,
                            modifier = Modifier
                                .size(width = 131.dp, height = 92.dp)
                                .then(
                                    if (isSharedTarget) {
                                        with(sharedTransitionScope) {
                                            Modifier.sharedElementWithCallerManagedVisibility(
                                                sharedContentState = rememberSharedContentState("player-thumb-${currentTask.id}"),
                                                visible = sharedElementVisibleInActiveTask,
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
                                )
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = currentTask.title,
                                style = MaterialTheme.typography.titleSmall.copy(lineHeight = 18.sp),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            val infoItems = remember(sourceText, currentTask.formatLabel) {
                                listOf(sourceText, currentTask.formatLabel)
                                    .filter { it.isNotBlank() }
                                    .distinct()
                                    .ifEmpty { listOf("媒体来源") }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                infoItems.forEach { itemText ->
                                    Text(
                                        text = itemText,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 13.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val progressTextColor = if (currentTask.status == DownloadStatus.Failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            text = activeTaskProgressLine(currentTask),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = progressTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val speedText = activeTaskSpeedText(currentTask)
                        if (speedText.isNotBlank()) {
                            Text(
                                text = speedText,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                textAlign = TextAlign.End
                            )
                        }
                    }

                    Spacer(Modifier.height(5.dp))

                    ContinuousProgressBar(
                        progress = currentTask.progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                    )

                    Spacer(Modifier.height(5.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = activeTaskBytesText(currentTask),
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 0.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        when {
                            currentTask.status == DownloadStatus.Downloading || currentTask.status == DownloadStatus.Processing -> {
                                Surface(
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(40.dp)
                                        .elasticPress(cancelInteractionSource)
                                        .clip(CircleShape)
                                        .clickable(
                                            interactionSource = cancelInteractionSource,
                                            indication = null,
                                            onClick = onCancel
                                        ),
                                    shape = CircleShape,
                                    color = BrandGreen
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.Stop,
                                            contentDescription = "取消",
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                            currentTask.status == DownloadStatus.Completed && currentTask.filePath.isNotBlank() -> {
                                RoundTaskActionButton(
                                    icon = Icons.Rounded.PlayArrow,
                                    contentDescription = "播放",
                                    interactionSource = openInteractionSource,
                                    modifier = Modifier.padding(top = 2.dp),
                                    filled = true,
                                    onClick = { onOpen(currentTask) }
                                )
                                RoundTaskActionButton(
                                    icon = Icons.AutoMirrored.Rounded.Reply,
                                    contentDescription = "分享",
                                    interactionSource = shareInteractionSource,
                                    modifier = Modifier.padding(top = 2.dp),
                                    mirrorIcon = true,
                                    onClick = {
                                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                        onShare(currentTask)
                                    }
                                )
                            }
                            currentTask.status == DownloadStatus.Failed -> {
                                if (currentTask.canTryCookieAssist()) {
                                    CompactTaskActionChip(
                                        icon = Icons.Rounded.Link,
                                        label = "登录",
                                        contentDescription = "登录并获取 Cookie",
                                        interactionSource = sourceInteractionSource,
                                        modifier = Modifier.padding(top = 2.dp),
                                        onClick = { onOpenSource(currentTask) }
                                    )
                                }
                                RoundTaskActionButton(
                                    icon = Icons.Rounded.Refresh,
                                    contentDescription = "重试",
                                    interactionSource = retryInteractionSource,
                                    modifier = Modifier.padding(top = 2.dp),
                                    filled = true,
                                    onClick = { onRetry(currentTask) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompactTaskActionChip(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(44.dp)
            .elasticPress(interactionSource)
            .clip(RoundedCornerShape(22.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(22.dp),
        color = BrandBeigeSoft.copy(alpha = 0.78f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = BrandGreen,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                color = BrandGreen,
                style = MaterialTheme.typography.labelLarge.copy(
                    lineHeight = 20.sp,
                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                        includeFontPadding = false
                    )
                ),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun RoundTaskActionButton(
    icon: ImageVector,
    contentDescription: String,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    mirrorIcon: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .size(40.dp)
            .elasticPress(interactionSource)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = CircleShape,
        color = if (filled) BrandGreen else BrandBeigeSoft.copy(alpha = 0.72f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (filled) Color.White else BrandGreen,
                modifier = Modifier
                    .size(21.dp)
                    .then(if (mirrorIcon) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier)
            )
        }
    }
}

@Composable
fun QueuePanel(queue: List<DownloadTask>) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title = "等待队列", action = "${queue.size} 个")
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(1.dp, RoundedCornerShape(10.dp), clip = false),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                queue.forEachIndexed { index, task ->
                    val sourceText = remember(task.sourceUrl, context) {
                        historyMediaSource(task, AppSettings(context).storedRefererForUrl(task.sourceUrl))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = (index + 1).toString(),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = sourceText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        if (task.stage == DownloadStage.Queued) {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = "等待中",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Text(
                                text = task.stage.displayName(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

fun downloadPercentText(task: DownloadTask): String {
    return "${(task.progress.coerceIn(0f, 1f) * 100).toInt()}%"
}

fun taskSourceAndFormatText(sourceText: String, task: DownloadTask): String {
    return listOf(sourceText, task.formatLabel)
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(" · ")
        .ifBlank { "媒体来源" }
}

fun activeTaskProgressLine(task: DownloadTask): String {
    return when (task.status) {
        DownloadStatus.Queued -> "等待当前任务完成"
        DownloadStatus.Downloading -> {
            "正在下载媒体 · ${downloadPercentText(task)}"
        }
        DownloadStatus.Processing -> "正在合并音视频"
        DownloadStatus.Completed -> "已完成 · 100%"
        DownloadStatus.Failed -> if (task.canTryCookieAssist()) {
            "需要登录或验证后重试"
        } else {
            task.errorMessage.ifBlank { "下载失败，可重试" }
        }
        DownloadStatus.Cancelled -> "已取消"
    }
}

fun activeTaskBytesText(task: DownloadTask): String {
    return when {
        task.totalBytes > 0 -> "${task.downloadedBytes.toReadableSize()} / ${task.totalBytes.toReadableSize()}"
        task.downloadedBytes > 0 -> task.downloadedBytes.toReadableSize()
        else -> ""
    }
}

fun activeTaskSpeedText(task: DownloadTask): String {
    return if (task.status == DownloadStatus.Downloading && task.speedBytesPerSecond > 0) {
        "${task.speedBytesPerSecond.toReadableSize()}/s"
    } else {
        ""
    }
}

fun downloadDetailText(task: DownloadTask): String {
    val bytes = when {
        task.totalBytes > 0 -> "${task.downloadedBytes.toReadableSize()} / ${task.totalBytes.toReadableSize()}"
        task.downloadedBytes > 0 -> task.downloadedBytes.toReadableSize()
        else -> "正在建立连接"
    }
    val speed = if (task.speedBytesPerSecond > 0) {
        "${task.speedBytesPerSecond.toReadableSize()}/s"
    } else {
        ""
    }
    return listOf(bytes, speed).filter { it.isNotBlank() }.joinToString(" · ")
}

fun downloadProgressText(task: DownloadTask): String {
    val percent = (task.progress.coerceIn(0f, 1f) * 100).toInt()
    val bytes = if (task.totalBytes > 0) {
        " · ${task.downloadedBytes.toReadableSize()} / ${task.totalBytes.toReadableSize()}"
    } else if (task.downloadedBytes > 0) {
        " · ${task.downloadedBytes.toReadableSize()}"
    } else {
        ""
    }
    val speed = if (task.speedBytesPerSecond > 0) {
        " · ${task.speedBytesPerSecond.toReadableSize()}/s"
    } else {
        ""
    }
    return if (percent > 0) {
        "正在下载媒体流 · $percent%$bytes$speed"
    } else {
        "正在下载媒体流$bytes$speed"
    }
}

fun DownloadTask.needsCookieAssist(): Boolean {
    if (status != DownloadStatus.Failed) return false
    val text = errorMessage.lowercase()
    return listOf(
        "登录",
        "cookie",
        "会员",
        "权限",
        "授权",
        "登录态",
        "403",
        "forbidden",
        "unauthorized",
        "authentication",
        "sign in",
        "private",
        "age-restricted",
        "registered users"
    ).any { marker -> marker in text }
}

fun DownloadTask.canTryCookieAssist(): Boolean {
    if (status != DownloadStatus.Failed) return false
    val text = errorMessage.lowercase()
    return needsCookieAssist() || listOf(
        "tls",
        "ssl",
        "握手",
        "站点拦截",
        "地区",
        "证书",
        "unexpected_eof"
    ).any { marker -> marker in text }
}

@Composable
fun ContinuousProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.current_task_empty),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .aspectRatio(900f / 624f)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "分享或粘贴链接后开始下载。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
