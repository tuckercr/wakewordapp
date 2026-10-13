package com.tuckercr.hark

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChimePlayer
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val handler = Handler(Looper.getMainLooper())
        private var mediaPlayer: MediaPlayer? = null
        private val stopRunnable = Runnable { stop() }

        fun play(settings: AlertSettings = AlertSettings()) {
            stop()
            val uri =
                when (val sound = settings.sound) {
                    is AlertSound.Silent -> return
                    is AlertSound.Custom -> runCatching { Uri.parse(sound.uri) }.getOrNull()
                    is AlertSound.Default -> null
                }
            // A custom sound that cannot be opened (deleted, permission revoked) falls back to the default.
            if (uri == null || !start(uri, settings.duration)) {
                defaultUri()?.let { start(it, settings.duration) }
            }
        }

        private fun defaultUri(): Uri? =
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        private fun start(
            uri: Uri,
            duration: AlertDuration,
        ): Boolean =
            runCatching {
                val player =
                    MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes
                                .Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build(),
                        )
                        setDataSource(context, uri)
                        isLooping = duration.loops
                        setOnCompletionListener {
                            it.release()
                            if (mediaPlayer == it) mediaPlayer = null
                        }
                        prepare()
                    }
                mediaPlayer = player
                player.start()
                duration.limitMillis?.let { handler.postDelayed(stopRunnable, it) }
            }.isSuccess

        fun stop() {
            handler.removeCallbacks(stopRunnable)
            runCatching {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            }
            mediaPlayer = null
        }
    }
