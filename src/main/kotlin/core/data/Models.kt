package com.asap.core.data

data class WebPage(
    val url: String,
    val title: String,
    val content: String,
    val metaDescription: String = "",
    val keywords: List<String> = emptyList(),
    val lastModified: Long = System.currentTimeMillis(),
    val contentLength: Int = content.length,
    val headings: List<String> = emptyList()
)

data class WebSearchResult(
    val webPage: WebPage,
    val score: Double,
    val matchedTerms: Set<String>,
    val fuzzyMatches: Map<String, String> = emptyMap(),
    val snippet: String = "",
    val matchCount: Int = 0
)

data class CrawlerConfig(
    val maxDepth: Int = 3,
    val maxPages: Int = 1000,
    val delayBetweenRequests: Long = 100, // milliseconds
    val maxConcurrentRequests: Int = 10,
    val userAgent: String = "PostmanRuntime/7.37.3",
    val cookie: String? = null,
    val respectRobotsTxt: Boolean = true,
    val allowedDomains: Set<String> = emptySet(),
    val excludePatterns: List<Regex> = listOf(
        Regex(".*\\.(jpg|jpeg|png|gif|pdf|doc|docx)$", RegexOption.IGNORE_CASE),
        Regex(".*/admin/.*"),
        Regex(".*/api/.*")
    )
)

data class Document(
    val id: String,
    val title: String,
    val content: String
) {
    val fullText: String get() = "$title $content"
}

data class PostingListEntry(
    val documentId: String,
    val termFrequency: Int,
    val positions: List<Int> = emptyList(),
    val titleBoost: Boolean = false
)

data class SearchResult(
    val document: Document,
    val score: Double,
    val matchedTerms: Set<String>,
    val fuzzyMatches: Map<String, String> = emptyMap()
)

data class TermSuggestion(
    val term: String,
    val editDistance: Int,
    val frequency: Int
)