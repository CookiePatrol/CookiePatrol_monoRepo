package com.cookiepatrol.evaluation

import org.json.JSONObject

/** Keep observations tied to the environment that produced them, including across app upgrades. */
internal class TrialEvidence(var environment: JSONObject, val observations: JSONObject) {
    fun compatible(current: JSONObject): Boolean = observations.length() == 0 ||
        (environment.optString("signature").isNotBlank() && environment.optString("signature") == current.optString("signature"))

    fun record(current: JSONObject, caseId: String, outcome: TrialOutcome, at: String, permissions: JSONObject): Boolean {
        if (!compatible(current)) return false
        if (observations.length() == 0) environment = current
        observations.put(caseId, JSONObject().put("outcome", outcome.name).put("recorded_at", at)
            .put("permissions", permissions).put("environment_signature", environment.getString("signature")))
        return true
    }

    fun matrix(cases: List<EvaluationCase>, current: JSONObject): ConfusionMatrix? {
        if (!compatible(current)) return null
        return ConfusionMatrix.calculate(cases) { case ->
            val name = observations.optJSONObject(case.id)?.optString("outcome")
            TrialOutcome.entries.firstOrNull { it.name == name }?.warned
        }
    }

    fun envelope(current: JSONObject): JSONObject = JSONObject()
        .put("schema_version", 2).put("environment_compatible", compatible(current))
        .put("session_environment", if (observations.length() == 0) current else environment)
        .put("observations", observations)
}
