package com.tuckercr.zamzam

import android.app.Activity
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * Foreground Service for sticky notifications
 *
 * @author colintucker
 */
class ListenerService : Service() {
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (ACTION_START_FOREGROUND == intent.action) {
            Log.i(TAG, "onStartCommand: Received Start Foreground Intent")
            val wakeWord = intent.getStringExtra(EXTRA_WAKE_WORD)!!
            val notification = NotificationUtils.createServiceNotification(this, wakeWord)
            startForeground(NotificationUtils.NOTIFICATION_ID_SERVICE, notification)
        } else if (ACTION_STOP_FOREGROUND == intent.action) {
            Log.i(TAG, "onStartCommand: Received Stop Foreground Intent")
            stopForeground(true)
            stopSelf()
        }

        // This was START_STICKY but when service is recreated it causes the intent to be null
        return START_REDELIVER_INTENT
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate() called")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy() called")
    }

    override fun onBind(intent: Intent): IBinder? {
        // Used only in case of bound services.
        return null
    }

    companion object {
        private const val TAG = "ListeningService"
        private const val ACTION_START_FOREGROUND = "action_start_foreground"
        private const val ACTION_STOP_FOREGROUND = "action_stop_foreground"
        private const val EXTRA_WAKE_WORD = "wake_word"
        fun createStartForegroundIntent(activity: Activity, wakeWord: String): Intent {
            val intent = Intent(activity, ListenerService::class.java)
            intent.action = ACTION_START_FOREGROUND
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            intent.putExtra(EXTRA_WAKE_WORD, wakeWord)
            return intent
        }

        fun createStopForegroundIntent(activity: Activity): Intent {
            val stopForegroundIntent = Intent(activity, ListenerService::class.java)
            stopForegroundIntent.action = ACTION_STOP_FOREGROUND
            return stopForegroundIntent
        }
    }
}