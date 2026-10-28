package com.cookiepatrol.detect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlCheckCooldownTest {
    @Test
    fun suppressesSuccessfulRepeatUntilTtlExpires() {
        val now = longArrayOf(100)
        val cooldown = UrlCheckCooldown(ttlMillis = 300, nowMillis = { now[0] })

        assertTrue(cooldown.shouldCheck("https://example.com"))
        now[0] = 399
        assertFalse(cooldown.shouldCheck("https://example.com"))
        now[0] = 400
        assertTrue(cooldown.shouldCheck("https://example.com"))
    }

    @Test
    fun unknownResultCanBeRetriedImmediately() {
        val cooldown = UrlCheckCooldown(nowMillis = { 100 })

        assertTrue(cooldown.shouldCheck("https://example.com"))
        cooldown.forget("https://example.com")
        assertTrue(cooldown.shouldCheck("https://example.com"))
    }

    @Test
    fun evictsOldestEntriesToKeepTheCacheBounded() {
        val cooldown = UrlCheckCooldown(maxEntries = 2, nowMillis = { 100 })

        assertTrue(cooldown.shouldCheck("https://one.example"))
        assertTrue(cooldown.shouldCheck("https://two.example"))
        assertTrue(cooldown.shouldCheck("https://three.example"))
        assertTrue(cooldown.shouldCheck("https://one.example"))
    }
}
