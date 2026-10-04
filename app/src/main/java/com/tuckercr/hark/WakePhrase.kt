package com.tuckercr.hark

/**
 * A wake phrase is one or more dictionary words, for example "hark" or "ok harp".
 * PocketSphinx can only listen for words that exist in its pronunciation dictionary, so every
 * word of the phrase is validated against it.
 */
object WakePhrase {
    const val MAX_WORDS = 2
    private const val MAX_SUGGESTIONS = 3
    private const val MAX_EDIT_DISTANCE = 2
    private val whitespace = Regex("\\s+")

    sealed interface Validation {
        data object Empty : Validation

        data class Valid(
            val phrase: String,
        ) : Validation

        /** One or more words are not in the dictionary. */
        data class UnknownWords(
            val words: List<String>,
        ) : Validation

        data object TooManyWords : Validation
    }

    fun words(input: String): List<String> =
        input
            .trim()
            .lowercase()
            .split(whitespace)
            .filter { it.isNotEmpty() }

    fun normalize(input: String): String = words(input).joinToString(" ")

    fun validate(
        input: String,
        dictionary: Set<String>,
    ): Validation {
        val words = words(input)
        return when {
            words.isEmpty() -> Validation.Empty
            words.size > MAX_WORDS -> Validation.TooManyWords
            else -> {
                val unknown = words.filter { it !in dictionary }.distinct()
                if (unknown.isEmpty()) Validation.Valid(words.joinToString(" ")) else Validation.UnknownWords(unknown)
            }
        }
    }

    /** Dictionary words that complete the word currently being typed, best matches first. */
    fun completions(
        input: String,
        dictionary: List<String>,
        limit: Int = 100,
    ): List<String> {
        if (input.isBlank() || input.last().isWhitespace()) return emptyList()
        val partial = words(input).last()
        return dictionary
            .asSequence()
            .filter { it.contains(partial) }
            .sortedWith(compareBy({ !it.startsWith(partial) }, { it.length }))
            .take(limit)
            .toList()
    }

    /** Replaces the word being typed with [word], keeping the words before it. */
    fun replaceLastWord(
        input: String,
        word: String,
    ): String {
        val head = words(input).dropLast(if (input.isBlank() || input.last().isWhitespace()) 0 else 1)
        return (head + word).joinToString(" ")
    }

    /** Close dictionary words for a mistyped [word], for "did you mean". */
    fun suggestionsFor(
        word: String,
        dictionary: List<String>,
    ): List<String> {
        if (word.isEmpty()) return emptyList()
        return dictionary
            .asSequence()
            .filter { it[0] == word[0] && kotlin.math.abs(it.length - word.length) <= MAX_EDIT_DISTANCE }
            .map { it to editDistance(word, it) }
            .filter { it.second in 1..MAX_EDIT_DISTANCE }
            .sortedWith(compareBy({ it.second }, { it.first.length }))
            .take(MAX_SUGGESTIONS)
            .map { it.first }
            .toList()
    }

    internal fun editDistance(
        a: String,
        b: String,
    ): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            previous = current
        }
        return previous[b.length]
    }
}
