package com.cookiepatrol.detect

import java.util.LinkedHashMap

/** Bounds repeat provider lookups while allowing transient failures to be retried. */
internal class UrlCheckCooldown(
    private val ttlMillis: Long = 5 * 60_000L,
    private val maxEntries: Int = 500,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val checkedAt = LinkedHashMap<String, Long>()

    @Synchronized
    fun shouldCheck(url: String): Boolean {
        val now = nowMillis()
        val lastChecked = checkedAt[url]
        if (lastChecked != null && now - lastChecked < ttlMillis) return false
        checkedAt.remove(url)
        checkedAt[url] = now
        while (checkedAt.size > maxEntries) {
            checkedAt.remove(checkedAt.entries.first().key)
        }
        return true
    }

    @Synchronized
    fun forget(url: String) {
        checkedAt.remove(url)
    }
}
