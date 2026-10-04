package com.tuckercr.hark

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Keeps the process alive as a microphone foreground service and runs the [ListenerEngine], so
 * listening does not depend on an Activity being around.
 */
@AndroidEntryPoint
class ListenerService : Service() {
    @Inject
    lateinit var engine: ListenerEngine

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_START_FOREGROUND -> {
                val wakeWord = intent.getStringExtra(EXTRA_WAKE_WORD) ?: return stopWithoutListening()
                Log.i(TAG, "onStartCommand: start foreground for \"$wakeWord\"")
                val notification = NotificationUtils.createServiceNotification(this, wakeWord)
                try {
                    ServiceCompat.startForeground(
                        this,
                        NotificationUtils.NOTIFICATION_ID_SERVICE,
                        notification,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        } else {
                            0
                        },
                    )
                    NotificationUtils.cancelResumeListeningNotification(this)
                } catch (e: SecurityException) {
                    Log.e(TAG, "Cannot start microphone foreground service: ${e.message}")
                    return stopWithoutListening()
                } catch (e: IllegalStateException) {
                    // ForegroundServiceStartNotAllowedException (API 31+) extends IllegalStateException:
                    // Android 15+ refuses a microphone foreground service started from BOOT_COMPLETED
                    // or any other background context. Ask the user to reopen the app instead.
                    Log.e(TAG, "Not allowed to start microphone foreground service: ${e.message}")
                    NotificationUtils.showResumeListeningNotification(this, wakeWord)
                    return stopWithoutListening()
                }
                engine.start()
            }
            ACTION_STOP_FOREGROUND -> {
                Log.i(TAG, "onStartCommand: stop foreground")
                engine.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_REDELIVER_INTENT
    }

    private fun stopWithoutListening(): Int {
        engine.stop()
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        engine.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "ListenerService"
        private const val ACTION_START_FOREGROUND = "action_start_foreground"
        private const val ACTION_STOP_FOREGROUND = "action_stop_foreground"
        private const val EXTRA_WAKE_WORD = "wake_word"

        fun createStartForegroundIntent(
            context: Context,
            wakeWord: String,
        ): Intent =
            Intent(context, ListenerService::class.java).apply {
                action = ACTION_START_FOREGROUND
                putExtra(EXTRA_WAKE_WORD, wakeWord)
            }

        fun createStopForegroundIntent(context: Context): Intent =
            Intent(context, ListenerService::class.java).apply {
                action = ACTION_STOP_FOREGROUND
            }
    }
}
