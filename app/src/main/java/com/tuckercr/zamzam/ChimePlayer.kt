package com.tuckercr.zamzam

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChimePlayer
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private var mediaPlayer: MediaPlayer? = null

        fun play() {
            stop()
            val uri =
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: return

            runCatching {
                mediaPlayer =
                    MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes
                                .Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build(),
                        )
                        setDataSource(context, uri)
                        isLooping = false
                        setOnCompletionListener {
                            release()
                            if (mediaPlayer == it) mediaPlayer = null
                        }
                        prepare()
                        start()
                    }
            }
        }

        fun stop() {
            runCatching {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            }
            mediaPlayer = null
        }
    }
