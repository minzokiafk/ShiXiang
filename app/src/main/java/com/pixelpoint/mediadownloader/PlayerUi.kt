package com.pixelpoint.mediadownloader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.SystemClock
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Reply
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun InternalPlayerScreen(
    task: DownloadTask,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    sharedElementVisible: Boolean,
    transitionPhase: PlayerTransitionPhase,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onPlaybackProgress: (String, Long, Long) -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val density = LocalDensity.current
    val view = LocalView.current
    var controlsVisible by remember(task.id) { mutableStateOf(true) }
    var isPlaying by remember(task.id) { mutableStateOf(false) }
    var playbackState by remember(task.id) { mutableStateOf(Player.STATE_IDLE) }
    var durationMs by remember(task.id) { mutableLongStateOf(0L) }
    var positionMs by remember(task.id) {
        mutableLongStateOf(resumePlaybackPositionMs(task.playbackPositionMs, task.playbackDurationMs))
    }
    var playbackSpeed by remember(task.id) { mutableFloatStateOf(1f) }
    var speedMenuExpanded by remember(task.id) { mutableStateOf(false) }
    var playbackError by remember(task.id) { mutableStateOf<String?>(null) }
    var isFullscreen by remember(task.id) { mutableStateOf(false) }
    var sharedPreviewVisible by remember(task.id) { mutableStateOf(true) }
    var isSeeking by remember(task.id) { mutableStateOf(false) }
    var longPressFastForwardActive by remember(task.id) { mutableStateOf(false) }
    var controlsVisibleBeforeLongPress by remember(task.id) { mutableStateOf(true) }
    var speedBeforeLongPress by remember(task.id) { mutableFloatStateOf(1f) }
    var wasPlayingBeforeLongPress by remember(task.id) { mutableStateOf(false) }
    var lastProgressSaveElapsedMs by remember(task.id) { mutableLongStateOf(0L) }
    val isExiting = transitionPhase == PlayerTransitionPhase.Exiting

    val previewAlpha by animateFloatAsState(
        targetValue = if (sharedPreviewVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "preview-crossfade"
    )

    val playerBgColor by animateColorAsState(
        targetValue = if (sharedElementVisible) Color.Black else Color.Transparent,
        animationSpec = tween(durationMillis = 250),
        label = "player-background-color"
    )

    // Persistent measured system bars heights to prevent layout shifts when controls hide/show
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var rememberedStatusBarHeight by remember { mutableStateOf(0.dp) }
    if (statusBarPadding > 0.dp && rememberedStatusBarHeight == 0.dp) {
        rememberedStatusBarHeight = statusBarPadding
    }
    val playerTopPadding = if (rememberedStatusBarHeight > 0.dp) rememberedStatusBarHeight else statusBarPadding

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var rememberedNavBarHeight by remember { mutableStateOf(0.dp) }
    if (navBarPadding > 0.dp && rememberedNavBarHeight == 0.dp) {
        rememberedNavBarHeight = navBarPadding
    }
    val playerBottomPadding = if (rememberedNavBarHeight > 0.dp) rememberedNavBarHeight else navBarPadding

    var playerActive by remember(task.id) { mutableStateOf(false) }

    val player = remember(task.filePath, playerActive) {
        if (playerActive) {
            val resumePositionMs = resumePlaybackPositionMs(task.playbackPositionMs, task.playbackDurationMs)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build()
            ExoPlayer.Builder(context)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .build().apply {
                    setMediaItem(MediaItem.fromUri(Uri.fromFile(File(task.filePath))))
                    if (resumePositionMs > 0L) {
                        seekTo(resumePositionMs)
                    }
                    playWhenReady = true
                    prepare()
                }
        } else {
            null
        }
    }

    fun persistPlaybackProgress(force: Boolean = false) {
        val currentPlayer = player ?: return
        val currentDuration = currentPlayer.duration
        val safeDuration = if (currentDuration == C.TIME_UNSET) durationMs else max(0L, currentDuration)
        val safePosition = max(0L, currentPlayer.currentPosition)
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastProgressSaveElapsedMs < PLAYBACK_PROGRESS_SAVE_INTERVAL_MS) {
            return
        }
        lastProgressSaveElapsedMs = now
        onPlaybackProgress(task.id, safePosition, safeDuration)
    }

    fun closeWithProgressSaved() {
        persistPlaybackProgress(force = true)
        onBack()
    }

    fun startLongPressFastForward() {
        val currentPlayer = player ?: return
        if (playbackState == Player.STATE_ENDED) return
        controlsVisibleBeforeLongPress = controlsVisible
        wasPlayingBeforeLongPress = currentPlayer.isPlaying
        speedBeforeLongPress = playbackSpeed
        longPressFastForwardActive = true
        playbackSpeed = LONG_PRESS_FAST_FORWARD_SPEED
        currentPlayer.setPlaybackSpeed(LONG_PRESS_FAST_FORWARD_SPEED)
        currentPlayer.play()
        controlsVisible = false
        AppLogger.event("player", "longPressFastForwardStarted", "taskId" to task.id)
    }

    fun stopLongPressFastForward() {
        if (!longPressFastForwardActive) return
        val restoreSpeed = speedBeforeLongPress
        player?.let { currentPlayer ->
            currentPlayer.setPlaybackSpeed(restoreSpeed)
            if (!wasPlayingBeforeLongPress) {
                persistPlaybackProgress(force = true)
                currentPlayer.pause()
            }
        }
        playbackSpeed = restoreSpeed
        longPressFastForwardActive = false
        controlsVisible = controlsVisibleBeforeLongPress
        AppLogger.event("player", "longPressFastForwardStopped", "taskId" to task.id)
    }

    var wasPlayingWhenPaused by remember(player) { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        if (player == null) return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    wasPlayingWhenPaused = player.isPlaying
                    persistPlaybackProgress(force = true)
                    player.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (wasPlayingWhenPaused) {
                        player.play()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
                if (!value && !longPressFastForwardActive && playbackState != Player.STATE_ENDED) {
                    controlsVisible = true
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                val playerDuration = player.duration
                durationMs = if (playerDuration == C.TIME_UNSET) 0L else max(0L, playerDuration)
                positionMs = max(0L, player.currentPosition)
                if (state == Player.STATE_ENDED) {
                    persistPlaybackProgress(force = true)
                    controlsVisible = true
                }
            }

            override fun onRenderedFirstFrame() {
                sharedPreviewVisible = false
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.localizedMessage ?: "无法播放该文件"
                AppLogger.error("player", "playbackError", error, "taskId" to task.id, "filePath" to task.filePath)
            }
        }
        player.addListener(listener)
        onDispose {
            persistPlaybackProgress(force = true)
            player.removeListener(listener)
            player.release()
        }
    }

    val window = activity?.window
    val controller = remember(window) { window?.let { WindowCompat.getInsetsController(it, it.decorView) } }

    var originalLightStatus by remember { mutableStateOf(true) }
    var originalLightNav by remember { mutableStateOf(true) }

    // Manage edge-to-edge drawing and transparent system bars for player screen
    DisposableEffect(window, controller) {
        if (window != null && controller != null) {
            originalLightStatus = controller.isAppearanceLightStatusBars
            originalLightNav = controller.isAppearanceLightNavigationBars

            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false

            onDispose {
                controller.isAppearanceLightStatusBars = originalLightStatus
                controller.isAppearanceLightNavigationBars = originalLightNav
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        } else {
            onDispose {}
        }
    }

    // Keep screen on during playback
    LaunchedEffect(isPlaying, window) {
        if (window != null) {
            if (isPlaying) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    DisposableEffect(window) {
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Restore system bars immediately at the start of exit transition to prevent post-transition flash
    LaunchedEffect(isExiting, controller) {
        if (isExiting && controller != null) {
            controller.isAppearanceLightStatusBars = originalLightStatus
            controller.isAppearanceLightNavigationBars = originalLightNav
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Synchronize status bar and navigation bar visibility with controls visible state
    LaunchedEffect(controlsVisible, controller, isFullscreen, isExiting) {
        if (controller != null && !isExiting) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (controlsVisible) {
                controller.show(WindowInsetsCompat.Type.statusBars())
                controller.show(WindowInsetsCompat.Type.navigationBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.statusBars())
                if (isFullscreen) {
                    controller.hide(WindowInsetsCompat.Type.navigationBars())
                }
            }
        }
    }

    DisposableEffect(isFullscreen, activity) {
        val previousOrientation = activity?.requestedOrientation
        if (isFullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            if (previousOrientation != null) {
                activity.requestedOrientation = previousOrientation
            }
        }
    }

    LaunchedEffect(player, isPlaying) {
        if (player == null) return@LaunchedEffect
        val playerDuration = player.duration
        durationMs = if (playerDuration == C.TIME_UNSET) 0L else max(0L, playerDuration)
        positionMs = max(0L, player.currentPosition)
        if (isPlaying) {
            while (true) {
                kotlinx.coroutines.delay(250)
                positionMs = max(0L, player.currentPosition)
                val currentDuration = player.duration
                durationMs = if (currentDuration == C.TIME_UNSET) 0L else max(0L, currentDuration)
                persistPlaybackProgress()
            }
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, isSeeking, speedMenuExpanded) {
        if (controlsVisible && isPlaying && !isSeeking && !speedMenuExpanded) {
            kotlinx.coroutines.delay(3200)
            controlsVisible = false
        }
    }

    LaunchedEffect(task.id, sharedElementVisible) {
        if (sharedElementVisible) {
            sharedPreviewVisible = true
            // First phase: Wait for zoom transition to complete
            kotlinx.coroutines.delay(320)
            // Second phase: Activate player loading
            playerActive = true
            // Wait up to 1200ms as a safety fallback to hide the thumbnail preview
            kotlinx.coroutines.delay(1200)
            sharedPreviewVisible = false
        } else {
            sharedPreviewVisible = true
            controlsVisible = false
            player?.pause()
        }
    }

    BackHandler(onBack = { closeWithProgressSaved() })

    var edgeDragActive by remember { mutableStateOf(false) }
    var edgeDragDistance by remember { mutableFloatStateOf(0f) }
    val edgeWidthPx = with(density) { 42.dp.toPx() }
    val exitThresholdPx = with(density) { 96.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(playerBgColor)
            .pointerInput(task.id) {
                detectHorizontalDragGestures(
                    onDragStart = { start ->
                        edgeDragActive = start.x <= edgeWidthPx || start.x >= size.width - edgeWidthPx
                        edgeDragDistance = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        if (edgeDragActive) edgeDragDistance += dragAmount
                    },
                    onDragEnd = {
                        if (edgeDragActive && abs(edgeDragDistance) >= exitThresholdPx) {
                            closeWithProgressSaved()
                        }
                        edgeDragActive = false
                        edgeDragDistance = 0f
                    },
                    onDragCancel = {
                        edgeDragActive = false
                        edgeDragDistance = 0f
                    }
                )
            }
            .pointerInput(task.id, player, playbackState) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val edgeGesture = down.position.x <= edgeWidthPx || down.position.x >= size.width - edgeWidthPx
                    if (edgeGesture) {
                        do {
                            val event = awaitPointerEvent()
                        } while (event.changes.any { it.pressed })
                        return@awaitEachGesture
                    }
                    var longPressReached = false
                    try {
                        withTimeout(viewConfiguration.longPressTimeoutMillis) {
                            waitForUpOrCancellation()
                        }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        longPressReached = true
                    }
                    if (longPressReached) {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        startLongPressFastForward()
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                        stopLongPressFastForward()
                    }
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { controlsVisible = !controlsVisible }
            )
    ) {
        if (player != null) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - previewAlpha },
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        this.player = player
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { it.player = player }
            )
        }

        val metadata by produceState(initialValue = VideoMetadata(), task.id, task.filePath) {
            value = withContext(Dispatchers.IO) {
                loadVideoMetadata(context, task.id, task.filePath)
            }
        }
        val videoAspectRatio = remember(metadata) {
            if (metadata.videoWidth > 0 && metadata.videoHeight > 0) {
                metadata.videoWidth.toFloat() / metadata.videoHeight.toFloat()
            } else {
                16f / 9f
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            ThumbnailPreview(
                task = task,
                modifier = with(sharedTransitionScope) {
                    Modifier
                        .aspectRatio(videoAspectRatio)
                        .sharedElementWithCallerManagedVisibility(
                            sharedContentState = rememberSharedContentState("player-thumb-${task.id}"),
                            visible = sharedElementVisible,
                            boundsTransform = BoundsTransform { _: Rect, _: Rect ->
                                if (isExiting) {
                                    tween(durationMillis = 240)
                                } else {
                                    spring(dampingRatio = 0.88f, stiffness = 650f)
                                }
                            }
                        )
                        .graphicsLayer { alpha = previewAlpha }
                },
                showLabels = false,
                contentScale = ContentScale.Crop, // 改为 Crop，因为容器大小等于画面比例，Crop 表现等同于无裁剪的 Fit
                shape = RectangleShape
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.zIndex(12f)
        ) {
            PlayerControlsOverlay(
                task = task,
                isPlaying = isPlaying,
                durationMs = durationMs,
                positionMs = positionMs,
                playbackSpeed = playbackSpeed,
                playbackError = playbackError,
                speedMenuExpanded = speedMenuExpanded,
                isFullscreen = isFullscreen,
                statusBarPadding = playerTopPadding,
                navBarPadding = playerBottomPadding,
                sharedTransitionScope = sharedTransitionScope,
                sharedElementVisible = sharedElementVisible,
                onBack = { closeWithProgressSaved() },
                onShare = {
                    persistPlaybackProgress(force = true)
                    player?.pause()
                    controlsVisible = true
                    onShare()
                },
                onTogglePlayback = {
                    player?.let {
                        if (it.isPlaying) {
                            persistPlaybackProgress(force = true)
                            it.pause()
                        } else {
                            if (playbackState == Player.STATE_ENDED) {
                                positionMs = 0L
                                it.seekTo(0L)
                            }
                            it.play()
                        }
                    }
                    controlsVisible = true
                },
                onSeek = { targetPosition ->
                    val boundedPosition = targetPosition.coerceIn(0L, max(1L, durationMs))
                    positionMs = boundedPosition
                    player?.seekTo(boundedPosition)
                    lastProgressSaveElapsedMs = SystemClock.elapsedRealtime()
                    onPlaybackProgress(task.id, boundedPosition, durationMs)
                    isSeeking = false
                    controlsVisible = true
                },
                onSeekInteractionChange = { isSeeking = it },
                onSpeedMenuChange = {
                    speedMenuExpanded = it
                    controlsVisible = true
                },
                onSpeedSelected = { speed ->
                    playbackSpeed = speed
                    player?.setPlaybackSpeed(speed)
                    speedMenuExpanded = false
                    controlsVisible = true
                    AppLogger.event("player", "speedSelected", "taskId" to task.id, "speed" to speed)
                },
                onToggleFullscreen = {
                    isFullscreen = !isFullscreen
                    controlsVisible = true
                    AppLogger.event("player", "fullscreenToggled", "taskId" to task.id, "fullscreen" to !isFullscreen)
                }
            )
        }

        AnimatedVisibility(
            visible = longPressFastForwardActive,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(120)),
            modifier = Modifier
                .align(Alignment.Center)
                .zIndex(14f)
        ) {
            LongPressFastForwardHint()
        }
    }
}

@Composable
private fun LongPressFastForwardHint() {
    val transition = rememberInfiniteTransition(label = "long-press-fast-forward")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 720, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fast-forward-chevron-pulse"
    )

    Surface(
        color = Color.Black.copy(alpha = 0.58f),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${LONG_PRESS_FAST_FORWARD_SPEED.trimSpeed()}x 快进",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            FastForwardChevronAnimation(pulse = pulse)
        }
    }
}

@Composable
private fun FastForwardChevronAnimation(pulse: Float) {
    Canvas(modifier = Modifier.size(width = 42.dp, height = 24.dp)) {
        val stroke = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
        val chevronWidth = 8.dp.toPx()
        val chevronHeight = 14.dp.toPx()
        val gap = 4.dp.toPx()
        val centerY = size.height / 2f
        val startX = 4.dp.toPx()
        val travel = 3.dp.toPx() * pulse
        repeat(3) { index ->
            val phase = ((pulse + index * 0.34f) % 1f)
            val alpha = 0.28f + phase * 0.72f
            val x = startX + index * (chevronWidth + gap) + travel
            drawLine(
                color = Color.White.copy(alpha = alpha),
                start = androidx.compose.ui.geometry.Offset(x, centerY - chevronHeight / 2f),
                end = androidx.compose.ui.geometry.Offset(x + chevronWidth, centerY),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White.copy(alpha = alpha),
                start = androidx.compose.ui.geometry.Offset(x + chevronWidth, centerY),
                end = androidx.compose.ui.geometry.Offset(x, centerY + chevronHeight / 2f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PlayerControlsOverlay(
    task: DownloadTask,
    isPlaying: Boolean,
    durationMs: Long,
    positionMs: Long,
    playbackSpeed: Float,
    playbackError: String?,
    speedMenuExpanded: Boolean,
    isFullscreen: Boolean,
    statusBarPadding: androidx.compose.ui.unit.Dp,
    navBarPadding: androidx.compose.ui.unit.Dp,
    sharedTransitionScope: SharedTransitionScope,
    sharedElementVisible: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekInteractionChange: (Boolean) -> Unit,
    onSpeedMenuChange: (Boolean) -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onToggleFullscreen: () -> Unit
) {
    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current

    val topPadding = statusBarPadding
    val bottomPadding = navBarPadding

    // Local state for dragging the slider to ensure complete responsiveness (跟手)
    var localPosition by remember { mutableStateOf<Float?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.64f),
                    0.32f to Color.Transparent,
                    0.62f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.72f)
                )
            )
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(top = topPadding)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerIconButton(icon = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", onClick = onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = task.title.ifBlank { "未命名媒体" },
                    modifier = Modifier,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = historyMediaSource(task, AppSettings(context).storedRefererForUrl(task.sourceUrl)),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            PlayerIconButton(
                icon = Icons.Rounded.Reply,
                contentDescription = "分享",
                modifier = Modifier.scale(scaleX = -1f, scaleY = 1f),
                onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                    onShare()
                }
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = 220.dp, height = 180.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.align(Alignment.Center)) {
                PlayerPrimaryButton(
                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    onClick = onTogglePlayback
                )
            }
            localPosition?.let { draggingPosition ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = 74.dp),
                    color = Color.Black.copy(alpha = 0.52f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "${formatPlayerTime(draggingPosition.toLong())} / ${formatPlayerTime(durationMs)}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = bottomPadding)
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            playbackError?.let { message ->
                Surface(
                    color = Color(0xFFB3261E).copy(alpha = 0.92f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        text = message,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Slider(
                value = localPosition ?: positionMs.coerceAtMost(durationMs).toFloat(),
                onValueChange = {
                    localPosition = it
                    onSeekInteractionChange(true)
                },
                onValueChangeFinished = {
                    localPosition?.let {
                        onSeek(it.toLong())
                        localPosition = null
                    }
                    onSeekInteractionChange(false)
                },
                valueRange = 0f..max(1L, durationMs).toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = BrandBeige,
                    inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displayPosition = localPosition?.toLong() ?: positionMs
                Text(
                    text = "${formatPlayerTime(displayPosition)} / ${formatPlayerTime(durationMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.86f)
                )
                Spacer(Modifier.weight(1f))
                Box {
                    PlayerTextButton(
                        icon = Icons.Rounded.Speed,
                        text = "${playbackSpeed.trimSpeed()}x",
                        onClick = { onSpeedMenuChange(true) }
                    )
                    DropdownMenu(
                        expanded = speedMenuExpanded,
                        onDismissRequest = { onSpeedMenuChange(false) }
                    ) {
                        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                            DropdownMenuItem(
                                text = { Text("${speed.trimSpeed()}x") },
                                onClick = { onSpeedSelected(speed) }
                            )
                        }
                    }
                }
                PlayerIconButton(
                    icon = if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                    contentDescription = if (isFullscreen) "退出全屏" else "全屏",
                    onClick = onToggleFullscreen
                )
            }
        }
    }
}

@Composable
private fun PlayerIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = modifier)
    }
}

@Composable
private fun PlayerRoundButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        color = Color.White.copy(alpha = 0.18f),
        shape = CircleShape
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun PlayerPrimaryButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        color = BrandBeigeSoft.copy(alpha = 0.50f),
        shape = CircleShape
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(38.dp))
        }
    }
}

@Composable
private fun PlayerTextButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        color = Color.White.copy(alpha = 0.16f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(text = text, color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun formatPlayerTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun resumePlaybackPositionMs(positionMs: Long, durationMs: Long): Long {
    if (positionMs < PLAYBACK_MIN_RESUME_POSITION_MS) return 0L
    if (durationMs <= 0L) return positionMs
    val boundedPosition = positionMs.coerceIn(0L, durationMs)
    val isNearEnd = durationMs - boundedPosition <= PLAYBACK_RESUME_END_GUARD_MS ||
        boundedPosition >= (durationMs * PLAYBACK_RESUME_END_GUARD_RATIO).toLong()
    return if (isNearEnd) 0L else boundedPosition
}

private const val PLAYBACK_PROGRESS_SAVE_INTERVAL_MS = 4_000L
private const val PLAYBACK_MIN_RESUME_POSITION_MS = 3_000L
private const val PLAYBACK_RESUME_END_GUARD_MS = 5_000L
private const val PLAYBACK_RESUME_END_GUARD_RATIO = 0.95
private const val LONG_PRESS_FAST_FORWARD_SPEED = 2f

private fun Float.trimSpeed(): String {
    return if (this % 1f == 0f) this.toInt().toString() else this.toString()
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
