package com.noirmusic.app

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddToQueue
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun MenuItem(icon: ImageVector, label: String, tint: Color = N.text, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (tint == N.text) N.accent else tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(label, color = tint, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackMenuSheet(track: MusicTrack, onDismiss: () -> Unit, onInfo: () -> Unit, onDelete: () -> Unit) {
    val vm = LocalVm.current
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = N.panel) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(track, Modifier.size(56.dp), px = 160, corner = 14.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = N.text, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${track.artist} • ${track.album}", color = N.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = N.panel2)
            MenuItem(Icons.Rounded.PlaylistPlay, "Play next") { vm.playNext(track); onDismiss() }
            MenuItem(Icons.Rounded.AddToQueue, "Add to queue") { vm.addToQueue(track); onDismiss() }
            MenuItem(Icons.Rounded.PlaylistAdd, "Add to playlist") { onDismiss(); ui.addToPlaylist(listOf(track)) }
            val fav = vm.isFavorite(track.id)
            MenuItem(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (fav) "Remove from favorites" else "Add to favorites") { vm.toggleFavorite(track); onDismiss() }
            MenuItem(Icons.Rounded.Album, "Go to album") { onDismiss(); ui.navigate(Route.Album(track.albumKey)) }
            MenuItem(Icons.Rounded.Person, "Go to artist") { onDismiss(); ui.navigate(Route.Artist(track.artist)) }
            MenuItem(Icons.Rounded.Share, "Share") {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = track.mime.ifBlank { "audio/*" }
                    putExtra(Intent.EXTRA_STREAM, track.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { ctx.startActivity(Intent.createChooser(send, "Share song").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                onDismiss()
            }
            MenuItem(Icons.Rounded.Info, "Details") { onDismiss(); onInfo() }
            if (vm.canDelete()) MenuItem(Icons.Rounded.Delete, "Delete from device", tint = Color(0xFFFF6B6B)) { onDismiss(); onDelete() }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun InfoDialog(track: MusicTrack, onDismiss: () -> Unit) {
    val rows = listOf(
        "Title" to track.title, "Artist" to track.artist, "Album" to track.album,
        "Year" to (if (track.year > 0) track.year.toString() else "-"),
        "Duration" to formatTime(track.duration), "Format" to track.mime.ifBlank { "-" },
        "Size" to formatSize(track.size), "Folder" to track.folder,
        "Added" to (if (track.dateAdded > 0) DateFormat.getDateInstance().format(Date(track.dateAdded * 1000)) else "-")
    )
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = N.panel2,
        title = { Text("Details", color = N.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { (k, v) ->
                    Column { Text(k, color = N.muted, fontSize = 11.sp); Text(v, color = N.text, fontSize = 14.sp) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = N.accent) } }
    )
}

@Composable
fun DeleteDialog(track: MusicTrack, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = N.panel2,
        title = { Text("Delete song?", color = N.text) },
        text = { Text("\"${track.title}\" will be permanently deleted from this device.", color = N.muted) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text("Delete", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = N.muted) } }
    )
}

@Composable
fun PlaylistPicker(tracks: List<MusicTrack>, onDismiss: () -> Unit) {
    val vm = LocalVm.current
    var create by remember { mutableStateOf(false) }
    if (create) {
        TextInputDialog("New playlist", "", "Create", "Playlist name", { vm.createPlaylist(it, tracks) }, { create = false; onDismiss() })
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = N.panel2,
        title = { Text(if (tracks.size == 1) "Add to playlist" else "Add ${tracks.size} songs to playlist", color = N.text) },
        text = {
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                item {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { create = true }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Add, null, tint = N.accent); Spacer(Modifier.width(12.dp)); Text("New playlist", color = N.accent, fontWeight = FontWeight.Bold)
                    }
                }
                items(vm.playlists.keys.toList(), key = { it }) { name ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.addToPlaylist(name, tracks); onDismiss() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.QueueMusic, null, tint = N.muted); Spacer(Modifier.width(12.dp))
                        Text(name, color = N.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${vm.playlists[name]?.size ?: 0}", color = N.muted, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = N.muted) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(onDismiss: () -> Unit) {
    val vm = LocalVm.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state = rememberLazyListState()
    LaunchedEffect(Unit) { if (vm.queueIndex > 2) state.scrollToItem(vm.queueIndex - 2) }
    var saving by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = N.panel) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.85f).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Queue", color = N.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text(songCount(vm.queue.size), color = N.muted, fontSize = 12.sp)
                }
                TextButton(onClick = { saving = true }, enabled = vm.queue.isNotEmpty()) { Text("Save", color = if (vm.queue.isNotEmpty()) N.accent else N.muted) }
                TextButton(onClick = { vm.clearUpcoming() }, enabled = vm.queueIndex < vm.queue.lastIndex) { Text("Clear upcoming", color = if (vm.queueIndex < vm.queue.lastIndex) N.accent else N.muted) }
            }
            if (vm.queue.isEmpty()) {
                EmptyState(icon = Icons.Rounded.QueueMusic, title = "Queue is empty", subtitle = "Play a song or add some with Add to queue.")
            } else LazyColumn(state = state) {
                itemsIndexed(vm.queue, key = { i, t -> "${t.id}#$i" }) { i, t ->
                    val isCurrent = i == vm.queueIndex
                    var dragY by remember { mutableFloatStateOf(0f) }
                    var dragging by remember { mutableStateOf(false) }
                    val rowPx = with(LocalDensity.current) { 70.dp.toPx() }
                    val lastIndex = vm.queue.lastIndex
                    TrackRow(
                        t, isCurrent, vm.isPlaying, { vm.skipTo(i) },
                        modifier = (if (dragging) Modifier else Modifier.animateItem())
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer { translationY = dragY; if (dragging) { scaleX = 1.03f; scaleY = 1.03f; shadowElevation = 24f } },
                        selectable = false,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { vm.removeFromQueue(i) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.Close, "Remove", tint = N.muted, modifier = Modifier.size(18.dp)) }
                                Box(
                                    Modifier.size(40.dp).pointerInput(i, lastIndex) {
                                        detectDragGestures(
                                            onDragStart = { dragging = true },
                                            onDragEnd = {
                                                val steps = Math.round(dragY / rowPx)
                                                val target = (i + steps).coerceIn(0, lastIndex)
                                                dragging = false; dragY = 0f
                                                if (target != i) vm.moveInQueue(i, target)
                                            },
                                            onDragCancel = { dragging = false; dragY = 0f },
                                            onDrag = { change, amount -> change.consume(); dragY += amount.y }
                                        )
                                    },
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Rounded.DragHandle, "Drag to reorder", tint = N.muted, modifier = Modifier.size(22.dp)) }
                            }
                        }
                    )
                }
            }
        }
    }
    if (saving) TextInputDialog("Save queue as playlist", "", "Save", "Playlist name", { vm.createPlaylist(it, vm.queue) }, { saving = false })
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SleepSheet(onDismiss: () -> Unit) {
    val remaining by SleepTimer.remainingSec.collectAsState()
    val endOfTrack by SleepTimer.endOfTrack.collectAsState()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = N.panel) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sleep timer", color = N.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                when { endOfTrack -> "Playback stops after this song"; remaining > 0 -> "Playback stops in ${formatTime(remaining * 1000L)}"; else -> "Choose when playback should stop" },
                color = if (remaining > 0 || endOfTrack) N.accent else N.muted, fontSize = 13.sp
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15, 30, 45, 60, 90).forEach { m ->
                    NoirChip("$m min", remaining in (m * 60 - 59)..(m * 60), { SleepTimer.startMinutes(m); onDismiss() })
                }
                NoirChip("End of song", endOfTrack, { SleepTimer.startEndOfTrack(); onDismiss() })
            }
            if (remaining > 0 || endOfTrack) {
                TextButton(onClick = { SleepTimer.cancel(); onDismiss() }) { Text("Turn off timer", color = N.accent, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun SpeedDialog(onDismiss: () -> Unit) {
    val vm = LocalVm.current
    val colors = SliderDefaults.colors(thumbColor = N.accent, activeTrackColor = N.accent, inactiveTrackColor = N.panel)
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = N.panel2,
        title = { Text("Speed and pitch", color = N.text) },
        text = {
            Column {
                Text("Speed  ${"%.2f".format(Locale.US, vm.speed)}x", color = N.text, fontWeight = FontWeight.SemiBold)
                Slider(vm.speed, { vm.setSpeedPitch(Math.round(it * 20) / 20f, vm.pitch) }, valueRange = 0.5f..2f, steps = 29, colors = colors)
                Text("Pitch  ${"%.2f".format(Locale.US, vm.pitch)}x", color = N.text, fontWeight = FontWeight.SemiBold)
                Slider(vm.pitch, { vm.setSpeedPitch(vm.speed, Math.round(it * 20) / 20f) }, valueRange = 0.5f..2f, steps = 29, colors = colors)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = N.accent, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { vm.setSpeedPitch(1f, 1f) }) { Text("Reset", color = N.muted) } }
    )
}
