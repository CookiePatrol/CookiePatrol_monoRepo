package com.cookiepatrol.evaluation

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TrialEvidenceTest {
    @Test fun changedAppDatasetOrMessengerCannotRelabelPreviousObservations() {
        val old = JSONObject().put("signature", "old").put("app_version", "0.2.0")
        val current = JSONObject().put("signature", "new").put("app_version", "0.2.1")
        val evidence = TrialEvidence(old, JSONObject())
        assertTrue(evidence.record(old, "EN01", TrialOutcome.WARNING, "time1", JSONObject()))
        assertFalse(evidence.record(current, "EP01", TrialOutcome.NO_WARNING, "time2", JSONObject()))
        assertNull(evidence.matrix(emptyList(), current))
        assertEquals(1, evidence.observations.length())
        val exported = evidence.envelope(current)
        assertFalse(exported.getBoolean("environment_compatible"))
        assertEquals("0.2.0", exported.getJSONObject("session_environment").getString("app_version"))
        assertEquals("WARNING", exported.getJSONObject("observations").getJSONObject("EN01").getString("outcome"))
    }

    @Test fun legacyRecordsStayExportableAndNewSessionCountsOnlyConfirmedObservations() {
        val environment = JSONObject().put("signature", "current")
        val legacy = TrialEvidence(JSONObject(), JSONObject().put("EN01", JSONObject().put("outcome", "WARNING")))
        assertFalse(legacy.compatible(environment))
        assertEquals(1, legacy.envelope(environment).getJSONObject("observations").length())
        val fresh = TrialEvidence(JSONObject(), JSONObject())
        val cases = listOf(EvaluationCase("EN01", false, "normal", "문장"), EvaluationCase("EP01", true, "phishing", "문장"))
        assertTrue(fresh.record(environment, "EN01", TrialOutcome.NO_WARNING, "time", JSONObject()))
        assertTrue(fresh.record(environment, "EP01", TrialOutcome.INCONCLUSIVE, "time", JSONObject()))
        assertEquals(ConfusionMatrix(0, 0, 1, 0, 1), fresh.matrix(cases, environment))
    }
}
