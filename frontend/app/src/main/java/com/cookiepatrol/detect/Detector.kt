package com.cookiepatrol.detect

import android.content.Context
import com.cookiepatrol.warn.Warning
import kotlin.concurrent.thread

/** 위험 신호 하나. reason은 경고문 "이유" 줄에 그대로 쓰인다. */
class Signal(val score: Int, val reason: String, val matches: (String) -> Boolean)

private fun any(vararg words: String): (String) -> Boolean = { text -> words.any { it in text } }

val SIGNALS = listOf(
    Signal(2, "진짜 주소를 숨기는 단축 주소예요.") { Regex("""\b(bit\.ly|han\.gl|me2\.kr|url\.kr|vo\.la|t\.ly|tinyurl\.com)/""").containsMatchIn(it) },
    Signal(1, "암호화되지 않은(http) 사이트예요.") { "http://" in it },
    Signal(3, "정상 사이트는 숫자 주소(IP)를 거의 쓰지 않아요.") { Regex("""https?://\d+\.\d+\.\d+\.\d+""").containsMatchIn(it) },
    Signal(3, "앱 설치 파일(APK)을 받게 하는 링크예요.") { Regex("""\.apk\b""").containsMatchIn(it) },
    Signal(2, "택배·환급·미납처럼 사기 문자에 자주 나오는 말이 있어요.", any("택배", "미납", "환급", "과태료", "정부지원", "당첨")),
    Signal(2, "가족인 척 급하게 돈이나 인증번호를 요구해요.", any("엄마", "아빠", "폰이 고장", "액정", "인증번호")),
    Signal(3, "주민등록번호 전체를 입력하라고 해요.", any("주민등록번호", "주민번호")),
)

private val URL = Regex("""(?:https?://)?(?:[a-z0-9-]+\.)+[a-z]{2,}(?:/\S*)?""")

object Detector {
    private const val THRESHOLD = 3

    // ponytail: 앱이 켜져 있는 동안 계속 커짐. 메모리가 문제되면 LRU로 교체
    private val checked = HashSet<String>()

    /** 알림·화면 글자 검사: 점수 합산 → 의심이면 백엔드(피싱 DB) 확인 → 위험이면 경고. 막지는 않는다. */
    fun inspect(context: Context, rawText: String) {
        val text = rawText.lowercase()
        val urls = URL.findAll(text).map { it.value }.filter { checked.add(it) }.toList()
        if (urls.isEmpty()) return
        val hits = SIGNALS.filter { it.matches(text) }
        if (hits.sumOf { it.score } < THRESHOLD) return
        thread {
            if (urls.any(CheckApi::isDangerous)) Warning.show(context, hits.maxBy { it.score }.reason)
        }
    }
}
