package com.noirmusic.app

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

/**
 * Serves song artwork as image/jpeg so notifications, the lock screen and Bluetooth/car displays
 * can load it on every Android version (the legacy album-art provider stopped working on Android 10+).
 */
class ArtProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "com.noirmusic.app.art"
        fun uriFor(t: MusicTrack): Uri = Uri.parse("content://$AUTHORITY/track/${t.id}?a=${t.albumId}")
    }

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val ctx = context ?: throw FileNotFoundException()
        val id = uri.lastPathSegment?.toLongOrNull() ?: throw FileNotFoundException()
        val albumId = uri.getQueryParameter("a")?.toLongOrNull() ?: 0L
        val dir = File(ctx.cacheDir, "art").apply { mkdirs() }
        val file = File(dir, "$id.jpg")
        if (!file.exists() || file.length() == 0L) {
            val token = Binder.clearCallingIdentity()
            try {
                val trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                val art = if (albumId > 0) ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId) else null
                val bmp = ArtCache.decodeSync(ctx, trackUri, art, 512) ?: throw FileNotFoundException()
                FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            } finally {
                Binder.restoreCallingIdentity(token)
            }
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/jpeg"
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}
