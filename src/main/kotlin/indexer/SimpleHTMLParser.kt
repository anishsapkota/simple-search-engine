package com.asap.indexer

import java.net.URI
import java.net.URL

class SimpleHTMLParser {
    fun parseHTML(html: String, baseUrl: String): ParsedPage {
        val title = extractTitle(html)
        val content = extractTextContent(html)
        val metaDescription = extractMetaDescription(html)
        val keywords = extractMetaKeywords(html)
        val headings = extractHeadings(html)
        val links = extractLinks(html, baseUrl)

        return ParsedPage(title, content, metaDescription, keywords, headings, links)
    }

    private fun extractTitle(html: String): String {
        val titleRegex = Regex("<title[^>]*>([^<]*)</title>", RegexOption.IGNORE_CASE)
        return titleRegex.find(html)?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun extractTextContent(html: String): String {
        return html
            .replace(
                Regex("<script[^>]*>.*?</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)),
                ""
            )
            .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("&\\w+;"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun extractMetaDescription(html: String): String {
        val metaRegex =
            Regex("<meta[^>]+name=[\"']description[\"'][^>]+content=[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
        return metaRegex.find(html)?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun extractMetaKeywords(html: String): List<String> {
        val keywordRegex =
            Regex("<meta[^>]+name=[\"']keywords[\"'][^>]+content=[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
        val keywords = keywordRegex.find(html)?.groupValues?.get(1) ?: ""
        return keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun extractHeadings(html: String): List<String> {
        val headingRegex = Regex("<h[1-6][^>]*>([^<]*)</h[1-6]>", RegexOption.IGNORE_CASE)
        return headingRegex.findAll(html).map { it.groupValues[1].trim() }.toList()
    }

    private fun extractLinks(html: String, baseUrl: String): List<String> {
        val linkRegex = Regex("""location\.href\s*=\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
        return linkRegex.findAll(html)
            .map { it.groupValues[1] }
            .map { resolveUrl(it, baseUrl) }
            .filter { it.isNotEmpty() }
            .toList()
    }

    private fun resolveUrl(url: String, baseUrl: String): String {
        return try {
            when {
                url.startsWith("http") -> url
                url.startsWith("/") -> {
                    val base = URI(baseUrl).toURL()
                    "${base.protocol}://${base.host}${if (base.port != -1) ":${base.port}" else ""}$url"
                }

                url.startsWith("#") -> ""
                else -> {
                    val base = URI(baseUrl).toURL()
                    val basePath = base.path.substringBeforeLast("/")
                    "${base.protocol}://${base.host}${if (base.port != -1) ":${base.port}" else ""}$basePath/$url"
                }
            }
        } catch (e: Exception) {
            ""
        }
    }

    data class ParsedPage(
        val title: String,
        val content: String,
        val metaDescription: String,
        val keywords: List<String>,
        val headings: List<String>,
        val links: List<String>
    )
}
