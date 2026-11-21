package com.cookiepatrol.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageLinkChecksTest {
    @Test fun reportsOnlyOneQuietResultAfterEveryLinkHasBeenChecked() {
        val checks = MessageLinkChecks(2)
        assertNull(checks.record(CheckStatus.NOT_DETECTED))
        assertEquals(CheckStatus.NOT_DETECTED, checks.record(CheckStatus.NOT_DETECTED))
    }

    @Test fun aDangerousLinkSuppressesTheQuietResult() {
        val checks = MessageLinkChecks(3)
        assertNull(checks.record(CheckStatus.NOT_DETECTED))
        assertNull(checks.record(CheckStatus.DANGEROUS))
        assertNull(checks.record(CheckStatus.UNKNOWN))
    }

    @Test fun failedOrSkippedLinksNeverProduceANotDetectedResult() {
        val checks = MessageLinkChecks(2)
        assertNull(checks.record(CheckStatus.UNKNOWN))
        assertEquals(CheckStatus.UNKNOWN, checks.record(CheckStatus.NOT_DETECTED))
        assertEquals(
            CheckStatus.UNKNOWN,
            MessageLinkChecks(1, hasUncheckedLinks = true).record(CheckStatus.NOT_DETECTED),
        )
    }
}
