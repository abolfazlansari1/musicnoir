package com.noirmusic.app

import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Runs inside the app process so it keeps counting after the screen is closed. */
object SleepTimer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    var player: Player? = null

    private val _remaining = MutableStateFlow(0)
    val remainingSec: StateFlow<Int> = _remaining
    private val _endOfTrack = MutableStateFlow(false)
    val endOfTrack: StateFlow<Boolean> = _endOfTrack

    fun startMinutes(minutes: Int) {
        cancel()
        _remaining.value = minutes * 60
        job = scope.launch {
            while (_remaining.value > 0) {
                delay(1000)
                _remaining.value = _remaining.value - 1
                val left = _remaining.value
                if (left in 1..10) player?.volume = left / 10f
            }
            player?.pause()
            player?.volume = 1f
        }
    }

    fun startEndOfTrack() {
        cancel()
        _endOfTrack.value = true
        (player as? androidx.media3.exoplayer.ExoPlayer)?.pauseAtEndOfMediaItems = true
    }

    fun onEndOfTrackReached() {
        _endOfTrack.value = false
        (player as? androidx.media3.exoplayer.ExoPlayer)?.pauseAtEndOfMediaItems = false
    }

    fun cancel() {
        job?.cancel(); job = null
        _remaining.value = 0
        if (_endOfTrack.value) onEndOfTrackReached()
        player?.volume = 1f
    }
}
