package com.asap.search


import com.asap.core.data.Document
import com.asap.core.data.PostingListEntry
import com.asap.core.data.SearchResult
import com.asap.core.utils.TextProcessor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.map
import kotlin.collections.set
import kotlin.math.ln

class InvertedIndex {
    private val index = ConcurrentHashMap<String, MutableList<PostingListEntry>>()
    private val documents = ConcurrentHashMap<String, Document>()
    private val documentFrequency = ConcurrentHashMap<String, AtomicInteger>()
    private val textProcessor = TextProcessor()
    private val fuzzyMatcher = FuzzyMatcher()

    @Volatile
    var totalDocuments: Int = 0
        private set

    fun addDocument(document: Document) {
        documents[document.id] = document
        totalDocuments++

        val titleTokens = textProcessor.tokenizeWithPositions(document.title, true)
        val contentTokens = textProcessor.tokenizeWithPositions(document.content, false)

        val allTokens = mutableMapOf<String, MutableList<Int>>()
        titleTokens.forEach { (term, positions) ->
            allTokens.getOrPut(term) { mutableListOf() }.addAll(positions)
        }
        contentTokens.forEach { (term, positions) ->
            allTokens.getOrPut(term) { mutableListOf() }.addAll(positions)
        }

        allTokens.forEach { (term, positions) ->
            val hasTitle = positions.any { it < 0 }
            val postingEntry = PostingListEntry(
                documentId = document.id,
                termFrequency = positions.size,
                positions = positions,
                titleBoost = hasTitle
            )

            index.getOrPut(term) { mutableListOf() }.add(postingEntry)
            documentFrequency.getOrPut(term) { AtomicInteger(0) }.incrementAndGet()
            fuzzyMatcher.addTerm(term)
        }
    }

    fun search(query: String, maxResults: Int = 10, enableFuzzy: Boolean = true): List<SearchResult> {
        val queryTerms = textProcessor.tokenize(query)
        if (queryTerms.isEmpty()) return emptyList()

        val documentScores = mutableMapOf<String, Double>()
        val documentMatchedTerms = mutableMapOf<String, MutableSet<String>>()
        val fuzzyMatches = mutableMapOf<String, String>()

        queryTerms.forEach { term ->
            var foundExact = false

            // Try exact match first
            index[term]?.let { postings ->
                foundExact = true
                postings.forEach { entry ->
                    val tfIdfScore = calculateTfIdf(entry.termFrequency, documentFrequency[term]?.get() ?: 0)
                    val boost = if (entry.titleBoost) 2.0 else 1.0
                    documentScores[entry.documentId] =
                        documentScores.getOrDefault(entry.documentId, 0.0) + (tfIdfScore * boost)
                    documentMatchedTerms.getOrPut(entry.documentId) { mutableSetOf() }.add(term)
                }
            }

            // If no exact match and fuzzy is enabled, try fuzzy matching
            if (!foundExact && enableFuzzy) {
                val suggestions = fuzzyMatcher.findSuggestions(term, 3)
                suggestions.forEach { suggestion ->
                    index[suggestion.term]?.let { postings ->
                        fuzzyMatches[term] = suggestion.term
                        postings.forEach { entry ->
                            val tfIdfScore =
                                calculateTfIdf(entry.termFrequency, documentFrequency[suggestion.term]?.get() ?: 0)
                            val fuzzyPenalty = 1.0 - (suggestion.editDistance * 0.2)
                            val boost = if (entry.titleBoost) 2.0 else 1.0
                            documentScores[entry.documentId] =
                                documentScores.getOrDefault(entry.documentId, 0.0) + (tfIdfScore * fuzzyPenalty * boost)
                            documentMatchedTerms.getOrPut(entry.documentId) { mutableSetOf() }.add(suggestion.term)
                        }
                    }
                }
            }
        }

        return documentScores.entries
            .sortedByDescending { it.value }
            .take(maxResults)
            .mapNotNull { (docId, score) ->
                documents[docId]?.let { document ->
                    SearchResult(
                        document = document,
                        score = score,
                        matchedTerms = documentMatchedTerms[docId] ?: emptySet(),
                        fuzzyMatches = fuzzyMatches
                    )
                }
            }
    }

    private fun calculateTfIdf(termFreq: Int, docFreq: Int): Double {
        if (docFreq == 0) return 0.0
        val tf = 1.0 + ln(termFreq.toDouble())
        val idf = ln(totalDocuments.toDouble() / docFreq.toDouble())
        return tf * idf
    }

    fun getIndexStats(): Map<String, Any> {
        return mapOf(
            "totalDocuments" to totalDocuments,
            "totalTerms" to index.size,
            "averageTermsPerDocument" to if (totalDocuments > 0) {
                index.values.sumOf { it.size } / totalDocuments
            } else 0,
            "memoryUsage" to Runtime.getRuntime().let {
                "Used: ${(it.totalMemory() - it.freeMemory()) / 1024 / 1024}MB, " +
                        "Total: ${it.totalMemory() / 1024 / 1024}MB"
            }
        )
    }

    fun getSuggestions(term: String): List<String> {
        return fuzzyMatcher.findSuggestions(term).map { it.term }
    }
}