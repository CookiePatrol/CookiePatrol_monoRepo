package com.cookiepatrol.detect

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CheckApiTransportTest {
    @Test fun googleTtlIsParsedAndAbsentTtlDoesNotInventFiveMinutes() {
        withResponse(200, """{"dangerous":true,"source":"safe_browsing","indicators":[],"google_cache_ttl_seconds":30}""") { endpoint ->
            val result = CheckApi.checkAt("https://example.com/한글?token=FAKE", endpoint)
            assertEquals(CheckStatus.DANGEROUS, result.status)
            assertTrue(result.cacheLifetimeMillis in 1..30_000L)
        }
        withResponse(200, """{"dangerous":true,"source":"safe_browsing","indicators":[]}""") { endpoint ->
            assertEquals(0L, CheckApi.checkAt("https://example.com/한글?token=FAKE", endpoint).cacheLifetimeMillis)
        }
    }

    private fun withResponse(status: Int, body: String, path: String = "/check/details", run: (String) -> Unit) {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 5000 }
        val executor = Executors.newSingleThreadExecutor()
        val serving = executor.submit {
            server.accept().use { socket ->
                socket.soTimeout = 5000
                val input = socket.getInputStream()
                val headers = StringBuilder()
                while (!headers.endsWith("\r\n\r\n")) {
                    val byte = input.read(); check(byte >= 0 && headers.length < 8192); headers.append(byte.toChar())
                }
                assertTrue(headers.startsWith("POST $path HTTP/1.1"))
                val length = Regex("(?i)Content-Length: (\\d+)").find(headers)!!.groupValues[1].toInt()
                val content = ByteArray(length)
                var read = 0
                while (read < length) { val count = input.read(content, read, length - read); check(count > 0); read += count }
                assertEquals("https://example.com/한글?token=FAKE", JSONObject(String(content, Charsets.UTF_8)).getString("url"))
                val bytes = body.toByteArray(Charsets.UTF_8)
                socket.getOutputStream().apply {
                    write("HTTP/1.1 $status Test\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(bytes); flush()
                }
            }
        }
        try {
            run("http://127.0.0.1:${server.localPort}$path")
            serving.get(5, TimeUnit.SECONDS)
        } finally { server.close(); executor.shutdownNow() }
    }

    @Test fun actualHttpTransportMatchesDetailedServerContract() {
        withResponse(200, """{"dangerous":true,"source":"ai","indicators":["deceptive_host"],"reason":"untrusted server text","prompt_version":"url-structure-v2","limitation":"URL only"}""") { endpoint ->
            val result = CheckApi.checkAt("https://example.com/한글?token=FAKE", endpoint)
            assertEquals(CheckStatus.DANGEROUS, result.status)
            assertEquals("ai", result.source)
            assertFalse(result.warningReason.contains("untrusted"))
        }
    }

    @Test fun olderDeploymentStillChecksLinksWithoutClaimingASpecificProvider() {
        for (dangerous in listOf(true, false)) {
            withResponse(404, "{}") { details ->
                withResponse(200, """{"dangerous":$dangerous}""", "/check") { legacy ->
                    val result = CheckApi.checkAt("https://example.com/한글?token=FAKE", details, legacy)
                    assertEquals(if (dangerous) CheckStatus.DANGEROUS else CheckStatus.NOT_DETECTED, result.status)
                    assertEquals("legacy", result.source)
                    assertEquals(0L, result.cacheLifetimeMillis)
                    assertFalse(result.warningReason.contains("Google"))
                }
            }
        }
    }

    @Test fun cachedEvidenceIsShownAndOldReasonlessResponsesStillWork() {
        withResponse(200, """{"dangerous":true,"source":"ai_cache","indicators":[],"cached_indicators":["deceptive_host"]}""") { endpoint ->
            val result = CheckApi.checkAt("https://example.com/한글?token=FAKE", endpoint)
            assertEquals(CheckStatus.DANGEROUS, result.status)
            assertEquals(listOf("deceptive_host"), result.indicators)
            assertTrue(result.warningReason.contains(AI_INDICATOR_REASONS.getValue("deceptive_host")))
        }
        withResponse(200, """{"dangerous":true,"source":"ai_cache","indicators":[]}""") { endpoint ->
            assertEquals(CheckStatus.DANGEROUS, CheckApi.checkAt("https://example.com/한글?token=FAKE", endpoint).status)
        }
    }

    @Test fun rateLimitMalformedAndOversizedResponsesAreUnknown() {
        for ((status, body) in listOf(429 to "{}", 503 to "{}", 200 to "broken",
            200 to """{"dangerous":"false","source":"ai","indicators":[]}""",
            200 to "x".repeat(16_385))) {
            withResponse(status, body) { endpoint ->
                assertEquals(CheckStatus.UNKNOWN, CheckApi.checkAt("https://example.com/한글?token=FAKE", endpoint).status)
            }
        }
    }
}
