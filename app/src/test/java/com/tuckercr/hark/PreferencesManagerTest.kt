package com.tuckercr.hark

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.tuckercr.hark.prefs.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreferencesManagerTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun createManager(): PreferencesManager =
        PreferencesManager(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Job()),
                produceFile = { tmpFolder.newFile("prefs_${System.nanoTime()}.preferences_pb") },
            ),
        )

    // region — wakeWordFlow

    @Test
    fun `wakeWordFlow emits null when no preference is set`() = runBlocking<Unit> {
        assertNull(createManager().wakeWordFlow.first())
    }

    @Test
    fun `updateWakeWord persists the value`() = runBlocking<Unit> {
        val pm = createManager()
        pm.updateWakeWord("hark")
        assertEquals("hark", pm.wakeWordFlow.first())
    }

    @Test
    fun `updateWakeWord overwrites the previous value`() = runBlocking<Unit> {
        val pm = createManager()
        pm.updateWakeWord("hello")
        pm.updateWakeWord("world")
        assertEquals("world", pm.wakeWordFlow.first())
    }

    // endregion

    // region — onboardingCompleteFlow

    @Test
    fun `onboardingCompleteFlow emits false when no preference is set`() = runBlocking<Unit> {
        assertFalse(createManager().onboardingCompleteFlow.first())
    }

    @Test
    fun `setOnboardingComplete true persists and emits true`() = runBlocking<Unit> {
        val pm = createManager()
        pm.setOnboardingComplete(true)
        assertTrue(pm.onboardingCompleteFlow.first())
    }

    @Test
    fun `setOnboardingComplete false after true emits false`() = runBlocking<Unit> {
        val pm = createManager()
        pm.setOnboardingComplete(true)
        pm.setOnboardingComplete(false)
        assertFalse(pm.onboardingCompleteFlow.first())
    }

    // endregion

    // region — clear

    @Test
    fun `clear removes the wake word`() = runBlocking<Unit> {
        val pm = createManager()
        pm.updateWakeWord("hark")
        pm.clear()
        assertNull(pm.wakeWordFlow.first())
    }

    @Test
    fun `clear resets onboarding to false`() = runBlocking<Unit> {
        val pm = createManager()
        pm.setOnboardingComplete(true)
        pm.clear()
        assertFalse(pm.onboardingCompleteFlow.first())
    }

    // endregion

    // region — preference keys

    @Test
    fun `WAKE_WORD key name is wake_word`() {
        assertEquals("wake_word", PreferencesManager.PreferencesKeys.WAKE_WORD.name)
    }

    @Test
    fun `ONBOARDING_COMPLETE key name is onboarding_complete`() {
        assertEquals("onboarding_complete", PreferencesManager.PreferencesKeys.ONBOARDING_COMPLETE.name)
    }

    // endregion
}
