package com.tuckercr.zamzam

import android.app.Application
import android.content.pm.PackageManager
import com.tuckercr.zamzam.prefs.PreferencesManager
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
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
    private lateinit var viewModel: ListenerViewModel

    @Before
    fun setUp() {
        application = mockk(relaxed = true)
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
