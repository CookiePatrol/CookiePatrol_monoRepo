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
    fun fakeAccountSecurityPageTriggersLocalWarningThreshold() {
        assertTrue(
            score("계정 보안 확인이 필요합니다. 지금 확인하지 않으면 이용이 제한됩니다. 확인 주소: http://192.0.2.2/account.apk") >= 3,
        )
    }

    @Test
    fun fakePrizeAdWithShortLinkTriggersLocalWarningThreshold() {
        assertTrue(score("축하합니다! 경품에 당첨됐어요. 지금 확인하세요: bit.ly/reward-check") >= 3)
    }

    @Test
    fun accountSuspensionThreatWithShortLinkTriggersLocalWarningThreshold() {
        assertTrue(score("고객님 계정이 곧 정지됩니다. 지금 보안 확인: bit.ly/account-check") >= 3)
    }

    @Test
    fun familyImpersonationAskingForVerificationCodeTriggersWarning() {
        assertTrue(score("엄마 나 휴대폰이 고장났어. 본인 확인해야 하니까 인증번호 보내줘.") >= 3)
        assertTrue(score("아빠 계좌가 바뀌었어. 급하게 30만원 이체해줘.") >= 3)
    }

    @Test
    fun insecurePaymentLinkAndSensitiveDataRequestTriggerWarning() {
        assertTrue(score("미납 요금 확인: http://192.0.2.3/pay. 카드 비밀번호와 인증번호를 입력하세요.") >= 3)
        assertTrue(score("정부지원 환급 신청: http://192.0.2.4/refund") >= 3)
    }

    @Test
    fun suspiciousHttpsLinkIsCheckedEvenBelowLocalWarningThreshold() {
        val riskScore = score("택배 주소를 확인하세요: https://parcel.example.com/track")
        assertTrue(riskScore < 3)
        assertTrue(Detector.isLookupCandidate(riskScore))
    }

    @Test
    fun deceptiveUserInfoUrlTriggersWarningAndChecksActualHost() {
        val text = "https://trusted.example@attacker.example/login"
        assertTrue(score(text) >= 3)
        assertEquals(listOf("https://attacker.example/login"), UrlExtractor.extract(text))
    }

    @Test
    fun unicodeHomographDomainIsCanonicalizedAndFlagged() {
        val url = UrlExtractor.extract("https://раураl.com/login").single()
        assertTrue(url.contains("xn--"))
        assertTrue(score(url) >= 2)
    }

    @Test
    fun urlsAreTrimmedValidatedAndNormalized() {
        assertEquals(
            listOf(
                "http://example.com:8080/path?q=1",
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

    @Test
    fun normalAdvertisementIsNotSuspiciousByItself() {
        assertTrue(score("오늘 하루 20% 할인 쿠폰을 드려요. 자세한 내용은 https://shop.example.com/event 에서 확인하세요.") < 3)
    }

    @Test
    fun familyOrIdentityMentionsWithoutRiskRequestDoNotTrigger() {
        assertTrue(score("엄마가 새 휴대폰으로 바꿨어. 사진 보내줘.") < 3)
        assertTrue(score("주민등록번호 제도에 관한 안내 자료입니다.") < 3)
        assertTrue(score("고객센터에서 계정 보안과 로그인 잠금 정책을 안내합니다.") < 3)
    }
}
