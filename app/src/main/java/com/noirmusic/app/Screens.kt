package com.noirmusic.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.rounded.VisibilityOff
import kotlinx.coroutines.launch
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

class ListStates {
    val home = LazyListState()
    val songs = LazyListState()
    val albums = LazyGridState()
    val artists = LazyListState()
    val folders = LazyListState()
    val playlists = LazyListState()
    val search = LazyListState()
    val settings = LazyListState()
}

// ============================ HOME ============================

@Composable
fun HomeScreen(state: LazyListState) {
    val vm = LocalVm.current
    val ui = LocalUi.current
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when { hour < 5 -> "Good night"; hour < 12 -> "Good morning"; hour < 18 -> "Good afternoon"; else -> "Good evening" }
    val currentId = vm.current?.id
    LazyColumn(state = state, contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("NOIR MUSIC", color = N.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    Text(greeting, color = N.text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                }
                IconButton(onClick = { ui.navigate(Route.Equalizer) }) { Icon(Icons.Rounded.Equalizer, "Equalizer", tint = N.accent) }
            }
        }
        if (!vm.permissionGranted) {
            item { EmptyState(title = "Music access required", subtitle = "Allow audio access so Noir Music can find the songs on this device.", actionLabel = "Allow access", onAction = ui.requestPermission) }
            return@LazyColumn
        }
        if (vm.loading && vm.tracks.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(60.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = N.accent) } }
            return@LazyColumn
        }
        if (vm.tracks.isEmpty()) {
            item { EmptyState(title = "No music found", subtitle = "Add songs to this device, or lower the minimum duration in Settings.", actionLabel = "Rescan", onAction = { vm.loadLibrary() }) }
            return@LazyColumn
        }
        item { HeroCard() }
        item {
            val tiles = buildList<@Composable (Modifier) -> Unit> {
                add { m -> QuickTile("Shuffle all", songCount(vm.tracks.size), Icons.Rounded.Shuffle, true, m) { vm.playShuffled(vm.sortedTracks) } }
                if (vm.favoriteTracks.isNotEmpty()) add { m -> QuickTile("Favorites", songCount(vm.favoriteTracks.size), Icons.Rounded.Favorite, false, m) { ui.navigate(Route.Favorites) } }
                if (vm.recentTracks.isNotEmpty()) add { m -> QuickTile("Recently played", songCount(vm.recentTracks.size), Icons.Rounded.History, false, m) { ui.navigate(Route.Recent) } }
                if (vm.mostPlayed.isNotEmpty()) add { m -> QuickTile("Most played", songCount(vm.mostPlayed.size), Icons.Rounded.TrendingUp, false, m) { ui.navigate(Route.MostPlayed) } }
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tiles.chunked(2).forEach { rowTiles ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowTiles.forEach { t -> t(Modifier.weight(1f)) }
                        if (rowTiles.size == 1 && tiles.size > 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        if (vm.recentTracks.isNotEmpty()) {
            item { SectionHeader("Jump back in", action = "See all") { ui.navigate(Route.Recent) } }
            item {
                val list = vm.recentTracks.take(15)
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    itemsIndexed(list, key = { _, t -> "r${t.id}" }) { i, t -> TrackCard(t, t.id == currentId) { vm.play(list, i) } }
                }
            }
        }
        item { SectionHeader("Recently added") }
        item {
            val list = vm.recentlyAdded.take(15)
            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                itemsIndexed(list, key = { _, t -> "a${t.id}" }) { i, t -> TrackCard(t, t.id == currentId) { vm.play(list, i) } }
            }
        }
        if (vm.albums.isNotEmpty()) {
            item { SectionHeader("Albums") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(vm.albums.take(20), key = { "al${it.id}" }) { a -> AlbumCard(a, Modifier.width(138.dp)) { ui.navigate(Route.Album(a.id)) } }
                }
            }
        }
        if (vm.mostPlayed.isNotEmpty()) {
            item { SectionHeader("Most played", action = "See all") { ui.navigate(Route.MostPlayed) } }
            val top = vm.mostPlayed.take(5)
            itemsIndexed(top, key = { _, t -> "m${t.id}" }) { i, t ->
                TrackRow(t, t.id == currentId, vm.isPlaying, { vm.play(top, i) }, index = i)
            }
        }
    }
}

@Composable
private fun HeroCard() {
    val vm = LocalVm.current
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val track = vm.current ?: vm.recentTracks.firstOrNull() ?: return
    val isCurrent = vm.current?.id == track.id
    val accent by produceState<Color?>(null, track.id) { value = ArtCache.accentFor(ctx, track) }
    val tint by animateColorAsState(accent ?: N.panel2, tween(500), label = "herotint")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp).clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(tint, N.panel)))
            .bounceClick { if (isCurrent) ui.openPlayer() else vm.play(vm.recentTracks, 0, false) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(track, Modifier.size(104.dp), px = 320, corner = 22.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(if (isCurrent) "NOW PLAYING" else "CONTINUE LISTENING", color = N.text.copy(alpha = .7f), fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Text(track.title, color = N.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            Text(track.artist, color = N.text.copy(alpha = .7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .94f))
                    .bounceClick { if (isCurrent) vm.togglePlay() else vm.play(vm.recentTracks, 0, false) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayPauseIcon(isCurrent && vm.isPlaying, Color(0xFF14101C), Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isCurrent && vm.isPlaying) "Pause" else "Play", color = Color(0xFF14101C), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun QuickTile(title: String, subtitle: String, icon: ImageVector, highlight: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg = if (highlight) Modifier.background(Brush.linearGradient(listOf(N.accent, N.accent.copy(alpha = .55f)))) else Modifier.background(N.panel)
    val fg = if (highlight) N.onAccent else N.text
    Row(modifier.clip(RoundedCornerShape(20.dp)).then(bg).hairline(RoundedCornerShape(20.dp)).bounceClick(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(if (highlight) Color.White.copy(alpha = .25f) else N.panel2), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (highlight) N.onAccent else N.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = fg, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = if (highlight) N.onAccent.copy(alpha = .7f) else N.muted, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun TrackCard(track: MusicTrack, current: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(132.dp).bounceClick(onClick = onClick)) {
        Artwork(track, Modifier.size(132.dp), px = 320, corner = 18.dp)
        Spacer(Modifier.height(8.dp))
        Text(track.title, color = if (current) N.accent else N.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artist, color = N.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun AlbumCard(album: AlbumItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.bounceClick(onClick = onClick)) {
        Artwork(album.cover, Modifier.fillMaxWidth().height(138.dp), px = 360, corner = 18.dp)
        Spacer(Modifier.height(8.dp))
        Text(album.name, color = N.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${album.artist} • ${album.tracks.size}", color = N.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ============================ LIBRARY ============================

@Composable
fun LibraryScreen(states: ListStates, sub: Int, onSub: (Int) -> Unit) {
    val vm = LocalVm.current
    val ui = LocalUi.current
    val labels = listOf("Songs", "Albums", "Artists", "Folders", "Playlists")
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Library")
        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(labels) { i, l -> NoirChip(l, sub == i, { onSub(i) }) }
        }
        Spacer(Modifier.height(8.dp))
        if (!vm.permissionGranted) {
            EmptyState(title = "Music access required", subtitle = "Allow audio access to see your library.", actionLabel = "Allow access", onAction = ui.requestPermission)
            return
        }
        AnimatedContent(sub, label = "libsub", transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }) { s ->
            when (s) {
                0 -> SongsTab(states.songs)
                1 -> AlbumsTab(states.albums)
                2 -> ArtistsTab(states.artists)
                3 -> FoldersTab(states.folders)
                else -> PlaylistsTab(states.playlists)
            }
        }
    }
}

@Composable
private fun SongsTab(state: LazyListState) {
    val vm = LocalVm.current
    val songs = vm.sortedTracks
    val currentId = vm.current?.id
    if (songs.isEmpty()) {
        EmptyState(title = if (vm.loading) "Scanning…" else "No songs", subtitle = "Songs on this device appear here.", actionLabel = if (vm.loading) null else "Rescan", onAction = { vm.loadLibrary() })
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${songs.size} songs • ${SortKey.entries[vm.sortKey].label}", color = N.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            var open by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { open = true }) { Icon(Icons.Rounded.Sort, "Sort", tint = N.accent) }
                DropdownMenu(open, { open = false }, containerColor = N.panel2) {
                    SortKey.entries.forEachIndexed { i, k ->
                        DropdownMenuItem(
                            text = { Text(k.label, color = if (vm.sortKey == i) N.accent else N.text) },
                            onClick = { vm.setSort(i, if (vm.sortKey == i) !vm.sortAsc else true); open = false },
                            trailingIcon = { if (vm.sortKey == i) Icon(if (vm.sortAsc) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward, null, tint = N.accent, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
            }
        }
        PlayShuffleRow({ vm.play(songs, 0, false) }, { vm.playShuffled(songs) })
        val letters = remember(songs, vm.sortKey) { if (vm.sortKey in 0..2 && songs.size > 40) buildLetters(songs, vm.sortKey) else emptyList() }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(state = state, contentPadding = PaddingValues(bottom = 16.dp, end = if (letters.isEmpty()) 0.dp else 14.dp)) {
                itemsIndexed(songs, key = { _, t -> t.id }) { i, t ->
                    TrackRow(t, t.id == currentId, vm.isPlaying, { vm.play(songs, i, false) }, modifier = Modifier.animateItem())
                }
            }
            if (letters.isNotEmpty()) FastScroller(letters, state, Modifier.align(Alignment.CenterEnd))
        }
    }
}

private fun buildLetters(songs: List<MusicTrack>, key: Int): List<Pair<Char, Int>> {
    val out = LinkedHashMap<Char, Int>()
    songs.forEachIndexed { i, t ->
        val text = when (key) { 1 -> t.artist; 2 -> t.album; else -> t.title }
        val c = PlayerViewModel.normalize(text).firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.let { if (it.isDigit()) '#' else it } ?: '#'
        out.putIfAbsent(c, i)
    }
    return out.map { it.key to it.value }
}

@Composable
private fun FastScroller(letters: List<Pair<Char, Int>>, state: LazyListState, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var active by remember { mutableStateOf<Char?>(null) }
    var height by remember { mutableIntStateOf(1) }
    val shown = remember(letters) {
        val step = ((letters.size + 17) / 18).coerceAtLeast(1)
        letters.filterIndexed { i, _ -> i % step == 0 }
    }
    val pick: (Float) -> Unit = { y ->
        val i = ((y / height) * letters.size).toInt().coerceIn(0, letters.lastIndex)
        active = letters[i].first
        scope.launch { state.scrollToItem(letters[i].second) }
    }
    Box(
        modifier.fillMaxHeight().padding(vertical = 8.dp).width(26.dp).onSizeChanged { height = it.height.coerceAtLeast(1) }
            .pointerInput(letters) { detectTapGestures(onPress = { o -> pick(o.y); tryAwaitRelease(); active = null }) }
            .pointerInput(letters) {
                detectVerticalDragGestures(onDragStart = { o -> pick(o.y) }, onDragEnd = { active = null }, onDragCancel = { active = null }) { c, _ ->
                    c.consume(); pick(c.position.y)
                }
            }
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
            shown.forEach { (ch, _) -> Text(ch.toString(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (active == ch) N.accent else N.muted) }
        }
        active?.let {
            Box(Modifier.align(Alignment.CenterStart).offset(x = (-72).dp).size(54.dp).clip(RoundedCornerShape(16.dp)).background(N.accent), contentAlignment = Alignment.Center) {
                Text(it.toString(), color = N.onAccent, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun AlbumsTab(state: LazyGridState) {
    val vm = LocalVm.current
    val ui = LocalUi.current
    if (vm.albums.isEmpty()) { EmptyState(title = "No albums", subtitle = "Albums are built from your songs' tags."); return }
    var order by rememberSaveable { mutableIntStateOf(0) }
    var asList by rememberSaveable { mutableStateOf(false) }
    val labels = listOf("Name", "Artist", "Song count")
    val albums = remember(vm.albums, order) {
        when (order) {
            1 -> vm.albums.sortedWith(compareBy({ it.artist.lowercase() }, { it.name.lowercase() }))
            2 -> vm.albums.sortedByDescending { it.tracks.size }
            else -> vm.albums.sortedBy { it.name.lowercase() }
        }
    }
    LazyVerticalGrid(
        GridCells.Fixed(2), state = state, contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 16.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${albums.size} albums  •  Sort", color = N.muted, fontSize = 12.sp, modifier = Modifier.padding(end = 4.dp))
                labels.forEachIndexed { i, l -> NoirChip(l, order == i, { order = i }) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { asList = !asList }, modifier = Modifier.size(36.dp)) {
                    Icon(if (asList) Icons.Rounded.GridView else Icons.Rounded.ViewList, if (asList) "Grid view" else "List view", tint = N.accent)
                }
            }
        }
        if (asList) items(albums, key = { "l${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { a ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { ui.navigate(Route.Album(a.id)) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(a.cover, Modifier.size(60.dp), px = 160, corner = 14.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.name, color = N.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${a.artist}  •  ${songCount(a.tracks.size)}", color = N.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        } else items(albums, key = { it.id }) { a -> AlbumCard(a) { ui.navigate(Route.Album(a.id)) } }
    }
}

@Composable
private fun ArtistsTab(state: LazyListState) {
    val vm = LocalVm.current
    val ui = LocalUi.current
    if (vm.artists.isEmpty()) { EmptyState(title = "No artists", subtitle = "Artists are built from your songs' tags."); return }
    LazyColumn(state = state, contentPadding = PaddingValues(bottom = 16.dp)) {
        items(vm.artists, key = { it.name }) { a ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 1.dp).clip(RoundedCornerShape(16.dp)).clickable { ui.navigate(Route.Artist(a.name)) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(a.tracks.first(), Modifier.size(54.dp), px = 160, corner = 27.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.name, color = N.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${a.tracks.size} songs • ${a.albumCount} albums", color = N.muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun FoldersTab(state: LazyListState) {
    val vm = LocalVm.current
    val ui = LocalUi.current
    if (vm.folders.isEmpty()) { EmptyState(title = "No folders", subtitle = "Folders appear when songs are found."); return }
    LazyColumn(state = state, contentPadding = PaddingValues(bottom = 16.dp)) {
        items(vm.folders, key = { it.path }) { f ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 1.dp).clip(RoundedCornerShape(16.dp)).clickable { ui.navigate(Route.Folder(f.path)) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(N.panel2), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Folder, null, tint = N.accent) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.name, color = N.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${f.tracks.size} songs • ${f.path}", color = N.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { vm.hideFolder(f.path) }) { Icon(Icons.Rounded.VisibilityOff, "Hide folder", tint = N.muted) }
            }
        }
    }
}

@Composable
private fun PlaylistsTab(state: LazyListState) {
    val vm = LocalVm.current
    val ui = LocalUi.current
    var create by remember { mutableStateOf(false) }
    LazyColumn(state = state, contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(N.panel).bounceClick { create = true }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(N.accent), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, tint = N.onAccent) }
                Spacer(Modifier.width(12.dp))
                Text("New playlist", color = N.text, fontWeight = FontWeight.SemiBold)
            }
        }
        if (vm.playlists.isEmpty()) item { EmptyState(icon = Icons.Rounded.QueueMusic, title = "No playlists yet", subtitle = "Create one here, or choose Add to playlist from any song's menu.") }
        items(vm.playlists.keys.toList(), key = { it }) { name ->
            val ids = vm.playlists[name].orEmpty()
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 1.dp).clip(RoundedCornerShape(16.dp)).clickable { ui.navigate(Route.Playlist(name)) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val cover = ids.firstNotNullOfOrNull { id -> vm.tracks.firstOrNull { it.id == id } }
                if (cover != null) Artwork(cover, Modifier.size(54.dp), px = 160, corner = 14.dp)
                else Box(Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(N.panel2), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.QueueMusic, null, tint = N.accent) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, color = N.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(songCount(ids.size), color = N.muted, fontSize = 12.sp)
                }
            }
        }
    }
    if (create) TextInputDialog("New playlist", "", "Create", "Playlist name", { vm.createPlaylist(it) }, { create = false })
}

// ============================ COLLECTIONS ============================

@Composable
fun RouteScreen(route: Route) {
    val vm = LocalVm.current
    val ui = LocalUi.current
    when (route) {
        is Route.Album -> {
            val a = vm.albums.firstOrNull { it.id == route.id }
            if (a == null) MissingScreen("Album") else CollectionScreen(a.name, "${a.artist} • ${a.tracks.size} songs • ${formatTime(a.tracks.sumOf { it.duration })}", a.tracks, a.cover, showIndex = true)
        }
        is Route.Artist -> {
            val a = vm.artists.firstOrNull { it.name == route.name }
            if (a == null) MissingScreen("Artist") else CollectionScreen(a.name, "${a.tracks.size} songs • ${a.albumCount} albums", a.tracks, a.tracks.first(), round = true, headerExtra = {
                val albs = vm.albums.filter { al -> al.tracks.any { it.artist == a.name } }
                if (albs.size > 1) {
                    SectionHeader("Albums")
                    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(albs, key = { it.id }) { al -> AlbumCard(al, Modifier.width(130.dp)) { ui.navigate(Route.Album(al.id)) } }
                    }
                }
            })
        }
        is Route.Folder -> {
            val f = vm.folders.firstOrNull { it.path == route.path }
            if (f == null) MissingScreen("Folder") else CollectionScreen(f.name, "${f.tracks.size} songs • ${f.path}", f.tracks, f.tracks.first())
        }
        Route.Favorites -> CollectionScreen("Favorites", songCount(vm.favoriteTracks.size), vm.favoriteTracks, vm.favoriteTracks.firstOrNull(), emptyText = "Tap the heart on any song to add it here.")
        Route.Recent -> CollectionScreen("Recently played", songCount(vm.recentTracks.size), vm.recentTracks, vm.recentTracks.firstOrNull(), emptyText = "Songs you play will show up here.")
        Route.MostPlayed -> CollectionScreen("Most played", songCount(vm.mostPlayed.size), vm.mostPlayed, vm.mostPlayed.firstOrNull(), emptyText = "Play some songs to build this list.")
        Route.Equalizer -> EqualizerScreen(onBack = ui.back)
        is Route.Playlist -> PlaylistScreen(route.name)
    }
}

@Composable
private fun MissingScreen(what: String) {
    val ui = LocalUi.current
    Column(Modifier.fillMaxSize()) {
        TopBar("", ui.back)
        EmptyState(title = "$what not found", subtitle = "It may have been removed from the device.", actionLabel = "Go back", onAction = ui.back)
    }
}

@Composable
private fun PlaylistScreen(name: String) {
    val vm = LocalVm.current
    val ui = LocalUi.current
    if (!vm.playlists.containsKey(name)) { MissingScreen("Playlist"); return }
    val tracks = vm.playlistTracks(name)
    var rename by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    CollectionScreen(
        title = name, subtitle = songCount(tracks.size), tracks = tracks, cover = tracks.firstOrNull(),
        emptyText = "Add songs from the ⋮ menu on any track.", showIndex = true,
        actions = {
            IconButton(onClick = { rename = true }) { Icon(Icons.Rounded.Edit, "Rename", tint = N.text) }
            IconButton(onClick = { delete = true }) { Icon(Icons.Rounded.Delete, "Delete playlist", tint = N.text) }
        },
        trailing = { t -> { IconButton(onClick = { vm.removeFromPlaylist(name, t.id) }) { Icon(Icons.Rounded.Close, "Remove from playlist", tint = N.muted) } } }
    )
    if (rename) TextInputDialog("Rename playlist", name, "Save", "Playlist name", { vm.renamePlaylist(name, it) }, { rename = false })
    if (delete) AlertDialog(
        onDismissRequest = { delete = false }, containerColor = N.panel2,
        title = { Text("Delete playlist?", color = N.text) },
        text = { Text("\"$name\" will be removed. Your songs are not deleted.", color = N.muted) },
        confirmButton = { TextButton(onClick = { vm.deletePlaylist(name); delete = false; ui.back() }) { Text("Delete", color = N.accent) } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text("Cancel", color = N.muted) } }
    )
}

@Composable
fun CollectionScreen(
    title: String, subtitle: String, tracks: List<MusicTrack>, cover: MusicTrack?,
    round: Boolean = false, showIndex: Boolean = false, emptyText: String = "No songs here.",
    actions: @Composable () -> Unit = {},
    headerExtra: @Composable () -> Unit = {},
    trailing: ((MusicTrack) -> (@Composable () -> Unit))? = null
) {
    val vm = LocalVm.current
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val accent by produceState<Color?>(null, cover?.id) { value = cover?.let { ArtCache.accentFor(ctx, it) } }
    val tint by animateColorAsState((accent ?: N.panel2).copy(alpha = .6f), tween(600), label = "coltint")
    val currentId = vm.current?.id
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(colorStops = arrayOf(0f to tint, .5f to Color.Transparent)))) {
    Column(Modifier.fillMaxSize()) {
        TopBar("", ui.back) {
            if (tracks.isNotEmpty()) IconButton(onClick = { ui.addToPlaylist(tracks) }) { Icon(Icons.Rounded.QueueMusic, "Add all to playlist", tint = N.text) }
            actions()
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Artwork(cover, Modifier.size(190.dp), px = 520, corner = if (round) 95.dp else 26.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(title, color = N.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, color = N.muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
            }
            if (tracks.isEmpty()) {
                item { EmptyState(title = "Nothing here yet", subtitle = emptyText) }
            } else {
                item { Spacer(Modifier.height(8.dp)); PlayShuffleRow({ vm.play(tracks, 0, false) }, { vm.playShuffled(tracks) }) }
                item { headerExtra() }
                itemsIndexed(tracks, key = { i, t -> "${t.id}#$i" }) { i, t ->
                    TrackRow(t, t.id == currentId, vm.isPlaying, { vm.play(tracks, i, false) }, modifier = Modifier.animateItem(), index = if (showIndex) i else null, trailing = trailing?.invoke(t))
                }
            }
        }
    }
    }
}

@Composable
fun TextInputDialog(title: String, initial: String, confirm: String, label: String, onConfirm: (String) -> Boolean, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = N.panel2,
        title = { Text(title, color = N.text) },
        text = {
            OutlinedTextField(
                text, { text = it; error = false }, label = { Text(label) }, singleLine = true, isError = error,
                supportingText = { if (error) Text("Enter a unique name") }, shape = RoundedCornerShape(14.dp)
            )
        },
        confirmButton = { TextButton(onClick = { if (onConfirm(text)) onDismiss() else error = true }) { Text(confirm, color = N.accent, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = N.muted) } }
    )
}
