package com.cookiepatrol.detect

import java.net.URI

/** Production, JVM evaluation and device trials share these exact rules and thresholds. */
object RiskAnalyzer {
    const val RULE_VERSION = "2026-11-22-v8"
    const val WARNING_THRESHOLD = 3
    const val LOOKUP_THRESHOLD = 2

    fun analyze(rawText: String): LocalAssessment {
        val urls = UrlExtractor.extract(rawText)
        val signalText = (listOf(rawText) + urls).joinToString("\n").lowercase()
        return LocalAssessment(SIGNALS.filter { it.matches(signalText) }, urls)
    }
}

data class LocalAssessment(val hits: List<Signal>, val urls: List<String>) {
    val score: Int get() = hits.sumOf { it.score }
    val shouldWarn: Boolean get() = score >= RiskAnalyzer.WARNING_THRESHOLD
    val reason: String? get() = hits.maxByOrNull { it.score }?.reason

    internal fun categoryFor(signal: Signal): SignalCategory {
        if (signal.category != SignalCategory.LINK) return signal.category
        val refersToLink = if (signal.id == "apk") {
            urls.any { url -> signal.matches(URI.create(url).rawPath.orEmpty().lowercase()) }
        } else urls.isNotEmpty()
        return if (refersToLink) SignalCategory.LINK else SignalCategory.MESSAGE
    }

    internal fun shouldLookup(scope: RemoteCheckScope, fromMessage: Boolean): Boolean =
        scope != RemoteCheckScope.OFF && urls.isNotEmpty() &&
            (score >= RiskAnalyzer.LOOKUP_THRESHOLD || (fromMessage && scope == RemoteCheckScope.ALL_MESSAGE_LINKS))
}
