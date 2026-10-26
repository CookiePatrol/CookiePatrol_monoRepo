package com.cookiepatrol.detect

import com.cookiepatrol.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class CheckStatus { DANGEROUS, NOT_DETECTED, UNKNOWN }

object CheckApi {
    private val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')

    /** 피싱 DB 우선 확인 후 AI 분석. NOT_DETECTED와 UNKNOWN도 로컬 경고를 유지한다. */
    fun check(url: String): CheckStatus = runCatching {
        val conn = URL("$baseUrl/check").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 3000
            conn.readTimeout = 30000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(JSONObject().put("url", url).toString().toByteArray()) }

            if (conn.responseCode !in 200..299) {
                CheckStatus.UNKNOWN
            } else if (JSONObject(conn.inputStream.bufferedReader().readText()).getBoolean("dangerous")) {
                CheckStatus.DANGEROUS
            } else {
                CheckStatus.NOT_DETECTED
            }
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(CheckStatus.UNKNOWN)
}
