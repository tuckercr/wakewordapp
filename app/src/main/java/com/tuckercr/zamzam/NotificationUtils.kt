package com.tuckercr.zamzam

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat

internal object NotificationUtils {
    private const val CHANNEL_ID_HOT_WORD = "hot_word_channel_id"
    private const val CHANNEL_ID_SERVICE = "main_channel_id"
    const val NOTIFICATION_ID_SERVICE = 42
    const val NOTIFICATION_ID_HOT_WORD = 43
    val VIBRATION_PATTERN = longArrayOf(0, 1000, 500, 1000, 500)

    fun initChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val serviceChannel =
            NotificationChannel(
                CHANNEL_ID_SERVICE,
                context.getString(R.string.channel_name_service),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_desc_service)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        nm.createNotificationChannel(serviceChannel)

        val hotWordChannel =
            NotificationChannel(
                CHANNEL_ID_HOT_WORD,
                context.getString(R.string.channel_name_hotword),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_desc_hotword)
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        nm.createNotificationChannel(hotWordChannel)
    }

    fun createServiceNotification(
        context: Context,
        wakeWord: String,
    ): Notification {
        initChannels(context)
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat
            .Builder(context, CHANNEL_ID_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_hearing)
            .setOngoing(true)
            .setContentTitle(context.getString(R.string.listening_for_hotword) + " \"$wakeWord\"")
            .setContentText(context.getString(R.string.the_test_app_is_still_running))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun createHotWordNotification(context: Context): Notification {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_HOT_WORD_DETECTED, true)
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat
            .Builder(context, CHANNEL_ID_HOT_WORD)
            .setSmallIcon(R.drawable.ic_stat_hearing)
            .setAutoCancel(true)
            .setContentTitle(context.getString(R.string.hotword_detected))
            .setContentText(context.getString(R.string.the_hotword_was_heard_click_to_return_to_test_app))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()
    }
}
