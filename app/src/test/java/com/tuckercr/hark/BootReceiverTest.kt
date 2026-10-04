package com.tuckercr.hark

import android.content.Context
import android.content.pm.PackageManager
import com.tuckercr.hark.prefs.PreferencesManager
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class BootReceiverTest {
    private lateinit var context: Context
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var receiver: BootReceiver

    // Android 10: a microphone foreground service may still be started directly at boot.
    private val direct = 29

    // Android 11: the microphone is unavailable to a service started from the background.
    private val restricted = 30

    @Before
    fun setUp() {
        preferencesManager = mockk()
        context = mockk(relaxed = true)
        every { context.checkPermission(any(), any(), any()) } returns PackageManager.PERMISSION_GRANTED
        receiver = BootReceiver()
        mockkObject(NotificationUtils)
        every { NotificationUtils.showResumeListeningNotification(any(), any()) } just runs
    }

    @After
    fun tearDown() {
        unmockkObject(NotificationUtils)
    }

    // region — Android 11 and later: ask the user to resume

    @Test
    fun `on Android 11 and later posts a resume notification instead of starting a mic service`() =
        runBlocking {
            every { preferencesManager.wakeWordFlow } returns flowOf("hark")
            receiver.startIfWakeWordConfigured(context, preferencesManager, sdkInt = restricted)
            verify(exactly = 0) { context.startService(any()) }
            verify(exactly = 0) { context.startForegroundService(any()) }
            verify { NotificationUtils.showResumeListeningNotification(context, "hark") }
        }

    @Test
    fun `on Android 14 and later it also posts a resume notification`() =
        runBlocking {
            every { preferencesManager.wakeWordFlow } returns flowOf("hark")
            receiver.startIfWakeWordConfigured(context, preferencesManager, sdkInt = 34)
            verify(exactly = 0) { context.startService(any()) }
            verify { NotificationUtils.showResumeListeningNotification(context, "hark") }
        }

    @Test
    fun `on Android 11 and later nothing is posted when there is no wake word at all`() =
        runBlocking {
            every { preferencesManager.wakeWordFlow } returns flowOf(null)
            every { context.getString(R.string.default_wake_word) } returns ""
            receiver.startIfWakeWordConfigured(context, preferencesManager, sdkInt = restricted)
            verify(exactly = 0) { NotificationUtils.showResumeListeningNotification(any(), any()) }
        }

    // endregion

    // region — Android 10 and earlier: start directly

    @Test
    fun `startIfWakeWordConfigured starts service when wake word is configured`() =
        runBlocking {
            every { preferencesManager.wakeWordFlow } returns flowOf("hark")
            receiver.startIfWakeWordConfigured(context, preferencesManager, sdkInt = direct)
            verify { context.startService(any()) }
            verify(exactly = 0) { NotificationUtils.showResumeListeningNotification(any(), any()) }
        }

    @Test
    fun `falls back to a resume notification when the service start is not allowed`() =
        runBlocking {
            every { preferencesManager.wakeWordFlow } returns flowOf("hark")
            every { context.startService(any()) } throws IllegalStateException("not allowed")
            every { context.startForegroundService(any()) } throws IllegalStateException("not allowed")
            receiver.startIfWakeWordConfigured(context, preferencesManager, sdkInt = direct)
            verify { NotificationUtils.showResumeListeningNotification(context, "hark") }
        }

    // endregion

    // region — which wake word

    @Test
    fun `startIfWakeWordConfigured uses the default wake word when none was ever saved`() =
        runBlocking {
            every { preferencesManager.wakeWordFlow } returns flowOf(null)
            every { context.getString(R.string.default_wake_word) } returns "hark"
            receiver.startIfWakeWordConfigured(context, preferencesManager, sdkInt = direct)
            verify { context.startService(any()) }
        }

    @Test
    fun `startIfWakeWordConfigured does nothing when nothing is saved and there is no default`() =
        runBlocking {
            every { preferencesManager.wakeWordFlow } returns flowOf(null)
            every { context.getString(R.string.default_wake_word) } returns ""
            receiver.startIfWakeWordConfigured(context, preferencesManager, sdkInt = direct)
            verify(exactly = 0) { context.startService(any()) }
        }

    @Test
    fun `startIfWakeWordConfigured does nothing when wake word is blank`() =
        runBlocking {
            every { preferencesManager.wakeWordFlow } returns flowOf("   ")
            receiver.startIfWakeWordConfigured(context, preferencesManager, sdkInt = direct)
            verify(exactly = 0) { context.startService(any()) }
        }

    // endregion
}
