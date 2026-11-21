package com.cookiepatrol.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class BankTransferAssessmentTest(
    private val category: String,
    private val message: String,
    private val expectedBankScore: Int,
    private val expectedWarning: Boolean,
) {
    @Test
    fun assessesTransferContextLocally() {
        val assessment = RiskAnalyzer.analyze(message)
        val bankScore = assessment.hits.filter { it.id == BANK_TRANSFER_SIGNAL_ID }.sumOf { it.score }
        assertEquals(category, expectedBankScore, bankScore)
        assertEquals(category, expectedWarning, assessment.shouldWarn)
        assertTrue(category, assessment.urls.isEmpty())
        assertFalse(category, assessment.shouldLookup(RemoteCheckScope.ALL_MESSAGE_LINKS, fromMessage = true))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun examples(): List<Array<Any>> =
            suspiciousAccounts.map { (category, message) -> arrayOf<Any>(category, message, 3, true) } +
                routineAccounts.map { (category, message) -> arrayOf<Any>(category, message, 0, false) } +
                otherWarningSignals.map { (category, message) -> arrayOf<Any>(category, message, 0, true) }
    }
}

class BankTransferUrlAssessmentTest {
    @Test
    fun doesNotTreatDigitsInALinkAsASuppliedBankAccount() {
        val message = "대출 보증료 안내입니다. 결제 주소 https://example.invalid/pay?reference=(${COMPACT_ACCOUNT})으로 보내 주세요."
        val assessment = RiskAnalyzer.analyze(message)
        assertTrue(assessment.urls.single().startsWith("https://example.invalid/pay?"))
        assertFalse(assessment.hits.any { it.id == BANK_TRANSFER_SIGNAL_ID })
    }
}

// All account and phone numbers are synthetic. The fixtures never initiate a transfer.
private const val GROUPED_ACCOUNT = "000-000000-00000"
private const val COMPACT_ACCOUNT = "00000000000000"

private val suspiciousAccounts = listOf(
    "거래처 계좌 교체" to "[거래처 안내] 기존 계좌가 폐쇄되어 이번 대금은 변경된 계좌에 입금해주세요. 계좌: ${GROUPED_ACCOUNT}",
    "기한 압박이 없는 신규 계좌 요구" to "등록된 계좌는 사용하지 마시고 새 계좌 ${COMPACT_ACCOUNT} 계좌로 이체 바랍니다.",
    "검찰 안전계좌" to "[검찰청] 명의도용 수사 중입니다. 안전계좌 ${GROUPED_ACCOUNT}에 송금하세요.",
    "금융당국 사칭" to "금융감독원입니다. 자금 보호를 위해 국민은행 ${GROUPED_ACCOUNT}으로 이체하세요.",
    "가족 사칭과 휴대폰 고장" to "엄마 나 휴대폰 고장났어. 이 계좌 ${GROUPED_ACCOUNT}으로 돈 좀 보내줘.",
    "회사 대표의 비밀 송금 요구" to "대표입니다. 다른 직원에게는 말하지 말고 우리은행 ${GROUPED_ACCOUNT}에 이체해주세요.",
    "대출 보증료" to "[대출 안내] 대출 승인을 위해 보증료를 계좌번호 ${GROUPED_ACCOUNT}에 입금하세요.",
    "대출 선입금" to "대출을 받으려면 선입금이 필요합니다. 신한은행 ${GROUPED_ACCOUNT}으로 보내 주세요.",
    "환불 수수료" to "환불을 받으시려면 수수료를 새마을금고 ${GROUPED_ACCOUNT}로 먼저 입금하세요.",
    "착오송금 반환 요구" to "제가 착오송금했습니다. 농협 ${GROUPED_ACCOUNT}로 돌려주세요.",
    "채용 보증금" to "알바 채용 확정입니다. 일을 시작하려면 보증금을 우리은행 ${GROUPED_ACCOUNT}로 먼저 입금하세요.",
    "당첨금 처리비" to "당첨금을 받기 전에 처리비를 우체국 ${GROUPED_ACCOUNT}에 입금해주세요.",
    "계좌 정지 압박" to "미입금 시 계좌가 정지됩니다. 지금 계좌번호 ${GROUPED_ACCOUNT}으로 송금하십시오.",
    "동결과 공백 구분 번호" to "불응하면 계좌가 동결됩니다. 오늘 중으로 계좌번호 0000 0000 0000에 이체해주세요.",
    "기존 계좌를 버리고 본문에 제시된 번호로 입금" to "기존 계좌는 폐쇄됐습니다. ${GROUPED_ACCOUNT}으로 입금하세요.",
    "전각 숫자와 줄바꿈" to "대출 보증료를 먼저 입금하세요.\n계좌번호: ０００－００００００－０００００",
    "계좌번호 다음 줄에 보증료 송금 지시" to "대출 보증료 안내입니다.\n계좌번호: ${GROUPED_ACCOUNT}\n이 계좌로 입금하세요.",
    "운송장과 계좌번호가 함께 있음" to "운송장 000000000000. 통관 문제 해결을 위해 지정 계좌 ${GROUPED_ACCOUNT}로만 즉시 송금하세요.",
    "다른 곳에 알리지 말라는 지시" to "피해 방지를 위해 가족에게 알리지 말고 지금 계좌번호 ${GROUPED_ACCOUNT}에 송금하세요.",
)

private val routineAccounts = listOf(
    "계좌번호만 제시" to "계좌번호: ${GROUPED_ACCOUNT}",
    "숫자만 제시" to COMPACT_ACCOUNT,
    "계좌번호와 은행만 제시" to "국민은행 ${COMPACT_ACCOUNT}",
    "정상 회비" to "동아리 11월 회비는 우리은행 ${GROUPED_ACCOUNT}으로 입금해주세요.",
    "기존 임대료 계좌에 납부" to "이번 달 임대료는 기존 계약서에 기재된 계좌 ${GROUPED_ACCOUNT}에 이체해주세요.",
    "일상 식사 정산" to "어제 점심값 8천 원만 카카오뱅크 ${GROUPED_ACCOUNT}에 보내줘.",
    "정상 행사 대관 정산" to "대관료 정산 안내입니다. 신한은행 ${GROUPED_ACCOUNT}에 입금 부탁드립니다.",
    "기존 가상계좌 납부 안내" to "도서관 연체료 안내입니다. 아래 가상계좌 ${GROUPED_ACCOUNT}으로 납부할 수 있습니다.",
    "대출 상환 완료" to "대출 상환이 정상 처리되었습니다. 출금 계좌번호 ${GROUPED_ACCOUNT}",
    "정상 환불 완료" to "환불이 완료됐습니다. 환불 계좌는 우리은행 ${GROUPED_ACCOUNT}입니다.",
    "입금 기록" to "[은행 알림] ${GROUPED_ACCOUNT} 계좌 입금 50,000원 잔액 200,000원",
    "예금주 변경 안내 문의" to "계좌 정보 변경 신청이 접수되었습니다. 계좌번호 ${GROUPED_ACCOUNT}의 변경 내역은 앱에서 확인하세요.",
    "주문번호를 계좌로 간주하지 않음" to "주문번호 ${COMPACT_ACCOUNT}. 기존 계좌를 대체하는 계좌번호는 공식 앱에서 확인하세요.",
    "운송장 번호를 계좌로 간주하지 않음" to "운송장번호 ${GROUPED_ACCOUNT}. 요금 관련 문의는 안내 데스크에서 받습니다.",
    "승인번호만 포함" to "승인번호 ${COMPACT_ACCOUNT}. 승인 내역은 명세서에서 확인할 수 있습니다.",
    "금액을 계좌로 간주하지 않음" to "수리비 000,000원. 청구 금액과 날짜를 확인해주세요.",
    "날짜와 시각" to "정산 회의는 0000-00-00 00:00에 진행합니다. 입금일은 회의에서 정합니다.",
    "부분 마스킹된 계좌" to "대출 보증료는 계좌번호 000-****00-00000에 입금하세요.",
    "카드번호를 계좌로 간주하지 않음" to "카드번호 0000-0000-0000-0000. 대출 보증료를 입금하세요.",
    "착오송금 신고 안내" to "착오송금을 받았다면 ${GROUPED_ACCOUNT}로 돌려주지 말고 거래 은행에 신고하세요.",
    "교육 문구에 인용된 송금 요청" to "보이스피싱 예방 교육 예시: ‘안전계좌 ${GROUPED_ACCOUNT}로 송금하세요.’ 이런 문구를 믿지 마세요.",
    "계좌를 지급 중지한다는 정상 안내" to "신고 계좌 ${GROUPED_ACCOUNT}에 대한 지급정지 처리가 완료되었습니다.",
    "다른 계좌로 송금하지 말라는 안내" to "계좌 변경 문자를 받더라도 계좌번호 ${GROUPED_ACCOUNT}로 송금하지 마세요.",
    "해외 형식 전화번호와 수수료" to "대출 수수료 안내는 +82 10 0000 0000으로 전화 바랍니다.",
)

private val otherWarningSignals = listOf(
    "전화번호만 있는 독립 전화 유도 경고" to "계정이 곧 정지됩니다. 지금 010-0000-0000으로 전화하세요.",
    "번호가 없는 가족 사칭과 송금 경고" to "엄마 나 폰 고장났어. 지금 송금해줘.",
)
