package com.asap.search

import com.asap.core.data.TermSuggestion
import com.asap.core.utils.EditDistance
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

class FuzzyMatcher {
    private val termFrequencies = ConcurrentHashMap<String, Int>()
    private val maxEditDistance = 2

    fun addTerm(term: String) {
        termFrequencies[term] = termFrequencies.getOrDefault(term, 0) + 1
    }

    fun findSuggestions(term: String, maxSuggestions: Int = 5): List<TermSuggestion> {
        if (term.length < 3) return emptyList()

        val suggestions = mutableListOf<TermSuggestion>()

        // First, try exact match
        if (termFrequencies.containsKey(term)) {
            return listOf(TermSuggestion(term, 0, termFrequencies[term]!!))
        }

        // Then try edit distance matches
        termFrequencies.entries.forEach { (candidate, frequency) ->
            if (abs(candidate.length - term.length) <= maxEditDistance) {
                val distance = EditDistance.levenshtein(term, candidate, maxEditDistance)
                if (distance <= maxEditDistance) {
                    suggestions.add(TermSuggestion(candidate, distance, frequency))
                }
            }
        }

        return suggestions
            .sortedWith(compareBy<TermSuggestion> { it.editDistance }.thenByDescending { it.frequency })
            .take(maxSuggestions)
    }
}
