package com.asap.core.utils

class TextProcessor {
    companion object {
        private val STOP_WORDS = setOf(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "can", "this", "that", "these", "those",
            "from", "up", "out", "down", "off", "over", "under", "again", "further",
            "then", "once", "here", "there", "when", "where", "why", "how", "all",
            "any", "both", "each", "few", "more", "most", "other", "some", "such",
            "no", "nor", "not", "only", "own", "same", "so", "than", "too", "very"
        )
    }

    fun tokenize(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it.length > 2 && !STOP_WORDS.contains(it) }
    }

    fun tokenizeWithPositions(text: String, isTitle: Boolean = false): Map<String, List<Int>> {
        val tokens = tokenize(text)
        val tokenPositions = mutableMapOf<String, MutableList<Int>>()

        tokens.forEachIndexed { index, token ->
            tokenPositions.getOrPut(token) { mutableListOf() }.add(if (isTitle) -index - 1 else index)
        }

        return tokenPositions
    }
}