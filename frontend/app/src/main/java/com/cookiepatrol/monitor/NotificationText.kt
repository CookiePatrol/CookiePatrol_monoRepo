package com.cookiepatrol.monitor

import java.security.MessageDigest
import java.util.UUID

data class MessagePart(val text: String, val timestamp: Long = 0, val sender: String? = null)
data class NotificationContent(
    val title: String = "", val text: String = "", val bigText: String = "",
    val lines: List<String> = emptyList(), val messages: List<MessagePart> = emptyList(),
    val groupSummary: Boolean = false,
)

/** Conversation notifications carry the individual messages. The summary repeats their content. */
internal object NotificationText {
    fun parts(content: NotificationContent): List<MessagePart> {
        if (content.groupSummary) return emptyList()
        val bodies = when {
            content.messages.any { it.text.isNotBlank() } -> content.messages.filter { it.text.isNotBlank() }.takeLast(10)
            content.lines.any { it.isNotBlank() } -> content.lines.filter { it.isNotBlank() }.takeLast(10).map { MessagePart(it) }
            content.bigText.isNotBlank() -> listOf(MessagePart(content.bigText))
            content.text.isNotBlank() -> listOf(MessagePart(content.text))
            content.title.isNotBlank() -> listOf(MessagePart(content.title))
            else -> emptyList()
        }
        return bodies.map { it.copy(text = it.text.take(12_000)) }
    }
}

internal data class IncomingMessage(val eventKey: String, val part: MessagePart)

/** Retain hashes of the messages still displayed under each notification key. */
internal class NotificationMessageTracker {
    private val active = LinkedHashMap<String, Map<String, Int>>()
    private val recentTimedMessages = EventDeduplicator(ttlMillis = 24 * 60 * 60_000L, maxEntries = 2_048)

    @Synchronized
    fun update(key: String, conversation: String, parts: List<MessagePart>): List<IncomingMessage> {
        val previous = active.remove(key).orEmpty()
        val current = mutableMapOf<String, Int>()
        val incoming = parts.mapNotNull { part ->
            val timed = part.timestamp > 0
            val body = fingerprint("$conversation\u0000${part.timestamp}\u0000${part.sender.orEmpty()}\u0000${part.text}")
            val occurrence = current.getOrDefault(body, 0) + 1
            current[body] = occurrence
            if (occurrence <= previous.getOrDefault(body, 0)) return@mapNotNull null

            if (timed) {
                if (recentTimedMessages.admit(body)) IncomingMessage(body, part) else null
            } else {
                // InboxStyle supplies no per-message time. Use new occurrences in its current snapshot.
                IncomingMessage(UUID.randomUUID().toString(), part)
            }
        }
        if (current.isNotEmpty()) active[key] = current
        while (active.size > 512) active.remove(active.keys.first())
        return incoming
    }

    @Synchronized
    fun remove(key: String) {
        active.remove(key)
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
