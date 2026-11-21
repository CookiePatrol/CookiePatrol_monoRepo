package com.cookiepatrol.detect

import com.cookiepatrol.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class CheckStatus { DANGEROUS, NOT_DETECTED, UNKNOWN }

data class CheckResult(val status: CheckStatus, val source: String? = null, val indicators: List<String> = emptyList(),
                       val googleCacheTtlSeconds: Double = 0.0) {
    internal val cacheLifetimeMillis: Long get() = when (source) {
        "safe_browsing" -> (googleCacheTtlSeconds.coerceIn(0.0, 300.0) * 1000).toLong()
        "legacy" -> 0L // The old endpoint cannot report the provider or the verdict's lifetime.
        else -> 5 * 60_000L
    }
    val warningReason: String get() = when (source) {
        "safe_browsing" -> "Google 위험 목록에 등록된 링크예요."
        "ai_cache" -> "최근 AI 검사에서 주소의 위험 신호를 찾았어요." +
            (AI_INDICATOR_REASONS[indicators.firstOrNull()]?.let { " $it" } ?: "")
        "ai" -> "AI가 주소에서 위험 신호를 찾았어요. " +
            (AI_INDICATOR_REASONS[indicators.firstOrNull()] ?: "사이트 방문 결과는 아니에요.")
        "legacy" -> "서버가 위험 신호를 찾았어요. 이 검사에서는 구체적인 이유를 확인할 수 없어요."
        else -> "링크 추가 검사에서 위험 신호를 찾았어요."
    }
}

internal val AI_INDICATOR_REASONS = mapOf(
    "brand_impersonation" to "공식 기관이나 서비스를 흉내 낸 구조예요.",
    "credential_lure" to "인증정보 입력으로 유도하는 구조예요.",
    "deceptive_host" to "실제 연결 대상을 혼동하게 하는 구조예요.",
    "encoded_destination" to "연결 대상을 감추는 구조예요.",
    "suspicious_download" to "의심스러운 설치 파일로 유도하는 구조예요.",
)

internal fun detailedResult(dangerous: Boolean, source: String, indicators: List<String>, googleTtl: Double = 0.0): CheckResult {
    if (!googleTtl.isFinite() || googleTtl < 0) return CheckResult(CheckStatus.UNKNOWN)
    val knownIndicators = indicators.size <= AI_INDICATOR_REASONS.size &&
        indicators.distinct().size == indicators.size && indicators.all { it in AI_INDICATOR_REASONS }
    val valid = when (source) {
        "safe_browsing" -> dangerous && indicators.isEmpty()
        "ai_cache" -> knownIndicators && (dangerous || indicators.isEmpty())
        "ai" -> knownIndicators && dangerous == indicators.isNotEmpty()
        else -> false
    }
    return if (valid) CheckResult(statusForResponse(200, dangerous), source, indicators, googleTtl)
        else CheckResult(CheckStatus.UNKNOWN)
}

internal fun statusForResponse(responseCode: Int, dangerous: Boolean?): CheckStatus = when {
    responseCode !in 200..299 || dangerous == null -> CheckStatus.UNKNOWN
    dangerous -> CheckStatus.DANGEROUS
    else -> CheckStatus.NOT_DETECTED
}

object CheckApi {
    private val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')

    /** 서버의 Safe Browsing + AI 판정. NOT_DETECTED는 안전 보증이 아니다. */
    fun check(url: String): CheckStatus = checkDetails(url).status

    fun checkDetails(url: String): CheckResult = checkAt(url, "$baseUrl/check/details", "$baseUrl/check")

    internal fun checkAt(url: String, endpoint: String, legacyEndpoint: String? = null): CheckResult = runCatching {
        val startedAt = System.nanoTime()
        val response = post(url, endpoint)
        if (legacyEndpoint != null && (response.code == 404 || response.code == 405)) {
            val legacy = post(url, legacyEndpoint)
            val status = statusForResponse(legacy.code, legacy.data?.opt("dangerous") as? Boolean)
            return CheckResult(status, "legacy".takeIf { status != CheckStatus.UNKNOWN })
        }
        val data = response.data ?: return CheckResult(CheckStatus.UNKNOWN)
        val dangerous = data.get("dangerous") as? Boolean ?: return CheckResult(CheckStatus.UNKNOWN)
        val source = data.getString("source")
        val indicators = data.getJSONArray("indicators")
        val evidence = if (source == "ai_cache") {
            if (indicators.length() != 0) return CheckResult(CheckStatus.UNKNOWN)
            if (data.has("cached_indicators")) data.getJSONArray("cached_indicators") else indicators
        } else indicators
        val result = detailedResult(dangerous, source,
            (0 until evidence.length()).map { evidence.getString(it) },
            if (data.has("google_cache_ttl_seconds")) {
                (data.get("google_cache_ttl_seconds") as? Number)?.toDouble()
                    ?: return CheckResult(CheckStatus.UNKNOWN)
            } else 0.0)
        if (result.source == "safe_browsing") result.copy(googleCacheTtlSeconds =
            (result.googleCacheTtlSeconds - (System.nanoTime() - startedAt) / 1_000_000_000.0).coerceAtLeast(0.0))
        else result
    }.getOrDefault(CheckResult(CheckStatus.UNKNOWN))

    private data class Response(val code: Int, val data: JSONObject? = null)

    private fun post(url: String, endpoint: String): Response {
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 3000
            conn.readTimeout = 25000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(JSONObject().put("url", url).toString().toByteArray(Charsets.UTF_8)) }

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) return Response(responseCode)
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(16_385)
                var length = 0
                while (length < buffer.size) {
                    val read = reader.read(buffer, length, buffer.size - length)
                    if (read < 0) break
                    length += read
                }
                require(length <= 16_384)
                String(buffer, 0, length)
            }
            return Response(responseCode, JSONObject(body))
        } finally {
            conn.disconnect()
        }
    }
}
