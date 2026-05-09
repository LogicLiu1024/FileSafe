package cn.logicliu.filesafe.ui.screens.viewer

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.View
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import cn.logicliu.filesafe.data.entity.FileItemEntity
import cn.logicliu.filesafe.ui.screens.player.isVideoFile
import coil.compose.rememberAsyncImagePainter
import java.io.File
import java.util.concurrent.TimeUnit
import androidx.compose.ui.input.pointer.PointerInputScope

@OptIn(UnstableApi::class)
@Composable
fun MediaViewerScreen(
    mediaFileEntities: List<FileItemEntity>,
    initialIndex: Int,
    onNavigateBack: () -> Unit,
    onDecryptFile: suspend (Long) -> Result<File>
) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { mediaFileEntities.size }
    var decryptedFiles by remember { mutableStateOf<Map<Long, File>>(emptyMap()) }
    val currentEntity = remember(pagerState.currentPage, mediaFileEntities) {
        mediaFileEntities.getOrNull(pagerState.currentPage)
    }

    BackHandler(onBack = onNavigateBack)

    LaunchedEffect(pagerState.currentPage, pagerState.settledPage) {
        val current = pagerState.settledPage
        val pagesToLoad = ((current - 1).coerceAtLeast(0)..(current + 1).coerceAtMost(mediaFileEntities.size - 1))
            .filter { it in mediaFileEntities.indices }

        for (page in pagesToLoad) {
            val entity = mediaFileEntities[page]
            if (decryptedFiles.containsKey(entity.id)) continue

            val result = onDecryptFile(entity.id)
            result.onSuccess { file ->
                decryptedFiles = decryptedFiles + (entity.id to file)
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
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val entity = mediaFileEntities[page]
            val decryptedFile = decryptedFiles[entity.id]

            if (decryptedFile != null) {
                if (isVideoFile(entity.name)) {
                    VideoPage(
                        videoFile = decryptedFile,
                        videoName = entity.name,
                        onNavigateBack = onNavigateBack
                    )
                } else {
                    ImagePage(
                        imageFile = decryptedFile,
                        imageName = entity.name,
                        onNavigateBack = onNavigateBack
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

@OptIn(UnstableApi::class)
@Composable
private fun VideoPage(
    videoFile: File,
    videoName: String,
    onNavigateBack: () -> Unit
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
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
            .pointerInput(Unit) {
                detectZoomAndPan(
                    onScaleChange = { newScale -> scale = newScale },
                    onOffsetChange = { newOffset -> offset = newOffset },
                    currentScale = { scale }
                )
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
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
    onNavigateBack: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Image(
            painter = rememberAsyncImagePainter(Uri.fromFile(imageFile)),
            contentDescription = imageName,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .pointerInput(Unit) {
                    detectZoomAndPan(
                        onScaleChange = { newScale -> scale = newScale },
                        onOffsetChange = { newOffset -> offset = newOffset },
                        currentScale = { scale }
                    )
                },
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center
        )
    }
}

private suspend fun PointerInputScope.detectZoomAndPan(
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Offset) -> Unit,
    currentScale: () -> Float
) {
    awaitPointerEventScope {
        while (true) {
            var downEvent = awaitPointerEvent()
            while (downEvent.type != PointerEventType.Press) {
                downEvent = awaitPointerEvent()
            }
            val initialChanges = downEvent.changes
            val initialCentroid = if (initialChanges.size >= 2) {
                initialChanges.map { it.position }.reduce { acc, pos -> acc + pos } / initialChanges.size.toFloat()
            } else {
                initialChanges.first().position
            }
            val initialSpan = if (initialChanges.size >= 2) {
                initialChanges.map { (it.position - initialCentroid).getDistance() }.sum() / initialChanges.size
            } else {
                0f
            }
            val initialScale = currentScale()

            var previousCentroid = initialCentroid
            var previousSpan = initialSpan
            var currentZoomedScale = initialScale
            var totalOffset = Offset.Zero
            var isMultiTouch = initialChanges.size >= 2

            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Move) continue
                val changes = event.changes
                val allUp = changes.all { !it.pressed }

                if (allUp) break

                val currentCentroid = if (changes.size >= 2) {
                    changes.map { it.position }.reduce { acc, pos -> acc + pos } / changes.size.toFloat()
                } else {
                    changes.first().position
                }

                if (changes.size >= 2) {
                    if (!isMultiTouch) {
                        isMultiTouch = true
                        previousSpan = 0f
                        previousCentroid = currentCentroid
                        totalOffset = Offset.Zero
                    }

                    val currentSpan = changes.map { (it.position - currentCentroid).getDistance() }.sum() / changes.size

                    val zoomFactor = if (previousSpan > 0f && initialSpan > 0f) {
                        currentSpan / initialSpan
                    } else {
                        1f
                    }

                    currentZoomedScale = (initialScale * zoomFactor).coerceIn(1f, 4f)
                    onScaleChange(currentZoomedScale)

                    if (currentZoomedScale > 1f) {
                        val pan = Offset(
                            currentCentroid.x - previousCentroid.x,
                            currentCentroid.y - previousCentroid.y
                        )
                        if (previousSpan > 0f) {
                            totalOffset = Offset(
                                totalOffset.x + pan.x,
                                totalOffset.y + pan.y
                            )
                            val clampedOffset = Offset(
                                totalOffset.x.coerceIn(-500f * (currentZoomedScale - 1), 500f * (currentZoomedScale - 1)),
                                totalOffset.y.coerceIn(-500f * (currentZoomedScale - 1), 500f * (currentZoomedScale - 1))
                            )
                            onOffsetChange(clampedOffset)
                        }
                    } else {
                        totalOffset = Offset.Zero
                        onOffsetChange(Offset.Zero)
                    }

                    previousCentroid = currentCentroid
                    previousSpan = currentSpan

                    changes.forEach { it.consume() }
                } else if (currentScale() > 1f) {
                    val pan = Offset(
                        currentCentroid.x - previousCentroid.x,
                        currentCentroid.y - previousCentroid.y
                    )
                    totalOffset = Offset(
                        totalOffset.x + pan.x,
                        totalOffset.y + pan.y
                    )
                    val clampedOffset = Offset(
                        totalOffset.x.coerceIn(-500f * (currentScale() - 1), 500f * (currentScale() - 1)),
                        totalOffset.y.coerceIn(-500f * (currentScale() - 1), 500f * (currentScale() - 1))
                    )
                    onOffsetChange(clampedOffset)

                    previousCentroid = currentCentroid
                    changes.forEach { it.consume() }
                }
            }
        }
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
