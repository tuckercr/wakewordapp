package com.tuckercr.hark

import android.content.Context
import android.content.pm.PackageManager
import com.tuckercr.hark.prefs.PreferencesManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class BootReceiverTest {

    private lateinit var context: Context
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var receiver: BootReceiver

    @Before
    fun setUp() {
        preferencesManager = mockk()
        context = mockk(relaxed = true)
        every { context.checkPermission(any(), any(), any()) } returns PackageManager.PERMISSION_GRANTED
        receiver = BootReceiver()
    }

    @Test
    fun `startIfWakeWordConfigured starts service when wake word is configured`() =
        runBlocking {
            every { preferencesManager.wakeWordFlow } returns flowOf("hark")
            receiver.startIfWakeWordConfigured(context, preferencesManager)
            verify { context.startService(any()) }
        }

    @Test
    fun `startIfWakeWordConfigured uses the default wake word when none was ever saved`() =
        runBlocking {
            every { preferencesManager.wakeWordFlow } returns flowOf(null)
            every { context.getString(R.string.default_wake_word) } returns "hark"
            receiver.startIfWakeWordConfigured(context, preferencesManager)
            verify { context.startService(any()) }
        }

    @Test
    fun `startIfWakeWordConfigured does nothing when nothing is saved and there is no default`() =
        runBlocking {
            every { preferencesManager.wakeWordFlow } returns flowOf(null)
            every { context.getString(R.string.default_wake_word) } returns ""
            receiver.startIfWakeWordConfigured(context, preferencesManager)
            verify(exactly = 0) { context.startService(any()) }
        }

    @Test
    fun `startIfWakeWordConfigured does nothing when wake word is blank`() =
        runBlocking {
            every { preferencesManager.wakeWordFlow } returns flowOf("   ")
            receiver.startIfWakeWordConfigured(context, preferencesManager)
            verify(exactly = 0) { context.startService(any()) }
        }

    @Test
    fun `on Android 11 and later it asks the user to resume instead of starting a microphone service`() =
        runBlocking {
            every { preferencesManager.wakeWordFlow } returns flowOf("hark")
            var notifiedWith: String? = null
            receiver.startIfWakeWordConfigured(context, preferencesManager, sdkInt = 30) { _, word -> notifiedWith = word }
            assertEquals("hark", notifiedWith)
            verify(exactly = 0) { context.startService(any()) }
        }

    @Test
    fun `on Android 10 and earlier it starts the listener directly`() =
        runBlocking {
            every { preferencesManager.wakeWordFlow } returns flowOf("hark")
            var notified = false
            receiver.startIfWakeWordConfigured(context, preferencesManager, sdkInt = 29) { _, _ -> notified = true }
            assertFalse(notified)
            verify { context.startService(any()) }
        }

    @Test
    fun `on Android 11 and later nothing is posted when no wake word is configured`() =
        runBlocking {
            every { preferencesManager.wakeWordFlow } returns flowOf(null)
            var notified = false
            receiver.startIfWakeWordConfigured(context, preferencesManager, sdkInt = 34) { _, _ -> notified = true }
            assertFalse(notified)
        }
}
