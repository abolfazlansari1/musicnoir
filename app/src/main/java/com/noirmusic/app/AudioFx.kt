package com.noirmusic.app

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class FxState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val freqsHz: List<Int> = emptyList(),
    val minLevel: Int = -1500,
    val maxLevel: Int = 1500,
    val levels: List<Int> = emptyList(),
    val presets: List<String> = emptyList(),
    val preset: Int = -1,
    val bass: Int = 0,
    val bassSupported: Boolean = false,
    val virt: Int = 0,
    val virtSupported: Boolean = false,
    val loud: Int = 0
)

/**
 * Audio effects live here (owned by the playback service), not in a screen, so they keep working
 * when the user leaves the equalizer page and are restored on every launch.
 */
object AudioFx {
    private var store: Store? = null
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null
    private var loud: LoudnessEnhancer? = null
    private val _state = MutableStateFlow(FxState())
    val state: StateFlow<FxState> = _state

    @Synchronized
    fun attach(context: Context, sessionId: Int) {
        val s = store ?: Store(context).also { store = it }
        releaseEffects()
        if (sessionId <= 0) { _state.value = FxState(); return }
        eq = runCatching { Equalizer(0, sessionId) }.getOrNull()
        bass = runCatching { BassBoost(0, sessionId) }.getOrNull()
        virt = runCatching { Virtualizer(0, sessionId) }.getOrNull()
        loud = runCatching { LoudnessEnhancer(sessionId) }.getOrNull()

        val e = eq
        if (e != null) {
            val bands = e.numberOfBands.toInt()
            val saved = s.fxLevels
            if (s.fxPreset in 0 until e.numberOfPresets) {
                runCatching { e.usePreset(s.fxPreset.toShort()) }
            } else if (saved.size == bands) {
                saved.forEachIndexed { i, v -> runCatching { e.setBandLevel(i.toShort(), v.toShort()) } }
            }
            runCatching { e.enabled = s.fxEnabled }
        }
        runCatching { bass?.setStrength(s.fxBass.toShort()); bass?.enabled = s.fxEnabled && s.fxBass > 0 }
        runCatching { virt?.setStrength(s.fxVirt.toShort()); virt?.enabled = s.fxEnabled && s.fxVirt > 0 }
        runCatching { loud?.setTargetGain(s.fxLoud); loud?.enabled = s.fxEnabled && s.fxLoud > 0 }
        publish()
    }

    @Synchronized
    fun release() {
        releaseEffects()
        _state.value = FxState()
    }

    private fun releaseEffects() {
        runCatching { eq?.release() }; runCatching { bass?.release() }
        runCatching { virt?.release() }; runCatching { loud?.release() }
        eq = null; bass = null; virt = null; loud = null
    }

    private fun publish() {
        val s = store ?: return
        val e = eq
        val range = e?.bandLevelRange
        _state.value = FxState(
            available = e != null,
            enabled = s.fxEnabled,
            freqsHz = e?.let { x -> List(x.numberOfBands.toInt()) { i -> x.getCenterFreq(i.toShort()) / 1000 } } ?: emptyList(),
            minLevel = range?.getOrNull(0)?.toInt() ?: -1500,
            maxLevel = range?.getOrNull(1)?.toInt() ?: 1500,
            levels = e?.let { x -> List(x.numberOfBands.toInt()) { i -> runCatching { x.getBandLevel(i.toShort()).toInt() }.getOrDefault(0) } } ?: emptyList(),
            presets = e?.let { x -> List(x.numberOfPresets.toInt()) { i -> runCatching { x.getPresetName(i.toShort()) }.getOrDefault("Preset ${i + 1}") } } ?: emptyList(),
            preset = s.fxPreset,
            bass = s.fxBass, bassSupported = bass?.strengthSupported == true,
            virt = s.fxVirt, virtSupported = virt?.strengthSupported == true,
            loud = s.fxLoud
        )
    }

    @Synchronized
    fun setEnabled(on: Boolean) {
        val s = store ?: return
        s.fxEnabled = on
        runCatching { eq?.enabled = on }
        runCatching { bass?.enabled = on && s.fxBass > 0 }
        runCatching { virt?.enabled = on && s.fxVirt > 0 }
        runCatching { loud?.enabled = on && s.fxLoud > 0 }
        publish()
    }

    @Synchronized
    fun setBand(index: Int, level: Int) {
        val s = store ?: return
        val e = eq ?: return
        val clamped = level.coerceIn(_state.value.minLevel, _state.value.maxLevel)
        runCatching { e.setBandLevel(index.toShort(), clamped.toShort()) }
        s.fxPreset = -1
        s.fxLevels = List(e.numberOfBands.toInt()) { i -> runCatching { e.getBandLevel(i.toShort()).toInt() }.getOrDefault(0) }
        publish()
    }

    @Synchronized
    fun usePreset(index: Int) {
        val s = store ?: return
        val e = eq ?: return
        runCatching { e.usePreset(index.toShort()) }
        s.fxPreset = index
        s.fxLevels = List(e.numberOfBands.toInt()) { i -> runCatching { e.getBandLevel(i.toShort()).toInt() }.getOrDefault(0) }
        publish()
    }

    @Synchronized
    fun reset() {
        val s = store ?: return
        val e = eq
        e?.let { x -> for (i in 0 until x.numberOfBands.toInt()) runCatching { x.setBandLevel(i.toShort(), 0) } }
        s.fxPreset = -1; s.fxLevels = emptyList(); s.fxBass = 0; s.fxVirt = 0; s.fxLoud = 0
        runCatching { bass?.setStrength(0); bass?.enabled = false }
        runCatching { virt?.setStrength(0); virt?.enabled = false }
        runCatching { loud?.setTargetGain(0); loud?.enabled = false }
        publish()
    }

    @Synchronized
    fun setBass(v: Int) {
        val s = store ?: return
        s.fxBass = v
        runCatching { bass?.setStrength(v.toShort()); bass?.enabled = s.fxEnabled && v > 0 }
        publish()
    }

    @Synchronized
    fun setVirtualizer(v: Int) {
        val s = store ?: return
        s.fxVirt = v
        runCatching { virt?.setStrength(v.toShort()); virt?.enabled = s.fxEnabled && v > 0 }
        publish()
    }

    @Synchronized
    fun setLoudness(v: Int) {
        val s = store ?: return
        s.fxLoud = v
        runCatching { loud?.setTargetGain(v); loud?.enabled = s.fxEnabled && v > 0 }
        publish()
    }
}
