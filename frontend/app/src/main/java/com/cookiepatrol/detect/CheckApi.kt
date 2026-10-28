package com.cookiepatrol.detect

import com.cookiepatrol.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class CheckStatus { DANGEROUS, NOT_DETECTED, UNKNOWN }

internal fun statusForResponse(responseCode: Int, dangerous: Boolean?): CheckStatus = when {
    responseCode !in 200..299 || dangerous == null -> CheckStatus.UNKNOWN
    dangerous -> CheckStatus.DANGEROUS
    else -> CheckStatus.NOT_DETECTED
}

object CheckApi {
    private val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')

    /** 서버의 Safe Browsing + AI 판정. NOT_DETECTED는 안전 보증이 아니다. */
    fun check(url: String): CheckStatus = runCatching {
        val conn = URL("$baseUrl/check").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 3000
            conn.readTimeout = 30000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(JSONObject().put("url", url).toString().toByteArray()) }

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) return CheckStatus.UNKNOWN
            val dangerous = JSONObject(conn.inputStream.bufferedReader().readText()).getBoolean("dangerous")
            statusForResponse(responseCode, dangerous)
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(CheckStatus.UNKNOWN)
}
