package com.cookiepatrol.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CombinedMessageAssessmentTest {
    @Test
    fun ordinaryLinkContactAndDuesScoreZero() {
        val assessment = RiskAnalyzer.analyze(
            "동호회 일정 https://example.com 문의 010-0000-0000. 모임 회비 계좌 국민은행 000-0000-000000.",
        )

        assertEquals(0, assessment.score)
        assertFalse(assessment.shouldWarn)
        assertEquals(listOf("https://example.com"), assessment.urls)
    }

    @Test
    fun twoRiskyRequestsContributeSixPointsWithAnOrdinaryHttpsLink() {
        val assessment = RiskAnalyzer.analyze("$RISKY_REQUESTS 확인 https://example.com/")

        assertEquals(6, assessment.score)
        assertTrue(assessment.shouldWarn)
        assertEquals(
            mapOf(SignalCategory.PHONE to 3, SignalCategory.BANK_ACCOUNT to 3),
            assessment.hits.associate { assessment.categoryFor(it) to it.score },
        )
    }

    @Test
    fun httpLinkAddsOnePointToTheTwoRiskyRequests() {
        val assessment = RiskAnalyzer.analyze(
            "$RISKY_REQUESTS 확인 http://testsafebrowsing.appspot.com/apiv4/ANY_PLATFORM/SOCIAL_ENGINEERING/URL/",
        )

        assertEquals(7, assessment.score)
        assertEquals(
            mapOf(SignalCategory.LINK to 1, SignalCategory.PHONE to 3, SignalCategory.BANK_ACCOUNT to 3),
            assessment.hits.associate { assessment.categoryFor(it) to it.score },
        )
    }

    @Test
    fun apkLinkAddsThreePointsAndRepeatedFindingsCountOnce() {
        val sections = "$RISKY_REQUESTS 확인 https://example.invalid/update.apk"
        val assessment = RiskAnalyzer.analyze("$sections\n$sections")

        assertEquals(9, assessment.score)
        assertEquals(
            listOf("apk", PHONE_CALLBACK_SIGNAL_ID, BANK_TRANSFER_SIGNAL_ID),
            assessment.hits.map { it.id },
        )
        assertEquals(SignalCategory.LINK, assessment.categoryFor(assessment.hits.first()))
        assertEquals(listOf("https://example.invalid/update.apk"), assessment.urls)
    }

    @Test
    fun bankPressureDoesNotAffectAnOrdinaryCallbackClause() {
        val assessment = RiskAnalyzer.analyze(
            "해외 결제가 승인되었습니다. 문의는 010-0000-0000으로 전화하세요. " +
                "변경된 계좌 국민은행 000-0000-000000으로 지금 입금해 주세요. https://example.com/",
        )

        assertEquals(setOf("transfer", BANK_TRANSFER_SIGNAL_ID), assessment.hits.map { it.id }.toSet())
    }

    @Test
    fun phonePressureDoesNotAffectARoutineAccountClause() {
        val assessment = RiskAnalyzer.analyze(
            "계정이 곧 정지됩니다. 지금 010-0000-0000으로 전화하세요. " +
                "모임 회비는 계좌 국민은행 000-0000-000000으로 보내 주세요. https://example.com/",
        )

        assertEquals(setOf("suspension", PHONE_CALLBACK_SIGNAL_ID), assessment.hits.map { it.id }.toSet())
    }

    @Test
    fun phoneNumberInsideAUrlIsNotScoredAsACallbackNumber() {
        val assessment = RiskAnalyzer.analyze(
            "해외 결제가 승인되었습니다. 지금 https://example.com/call/(010-0000-0000)으로 전화하세요.",
        )

        assertEquals(1, assessment.urls.size)
        assertFalse(assessment.hits.any { it.id == PHONE_CALLBACK_SIGNAL_ID })
    }

    @Test
    fun fileMentionIsNotAttributedToADifferentLink() {
        val assessment = RiskAnalyzer.analyze("안내 https://example.com/ 참고 파일 update.apk")
        val apk = assessment.hits.single { it.id == "apk" }

        assertEquals(SignalCategory.MESSAGE, assessment.categoryFor(apk))
    }

    private companion object {
        const val RISKY_REQUESTS = "해외 결제가 승인되었습니다. 취소하려면 즉시 010-0000-0000으로 전화하세요. " +
            "기존 계좌는 사용하지 마세요. 변경된 계좌 국민은행 000-0000-000000으로 대금을 보내 주세요."
    }
}
