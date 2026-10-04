package com.tuckercr.hark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertSettingsTest {
    private val defaultUri = "content://settings/system/alarm_alert"

    @Test
    fun `picking nothing means silent`() {
        assertEquals(AlertSound.Silent, AlertSound.fromPickedUri(null, defaultUri))
    }

    @Test
    fun `picking the default alarm uri means default`() {
        assertEquals(AlertSound.Default, AlertSound.fromPickedUri(defaultUri, defaultUri))
    }

    @Test
    fun `picking any other uri is a custom sound`() {
        assertEquals(AlertSound.Custom("content://media/1"), AlertSound.fromPickedUri("content://media/1", defaultUri))
    }

    @Test
    fun `alert stops after five seconds by default instead of playing forever`() {
        assertEquals(AlertDuration.SECONDS_5, AlertSettings().duration)
        assertEquals(5_000L, AlertSettings().duration.limitMillis)
    }

    @Test
    fun `only until dismissed loops without a time limit`() {
        val unbounded = AlertDuration.entries.filter { it.loops && it.limitMillis == null }
        assertEquals(listOf(AlertDuration.UNTIL_DISMISSED), unbounded)
    }

    @Test
    fun `once through neither loops nor has a limit`() {
        assertFalse(AlertDuration.ONCE.loops)
        assertNull(AlertDuration.ONCE.limitMillis)
    }

    @Test
    fun `timed durations loop to fill the time`() {
        assertTrue(AlertDuration.entries.filter { it.limitMillis != null }.all { it.loops })
    }

    @Test
    fun `unknown stored duration falls back to the default`() {
        assertEquals(AlertDuration.DEFAULT, AlertDuration.fromName("bogus"))
        assertEquals(AlertDuration.DEFAULT, AlertDuration.fromName(null))
        assertEquals(AlertDuration.SECONDS_30, AlertDuration.fromName("SECONDS_30"))
    }
}
