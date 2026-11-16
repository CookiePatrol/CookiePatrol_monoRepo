package com.cookiepatrol.detect

import org.junit.Assert.*
import org.junit.Test

class CheckDetailsTest {
    @Test fun aiWarningDoesNotClaimDatabaseConfirmation() {
        val result = detailedResult(true, "ai", listOf("brand_impersonation"))
        assertEquals(CheckStatus.DANGEROUS, result.status)
        assertTrue(result.warningReason.startsWith("AI가"))
        assertFalse(result.warningReason.contains("등록"))
        assertTrue(detailedResult(true, "safe_browsing", emptyList()).warningReason.contains("Google"))
        assertTrue(detailedResult(true, "ai_cache", emptyList()).warningReason.contains("최근"))
    }

    @Test fun rejectsContradictionsAndUnknownEvidence() {
        for (result in listOf(
            detailedResult(true, "ai", emptyList()),
            detailedResult(false, "ai", listOf("deceptive_host")),
            detailedResult(true, "ai", listOf("invented")),
            detailedResult(true, "ai", listOf("deceptive_host", "deceptive_host")),
            detailedResult(false, "safe_browsing", emptyList()),
            detailedResult(true, "unknown-provider", emptyList()),
        )) assertEquals(CheckStatus.UNKNOWN, result.status)
        assertEquals(CheckStatus.NOT_DETECTED, detailedResult(false, "ai", emptyList()).status)
    }
}
