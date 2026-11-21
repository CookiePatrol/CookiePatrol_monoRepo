package com.cookiepatrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cookiepatrol.detect.CheckApi
import com.cookiepatrol.detect.CheckResult
import com.cookiepatrol.detect.CheckStatus
import com.cookiepatrol.detect.LocalAssessment
import com.cookiepatrol.detect.RiskAnalyzer
import com.cookiepatrol.detect.UrlExtractor
import com.cookiepatrol.ui.CookiePatrolTheme
import com.cookiepatrol.ui.LinkDisclosure
import com.cookiepatrol.ui.PatrolButton
import com.cookiepatrol.ui.PatrolCard
import com.cookiepatrol.ui.PatrolDetail
import com.cookiepatrol.ui.PatrolIcon
import com.cookiepatrol.ui.PatrolLabel
import com.cookiepatrol.ui.PatrolPage
import com.cookiepatrol.ui.PatrolSectionTitle
import com.cookiepatrol.ui.PatrolSymbol
import java.util.concurrent.Executors

/** Checks the submitted URL regardless of the device score. */
class ManualCheckActivity : ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CookiePatrolTheme {
                ManualCheckScreen(onClose = ::finish) { url, complete ->
                    executor.execute {
                        val result = CheckApi.checkDetails(url)
                        runOnUiThread {
                            if (!isDestroyed && !isFinishing) complete(result)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}

@Composable
private fun ManualCheckScreen(onClose: () -> Unit, check: (String, (CheckResult) -> Unit) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    var input by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var lastInput by remember { mutableStateOf<String?>(null) }
    var local by remember { mutableStateOf<LocalAssessment?>(null) }
    var result by remember { mutableStateOf<CheckResult?>(null) }
    var showDetails by remember { mutableStateOf(false) }

    PatrolPage(Modifier.imePadding()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PatrolIcon(PatrolSymbol.LINK, colors.onPrimaryContainer)
            Text("링크 직접 검사", Modifier.weight(1f).semantics { heading() },
                fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onClose) { Text("닫기", fontSize = 17.sp) }
        }
        PatrolCard {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it.take(2049)
                        inputError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    isError = inputError,
                    singleLine = true,
                    label = { Text("검사할 URL") },
                    placeholder = { Text("https://example.com") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                )
                if (inputError) {
                    Text("공백 없는 웹 주소 한 개를 2,048자 이내로 입력하세요.",
                        color = colors.error, fontSize = 16.sp, lineHeight = 23.sp)
                }
                Text(LinkDisclosure.TRANSFER, fontSize = 16.sp, lineHeight = 23.sp, color = colors.onSurfaceVariant)
                Text(LinkDisclosure.SENSITIVE, fontSize = 16.sp, lineHeight = 23.sp, color = colors.onSurfaceVariant)
                PatrolButton(if (busy) "검사 중이에요" else "주소 추가 검사", enabled = !busy) {
                    if (busy) return@PatrolButton
                    val raw = input.trim()
                    val url = UrlExtractor.normalize(raw)
                    if (raw.isBlank() || raw.length > 2048 || raw.any { it.isWhitespace() } || url == null) {
                        inputError = true
                        lastInput = null
                        local = null
                        result = null
                    } else {
                        focus.clearFocus()
                        lastInput = raw
                        local = RiskAnalyzer.analyze(raw)
                        result = null
                        busy = true
                        check(url) { response ->
                            result = response
                            busy = false
                        }
                    }
                }
                TextButton(onClick = { showDetails = true }, modifier = Modifier.align(Alignment.End)) {
                    Text("전송 안내", fontSize = 16.sp)
                }
            }
        }

        local?.let { assessment ->
            if (lastInput == input.trim()) {
                DeviceCheckCard(assessment)
                AdditionalCheckCard(result)
            } else {
                Text("주소를 바꿨어요. 다시 검사해 주세요.",
                    Modifier.padding(horizontal = 4.dp), fontSize = 17.sp, color = colors.onSurfaceVariant)
            }
        }
    }

    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { PatrolSectionTitle("직접 검사 안내") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    PatrolDetail("전송 범위", "${LinkDisclosure.TRANSFER} ${LinkDisclosure.SENSITIVE}")
                    PatrolDetail("분석 범위", LinkDisclosure.ANALYSIS)
                    PatrolDetail("기록", "화면을 닫으면 입력과 결과가 지워져요. ${LinkDisclosure.STORAGE}")
                }
            },
            confirmButton = { TextButton(onClick = { showDetails = false }) { Text("닫기", fontSize = 17.sp) } },
        )
    }
}

@Composable
private fun DeviceCheckCard(assessment: LocalAssessment) {
    val colors = MaterialTheme.colorScheme
    val warning = assessment.shouldWarn
    PatrolCard(containerColor = if (warning) colors.errorContainer else colors.surface) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PatrolSectionTitle("기기 검사", Modifier.weight(1f))
                PatrolLabel("${assessment.score}점",
                    if (warning) colors.error else colors.primaryContainer,
                    if (warning) colors.onError else colors.onPrimaryContainer)
            }
            if (assessment.hits.isEmpty()) {
                Text("기기 검사에서 위험 신호를 찾지 못했어요.", fontSize = 18.sp, lineHeight = 26.sp)
            } else {
                Text(if (warning) "주의가 필요해요" else "경고 기준 미만", fontSize = 19.sp, lineHeight = 26.sp,
                    fontWeight = FontWeight.SemiBold)
                assessment.hits.sortedByDescending { it.score }.forEach { signal ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(signal.reason, Modifier.weight(1f), fontSize = 18.sp, lineHeight = 26.sp)
                        PatrolLabel("+${signal.score}")
                    }
                }
            }
        }
    }
}

@Composable
private fun AdditionalCheckCard(result: CheckResult?) {
    val colors = MaterialTheme.colorScheme
    val dangerous = result?.status == CheckStatus.DANGEROUS
    PatrolCard(containerColor = if (dangerous) colors.errorContainer else colors.surface) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val summary = when (result?.status) {
                CheckStatus.DANGEROUS -> "위험 신호"
                CheckStatus.NOT_DETECTED -> "미감지"
                CheckStatus.UNKNOWN -> "확인 불가"
                null -> "검사 중"
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PatrolSectionTitle("서버 추가 검사", Modifier.weight(1f))
                PatrolLabel(summary,
                    if (dangerous) colors.error else colors.surfaceVariant,
                    if (dangerous) colors.onError else colors.onSurfaceVariant)
            }
            when (result?.status) {
                CheckStatus.DANGEROUS -> {
                    Text(result.warningReason, fontSize = 18.sp, lineHeight = 26.sp)
                    Text("링크를 열지 말고 공식 앱이나 평소 쓰던 번호로 확인하세요.",
                        fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
                }
                CheckStatus.NOT_DETECTED -> Text("위험 신호를 찾지 못했어요. 안전이 확인된 것은 아니에요.",
                    fontSize = 18.sp, lineHeight = 26.sp)
                CheckStatus.UNKNOWN -> Text("연결이나 서버 상태를 확인한 뒤 다시 시도해 주세요.",
                    fontSize = 18.sp, lineHeight = 26.sp)
                null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}
