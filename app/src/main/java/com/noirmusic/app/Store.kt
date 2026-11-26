package com.noirmusic.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Local persistence. Keeps the same file/keys as earlier versions so favorites and playlists survive updates. */
class Store(context: Context) {
    val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("noir_preferences", Context.MODE_PRIVATE)

    companion object {
        const val K_SKIP = "skip_silence"
    }

    var favorites: Set<Long>
        get() = sp.getStringSet("favorites", emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
        set(v) { sp.edit().putStringSet("favorites", v.map { it.toString() }.toSet()).apply() }

    fun loadPlaylists(): Map<String, List<Long>> {
        val o = runCatching { JSONObject(sp.getString("playlists", "{}") ?: "{}") }.getOrDefault(JSONObject())
        return o.keys().asSequence().sortedBy { it.lowercase() }.associateWith { k ->
            val a = o.optJSONArray(k) ?: JSONArray()
            (0 until a.length()).mapNotNull { a.optString(it).toLongOrNull() }
        }
    }

    fun savePlaylists(m: Map<String, List<Long>>) {
        val o = JSONObject()
        m.forEach { (k, ids) -> o.put(k, JSONArray().also { a -> ids.forEach { a.put(it.toString()) } }) }
        sp.edit().putString("playlists", o.toString()).apply()
    }

    var recents: List<Long>
        get() = idList("recents")
        set(v) { sp.edit().putString("recents", v.joinToString(",")).apply() }

    var playCounts: Map<Long, Int>
        get() = (sp.getString("play_counts", "") ?: "").split(",").mapNotNull {
            val p = it.split(":"); if (p.size == 2) (p[0].toLongOrNull() ?: return@mapNotNull null) to (p[1].toIntOrNull() ?: 0) else null
        }.toMap()
        set(v) { sp.edit().putString("play_counts", v.entries.joinToString(",") { "${it.key}:${it.value}" }).apply() }

    // appearance / library / playback settings
    var accent: Int get() = sp.getInt("accent", 0); set(v) { sp.edit().putInt("accent", v).apply() }
    var amoled: Boolean get() = sp.getBoolean("amoled", false); set(v) { sp.edit().putBoolean("amoled", v).apply() }
    var minDurationSec: Int get() = sp.getInt("min_duration", 15); set(v) { sp.edit().putInt("min_duration", v).apply() }
    var includeNonMusic: Boolean get() = sp.getBoolean("include_non_music", false); set(v) { sp.edit().putBoolean("include_non_music", v).apply() }
    var skipSilence: Boolean get() = sp.getBoolean(K_SKIP, false); set(v) { sp.edit().putBoolean(K_SKIP, v).apply() }
    var sortKey: Int get() = sp.getInt("sort_key", 0); set(v) { sp.edit().putInt("sort_key", v).apply() }
    var sortAsc: Boolean get() = sp.getBoolean("sort_asc", true); set(v) { sp.edit().putBoolean("sort_asc", v).apply() }
    var shuffle: Boolean get() = sp.getBoolean("shuffle", false); set(v) { sp.edit().putBoolean("shuffle", v).apply() }
    var repeat: Int get() = sp.getInt("repeat", 0); set(v) { sp.edit().putInt("repeat", v).apply() }
    var speed: Float get() = sp.getFloat("speed", 1f); set(v) { sp.edit().putFloat("speed", v).apply() }
    var pitch: Float get() = sp.getFloat("pitch", 1f); set(v) { sp.edit().putFloat("pitch", v).apply() }

    var keepScreenOn: Boolean get() = sp.getBoolean("keep_screen_on", false); set(v) { sp.edit().putBoolean("keep_screen_on", v).apply() }
    var haptics: Boolean get() = sp.getBoolean("haptics", true); set(v) { sp.edit().putBoolean("haptics", v).apply() }
    var startTab: Int get() = sp.getInt("start_tab", 0); set(v) { sp.edit().putInt("start_tab", v).apply() }
    var hiddenFolders: Set<String>
        get() = sp.getStringSet("hidden_folders", emptySet())?.toSet() ?: emptySet()
        set(v) { sp.edit().putStringSet("hidden_folders", v).apply() }
    var searchHistory: List<String>
        get() = (sp.getString("search_history", "") ?: "").split("\n").filter { it.isNotBlank() }
        set(v) { sp.edit().putString("search_history", v.joinToString("\n")).apply() }

    // last queue (written by the playback service)
    var lastQueue: List<Long> get() = idList("last_queue"); set(v) { sp.edit().putString("last_queue", v.joinToString(",")).apply() }
    var lastCurrentId: Long get() = sp.getLong("last_current", -1L); set(v) { sp.edit().putLong("last_current", v).apply() }
    var lastPosition: Long get() = sp.getLong("last_position", 0L); set(v) { sp.edit().putLong("last_position", v).apply() }

    // audio effects
    var fxEnabled: Boolean get() = sp.getBoolean("fx_enabled", false); set(v) { sp.edit().putBoolean("fx_enabled", v).apply() }
    var fxLevels: List<Int> get() = (sp.getString("fx_levels", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }; set(v) { sp.edit().putString("fx_levels", v.joinToString(",")).apply() }
    var fxPreset: Int get() = sp.getInt("fx_preset", -1); set(v) { sp.edit().putInt("fx_preset", v).apply() }
    var fxBass: Int get() = sp.getInt("fx_bass", 0); set(v) { sp.edit().putInt("fx_bass", v).apply() }
    var fxVirt: Int get() = sp.getInt("fx_virt", 0); set(v) { sp.edit().putInt("fx_virt", v).apply() }
    var fxLoud: Int get() = sp.getInt("fx_loud", 0); set(v) { sp.edit().putInt("fx_loud", v).apply() }

    private fun idList(key: String): List<Long> =
        (sp.getString(key, "") ?: "").split(",").mapNotNull { it.toLongOrNull() }
}
