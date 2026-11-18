package com.cookiepatrol.monitor

import org.junit.Assert.*
import org.junit.Test

class NotificationTextTest {
    @Test fun messagingStyleKeepsDifferentMessagesSeparate() {
        val result = NotificationText.parts(NotificationContent(title = "대화방", text = "메시지 2개", bigText = "이전 내용",
            messages = listOf(MessagePart("인증번호 123456", 1), MessagePart("사진 보내줘", 2))))
        assertEquals(listOf("인증번호 123456", "사진 보내줘"), result.map { it.text })
    }
    @Test fun expandedTextAndSummaryFallbacks() {
        assertEquals("긴 실제 본문", NotificationText.parts(NotificationContent(text = "짧은 본문", bigText = "긴 실제 본문")).single().text)
        assertTrue(NotificationText.parts(NotificationContent(title = "새 메시지 8개", groupSummary = true)).isEmpty())
        assertEquals(3, NotificationText.parts(NotificationContent(lines = listOf("첫 문장", "두 문장", "두 문장"))).size)
    }
    @Test fun summariesDoNotRescanChildMessages() {
        val body = NotificationContent(
            groupSummary = true,
            text = "수신 메시지",
            bigText = "의심 링크를 열어주세요",
            lines = listOf("의심 링크를 열어주세요"),
            messages = listOf(MessagePart("의심 링크를 열어주세요", 1)),
        )
        assertTrue(NotificationText.parts(body).isEmpty())
        assertEquals(listOf(body.messages.single()), NotificationText.parts(body.copy(groupSummary = false)))
    }

    @Test fun repeatedHistoryKeepsIndependentMessagesEvenWhenTheTextIsIdentical() {
        val tracker = NotificationMessageTracker()
        val first = MessagePart("인증번호를 보내주세요", 1L, "한 사람")
        val second = first.copy(timestamp = 2L)
        val third = second.copy(sender = "다른 사람")

        val firstMessage = tracker.update("old", "conversation", listOf(first)).single()
        assertTrue(tracker.update("old", "conversation", listOf(first)).isEmpty())
        tracker.remove("old")
        val incoming = tracker.update("new", "conversation", listOf(first, second, third))
        assertEquals(listOf(second, third), incoming.map { it.part })
        assertEquals(3, (incoming.map { it.eventKey } + firstMessage.eventKey).toSet().size)
        assertEquals(listOf(second), tracker.update("other", "other conversation", listOf(second)).map { it.part })
    }

    @Test fun untimedInboxUpdatesInspectOnlyNewOccurrences() {
        val tracker = NotificationMessageTracker()
        val first = MessagePart("첫 번째")
        val suspicious = MessagePart("의심 메시지")
        val next = MessagePart("새 메시지")

        val initial = tracker.update("key", "conversation", listOf(first, suspicious))
        assertEquals(listOf(first, suspicious), initial.map { it.part })
        assertTrue(tracker.update("key", "conversation", listOf(first, suspicious)).isEmpty())
        val incoming = tracker.update("key", "conversation", listOf(suspicious, suspicious, next))
        assertEquals(listOf(suspicious, next), incoming.map { it.part })
        assertNotEquals(initial.last().eventKey, incoming.first().eventKey)
        tracker.remove("key")
        assertNotEquals(initial.last().eventKey, tracker.update("key", "conversation", listOf(suspicious)).single().eventKey)
    }
    @Test fun boundsTextAndRetainsLatestMessages() {
        val result = NotificationText.parts(NotificationContent(messages = (1..30).map { MessagePart("$it".repeat(20_000), it.toLong()) }))
        assertEquals(10, result.size)
        assertTrue(result.all { it.text.length <= 12_000 })
        assertEquals(21L, result.first().timestamp)
    }
    @Test fun duplicateGuardKeepsNewMessagesRegardlessOfSharedReason() {
        var now = 0L
        val gate = EventDeduplicator(ttlMillis = 30, maxEntries = 2, clock = { now }, nanos = false)
        assertTrue(gate.admit(fingerprint("message-a")))
        assertFalse(gate.admit(fingerprint("message-a")))
        assertTrue(gate.admit(fingerprint("message-b")))
        now = 30
        assertTrue(gate.admit(fingerprint("message-a")))
        gate.forget(fingerprint("message-a"))
        assertTrue(gate.admit(fingerprint("message-a")))
    }
}
