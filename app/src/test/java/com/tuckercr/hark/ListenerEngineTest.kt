package com.tuckercr.hark

import android.app.Application
import android.content.pm.PackageManager
import com.tuckercr.hark.prefs.PreferencesManager
import edu.cmu.pocketsphinx.Hypothesis
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The real recognizer needs the native PocketSphinx library, so these tests cover everything around
 * it: state transitions, detection, the cooldown, mute, and the start/stop lifecycle without a
 * microphone permission. Recognition callbacks are invoked directly through [ListenerEngine.recognitionListener].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListenerEngineTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var application: Application
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var chimePlayer: ChimePlayer
    private lateinit var engine: ListenerEngine
    private var clock = 0L

    @Before
    fun setUp() {
        application = mockk(relaxed = true)
        every { application.applicationContext } returns application
        preferencesManager = mockk(relaxed = true)
        chimePlayer = mockk(relaxed = true)
        every { preferencesManager.wakeWordFlow } returns flowOf("testword")
        every { preferencesManager.sensitivityFlow } returns flowOf(null)
        every { preferencesManager.detectionActionFlow } returns flowOf(DetectionAction.Default)
        every { preferencesManager.alertSettingsFlow } returns flowOf(AlertSettings())
        denyPermission()
        engine = newEngine()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun newEngine() =
        ListenerEngine(
            application = application,
            preferencesManager = preferencesManager,
            chimePlayer = chimePlayer,
            scope = CoroutineScope(Dispatchers.Main),
            ioDispatcher = UnconfinedTestDispatcher(),
            now = { clock },
        )

    private fun hypothesis(text: String?) = mockk<Hypothesis>().also { every { it.hypstr } returns text }

    // region — initial state

    @Test
    fun `starts off, unmuted and not triggered`() {
        assertEquals(EngineState(MicState.OFF, null, false), engine.state.value)
        assertFalse(engine.isRunning)
    }

    // endregion

    // region — recognition callbacks

    @Test
    fun `onBeginningOfSpeech sets micState to SPEAKING`() {
        engine.recognitionListener.onBeginningOfSpeech()
        assertEquals(MicState.SPEAKING, engine.state.value.micState)
    }

    @Test
    fun `onEndOfSpeech sets micState to LISTENING`() {
        engine.recognitionListener.onEndOfSpeech()
        assertEquals(MicState.LISTENING, engine.state.value.micState)
    }

    @Test
    fun `onResult sets micState to LISTENING`() {
        engine.recognitionListener.onResult(null)
        assertEquals(MicState.LISTENING, engine.state.value.micState)
    }

    @Test
    fun `onPartialResult with null hypothesis does nothing`() {
        engine.start()
        engine.recognitionListener.onPartialResult(null)
        assertNull(engine.state.value.wakeWordTriggered)
    }

    @Test
    fun `onPartialResult with null hypstr does nothing`() {
        engine.start()
        engine.recognitionListener.onPartialResult(hypothesis(null))
        assertNull(engine.state.value.wakeWordTriggered)
    }

    @Test
    fun `onPartialResult with matching word triggers detection`() {
        engine.start()
        engine.recognitionListener.onPartialResult(hypothesis("testword"))
        assertEquals("testword", engine.state.value.wakeWordTriggered)
        assertEquals(MicState.OFF, engine.state.value.micState)
        verify { chimePlayer.play(AlertSettings()) }
    }

    @Test
    fun `onPartialResult with text containing the wake word triggers detection`() {
        engine.start()
        engine.recognitionListener.onPartialResult(hypothesis("please testword now"))
        assertEquals("please testword now", engine.state.value.wakeWordTriggered)
    }

    @Test
    fun `onPartialResult with non-matching text does not trigger detection`() {
        engine.start()
        engine.recognitionListener.onPartialResult(hypothesis("completely different"))
        assertNull(engine.state.value.wakeWordTriggered)
        verify(exactly = 0) { chimePlayer.play(any()) }
    }

    @Test
    fun `detection uses the saved alert settings`() {
        val saved = AlertSettings(AlertSound.Silent, AlertDuration.SECONDS_30)
        every { preferencesManager.alertSettingsFlow } returns flowOf(saved)
        val e = newEngine()
        e.start()
        e.recognitionListener.onPartialResult(hypothesis("testword"))
        verify { chimePlayer.play(saved) }
    }

    @Test
    fun `nothing is detected before a wake word is configured`() {
        engine.recognitionListener.onPartialResult(hypothesis("testword"))
        assertNull(engine.state.value.wakeWordTriggered)
    }

    // endregion

    // region — cooldown

    @Test
    fun `a repeat inside the cooldown is ignored`() {
        engine.start()
        clock = 1_000
        engine.recognitionListener.onPartialResult(hypothesis("testword"))
        clock = 1_000 + ListenerEngine.COOLDOWN_MS - 1
        engine.recognitionListener.onPartialResult(hypothesis("testword"))
        verify(exactly = 1) { chimePlayer.play(any()) }
    }

    @Test
    fun `after the cooldown the listener is armed again without anyone dismissing a screen`() {
        engine.start()
        clock = 1_000
        engine.recognitionListener.onPartialResult(hypothesis("testword"))
        clock = 1_000 + ListenerEngine.COOLDOWN_MS
        engine.recognitionListener.onPartialResult(hypothesis("testword"))
        verify(exactly = 2) { chimePlayer.play(any()) }
    }

    // endregion

    // region — clearTriggered / mute

    @Test
    fun `clearTriggered stops the chime and clears the detection`() {
        engine.start()
        engine.recognitionListener.onPartialResult(hypothesis("testword"))
        engine.clearTriggered()
        assertNull(engine.state.value.wakeWordTriggered)
        verify { chimePlayer.stop() }
    }

    @Test
    fun `setMuted true mutes and stops the chime`() {
        engine.setMuted(true)
        assertTrue(engine.state.value.isMuted)
        verify { chimePlayer.stop() }
    }

    @Test
    fun `setMuted with the same value changes nothing`() {
        val before = engine.state.value
        engine.setMuted(false)
        assertEquals(before, engine.state.value)
    }

    @Test
    fun `starting while muted stays off instead of reporting a missing permission`() {
        engine.setMuted(true)
        engine.start()
        // Without the mute check this would be DISABLED_NO_PERMISSION, since permission is denied here.
        assertEquals(MicState.OFF, engine.state.value.micState)
    }

    @Test
    fun `unmuting a running engine tries to listen again`() {
        engine.setMuted(true)
        engine.start()
        engine.setMuted(false)
        assertEquals(MicState.DISABLED_NO_PERMISSION, engine.state.value.micState)
    }

    @Test
    fun `mute survives stop and start`() {
        engine.setMuted(true)
        engine.start()
        engine.stop()
        assertTrue(engine.state.value.isMuted)
    }

    // endregion

    // region — lifecycle

    @Test
    fun `start without microphone permission reports DISABLED_NO_PERMISSION`() {
        engine.start()
        assertTrue(engine.isRunning)
        assertEquals(MicState.DISABLED_NO_PERMISSION, engine.state.value.micState)
    }

    @Test
    fun `start with permission but a blank wake word does not listen`() {
        grantPermission()
        every { preferencesManager.wakeWordFlow } returns flowOf(null)
        every { application.getString(any<Int>()) } returns ""
        val e = newEngine()
        e.start()
        assertEquals(MicState.OFF, e.state.value.micState)
    }

    @Test
    fun `stop turns the mic off, stops the chime and is no longer running`() {
        engine.start()
        engine.stop()
        assertFalse(engine.isRunning)
        assertEquals(MicState.OFF, engine.state.value.micState)
        verify { chimePlayer.stop() }
    }

    @Test
    fun `refresh does nothing when the engine is not running`() {
        val before = engine.state.value
        engine.refresh()
        assertEquals(before, engine.state.value)
    }

    @Test
    fun `refresh recovers a running engine once permission is granted`() {
        engine.start()
        assertEquals(MicState.DISABLED_NO_PERMISSION, engine.state.value.micState)
        grantPermission()
        every { preferencesManager.wakeWordFlow } returns flowOf(null)
        every { application.getString(any<Int>()) } returns ""
        engine.refresh()
        // Permission is granted but there is no wake word, so it stops short of the real recognizer.
        assertEquals(MicState.DISABLED_NO_PERMISSION, engine.state.value.micState)
    }

    // endregion

    private fun grantPermission() {
        every { application.checkPermission(any(), any(), any()) } returns PackageManager.PERMISSION_GRANTED
        every { application.checkSelfPermission(any()) } returns PackageManager.PERMISSION_GRANTED
    }

    private fun denyPermission() {
        every { application.checkPermission(any(), any(), any()) } returns PackageManager.PERMISSION_DENIED
        every { application.checkSelfPermission(any()) } returns PackageManager.PERMISSION_DENIED
    }
}
