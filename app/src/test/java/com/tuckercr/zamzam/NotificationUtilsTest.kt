package com.tuckercr.zamzam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotificationUtilsTest {

    @Test
    fun `vibration pattern starts with silence`() {
        assertEquals(0L, NotificationUtils.VIBRATION_PATTERN[0])
    }

    @Test
    fun `vibration pattern has five elements`() {
        assertEquals(5, NotificationUtils.VIBRATION_PATTERN.size)
    }

    @Test
    fun `vibration pattern alternates on-off durations`() {
        val pattern = NotificationUtils.VIBRATION_PATTERN
        assertEquals(0L, pattern[0])
        assertEquals(1000L, pattern[1])
        assertEquals(500L, pattern[2])
        assertEquals(1000L, pattern[3])
        assertEquals(500L, pattern[4])
    }

    @Test
    fun `service and hotword notification ids are distinct`() {
        assertNotEquals(
            NotificationUtils.NOTIFICATION_ID_SERVICE,
            NotificationUtils.NOTIFICATION_ID_HOT_WORD,
        )
    }
}
