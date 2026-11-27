package com.noirmusic.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddToQueue
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

class UiActions(
    val navigate: (Route) -> Unit,
    val back: () -> Unit,
    val openPlayer: () -> Unit,
    val menu: (MusicTrack) -> Unit,
    val addToPlaylist: (List<MusicTrack>) -> Unit,
    val showQueue: () -> Unit,
    val showSleep: () -> Unit,
    val showSpeed: () -> Unit,
    val requestPermission: () -> Unit
)

val LocalVm = staticCompositionLocalOf<PlayerViewModel> { error("PlayerViewModel not provided") }
val LocalUi = staticCompositionLocalOf<UiActions> { error("UiActions not provided") }

/** Click with a subtle spring "press" scale. */
@Composable
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .95f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "press")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
}

@Composable
fun EqBars(color: Color, playing: Boolean, modifier: Modifier = Modifier.size(18.dp)) {
    val t = rememberInfiniteTransition(label = "eqbars")
    val a by t.animateFloat(.25f, 1f, infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(.35f, 1f, infiniteRepeatable(tween(380, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    val c by t.animateFloat(.2f, .9f, infiniteRepeatable(tween(660, easing = LinearEasing), RepeatMode.Reverse), label = "c")
    Canvas(modifier) {
        val w = size.width / 5f
        listOf(a, b, c).forEachIndexed { i, h ->
            val hh = size.height * (if (playing) h else .25f)
            drawRoundRect(color, Offset(i * 2 * w, size.height - hh), Size(w, hh), CornerRadius(w / 2))
        }
    }
}

fun Modifier.hairline(shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.border(1.dp, Color.White.copy(alpha = .07f), shape)

@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title, color = N.text, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.5).sp,
        modifier = modifier.padding(start = 20.dp, top = 20.dp, bottom = 12.dp)
    )
}

@Composable
fun rememberHaptic(): () -> Unit {
    val vm = LocalVm.current
    val h = LocalHapticFeedback.current
    return remember(vm.haptics, h) { { if (vm.haptics) h.performHapticFeedback(HapticFeedbackType.LongPress) } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: MusicTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int? = null,
    selectable: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    val vm = LocalVm.current
    val ui = LocalUi.current
    val haptic = rememberHaptic()
    val selected = selectable && vm.selection.any { it.id == track.id }
    val bg by animateColorAsState(
        when { selected -> N.accent.copy(alpha = .24f); isCurrent -> N.accent.copy(alpha = .13f); else -> Color.Transparent }, label = "rowbg"
    )
    Row(
        modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(16.dp)).background(bg)
            .combinedClickable(
                onClick = { if (selectable && vm.selecting) vm.toggleSelect(track) else onClick() },
                onLongClick = if (selectable) ({ haptic(); vm.toggleSelect(track) }) else null
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (index != null) {
            Text("${index + 1}", color = N.muted, fontSize = 12.sp, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
        }
        Box(Modifier.size(56.dp)) {
            Artwork(track, Modifier.fillMaxSize(), px = 160, corner = 13.dp)
            if (selected) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(13.dp)).background(N.accent.copy(alpha = .85f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, null, tint = N.onAccent, modifier = Modifier.size(26.dp))
                }
            } else if (isCurrent) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(13.dp)).background(Color.Black.copy(alpha = .45f)), contentAlignment = Alignment.Center) {
                    EqBars(N.accentSoft, isPlaying, Modifier.size(20.dp))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = if (isCurrent) N.accent else N.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${track.artist}  •  ${formatTime(track.duration)}", color = N.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing() else IconButton(onClick = { ui.menu(track) }) { Icon(Icons.Rounded.MoreVert, "More", tint = N.muted) }
    }
}

@Composable
fun SelectionBar(modifier: Modifier = Modifier) {
    val vm = LocalVm.current
    val ui = LocalUi.current
    val sel = vm.selection
    Row(
        modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).clip(RoundedCornerShape(22.dp))
            .background(N.panel2).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { vm.clearSelection() }) { Icon(Icons.Rounded.Close, "Cancel selection", tint = N.text) }
        Text("${sel.size} selected", color = N.text, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = { vm.play(sel, 0, false); vm.clearSelection() }) { Icon(Icons.Rounded.PlayArrow, "Play", tint = N.accent) }
        IconButton(onClick = { vm.playNextAll(sel); vm.clearSelection() }) { Icon(Icons.Rounded.PlaylistPlay, "Play next", tint = N.text) }
        IconButton(onClick = { vm.addAllToQueue(sel); vm.clearSelection() }) { Icon(Icons.Rounded.AddToQueue, "Add to queue", tint = N.text) }
        IconButton(onClick = { ui.addToPlaylist(sel); vm.clearSelection() }) { Icon(Icons.Rounded.PlaylistAdd, "Add to playlist", tint = N.text) }
        IconButton(onClick = { vm.favoriteAll(sel); vm.clearSelection() }) { Icon(Icons.Rounded.Favorite, "Favorite", tint = N.text) }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = N.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        if (action != null) Text(action, color = N.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(6.dp))
    }
}

@Composable
fun PlayShuffleRow(onPlay: () -> Unit, onShuffle: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = onPlay, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = N.accent, contentColor = N.onAccent)) {
            Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Play", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = onShuffle, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Rounded.Shuffle, null, tint = N.accent); Spacer(Modifier.width(6.dp)); Text("Shuffle", color = N.text, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector = Icons.Rounded.LibraryMusic, title: String, subtitle: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(N.panel2), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = N.accent, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, color = N.text, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = N.muted, fontSize = 13.sp, textAlign = TextAlign.Center)
        if (actionLabel != null) {
            Spacer(Modifier.height(18.dp))
            Button(onClick = onAction, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = N.accent, contentColor = N.onAccent)) { Text(actionLabel, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun NoirChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(if (selected) N.accent else N.panel2, label = "chipbg")
    val fg by animateColorAsState(if (selected) N.onAccent else N.muted, label = "chipfg")
    Box(modifier.clip(RoundedCornerShape(50)).background(bg).bounceClick(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun TopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back", tint = N.text) }
        Text(title, color = N.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun FavoriteButton(favorite: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 26.dp) {
    IconButton(onClick = onClick, modifier = modifier) {
        AnimatedContent(
            favorite, label = "fav",
            transitionSpec = { (scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) + fadeIn()) togetherWith (scaleOut() + fadeOut()) }
        ) { fav ->
            Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (fav) "Remove from favorites" else "Add to favorites", tint = if (fav) N.accent else N.muted, modifier = Modifier.size(size))
        }
    }
}

@Composable
fun PlayPauseIcon(playing: Boolean, tint: Color, modifier: Modifier = Modifier) {
    AnimatedContent(playing, label = "pp", transitionSpec = { (scaleIn(tween(160)) + fadeIn(tween(160))) togetherWith (scaleOut(tween(120)) + fadeOut(tween(120))) }) { p ->
        Icon(if (p) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (p) "Pause" else "Play", tint = tint, modifier = modifier)
    }
}

@Composable
fun SeekBar(progress: Float, onSeek: (Float) -> Unit, modifier: Modifier = Modifier, color: Color = N.accent) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val seek by rememberUpdatedState(onSeek)
    val shown = if (dragging) dragValue else progress.coerceIn(0f, 1f)
    val thumb by animateDpAsState(if (dragging) 10.dp else 6.dp, label = "thumb")
    val track = N.text.copy(alpha = .16f)
    Canvas(
        modifier.fillMaxWidth().height(34.dp)
            .pointerInput(Unit) { detectTapGestures { o -> seek((o.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragging = true; dragValue = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { seek(dragValue); dragging = false },
                    onDragCancel = { dragging = false }
                ) { change, _ -> dragValue = (change.position.x / size.width).coerceIn(0f, 1f) }
            }
    ) {
        val h = 5.dp.toPx()
        val y = size.height / 2
        drawRoundRect(track, Offset(0f, y - h / 2), Size(size.width, h), CornerRadius(h / 2))
        drawRoundRect(color, Offset(0f, y - h / 2), Size(size.width * shown, h), CornerRadius(h / 2))
        drawCircle(Color.White, thumb.toPx(), Offset(size.width * shown, y))
    }
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "-"
    val mb = bytes / 1048576.0
    return if (mb >= 1) String.format(Locale.US, "%.1f MB", mb) else String.format(Locale.US, "%.0f KB", bytes / 1024.0)
}

fun songCount(n: Int) = if (n == 1) "1 song" else "$n songs"
