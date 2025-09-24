package com.asap.search

import com.asap.core.data.Document
import com.asap.core.data.SearchResult

class SearchEngine {
    private val invertedIndex = InvertedIndex()

    fun addDocument(id: String, title: String, content: String) {
        invertedIndex.addDocument(Document(id, title, content))
    }

    fun search(query: String, maxResults: Int = 10, enableFuzzy: Boolean = true): List<SearchResult> {
        return invertedIndex.search(query, maxResults, enableFuzzy)
    }

    fun getStats(): Map<String, Any> {
        return invertedIndex.getIndexStats()
    }

    fun getSuggestions(term: String): List<String> {
        return invertedIndex.getSuggestions(term)
    }
}