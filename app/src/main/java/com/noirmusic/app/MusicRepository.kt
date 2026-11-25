package com.noirmusic.app

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

class MusicRepository(private val context: Context) {
    fun loadAudio(): List<MusicTrack> {
        val result = mutableListOf<MusicTrack>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.ALBUM_ID
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 1000"
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        context.contentResolver.query(collection, projection, selection, null, sort)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                val art = if (albumId > 0) android.content.ContentUris.withAppendedId(android.net.Uri.parse("content://media/external/audio/albumart"), albumId) else null
                result += MusicTrack(
                    id, cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Unknown title",
                    cursor.getString(artistCol)?.takeUnless { it.isNullOrBlank() || it == "<unknown>" } ?: "Unknown artist",
                    cursor.getString(albumCol)?.takeIf { it.isNotBlank() } ?: "Unknown album",
                    cursor.getLong(durationCol), ContentUris.withAppendedId(collection, id), albumId, art
                )
            }
        }
        return result
    }
}
