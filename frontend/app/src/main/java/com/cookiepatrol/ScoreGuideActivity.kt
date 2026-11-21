package com.cookiepatrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cookiepatrol.detect.BANK_TRANSFER_SIGNAL_ID
import com.cookiepatrol.detect.PHONE_CALLBACK_SIGNAL_ID
import com.cookiepatrol.detect.RiskAnalyzer
import com.cookiepatrol.detect.SIGNALS
import com.cookiepatrol.ui.CookiePatrolTheme
import com.cookiepatrol.ui.PatrolCard
import com.cookiepatrol.ui.PatrolDetail
import com.cookiepatrol.ui.PatrolIcon
import com.cookiepatrol.ui.PatrolLabel
import com.cookiepatrol.ui.PatrolPage
import com.cookiepatrol.ui.PatrolSectionTitle
import com.cookiepatrol.ui.PatrolSymbol
import com.cookiepatrol.warn.LinkRiskLevel

private val exampleSignals = setOf("http", PHONE_CALLBACK_SIGNAL_ID, BANK_TRANSFER_SIGNAL_ID).let { ids ->
    SIGNALS.filter { it.id in ids }
}

class ScoreGuideActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CookiePatrolTheme {
                ScoreGuideScreen(onClose = ::finish)
            }
        }
    }
}

@Composable
private fun ScoreGuideScreen(onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    PatrolPage {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PatrolIcon(PatrolSymbol.INFO, colors.primary)
            Text(
                "검사 기준표",
                Modifier.weight(1f).semantics { heading() },
                fontSize = 28.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onClose) { Text("닫기", fontSize = 17.sp) }
        }

        PatrolCard(containerColor = colors.primaryContainer) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PatrolSectionTitle("점수 합산")
                Text(
                    "한 문자에서 감지한 위험 신호마다 한 번씩 점수를 더해요. 링크는 주소의 위험 신호를, 전화·계좌번호는 통화나 송금 요청의 맥락을 함께 살펴봐요.",
                    color = colors.onPrimaryContainer,
                    fontSize = 17.sp,
                    lineHeight = 25.sp,
                )
                PatrolDetail(
                    "세 신호만 감지된 예",
                    exampleSignals.joinToString(" + ") { "${it.title} ${it.score}점" } +
                        " = ${exampleSignals.sumOf { it.score }}점",
                )
            }
        }

        PatrolCard {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PatrolSectionTitle("경고 단계")
                PatrolDetail(
                    "${LinkRiskLevel.NOT_DETECTED.label} · 조용한 알림",
                    "기기 점수가 ${RiskAnalyzer.WARNING_THRESHOLD}점 미만이고 링크 검사에서 위험 신호를 찾지 못한 경우예요.",
                )
                PatrolDetail(
                    "${LinkRiskLevel.SUSPICIOUS.label} · 화면 팝업",
                    "기기 검사 ${RiskAnalyzer.WARNING_THRESHOLD}점 이상 또는 링크 추가 검사에서 의심 결과가 나오면 표시해요.",
                )
                PatrolDetail(
                    "${LinkRiskLevel.BLOCKLIST_MATCH.label} · 화면 팝업",
                    "Google 위험 목록과 일치하면 표시해요. 서버 결과는 경고 단계에 반영돼요.",
                )
                Text(
                    "같은 문자에서 더 높은 단계가 확인되면 처음 경고의 내용이 갱신돼요.",
                    color = colors.onSurfaceVariant,
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                )
            }
        }

        PatrolCard {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PatrolSectionTitle("위험 신호별 기준")
                SIGNALS.forEach { signal ->
                    HorizontalDivider(color = colors.outlineVariant)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(signal.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
                            Text(signal.reason, fontSize = 16.sp, lineHeight = 23.sp, color = colors.onSurfaceVariant)
                        }
                        PatrolLabel("+${signal.score}점", colors.primaryContainer, colors.onPrimaryContainer)
                    }
                }
            }
        }
    }
}
