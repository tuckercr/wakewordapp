package com.tuckercr.hark

import android.app.Application
import android.content.pm.PackageManager
import com.tuckercr.hark.prefs.PreferencesManager
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * ContextCompat.checkSelfPermission → context.checkPermission(permission, pid, uid) via
 * PermissionChecker (SDK_INT=0 path in JVM tests). Stubbing checkPermission on the mock
 * Application gives full control over permission results without needing mockkStatic.
 *
 * returnDefaultValues=true (build.gradle) makes Android Log/Process stubs return 0 instead
 * of throwing, so coroutines in the ViewModel's init block run to completion.
 */
class ListenerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var application: Application
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var chimePlayer: ChimePlayer
    private lateinit var dictionaryRepository: DictionaryRepository
    private lateinit var engine: ListenerEngine
    private lateinit var engineState: MutableStateFlow<EngineState>
    private lateinit var viewModel: ListenerViewModel

    @Before
    fun setUp() {
        application = mockk(relaxed = true)
        every { application.applicationContext } returns application
        preferencesManager = mockk(relaxed = true)
        chimePlayer = mockk(relaxed = true)
        dictionaryRepository = mockk(relaxed = true)
        engine = mockk(relaxed = true)
        engineState = MutableStateFlow(EngineState())
        every { engine.state } returns engineState

        every { preferencesManager.wakeWordFlow } returns flowOf("testword")
        every { preferencesManager.onboardingCompleteFlow } returns flowOf(false)
        every { preferencesManager.sensitivityFlow } returns flowOf(null)
        every { preferencesManager.alertSettingsFlow } returns flowOf(AlertSettings())
        every { dictionaryRepository.loadList() } returns emptyList()

        denyPermission()

        viewModel = newViewModel()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // region — initial state

    @Test
    fun `initial micState is DISABLED_NO_PERMISSION when permission is denied`() {
        assertEquals(MicState.DISABLED_NO_PERMISSION, viewModel.uiState.value.micState)
    }

    @Test
    fun `initial wakeWord is taken from preferences flow`() {
        assertEquals("testword", viewModel.uiState.value.wakeWord)
    }

    @Test
    fun `initial isMicrophonePermissionGranted is false when permission is denied`() {
        assertFalse(viewModel.uiState.value.isMicrophonePermissionGranted)
    }

    @Test
    fun `initial wakeWordTriggered is null`() {
        assertNull(viewModel.uiState.value.wakeWordTriggered)
    }

    @Test
    fun `onboardingCompleteFlow reflects preferences manager flow`() {
        assertEquals(false, viewModel.onboardingCompleteFlow.value)
    }

    // endregion

    // region — checkPermissions

    @Test
    fun `checkPermissions with GRANTED sets isMicrophonePermissionGranted true`() {
        grantPermission()
        viewModel.checkPermissions()
        assertTrue(viewModel.uiState.value.isMicrophonePermissionGranted)
    }

    @Test
    fun `checkPermissions with DENIED sets isMicrophonePermissionGranted false`() {
        denyPermission()
        viewModel.checkPermissions()
        assertFalse(viewModel.uiState.value.isMicrophonePermissionGranted)
    }

    @Test
    fun `checkPermissions does not change micState`() {
        val micStateBefore = viewModel.uiState.value.micState
        viewModel.checkPermissions()
        assertEquals(micStateBefore, viewModel.uiState.value.micState)
    }

    // endregion

    // region — setup

    @Test
    fun `setup rechecks permissions and lets the engine recover`() {
        grantPermission()
        viewModel.setup()
        assertTrue(viewModel.uiState.value.isMicrophonePermissionGranted)
        verify { engine.refresh() }
    }

    // endregion

    // region — engine state is mirrored into the UI

    @Test
    fun `listening engine shows as listening when permission is granted`() {
        grantPermission()
        viewModel.checkPermissions()
        engineState.value = EngineState(micState = MicState.LISTENING)
        assertEquals(MicState.LISTENING, viewModel.uiState.value.micState)
    }

    @Test
    fun `listening engine still shows disabled without permission`() {
        engineState.value = EngineState(micState = MicState.LISTENING)
        assertEquals(MicState.DISABLED_NO_PERMISSION, viewModel.uiState.value.micState)
    }

    @Test
    fun `granting permission after the engine reported listening updates the mic state`() {
        engineState.value = EngineState(micState = MicState.LISTENING)
        grantPermission()
        viewModel.checkPermissions()
        assertEquals(MicState.LISTENING, viewModel.uiState.value.micState)
    }

    @Test
    fun `a detection in the engine is shown to the UI`() {
        engineState.value = EngineState(micState = MicState.OFF, wakeWordTriggered = "testword")
        assertEquals("testword", viewModel.uiState.value.wakeWordTriggered)
    }

    @Test
    fun `mute in the engine is shown to the UI`() {
        engineState.value = EngineState(isMuted = true)
        assertTrue(viewModel.uiState.value.isMuted)
    }

    // endregion

    // region — setSensitivity

    @Test
    fun `setSensitivity with same value does not change state`() {
        val stateBefore = viewModel.uiState.value
        viewModel.setSensitivity(ListenerViewModel.DEFAULT_SENSITIVITY)
        assertEquals(stateBefore, viewModel.uiState.value)
    }

    @Test
    fun `setSensitivity with new value updates sensitivity`() {
        val newValue = ListenerViewModel.DEFAULT_SENSITIVITY + 1
        viewModel.setSensitivity(newValue)
        assertEquals(newValue, viewModel.uiState.value.sensitivity)
    }

    @Test
    fun `setSensitivity persists the value`() {
        viewModel.setSensitivity(2)
        coVerify { preferencesManager.setSensitivity(2) }
    }

    @Test
    fun `setSensitivity clamps out of range values`() {
        viewModel.setSensitivity(500)
        assertEquals(ListenerViewModel.MAX_SENSITIVITY, viewModel.uiState.value.sensitivity)
    }

    @Test
    fun `saved sensitivity is loaded on startup`() {
        every { preferencesManager.sensitivityFlow } returns flowOf(1)
        assertEquals(1, newViewModel().uiState.value.sensitivity)
    }

    // endregion

    // region — mute

    @Test
    fun `setMuted delegates to the engine`() {
        viewModel.setMuted(true)
        verify { engine.setMuted(true) }
        viewModel.setMuted(false)
        verify { engine.setMuted(false) }
    }

    @Test
    fun `a fresh view model is not muted`() {
        assertFalse(newViewModel().uiState.value.isMuted)
    }

    // endregion

    // region — clearWakeWordTriggered

    @Test
    fun `clearWakeWordTriggered clears the detection in the engine`() {
        viewModel.clearWakeWordTriggered()
        verify { engine.clearTriggered() }
    }

    // endregion

    // region — setWakeWord / completeOnboarding

    @Test
    fun `setWakeWord delegates to preferences manager`() {
        viewModel.setWakeWord("hello")
        coVerify { preferencesManager.updateWakeWord("hello") }
    }

    @Test
    fun `setWakeWord normalizes a multi word phrase`() {
        viewModel.setWakeWord("  OK   Harp ")
        coVerify { preferencesManager.updateWakeWord("ok harp") }
    }

    @Test
    fun `setWakeWord ignores a blank phrase`() {
        viewModel.setWakeWord("   ")
        coVerify(exactly = 0) { preferencesManager.updateWakeWord(any()) }
    }

    @Test
    fun `setAlertSound persists the sound`() {
        viewModel.setAlertSound(AlertSound.Silent)
        coVerify { preferencesManager.setAlertSound(AlertSound.Silent) }
    }

    @Test
    fun `setAlertDuration persists the duration`() {
        viewModel.setAlertDuration(AlertDuration.SECONDS_10)
        coVerify { preferencesManager.setAlertDuration(AlertDuration.SECONDS_10) }
    }

    @Test
    fun `saved alert settings are loaded on startup`() {
        val saved = AlertSettings(AlertSound.Custom("content://x/1"), AlertDuration.UNTIL_DISMISSED)
        every { preferencesManager.alertSettingsFlow } returns flowOf(saved)
        assertEquals(saved, newViewModel().uiState.value.alertSettings)
    }

    @Test
    fun `previewAlert plays with the current alert settings`() {
        val saved = AlertSettings(AlertSound.Silent, AlertDuration.ONCE)
        every { preferencesManager.alertSettingsFlow } returns flowOf(saved)
        newViewModel().previewAlert()
        verify { chimePlayer.play(saved) }
    }

    @Test
    fun `stopAlert stops the chime`() {
        viewModel.stopAlert()
        verify { chimePlayer.stop() }
    }

    @Test
    fun `completeOnboarding delegates to preferences manager`() {
        viewModel.completeOnboarding()
        coVerify { preferencesManager.setOnboardingComplete(true) }
    }

    // endregion

    // region — dictionary loading

    @Test
    fun `dictionary words are requested from repository on init`() {
        verify(timeout = 2_000) { dictionaryRepository.loadList() }
    }

    @Test
    fun `dictionary words state is populated from repository result`() {
        val words = listOf("hello", "world", "zamzow")
        every { dictionaryRepository.loadList() } returns words

        val vm = newViewModel()

        val deadline = System.currentTimeMillis() + 2_000
        while (vm.uiState.value.dictionaryWords
                .isEmpty() &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(10)
        }
        assertEquals(words, vm.uiState.value.dictionaryWords)
    }

    // endregion

    // region — constants

    @Test
    fun `DEFAULT_SENSITIVITY is a positive integer`() {
        assertTrue(ListenerViewModel.DEFAULT_SENSITIVITY > 0)
    }

    // endregion

    // region — wake word flow update

    @Test
    fun `wakeWord flow emitting new value updates wakeWord state`() {
        val wakeWordFlow = MutableStateFlow<String?>("first")
        every { preferencesManager.wakeWordFlow } returns wakeWordFlow
        val vm = newViewModel()
        assertEquals("first", vm.uiState.value.wakeWord)
        wakeWordFlow.value = "second"
        assertEquals("second", vm.uiState.value.wakeWord)
    }

    // endregion

    // region — permission fields

    @Test
    fun `checkPermissions sets supportsMicrophoneToggle false when sensorPrivacyManager is null`() {
        viewModel.checkPermissions()
        assertFalse(viewModel.uiState.value.supportsMicrophoneToggle)
    }

    @Test
    fun `checkPermissions sets isMicrophonePrivacyEnabled false when microphone is not muted`() {
        viewModel.checkPermissions()
        assertFalse(viewModel.uiState.value.isMicrophonePrivacyEnabled)
    }

    // endregion

    // ----

    private fun newViewModel() =
        ListenerViewModel(
            application = application,
            preferencesManager = preferencesManager,
            chimePlayer = chimePlayer,
            dictionaryRepository = dictionaryRepository,
            engine = engine,
            sensorPrivacyManager = null,
        )

    private fun grantPermission() {
        every {
            application.checkPermission(
                any(),
                any(),
                any(),
            )
        } returns PackageManager.PERMISSION_GRANTED
        every { application.checkSelfPermission(any()) } returns PackageManager.PERMISSION_GRANTED
    }

    private fun denyPermission() {
        every {
            application.checkPermission(
                any(),
                any(),
                any(),
            )
        } returns PackageManager.PERMISSION_DENIED
        every { application.checkSelfPermission(any()) } returns PackageManager.PERMISSION_DENIED
    }
}
