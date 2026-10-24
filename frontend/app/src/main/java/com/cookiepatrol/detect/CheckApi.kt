package com.cookiepatrol.detect

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class CheckStatus { DANGEROUS, NOT_LISTED, UNKNOWN }

object CheckApi {
    // 에뮬레이터에서 본 PC의 localhost. 실기기 데모면 PC의 LAN IP로 바꿀 것.
    private const val BASE_URL = "http://10.0.2.2:8000"

    /** Web Risk 목록 판정. NOT_LISTED는 안전 보증이 아니며 UNKNOWN과 함께 로컬 경고를 유지한다. */
    fun check(url: String): CheckStatus = runCatching {
        val conn = URL("$BASE_URL/check").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(JSONObject().put("url", url).toString().toByteArray()) }

            if (conn.responseCode !in 200..299) {
                CheckStatus.UNKNOWN
            } else if (JSONObject(conn.inputStream.bufferedReader().readText()).getBoolean("dangerous")) {
                CheckStatus.DANGEROUS
            } else {
                CheckStatus.NOT_LISTED
            }
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(CheckStatus.UNKNOWN)
}
