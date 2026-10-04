package com.tuckercr.hark

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.tuckercr.hark.prefs.PreferencesManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootReceiverEntryPoint {
        fun preferencesManager(): PreferencesManager
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val preferencesManager = EntryPointAccessors
            .fromApplication(context.applicationContext, BootReceiverEntryPoint::class.java)
            .preferencesManager()

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                startIfWakeWordConfigured(context, preferencesManager)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * On Android 11+ a microphone foreground service started from the background has no microphone
     * access (and Android 15+ throws for BOOT_COMPLETED), so ask the user to tap a notification
     * instead: it opens MainActivity, which starts the service from the foreground. Older versions
     * can start the listener directly.
     */
    internal suspend fun startIfWakeWordConfigured(
        context: Context,
        preferencesManager: PreferencesManager,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ) {
        // A wake word that was never changed is not stored; the app treats that as the default.
        val wakeWord = preferencesManager.wakeWordFlow.first() ?: context.getString(R.string.default_wake_word)
        if (wakeWord.isBlank()) return

        if (sdkInt >= Build.VERSION_CODES.R) {
            Log.i(TAG, "Boot: posting resume-listening notification for \"$wakeWord\"")
            NotificationUtils.showResumeListeningNotification(context, wakeWord)
            return
        }

        try {
            ContextCompat.startForegroundService(
                context,
                ListenerService.createStartForegroundIntent(context, wakeWord),
            )
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException extends IllegalStateException.
            Log.w(TAG, "Cannot start listener on boot: ${e.message}")
            NotificationUtils.showResumeListeningNotification(context, wakeWord)
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
