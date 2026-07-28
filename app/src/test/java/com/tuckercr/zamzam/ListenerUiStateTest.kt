package com.tuckercr.zamzam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ListenerUiStateTest {

    @Test
    fun `default micState is OFF`() {
        assertEquals(MicState.OFF, ListenerUiState().micState)
    }

    @Test
    fun `default wakeWord is empty`() {
        assertEquals("", ListenerUiState().wakeWord)
    }

    @Test
    fun `default sensitivity matches ViewModel constant`() {
        assertEquals(ListenerViewModel.DEFAULT_SENSITIVITY, ListenerUiState().sensitivity)
    }

    @Test
    fun `default wakeWordTriggered is null`() {
        assertNull(ListenerUiState().wakeWordTriggered)
    }

    @Test
    fun `default dictionaryWords is empty`() {
        assertEquals(emptyList<String>(), ListenerUiState().dictionaryWords)
    }

    @Test
    fun `default isMicrophonePermissionGranted is false`() {
        assertFalse(ListenerUiState().isMicrophonePermissionGranted)
    }

    @Test
    fun `default isMicrophonePrivacyEnabled is false`() {
        assertFalse(ListenerUiState().isMicrophonePrivacyEnabled)
    }

    @Test
    fun `default supportsMicrophoneToggle is false`() {
        assertFalse(ListenerUiState().supportsMicrophoneToggle)
    }

    @Test
    fun `copy only changes specified fields`() {
        val original = ListenerUiState()
        val updated = original.copy(micState = MicState.LISTENING, wakeWord = "hello")

        assertEquals(MicState.LISTENING, updated.micState)
        assertEquals("hello", updated.wakeWord)
        assertEquals(original.sensitivity, updated.sensitivity)
        assertEquals(original.dictionaryWords, updated.dictionaryWords)
        assertEquals(original.isMicrophonePermissionGranted, updated.isMicrophonePermissionGranted)
    }

    @Test
    fun `data class equality works correctly`() {
        val a = ListenerUiState(wakeWord = "foo")
        val b = ListenerUiState(wakeWord = "foo")
        assertEquals(a, b)
    }

    @Test
    fun `data class inequality on different wakeWord`() {
        val a = ListenerUiState(wakeWord = "foo")
        val b = ListenerUiState(wakeWord = "bar")
        assert(a != b)
    }
}
