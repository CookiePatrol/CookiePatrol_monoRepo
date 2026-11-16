package com.cookiepatrol.detect

/** Production, JVM evaluation and device trials share these exact rules and thresholds. */
object RiskAnalyzer {
    const val RULE_VERSION = "2026-11-16-v2"
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
    val shouldLookup: Boolean get() = score >= RiskAnalyzer.LOOKUP_THRESHOLD && urls.isNotEmpty()
    val reason: String? get() = hits.maxByOrNull { it.score }?.reason
}
