package com.pralayakaveri.orbitmusic.domain.util

/**
 * Normalization utilities for a forgiving search experience.
 */
object SearchUtils {

    /**
     * Normalizes a string by:
     * 1. Converting to lowercase.
     * 2. Removing all non-alphanumeric characters (punctuation, symbols).
     * 3. Trimming whitespace.
     */
    fun normalize(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
    }

    /**
     * Checks if the normalized [text] contains the normalized [query].
     */
    fun matches(text: String, query: String): Boolean {
        if (query.isBlank()) return true
        val normalizedText = normalize(text)
        val normalizedQuery = normalize(query)
        return normalizedText.contains(normalizedQuery)
    }
}
