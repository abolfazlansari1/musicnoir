package com.noirmusic.app

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.ComponentName
import android.content.IntentSender
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.ContentUris
import android.media.MediaMetadataRetriever
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.Normalizer

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    private val repo = MusicRepository(app)
    private val ctx get() = getApplication<Application>()

    // ---------- library ----------
    var tracks by mutableStateOf<List<MusicTrack>>(emptyList()); private set
    var albums by mutableStateOf<List<AlbumItem>>(emptyList()); private set
    var artists by mutableStateOf<List<ArtistItem>>(emptyList()); private set
    var folders by mutableStateOf<List<FolderItem>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var permissionGranted by mutableStateOf(false); private set
    var searchIndex by mutableStateOf<Map<Long, String>>(emptyMap()); private set
    private val known = HashMap<Long, MusicTrack>()
    private val byId by derivedStateOf { tracks.associateBy { it.id } }

    // ---------- settings ----------
    var accent by mutableIntStateOf(store.accent); private set
    var amoled by mutableStateOf(store.amoled); private set
    var minDurationSec by mutableIntStateOf(store.minDurationSec); private set
    var includeNonMusic by mutableStateOf(store.includeNonMusic); private set
    var keepScreenOn by mutableStateOf(store.keepScreenOn); private set
    var haptics by mutableStateOf(store.haptics); private set
    var startTab by mutableIntStateOf(store.startTab); private set
    var hiddenFolders by mutableStateOf(store.hiddenFolders); private set
    var searchHistory by mutableStateOf(store.searchHistory); private set
    var notice by mutableStateOf<String?>(null)
    var selection by mutableStateOf<List<MusicTrack>>(emptyList()); private set
    val selecting: Boolean get() = selection.isNotEmpty()
    var skipSilence by mutableStateOf(store.skipSilence); private set
    var sortKey by mutableIntStateOf(store.sortKey); private set
    var sortAsc by mutableStateOf(store.sortAsc); private set

    // ---------- user data ----------
    var favorites by mutableStateOf(store.favorites); private set
    var playlists by mutableStateOf(store.loadPlaylists()); private set
    var recents by mutableStateOf(store.recents); private set
    var playCounts by mutableStateOf(store.playCounts); private set

    val sortedTracks: List<MusicTrack> by derivedStateOf { sortTracks(tracks, SortKey.entries[sortKey.coerceIn(0, SortKey.entries.lastIndex)], sortAsc) }
    val favoriteTracks: List<MusicTrack> by derivedStateOf { sortedTracks.filter { it.id in favorites } }
    val recentTracks: List<MusicTrack> by derivedStateOf { recents.mapNotNull { byId[it] } }
    val recentlyAdded: List<MusicTrack> by derivedStateOf { tracks.sortedByDescending { it.dateAdded }.take(30) }
    val mostPlayed: List<MusicTrack> by derivedStateOf {
        playCounts.entries.sortedByDescending { it.value }.mapNotNull { e -> byId[e.key] }.take(50)
    }

    // ---------- playback ----------
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    var current by mutableStateOf<MusicTrack?>(null); private set
    var isPlaying by mutableStateOf(false); private set
    var position by mutableLongStateOf(0L); private set
    var duration by mutableLongStateOf(0L); private set
    var shuffle by mutableStateOf(false); private set
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF); private set
    var speed by mutableFloatStateOf(1f); private set
    var pitch by mutableFloatStateOf(1f); private set
    var queue by mutableStateOf<List<MusicTrack>>(emptyList()); private set
    var queueIndex by mutableIntStateOf(-1); private set
    var artColor by mutableStateOf<Color?>(null); private set
    var upNext by mutableStateOf<MusicTrack?>(null); private set
    private var artFor = -1L
    private var lastRecorded = -1L
    private var restored = false
    private var pendingUri: Uri? = null
    private var foreground = true

    // ---------- lyrics / delete ----------
    var lyrics by mutableStateOf<LyricsState>(LyricsState.Idle); private set
    private var lyricsFor = -1L
    private var lyricsJob: Job? = null
    var pendingDelete by mutableStateOf<IntentSender?>(null)
    private var deleting: MusicTrack? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_PLAYLIST_METADATA_CHANGED
                )
            ) syncQueue()
            syncState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) recordPlay()
        }
    }

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { scheduleReload() }
    }
    private var reloadJob: Job? = null

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching {
                val c = future.get()
                controller = c
                c.addListener(listener)
                if (c.mediaItemCount == 0) {
                    c.shuffleModeEnabled = store.shuffle
                    c.repeatMode = store.repeat
                    c.playbackParameters = PlaybackParameters(store.speed, store.pitch)
                }
                syncQueue(); syncState()
                tryRestore()
                tryPlayPending()
            }
        }, MoreExecutors.directExecutor())
        runCatching { app.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer) }
        viewModelScope.launch {
            while (true) {
                val c = controller
                if (c != null && foreground) {
                    position = c.currentPosition.coerceAtLeast(0)
                    if (c.duration > 0) duration = c.duration
                }
                delay(if (isPlaying && foreground) 200 else 700)
            }
        }
    }

    override fun onCleared() {
        runCatching { ctx.contentResolver.unregisterContentObserver(observer) }
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }

    fun setForeground(value: Boolean) {
        foreground = value
        if (value) { syncState(); controller?.let { position = it.currentPosition.coerceAtLeast(0) } }
    }

    // ---------- library loading ----------
    fun onPermission(granted: Boolean) {
        permissionGranted = granted
        if (granted) loadLibrary() else loading = false
    }

    fun loadLibrary(silent: Boolean = false) {
        if (!permissionGranted) return
        viewModelScope.launch {
            if (!silent) loading = true
            val list = withContext(Dispatchers.IO) {
                runCatching { repo.loadAudio(minDurationSec * 1000L, includeNonMusic) }.getOrDefault(emptyList())
                    .filter { it.folder !in hiddenFolders }
            }
            applyLibrary(list)
            loading = false
            tryRestore()
            syncQueue(); syncState()
        }
    }

    private fun scheduleReload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch { delay(1500); loadLibrary(silent = true) }
    }

    private suspend fun applyLibrary(list: List<MusicTrack>) {
        val grouped = withContext(Dispatchers.Default) {
            val al = list.groupBy { it.albumKey }.map { (id, ts) ->
                val sorted = ts.sortedWith(compareBy({ it.trackNo }, { it.title.lowercase() }))
                val artistNames = ts.map { it.artist }.distinct()
                AlbumItem(id, ts.first().album, if (artistNames.size == 1) artistNames[0] else "Various artists", sorted)
            }.sortedBy { it.name.lowercase() }
            val ar = list.groupBy { it.artist.lowercase() }.map { (_, ts) ->
                ArtistItem(ts.first().artist, ts, ts.map { it.albumId }.distinct().size)
            }.sortedBy { it.name.lowercase() }
            val fo = list.groupBy { it.folder }.map { (path, ts) ->
                FolderItem(path, path.substringAfterLast('/').ifBlank { path }, ts)
            }.sortedBy { it.name.lowercase() }
            Triple(al, ar, fo)
        }
        val index = withContext(Dispatchers.Default) {
            list.associate { it.id to normalize("${it.title} ${it.artist} ${it.album}") }
        }
        searchIndex = index
        tracks = list
        known.putAll(list.associateBy { it.id })
        albums = grouped.first; artists = grouped.second; folders = grouped.third
    }

    // ---------- settings ----------
    fun chooseAccent(i: Int) { accent = i; store.accent = i }
    fun chooseAmoled(v: Boolean) { amoled = v; store.amoled = v }
    fun chooseSkipSilence(v: Boolean) { skipSilence = v; store.skipSilence = v }
    fun setMinDuration(sec: Int) { minDurationSec = sec; store.minDurationSec = sec; loadLibrary(silent = true) }
    fun chooseIncludeNonMusic(v: Boolean) { includeNonMusic = v; store.includeNonMusic = v; loadLibrary(silent = true) }
    fun setSort(key: Int, asc: Boolean) { sortKey = key; sortAsc = asc; store.sortKey = key; store.sortAsc = asc }

    fun chooseKeepScreenOn(v: Boolean) { keepScreenOn = v; store.keepScreenOn = v }
    fun chooseHaptics(v: Boolean) { haptics = v; store.haptics = v }
    fun chooseStartTab(v: Int) { startTab = v; store.startTab = v }

    fun hideFolder(path: String) { hiddenFolders = hiddenFolders + path; store.hiddenFolders = hiddenFolders; loadLibrary(silent = true) }
    fun unhideFolder(path: String) { hiddenFolders = hiddenFolders - path; store.hiddenFolders = hiddenFolders; loadLibrary(silent = true) }

    fun addSearch(q: String) {
        val s = q.trim()
        if (s.length < 2) return
        searchHistory = (listOf(s) + searchHistory.filter { !it.equals(s, true) }).take(8)
        store.searchHistory = searchHistory
    }
    fun clearSearchHistory() { searchHistory = emptyList(); store.searchHistory = emptyList() }

    // ---------- multi-select ----------
    fun toggleSelect(t: MusicTrack) {
        selection = if (selection.any { it.id == t.id }) selection.filter { it.id != t.id } else selection + t
    }
    fun clearSelection() { selection = emptyList() }
    fun selectAll(list: List<MusicTrack>) { selection = list }
    fun playNextAll(list: List<MusicTrack>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) { play(list); return }
        known.putAll(list.associateBy { it.id })
        c.addMediaItems(c.currentMediaItemIndex + 1, list.map(::toItem))
    }
    fun favoriteAll(list: List<MusicTrack>) {
        favorites = favorites + list.map { it.id }
        store.favorites = favorites
    }

    // ---------- backup / restore ----------
    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    fun ref(id: Long): JSONObject? {
                        val t = known[id] ?: return null
                        return JSONObject().put("id", t.id).put("title", t.title).put("artist", t.artist)
                    }
                    val o = JSONObject().put("app", "NoirMusic").put("version", 1)
                    o.put("favorites", JSONArray().also { a -> favorites.mapNotNull { ref(it) }.forEach { a.put(it) } })
                    val pl = JSONObject()
                    playlists.forEach { (n, ids) -> pl.put(n, JSONArray().also { a -> ids.mapNotNull { ref(it) }.forEach { a.put(it) } }) }
                    o.put("playlists", pl)
                    ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(o.toString(2).toByteArray()) }
                }.isSuccess
            }
            notice = if (ok) "Backup saved" else "Backup failed"
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
            }
            if (text == null) { notice = "Could not read the file"; return@launch }
            try {
                val o = JSONObject(text)
                val byKey = tracks.associateBy { normalize(it.title) + "|" + normalize(it.artist) }
                fun resolve(r: JSONObject): Long? {
                    val id = r.optLong("id", -1)
                    val direct = byId[id]
                    if (direct != null && normalize(direct.title) == normalize(r.optString("title"))) return id
                    return byKey[normalize(r.optString("title")) + "|" + normalize(r.optString("artist"))]?.id
                }
                val favs = favorites.toMutableSet()
                o.optJSONArray("favorites")?.let { a -> for (i in 0 until a.length()) resolve(a.getJSONObject(i))?.let { favs.add(it) } }
                val pls = playlists.toMutableMap()
                o.optJSONObject("playlists")?.let { po ->
                    for (name in po.keys()) {
                        val a = po.getJSONArray(name)
                        val cur = pls[name].orEmpty().toMutableList()
                        for (i in 0 until a.length()) resolve(a.getJSONObject(i))?.let { if (it !in cur) cur.add(it) }
                        pls[name] = cur
                    }
                }
                favorites = favs; store.favorites = favs
                savePlaylists(pls)
                notice = "Backup restored"
            } catch (_: Exception) {
                notice = "This is not a valid backup file"
            }
        }
    }

    fun importLyrics(t: MusicTrack, uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }.toString(Charsets.UTF_8).removePrefix("\uFEFF") }.getOrNull()
            }
            if (text.isNullOrBlank()) { lyrics = LyricsState.Message("That file could not be read."); return@launch }
            val synced = Regex("\\[\\d{1,3}:\\d{2}").containsMatchIn(text)
            Lyrics.writeCache(File(ctx.filesDir, "lyrics"), t.id, Lyrics.Found(synced, text))
            lyricsFor = -1L
            loadLyrics(t)
        }
    }

    fun clearHistory() {
        recents = emptyList(); playCounts = emptyMap()
        store.recents = emptyList(); store.playCounts = emptyMap()
    }

    fun clearLyricsCache() {
        runCatching { File(ctx.filesDir, "lyrics").deleteRecursively() }
        lyrics = LyricsState.Idle; lyricsFor = -1L
    }

    // ---------- playback sync ----------
    private fun syncQueue() {
        val c = controller ?: return
        val n = c.mediaItemCount
        val list = ArrayList<MusicTrack>(n)
        for (i in 0 until n) known[c.getMediaItemAt(i).mediaId.toLongOrNull()]?.let { list.add(it) }
        queue = list
    }

    private fun syncState() {
        val c = controller ?: return
        isPlaying = c.isPlaying
        shuffle = c.shuffleModeEnabled
        repeat = c.repeatMode
        speed = c.playbackParameters.speed
        pitch = c.playbackParameters.pitch
        queueIndex = c.currentMediaItemIndex
        val now = c.currentMediaItem?.mediaId?.toLongOrNull()?.let { known[it] }
        if (now?.id != current?.id) {
            current = now
            if (now != null) updateArtColor(now) else artColor = null
        }
        duration = if (c.duration > 0) c.duration else (current?.duration ?: 0L)
        val ni = c.nextMediaItemIndex
        upNext = if (ni in 0 until c.mediaItemCount) known[c.getMediaItemAt(ni).mediaId.toLongOrNull()] else null
    }

    private fun updateArtColor(t: MusicTrack) {
        if (artFor == t.id) return
        artFor = t.id
        viewModelScope.launch { val col = ArtCache.accentFor(ctx, t); if (artFor == t.id) artColor = col }
    }

    private fun recordPlay() {
        val id = current?.id ?: return
        if (id == lastRecorded) return
        lastRecorded = id
        recents = (listOf(id) + recents.filter { it != id }).take(40)
        playCounts = playCounts + (id to ((playCounts[id] ?: 0) + 1))
        store.recents = recents; store.playCounts = playCounts
    }

    private fun tryRestore() {
        val c = controller ?: return
        if (restored || tracks.isEmpty()) return
        restored = true
        if (c.mediaItemCount > 0) return
        val list = store.lastQueue.mapNotNull { byId[it] }
        if (list.isEmpty()) return
        val idx = list.indexOfFirst { it.id == store.lastCurrentId }.coerceAtLeast(0)
        c.setMediaItems(list.map(::toItem), idx, store.lastPosition)
        c.prepare()
    }

    private fun toItem(t: MusicTrack): MediaItem {
        val meta = MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album)
            .setIsPlayable(true).setIsBrowsable(false)
            .setArtworkUri(ArtProvider.uriFor(t)).build()
        return MediaItem.Builder().setUri(t.uri).setMediaId(t.id.toString()).setMediaMetadata(meta).build()
    }

    // ---------- open an audio file from outside the app ----------
    fun playExternal(uri: Uri) { pendingUri = uri; tryPlayPending() }

    private fun tryPlayPending() {
        val uri = pendingUri ?: return
        if (controller == null) return
        pendingUri = null
        viewModelScope.launch {
            val track = withContext(Dispatchers.IO) { resolveExternal(uri) }
            play(listOf(track))
        }
    }

    private fun resolveExternal(uri: Uri): MusicTrack {
        val mediaId = runCatching { if (uri.authority == "media") ContentUris.parseId(uri) else -1L }.getOrDefault(-1L)
        tracks.firstOrNull { it.id == mediaId }?.let { return it }
        var title = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')?.ifBlank { null } ?: "Unknown title"
        var artist = "Unknown artist"; var album = "Unknown album"; var dur = 0L
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let { title = it }
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.let { artist = it }
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }?.let { album = it }
            dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
        } finally { runCatching { r.release() } }
        val id = -(Math.abs(uri.toString().hashCode().toLong()) + 1_000_000L)
        return MusicTrack(id, title, artist, album, dur, uri, 0L, null, folder = "External")
    }

    // ---------- playback actions ----------
    fun play(list: List<MusicTrack>, start: Int = 0, shuffleOn: Boolean? = null) {
        val c = controller ?: return
        if (list.isEmpty()) return
        val idx = start.coerceIn(0, list.lastIndex)
        // very large queues are windowed around the chosen track to keep the session snappy
        val from = if (list.size > 1000) (idx - 200).coerceAtLeast(0) else 0
        val to = if (list.size > 1000) (from + 1000).coerceAtMost(list.size) else list.size
        val window = list.subList(from, to)
        known.putAll(window.associateBy { it.id })
        shuffleOn?.let { c.shuffleModeEnabled = it; store.shuffle = it }
        c.setMediaItems(window.map(::toItem), idx - from, 0L)
        c.prepare(); c.play()
    }

    fun playShuffled(list: List<MusicTrack>) {
        if (list.isEmpty()) return
        play(list, (list.indices).random(), true)
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else { if (c.playbackState == Player.STATE_IDLE) c.prepare(); c.play() }
    }

    fun next() { controller?.seekToNext() }
    fun previous() { controller?.seekToPrevious() }
    fun seekTo(ms: Long) { controller?.seekTo(ms); position = ms }
    fun seekFraction(f: Float) { if (duration > 0) seekTo((duration * f).toLong()) }
    fun skipTo(index: Int) { controller?.let { if (index in 0 until it.mediaItemCount && index != it.currentMediaItemIndex) { it.seekToDefaultPosition(index) } } }

    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled; store.shuffle = it.shuffleModeEnabled } }
    fun cycleRepeat() {
        controller?.let {
            val next = when (it.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }
            it.repeatMode = next; store.repeat = next
        }
    }

    fun setSpeedPitch(s: Float, p: Float) {
        controller?.playbackParameters = PlaybackParameters(s, p)
        store.speed = s; store.pitch = p
        speed = s; pitch = p
    }

    fun playNext(t: MusicTrack) {
        val c = controller ?: return
        known[t.id] = t
        if (c.mediaItemCount == 0) { play(listOf(t)); return }
        c.addMediaItem(c.currentMediaItemIndex + 1, toItem(t))
    }

    fun addToQueue(t: MusicTrack) {
        val c = controller ?: return
        known[t.id] = t
        if (c.mediaItemCount == 0) { play(listOf(t)); return }
        c.addMediaItem(toItem(t))
    }

    fun addAllToQueue(list: List<MusicTrack>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) { play(list); return }
        known.putAll(list.associateBy { it.id })
        c.addMediaItems(list.map(::toItem))
    }

    fun removeFromQueue(index: Int) { controller?.let { if (index in 0 until it.mediaItemCount) it.removeMediaItem(index) } }
    fun moveInQueue(from: Int, to: Int) { controller?.let { if (from in 0 until it.mediaItemCount && to in 0 until it.mediaItemCount) it.moveMediaItem(from, to) } }
    fun clearUpcoming() {
        controller?.let { if (it.currentMediaItemIndex + 1 < it.mediaItemCount) it.removeMediaItems(it.currentMediaItemIndex + 1, it.mediaItemCount) }
    }

    // ---------- favorites & playlists ----------
    fun isFavorite(id: Long) = id in favorites
    fun toggleFavorite(t: MusicTrack) {
        favorites = if (t.id in favorites) favorites - t.id else favorites + t.id
        store.favorites = favorites
    }

    fun createPlaylist(name: String, initial: List<MusicTrack> = emptyList()): Boolean {
        val n = name.trim()
        if (n.isEmpty() || playlists.keys.any { it.equals(n, true) }) return false
        savePlaylists(playlists + (n to initial.map { it.id }.distinct()))
        return true
    }

    fun renamePlaylist(old: String, new: String): Boolean {
        val n = new.trim()
        if (n.isEmpty() || (!old.equals(n, true) && playlists.keys.any { it.equals(n, true) })) return false
        val ids = playlists[old] ?: return false
        savePlaylists((playlists - old) + (n to ids))
        return true
    }

    fun deletePlaylist(name: String) = savePlaylists(playlists - name)

    fun addToPlaylist(name: String, list: List<MusicTrack>) {
        val cur = playlists[name] ?: return
        savePlaylists(playlists + (name to (cur + list.map { it.id }.filter { it !in cur }).distinct()))
    }

    fun removeFromPlaylist(name: String, id: Long) {
        val cur = playlists[name] ?: return
        savePlaylists(playlists + (name to cur.filter { it != id }))
    }

    fun playlistTracks(name: String): List<MusicTrack> = playlists[name].orEmpty().mapNotNull { byId[it] }

    private fun savePlaylists(m: Map<String, List<Long>>) {
        playlists = m.toSortedMap(compareBy<String> { it.lowercase() })
        store.savePlaylists(playlists)
    }

    // ---------- delete ----------
    fun canDelete() = Build.VERSION.SDK_INT >= 29

    fun requestDelete(t: MusicTrack) {
        val r = ctx.contentResolver
        if (Build.VERSION.SDK_INT >= 30) {
            deleting = t
            pendingDelete = MediaStore.createDeleteRequest(r, listOf(t.uri)).intentSender
        } else if (Build.VERSION.SDK_INT >= 29) {
            deleteApi29(t)
        }
    }

    @RequiresApi(29)
    private fun deleteApi29(t: MusicTrack) {
        try {
            if (ctx.contentResolver.delete(t.uri, null, null) > 0) onDeleted(t)
        } catch (e: RecoverableSecurityException) {
            deleting = t
            pendingDelete = e.userAction.actionIntent.intentSender
        } catch (_: Exception) {}
    }

    fun onDeleteResult(ok: Boolean) {
        pendingDelete = null
        val t = deleting
        deleting = null
        if (!ok || t == null) return
        if (Build.VERSION.SDK_INT == 29) deleteApi29(t) else onDeleted(t)
    }

    private fun onDeleted(t: MusicTrack) {
        controller?.let { c ->
            for (i in c.mediaItemCount - 1 downTo 0) if (c.getMediaItemAt(i).mediaId == t.id.toString()) c.removeMediaItem(i)
        }
        if (t.id in favorites) { favorites = favorites - t.id; store.favorites = favorites }
        savePlaylists(playlists.mapValues { (_, ids) -> ids.filter { it != t.id } })
        loadLibrary(silent = true)
    }

    // ---------- lyrics ----------
    fun loadLyrics(t: MusicTrack, force: Boolean = false) {
        if (!force && lyricsFor == t.id && lyrics !is LyricsState.Message) return
        lyricsFor = t.id
        lyricsJob?.cancel()
        lyrics = LyricsState.Loading
        val dir = File(ctx.filesDir, "lyrics")
        lyricsJob = viewModelScope.launch {
            val result: LyricsState = withContext(Dispatchers.IO) {
                try {
                    val cached = if (force) null else Lyrics.readCache(dir, t.id)
                    val found = cached ?: Lyrics.fetch(t)?.also { Lyrics.writeCache(dir, t.id, it) }
                    when {
                        found == null -> LyricsState.Message("No lyrics found for this track.")
                        found.instrumental -> LyricsState.Message("This track is instrumental.")
                        else -> LyricsState.Loaded(Lyrics.toLines(found), found.synced, if (cached != null) "Saved" else "LRCLIB")
                    }
                } catch (_: IOException) {
                    LyricsState.Message("Could not load lyrics. Check your internet connection.")
                } catch (_: Exception) {
                    LyricsState.Message("Lyrics could not be read.")
                }
            }
            if (lyricsFor == t.id) lyrics = result
        }
    }

    companion object {
        fun sortTracks(list: List<MusicTrack>, key: SortKey, asc: Boolean): List<MusicTrack> {
            val cmp: Comparator<MusicTrack> = when (key) {
                SortKey.TITLE -> compareBy { normalize(it.title) }
                SortKey.ARTIST -> compareBy<MusicTrack> { normalize(it.artist) }.thenBy { normalize(it.title) }
                SortKey.ALBUM -> compareBy<MusicTrack> { normalize(it.album) }.thenBy { it.trackNo }
                SortKey.DATE -> compareBy { it.dateAdded }
                SortKey.DURATION -> compareBy { it.duration }
            }
            return list.sortedWith(if (asc) cmp else cmp.reversed())
        }

        /** Lowercase, strip diacritics and unify Arabic/Persian letter variants so search and sorting feel natural. */
        fun normalize(s: String): String {
            val d = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .replace('ي', 'ی').replace('ك', 'ک').replace('ة', 'ه').replace('ۀ', 'ه')
            return d.lowercase().trim()
        }
    }
}
