package com.cookiepatrol.detect

import org.junit.Assert.assertEquals
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
    fun demoMessageTriggersLocalWarningThreshold() {
        assertTrue(score("[배송 안내] 주소가 맞지 않아 택배가 보류됐습니다. 확인: http://192.0.2.1/parcel.apk") >= 3)
    }

    @Test
    fun urlsAreTrimmedValidatedAndNormalized() {
        assertEquals(
            listOf(
                "HTTP://Example.com:8080/path?q=1",
                "https://192.0.2.1/parcel.apk",
                "https://[2001:db8::1]:8443/path",
                "https://bare.example/path",
                "https://wrapped.example/path",
            ),
            UrlExtractor.extract(
                "HTTP://Example.com:8080/path?q=1. https://192.0.2.1/parcel.apk, " +
                    "https://[2001:db8::1]:8443/path bare.example/path) [https://wrapped.example/path]"
            ),
        )
    }

    @Test
    fun normalMessageIsNot() {
        assertTrue(score("내일 회의 자료 https://docs.google.com/abc 확인 부탁드려요") < 3)
    }
}
