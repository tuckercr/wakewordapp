package com.tuckercr.hark

import android.content.Context
import android.content.pm.PackageManager
import com.tuckercr.hark.prefs.PreferencesManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
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
    fun `startIfWakeWordConfigured does nothing when wake word is null`() =
        runBlocking {
            every { preferencesManager.wakeWordFlow } returns flowOf(null)
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
}
