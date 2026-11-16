package com.cookiepatrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cookiepatrol.detect.CheckApi
import com.cookiepatrol.detect.CheckResult
import com.cookiepatrol.detect.CheckStatus
import com.cookiepatrol.detect.RiskAnalyzer
import com.cookiepatrol.detect.UrlExtractor
import com.cookiepatrol.ui.CookiePatrolTheme
import com.cookiepatrol.ui.PatrolButton
import java.util.concurrent.Executors

/** Explicit URL checks reach AI even when the automatic local score is below 2. */
class ManualCheckActivity : ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CookiePatrolTheme {
                var input by remember { mutableStateOf("") }
                var busy by remember { mutableStateOf(false) }
                var message by remember { mutableStateOf("") }
                var lastInput by remember { mutableStateOf<String?>(null) }
                var localExplanation by remember { mutableStateOf("") }
                Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("링크 직접 검사", fontSize = 32.sp)
                    Text("문자 원문 대신 검사할 주소 한 개를 붙여넣으세요. 로컬 점수가 낮아도 추가 검사해요.", fontSize = 22.sp)
                    Text("검사 버튼을 누르면 주소 전체를 서버·Google으로 보내고, 필요하면 OpenAI로 전송해요. 개인정보나 로그인 토큰이 있는 주소는 보내지 마세요. 입력한 주소와 결과는 이 화면을 닫으면 버려요.", fontSize = 22.sp)
                    OutlinedTextField(value = input, onValueChange = { if (!busy) input = it.take(2049) },
                        enabled = !busy, label = { Text("검사할 URL", fontSize = 22.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp))
                    if (!busy) PatrolButton("주소 추가 검사") {
                        val raw = input.trim()
                        val local = RiskAnalyzer.analyze(raw)
                        val url = UrlExtractor.normalize(raw)
                        if (raw.isBlank() || raw.length > 2048 || raw.any { it.isWhitespace() } || url == null) {
                            lastInput = null; localExplanation = ""
                            message = "공백 없는 웹 주소 한 개를 2,048자 이내로 입력하세요."
                        } else {
                            lastInput = raw
                            localExplanation = "기기 검사 ${local.score}점 · " +
                                if (local.shouldWarn) "주의가 필요해요. ${local.reason}" else "로컬 경고 기준 미만이에요. 안전 확인은 아니에요."
                            busy = true
                            message = "추가 검사 중이에요. 잠시 기다려주세요."
                            executor.execute {
                                val result = CheckApi.checkDetails(url)
                                runOnUiThread {
                                    if (!isDestroyed && !isFinishing) {
                                        message = describe(result)
                                        busy = false
                                    }
                                }
                            }
                        }
                    }
                    if (message.isNotBlank()) {
                        if (lastInput != null && input.trim() != lastInput) {
                            Text("주소가 바뀌었어요. 아래는 이전 주소의 결과이므로 다시 검사하세요.", fontSize = 22.sp, color = MaterialTheme.colorScheme.error)
                        }
                        if (localExplanation.isNotBlank()) Text(localExplanation, fontSize = 24.sp)
                        Text(message, fontSize = 26.sp)
                    }
                    Text("AI는 도메인·하위 주소·경로·쿼리의 조합만 살펴봐요. 웹페이지 방문이나 이동 주소 확인은 하지 않아요. 위험 미감지·조회 실패여도 로컬 경고는 유지됩니다.", fontSize = 22.sp)
                    PatrolButton("닫기") { finish() }
                }
            }
        }
    }

    private fun describe(result: CheckResult) = when (result.status) {
        CheckStatus.DANGEROUS -> "주의가 필요해요. ${result.warningReason}\n누르지 말고 공식 앱이나 평소 알던 전화번호로 확인하세요."
        CheckStatus.NOT_DETECTED -> "추가 검사에서 위험 신호를 찾지 못했어요. 안전 보증은 아니에요."
        CheckStatus.UNKNOWN -> "추가 검사를 마치지 못했어요. 연결·서버 상태를 확인한 뒤 다시 시도하세요."
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
