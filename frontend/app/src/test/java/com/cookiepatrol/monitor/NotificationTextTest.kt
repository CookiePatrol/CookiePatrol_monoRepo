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
        assertEquals(2, NotificationText.parts(NotificationContent(lines = listOf("첫 문장", "두 문장", "두 문장"))).size)
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
