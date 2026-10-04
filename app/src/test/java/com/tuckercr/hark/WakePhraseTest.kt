package com.tuckercr.hark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WakePhraseTest {
    private val list = listOf("ok", "okay", "harp", "hark", "hard", "park", "heart", "computer")
    private val dictionary = list.toSet()

    @Test
    fun `normalize lowercases trims and collapses whitespace`() {
        assertEquals("ok harp", WakePhrase.normalize("  OK    Harp "))
        assertEquals("", WakePhrase.normalize("   "))
    }

    @Test
    fun `a single dictionary word is valid`() {
        assertEquals(WakePhrase.Validation.Valid("hark"), WakePhrase.validate("hark", dictionary))
    }

    @Test
    fun `a two word phrase is valid when both words are in the dictionary`() {
        assertEquals(WakePhrase.Validation.Valid("ok harp"), WakePhrase.validate("OK Harp", dictionary))
    }

    @Test
    fun `an unknown word is reported by name`() {
        assertEquals(WakePhrase.Validation.UnknownWords(listOf("zzz")), WakePhrase.validate("ok zzz", dictionary))
    }

    @Test
    fun `every unknown word is reported once`() {
        assertEquals(WakePhrase.Validation.UnknownWords(listOf("aaa", "bbb")), WakePhrase.validate("aaa bbb aaa", dictionary))
    }

    @Test
    fun `blank input is empty`() {
        assertEquals(WakePhrase.Validation.Empty, WakePhrase.validate("  ", dictionary))
    }

    @Test
    fun `more than the maximum number of words is rejected`() {
        assertEquals(WakePhrase.Validation.TooManyWords, WakePhrase.validate("ok ok ok ok ok", dictionary))
    }

    @Test
    fun `completions list prefix matches before mid word matches`() {
        assertEquals(listOf("harp", "bohark"), WakePhrase.completions("ha", listOf("bohark", "harp", "park")))
    }

    @Test
    fun `completions use only the last word of a phrase`() {
        assertTrue(WakePhrase.completions("ok ha", list).containsAll(listOf("hark", "harp")))
    }

    @Test
    fun `no completions while starting a new word`() {
        assertTrue(WakePhrase.completions("ok ", list).isEmpty())
        assertTrue(WakePhrase.completions("", list).isEmpty())
    }

    @Test
    fun `replaceLastWord keeps the earlier words`() {
        assertEquals("ok harp", WakePhrase.replaceLastWord("ok ha", "harp"))
        assertEquals("harp", WakePhrase.replaceLastWord("ha", "harp"))
        assertEquals("ok harp", WakePhrase.replaceLastWord("ok ", "harp"))
    }

    @Test
    fun `suggestions offer close dictionary words for a typo`() {
        assertTrue("hark" in WakePhrase.suggestionsFor("harc", list))
        assertTrue("computer" in WakePhrase.suggestionsFor("compter", list))
    }

    @Test
    fun `suggestions never include the word itself or far away words`() {
        assertTrue("hark" !in WakePhrase.suggestionsFor("hark", list))
        assertTrue(WakePhrase.suggestionsFor("zzzzzz", list).isEmpty())
    }

    @Test
    fun `edit distance counts single character edits`() {
        assertEquals(0, WakePhrase.editDistance("hark", "hark"))
        assertEquals(1, WakePhrase.editDistance("hark", "harp"))
        assertEquals(1, WakePhrase.editDistance("hark", "har"))
        assertEquals(2, WakePhrase.editDistance("hark", "hardy"))
    }
}
