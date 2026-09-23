package com.tuckercr.zamzam

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat

class ListenerService : Service() {
    override fun onStartCommand(
        intent: Intent,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent.action) {
            ACTION_START_FOREGROUND -> {
                val wakeWord = intent.getStringExtra(EXTRA_WAKE_WORD)!!
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
                } catch (e: SecurityException) {
                    Log.e(TAG, "Cannot start microphone foreground service: ${e.message}")
                    stopSelf()
                }
            }
            ACTION_STOP_FOREGROUND -> {
                Log.i(TAG, "onStartCommand: stop foreground")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent): IBinder? = null

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
