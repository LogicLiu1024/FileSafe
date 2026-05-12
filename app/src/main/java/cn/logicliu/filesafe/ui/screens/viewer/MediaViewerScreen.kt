package cn.logicliu.filesafe.ui.screens.viewer

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import cn.logicliu.filesafe.data.entity.FileItemEntity
import cn.logicliu.filesafe.data.repository.DecryptedFileInfo
import cn.logicliu.filesafe.ui.screens.player.isVideoFile
import coil.compose.rememberAsyncImagePainter
import java.io.File
import java.util.concurrent.TimeUnit
import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown

@OptIn(UnstableApi::class)
@Composable
fun MediaViewerScreen(
    mediaFileEntities: List<FileItemEntity>,
    initialIndex: Int,
    onNavigateBack: () -> Unit,
    onDecryptFile: suspend (Long) -> Result<DecryptedFileInfo>
) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { mediaFileEntities.size }
    var decryptedFiles by remember { mutableStateOf<Map<Long, DecryptedFileInfo>>(emptyMap()) }
    var isAnyPageZoomed by remember { mutableStateOf(false) }

    BackHandler(onBack = onNavigateBack)

    LaunchedEffect(pagerState.currentPage, pagerState.settledPage) {
        val current = pagerState.settledPage
        val pagesToLoad = ((current - 1).coerceAtLeast(0)..(current + 1).coerceAtMost(mediaFileEntities.size - 1))
            .filter { it in mediaFileEntities.indices }

        for (page in pagesToLoad) {
            val entity = mediaFileEntities[page]
            if (decryptedFiles.containsKey(entity.id)) continue

            val result = onDecryptFile(entity.id)
            result.onSuccess { fileInfo ->
                decryptedFiles = decryptedFiles + (entity.id to fileInfo)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !isAnyPageZoomed
        ) { page ->
            val entity = mediaFileEntities[page]
            val decryptedFile = decryptedFiles[entity.id]

            if (decryptedFile != null) {
                if (isVideoFile(entity.name)) {
                    VideoPage(
                        videoFile = decryptedFile.file,
                        videoName = decryptedFile.originalName,
                        onNavigateBack = onNavigateBack,
                        onZoomChanged = { zoomed -> isAnyPageZoomed = zoomed }
                    )
                } else {
                    ImagePage(
                        imageFile = decryptedFile.file,
                        imageName = decryptedFile.originalName,
                        onNavigateBack = onNavigateBack,
                        onZoomChanged = { zoomed -> isAnyPageZoomed = zoomed }
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }
}

private suspend fun PointerInputScope.detectTransformGesturesSelectively(
    isZoomed: () -> Boolean,
    onGesture: (centroid: Offset, pan: Offset, zoom: Float) -> Unit
) {
    awaitEachGesture {
        val firstDown = awaitFirstDown(requireUnconsumed = false)

        var shouldConsume = isZoomed()
        if (shouldConsume) {
            firstDown.consume()
        }

        var previousCentroid = firstDown.position
        var previousSpan = 0f
        var isMultiTouch = false

        do {
            val event = awaitPointerEvent()
            val activeChanges = event.changes.filter { it.pressed }
            if (activeChanges.isEmpty()) break

            val currentCentroid = if (activeChanges.size >= 2) {
                activeChanges.map { it.position }.reduce { acc, pos -> acc + pos } / activeChanges.size.toFloat()
            } else {
                activeChanges.first().position
            }

            if (activeChanges.size >= 2) {
                if (!isMultiTouch) {
                    isMultiTouch = true
                    shouldConsume = true
                    previousSpan = activeChanges.map { (it.position - currentCentroid).getDistance() }.sum() / activeChanges.size
                    previousCentroid = currentCentroid
                } else {
                    val currentSpan = activeChanges.map { (it.position - currentCentroid).getDistance() }.sum() / activeChanges.size
                    val zoom = if (previousSpan > 0f) currentSpan / previousSpan else 1f
                    val pan = currentCentroid - previousCentroid

                    onGesture(currentCentroid, pan, zoom)

                    previousCentroid = currentCentroid
                    previousSpan = currentSpan
                }
                activeChanges.forEach { it.consume() }
            } else if (shouldConsume) {
                if (isMultiTouch) {
                    isMultiTouch = false
                    previousCentroid = currentCentroid
                } else {
                    val pan = currentCentroid - previousCentroid
                    onGesture(currentCentroid, pan, 1f)
                    previousCentroid = currentCentroid
                }
                activeChanges.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPage(
    videoFile: File,
    videoName: String,
    onNavigateBack: () -> Unit,
    onZoomChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val uri = Uri.fromFile(videoFile)
    var isPlaying by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isLandscape by remember { mutableStateOf(false) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = false
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            kotlinx.coroutines.delay(500)
            if (!isSeeking) {
                currentPosition = exoPlayer.currentPosition.toFloat()
            }
            duration = exoPlayer.duration.coerceAtLeast(0).toFloat()
            isPlaying = exoPlayer.isPlaying
        }
    }

    LaunchedEffect(playbackSpeed) {
        exoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(showControls) {
        if (showControls && isPlaying) {
            kotlinx.coroutines.delay(3000)
            showControls = false
        } else if (!showControls) {
            showSpeedMenu = false
        }
    }

    DisposableEffect(uri) {
        val originalOrientation = activity?.requestedOrientation
        val window = activity?.window
        val decorView = window?.decorView
        val originalSystemUiVisibility = decorView?.systemUiVisibility ?: 0

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    duration = exoPlayer.duration.toFloat()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            decorView?.systemUiVisibility = originalSystemUiVisibility
            if (isLandscape) {
                activity?.requestedOrientation = originalOrientation
                    ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    LaunchedEffect(isLandscape) {
        val window = activity?.window
        val decorView = window?.decorView
        if (isLandscape) {
            decorView?.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
        } else {
            decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    LaunchedEffect(scale) {
        onZoomChanged(scale > 1f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTransformGesturesSelectively(
                    isZoomed = { scale > 1f }
                ) { centroid, pan, zoom ->
                    val oldScale = scale
                    val newScale = (oldScale * zoom).coerceIn(1f, 4f)

                    if (newScale > 1f || oldScale > 1f) {
                        val containerCenter = Offset(
                            containerSize.width.toFloat() / 2f,
                            containerSize.height.toFloat() / 2f
                        )
                        val focalDelta = centroid - containerCenter
                        val scaleCorrection = (oldScale - newScale) / oldScale
                        val centroidOffset = Offset(
                            focalDelta.x * scaleCorrection,
                            focalDelta.y * scaleCorrection
                        )

                        scale = newScale

                        if (newScale > 1f) {
                            val maxOffsetX = (containerSize.width.toFloat() / 2f) * (newScale - 1f)
                            val maxOffsetY = (containerSize.height.toFloat() / 2f) * (newScale - 1f)
                            offset = Offset(
                                x = (offset.x + pan.x + centroidOffset.x).coerceIn(-maxOffsetX, maxOffsetX),
                                y = (offset.y + pan.y + centroidOffset.y).coerceIn(-maxOffsetY, maxOffsetY)
                            )
                        } else {
                            offset = Offset.Zero
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                    },
                    onDoubleTap = {
                        showControls = true
                        if (isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    }
                )
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    isClickable = false
                    isFocusable = false
                    setOnTouchListener { _, _ -> false }
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                },
            update = { playerView ->
                playerView.player = exoPlayer
            }
        )

        if (!isPlaying) {
            IconButton(
                onClick = {
                    exoPlayer.play()
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .background(Color.White.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "播放",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (!isLandscape) {
                                    Modifier.windowInsetsPadding(WindowInsets.systemBars)
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = Color.White
                            )
                        }

                        Text(
                            text = videoName,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )

                        Box {
                            IconButton(onClick = { showSpeedMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "播放倍速",
                                    tint = Color.White
                                )
                            }
                            DropdownMenu(
                                expanded = showSpeedMenu,
                                onDismissRequest = { showSpeedMenu = false }
                            ) {
                                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (speed == 1f) "正常" else "${speed}x",
                                                color = if (speed == playbackSpeed)
                                                    MaterialTheme.colorScheme.primary
                                                else Color.Unspecified
                                            )
                                        },
                                        onClick = {
                                            playbackSpeed = speed
                                            showSpeedMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        IconButton(onClick = {
                            if (isLandscape) {
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            } else {
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            }
                            isLandscape = !isLandscape
                        }) {
                            Icon(
                                imageVector = Icons.Default.ScreenRotation,
                                contentDescription = if (isLandscape) "切换为竖屏" else "切换为横屏",
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val newPosition = (currentPosition - 10000f).coerceAtLeast(0f)
                                exoPlayer.seekTo(newPosition.toLong())
                                showControls = true
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.White.copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastRewind,
                                contentDescription = "后退10秒",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.play()
                                }
                                showControls = true
                            }
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "暂停" else "播放",
                                tint = Color.White,
                                modifier = Modifier.size(56.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val newPosition = (currentPosition + 10000).coerceAtMost(duration)
                                exoPlayer.seekTo(newPosition.toLong())
                                showControls = true
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.White.copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "快进10秒",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Slider(
                            value = currentPosition,
                            onValueChange = { newValue ->
                                isSeeking = true
                                currentPosition = newValue
                                exoPlayer.seekTo(newValue.toLong())
                            },
                            onValueChangeFinished = {
                                isSeeking = false
                            },
                            valueRange = 0f..duration.coerceAtLeast(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatVideoTime(currentPosition.toLong()),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                            Text(
                                text = formatVideoTime(duration.toLong()),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ImagePage(
    imageFile: File,
    imageName: String,
    onNavigateBack: () -> Unit,
    onZoomChanged: (Boolean) -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(scale) {
        onZoomChanged(scale > 1f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTransformGesturesSelectively(
                    isZoomed = { scale > 1f }
                ) { centroid, pan, zoom ->
                    val oldScale = scale
                    val newScale = (oldScale * zoom).coerceIn(1f, 5f)

                    if (newScale > 1f || oldScale > 1f) {
                        val containerCenter = Offset(
                            containerSize.width.toFloat() / 2f,
                            containerSize.height.toFloat() / 2f
                        )
                        val focalDelta = centroid - containerCenter
                        val scaleCorrection = (oldScale - newScale) / oldScale
                        val centroidOffset = Offset(
                            focalDelta.x * scaleCorrection,
                            focalDelta.y * scaleCorrection
                        )

                        scale = newScale

                        if (newScale > 1f) {
                            val maxOffsetX = (containerSize.width.toFloat() / 2f) * (newScale - 1f)
                            val maxOffsetY = (containerSize.height.toFloat() / 2f) * (newScale - 1f)
                            offset = Offset(
                                x = (offset.x + pan.x + centroidOffset.x).coerceIn(-maxOffsetX, maxOffsetX),
                                y = (offset.y + pan.y + centroidOffset.y).coerceIn(-maxOffsetY, maxOffsetY)
                            )
                        } else {
                            offset = Offset.Zero
                        }
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
            }
    ) {
        Image(
            painter = rememberAsyncImagePainter(Uri.fromFile(imageFile)),
            contentDescription = imageName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center
        )
    }
}

private fun formatVideoTime(millis: Long): String {
    if (millis < 0) return "00:00"
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
