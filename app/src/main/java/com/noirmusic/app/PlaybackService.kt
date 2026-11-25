package com.noirmusic.app

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    companion object { @Volatile var activeAudioSessionId: Int = 0 }
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(), true
            )
            setHandleAudioBecomingNoisy(true)
        }.also { player ->
            activeAudioSessionId = player.audioSessionId
            player.addListener(object : Player.Listener {
                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    activeAudioSessionId = audioSessionId
                }
            })
        }
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run { player.release(); release() }
        activeAudioSessionId = 0
        mediaSession = null
        super.onDestroy()
    }
}
