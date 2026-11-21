package com.cookiepatrol.detect

import java.text.Normalizer

internal const val BANK_TRANSFER_SIGNAL_ID = "bank_transfer"

internal object BankTransferRisk {
    private const val CONTEXT_LENGTH = 160
    private const val REQUEST_LENGTH = 80
    private const val NUMBER_SEPARATOR = "[\\p{Zs}\\t\\p{Pd}\\u2212]{0,2}"
    private const val TRANSFER_VERB = """(?:입금|송금|이체|납부|납입|반환|상환)"""
    private const val POLITE_REQUEST = """(?:주(?:세요|십시오|시길|셔야|길)|줘|달라|바랍니다|바람|부탁)"""
    private const val REQUEST_BOUNDARY = """(?=\s*(?:[.!?。！？]|$))"""
    private const val SEND_VERB = """(?:보내|돌려(?:\s*보내)?|부쳐|넣어)"""
    private const val TRANSFER_REQUEST =
        """(?:$TRANSFER_VERB\s*(?:을|를)?\s*(?:진행\s*)?(?:(?:해|하여|하)?\s*(?:$POLITE_REQUEST|세요|십시오|고\s*(?:나서|바로)|야|면\s*(?:해제|처리|승인)|(?:한\s*뒤|후)\s*(?:연락|확인|처리|승인))|(?:해|하여라|해라)$REQUEST_BOUNDARY)|$SEND_VERB\s*(?:$POLITE_REQUEST|야|세요|십시오|라$REQUEST_BOUNDARY|$REQUEST_BOUNDARY))"""
    private const val ACCOUNT_LABEL =
        """(?:(?:입금|송금|이체|수취|납부|반환|안전|가상|개인|변경된|새)\s*)?계좌\s*(?:번호)?"""
    private const val BANK_NAME =
        """(?:(?:kb\s*)?국민|신한|우리|(?:nh\s*)?농협|(?:ibk\s*)?기업|하나|(?:kdb\s*)?산업|수협|우체국|새마을금고|신협|카카오뱅크|토스뱅크|케이뱅크|카카오|토스|부산|경남|대구|광주|전북|제주|sc\s*제일|씨티)(?:\s*은행)?"""

    private val accountNumber = Regex("""(?<![\da-z_./@=+%\-])\d(?:$NUMBER_SEPARATOR\d){9,15}(?![\d/%\-]|\.\d)""")
    private val namedDestination = Regex(
        """(?:$ACCOUNT_LABEL(?:\s*(?:는|은|입니다))?|(?<![가-힣])$BANK_NAME)(?:\s*[\])>])?\s*[:=]?\s*[\[(<]?\s*$""",
    )
    private val directedTransfer = Regex(
        """^\s*[\])>]?\s*(?:계좌(?:번호)?|번)?\s*(?:으?로|에)\s*(?:만\s*)?[^.!?。！？\r\n]{0,42}?$TRANSFER_REQUEST""",
    )
    private val exclusiveDestination = Regex("""^\s*[\])>]?\s*(?:계좌(?:번호)?|번)?\s*(?:으?로|에)\s*만""")
    private val transferRequest = Regex(TRANSFER_REQUEST)
    private val nonAccountLabel = Regex(
        """(?:운송장|송장|주문|가맹점|승인|접수|문서|거래|인증|고객|연락처|전화|휴대폰|주민(?:등록)?|카드)\s*(?:번호|코드)?\s*[:=]?\s*[\[(<]?\s*$""",
    )
    private val phoneDigits = Regex("""(?:0(?:1[016789]|2|3[1-3]|4[1-4]|5[1-5]|6[1-4]|[578]0)\d{7,8}|82(?:1[016789]|2|3[1-3]|4[1-4]|5[1-5]|6[1-4]|[578]0)\d{7,8})""")
    private val identificationLayout = Regex("""(?:\d{6}[-\s]+\d{7}|\d{4}(?:[-\s]+\d{4}){3})""")

    private val replacement = Regex(
        """
            (?:계좌(?:번호)?|입금처).{0,15}(?:바뀌|바꿨|바꿔|변경(?:됐|되었|되어|합니다|됩니다|\s*안내|(?=\s*[:：])))
            |변경된\s*(?:계좌|입금처)|새\s*계좌
            |(?:기존|이전|원래).{0,10}(?:계좌|입금처).{0,24}(?:폐쇄|해지|막혔|사용하지|대신|무시)
        """.trimIndent(),
        RegexOption.COMMENTS,
    )
    private val safeAccountClaim = Regex(
        """안전\s*계좌|보호\s*계좌|(?:자금|재산|예금|잔액)\s*(?:을|를)?\s*(?:보호|보전|동결\s*해제)""",
    )
    private val authority = Regex(
        """경찰(?:청|서|관)?|검찰(?:청)?|검사입니다|법원|수사관|금융\s*감독원|금감원|국세청|세무서""",
    )
    private val investigation = Regex(
        """명의\s*도용|수사\s*(?:대상|협조|절차)|범죄.{0,8}(?:연루|사용)|(?:사건|계좌).{0,12}(?:조사|연루|압류|동결)|영장|체포|공탁금|개인\s*계좌""",
    )
    private val advanceFee = Regex(
        """
            (?:대출|채용|취업|알바|당첨|경품|환불|환급).{0,42}(?:수수료|보증료|보증금|처리비|수령비|선입금|선납|선이체)
            |(?:수수료|보증료|보증금|처리비|수령비|선입금|선납|선이체).{0,42}(?:대출|채용|취업|알바|당첨|경품|환불|환급)
        """.trimIndent(),
        setOf(RegexOption.COMMENTS, RegexOption.DOT_MATCHES_ALL),
    )
    private val loanSubstitution = Regex("""대환\s*대출.{0,55}(?:기존|종전).{0,18}(?:대출|채무|원리금).{0,10}(?:상환|정리)""", RegexOption.DOT_MATCHES_ALL)
    private val mistakenTransfer = Regex(
        """착오\s*송금|(?:잘못|실수로).{0,22}(?:보냈|송금|입금|이체)|(?:송금|입금|이체).{0,14}(?:잘못했|실수했)""",
    )
    private val family = Regex("""엄마|아빠|어머니|아버지|누나|언니|오빠|동생|아들|딸""")
    private val changedContact = Regex(
        """(?:휴대폰|핸드폰|폰|액정|번호).{0,15}(?:고장|깨졌|잃어버|바뀌|바꿨|정지)|(?:새|다른|임시)\s*(?:전화)?\s*번호로\s*연락""",
    )
    private val secrecy = Regex(
        """(?:가족|지인|주변|다른\s*(?:사람|곳|직원)|아무에게도|경찰|은행|거래처|직원).{0,16}(?:알리|말하|신고하|연락하|확인하)\s*지\s*(?:마|말)|비밀로\s*(?:해|하)|비밀\s*유지""",
    )
    private val pressure = Regex(
        """지금(?!까지)|즉시|당장|곧바로|오늘\s*(?:안에|중으로)|\d{1,3}\s*(?:분|시간)\s*(?:안에|이내|내)|(?:입금|송금|이체|납부|응답|이행)\s*(?:하지\s*않으면|안\s*하면)|미\s*(?:입금|납부|이행|응답)\s*시""",
    )
    private val consequence = Regex(
        """(?:계좌|카드|계정|거래|급여).{0,15}(?:동결|압류|정지|차단|잠금)|강제\s*(?:집행|압류)|(?:체포|구속|고소|형사\s*처벌|법적\s*조치)""",
    )
    private val customsProblem = Regex("""통관.{0,14}(?:문제|보류|중단|반송)""")
    private val exampleMarker = Regex(
        """(?:스미싱|피싱|사기|사칭|예방|교육|훈련).{0,22}(?:예시|사례)|(?:예시|사례)\s*[:：]""",
    )
    private val quotedWarning = Regex(
        """(?:라는|하라는|라고\s*하는)\s*(?:문자|안내|내용|요청|요구|문구).{0,30}(?:무시|조심|주의|신고|응하지|사기|피싱)""",
    )
    private val adviceInSameClause = Regex(
        """(?:입금|송금|이체|납부|반환|보내)(?:를|을|하|해)?\s*지\s*(?:마|말|않(?!으면|는다면|을\s*경우))|(?:입금|송금|이체|납부|반환)\s*금지""",
    )
    private val sentenceBreak = Regex("""[.!?。！？\r\n]+""")
    private val quotedText = Regex("""["“‘「『]|['”’」』]""")

    fun matches(text: String): Boolean {
        val normalized = Normalizer.normalize(UrlExtractor.removeUrls(text), Normalizer.Form.NFKC)
        return accountNumber.findAll(normalized).any { candidate ->
            val start = candidate.range.first
            val end = candidate.range.last + 1
            val before = normalized.substring((start - CONTEXT_LENGTH).coerceAtLeast(0), start)
            val after = normalized.substring(end, (end + CONTEXT_LENGTH).coerceAtMost(normalized.length))
            isRiskyTransfer(candidate.value, before, after)
        }
    }

    private fun isRiskyTransfer(number: String, before: String, after: String): Boolean {
        if (nonAccountLabel.containsMatchIn(before)) return false
        val named = namedDestination.containsMatchIn(before)
        val digits = number.filter(Char::isDigit)
        if (!named && (phoneDigits.matches(digits) || identificationLayout.matches(number))) return false

        val requestContext = transferRequestContext(named, before, after) ?: return false
        if (isQuotedExample(before) || quotedWarning.containsMatchIn(after)) return false
        val nearby = before + ' ' + after
        return replacement.containsMatchIn(nearby) ||
            safeAccountClaim.containsMatchIn(nearby) ||
            (authority.containsMatchIn(nearby) && investigation.containsMatchIn(nearby)) ||
            advanceFee.containsMatchIn(nearby) || loanSubstitution.containsMatchIn(nearby) ||
            mistakenTransfer.containsMatchIn(nearby) ||
            (family.containsMatchIn(nearby) && changedContact.containsMatchIn(nearby)) ||
            secrecy.containsMatchIn(nearby) ||
            (pressure.containsMatchIn(requestContext) && (consequence.containsMatchIn(nearby) ||
                (exclusiveDestination.containsMatchIn(after) && customsProblem.containsMatchIn(nearby))))
    }

    private fun transferRequestContext(named: Boolean, before: String, after: String): String? {
        val next = after.take(REQUEST_LENGTH).trimStart()
        val nextClause = next.substringBeforeAnySentenceBreak()
        if (adviceInSameClause.containsMatchIn(nextClause)) return null
        val precedingClauses = sentenceBreak.split(before.takeLast(REQUEST_LENGTH))
        val currentClause = precedingClauses.last() + " " + nextClause
        if (directedTransfer.containsMatchIn(nextClause) ||
            (named && transferRequest.containsMatchIn(nextClause))
        ) return currentClause
        return if (named) precedingClauses.lastOrNull(transferRequest::containsMatchIn) else null
    }

    private fun String.substringBeforeAnySentenceBreak(): String =
        sentenceBreak.find(this)?.let { substring(0, it.range.first) } ?: this

    private fun isQuotedExample(before: String): Boolean {
        val marked = exampleMarker.findAll(before).lastOrNull() ?: return false
        val suffix = before.substring(marked.range.last + 1)
        return quotedText.findAll(suffix).count() % 2 == 1 ||
            (quotedText.find(suffix) == null && !sentenceBreak.containsMatchIn(suffix.trimStart()))
    }
}
