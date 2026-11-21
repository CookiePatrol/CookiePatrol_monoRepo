package com.cookiepatrol.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalsTest {
    private fun score(text: String) = SIGNALS.filter { it.matches(text.lowercase()) }.sumOf { it.score }

    @Test
    fun safetyAdviceDoesNotCancelASeparatePositiveRequest() {
        assertTrue(RiskAnalyzer.analyze("인증번호를 알려주지 마세요. 엄마 사칭에 주의하세요.").score < 3)
        assertTrue(RiskAnalyzer.analyze("주민등록번호를 입력하지 마세요.").score < 3)
        val result = RiskAnalyzer.analyze("엄마 나 폰 고장났어. 인증번호 입력해주세요. 사진은 보내지 마세요.")
        assertTrue(result.shouldWarn)
        assertTrue(result.hits.any { it.id == "credential" })
    }

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
        val result = RiskAnalyzer.analyze("택배 주소를 확인하세요: https://parcel.example.com/track")
        assertFalse(result.shouldWarn)
        assertTrue(result.shouldLookup(RemoteCheckScope.SUSPICIOUS, fromMessage = true))
    }

    @Test
    fun lowScoreLinksInMessageNotificationsAreCheckedAfterAllLinksConsent() {
        val result = RiskAnalyzer.analyze("회의 자료 https://example.com/meeting")
        assertEquals(0, result.score)
        assertTrue(result.shouldLookup(RemoteCheckScope.ALL_MESSAGE_LINKS, fromMessage = true))
        assertFalse(result.shouldLookup(RemoteCheckScope.SUSPICIOUS, fromMessage = true))
        assertFalse(result.shouldLookup(RemoteCheckScope.OFF, fromMessage = true))
        assertFalse(result.shouldLookup(RemoteCheckScope.ALL_MESSAGE_LINKS, fromMessage = false))
        assertFalse(RiskAnalyzer.analyze("내일 만나요").shouldLookup(RemoteCheckScope.ALL_MESSAGE_LINKS, fromMessage = true))
    }

    @Test
    fun remembersDangerousUrlsAndEvictsTheOldestBeyond500Entries() {
        val urls = (0..500).map { "https://dangerous-memory.example/$it" }
        val oldest = urls.first()
        val coordinator = ReputationCoordinator(java.util.concurrent.Executor { it.run() }, {
            CheckResult(CheckStatus.DANGEROUS, "ai_cache")
        })
        assertEquals(null, coordinator.cachedDangerous(oldest))
        coordinator.lookup(oldest) {}
        assertEquals(CheckStatus.DANGEROUS, coordinator.cachedDangerous(oldest)?.status)
        assertFalse(RiskAnalyzer.analyze(oldest).shouldLookup(RemoteCheckScope.SUSPICIOUS, fromMessage = true))
        urls.subList(1, 500).forEach { coordinator.lookup(it) {} }
        assertEquals(CheckStatus.DANGEROUS, coordinator.cachedDangerous(oldest)?.status)
        coordinator.lookup(oldest) {}
        coordinator.lookup(urls.last()) {}
        assertEquals(null, coordinator.cachedDangerous(oldest))
        urls.drop(1).forEach { assertEquals(CheckStatus.DANGEROUS, coordinator.cachedDangerous(it)?.status) }
        assertEquals(null, coordinator.cachedDangerous("https://unseen-memory.example"))
    }

    @Test
    fun deceptiveUserInfoUrlTriggersWarningAndChecksActualHost() {
        val text = "https://trusted.example@attacker.example/login"
        assertTrue(score(text) >= 3)
        assertEquals(listOf("https://attacker.example/login"), UrlExtractor.extract(text))
    }

    @Test
    fun embeddedOfficialDomainTriggersLocalWarningOnItsOwn() {
        val result = RiskAnalyzer.analyze("https://paypal.com.account-verification.invalid/sign-in")

        assertEquals(listOf("embedded_official_domain"), result.hits.map { it.id })
        assertTrue(result.shouldWarn)
        assertTrue(result.shouldLookup(RemoteCheckScope.SUSPICIOUS, fromMessage = true))
    }

    @Test
    fun officialDomainsOnlyCountWhenEmbeddedInAnotherHost() {
        listOf(
            "https://paypal.com/sign-in",
            "https://www.paypal.com/sign-in",
            "https://paypal.com.subdomain.paypal.com/sign-in",
            "https://notpaypal.com.account-verification.invalid/sign-in",
            "https://example.com/help/paypal.com/guide",
        ).forEach { url ->
            assertFalse(RiskAnalyzer.analyze(url).hits.any { it.id == "embedded_official_domain" })
        }
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
    fun bareNumbersAndDatesDoNotBecomeLinksForAutomaticChecks() {
        val text = "결제 금액 10.00, 날짜 2026.11.21, 버전 1.21, 연락처 01012345678"
        assertTrue(UrlExtractor.extract(text).isEmpty())
        assertEquals(null, UrlExtractor.normalize("01012345678"))
        assertEquals(listOf("https://192.0.2.1/parcel"), UrlExtractor.extract("192.0.2.1/parcel"))
    }

    @Test
    fun callbackCanStartBeforeTheNumberAndSpanLines() {
        val result = RiskAnalyzer.analyze("카드 사용이 차단됩니다. 지금 전화해주세요.\n(02) 000-0000")
        assertTrue(result.shouldWarn)
        assertTrue(result.hits.any { it.id == PHONE_CALLBACK_SIGNAL_ID })
        assertTrue(result.urls.isEmpty())
    }

    @Test
    fun adviceAboutAnOfficialNumberDoesNotHideASeparateRiskyCallback() {
        val result = RiskAnalyzer.analyze(
            "카드 뒷면에 적힌 고객센터: 02-000-0000. 계정 정지 예정입니다. " +
                "지금 010-0000-0000으로만 전화하세요.",
        )
        assertTrue(result.hits.any { it.id == PHONE_CALLBACK_SIGNAL_ID })
    }

    @Test
    fun sensitiveRequestRemainsRiskyEvenWhenItClaimsAnOfficialNumber() {
        val result = RiskAnalyzer.analyze("공식 앱에서 확인한 번호 02-000-0000으로 전화해서 인증번호를 불러주세요.")
        assertTrue(result.hits.any { it.id == PHONE_CALLBACK_SIGNAL_ID })
    }

    @Test
    fun aCompletedPaymentIsNotASensitiveActionRequest() {
        val result = RiskAnalyzer.analyze("입금하셨다면 02-000-0000으로 전화해 주세요.")
        assertFalse(result.hits.any { it.id == PHONE_CALLBACK_SIGNAL_ID })
    }

    @Test
    fun aDifferentNumberInACompletedEducationalQuoteDoesNotHideTheCallback() {
        val result = RiskAnalyzer.analyze(
            "피싱 예시: ‘지금 02-000-0000으로 전화해 인증번호를 불러주세요.’ " +
                "계정이 곧 정지됩니다. 지금 010-0000-0000으로 전화하세요.",
        )
        assertTrue(result.hits.any { it.id == PHONE_CALLBACK_SIGNAL_ID })
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
