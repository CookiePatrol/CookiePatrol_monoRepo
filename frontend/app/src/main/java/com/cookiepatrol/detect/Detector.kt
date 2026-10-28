package com.cookiepatrol.detect

import android.content.Context
import com.cookiepatrol.warn.Warning
import java.net.URI
import java.util.LinkedHashMap
import kotlin.concurrent.thread

/** 위험 신호 하나. reason은 경고문 "이유" 줄에 그대로 쓰인다. */
class Signal(val score: Int, val reason: String, val matches: (String) -> Boolean)

private fun any(vararg words: String): (String) -> Boolean = { text -> words.any { it in text } }

val SIGNALS = listOf(
    Signal(2, "진짜 주소를 숨기는 단축 주소예요.") { Regex("""\b(bit\.ly|han\.gl|me2\.kr|url\.kr|vo\.la|t\.ly|tinyurl\.com)/""").containsMatchIn(it) },
    Signal(1, "암호화되지 않은(http) 사이트예요.") { "http://" in it },
    Signal(3, "정상 사이트는 숫자 주소(IP)를 거의 쓰지 않아요.") {
        Regex("""https?://(?:\d+\.\d+\.\d+\.\d+|\[[0-9a-f:]+])""").containsMatchIn(it)
    },
    Signal(3, "앱 설치 파일(APK)을 받게 하는 링크예요.") { Regex("""\.apk\b""").containsMatchIn(it) },
    Signal(2, "택배·환급·미납처럼 사기 문자에 자주 나오는 말이 있어요.", any("택배", "미납", "환급", "과태료", "정부지원", "당첨")),
    Signal(2, "가족인 척 급하게 돈이나 인증번호를 요구해요.") { text ->
        val mentionsFamily = listOf("엄마", "아빠", "어머니", "아버지").any { it in text }
        val mentionsRisk = listOf("폰", "휴대폰", "액정", "고장", "깨졌", "인증번호", "송금", "계좌", "돈").any { it in text }
        mentionsFamily && mentionsRisk
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
        """(?i)(?<![a-z0-9._-])(?:https?://)?(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9-]{2,63}|(?:\d{1,3}\.){3}\d{1,3}|https?://\[[0-9a-f:.]+])(?::\d{1,5})?(?:[/?#][^\s<>"']*)?""",
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
        if (candidate.isBlank()) return null

        val normalized = if (candidate.startsWith("http://", ignoreCase = true) ||
            candidate.startsWith("https://", ignoreCase = true)
        ) candidate else "https://$candidate"
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
        val port = uri.port
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank() || port > 65535) {
            return null
        }
        return normalized
    }
}

object Detector {
    private const val THRESHOLD = 3
    private const val WARNING_COOLDOWN_MS = 30_000L
    private const val URL_CHECK_TTL_MS = 5 * 60_000L
    private const val MAX_CHECKED_URLS = 500

    private val checkedAt = LinkedHashMap<String, Long>()
    private val recentWarnings = HashMap<String, Long>()

    /** 로컬 위험 신호는 Safe Browsing 결과와 무관하게 경고하고, 알려진 위험 URL이면 경고를 강화한다. */
    fun inspect(context: Context, rawText: String) {
        val text = rawText.lowercase()
        val hits = SIGNALS.filter { it.matches(text) }
        if (hits.sumOf { it.score } < THRESHOLD) return
        val reason = hits.maxBy { it.score }.reason
        showWarningOnce(context, reason)

        val urls = UrlExtractor.extract(rawText).filter { shouldCheckUrl(it) }
        if (urls.isEmpty()) return
        thread {
            if (urls.any { CheckApi.check(it) == CheckStatus.DANGEROUS }) {
                showWarningOnce(context, "위험한 사이트로 확인된 링크가 있어요.")
            }
        }
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
