package com.noirmusic.app

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.Locale
import kotlin.math.max

private val Bg = Color(0xFF08070D)
private val Panel = Color(0xFF15121F)
private val Panel2 = Color(0xFF21182E)
private val Purple = Color(0xFFB879FF)
private val PurpleBright = Color(0xFFD7A8FF)
private val Muted = Color(0xFF9B91AA)
private val White = Color(0xFFF8F5FC)

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync().also { future ->
            future.addListener({ runCatching { controller = future.get() } }, MoreExecutors.directExecutor())
        }
        setContent { NoirTheme { NoirApp(getController = { controller }) } }
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        super.onDestroy()
    }
}

@Composable
private fun NoirTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Purple, secondary = PurpleBright, background = Bg, surface = Panel, onBackground = White, onSurface = White), content = content)
}

@Composable
private fun NoirApp(getController: () -> MediaController?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("noir_preferences", android.content.Context.MODE_PRIVATE) }
    var tracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var permissionGranted by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf<MusicTrack?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var showPlayer by remember { mutableStateOf(false) }
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()) }
    var playlists by remember { mutableStateOf(prefs.getString("playlists", "{}") ?: "{}") }
    var playlistDialog by remember { mutableStateOf(false) }
    var pendingPlaylistTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var newPlaylistName by remember { mutableStateOf("") }
    var sleepMinutes by remember { mutableIntStateOf(0) }
    var sleepRemaining by remember { mutableIntStateOf(0) }
    var lyrics by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }
    var lyricsLoading by remember { mutableStateOf(false) }
    var lyricsMessage by remember { mutableStateOf("No lyrics loaded") }

    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    fun refreshLibrary() { scope.launch { tracks = withContext(Dispatchers.IO) { MusicRepository(context).loadAudio() } } }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        if (granted) refreshLibrary()
    }

    LaunchedEffect(Unit) {
        permissionGranted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (permissionGranted) refreshLibrary() else permissionLauncher.launch(permission)
    }

    LaunchedEffect(Unit) {
        while (true) {
            val p = getController()
            if (p != null) {
                isPlaying = p.isPlaying
                position = max(0L, p.currentPosition)
                duration = max(0L, p.duration.takeIf { it > 0 } ?: (current?.duration ?: 0L))
                if (p.currentMediaItem != null) {
                    val playingUri = p.currentMediaItem?.localConfiguration?.uri?.toString()
                    current = tracks.firstOrNull { it.uri.toString() == playingUri } ?: current
                }
            }
            delay(400)
        }
    }

    LaunchedEffect(sleepMinutes) {
        if (sleepMinutes > 0) {
            sleepRemaining = sleepMinutes * 60
            while (sleepRemaining > 0) { delay(1000); sleepRemaining-- }
            getController()?.pause()
            sleepMinutes = 0
        }
    }

    fun playTrack(track: MusicTrack, queue: List<MusicTrack> = tracks) {
        val player = getController() ?: return
        val list = queue.ifEmpty { listOf(track) }
        val items = list.map { item ->
            MediaItem.Builder().setUri(item.uri).setMediaId(item.id.toString())
                .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).setArtist(item.artist).setAlbumTitle(item.album).apply { item.artworkUri?.let { setArtworkUri(it) } }.build()).build()
        }
        val index = list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        player.setMediaItems(items, index, 0L)
        player.prepare(); player.play()
        current = track
        prefs.edit().putString("last_played", track.id.toString()).apply()
    }

    fun toggleFavorite(track: MusicTrack) {
        favorites = if (track.id.toString() in favorites) favorites - track.id.toString() else favorites + track.id.toString()
        prefs.edit().putStringSet("favorites", favorites).apply()
    }

    val visibleTracks = remember(tracks, query, tab, favorites) {
        tracks.filter { t ->
            (query.isBlank() || t.title.contains(query, true) || t.artist.contains(query, true) || t.album.contains(query, true)) &&
                (tab != 2 || t.id.toString() in favorites)
        }
    }

    Scaffold(containerColor = Bg, bottomBar = {
        NavigationBar(containerColor = Color(0xFF100D17), contentColor = Purple, tonalElevation = 0.dp) {
            val items = listOf("Home" to Icons.Default.Home, "Library" to Icons.Default.LibraryMusic, "Favorites" to Icons.Default.Favorite, "Equalizer" to Icons.Default.Equalizer, "Settings" to Icons.Default.Settings)
            items.forEachIndexed { i, pair ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(pair.second, null) }, label = { Text(pair.first, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = PurpleBright, selectedTextColor = PurpleBright, indicatorColor = Panel2, unselectedIconColor = Muted, unselectedTextColor = Muted))
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(Brush.verticalGradient(listOf(Color(0xFF21112F), Bg, Bg))).padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("NOIR MUSIC", color = Purple, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    Text(listOf("Good evening", "Your library", "Your favorites", "Sound studio", "Personalize")[tab], color = White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                }
                Box(Modifier.size(44.dp).clip(CircleShape).background(Panel2), contentAlignment = Alignment.Center) { Icon(Icons.Default.Headphones, null, tint = PurpleBright) }
            }
            Spacer(Modifier.height(18.dp))
            if (tab != 3 && tab != 4) {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search songs, artists, albums...", color = Muted) }, leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) }, trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, null) } }, singleLine = true, shape = RoundedCornerShape(18.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple, unfocusedBorderColor = Color(0xFF3A3047), focusedContainerColor = Panel, unfocusedContainerColor = Panel))
                Spacer(Modifier.height(16.dp))
            }
            when (tab) {
                0, 1, 2 -> {
                    if (tab == 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatCard("SONGS", tracks.size.toString(), Modifier.weight(1f))
                            StatCard("ALBUMS", tracks.map { it.albumId }.distinct().size.toString(), Modifier.weight(1f))
                            StatCard("ARTISTS", tracks.map { it.artist }.distinct().size.toString(), Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(18.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (tab == 2) "FAVORITES" else if (tab == 1) "ALL TRACKS" else "RECENTLY ADDED", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
                        Text("${visibleTracks.size} tracks", color = Muted, fontSize = 11.sp)
                        IconButton(onClick = { refreshLibrary() }) { Icon(Icons.Default.Refresh, null, tint = Purple) }
                    }
                    if (!permissionGranted) EmptyState("Music access required", "Allow audio access to scan your local library.") { permissionLauncher.launch(permission) }
                    else if (visibleTracks.isEmpty()) EmptyState("Nothing here yet", if (tab == 2) "Tap the heart on any song to save it." else "Add music to your device and refresh the library.") { refreshLibrary() }
                    else LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                        items(visibleTracks, key = { it.id }) { track ->
                            TrackRow(track, track.id.toString() in favorites, { playTrack(track, visibleTracks) }, { toggleFavorite(track) }, { pendingPlaylistTrack = track })
                        }
                    }
                }
                3 -> EqualizerScreen(PlaybackService.activeAudioSessionId)
                4 -> SettingsScreen(
                    sleepMinutes = sleepMinutes, onSleepChange = { sleepMinutes = it }, sleepRemaining = sleepRemaining,
                    onRefresh = { refreshLibrary() }, onCreatePlaylist = { playlistDialog = true },
                    playlistEntries = runCatching { val j = org.json.JSONObject(playlists); j.keys().asSequence().map { key -> key to (j.optJSONArray(key)?.length() ?: 0) }.toList() }.getOrDefault(emptyList()),
                    onPlayPlaylist = { name -> val j = org.json.JSONObject(playlists); val ids = j.optJSONArray(name) ?: JSONArray(); val list = (0 until ids.length()).mapNotNull { idIndex -> tracks.firstOrNull { it.id.toString() == ids.optString(idIndex) } }; if (list.isNotEmpty()) { playTrack(list.first(), list); showPlayer = true } }
                )
            }
            current?.let { track ->
                Spacer(Modifier.height(8.dp))
                MiniPlayer(track, isPlaying, position, duration, onOpen = { showPlayer = true }, onToggle = { if (getController()?.isPlaying == true) getController()?.pause() else getController()?.play() }, onNext = { getController()?.seekToNextMediaItem() })
            }
        }
    }

    if (pendingPlaylistTrack != null) {
        val names = runCatching { val j = org.json.JSONObject(playlists); j.keys().asSequence().toList() }.getOrDefault(emptyList())
        AlertDialog(onDismissRequest = { pendingPlaylistTrack = null }, containerColor = Panel,
            title = { Text("Add to playlist", color = White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (names.isEmpty()) Text("Create a playlist in Settings first.", color = Muted)
                    names.forEach { name ->
                        TextButton(onClick = {
                            val json = org.json.JSONObject(playlists)
                            val ids = json.optJSONArray(name) ?: JSONArray()
                            val id = pendingPlaylistTrack!!.id.toString()
                            if ((0 until ids.length()).none { ids.optString(it) == id }) ids.put(id)
                            json.put(name, ids); playlists = json.toString()
                            prefs.edit().putString("playlists", playlists).apply()
                            pendingPlaylistTrack = null
                        }) { Icon(Icons.Default.QueueMusic, null); Spacer(Modifier.width(8.dp)); Text(name) }
                    }
                }
            }, confirmButton = { TextButton(onClick = { pendingPlaylistTrack = null }) { Text("Close") } })
    }

    if (playlistDialog) AlertDialog(onDismissRequest = { playlistDialog = false }, containerColor = Panel, title = { Text("Create playlist") }, text = { OutlinedTextField(newPlaylistName, { newPlaylistName = it }, label = { Text("Playlist name") }, singleLine = true) }, confirmButton = { TextButton(onClick = {
        if (newPlaylistName.isNotBlank()) {
            val json = org.json.JSONObject(playlists); json.put(newPlaylistName.trim(), JSONArray())
            playlists = json.toString(); prefs.edit().putString("playlists", playlists).apply(); newPlaylistName = ""
        }; playlistDialog = false
    }) { Text("Create") } }, dismissButton = { TextButton(onClick = { playlistDialog = false }) { Text("Cancel") } })

    if (showPlayer && current != null) {
        PlayerSheet(track = current!!, playing = isPlaying, position = position, duration = duration, favorite = current!!.id.toString() in favorites,
            lyrics = lyrics, lyricsLoading = lyricsLoading, lyricsMessage = lyricsMessage,
            onDismiss = { showPlayer = false }, onToggle = { if (getController()?.isPlaying == true) getController()?.pause() else getController()?.play() },
            onNext = { getController()?.seekToNextMediaItem(); current = tracks.firstOrNull { it.uri == getController()?.currentMediaItem?.localConfiguration?.uri } ?: current },
            onPrevious = { getController()?.seekToPreviousMediaItem(); current = tracks.firstOrNull { it.uri == getController()?.currentMediaItem?.localConfiguration?.uri } ?: current },
            onSeek = { getController()?.seekTo(it) }, onFavorite = { toggleFavorite(current!!) },
            onShuffle = { getController()?.shuffleModeEnabled = !(getController()?.shuffleModeEnabled ?: false) },
            onRepeat = { val p = getController(); if (p != null) p.repeatMode = (p.repeatMode + 1) % 3 },
            onLoadLyrics = {
                val t = current!!; lyricsLoading = true; lyricsMessage = "Searching LRCLIB…"
                scope.launch {
                    val result = withContext(Dispatchers.IO) { fetchLyrics(t) }
                    lyrics = result.first; lyricsMessage = result.second; lyricsLoading = false
                }
            }, onSpeed = { speed -> getController()?.setPlaybackParameters(androidx.media3.common.PlaybackParameters(speed, 1f)) }
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Panel.copy(alpha = .92f)).padding(12.dp)) {
        Text(value, color = White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
        Text(label, color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun AlbumArt(track: MusicTrack, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFF51316C), Color(0xFF1A1425)))), contentAlignment = Alignment.Center) {
        if (track.artworkUri != null) {
            AsyncImage(model = track.artworkUri, contentDescription = "${track.album} artwork", modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                placeholder = androidx.compose.ui.graphics.painter.ColorPainter(Panel2), error = androidx.compose.ui.graphics.painter.ColorPainter(Panel2))
        } else {
            Icon(Icons.Default.MusicNote, null, tint = PurpleBright, modifier = Modifier.fillMaxSize(.48f))
        }
    }
}

@Composable
private fun TrackRow(track: MusicTrack, favorite: Boolean, onClick: () -> Unit, onFavorite: () -> Unit, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Panel).clickable(onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        AlbumArt(track, Modifier.size(52.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${track.artist}  •  ${track.album}", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onFavorite) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (favorite) PurpleBright else Muted, modifier = Modifier.size(20.dp)) }
        IconButton(onClick = onMore) { Icon(Icons.Default.MoreVert, null, tint = Muted) }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String, action: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.LibraryMusic, null, tint = Purple, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(14.dp)); Text(title, color = White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp)); Text(subtitle, color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp)); Button(onClick = action, colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text("Try again", color = Bg) }
    }
}

@Composable
private fun MiniPlayer(track: MusicTrack, playing: Boolean, position: Long, duration: Long, onOpen: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF38204D), Color(0xFF21182D)))).clickable(onClick = onOpen).padding(horizontal = 11.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AlbumArt(track, Modifier.size(43.dp)); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Text(track.title, color = White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(track.artist, color = Muted, fontSize = 11.sp) }
            IconButton(onClick = onToggle) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = White) }
            IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, null, tint = White) }
        }
        LinearProgressIndicator(progress = { if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth().height(2.dp).clip(CircleShape), color = PurpleBright, trackColor = Color.White.copy(alpha = .12f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSheet(track: MusicTrack, playing: Boolean, position: Long, duration: Long, favorite: Boolean, lyrics: List<Pair<Long, String>>, lyricsLoading: Boolean, lyricsMessage: String,
    onDismiss: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit, onSeek: (Long) -> Unit, onFavorite: () -> Unit, onShuffle: () -> Unit, onRepeat: () -> Unit, onLoadLyrics: () -> Unit, onSpeed: (Float) -> Unit) {
    var showLyrics by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Bg, dragHandle = { BottomSheetDefaults.DragHandle(color = Muted) }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = { showLyrics = !showLyrics }) { Icon(Icons.Default.Lyrics, null); Spacer(Modifier.width(6.dp)); Text(if (showLyrics) "PLAYER" else "LYRICS") } }
            if (!showLyrics) {
                val infinite = rememberInfiniteTransition(label = "vinyl")
                val rotation by infinite.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "rotation")
                AlbumArt(track, Modifier.size(260.dp).rotate(if (playing) rotation else 0f))
                Spacer(Modifier.height(28.dp))
                Text(track.title, color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = Muted, fontSize = 14.sp)
                Spacer(Modifier.height(20.dp))
                Slider(value = if (duration > 0) position.toFloat().coerceIn(0f, duration.toFloat()) else 0f, onValueChange = { onSeek(it.toLong()) }, valueRange = 0f..max(1L, duration).toFloat(), colors = SliderDefaults.colors(thumbColor = PurpleBright, activeTrackColor = Purple, inactiveTrackColor = Panel2))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatTime(position), color = Muted, fontSize = 11.sp); Text(formatTime(duration), color = Muted, fontSize = 11.sp) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onShuffle) { Icon(Icons.Default.Shuffle, null, tint = Purple) }
                    IconButton(onClick = onPrevious) { Icon(Icons.Default.SkipPrevious, null, tint = White, modifier = Modifier.size(34.dp)) }
                    IconButton(onClick = onToggle, modifier = Modifier.size(68.dp).clip(CircleShape).background(Brush.linearGradient(listOf(PurpleBright, Purple)))) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Bg, modifier = Modifier.size(38.dp)) }
                    IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, null, tint = White, modifier = Modifier.size(34.dp)) }
                    IconButton(onClick = onRepeat) { Icon(Icons.Default.Repeat, null, tint = Purple) }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onFavorite) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (favorite) PurpleBright else Muted) }
                    Spacer(Modifier.weight(1f)); Text("Speed", color = Muted, fontSize = 12.sp)
                    listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { value -> TextButton(onClick = { speed = value; onSpeed(value) }, contentPadding = PaddingValues(horizontal = 5.dp)) { Text("${value}x", color = if (speed == value) PurpleBright else Muted, fontSize = 11.sp) } }
                }
            } else {
                Text("LYRICS", color = Purple, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onLoadLyrics, enabled = !lyricsLoading, colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text(if (lyricsLoading) "Searching…" else "Find synchronized lyrics", color = Bg) }
                Spacer(Modifier.height(12.dp))
                if (lyrics.isEmpty()) Text(lyricsMessage, color = Muted)
                else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    items(lyrics) { line -> Text(line.second, color = if (position >= line.first && (lyrics.getOrNull(lyrics.indexOf(line) + 1)?.first ?: Long.MAX_VALUE) > position) PurpleBright else Muted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 9.dp)) }
                }
            }
        }
    }
}

@Composable
private fun EqualizerScreen(audioSessionId: Int) {
    var bands by remember { mutableStateOf(listOf(0f, 0f, 0f, 0f, 0f)) }
    var range by remember { mutableStateOf(ShortArray(2)) }
    var enabled by remember { mutableStateOf(false) }
    var bass by remember { mutableFloatStateOf(0f) }
    var virtual by remember { mutableFloatStateOf(0f) }
    var preset by remember { mutableStateOf("Custom") }
    var error by remember { mutableStateOf<String?>(null) }
    val eq = remember(audioSessionId) { if (audioSessionId > 0) runCatching { Equalizer(0, audioSessionId) }.getOrNull() else null }
    val bassFx = remember(audioSessionId) { if (audioSessionId > 0) runCatching { BassBoost(0, audioSessionId) }.getOrNull() else null }
    val virtualFx = remember(audioSessionId) { if (audioSessionId > 0) runCatching { Virtualizer(0, audioSessionId) }.getOrNull() else null }
    DisposableEffect(eq, bassFx, virtualFx) { onDispose { runCatching { eq?.release() }; runCatching { bassFx?.release() }; runCatching { virtualFx?.release() } } }
    LaunchedEffect(eq) { if (eq != null) { range = eq.bandLevelRange; bands = List(eq.numberOfBands.toInt()) { i -> (eq.getBandLevel(i.toShort()).toFloat() / 100f) } } }
    Column(Modifier.fillMaxSize().padding(bottom = 12.dp)) {
        Text("Shape your sound", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Flat", "Bass", "Vocal", "Rock", "Pop").forEach { name -> FilterChip(selected = preset == name, onClick = {
            preset = name
            val values = when (name) { "Bass" -> listOf(7f, 5f, 1f, 0f, 1f); "Vocal" -> listOf(-2f, 1f, 5f, 4f, 1f); "Rock" -> listOf(5f, 3f, -1f, 3f, 5f); "Pop" -> listOf(-1f, 2f, 4f, 2f, -1f); else -> List(5) { 0f } }
            bands = List(bands.size) { values.getOrElse(it) { 0f } }; bands.forEachIndexed { i, v -> runCatching { eq?.setBandLevel(i.toShort(), (v * 100).toInt().toShort()) } }
        }, label = { Text(name) }) } }
        Spacer(Modifier.height(14.dp))
        PanelCard {
            Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Equalizer", color = White, fontWeight = FontWeight.Bold); Text(if (eq == null) "Start playback to activate audio effects" else "${bands.size} frequency bands", color = Muted, fontSize = 11.sp) }; Switch(enabled, { enabled = it; runCatching { eq?.enabled = it }; error = if (eq == null) "Audio effects are available after playback starts." else null }) }
            if (eq != null) bands.forEachIndexed { index, value ->
                Row(verticalAlignment = Alignment.CenterVertically) { Text(listOf("60", "230", "910", "3.6k", "14k").getOrElse(index) { "${index + 1}" } + " Hz", color = Muted, fontSize = 10.sp, modifier = Modifier.width(46.dp)); Slider(value = value, onValueChange = { v -> bands = bands.toMutableList().also { it[index] = v }; runCatching { eq.setBandLevel(index.toShort(), (v * 100).toInt().coerceIn(range[0].toInt(), range[1].toInt()).toShort()) } }, valueRange = -15f..15f, enabled = enabled, modifier = Modifier.weight(1f)); Text("${value.toInt()} dB", color = Muted, fontSize = 10.sp, modifier = Modifier.width(42.dp)) }
            }
        }
        Spacer(Modifier.height(12.dp))
        PanelCard {
            Text("Bass boost", color = White, fontWeight = FontWeight.Bold)
            Slider(bass, { bass = it; runCatching { bassFx?.setStrength((it * 1000).toInt().toShort()) } }, valueRange = 0f..1f, enabled = enabled && bassFx != null)
            Text("Virtualizer", color = White, fontWeight = FontWeight.Bold)
            Slider(virtual, { virtual = it; runCatching { virtualFx?.setStrength((it * 1000).toInt().toShort()) } }, valueRange = 0f..1f, enabled = enabled && virtualFx != null)
        }
        error?.let { Text(it, color = PurpleBright, fontSize = 11.sp) }
        Text("Effect availability depends on the Android device and active audio session.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun SettingsScreen(sleepMinutes: Int, onSleepChange: (Int) -> Unit, sleepRemaining: Int, onRefresh: () -> Unit, onCreatePlaylist: () -> Unit, playlistEntries: List<Pair<String, Int>>, onPlayPlaylist: (String) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item { PanelCard { Text("Playback", color = White, fontWeight = FontWeight.Bold); Text("Sleep timer", color = Muted, fontSize = 12.sp); Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { listOf(0, 15, 30, 45, 60).forEach { n -> FilterChip(selected = sleepMinutes == n, onClick = { onSleepChange(n) }, label = { Text(if (n == 0) "Off" else "${n}m") }) } }; if (sleepRemaining > 0) Text("Stops in ${sleepRemaining / 60}:${(sleepRemaining % 60).toString().padStart(2, '0')}", color = PurpleBright) } }
        item { PanelCard { Text("Library", color = White, fontWeight = FontWeight.Bold); Text("Refresh music library", color = Muted); Button(onClick = onRefresh, colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(7.dp)); Text("Rescan device") } } }
        item { PanelCard { Text("Playlists", color = White, fontWeight = FontWeight.Bold); Text("${playlistEntries.size} playlists saved", color = Muted); playlistEntries.forEach { entry -> Row(Modifier.fillMaxWidth().clickable { onPlayPlaylist(entry.first) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.QueueMusic, null, tint = Purple); Spacer(Modifier.width(10.dp)); Text(entry.first, color = White, modifier = Modifier.weight(1f)); Text("${entry.second} tracks", color = Muted, fontSize = 11.sp); Icon(Icons.Default.PlayArrow, null, tint = Purple) } }; Button(onClick = onCreatePlaylist, colors = ButtonDefaults.buttonColors(containerColor = Panel2)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(7.dp)); Text("Create playlist") } } }
        item { PanelCard { Text("About Noir Music", color = White, fontWeight = FontWeight.Bold); Text("Noir Music • Premium local music player", color = Muted, fontSize = 12.sp); Text("Version 2.0.0", color = PurpleBright, fontSize = 11.sp) } }
    }
}

@Composable
private fun PanelCard(content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(16.dp), content = content) }

private fun fetchLyrics(track: MusicTrack): Pair<List<Pair<Long, String>>, String> {
    return try {
        val title = URLEncoder.encode(track.title, "UTF-8")
        val artist = URLEncoder.encode(track.artist, "UTF-8")
        val url = URL("https://lrclib.net/api/search?track_name=$title&artist_name=$artist")
        val connection = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 8000; requestMethod = "GET"; setRequestProperty("User-Agent", "NoirMusic/2.0") }
        val text = connection.inputStream.bufferedReader().use { it.readText() }; connection.disconnect()
        val array = JSONArray(text)
        if (array.length() == 0) return emptyList<Pair<Long, String>>() to "No lyrics found for this track."
        val obj = (0 until array.length()).map { array.getJSONObject(it) }.firstOrNull { it.optString("syncedLyrics").isNotBlank() } ?: array.getJSONObject(0)
        val synced = obj.optString("syncedLyrics")
        if (synced.isNotBlank()) {
            val lines = synced.lines().mapNotNull { line ->
                val match = Regex("\\[(\\d{2}):(\\d{2})(?:\\.(\\d{2,3}))?]\\s*(.*)").find(line)
                match?.let { m -> val ms = m.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L; ((m.groupValues[1].toLong() * 60 + m.groupValues[2].toLong()) * 1000 + ms) to m.groupValues[4] }
            }
            lines to if (lines.isEmpty()) "Lyrics found but could not be parsed." else "Synchronized lyrics • LRCLIB"
        } else {
            val plain = obj.optString("plainLyrics")
            plain.lines().map { 0L to it } to if (plain.isBlank()) "No lyrics available." else "Plain lyrics • LRCLIB"
        }
    } catch (_: Exception) { emptyList<Pair<Long, String>>() to "Could not load lyrics. Check your internet connection." }
}

private fun formatTime(ms: Long): String { val seconds = (ms / 1000).coerceAtLeast(0); return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60) }
