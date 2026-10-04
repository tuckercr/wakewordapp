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

    @Test
    fun `on API 34+ posts resume notification instead of starting mic service`() =
        runBlocking {
            every { preferencesManager.wakeWordFlow } returns flowOf("hark")
            receiver.startIfWakeWordConfigured(context, preferencesManager, sdkInt = 34)
            verify(exactly = 0) { context.startService(any()) }
            verify(exactly = 0) { context.startForegroundService(any()) }
            verify { NotificationUtils.showResumeListeningNotification(context, "hark") }
        }

    @Test
    fun `falls back to resume notification when service start is not allowed`() =
        runBlocking {
            every { preferencesManager.wakeWordFlow } returns flowOf("hark")
            every { context.startService(any()) } throws IllegalStateException("not allowed")
            every { context.startForegroundService(any()) } throws IllegalStateException("not allowed")
            receiver.startIfWakeWordConfigured(context, preferencesManager, sdkInt = 33)
            verify { NotificationUtils.showResumeListeningNotification(context, "hark") }
        }

    @Test
    fun `startIfWakeWordConfigured starts service when wake word is configured`() =
        runBlocking {
            every { preferencesManager.wakeWordFlow } returns flowOf("hark")
            receiver.startIfWakeWordConfigured(context, preferencesManager, sdkInt = 33)
            verify { context.startService(any()) }
        }

    @Test
    fun `startIfWakeWordConfigured does nothing when wake word is null`() =
        runBlocking {
            every { preferencesManager.wakeWordFlow } returns flowOf(null)
            receiver.startIfWakeWordConfigured(context, preferencesManager, sdkInt = 33)
            verify(exactly = 0) { context.startService(any()) }
        }

    @Test
    fun `startIfWakeWordConfigured does nothing when wake word is blank`() =
        runBlocking {
            every { preferencesManager.wakeWordFlow } returns flowOf("   ")
            receiver.startIfWakeWordConfigured(context, preferencesManager, sdkInt = 33)
            verify(exactly = 0) { context.startService(any()) }
        }
}
