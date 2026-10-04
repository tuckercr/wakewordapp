package com.tuckercr.hark

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityTest {
    @Test
    fun `service starts when wake word is set and permissions are granted`() {
        assertTrue(shouldStartListenerService("hark", hasPermission = true))
    }

    @Test
    fun `service does not start while the wake word is still loading`() {
        assertFalse(shouldStartListenerService("", hasPermission = true))
    }

    @Test
    fun `service does not start for a blank wake word`() {
        assertFalse(shouldStartListenerService("   ", hasPermission = true))
    }

    @Test
    fun `service does not start without permissions`() {
        assertFalse(shouldStartListenerService("hark", hasPermission = false))
    }
}
