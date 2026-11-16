package com.cookiepatrol.warn

enum class WarningPriority { UNAVAILABLE, LOCAL, AI, GOOGLE }

/** Serialize updates so a late timeout/AI response cannot replace stronger evidence. */
internal class WarningHistory(
    private val ttlMillis: Long = 5 * 60_000,
    private val maxEntries: Int = 500,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val evidence = LinkedHashMap<String, Pair<Long, WarningPriority>>()

    @Synchronized fun publish(event: String, priority: WarningPriority, post: () -> Unit): Boolean {
        val time = now()
        evidence.entries.removeAll { time - it.value.first >= ttlMillis }
        val previous = evidence[event]
        if (previous != null && previous.second > priority) return true // Stronger warning already handled it.
        return runCatching {
            post()
            evidence.remove(event)
            evidence[event] = time to priority
            while (evidence.size > maxEntries) evidence.remove(evidence.keys.first())
            true
        }.getOrDefault(false)
    }
}
