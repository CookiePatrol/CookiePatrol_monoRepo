package com.cookiepatrol.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class PhoneCallbackAssessmentTest(
    private val category: String,
    private val message: String,
    private val suspicious: Boolean,
) {
    @Test
    fun assessesPhoneContextWithoutALink() {
        val assessment = RiskAnalyzer.analyze(message)
        assertEquals(category, suspicious, assessment.hits.any { it.id == "phone_callback" })
        assertEquals(category, suspicious, assessment.shouldWarn)
        assertTrue(category, assessment.urls.isEmpty())
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun examples(): List<Array<Any>> =
            suspiciousExamples.map { (category, message) -> arrayOf<Any>(category, message, true) } +
                routineExamples.map { (category, message) -> arrayOf<Any>(category, message, false) }
    }
}

class InvestigatorCallbackScoreTest {
    @Test
    fun detectsAnInvestigatorCallbackWhenTheNumberFollowsTheInstruction() {
        val assessment = RiskAnalyzer.analyze(
            "당신의 계좌가 대포통장 범죄에 연류되어 막혀있습니다. " +
                "이것을 풀려면 형사의 전화번호로 직접 전화 하십시오. 010-0000-0000",
        )

        assertEquals(listOf(PHONE_CALLBACK_SIGNAL_ID), assessment.hits.map { it.id })
        assertEquals(3, assessment.score)
        assertTrue(assessment.shouldWarn)
        assertTrue(assessment.urls.isEmpty())
    }
}

// Synthetic examples for the local detector. No calls, texts, or network checks are made.
private val suspiciousExamples = listOf(
    "미승인 결제" to "[카드 안내] 승인한 적 없는 결제라면 지금 010-0000-0000으로 전화하세요.",
    "결제 취소" to "해외 결제가 완료됐습니다. 취소하려면 즉시 02-000-0000으로 전화하세요.",
    "정기 결제" to "유료 구독 요금이 오늘 결제됩니다. 취소를 원하면 즉시 070-0000-0000으로 전화하세요.",
    "계정 정지" to "계정이 곧 정지됩니다. 지금 01000000000으로 전화하세요.",
    "계좌 동결" to "계좌가 동결될 예정입니다. 지금 바로 031-000-0000으로 연락하세요.",
    "카드 차단" to "카드 사용이 차단됐습니다. 해제하려면 즉시 010 0000 0000으로 전화하세요.",
    "세금 환급" to "세금 환급 대상입니다. 오늘 중으로 010-0000-0000에 전화하세요.",
    "과태료 미납" to "미납 과태료로 불이익이 생깁니다. 지금 010-0000-0000으로 전화하세요.",
    "대출 수수료" to "대출 승인 안내입니다. 010-0000-0000으로 전화한 뒤 수수료를 입금하세요.",
    "기관 사칭" to "[검찰청] 명의도용 사건의 수사 대상입니다. 즉시 02-000-0000으로 전화하세요.",
    "수사관 사칭" to "수사관입니다. 계좌가 범죄에 연루되었습니다. 지금 070-0000-0000에 전화 바랍니다.",
    "형사 사칭과 다음 줄의 번호" to
        "통장이 대포통장 범죄에 연루됐습니다. 이 문제를 풀려면 담당 형사의 번호로 전화하십시오.\n010-0000-0000",
    "수사관을 내세운 계좌 해제" to
        "계좌가 대포통장 범죄에 연루되어 지급정지되었습니다. 해제하려면 담당 수사관에게 전화하십시오. 연락처: 02-000-0000",
    "배송 문제" to "택배 주소가 맞지 않아 반송 예정입니다. 즉시 010-0000-0000으로 전화하세요.",
    "통관 보류와 전용 연락처" to "통관이 보류되었습니다. 이 번호로만 연락하세요. 전용 번호: (02) 000-0000",
    "경품 수령" to "당첨 상품을 받으시려면 지금 010-0000-0000으로 전화해서 수령비를 입금하세요.",
    "인증번호 요구" to "010-0000-0000으로 전화해 상담원에게 인증번호를 불러주세요.",
    "비밀번호 요구" to "+82 10 0000 0000으로 전화해 비밀번호를 알려주세요.",
    "송금 요구" to "010-0000-0000에 전화한 뒤 안내에 따라 송금해 주세요.",
    "가족 번호 변경" to "엄마 나 폰 고장났어. 010-0000-0000으로 전화해서 인증번호 알려줘.",
    "별도 문장의 주의 문구" to "인증번호는 문자로 보내지 마세요. 지금 010-0000-0000으로 전화해 인증번호를 불러주세요.",
    "줄바꿈과 회신번호" to "해외 결제가 승인되었습니다.\n취소하시려면 지금 010-0000-0000으로 전화주세요.",
    "전각 숫자" to "해외 결제 승인. 즉시 ０１０－００００－００００으로 전화하세요.",
)

private val routineExamples = listOf(
    "휴대폰 번호만" to "010-0000-0000",
    "지역 번호만" to "02-000-0000",
    "대표번호 형식만" to "1500-0000",
    "연락처 공유" to "내 새 번호는 010-0000-0000이야. 저장해 줘.",
    "식당 예약" to "오늘 예약 시간을 변경하시려면 02-000-0000으로 전화하세요.",
    "병원 예약" to "내일 검진 예약입니다. 변경 문의는 031-000-0000으로 전화 부탁드립니다.",
    "업무 일정" to "회의실이 바뀌었습니다. 길을 못 찾으시면 010-0000-0000으로 전화주세요.",
    "수리 기사 방문" to "수리 기사 방문 예정입니다. 시간 변경은 070-0000-0000에 전화해 주세요.",
    "급한 일상 연락" to "회의 시작하기 전에 지금 010-0000-0000으로 전화해 주세요.",
    "발신보다 문자 선호" to "지금 회의 중입니다. 010-0000-0000에 전화하지 말고 문자로 남겨주세요.",
    "배송 완료 문의" to "택배는 경비실에 두었습니다. 필요하시면 010-0000-0000으로 전화주세요.",
    "결제 영수증 문의" to "결제 영수증이 필요하면 02-000-0000으로 전화하세요.",
    "예약금 결제 취소" to "예약을 확정했습니다. 취소와 결제 문의는 02-000-0000으로 전화해주세요.",
    "환불 처리 완료" to "환불 처리가 완료되었습니다. 문의는 070-0000-0000으로 전화 바랍니다.",
    "과태료 납부 완료" to "과태료가 정상적으로 납부되었습니다. 문의는 02-000-0000으로 전화하세요.",
    "공식 앱으로 확인" to "해외 결제 내역은 공식 앱에서 확인하세요. 일반 이용 안내 02-000-0000.",
    "정지 위협에 응하지 말라는 안내" to "계정 정지 문자에 010-0000-0000으로 전화하라는 내용이 있어도 응하지 마세요.",
    "해당 번호로 전화하지 말라는 안내" to "명의도용 안내 문자를 받아도 010-0000-0000으로 전화하지 마세요.",
    "전화로 민감정보를 요구하지 않음" to "문의는 02-000-0000으로 전화하세요. 전화로 비밀번호를 요구하지 않습니다.",
    "상담 중 인증번호 비공유" to "고객 상담은 02-000-0000으로 전화하세요. 상담원에게 인증번호를 불러주지 마세요.",
    "보안 교육에 인용된 사례" to "보안교육 예시: ‘해외 결제가 됐으니 지금 010-0000-0000으로 전화하세요.’ 이런 문자는 무시하세요.",
    "일반적인 수사 진행 문의" to
        "담당 형사가 사건 진행 일정을 알려드립니다. 확인이 필요하면 전화 하십시오. 010-0000-0000",
    "수사기관 사칭 문자에서 공식 번호 확인" to
        "계좌가 대포통장 범죄에 연루됐다는 수사관 사칭 연락을 받았다면 공식 홈페이지에서 직접 찾은 번호 02-000-0000으로 지금 전화하여 확인하세요.",
    "수사 사칭의 번호로 전화하지 말라는 안내" to
        "대포통장 범죄에 연류되었다며 형사라고 하면 속지 마세요. 010-0000-0000으로 전화하지 마세요.",
    "보안 교육에 인용된 형사 사칭" to
        "보이스피싱 사칭 예시: ‘계좌가 대포통장 범죄에 연루되었습니다. 이를 풀려면 형사의 번호로 전화 하십시오. 010-0000-0000’ 이런 문자는 무시하세요.",
    "주문번호" to "주문번호 910-0000-0000입니다. 등록된 배송 주소를 확인해 주세요.",
    "긴 식별번호 안의 휴대폰 형태" to "문서번호 9901000000000. 문의는 담당자를 찾아주세요.",
    "전화번호 없는 결제 통지" to "해외 결제 여부는 직접 공식 앱에서 확인해 주세요.",
    "기존에 저장한 번호로 확인" to "계정 정지 문자를 받으면 문자 속 번호에 전화하지 말고, 기존에 저장한 번호로만 확인하세요. 문자에 적힌 번호 예시: 010-0000-0000",
)
