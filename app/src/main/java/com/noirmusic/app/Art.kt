package com.noirmusic.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

/** Artwork loading: system thumbnail (API 29+), embedded picture, then the legacy album-art provider. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
object ArtCache {
    private val decoder = Dispatchers.IO.limitedParallelism(3)
    private val cache = object : LruCache<String, Bitmap>(56 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val misses: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val colors = ConcurrentHashMap<Long, Int>()

    fun key(id: Long, px: Int) = "$id:$px"
    fun peek(key: String): Bitmap? = cache.get(key)

    suspend fun load(ctx: Context, id: Long, uri: Uri, artUri: Uri?, px: Int): Bitmap? = withContext(decoder) {
        val k = key(id, px)
        cache.get(k)?.let { return@withContext it }
        if (k in misses) return@withContext null
        val bmp = decodeSync(ctx, uri, artUri, px)
        if (bmp != null) cache.put(k, bmp) else misses.add(k)
        bmp
    }

    fun decodeSync(ctx: Context, uri: Uri, artUri: Uri?, px: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= 29) {
            try { return ctx.contentResolver.loadThumbnail(uri, Size(px, px), null) } catch (_: Exception) {}
        }
        try {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, uri)
                val data = r.embeddedPicture
                if (data != null) decodeBytes(data, px)?.let { return it }
            } finally { runCatching { r.release() } }
        } catch (_: Exception) {}
        if (artUri != null) {
            try {
                ctx.contentResolver.openInputStream(artUri)?.use { s -> BitmapFactory.decodeStream(s)?.let { return it } }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun decodeBytes(data: ByteArray, px: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= px && bounds.outHeight / (sample * 2) >= px) sample *= 2
        return BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    suspend fun jpeg(ctx: Context, id: Long, uri: Uri, artUri: Uri?): ByteArray? {
        val b = load(ctx, id, uri, artUri, 320) ?: return null
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 88, it) }.toByteArray()
    }

    /** A darkened accent color taken from the artwork, used to tint the player. */
    suspend fun accentFor(ctx: Context, t: MusicTrack): Color? = withContext(Dispatchers.IO) {
        colors[t.id]?.let { return@withContext Color(it) }
        val b = load(ctx, t.id, t.uri, t.artworkUri, 128) ?: return@withContext null
        val p = Palette.from(b).maximumColorCount(16).generate()
        val rgb = listOf(p.getDarkVibrantColor(0), p.getVibrantColor(0), p.getDominantColor(0)).firstOrNull { it != 0 }
            ?: return@withContext null
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(rgb, hsv)
        hsv[1] = hsv[1].coerceAtMost(.75f)
        hsv[2] = hsv[2].coerceIn(.28f, .6f)
        val out = android.graphics.Color.HSVToColor(hsv)
        colors[t.id] = out
        Color(out)
    }
}

@Composable
fun Artwork(track: MusicTrack?, modifier: Modifier = Modifier, px: Int = 256, corner: Dp = 14.dp) {
    val ctx = LocalContext.current
    val key = track?.let { ArtCache.key(it.id, px) }
    var bmp by remember(key) { mutableStateOf(key?.let { ArtCache.peek(it) }) }
    LaunchedEffect(key) {
        if (track != null && bmp == null) bmp = ArtCache.load(ctx, track.id, track.uri, track.artworkUri, px)
    }
    val seed = ((track?.albumId ?: 0L) * 47 + (track?.album?.hashCode() ?: 0)).toInt()
    val brush = remember(seed) {
        val hue = ((seed % 360) + 360) % 360
        Brush.linearGradient(listOf(Color.hsv(hue.toFloat(), .45f, .42f), Color.hsv(((hue + 40) % 360).toFloat(), .5f, .16f)))
    }
    val shape = remember(corner) { RoundedCornerShape(corner) }
    val image = remember(bmp) { bmp?.asImageBitmap() }
    Box(modifier.clip(shape).background(brush), contentAlignment = Alignment.Center) {
        if (px >= 400) {
            Crossfade(image, label = "art") { b ->
                if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = .55f), modifier = Modifier.fillMaxSize(.42f))
            }
        } else if (image != null) {
            Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = .55f), modifier = Modifier.fillMaxSize(.42f))
        }
    }
}
