package com.cookiepatrol.warn

import org.junit.Assert.*
import org.junit.Test

class WarningHistoryTest {
    @Test fun lateFailuresAndAiCannotOverwriteGoogleOrLocalWarnings() {
        val history = WarningHistory()
        val displayed = mutableListOf<String>()
        history.publish("message", WarningPriority.GOOGLE) { displayed.add("google") }
        history.publish("message", WarningPriority.UNAVAILABLE) { displayed.add("timeout") }
        history.publish("message", WarningPriority.AI) { displayed.add("ai") }
        assertEquals(listOf("google"), displayed)
        history.publish("local", WarningPriority.LOCAL) { displayed.add("local") }
        history.publish("local", WarningPriority.UNAVAILABLE) { displayed.add("failure") }
        assertEquals(listOf("google", "local"), displayed)
    }

    @Test fun strongerEvidenceUpgradesAndNewMessagesRemainIndependent() {
        val history = WarningHistory()
        val displayed = mutableListOf<String>()
        history.publish("first", WarningPriority.UNAVAILABLE) { displayed.add("unknown") }
        history.publish("first", WarningPriority.AI) { displayed.add("ai") }
        history.publish("first", WarningPriority.GOOGLE) { displayed.add("google") }
        history.publish("second", WarningPriority.LOCAL) { displayed.add("new message") }
        assertEquals(listOf("unknown", "ai", "google", "new message"), displayed)
    }

    @Test fun onlyTheFirstPublicationForEachMessageMayAlert() {
        val history = WarningHistory()
        val updates = mutableListOf<Boolean>()
        history.publish("first", WarningPriority.LOCAL, updates::add)
        history.publish("first", WarningPriority.LOCAL, updates::add)
        history.publish("first", WarningPriority.AI, updates::add)
        history.publish("first", WarningPriority.AI, updates::add)
        history.publish("first", WarningPriority.GOOGLE, updates::add)
        history.publish("second", WarningPriority.GOOGLE, updates::add)
        assertEquals(listOf(false, true, true, false), updates)
    }

    @Test fun failedDeliveryDoesNotPoisonHistoryAndExpiredEvidenceCanRefresh() {
        var time = 0L
        val history = WarningHistory(ttlMillis = 10, maxEntries = 2, now = { time })
        assertFalse(history.publish("event", WarningPriority.GOOGLE) { throw SecurityException() })
        var displayed = ""
        assertTrue(history.publish("event", WarningPriority.LOCAL) { displayed = "local" })
        assertEquals("local", displayed)
        history.publish("event", WarningPriority.GOOGLE) { displayed = "google" }
        time = 10
        history.publish("event", WarningPriority.UNAVAILABLE) { displayed = "new check unknown" }
        assertEquals("new check unknown", displayed)
    }
}
