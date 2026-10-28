package com.cookiepatrol.detect

import android.content.Context
import com.cookiepatrol.warn.Warning
import java.net.IDN
import java.net.URI
import java.util.LinkedHashMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** 위험 신호 하나. reason은 경고문 "이유" 줄에 그대로 쓰인다. */
class Signal(val score: Int, val reason: String, val matches: (String) -> Boolean)

private fun any(vararg words: String): (String) -> Boolean = { text -> words.any { it in text } }

val SIGNALS = listOf(
    Signal(2, "진짜 주소를 숨기는 단축 주소예요.") { Regex("""\b(bit\.ly|han\.gl|me2\.kr|url\.kr|vo\.la|t\.ly|tinyurl\.com)/""").containsMatchIn(it) },
    Signal(1, "암호화되지 않은(http) 사이트예요.") { "http://" in it },
    Signal(3, "정상 사이트는 숫자 주소(IP)를 거의 쓰지 않아요.") {
        Regex("""https?://(?:\d+\.\d+\.\d+\.\d+|\[[0-9a-f:]+])""").containsMatchIn(it)
    },
    Signal(3, "주소 앞부분이 실제 사이트 주소를 숨기려 해요.") {
        Regex("""https?://[^/\s?#]*@""").containsMatchIn(it)
    },
    Signal(2, "문자 조합이 다른 도메인으로 위장했을 수 있어요.") {
        Regex("""(?:^|[./])xn--[a-z0-9-]+""").containsMatchIn(it)
    },
    Signal(3, "앱 설치 파일(APK)을 받게 하는 링크예요.") { Regex("""\.apk\b""").containsMatchIn(it) },
    Signal(2, "택배·환급·미납처럼 사기 문자에 자주 나오는 말이 있어요.", any("택배", "미납", "환급", "과태료", "정부지원", "당첨")),
    Signal(2, "가족인 척 급하게 돈이나 인증번호를 요구해요.") { text ->
        val mentionsFamily = listOf("엄마", "아빠", "어머니", "아버지", "누나", "언니", "형", "오빠", "아들", "딸").any { it in text }
        val mentionsRisk = listOf("폰", "휴대폰", "액정", "고장", "깨졌", "인증번호", "송금", "계좌", "돈").any { it in text }
        mentionsFamily && mentionsRisk
    },
    Signal(2, "인증번호나 비밀번호를 다른 사람에게 알려달라고 해요.") { text ->
        val mentionsCredential = listOf("인증번호", "승인번호", "인증 코드", "인증코드", "보안코드", "6자리 번호", "otp", "보안카드", "비밀번호").any { it in text }
        val asksToShare = listOf("보내", "알려", "불러", "전달", "입력").any { it in text }
        mentionsCredential && asksToShare
    },
    Signal(2, "급하게 돈을 보내거나 계좌로 이체하라고 해요.") { text ->
        val mentionsTransfer = listOf("송금", "이체", "입금", "돈 보내", "상품권").any { it in text }
        val asksToAct = listOf("해줘", "해주세요", "보내", "전달", "지금", "급하게", "만원", "코드 알려").any { it in text }
        mentionsTransfer && asksToAct
    },
    Signal(2, "계정이나 서비스가 곧 정지된다고 재촉해요.") {
        Regex(
            """(?:계정|계좌|카드|서비스|로그인).{0,15}(?:정지|차단|제한|잠금)|(?:정지|차단|제한|잠금).{0,15}(?:계정|계좌|카드|서비스|로그인)""",
            RegexOption.DOT_MATCHES_ALL,
        ).containsMatchIn(it)
    },
    Signal(3, "주민등록번호 전체를 입력하라고 해요.") {
        Regex(
            """주민(?:등록)?번호.{0,30}(?:전체|모두|13자리|입력|기재|인증)|(?:전체|13자리).{0,15}주민(?:등록)?번호""",
            RegexOption.DOT_MATCHES_ALL,
        ).containsMatchIn(it)
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

    private fun normalize(raw: String): String? {
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

object Detector {
    private const val WARNING_THRESHOLD = 3
    private const val LOOKUP_THRESHOLD = 2
    private const val WARNING_COOLDOWN_MS = 30_000L
    private const val URL_CHECK_TTL_MS = 5 * 60_000L
    private const val MAX_CHECKED_URLS = 500
    private const val MAX_URLS_PER_INSPECTION = 10

    private val checkedAt = LinkedHashMap<String, Long>()
    private val recentWarnings = HashMap<String, Long>()
    private val reputationExecutor = ThreadPoolExecutor(
        2,
        2,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(64),
        { task -> Thread(task, "cookiepatrol-url-check").apply { isDaemon = true } },
    )

    internal fun isLookupCandidate(score: Int): Boolean = score >= LOOKUP_THRESHOLD

    /** 로컬 위험 신호는 Safe Browsing 결과와 무관하게 경고하고, 알려진 위험 URL이면 경고를 강화한다. */
    fun inspect(context: Context, rawText: String) {
        val text = rawText.lowercase()
        val urls = UrlExtractor.extract(rawText)
        val signalText = buildString {
            append(text)
            urls.forEach { append('\n').append(it.lowercase()) }
        }
        val hits = SIGNALS.filter { it.matches(signalText) }
        val riskScore = hits.sumOf { it.score }
        if (riskScore >= WARNING_THRESHOLD) {
            showWarningOnce(context, hits.maxBy { it.score }.reason)
        }
        if (!isLookupCandidate(riskScore)) return

        val urlsToCheck = urls.take(MAX_URLS_PER_INSPECTION).filter { shouldCheckUrl(it) }
        if (urlsToCheck.isEmpty()) return
        try {
            reputationExecutor.execute {
                if (urlsToCheck.any { CheckApi.check(it) == CheckStatus.DANGEROUS }) {
                    showWarningOnce(context, "위험한 사이트로 확인된 링크가 있어요.")
                }
            }
        } catch (_: RejectedExecutionException) {
            forgetCheckedUrls(urlsToCheck)
            showWarningOnce(
                context,
                "의심 링크가 몰려 안전 확인을 마치지 못했어요.",
            )
        }
    }

    @Synchronized
    private fun forgetCheckedUrls(urls: List<String>) {
        urls.forEach { checkedAt.remove(it) }
    }

    @Synchronized
    private fun shouldCheckUrl(url: String): Boolean {
        val now = System.currentTimeMillis()
        val lastChecked = checkedAt[url]
        if (lastChecked != null && now - lastChecked < URL_CHECK_TTL_MS) return false
        checkedAt.remove(url)
        checkedAt[url] = now
        while (checkedAt.size > MAX_CHECKED_URLS) {
            checkedAt.remove(checkedAt.entries.first().key)
        }
        return true
    }

    private fun showWarningOnce(context: Context, reason: String) {
        val now = System.currentTimeMillis()
        val shouldShow = synchronized(recentWarnings) {
            val previous = recentWarnings[reason]
            if (previous == null || now - previous >= WARNING_COOLDOWN_MS) {
                recentWarnings[reason] = now
                true
            } else {
                false
            }
        }
        if (shouldShow) Warning.show(context, reason)
    }
}
