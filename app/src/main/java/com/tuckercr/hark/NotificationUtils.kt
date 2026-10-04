package com.tuckercr.hark

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

internal object NotificationUtils {
    private const val LEGACY_CHANNEL_ID_HOT_WORD = "hot_word_channel_id"
    private const val CHANNEL_ID_HOT_WORD = "hot_word_silent_channel_id"
    private const val CHANNEL_ID_SERVICE = "main_channel_id"
    const val NOTIFICATION_ID_SERVICE = 42
    const val NOTIFICATION_ID_HOT_WORD = 43
    const val NOTIFICATION_ID_RESUME = 44
    private const val CHANNEL_ID_RESUME = "resume_listening_channel_id"
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

        // The old channel played the default notification sound on top of the chime, and a channel's
        // sound cannot be changed after creation, so it is replaced by a silent one.
        nm.deleteNotificationChannel(LEGACY_CHANNEL_ID_HOT_WORD)
        val hotWordChannel =
            NotificationChannel(
                CHANNEL_ID_HOT_WORD,
                context.getString(R.string.channel_name_hotword),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_desc_hotword)
                setSound(null, null)
                enableLights(true)
                lightColor = ContextCompat.getColor(context, R.color.hark_green)
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        nm.createNotificationChannel(hotWordChannel)

        val resumeChannel =
            NotificationChannel(
                CHANNEL_ID_RESUME,
                context.getString(R.string.channel_name_resume),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.channel_desc_resume)
                setSound(null, null)
                enableVibration(false)
            }
        nm.createNotificationChannel(resumeChannel)
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
            .setColor(ContextCompat.getColor(context, R.color.hark_green))
            .setOngoing(true)
            .setContentTitle(context.getString(R.string.listening_for_hotword) + " \"$wakeWord\"")
            .setContentText(context.getString(R.string.the_test_app_is_still_running))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .build()
    }

    /**
     * Android 11+ will not give a microphone foreground service started from the background (such
     * as after boot) access to the microphone, and Android 15+ throws. So the user is asked to tap
     * this notification, which opens the app and starts listening from the foreground.
     */
    fun showResumeListeningNotification(
        context: Context,
        wakeWord: String,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        initChannels(context)
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                NOTIFICATION_ID_RESUME,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID_RESUME)
                .setSmallIcon(R.drawable.ic_stat_hearing)
                .setColor(ContextCompat.getColor(context, R.color.hark_green))
                .setAutoCancel(true)
                .setContentTitle(context.getString(R.string.tap_to_resume_listening))
                .setContentText(context.getString(R.string.resume_listening_text, wakeWord))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(pendingIntent)
                .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_RESUME, notification)
    }

    fun cancelResumeListeningNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID_RESUME)
    }

    fun createHotWordNotification(
        context: Context,
        action: DetectionAction = DetectionAction.Default,
    ): Notification {
        val launchIntent =
            when (action) {
                is DetectionAction.LaunchApp ->
                    context.packageManager.getLaunchIntentForPackage(action.packageName)
                        ?: Intent(context, MainActivity::class.java).apply {
                            putExtra(MainActivity.EXTRA_OPEN_HOT_WORD_DETECTED, true)
                        }
                is DetectionAction.Default ->
                    Intent(context, MainActivity::class.java).apply {
                        putExtra(MainActivity.EXTRA_OPEN_HOT_WORD_DETECTED, true)
                    }
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat
            .Builder(context, CHANNEL_ID_HOT_WORD)
            .setSmallIcon(R.drawable.ic_stat_hearing)
            .setColor(ContextCompat.getColor(context, R.color.hark_green))
            .setAutoCancel(true)
            .setContentTitle(context.getString(R.string.hotword_detected))
            .setContentText(context.getString(R.string.the_hotword_was_heard_click_to_return_to_test_app))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()
    }
}
