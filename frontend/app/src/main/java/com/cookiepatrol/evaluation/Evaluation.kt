package com.cookiepatrol.evaluation

import com.cookiepatrol.detect.RiskAnalyzer

data class EvaluationCase(val id: String, val phishing: Boolean, val category: String, val text: String,
                          val split: String = "development", val rationale: String = "", val source: String = "synthetic",
                          val evaluationType: String = "message_local_and_pipeline")

object EvaluationCases {
    const val VERSION = "cookiepatrol-synthetic-v2"

    fun parse(content: String): List<EvaluationCase> {
        val cases = content.lineSequence().filter { it.isNotBlank() && !it.startsWith('#') }.map { line ->
            val fields = line.split('|', limit = 8)
            require(fields.size == 8 && fields.all { it.isNotBlank() }) { "Invalid evaluation case" }
            require(fields[0].matches(Regex("E?[NP][0-9]{2}"))) { "Invalid case id" }
            require(fields[1] in setOf("normal", "phishing")) { "Invalid label" }
            require(fields[3] in setOf("development", "holdout") && fields[5] == "synthetic")
            EvaluationCase(fields[0], fields[1] == "phishing", fields[2], fields[7], fields[3], fields[4], fields[5], fields[6])
        }.toList()
        require(cases.map { it.id }.distinct().size == cases.size) { "Duplicate case id" }
        return cases
    }
}

/** A null observation is excluded, never silently counted as a successful negative. */
data class ConfusionMatrix(val tp: Int, val fp: Int, val tn: Int, val fn: Int, val excluded: Int) {
    val evaluated get() = tp + fp + tn + fn
    val precision: Double? get() = ratio(tp, tp + fp)
    val recall: Double? get() = ratio(tp, tp + fn)
    val f1: Double? get() = ratio(2 * tp, 2 * tp + fp + fn)
    val falsePositiveRate: Double? get() = ratio(fp, fp + tn)
    val falseNegativeRate: Double? get() = ratio(fn, fn + tp)
    private fun ratio(n: Int, d: Int) = if (d == 0) null else n.toDouble() / d

    companion object {
        fun calculate(cases: List<EvaluationCase>, observed: (EvaluationCase) -> Boolean?): ConfusionMatrix {
            var tp = 0; var fp = 0; var tn = 0; var fn = 0; var excluded = 0
            cases.forEach { case ->
                val warned = observed(case)
                when {
                    warned == null -> excluded++
                    case.phishing && warned -> tp++
                    !case.phishing && warned -> fp++
                    case.phishing -> fn++
                    else -> tn++
                }
            }
            return ConfusionMatrix(tp, fp, tn, fn, excluded)
        }

        fun local(cases: List<EvaluationCase>) = calculate(cases) { RiskAnalyzer.analyze(it.text).shouldWarn }
    }
}

enum class TrialOutcome(val warned: Boolean?) {
    WARNING(true), NO_WARNING(false), NOT_RECEIVED(null), INCONCLUSIVE(null),
}
