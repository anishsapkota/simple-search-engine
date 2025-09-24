package com.asap.indexer


import com.asap.core.data.CrawlerConfig
import com.asap.core.data.WebPage
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class WebCrawler(private val config: CrawlerConfig) {
    private val htmlParser = SimpleHTMLParser()
    private val visitedUrls = ConcurrentHashMap<String, Boolean>()
    private val urlQueue = ConcurrentLinkedQueue<Pair<String, Int>>()
    private val crawledPages = ConcurrentHashMap<String, WebPage>()
    private val requestSemaphore = Semaphore(config.maxConcurrentRequests)
    private val pagesProcessed = AtomicInteger(0)
    private val lastRequestTime = AtomicLong(0)

    suspend fun crawl(startUrls: List<String>): List<WebPage> = withContext(Dispatchers.IO) {
        println("Starting crawl with ${startUrls.size} seed URLs...")

        startUrls.forEach { url ->
            if (isAllowedUrl(url)) {
                urlQueue.offer(url to 0)
            }
        }

        val jobs = mutableListOf<Job>()

        repeat(config.maxConcurrentRequests) { workerId ->
            val job = launch {
                crawlWorker(workerId)
            }
            jobs.add(job)
        }

        jobs.joinAll()

        println("Crawl completed. Processed ${pagesProcessed.get()} pages.")
        return@withContext crawledPages.values.toList()
    }

    private suspend fun crawlWorker(workerId: Int) {
        while (pagesProcessed.get() < config.maxPages) {
            val urlWithDepth = urlQueue.poll() ?: break
            val (url, depth) = urlWithDepth

            if (visitedUrls.putIfAbsent(url, true) != null) continue
            if (depth > config.maxDepth) continue

            try {
                requestSemaphore.acquire()

                val timeSinceLastRequest = System.currentTimeMillis() - lastRequestTime.get()
                if (timeSinceLastRequest < config.delayBetweenRequests) {
                    delay(config.delayBetweenRequests - timeSinceLastRequest)
                }
                lastRequestTime.set(System.currentTimeMillis())

                val webPage = fetchAndParsePage(url)
                if (webPage != null) {
                    crawledPages[url] = webPage
                    val processed = pagesProcessed.incrementAndGet()

                    if (processed % 10 == 0) {
                        println("Worker $workerId: Processed $processed pages...")
                    }

                    if (depth < config.maxDepth) {
                        val html = fetchPageContent(url)
                        if (html != null) {
                            val parsedPage = htmlParser.parseHTML(html, url)
                            parsedPage.links
                                .filter { isAllowedUrl(it) && !visitedUrls.containsKey(it) }
                                .forEach { link ->
                                    urlQueue.offer(link to depth + 1)
                                }
                        }
                    }
                }
            } catch (e: Exception) {
                println("Error crawling $url: ${e.message}")
            } finally {
                requestSemaphore.release()
            }
        }
    }

    private suspend fun fetchAndParsePage(url: String): WebPage? = withContext(Dispatchers.IO) {
        try {
            val html = fetchPageContent(url) ?: return@withContext null
            val parsedPage = htmlParser.parseHTML(html, url)

            if (parsedPage.content.length < 100) return@withContext null

            WebPage(
                url = url,
                title = parsedPage.title.ifEmpty { url.substringAfterLast("/").ifEmpty { "Untitled" } },
                content = parsedPage.content,
                metaDescription = parsedPage.metaDescription,
                keywords = parsedPage.keywords,
                headings = parsedPage.headings,
                lastModified = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            println("Error parsing page $url: ${e.message}")
            null
        }
    }

    private fun fetchPageContent(url: String): String? {
        return try {
            val connection = URI(url).toURL().openConnection()
            connection.setRequestProperty("User-Agent", config.userAgent)
            config.cookie?.let { connection.setRequestProperty("Cookie", it) }
            connection.connectTimeout = 10000
            connection.readTimeout = 15000

            val contentType = connection.contentType?.lowercase() ?: ""
            if (!contentType.contains("text/html")) return null

            connection.getInputStream().use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            println("Error fetching $url: ${e.message}")
            null
        }
    }

    private fun isAllowedUrl(url: String): Boolean {
        try {
            val urlObj = URI(url).toURL()

            if (config.allowedDomains.isNotEmpty() &&
                !config.allowedDomains.contains(urlObj.host)
            ) {
                return false
            }

            if (config.excludePatterns.any { it.matches(url) }) {
                return false
            }

            return urlObj.protocol in listOf("http", "https")
        } catch (e: Exception) {
            return false
        }
    }
}