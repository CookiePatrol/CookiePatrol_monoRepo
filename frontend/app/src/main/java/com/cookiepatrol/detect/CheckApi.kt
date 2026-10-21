package com.cookiepatrol.detect

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object CheckApi {
    // 에뮬레이터에서 본 PC의 localhost. 실기기 데모면 PC의 LAN IP로 바꿀 것.
    private const val BASE_URL = "http://10.0.2.2:8000"

    /** 백엔드(Google Safe Browsing + 캐시)에 묻는다. 네트워크 실패는 "위험 아님"으로 본다(막지 않는 원칙). */
    fun isDangerous(url: String): Boolean = runCatching {
        val conn = URL("$BASE_URL/check").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 3000
        conn.readTimeout = 3000
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(JSONObject().put("url", url).toString().toByteArray()) }
        JSONObject(conn.inputStream.bufferedReader().readText()).getBoolean("dangerous")
    }.getOrDefault(false)
}
