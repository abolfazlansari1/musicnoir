package com.noirmusic.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
private fun Modifier.dragToDismiss(offsetY: Animatable<Float, AnimationVector1D>, onClose: () -> Unit): Modifier {
    val scope = rememberCoroutineScope()
    val close by rememberUpdatedState(onClose)
    return pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragEnd = { if (offsetY.value > 260f) close() else scope.launch { offsetY.animateTo(0f, spring()) } },
            onDragCancel = { scope.launch { offsetY.animateTo(0f, spring()) } }
        ) { change, dy ->
            change.consume()
            scope.launch { offsetY.snapTo((offsetY.value + dy).coerceAtLeast(0f)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(onClose: () -> Unit) {
    val vm = LocalVm.current
    val ui = LocalUi.current
    val track = vm.current
    if (track == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val view = LocalView.current
    DisposableEffect(vm.keepScreenOn) {
        view.keepScreenOn = vm.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    val haptic = rememberHaptic()
    val offsetY = remember { Animatable(0f) }
    var showLyrics by remember { mutableStateOf(false) }
    val tint by animateColorAsState(vm.artColor ?: N.accent.copy(alpha = .45f), tween(700), label = "tint")
    val sleepActive by SleepTimer.remainingSec.collectAsState()
    val endOfTrack by SleepTimer.endOfTrack.collectAsState()

    Box(
        Modifier.fillMaxSize()
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .background(N.bg)
            .background(Brush.verticalGradient(colorStops = arrayOf(0f to tint, .85f to N.bg)))
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp)) {
            Row(Modifier.fillMaxWidth().dragToDismiss(offsetY, onClose), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Close player", tint = N.text, modifier = Modifier.size(30.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NOW PLAYING", color = N.text.copy(alpha = .7f), fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                    if (vm.queue.isNotEmpty()) Text("${vm.queueIndex + 1} of ${vm.queue.size}", color = N.muted, fontSize = 11.sp)
                }
                IconButton(onClick = { ui.menu(track) }) { Icon(Icons.Rounded.MoreVert, "More", tint = N.text) }
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)) {
                val side = if (maxWidth < maxHeight) maxWidth else maxHeight
                AnimatedContent(showLyrics, label = "artlyrics", transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) }) { lyricsMode ->
                    if (lyricsMode) LyricsView(track) else ArtworkPager(side, Modifier.dragToDismiss(offsetY, onClose))
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(track, label = "titles", modifier = Modifier.weight(1f), transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }) { t ->
                    Column {
                        Text(t.title, color = N.text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.basicMarquee())
                        Text(t.artist, color = N.text.copy(alpha = .7f), fontSize = 15.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp).basicMarquee())
                    }
                }
                FavoriteButton(vm.isFavorite(track.id), { vm.toggleFavorite(track) })
            }

            SeekSection()

            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                ControlIcon(Icons.Rounded.Shuffle, "Shuffle", vm.shuffle) { vm.toggleShuffle() }
                Icon(Icons.Rounded.SkipPrevious, "Previous", tint = N.text, modifier = Modifier.size(44.dp).clip(CircleShape).bounceClick { vm.previous() }.padding(4.dp))
                Box(Modifier.size(76.dp).clip(CircleShape).background(N.accent).bounceClick { haptic(); vm.togglePlay() }, contentAlignment = Alignment.Center) {
                    PlayPauseIcon(vm.isPlaying, N.onAccent, Modifier.size(42.dp))
                }
                Icon(Icons.Rounded.SkipNext, "Next", tint = N.text, modifier = Modifier.size(44.dp).clip(CircleShape).bounceClick { vm.next() }.padding(4.dp))
                ControlIcon(if (vm.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repeat", vm.repeat != Player.REPEAT_MODE_OFF) { vm.cycleRepeat() }
            }

            vm.upNext?.let { n ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { ui.showQueue() }.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("UP NEXT", color = N.muted, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Text("${n.title} • ${n.artist}", color = N.text.copy(alpha = .8f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                ControlIcon(Icons.Rounded.Lyrics, "Lyrics", showLyrics) { showLyrics = !showLyrics }
                ControlIcon(Icons.Rounded.Speed, "Speed and pitch", vm.speed != 1f || vm.pitch != 1f) { ui.showSpeed() }
                ControlIcon(Icons.Rounded.Timer, "Sleep timer", sleepActive > 0 || endOfTrack) { ui.showSleep() }
                ControlIcon(Icons.Rounded.Equalizer, "Equalizer", false) { ui.navigate(Route.Equalizer) }
                ControlIcon(Icons.Rounded.QueueMusic, "Queue", false) { ui.showQueue() }
            }
        }
    }
}

@Composable
private fun ControlIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, active: Boolean, onClick: () -> Unit) {
    val tint by animateColorAsState(if (active) N.accent else N.text.copy(alpha = .75f), label = "ctl")
    IconButton(onClick = onClick) { Icon(icon, desc, tint = tint) }
}

@Composable
private fun SeekSection() {
    val vm = LocalVm.current
    Column(Modifier.fillMaxWidth()) {
        SeekBar(progress = if (vm.duration > 0) vm.position.toFloat() / vm.duration else 0f, onSeek = { vm.seekFraction(it) }, color = N.text)
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(vm.position), color = N.text.copy(alpha = .65f), fontSize = 12.sp)
            Text(formatTime(vm.duration), color = N.text.copy(alpha = .65f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun ArtworkPager(side: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    val vm = LocalVm.current
    val queue = vm.queue
    val pager = rememberPagerState(initialPage = vm.queueIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0))) { queue.size }
    var userSwiped by remember { mutableStateOf(false) }
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragged) { if (dragged) userSwiped = true }
    LaunchedEffect(vm.queueIndex, queue.size) {
        val i = vm.queueIndex
        if (i in 0 until queue.size && pager.currentPage != i && !pager.isScrollInProgress) pager.animateScrollToPage(i)
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { p ->
            if (userSwiped) {
                userSwiped = false
                if (p != vm.queueIndex && p in 0 until vm.queue.size) vm.skipTo(p)
            }
        }
    }
    val scale by animateFloatAsState(if (vm.isPlaying) 1f else .86f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "artscale")
    HorizontalPager(pager, modifier.fillMaxSize(), pageSpacing = 12.dp, beyondViewportPageCount = 1) { page ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Artwork(
                queue.getOrNull(page),
                Modifier.size(side).graphicsLayer { scaleX = scale; scaleY = scale }.shadow(24.dp, RoundedCornerShape(28.dp), clip = false, ambientColor = Color.Black, spotColor = Color.Black),
                px = 720, corner = 28.dp
            )
        }
    }
}

@Composable
private fun LyricsView(track: MusicTrack) {
    val vm = LocalVm.current
    LaunchedEffect(track.id) { vm.loadLyrics(track) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importLyrics(track, uri) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val s = vm.lyrics) {
            LyricsState.Idle, LyricsState.Loading -> CircularProgressIndicator(color = N.accent)
            is LyricsState.Message -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.text, color = N.text.copy(alpha = .8f), fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Button(onClick = { vm.loadLyrics(track, true) }, colors = ButtonDefaults.buttonColors(containerColor = N.accent, contentColor = N.onAccent)) { Text("Try again", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Import .lrc or .txt file", color = N.text) }
            }
            is LyricsState.Loaded -> LyricsList(s) { picker.launch(arrayOf("*/*")) }
        }
    }
}

@Composable
private fun LyricsList(s: LyricsState.Loaded, onImport: () -> Unit) {
    val vm = LocalVm.current
    val listState = rememberLazyListState()
    val position = vm.position
    val active = if (s.synced) s.lines.indexOfLast { it.timeMs <= position + 300 } else -1
    LaunchedEffect(active) {
        if (active >= 0) listState.animateScrollToItem(active, -(listState.layoutInfo.viewportSize.height / 3))
    }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 90.dp)) {
        itemsIndexed(s.lines) { i, line ->
            val isActive = i == active
            val color by animateColorAsState(if (!s.synced || isActive) N.text else N.text.copy(alpha = .38f), tween(250), label = "lc")
            val sc by animateFloatAsState(if (isActive) 1.05f else 1f, spring(stiffness = Spring.StiffnessLow), label = "ls")
            Text(
                line.text.ifBlank { "♪" }, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .then(if (s.synced) Modifier.clickable { vm.seekTo(line.timeMs) } else Modifier)
                    .padding(vertical = 8.dp, horizontal = 2.dp)
                    .graphicsLayer { scaleX = sc; scaleY = sc; transformOrigin = TransformOrigin(0f, .5f) }
            )
        }
        item {
            Column(Modifier.padding(top = 16.dp)) {
                Text(if (s.synced) "Synced lyrics • tap a line to jump • ${s.source}" else "Plain lyrics • ${s.source}", color = N.muted, fontSize = 11.sp)
                Text("Import a different lyrics file", color = N.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onImport).padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val vm = LocalVm.current
    val track = vm.current ?: return
    val tint by animateColorAsState(vm.artColor ?: N.panel2, tween(600), label = "minitint")
    var dragX by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(N.panel)
            .background(Brush.horizontalGradient(listOf(tint.copy(alpha = .85f), tint.copy(alpha = .25f))))
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { if (dragX > 120) vm.previous() else if (dragX < -120) vm.next(); dragX = 0f },
                    onDragCancel = { dragX = 0f }
                ) { _, dx -> dragX += dx }
            }
            .clickable(onClick = onOpen)
    ) {
        Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(track, Modifier.size(46.dp), px = 160, corner = 12.dp)
            Spacer(Modifier.width(12.dp))
            AnimatedContent(track, label = "minititle", modifier = Modifier.weight(1f), transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }) { t ->
                Column {
                    Text(t.title, color = N.text, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(t.artist, color = N.text.copy(alpha = .7f), fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = { vm.togglePlay() }) { PlayPauseIcon(vm.isPlaying, N.text, Modifier.size(30.dp)) }
            IconButton(onClick = { vm.next() }) { Icon(Icons.Rounded.SkipNext, "Next", tint = N.text, modifier = Modifier.size(28.dp)) }
        }
        MiniProgress(Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun MiniProgress(modifier: Modifier) {
    val vm = LocalVm.current
    val f = if (vm.duration > 0) (vm.position.toFloat() / vm.duration).coerceIn(0f, 1f) else 0f
    Box(modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = .1f))) {
        Box(Modifier.fillMaxWidth(f).height(3.dp).background(N.accent))
    }
}
