package com.tuckercr.hark

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
     * On Android 11+ a microphone foreground service cannot be started from the background (and
     * Android 15+ throws for BOOT_COMPLETED), so ask the user to tap a notification instead. Older
     * versions can start the listener directly.
     */
    internal suspend fun startIfWakeWordConfigured(
        context: Context,
        preferencesManager: PreferencesManager,
        sdkInt: Int = Build.VERSION.SDK_INT,
        postResumeNotification: (Context, String) -> Unit = ::postResumeNotification,
    ) {
        // A wake word that was never changed is not stored; the app treats that as the default.
        val wakeWord = preferencesManager.wakeWordFlow.first() ?: context.getString(R.string.default_wake_word)
        if (wakeWord.isBlank()) return
        if (sdkInt >= Build.VERSION_CODES.R) {
            postResumeNotification(context, wakeWord)
        } else {
            ContextCompat.startForegroundService(
                context,
                ListenerService.createStartForegroundIntent(context, wakeWord),
            )
        }
    }

    private fun postResumeNotification(
        context: Context,
        wakeWord: String,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.notify(
            NotificationUtils.NOTIFICATION_ID_RESUME,
            NotificationUtils.createResumeListeningNotification(context, wakeWord),
        )
    }
}
