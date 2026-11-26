package com.noirmusic.app

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media as A

class MusicRepository(private val context: Context) {
    private val albumArtBase = Uri.parse("content://media/external/audio/albumart")

    fun loadAudio(minDurationMs: Long, includeNonMusic: Boolean): List<MusicTrack> {
        val result = ArrayList<MusicTrack>()
        val collection = if (Build.VERSION.SDK_INT >= 29)
            A.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else A.EXTERNAL_CONTENT_URI
        val projection = mutableListOf(
            A._ID, A.TITLE, A.ARTIST, A.ALBUM, A.DURATION, A.ALBUM_ID,
            A.DATE_ADDED, A.TRACK, A.YEAR, A.SIZE, A.MIME_TYPE
        )
        if (Build.VERSION.SDK_INT >= 29) projection += A.RELATIVE_PATH else projection += A.DATA
        val selection = buildString {
            append("${A.IS_RINGTONE} = 0 AND ${A.IS_ALARM} = 0 AND ${A.IS_NOTIFICATION} = 0 AND ${A.DURATION} >= ?")
            if (!includeNonMusic) append(" AND ${A.IS_MUSIC} != 0")
        }
        context.contentResolver.query(
            collection, projection.toTypedArray(), selection, arrayOf(minDurationMs.toString()),
            "${A.TITLE} COLLATE NOCASE ASC"
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(A._ID)
            val iTitle = c.getColumnIndexOrThrow(A.TITLE)
            val iArtist = c.getColumnIndexOrThrow(A.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(A.ALBUM)
            val iDur = c.getColumnIndexOrThrow(A.DURATION)
            val iAlbumId = c.getColumnIndexOrThrow(A.ALBUM_ID)
            val iAdded = c.getColumnIndexOrThrow(A.DATE_ADDED)
            val iTrack = c.getColumnIndexOrThrow(A.TRACK)
            val iYear = c.getColumnIndexOrThrow(A.YEAR)
            val iSize = c.getColumnIndexOrThrow(A.SIZE)
            val iMime = c.getColumnIndexOrThrow(A.MIME_TYPE)
            val iPath = c.getColumnIndexOrThrow(if (Build.VERSION.SDK_INT >= 29) A.RELATIVE_PATH else A.DATA)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val albumId = c.getLong(iAlbumId)
                val rawPath = c.getString(iPath).orEmpty()
                val folder = if (Build.VERSION.SDK_INT >= 29) rawPath.trim('/')
                else rawPath.substringBeforeLast('/', "").removePrefix("/storage/emulated/0").trim('/')
                val artist = c.getString(iArtist)
                result += MusicTrack(
                    id = id,
                    title = c.getString(iTitle)?.takeIf { it.isNotBlank() } ?: "Unknown title",
                    artist = artist?.takeUnless { it.isBlank() || it == "<unknown>" } ?: "Unknown artist",
                    album = c.getString(iAlbum)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown album",
                    duration = c.getLong(iDur),
                    uri = ContentUris.withAppendedId(collection, id),
                    albumId = albumId,
                    artworkUri = if (albumId > 0) ContentUris.withAppendedId(albumArtBase, albumId) else null,
                    dateAdded = c.getLong(iAdded),
                    trackNo = c.getInt(iTrack) % 1000,
                    year = c.getInt(iYear),
                    folder = folder.ifBlank { "Internal storage" },
                    size = c.getLong(iSize),
                    mime = c.getString(iMime).orEmpty()
                )
            }
        }
        return result
    }
}
