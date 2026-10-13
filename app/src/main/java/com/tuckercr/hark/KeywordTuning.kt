package com.tuckercr.hark

/**
 * PocketSphinx keyword-spotting parameters for one slider position.
 *
 * [threshold] (`-kws_threshold`): smaller fires more easily.
 * [phoneLoopProbability] (`-kws_plp`): higher makes the background model harder to beat.
 * [delayFrames] (`-kws_delay`, 10 ms frames): wait this long after the word for the best score.
 * More delay means fewer false alarms but slower and less certain detection.
 */
data class KeywordTuning(
    val threshold: Float,
    val phoneLoopProbability: Float,
    val delayFrames: Int,
) {
    companion object {
        const val MIN_SENSITIVITY = 1
        const val MAX_SENSITIVITY = 10
        const val DEFAULT_SENSITIVITY = 4

        /**
         * Measured on the "hark" keyphrase with 11 synthetic voices (44 target utterances, 231
         * negatives including near-rhymes such as hard, heart, park, dark, hawk, harp, car). The
         * threshold alone barely changes the false-alarm rate; delay and plp do most of the work.
         * Approximate hits / false alarms: 1: 3/4, 2: 5/4, 3: 7/7, 4: 12/17, 5: 14/20, 6: 16/35,
         * 7: 17/52, 8: 20/70, 9: 25/82. Level 10 is extrapolated.
         */
        private val LEVELS =
            listOf(
                KeywordTuning(1e-1f, 0.9f, 80),
                KeywordTuning(1e-5f, 0.9f, 80),
                KeywordTuning(1e-10f, 0.9f, 80),
                KeywordTuning(1e-3f, 0.9f, 40),
                KeywordTuning(1e-5f, 0.9f, 40),
                KeywordTuning(1e-10f, 0.9f, 40),
                KeywordTuning(1e-10f, 0.1f, 10),
                KeywordTuning(1e-15f, 0.1f, 10),
                KeywordTuning(1e-20f, 0.1f, 10),
                KeywordTuning(1e-30f, 0.1f, 10),
            )

        /** Levels a two-word phrase shifts toward the lenient end, see [forSensitivity]. */
        private const val LEVELS_PER_EXTRA_WORD = 3

        /**
         * A longer phrase is more distinctive, so the strict delay settings that suit a single short
         * word make it almost deaf. Measured on "okay harp" with the same synthetic voices: at the
         * single-word default (level 4) only about 9% of utterances were heard, while level 7
         * heard about 23% with a false alarm rate on par with "hark" at its default. Each extra word
         * therefore starts [LEVELS_PER_EXTRA_WORD] levels more lenient.
         */
        fun forSensitivity(
            sensitivity: Int,
            wordCount: Int = 1,
        ): KeywordTuning {
            val level = sensitivity.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)
            val shifted = level + (wordCount.coerceAtLeast(1) - 1) * LEVELS_PER_EXTRA_WORD
            return LEVELS[shifted.coerceAtMost(MAX_SENSITIVITY) - 1]
        }
    }
}
