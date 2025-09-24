package com.asap

import com.asap.core.data.CrawlerConfig
import com.asap.indexer.WebsiteIndexerApp
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileWriter
import java.net.URI
import java.text.SimpleDateFormat
import java.util.*

class WebsiteIndexerCLI : CliktCommand(name = "Website Indexer CLI - Full-text search for your websites") {
    override fun run() = Unit
}

class IndexCommand : CliktCommand(name = "index") {
    private val urls by argument("urls").multiple(required = true)
    private val maxDepth by option("--max-depth", help = "Maximum crawl depth").int().default(2)
    private val maxPages by option("--max-pages", help = "Maximum pages to crawl").int().default(100)
    private val delay by option("--delay", help = "Delay between requests in ms").long().default(200L)
    private val concurrent by option("--concurrent", help = "Max concurrent requests").int().default(5)
    private val domains by option("--domains", help = "Allowed domains (comma-separated)").split(",")

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    override fun run() = runBlocking {
        try {
            val allowedDomains = domains?.map { it.trim() }?.toSet() ?: urls.map { url ->
                URI(url).toURL().host
            }.flatMap { setOf(it, "www.$it") }.toSet()

            val config = CrawlerConfig(
                maxDepth = maxDepth,
                maxPages = maxPages,
                delayBetweenRequests = delay,
                maxConcurrentRequests = concurrent,
                allowedDomains = allowedDomains
            )

            println("=== Starting Website Indexing ===")
            println("URLs: ${urls.joinToString()}")
            println("Max depth: $maxDepth, Max pages: $maxPages, Delay: ${delay}ms, Concurrent: $concurrent")
            println("Allowed domains: $allowedDomains")

            val indexerApp = WebsiteIndexerApp()
            indexerApp.indexWebsite(urls, config)
            println("\n✓ Indexing completed successfully!")

            val stats = indexerApp.getStats()
            println("=== Indexing Statistics ===")
            stats.forEach { (k, v) -> println("$k: $v") }

            saveIndexInfo(urls, config, stats)
        } catch (e: Exception) {
            echo("✗ Error: ${e.message}")
        }
    }

    private fun saveIndexInfo(urls: List<String>, config: CrawlerConfig, stats: Map<String, Any>) {
        try {
            val infoFile = File("last_index.info")
            FileWriter(infoFile).use { writer ->
                writer.write("Last indexing session\n")
                writer.write("Timestamp: ${dateFormatter.format(Date())}\n")
                writer.write("URLs: ${urls.joinToString(", ")}\n")
                writer.write("Config: maxDepth=${config.maxDepth}, maxPages=${config.maxPages}\n")
                writer.write("Statistics:\n")
                stats.forEach { (key, value) -> writer.write("  $key: $value\n") }
            }
        } catch (e: Exception) {
            echo("Warning: Could not save index info: ${e.message}")
        }
    }
}

class SearchCommand(private val indexerApp: WebsiteIndexerApp?) : CliktCommand(name = "search") {
    private val query by argument("query").multiple(required = true)
    private val maxResults by option("--max-results", help = "Maximum results").int().default(10)
    private val noFuzzy by option("--no-fuzzy", help = "Disable fuzzy matching").flag(default = false)

    override fun run() {
        if (indexerApp == null) {
            echo("No index available. Please run 'index' first.")
            return
        }

        val q = query.joinToString(" ")
        val results = indexerApp.search(q, maxResults, !noFuzzy)

        if (results.isEmpty()) {
            echo("No results found for '$q'")
            if (!noFuzzy) {
                val suggestions = indexerApp.getSuggestions(q)
                if (suggestions.isNotEmpty()) echo("Did you mean: ${suggestions.joinToString()}")
            }
        } else {
            results.forEachIndexed { index, result ->
                echo("${index + 1}. ${result.webPage.title}")
                echo(" URL: ${result.webPage.url}")
                echo(" Score: ${"%.3f".format(result.score)} | Matches: ${result.matchCount}")
                if (result.fuzzyMatches.isNotEmpty()) {
                    echo(" Corrections: ${result.fuzzyMatches.map { (key, value) -> "$key -> $value" }.joinToString(", ")}")
                }
                if (result.snippet.isNotEmpty()) {
                    echo(" ${result.snippet.take(150)}...")
                    }
                }
            }
            echo("Found ${results.size} result(s)")
        }
    }

    class StatsCommand(private val indexerApp: WebsiteIndexerApp?) : CliktCommand(name = "stats") {
        override fun run() {
            if (indexerApp == null) {
                echo("No index available. Please run 'index' first.")
                return
            }
            val stats = indexerApp.getStats()
            echo("=== Index Statistics ===")
            stats.forEach { (k, v) -> echo("$k: $v") }
        }
    }

    class ExportCommand(private val indexerApp: WebsiteIndexerApp?) : CliktCommand(name = "export") {
        private val filename by argument().optional()
        private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

        override fun run() {
            if (indexerApp == null) {
                echo("No index available. Please run 'index' first.")
                return
            }

            val file = filename ?: "search_index_${System.currentTimeMillis()}.txt"
            try {
                FileWriter(file).use { writer ->
                    writer.write("Website Search Index Export\n")
                    writer.write("Generated: ${dateFormatter.format(Date())}\n")
                    writer.write("=".repeat(50) + "\n\n")
                    indexerApp.getStats().forEach { (k, v) -> writer.write("$k: $v\n") }
                }
                echo("Index exported to: $file")
            } catch (e: Exception) {
                echo("Error exporting index: ${e.message}")
            }
        }
    }

    class InteractiveCommand(private var indexerApp: WebsiteIndexerApp?) : CliktCommand(name = "interactive") {
        override fun run() = runBlocking {
            echo("=== Interactive Website Search ===")
            echo("Commands: search <query>, stats, exit")

            if (indexerApp == null) {
                echo("No index loaded. Please provide indexing details.")
                print("Enter URLs (comma-separated): ")
                val urls =
                    readlnOrNull()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: return@runBlocking

                print("Max pages to crawl (default 50): ")
                val maxPages = readlnOrNull()?.toIntOrNull() ?: 50

                print("Max depth (default 2): ")
                val maxDepth = readlnOrNull()?.toIntOrNull() ?: 2

                val domains = urls.map { URI(it).toURL().host }.flatMap { setOf(it, "www.$it") }.toSet()
                val cookie = System.getenv("AUTH_COOKIE")?.takeIf { it.isNotBlank() }

                val config = CrawlerConfig(
                    maxDepth = maxDepth,
                    maxPages = maxPages,
                    allowedDomains = domains,
                    cookie = cookie
                )

                indexerApp = WebsiteIndexerApp().apply {
                    try {
                        indexWebsite(urls, config)
                        echo("✓ Indexing completed!")
                    } catch (e: Exception) {
                        echo("✗ Indexing failed: ${e.message}")
                        return@apply
                    }
                }
            }

            while (true) {
                print("\n> ")
                val input = readlnOrNull()?.trim() ?: continue
                when {
                    input.isBlank() -> continue
                    input == "exit" || input == "quit" -> {
                        echo("Goodbye!"); break
                    }

                    input == "stats" -> StatsCommand(indexerApp).run()
                    input.startsWith("search") -> {
                        val query = input.removePrefix("search").trim()
                        if (query.isEmpty()) {
                            echo("Empty query")
                        } else {
                            SearchCommand(indexerApp).main(query.split(" ").toTypedArray())
                        }
                    }

                    else -> echo("Unknown command: $input")
                }
            }
        }
    }

    fun main(args: Array<String>) {
        val indexerApp: WebsiteIndexerApp? = null
        WebsiteIndexerCLI().subcommands(
            IndexCommand(),
            SearchCommand(indexerApp),
            StatsCommand(indexerApp),
            ExportCommand(indexerApp),
            InteractiveCommand(indexerApp)
        ).main(args)
    }
