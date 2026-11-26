package com.noirmusic.app

import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var player: ExoPlayer? = null
    private lateinit var store: Store
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    private val saveTicker = object : Runnable {
        override fun run() {
            saveState()
            if (player?.isPlaying == true) handler.postDelayed(this, 5000)
        }
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Store.K_SKIP) player?.skipSilenceEnabled = store.skipSilence
    }

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        val exo = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        exo.skipSilenceEnabled = store.skipSilence
        player = exo
        SleepTimer.player = exo
        AudioFx.attach(this, exo.audioSessionId)

        exo.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                AudioFx.attach(this@PlaybackService, audioSessionId)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                saveState()
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = saveState()

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                saveState()
                handler.removeCallbacks(saveTicker)
                if (isPlaying) handler.postDelayed(saveTicker, 5000)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                    SleepTimer.onEndOfTrackReached()
                }
            }
        })

        val activityIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, exo).setSessionActivity(activityIntent).build()
        setMediaNotificationProvider(DefaultMediaNotificationProvider(this).apply { setSmallIcon(R.drawable.ic_stat_noir) })
        store.sp.registerOnSharedPreferenceChangeListener(prefListener)
    }

    private fun saveState() {
        val p = player ?: return
        if (p.mediaItemCount == 0) return
        val ids = (0 until p.mediaItemCount).mapNotNull { p.getMediaItemAt(it).mediaId.toLongOrNull() }
        store.lastQueue = ids
        store.lastCurrentId = p.currentMediaItem?.mediaId?.toLongOrNull() ?: -1L
        store.lastPosition = p.currentPosition.coerceAtLeast(0)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        saveState()
        handler.removeCallbacksAndMessages(null)
        store.sp.unregisterOnSharedPreferenceChangeListener(prefListener)
        SleepTimer.cancel()
        SleepTimer.player = null
        AudioFx.release()
        scope.cancel()
        session?.run { player.release(); release() }
        session = null
        player = null
        super.onDestroy()
    }
}
