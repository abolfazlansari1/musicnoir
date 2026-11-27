package com.noirmusic.app

import android.net.Uri

data class MusicTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val albumId: Long,
    val artworkUri: Uri?,
    val dateAdded: Long = 0,
    val trackNo: Int = 0,
    val year: Int = 0,
    val folder: String = "",
    val size: Long = 0,
    val mime: String = ""
)

data class AlbumItem(val id: Long, val name: String, val artist: String, val tracks: List<MusicTrack>) {
    val cover: MusicTrack get() = tracks.first()
}

data class ArtistItem(val name: String, val tracks: List<MusicTrack>, val albumCount: Int)

data class FolderItem(val path: String, val name: String, val tracks: List<MusicTrack>)

enum class SortKey(val label: String) {
    TITLE("Title"), ARTIST("Artist"), ALBUM("Album"), DATE("Date added"), DURATION("Duration")
}

data class LyricLine(val timeMs: Long, val text: String)

sealed interface LyricsState {
    data object Idle : LyricsState
    data object Loading : LyricsState
    data class Message(val text: String) : LyricsState
    data class Loaded(val lines: List<LyricLine>, val synced: Boolean, val source: String) : LyricsState
}

sealed interface Route {
    data class Album(val id: Long) : Route
    data class Artist(val name: String) : Route
    data class Folder(val path: String) : Route
    data class Playlist(val name: String) : Route
    data object Favorites : Route
    data object Recent : Route
    data object MostPlayed : Route
    data object Equalizer : Route
    data object Playlists : Route
}

/** Stable key used to group songs into albums (and to navigate to one). */
val MusicTrack.albumKey: Long get() = albumId.takeIf { it > 0 } ?: -(album.hashCode().toLong())
