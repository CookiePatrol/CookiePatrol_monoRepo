package com.cookiepatrol.detect

import org.junit.Assert.assertTrue
import org.junit.Test

class SignalsTest {
    private fun score(text: String) = SIGNALS.filter { it.matches(text.lowercase()) }.sumOf { it.score }

    @Test
    fun smishingIsSuspicious() {
        assertTrue(score("[국제발신] 택배 주소 불일치, 확인 bit.ly/x1Y2") >= 3)
        assertTrue(score("엄마 나 폰 액정 깨졌어 http://1.2.3.4/a.apk") >= 3)
    }

    @Test
    fun normalMessageIsNot() {
        assertTrue(score("내일 회의 자료 https://docs.google.com/abc 확인 부탁드려요") < 3)
    }
}
