package com.tuckercr.hark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordTuningTest {
    private val levels = (KeywordTuning.MIN_SENSITIVITY..KeywordTuning.MAX_SENSITIVITY).map { KeywordTuning.forSensitivity(it) }

    @Test
    fun `strictest level waits longest and is stricter than the most sensitive on every axis`() {
        val strict = levels.first()
        val loose = levels.last()
        assertTrue(strict.delayFrames > loose.delayFrames)
        assertTrue(strict.phoneLoopProbability > loose.phoneLoopProbability)
        assertTrue(strict.threshold > loose.threshold)
    }

    @Test
    fun `detection delay never increases as sensitivity rises`() {
        assertEquals(levels.map { it.delayFrames }.sortedDescending(), levels.map { it.delayFrames })
    }

    @Test
    fun `thresholds never underflow to zero`() {
        assertTrue(levels.all { it.threshold > 0f })
    }

    @Test
    fun `every level is distinct`() {
        assertEquals(levels.size, levels.toSet().size)
    }

    @Test
    fun `out of range sensitivity is clamped`() {
        assertEquals(levels.first(), KeywordTuning.forSensitivity(-5))
        assertEquals(levels.last(), KeywordTuning.forSensitivity(500))
    }

    @Test
    fun `default sensitivity is inside the range and stricter than the old default`() {
        assertTrue(KeywordTuning.DEFAULT_SENSITIVITY in KeywordTuning.MIN_SENSITIVITY..KeywordTuning.MAX_SENSITIVITY)
        assertTrue(KeywordTuning.forSensitivity(KeywordTuning.DEFAULT_SENSITIVITY).delayFrames > 10)
    }

    @Test
    fun `a two word phrase starts more lenient than a single word`() {
        assertEquals(KeywordTuning.forSensitivity(7), KeywordTuning.forSensitivity(4, wordCount = 2))
    }

    @Test
    fun `the shift for extra words stops at the most sensitive level`() {
        assertEquals(levels.last(), KeywordTuning.forSensitivity(KeywordTuning.MAX_SENSITIVITY, wordCount = 2))
        assertEquals(levels.last(), KeywordTuning.forSensitivity(9, wordCount = 2))
    }

    @Test
    fun `a single word or no word is not shifted`() {
        assertEquals(KeywordTuning.forSensitivity(4), KeywordTuning.forSensitivity(4, wordCount = 1))
        assertEquals(KeywordTuning.forSensitivity(4), KeywordTuning.forSensitivity(4, wordCount = 0))
    }
}
