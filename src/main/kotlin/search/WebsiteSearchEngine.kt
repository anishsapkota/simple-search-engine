package com.asap.search

import com.asap.core.data.WebPage
import com.asap.core.data.WebSearchResult
import kotlin.collections.forEach
import kotlin.collections.mapNotNull

class WebsiteSearchEngine {
    private val searchEngine = SearchEngine()
    private var indexedPages = mutableMapOf<String, WebPage>()

    fun indexWebsite(pages: List<WebPage>) {
        println("Indexing ${pages.size} web pages...")

        pages.forEach { page ->
            indexedPages[page.url] = page

            val enhancedContent = buildString {
                repeat(3) { append("${page.title} ") }

                if (page.metaDescription.isNotEmpty()) {
                    repeat(2) { append("${page.metaDescription} ") }
                }

                page.headings.forEach { heading ->
                    repeat(2) { append("$heading ") }
                }

                page.keywords.forEach { keyword ->
                    repeat(2) { append("$keyword ") }
                }

                append(page.content)
            }

            searchEngine.addDocument(
                id = page.url,
                title = page.title,
                content = enhancedContent
            )
        }

        println("Indexing completed!")
    }

    fun searchPages(query: String, maxResults: Int = 10, fuzzyEnabled: Boolean): List<WebSearchResult> {
        val results = searchEngine.search(query, maxResults, fuzzyEnabled)

        return results.mapNotNull { result ->
            indexedPages[result.document.id]?.let { page ->
                WebSearchResult(
                    webPage = page,
                    score = result.score,
                    matchedTerms = result.matchedTerms,
                    fuzzyMatches = result.fuzzyMatches,
                    snippet = generateSnippet(page, query),
                    matchCount = countMatches(page, query)
                )
            }
        }
    }

    private fun generateSnippet(page: WebPage, query: String): String {
        val queryTerms = query.lowercase().split("\\s+".toRegex())
        val content = page.content.lowercase()

        val firstMatchIndex = queryTerms.mapNotNull { term ->
            content.indexOf(term).takeIf { it >= 0 }
        }.minOrNull() ?: 0

        val start = maxOf(0, firstMatchIndex - 100)
        val end = minOf(content.length, firstMatchIndex + 200)

        var snippet = page.content.substring(start, end)

        queryTerms.forEach { term ->
            snippet = snippet.replace(
                Regex("\\b${Regex.escape(term)}\\b", RegexOption.IGNORE_CASE),
                "**$term**"
            )
        }

        return if (start > 0) "...$snippet" else snippet
    }

    private fun countMatches(page: WebPage, query: String): Int {
        val queryTerms = query.lowercase().split("\\s+".toRegex())
        val content = "${page.title} ${page.content}".lowercase()

        return queryTerms.sumOf { term ->
            Regex("\\b${Regex.escape(term)}\\b").findAll(content).count()
        }
    }

    fun getIndexStats(): Map<String, Any> {
        return searchEngine.getStats() + mapOf(
            "indexedPages" to indexedPages.size,
            "totalContentSize" to indexedPages.values.sumOf { it.contentLength },
            "averagePageSize" to if (indexedPages.isNotEmpty()) {
                indexedPages.values.sumOf { it.contentLength } / indexedPages.size
            } else 0
        )
    }

    fun getSuggestions(term: String): List<String> {
        return searchEngine.getSuggestions(term)
    }
}