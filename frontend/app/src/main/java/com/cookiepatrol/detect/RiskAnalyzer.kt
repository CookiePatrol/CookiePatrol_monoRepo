package com.cookiepatrol.detect

import java.net.IDN
import java.net.URI

/** 위험 신호 하나. reason은 경고문 "이유" 줄에 그대로 쓰인다. */
class Signal(val id: String, val score: Int, val reason: String, val matches: (String) -> Boolean)

private fun any(vararg words: String): (String) -> Boolean = { text -> words.any { it in text } }

private fun discouraged(text: String, from: Int, span: Int = 16): Boolean =
    Regex("""(?:하지|주지|지\s*마|금지|않|말아|안\s*(?:돼|되|됩))""")
        .containsMatchIn(text.substring(from, (from + span).coerceAtMost(text.length)))

private fun affirmativeRequest(text: String): Boolean =
    Regex("보내|알려|불러|전달|입력").findAll(text).any {
        val tail = text.substring(it.range.last + 1).take(20)
        !Regex("""^\s*(?:(?:해|하|주)\s*)?(?:지\s*(?:마|말|않)|면\s*안|하지|주지|금지)""").containsMatchIn(tail)
    }

val SIGNALS = listOf(
    Signal("short_url", 2, "짧게 줄인 주소가 있어 연결 대상을 바로 알기 어려워요.") { Regex("""\b(bit\.ly|han\.gl|me2\.kr|url\.kr|vo\.la|t\.ly|tinyurl\.com)/""").containsMatchIn(it) },
    Signal("http", 1, "암호화되지 않은(http) 사이트예요.") { "http://" in it },
    Signal("ip_host", 3, "사이트 이름 대신 숫자 주소를 써서 연결 대상을 알아보기 어려워요.") {
        Regex("""https?://(?:\d+\.\d+\.\d+\.\d+|\[[0-9a-f:]+])""").containsMatchIn(it)
    },
    Signal("userinfo", 3, "주소에 @가 있어 실제 연결 대상을 헷갈릴 수 있어요.") {
        Regex("""https?://[^/\s?#]*@""").containsMatchIn(it)
    },
    Signal("idn", 2, "문자 조합이 다른 도메인으로 위장했을 수 있어요.") {
        Regex("""(?:^|[./])xn--[a-z0-9-]+""").containsMatchIn(it)
    },
    Signal("apk", 3, "앱 설치 파일을 뜻하는 .apk 표시가 있어요.") { Regex("""\.apk\b""").containsMatchIn(it) },
    Signal("lure_words", 2, "택배·환급·미납처럼 사기 문자에 자주 나오는 말이 있어요.", any("택배", "미납", "환급", "과태료", "정부지원", "당첨")),
    Signal("family", 2, "가족 호칭과 휴대폰·돈·인증번호 관련 말이 함께 있어요. 아는 번호로 직접 확인하세요.") { text ->
        val mentionsFamily = listOf("엄마", "아빠", "어머니", "아버지", "누나", "언니", "형", "오빠", "아들", "딸").any { it in text }
        val mentionsRisk = listOf("폰", "휴대폰", "액정", "고장", "깨졌", "인증번호", "송금", "계좌", "돈").any { it in text }
        mentionsFamily && mentionsRisk
    },
    Signal("credential", 2, "인증번호·비밀번호 관련 말과 전달·입력 요청이 함께 있어요.") { text ->
        val mentionsCredential = listOf("인증번호", "승인번호", "인증 코드", "인증코드", "보안코드", "6자리 번호", "otp", "보안카드", "비밀번호").any { it in text }
        val asksToShare = affirmativeRequest(text)
        mentionsCredential && asksToShare
    },
    Signal("transfer", 2, "송금·상품권 관련 말과 행동을 요청하는 표현이 함께 있어요.") { text ->
        val mentionsTransfer = listOf("송금", "이체", "입금", "돈 보내", "상품권").any { it in text }
        val asksToAct = listOf("해줘", "해주세요", "보내", "전달", "지금", "급하게", "만원", "코드 알려").any { it in text }
        mentionsTransfer && asksToAct
    },
    Signal("suspension", 2, "계정·카드·서비스의 정지나 제한 관련 말이 있어요.") {
        Regex(
            """(?:계정|계좌|카드|서비스|로그인).{0,15}(?:정지|차단|제한|잠금)|(?:정지|차단|제한|잠금).{0,15}(?:계정|계좌|카드|서비스|로그인)""",
            RegexOption.DOT_MATCHES_ALL,
        ).containsMatchIn(it)
    },
    Signal("identity", 3, "주민등록번호와 전체 번호·입력 관련 표현이 함께 있어요.") {
        Regex(
            """주민(?:등록)?번호.{0,30}(?:전체|모두|13자리|입력|기재|인증)|(?:전체|13자리).{0,15}주민(?:등록)?번호""",
            RegexOption.DOT_MATCHES_ALL,
        ).findAll(it).any { match -> !discouraged(it, match.range.last + 1, 32) }
    },
)

object UrlExtractor {
    private val pattern = Regex(
        """(?i)(?<![\p{L}\p{N}._@-])(?:https?://[^\s<>"']+|(?:\[[0-9a-f:.]+]|(?:[\p{L}\p{N}](?:[\p{L}\p{N}-]*[\p{L}\p{N}])?\.)+[\p{L}\p{N}-]{2,63}|(?:\d{1,3}\.){3}\d{1,3})(?::\d{1,5})?(?:[/?#][^\s<>"']*)?)""",
    )
    private val trailingPunctuation = setOf('.', ',', '!', '?', ';', ':', '\'', '"', '…', '。')

    fun extract(text: String): List<String> = pattern.findAll(text)
        .mapNotNull { normalize(it.value) }
        .distinct()
        .toList()

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

        val normalized = if (candidate.startsWith("http://", ignoreCase = true) ||
            candidate.startsWith("https://", ignoreCase = true)
        ) candidate else "https://$candidate"
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
        val portSuffix = if (hostPort.port == -1) "" else ":${hostPort.port}"
        val suffix = buildString {
            append(uri.rawPath.orEmpty())
            uri.rawQuery?.let { append('?').append(it) }
            uri.rawFragment?.let { append('#').append(it) }
        }
        return runCatching { URI.create("$scheme://$host$portSuffix$suffix").toASCIIString() }.getOrNull()
    }

    private data class HostPort(val host: String, val port: Int)

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
