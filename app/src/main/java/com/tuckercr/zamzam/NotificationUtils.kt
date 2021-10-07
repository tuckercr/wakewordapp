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

/**
 * Notification helper methods
 *
 * @author colintucker
 */
internal object NotificationUtils {
    private const val TAG = "NotificationUtils"
    private const val CHANNEL_ID_HOT_WORD = "hot_word_channel_id"
    private const val CHANNEL_ID_SERVICE = "main_channel_id"
    const val NOTIFICATION_ID_SERVICE = 42
    const val NOTIFICATION_ID_HOT_WORD = 43
    val VIBRATION_PATTERN = longArrayOf(0, 1000, 500, 1000, 500)
    fun initChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) {
            return
        }
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // TODO we could have a custom sound
//        Uri alertSoundUri = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.xxxxxxxx);
//        AudioAttributes audioAttributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build();

        // The alert channel is for ongoing notifications
        val mainChannel = NotificationChannel(
            CHANNEL_ID_SERVICE,
            context.getString(R.string.channel_name_service), NotificationManager.IMPORTANCE_HIGH
        )
        mainChannel.description = context.getString(R.string.channel_desc_service)
        // mainChannel.setSound(alertSoundUri, audioAttributes);
        mainChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        notificationManager.createNotificationChannel(mainChannel)

        // The alert channel is for hot word detection notifications
        val hotWordChannel = NotificationChannel(
            CHANNEL_ID_HOT_WORD,
            context.getString(R.string.channel_name_hotword), NotificationManager.IMPORTANCE_HIGH
        )
        hotWordChannel.description = context.getString(R.string.channel_desc_hotword)
        hotWordChannel.enableLights(true)
        hotWordChannel.lightColor = Color.RED
        hotWordChannel.enableVibration(true)
        hotWordChannel.vibrationPattern = VIBRATION_PATTERN
        hotWordChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        notificationManager.createNotificationChannel(hotWordChannel)
    }

    /**
     * Creates the running service notification
     */
    fun createServiceNotification(context: Context, wakeWord: String): Notification {
        initChannels(context)
        val intent = Intent(context, HotWordActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0,
            intent, PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_hearing)
            .setOngoing(true)
            .setContentTitle(context.getString(R.string.listening_for_hotword) + " \"" + wakeWord + "\"")
            .setContentText(context.getString(R.string.the_test_app_is_still_running))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE) //.setFullScreenIntent(pendingIntent, true);
            .setContentIntent(pendingIntent)
        return notificationBuilder.build()
    }

    fun createHotWordNotification(context: Context): Notification {
        val intent = Intent(context, HotWordActivity::class.java)
        intent.putExtra(HotWordActivity.EXTRA_OPEN_HOT_WORD_DETECTED, true)
        val pendingIntent = PendingIntent.getActivity(
            context, 0,
            intent, PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID_HOT_WORD)
            .setSmallIcon(R.drawable.ic_stat_hearing)
            .setOngoing(true)
            .setAutoCancel(true)
            .setContentTitle(context.getString(R.string.hotword_detected))
            .setContentText(context.getString(R.string.the_hotword_was_heard_click_to_return_to_test_app))
            .setPriority(NotificationCompat.PRIORITY_HIGH) //                                    .setFullScreenIntent(pendingIntent, true);
            .setContentIntent(pendingIntent)
        return notificationBuilder.build()
    }
}