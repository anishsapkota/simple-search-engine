package com.asap.indexer

import com.asap.core.data.CrawlerConfig
import com.asap.core.data.WebSearchResult
import com.asap.search.WebsiteSearchEngine

class WebsiteIndexerApp {
    private val searchEngine = WebsiteSearchEngine()

    suspend fun indexWebsite(
        startUrls: List<String>,
        crawlerConfig: CrawlerConfig = CrawlerConfig()
    ) {
        val crawler = WebCrawler(crawlerConfig)
        val pages = crawler.crawl(startUrls)
        searchEngine.indexWebsite(pages)

        println("\n=== Indexing Statistics ===")
        searchEngine.getIndexStats().forEach { (key, value) ->
            println("$key: $value")
        }
    }

    fun search(query: String, maxResults: Int = 10, fuzzyEnabled: Boolean): List<WebSearchResult> {
        return searchEngine.searchPages(query, maxResults, fuzzyEnabled)
    }

    fun getStats(): Map<String, Any> {
        return searchEngine.getIndexStats()
    }

    fun getSuggestions(term: String): List<String> {
        return searchEngine.getSuggestions(term)
    }
}
