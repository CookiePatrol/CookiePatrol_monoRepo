package com.cookiepatrol.evaluation

import com.cookiepatrol.detect.RiskAnalyzer
import com.cookiepatrol.detect.SIGNALS
import com.cookiepatrol.detect.baseline.SIGNALS as BASELINE_SIGNALS
import com.cookiepatrol.detect.baseline.UrlExtractor as BASELINE_URLS
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class EvaluationTest {
    // IDs for the frozen baseline rules, in their original order.
    private val baselineSignalIds = listOf(
        "short_url", "http", "ip_host", "userinfo", "idn", "apk", "lure_words",
        "family", "credential", "transfer", "suspension", "identity",
    )

    private fun cases(name: String = "evaluation-cases.txt") =
        EvaluationCases.parse(File("src/debug/assets/$name").readText())

    private fun quote(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""
    private fun strings(values: List<String>) = values.joinToString(prefix = "[", postfix = "]") { quote(it) }
    private fun sha(path: String) = MessageDigest.getInstance("SHA-256").digest(File(path).readBytes()).joinToString("") { "%02x".format(it) }

    @Test fun evaluatesProductionAndFrozenBaselineAndWritesMachineReadableResults() {
        val development = cases()
        val holdout = cases("holdout-cases.txt")
        assertEquals(60, development.size)
        assertEquals(60, holdout.size)
        assertEquals(30, development.count { it.phishing })
        assertEquals(30, holdout.count { it.phishing })
        assertTrue(development.all { it.split == "development" })
        assertTrue(holdout.all { it.split == "holdout" })
        assertEquals(120, (development + holdout).map { it.id }.distinct().size)
        assertEquals(SIGNALS.size, SIGNALS.map { it.id }.distinct().size)
        assertEquals(BASELINE_SIGNALS.size, baselineSignalIds.size)
        assertFalse(RiskAnalyzer.analyze(development.first { it.id == "N14" }.text).shouldWarn)
        assertFalse(RiskAnalyzer.analyze(development.first { it.id == "N15" }.text).shouldWarn)
        // Warm up separately. Timing is a JVM micro measurement, never phone performance.
        repeat(20) { development.forEach { RiskAnalyzer.analyze(it.text) } }
        val rows = (development + holdout).map { case ->
            val baselineUrls = BASELINE_URLS.extract(case.text)
            val baselineText = (listOf(case.text) + baselineUrls).joinToString("\n").lowercase()
            val oldHits = BASELINE_SIGNALS.withIndex().filter { it.value.matches(baselineText) }
            val samples = (1..5).map { val start = System.nanoTime(); RiskAnalyzer.analyze(case.text); System.nanoTime() - start }.sorted()
            val result = RiskAnalyzer.analyze(case.text)
            "{\"id\":${quote(case.id)},\"score\":${result.score},\"signals\":${strings(result.hits.map { it.id })}," +
                "\"urls\":${strings(result.urls)},\"local_elapsed_ns_median\":${samples[2]}," +
                "\"baseline_score\":${oldHits.sumOf { it.value.score }},\"baseline_signals\":${strings(oldHits.map { baselineSignalIds[it.index] })}}"
        }
        val hashes = listOf("evaluation-cases.txt", "holdout-cases.txt").joinToString { quote(it) + ":" + quote(sha("src/debug/assets/$it")) }
        val sources = listOf("detect/RiskAnalyzer.kt", "detect/LocalAssessment.kt").joinToString {
            quote(it) + ":" + quote(sha("src/main/java/com/cookiepatrol/$it"))
        }
        val report = "{\"dataset_sha256\":{$hashes},\"rule_sources_sha256\":{$sources},\"dataset_version\":${quote(EvaluationCases.VERSION)},\"rule_version\":${quote(RiskAnalyzer.RULE_VERSION)}," +
            "\"baseline_commit\":\"70923b46037a166bf3cc6c6fa248d4f764f60f8c\",\"timing_environment\":\"Windows JVM 17; 20 warmups; median of 5\",\"rows\":[${rows.joinToString()}]}"
        File("build/reports/evaluation/local-results.json").apply { parentFile?.mkdirs(); writeText(report) }
    }

    @Test fun calculatesMetricsFromSmallVerifiedMatrix() {
        val small = cases().take(2) + cases().takeLast(2)
        val observed = mapOf("N01" to true, "N02" to false, "P29" to false)
        val matrix = ConfusionMatrix.calculate(small) { observed[it.id] }
        assertEquals(ConfusionMatrix(0, 1, 1, 1, 1), matrix)
        assertEquals(0.5, matrix.falsePositiveRate!!, 0.0001)
        assertEquals(1.0, matrix.falseNegativeRate!!, 0.0001)
        assertEquals(0.0, matrix.f1!!, 0.0001)
        val empty = ConfusionMatrix.calculate(small) { null }
        assertNull(empty.precision); assertNull(empty.recall); assertNull(empty.falsePositiveRate)
    }

    @Test fun rejectsCorruptFixtures() {
        for (text in listOf("bad", "P01|maybe|유형|문장", "P01|phishing|유형|문장\nP01|normal|유형|문장")) {
            assertThrows(IllegalArgumentException::class.java) { EvaluationCases.parse(text) }
        }
    }
}
