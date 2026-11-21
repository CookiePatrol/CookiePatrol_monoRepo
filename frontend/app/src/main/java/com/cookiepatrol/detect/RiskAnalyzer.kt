package com.cookiepatrol.detect

import java.net.IDN
import java.net.URI
import java.text.Normalizer

internal const val PHONE_CALLBACK_SIGNAL_ID = "phone_callback"

internal enum class SignalCategory { LINK, PHONE, BANK_ACCOUNT, MESSAGE }

/** 위험 신호 하나. reason은 경고문 "이유" 줄에 그대로 쓰인다. */
class Signal internal constructor(
    val id: String,
    val title: String,
    val score: Int,
    val reason: String,
    internal val category: SignalCategory = SignalCategory.MESSAGE,
    val matches: (String) -> Boolean,
)

private fun any(vararg words: String): (String) -> Boolean = { text -> words.any { it in text } }

private fun discouraged(text: String, from: Int, span: Int = 16): Boolean =
    Regex("""(?:하지|주지|지\s*마|금지|않|말아|안\s*(?:돼|되|됩))""")
        .containsMatchIn(text.substring(from, (from + span).coerceAtMost(text.length)))

private fun affirmativeRequest(text: String): Boolean =
    Regex("보내|알려|불러|전달|입력").findAll(text).any {
        val tail = text.substring(it.range.last + 1).take(20)
        !Regex("""^\s*(?:(?:해|하|주)\s*)?(?:지\s*(?:마|말|않)|면\s*안|하지|주지|금지)""").containsMatchIn(tail)
    }

private val impersonatedDomains = setOf(
    "epost.go.kr", "google.com", "gov.kr", "kakao.com", "naver.com", "paypal.com",
)

private val familyBrother = Regex("형(?!사)")

private fun embedsOfficialDomain(text: String): Boolean {
    if (impersonatedDomains.none { "$it." in text }) return false
    return UrlExtractor.extract(text).any { url ->
        val host = URI.create(url).host?.trimEnd('.') ?: return@any false
        impersonatedDomains.any { domain ->
            !host.endsWith(".$domain") && ".$domain." in ".$host"
        }
    }
}

val SIGNALS = listOf(
    Signal("short_url", "단축 주소", 2, "짧게 줄인 주소가 있어 연결 대상을 바로 알기 어려워요.", SignalCategory.LINK) { Regex("""\b(bit\.ly|han\.gl|me2\.kr|url\.kr|vo\.la|t\.ly|tinyurl\.com)/""").containsMatchIn(it) },
    Signal("http", "HTTP 주소", 1, "암호화되지 않은(http) 사이트예요.", SignalCategory.LINK) { "http://" in it },
    Signal("ip_host", "숫자 IP 주소", 3, "사이트 이름 대신 숫자 주소를 써서 연결 대상을 알아보기 어려워요.", SignalCategory.LINK) {
        Regex("""https?://(?:\d+\.\d+\.\d+\.\d+|\[[0-9a-f:]+])""").containsMatchIn(it)
    },
    Signal("userinfo", "@가 들어간 주소", 3, "주소에 @가 있어 실제 연결 대상을 헷갈릴 수 있어요.", SignalCategory.LINK) {
        Regex("""https?://[^/\s?#]*@""").containsMatchIn(it)
    },
    Signal("idn", "변환된 문자 도메인", 2, "문자 조합이 다른 도메인으로 위장했을 수 있어요.", SignalCategory.LINK) {
        Regex("""(?:^|[./])xn--[a-z0-9-]+""").containsMatchIn(it)
    },
    Signal("apk", "APK 설치 파일", 3, "앱 설치 파일을 뜻하는 .apk 표시가 있어요.", SignalCategory.LINK) { Regex("""\.apk\b""").containsMatchIn(it) },
    Signal("lure_words", "택배·환급 등 유도 문구", 2, "택배·환급·미납처럼 사기 문자에 자주 나오는 말이 있어요.",
        matches = any("택배", "미납", "환급", "과태료", "정부지원", "당첨")),
    Signal("family", "가족 사칭 의심", 2, "가족 호칭과 휴대폰·돈·인증번호 관련 말이 함께 있어요. 아는 번호로 직접 확인하세요.") { text ->
        val mentionsFamily = familyBrother.containsMatchIn(text) ||
            listOf("엄마", "아빠", "어머니", "아버지", "누나", "언니", "오빠", "아들", "딸").any { it in text }
        val mentionsRisk = listOf("폰", "휴대폰", "액정", "고장", "깨졌", "인증번호", "송금", "계좌", "돈").any { it in text }
        mentionsFamily && mentionsRisk
    },
    Signal("credential", "인증정보 요청", 2, "인증번호·비밀번호 관련 말과 전달·입력 요청이 함께 있어요.") { text ->
        val mentionsCredential = listOf("인증번호", "승인번호", "인증 코드", "인증코드", "보안코드", "6자리 번호", "otp", "보안카드", "비밀번호").any { it in text }
        val asksToShare = affirmativeRequest(text)
        mentionsCredential && asksToShare
    },
    Signal("transfer", "송금·상품권 요청", 2, "송금·상품권 관련 말과 행동을 요청하는 표현이 함께 있어요.") { text ->
        val mentionsTransfer = listOf("송금", "이체", "입금", "돈 보내", "상품권").any { it in text }
        val asksToAct = listOf("해줘", "해주세요", "보내", "전달", "지금", "급하게", "만원", "코드 알려").any { it in text }
        mentionsTransfer && asksToAct
    },
    Signal("suspension", "계정·카드 정지 문구", 2, "계정·카드·서비스의 정지나 제한 관련 말이 있어요.") {
        Regex(
            """(?:계정|계좌|카드|서비스|로그인).{0,15}(?:정지|차단|제한|잠금)|(?:정지|차단|제한|잠금).{0,15}(?:계정|계좌|카드|서비스|로그인)""",
            RegexOption.DOT_MATCHES_ALL,
        ).containsMatchIn(it)
    },
    Signal("identity", "주민등록번호 요청", 3, "주민등록번호와 전체 번호·입력 관련 표현이 함께 있어요.") {
        Regex(
            """주민(?:등록)?번호.{0,30}(?:전체|모두|13자리|입력|기재|인증)|(?:전체|13자리).{0,15}주민(?:등록)?번호""",
            RegexOption.DOT_MATCHES_ALL,
        ).findAll(it).any { match -> !discouraged(it, match.range.last + 1, 32) }
    },
    Signal("embedded_official_domain", "공식 도메인 사칭", 3, "주소에 공식 도메인이 들어 있지만 실제로 연결되는 도메인이 달라요.", SignalCategory.LINK, ::embedsOfficialDomain),
    Signal(
        PHONE_CALLBACK_SIGNAL_ID, "의심스러운 전화 유도", 3,
        "결제·계좌 문제나 수사를 내세우거나 민감정보를 요구하면서, 문자 속 번호로 직접 전화하도록 유도해요.",
        SignalCategory.PHONE,
        PhoneCallbackRisk::matches,
    ),
    Signal(
        BANK_TRANSFER_SIGNAL_ID, "의심스러운 계좌 송금 유도", 3,
        "문자에 적힌 계좌로 돈을 보내라고 하면서 계좌 변경, 사칭, 수수료 요구 같은 위험 신호가 보여요.",
        SignalCategory.BANK_ACCOUNT,
        BankTransferRisk::matches,
    ),
)

private object PhoneCallbackRisk {
    private const val CONTEXT_LENGTH = 160
    private const val CONTACT_LENGTH = 48
    private const val SPACING = "[\\p{Zs}\\t\\p{Pd}\\u2212]*"
    private const val AREA = "(?:1[016789]|2|3[1-3]|4[1-4]|5[1-5]|6[1-4]|[578]0)"
    private val number = Regex(
        """(?<![\da-z_./@=+\-])(?:(?:\(0$AREA\)|(?:0|\+82$SPACING(?:\(0\)$SPACING)?)$AREA)$SPACING\d{3,4}|1[568]\d{2})$SPACING\d{4}(?![\d/])""",
    )
    private val contactLabel = Regex(
        """(?:(?:전\s*화|연락|문의|통화|상담)\s*(?:번호|처|원|센터|전화|(?:해\s*)?주(?:세요|십시오)|하세요|바랍니다|부탁드립니다)?|고객\s*(?:센터|지원)|담당자|전용\s*번호|콜백|ars|tel)\s*[:：=.\[<]?\s*$""",
    )
    private val callAfterNumber = Regex(
        """^\s*[\])>]?\s*(?:번\s*)?(?:(?:으?로|에)\s*)?(?:만\s*)?(?:(?:지금|즉시|당장|바로|곧바로|직접|꼭|반드시|신속히)\s*)*(?:전\s*화|통화|연락|문의|상담|신고|연결|콜백|걸어)""",
    )
    private val callBeforeNumber = Regex(
        """(?:전\s*화|통화|연락|상담)\s*(?:(?:하|해)\s*)?(?:주(?:세요|십시오|시길)|세요|십시오|바랍니다|부탁드립니다)\s*[.!。:：]?\s*$""",
    )
    private val prohibition = Regex(
        """^\s*(?:(?:을|를)\s*)?(?:(?:하|걸|해|주|넣)\s*)?(?:지\s*(?:마|말|않)|면\s*안|금지|하지|주지)""",
    )
    private val example = Regex(
        """(?:스미싱|피싱|사기|사칭|예방|교육|훈련).{0,22}(?:예시|사례)|(?:예시|사례)\s*(?:[:：]|문자|입니다)|\[(?:보안\s*교육|피싱\s*훈련)]""",
    )
    private val warningAboutTheCall = Regex(
        """(?:전화|연락|통화|문의|상담)\s*(?:하라고|하라는|하라며).{0,32}(?:사기|사칭|피싱|주의|조심)|(?:이|해당)\s*번호로\s*(?:전화|연락|통화|문의|상담)\s*(?:하지\s*(?:마|말|않)|금지)""",
    )
    private val independentNumber = Regex(
        """(?:공식\s*(?:앱|홈페이지|웹사이트|누리집)|카드\s*(?:뒷면|뒤)|기존에\s*저장한).{0,24}(?:확인한|직접\s*찾은|기재된|적혀\s*있는|적힌)\s*(?:공식\s*)?(?:고객센터|번호)\s*[:：]?\s*$""",
    )
    private val pressure = Regex(
        """지금(?!까지)|즉시|당장|곧바로|신속히|오늘\s*(?:안에|중(?:으로)?)|\d{1,3}\s*(?:분|시간)\s*(?:내|안에|이내)|(?:연락|응답|처리|확인)하지\s*않으면|미\s*(?:응답|연락|이행|처리)\s*시""",
    )
    private val exclusiveChannel = Regex(
        """(?:이|해당|아래|안내된|지정된|문자\s*속)\s*번호로만|(?:다른\s*(?:번호|곳)|가족에게|주변에|공식\s*앱).{0,18}(?:연락|신고|알리|문의|확인).{0,5}(?:하지\s*마|지\s*마|금지|말고)""",
    )
    private val onlyThisNumber = Regex("""^\s*[\])>]?\s*(?:번\s*)?으?로\s*만""")
    private val problemClaim = Regex(
        """
            (?:계정|계좌|카드|서비스|로그인|뱅킹).{0,15}(?:정지|차단|제한|잠금|동결|압류)
            |(?:정지|차단|잠금)된\s*(?:계정|계좌|카드)
            |(?:미승인|미확인|의심|본인(?:이)?\s*아닌|모르는|해외|무단|승인한\s*적\s*없는).{0,14}(?:결제|승인|출금|이체|대출)
            |(?:결제|승인|출금|이체|대출).{0,22}(?:승인|시도|완료|접수|취소|도용|피해|본인(?:이)?\s*아니)
            |(?:유료\s*구독|정기\s*결제|자동\s*결제).{0,24}(?:결제|요금|청구)
            |(?:결제|환불|환급|미납|과태료|연체).{0,14}(?:필수|접수|거절|대상|누락|가산금|미처리)
            |(?:미납|과태료|연체).{0,16}(?:불이익|압류|가산금|제한|체납)
            |(?:경찰청|경찰서|검찰청|검찰|법원|수사관|금융감독원|국세청|세무서).{0,26}(?:사건|수사|압류|소환|출석|명의|체포|영장)
            |사건\s*접수|수사\s*대상|명의\s*(?:도용|대출)|(?:구속|체포)\s*영장|범죄.{0,8}연[루류]|대포\s*통장
            |(?:택배|배송|통관).{0,16}(?:보류|반송|실패|주소\s*(?:오류|불일치)|통관료)
        """.trimIndent(),
        RegexOption.COMMENTS,
    )
    private val sensitiveRequest = Regex(
        """
            (?:인증\s*(?:번호|코드)|otp|비밀번호|카드\s*(?:번호|뒷자리)|주민(?:등록)?번호|보안카드|상품권\s*코드)
            .{0,24}?(?:알려|말해|불러|전달|입력|보내|제출|공유|제공)
            |(?:송금|이체|입금)\s*(?:(?:을|를)\s*)?(?=(?:해|하|주|줘|달라|바랍니다|부탁))
        """.trimIndent(),
        RegexOption.COMMENTS,
    )
    private val requestEnding = Regex(
        """^\s*(?:(?:해|하)\s*)?(?:주(?:세요|십시오|시기|시길|셔야)|줘|세요|십시오|달라|바랍니다|부탁|야|$)""",
    )
    private val clauseBreak = Regex("""[.!?。！？\r\n]+""")
    private val callContinuation = Regex("""^\s*(?:통화\s*(?:중|후)|전화\s*(?:후|한\s*뒤)|상담원에게)""")
    private val investigator = Regex(
        """수사관|형사(?=님|의|에게|가|입니다|\s|[:：]|$)|경찰(?:청|서|관)|검찰(?:청)?|금융\s*감독원|금감원""",
    )
    private val criminalAccountClaim = Regex(
        """대포\s*통장|범죄.{0,8}연[루류]|(?:계좌|통장).{0,24}(?:동결|압류|막혔|막혀|지급\s*정지)""",
    )
    private val promisesRelease = Regex("""(?:풀|해제하|해결하)\s*(?:려면|시려면|어야)|정지\s*(?:해제|해소)\s*(?:를\s*)?위해""")

    fun matches(text: String): Boolean {
        val normalized = Normalizer.normalize(UrlExtractor.removeUrls(text), Normalizer.Form.NFKC)
        return number.findAll(normalized).any { candidate ->
            val before = normalized.substring((candidate.range.first - CONTEXT_LENGTH).coerceAtLeast(0), candidate.range.first)
            val end = candidate.range.last + 1
            val after = normalized.substring(end, (end + CONTEXT_LENGTH).coerceAtMost(normalized.length))
            isRiskyCallback(before, after)
        }
    }

    private fun isRiskyCallback(before: String, after: String): Boolean {
        val nextContact = after.take(CONTACT_LENGTH)
        val call = callAfterNumber.find(nextContact)
        val previousContact = before.takeLast(CONTACT_LENGTH)
        if (call == null && !contactLabel.containsMatchIn(previousContact) &&
            !callBeforeNumber.containsMatchIn(previousContact)
        ) return false
        if (call != null && prohibition.containsMatchIn(nextContact.substring(call.range.last + 1))) return false

        if (isEducationalExample(before) || warningAboutTheCall.containsMatchIn(nextContact)) return false

        val beforeClauses = clauseBreak.split(before.trimEnd())
        val afterClauses = clauseBreak.split(after.trimStart())
        val callClause = beforeClauses.last() + " " + afterClauses.first()
        val followingCall = afterClauses.getOrNull(1)?.takeIf(callContinuation::containsMatchIn)
        val asksForSensitiveAction = listOfNotNull(callClause, followingCall).any { clause ->
            sensitiveRequest.findAll(clause).any { request ->
                requestEnding.containsMatchIn(clause.substring(request.range.last + 1))
            }
        }
        if (asksForSensitiveAction) return true
        if (independentNumber.containsMatchIn(previousContact)) return false

        val clauses = beforeClauses + afterClauses
        val identifiedCall = if (call == null) {
            beforeClauses.dropLast(1).lastOrNull { it.isNotBlank() }.orEmpty() + " " + callClause
        } else {
            callClause
        }
        val pressuredCallback = clauses.any(problemClaim::containsMatchIn) &&
            (pressure.containsMatchIn(identifiedCall) || exclusiveChannel.containsMatchIn(identifiedCall) ||
                onlyThisNumber.containsMatchIn(nextContact))
        val investigativeCallback = clauses.any(criminalAccountClaim::containsMatchIn) &&
            investigator.containsMatchIn(identifiedCall) && promisesRelease.containsMatchIn(identifiedCall)
        return pressuredCallback || investigativeCallback
    }

    private fun isEducationalExample(before: String): Boolean {
        val marker = example.findAll(before).lastOrNull() ?: return false
        val following = before.substring(marker.range.last + 1).trimStart { it.isWhitespace() || it == ':' }
        val quoteEnd = when (val quoteStart = following.firstOrNull()) {
            '‘' -> '’'
            '“' -> '”'
            '\'', '"' -> quoteStart
            else -> null
        }
        return if (quoteEnd != null) quoteEnd !in following.drop(1) else !clauseBreak.containsMatchIn(following)
    }
}

object UrlExtractor {
    private val pattern = Regex(
        """(?i)(?<![\p{L}\p{N}._@-])(?:https?://[^\s<>"']+|(?:\[[0-9a-f:.]+]|(?:[\p{L}\p{N}](?:[\p{L}\p{N}-]*[\p{L}\p{N}])?\.)+[\p{L}\p{N}-]{2,63}|(?:\d{1,3}\.){3}\d{1,3})(?::\d{1,5})?(?:[/?#][^\s<>"']*)?)""",
    )
    private val trailingPunctuation = setOf('.', ',', '!', '?', ';', ':', '\'', '"', '…', '。')

    fun extract(text: String): List<String> = pattern.findAll(text)
        .mapNotNull { normalize(it.value) }
        .distinct()
        .toList()

    internal fun removeUrls(text: String): String = pattern.replace(text) { match ->
        if (normalize(match.value) != null) " " else match.value
    }

    internal fun normalize(raw: String): String? {
        var candidate = raw
        while (candidate.lastOrNull()?.let { it in trailingPunctuation } == true) {
            candidate = candidate.dropLast(1)
        }
        while (candidate.endsWith(')') && candidate.count { it == ')' } > candidate.count { it == '(' }) {
            candidate = candidate.dropLast(1)
        }
        while (candidate.endsWith(']') && candidate.count { it == ']' } > candidate.count { it == '[' }) {
            candidate = candidate.dropLast(1)
        }
        while (candidate.endsWith('}') && candidate.count { it == '}' } > candidate.count { it == '{' }) {
            candidate = candidate.dropLast(1)
        }
        if (candidate.isBlank() || candidate.length > MAX_URL_LENGTH) return null

        val explicitHttp = candidate.startsWith("http://", ignoreCase = true) ||
            candidate.startsWith("https://", ignoreCase = true)
        val normalized = if (explicitHttp) candidate else "https://$candidate"
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme !in setOf("http", "https")) return null
        val authority = uri.rawAuthority ?: return null

        // Drop misleading user-info (trusted.example@real-host) and check the actual host.
        val hostPort = parseAuthority(authority.substringAfterLast('@')) ?: return null
        val host = if (hostPort.host.startsWith("[")) {
            hostPort.host.lowercase()
        } else {
            runCatching { IDN.toASCII(hostPort.host, IDN.USE_STD3_ASCII_RULES).lowercase() }
                .getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        }
        if (!explicitHttp && !isBareWebHost(host)) return null
        val portSuffix = if (hostPort.port == -1) "" else ":${hostPort.port}"
        val suffix = buildString {
            append(uri.rawPath.orEmpty())
            uri.rawQuery?.let { append('?').append(it) }
            uri.rawFragment?.let { append('#').append(it) }
        }
        return runCatching { URI.create("$scheme://$host$portSuffix$suffix").toASCIIString() }.getOrNull()
    }

    private data class HostPort(val host: String, val port: Int)

    private fun isBareWebHost(host: String): Boolean {
        if (host.startsWith("[")) return true
        val labels = host.trimEnd('.').split('.')
        if (labels.size < 2) return false
        if (labels.last().any { it in 'a'..'z' }) return true
        return labels.size == 4 && labels.all { it.toIntOrNull()?.let { octet -> octet in 0..255 } == true }
    }

    private fun parseAuthority(authority: String): HostPort? {
        if (authority.startsWith("[")) {
            val closingBracket = authority.indexOf(']')
            if (closingBracket <= 1) return null
            val host = authority.substring(0, closingBracket + 1)
            val portPart = authority.substring(closingBracket + 1)
            val port = when {
                portPart.isEmpty() -> -1
                portPart.startsWith(":") -> portPart.drop(1).toIntOrNull() ?: return null
                else -> return null
            }
            return if (port in -1..65535) HostPort(host, port) else null
        }

        if (authority.count { it == ':' } > 1) return null
        val separator = authority.lastIndexOf(':')
        val host = if (separator < 0) authority else authority.substring(0, separator)
        val port = if (separator < 0) -1 else authority.substring(separator + 1).toIntOrNull() ?: return null
        if (host.isBlank() || port !in -1..65535) return null
        return HostPort(host, port)
    }

    private const val MAX_URL_LENGTH = 2048
}
