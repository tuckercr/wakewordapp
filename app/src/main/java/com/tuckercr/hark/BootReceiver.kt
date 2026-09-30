package com.tuckercr.hark

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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

    internal suspend fun startIfWakeWordConfigured(
        context: Context,
        preferencesManager: PreferencesManager,
    ) {
        val wakeWord = preferencesManager.wakeWordFlow.first()
        if (!wakeWord.isNullOrBlank()) {
            ContextCompat.startForegroundService(
                context,
                ListenerService.createStartForegroundIntent(context, wakeWord),
            )
        }
    }
}
