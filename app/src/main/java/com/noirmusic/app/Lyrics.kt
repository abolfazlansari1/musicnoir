package com.noirmusic.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/** Lyrics from LRCLIB (https://lrclib.net) with an on-disk cache. */
object Lyrics {
    data class Found(val synced: Boolean, val text: String, val instrumental: Boolean = false)

    private val tsRegex = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")

    fun cacheFile(dir: File, id: Long) = File(dir, "$id.txt")

    fun readCache(dir: File, id: Long): Found? = runCatching {
        val f = cacheFile(dir, id)
        if (!f.exists()) return null
        val text = f.readText()
        val tag = text.substringBefore('\n')
        val body = text.substringAfter('\n', "")
        when (tag) {
            "S" -> Found(true, body)
            "P" -> Found(false, body)
            "I" -> Found(false, "", true)
            else -> null
        }
    }.getOrNull()

    fun writeCache(dir: File, id: Long, f: Found) {
        runCatching {
            dir.mkdirs()
            cacheFile(dir, id).writeText((if (f.instrumental) "I" else if (f.synced) "S" else "P") + "\n" + f.text)
        }
    }

    /** Blocking network call. Returns null when nothing was found; throws IOException when offline. */
    @Throws(IOException::class)
    fun fetch(t: MusicTrack): Found? {
        val title = clean(t.title)
        val artist = if (t.artist.startsWith("Unknown")) "" else clean(t.artist)
        if (artist.isNotEmpty()) {
            val url = "https://lrclib.net/api/get?track_name=${enc(title)}&artist_name=${enc(artist)}" +
                (if (t.album.startsWith("Unknown")) "" else "&album_name=${enc(t.album)}") +
                "&duration=${t.duration / 1000}"
            httpGet(url)?.let { body -> parse(JSONObject(body))?.let { return it } }
        }
        val q = "https://lrclib.net/api/search?track_name=${enc(title)}" + (if (artist.isNotEmpty()) "&artist_name=${enc(artist)}" else "")
        val arr = JSONArray(httpGet(q) ?: return null)
        val objs = (0 until arr.length()).map { arr.getJSONObject(it) }
        val byDuration = objs.sortedBy { abs(it.optDouble("duration", 0.0) - t.duration / 1000.0) }
        val pick = byDuration.firstOrNull { str(it, "syncedLyrics").isNotBlank() }
            ?: byDuration.firstOrNull { str(it, "plainLyrics").isNotBlank() }
            ?: return null
        return parse(pick)
    }

    private fun parse(o: JSONObject): Found? {
        if (o.optBoolean("instrumental")) return Found(false, "", true)
        val synced = str(o, "syncedLyrics")
        if (synced.isNotBlank()) return Found(true, synced)
        val plain = str(o, "plainLyrics")
        return if (plain.isNotBlank()) Found(false, plain) else null
    }

    fun toLines(f: Found): List<LyricLine> {
        if (!f.synced) return f.text.lines().map { LyricLine(0L, it.trim()) }.dropWhile { it.text.isEmpty() }
        val out = ArrayList<LyricLine>()
        for (line in f.text.lines()) {
            val stamps = tsRegex.findAll(line).toList()
            if (stamps.isEmpty()) continue
            val text = line.replace(tsRegex, "").trim()
            for (m in stamps) {
                val frac = m.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                val ms = (m.groupValues[1].toLong() * 60 + m.groupValues[2].toLong()) * 1000 + frac
                out += LyricLine(ms, text)
            }
        }
        return out.sortedBy { it.timeMs }
    }

    private fun clean(s: String): String =
        s.replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
            .replace(Regex("(?i)\\b(feat|ft)\\.?.*$"), " ")
            .replace(Regex("(?i)\\.(mp3|m4a|flac|wav|ogg|opus)$"), "")
            .replace(Regex("\\s+"), " ").trim()

    private fun str(o: JSONObject, k: String) = if (o.isNull(k)) "" else o.optString(k)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    @Throws(IOException::class)
    private fun httpGet(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000; c.readTimeout = 10000
            c.setRequestProperty("User-Agent", "NoirMusic/3.0 (https://github.com/abolfazlansari1/musicnoir)")
            if (c.responseCode == 404) return null
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }
}
