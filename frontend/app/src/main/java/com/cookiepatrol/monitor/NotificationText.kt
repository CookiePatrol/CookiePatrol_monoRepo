package com.cookiepatrol.monitor

import java.security.MessageDigest

data class MessagePart(val text: String, val timestamp: Long = 0)
data class NotificationContent(
    val title: String = "", val text: String = "", val bigText: String = "",
    val lines: List<String> = emptyList(), val messages: List<MessagePart> = emptyList(),
    val groupSummary: Boolean = false,
)

/** Separate messages must not combine into a fabricated risk context. History is not scanned. */
internal object NotificationText {
    fun parts(content: NotificationContent): List<MessagePart> {
        val bodies = when {
            content.messages.any { it.text.isNotBlank() } -> content.messages.filter { it.text.isNotBlank() }.takeLast(10)
            content.lines.any { it.isNotBlank() } -> content.lines.filter { it.isNotBlank() }.takeLast(10).map { MessagePart(it) }
            content.bigText.isNotBlank() -> listOf(MessagePart(content.bigText))
            content.text.isNotBlank() -> listOf(MessagePart(content.text))
            !content.groupSummary && content.title.isNotBlank() -> listOf(MessagePart(content.title))
            else -> emptyList()
        }
        return bodies.distinct().map { it.copy(text = it.text.take(12_000)) }
    }
}

internal fun fingerprint(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

/** Keys are transient hashes; admission for a different message never depends on its risk reason. */
internal class EventDeduplicator(
    private val ttlMillis: Long = 30_000,
    private val maxEntries: Int = 512,
    private val clock: () -> Long = System::nanoTime,
    private val nanos: Boolean = true,
) {
    private val seen = LinkedHashMap<String, Long>()
    @Synchronized fun admit(key: String): Boolean {
        val now = clock().let { if (nanos) it / 1_000_000 else it }
        seen.entries.removeAll { now - it.value >= ttlMillis }
        if (seen.containsKey(key)) return false
        seen[key] = now
        while (seen.size > maxEntries) seen.remove(seen.keys.first())
        return true
    }
    @Synchronized fun forget(key: String) { seen.remove(key) }
}
