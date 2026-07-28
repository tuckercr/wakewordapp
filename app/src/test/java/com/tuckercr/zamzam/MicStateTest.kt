package com.tuckercr.zamzam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MicStateTest {

    @Test
    fun `enum has exactly four values`() {
        assertEquals(4, MicState.entries.size)
    }

    @Test
    fun `all expected values exist`() {
        val values = MicState.entries.toSet()
        assertTrue(MicState.DISABLED_NO_PERMISSION in values)
        assertTrue(MicState.LISTENING in values)
        assertTrue(MicState.SPEAKING in values)
        assertTrue(MicState.OFF in values)
    }
}
