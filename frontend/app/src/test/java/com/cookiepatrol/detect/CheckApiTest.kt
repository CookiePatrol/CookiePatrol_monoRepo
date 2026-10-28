package com.cookiepatrol.detect

import org.junit.Assert.assertEquals
import org.junit.Test

class CheckApiTest {
    @Test
    fun mapsSuccessfulProviderVerdicts() {
        assertEquals(CheckStatus.DANGEROUS, statusForResponse(200, true))
        assertEquals(CheckStatus.NOT_LISTED, statusForResponse(200, false))
    }

    @Test
    fun mapsErrorsAndMissingVerdictsToUnknown() {
        assertEquals(CheckStatus.UNKNOWN, statusForResponse(503, true))
        assertEquals(CheckStatus.UNKNOWN, statusForResponse(200, null))
    }
}
