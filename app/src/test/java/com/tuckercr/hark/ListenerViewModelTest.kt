package com.tuckercr.hark

import android.app.Application
import android.content.pm.PackageManager
import com.tuckercr.hark.prefs.PreferencesManager
import edu.cmu.pocketsphinx.Hypothesis
import io.mockk.clearMocks
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
import org.junit.Assert.assertNotNull
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
    private lateinit var viewModel: ListenerViewModel

    @Before
    fun setUp() {
        application = mockk(relaxed = true)
        every { application.applicationContext } returns application
        preferencesManager = mockk(relaxed = true)
        chimePlayer = mockk(relaxed = true)
        dictionaryRepository = mockk(relaxed = true)

        every { preferencesManager.wakeWordFlow } returns flowOf("testword")
        every { preferencesManager.onboardingCompleteFlow } returns flowOf(false)
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
    fun `setup with denied permission sets DISABLED_NO_PERMISSION`() {
        viewModel.shutdownRecognizer()
        assertEquals(MicState.OFF, viewModel.uiState.value.micState)
        viewModel.setup()
        assertEquals(MicState.DISABLED_NO_PERMISSION, viewModel.uiState.value.micState)
    }

    @Test
    fun `setup with granted permission but blank wakeWord does not start listener`() {
        grantPermission()
        every { preferencesManager.wakeWordFlow } returns flowOf(null)
        every { application.getString(any<Int>()) } returns ""

        val vm = newViewModel()
        vm.setup()

        assertEquals(MicState.OFF, vm.uiState.value.micState)
    }

    // endregion

    // region — shutdownRecognizer

    @Test
    fun `shutdownRecognizer sets micState to OFF`() {
        viewModel.shutdownRecognizer()
        assertEquals(MicState.OFF, viewModel.uiState.value.micState)
    }

    @Test
    fun `shutdownRecognizer clears wakeWordTriggered`() {
        viewModel.shutdownRecognizer()
        assertNull(viewModel.uiState.value.wakeWordTriggered)
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
    fun `setSensitivity with new value triggers setup which rechecks permissions`() {
        val newValue = ListenerViewModel.DEFAULT_SENSITIVITY + 1
        viewModel.setSensitivity(newValue)
        assertEquals(MicState.DISABLED_NO_PERMISSION, viewModel.uiState.value.micState)
    }

    // endregion

    // region — clearWakeWordTriggered

    @Test
    fun `clearWakeWordTriggered stops the chime player`() {
        viewModel.clearWakeWordTriggered()
        verify { chimePlayer.stop() }
    }

    @Test
    fun `clearWakeWordTriggered clears wakeWordTriggered`() {
        viewModel.clearWakeWordTriggered()
        assertNull(viewModel.uiState.value.wakeWordTriggered)
    }

    // endregion

    // region — setWakeWord / completeOnboarding

    @Test
    fun `setWakeWord delegates to preferences manager`() {
        viewModel.setWakeWord("hello")
        coVerify { preferencesManager.updateWakeWord("hello") }
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

    // region — recognitionListener callbacks

    @Test
    fun `onBeginningOfSpeech sets micState to SPEAKING`() {
        viewModel.recognitionListener.onBeginningOfSpeech()
        assertEquals(MicState.SPEAKING, viewModel.uiState.value.micState)
    }

    @Test
    fun `onEndOfSpeech sets micState to LISTENING`() {
        viewModel.recognitionListener.onEndOfSpeech()
        assertEquals(MicState.LISTENING, viewModel.uiState.value.micState)
    }

    @Test
    fun `onResult sets micState to LISTENING`() {
        viewModel.recognitionListener.onResult(null)
        assertEquals(MicState.LISTENING, viewModel.uiState.value.micState)
    }

    @Test
    fun `onPartialResult with null hypothesis does nothing`() {
        viewModel.recognitionListener.onPartialResult(null)
        assertNull(viewModel.uiState.value.wakeWordTriggered)
    }

    @Test
    fun `onPartialResult with matching word triggers detection`() {
        val hypothesis = mockk<Hypothesis>()
        every { hypothesis.hypstr } returns "testword"
        viewModel.recognitionListener.onPartialResult(hypothesis)
        assertEquals("testword", viewModel.uiState.value.wakeWordTriggered)
        assertEquals(MicState.OFF, viewModel.uiState.value.micState)
        verify { chimePlayer.play() }
    }

    @Test
    fun `onPartialResult with text containing wake word triggers detection`() {
        val hypothesis = mockk<Hypothesis>()
        every { hypothesis.hypstr } returns "please testword now"
        viewModel.recognitionListener.onPartialResult(hypothesis)
        assertNotNull(viewModel.uiState.value.wakeWordTriggered)
    }

    @Test
    fun `onPartialResult with non-matching text does not trigger detection`() {
        val hypothesis = mockk<Hypothesis>()
        every { hypothesis.hypstr } returns "completely different"
        viewModel.recognitionListener.onPartialResult(hypothesis)
        assertNull(viewModel.uiState.value.wakeWordTriggered)
        verify(exactly = 0) { chimePlayer.play() }
    }

    @Test
    fun `onPartialResult when already triggered does not re-trigger`() {
        val hypothesis = mockk<Hypothesis>()
        every { hypothesis.hypstr } returns "testword"
        viewModel.recognitionListener.onPartialResult(hypothesis)
        clearMocks(chimePlayer)
        every { chimePlayer.stop() } returns Unit // re-stub after clearMocks
        viewModel.recognitionListener.onPartialResult(hypothesis)
        verify(exactly = 0) { chimePlayer.play() }
    }

    @Test
    fun `onPartialResult with null hypstr does nothing`() {
        val hypothesis = mockk<Hypothesis>()
        every { hypothesis.hypstr } returns null
        viewModel.recognitionListener.onPartialResult(hypothesis)
        assertNull(viewModel.uiState.value.wakeWordTriggered)
    }

    // endregion

    // region — onCleared

    @Test
    fun `onCleared shuts down recognizer and stops chime`() {
        viewModel.javaClass.getDeclaredMethod("onCleared").apply {
            isAccessible = true
            invoke(viewModel)
        }
        assertEquals(MicState.OFF, viewModel.uiState.value.micState)
        verify(atLeast = 1) { chimePlayer.stop() }
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
