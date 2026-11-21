package com.cookiepatrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cookiepatrol.warn.WarningAssessment
import com.cookiepatrol.warn.warningLabel

@Composable
internal fun WarningScreen(
    reason: String,
    action: String,
    onDismiss: () -> Unit,
    messagePreview: String? = null,
    sender: String? = null,
    receivedAt: String? = null,
    linkHost: String? = null,
    additionalReason: String? = null,
    riskStage: String? = null,
    assessment: WarningAssessment? = null,
) {
    val colors = MaterialTheme.colorScheme
    PatrolPage {
        PatrolCard(containerColor = colors.errorContainer) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                riskStage?.let { PatrolLabel(it, colors.error, colors.onError) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PatrolIcon(PatrolSymbol.WARNING, colors.error)
                    Text(if (messagePreview == null) "사기(피싱)일 수 있어요" else "이 메시지는 사기일 수 있어요",
                        Modifier.weight(1f).semantics { heading() },
                        color = colors.onErrorContainer, fontSize = 27.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = colors.onErrorContainer.copy(alpha = 0.18f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PatrolSectionTitle("의심되는 이유")
                    if (assessment == null || assessment.findings.isEmpty()) {
                        Text(reason, fontSize = 20.sp, lineHeight = 29.sp)
                        additionalReason?.let { Text(it, fontSize = 18.sp, lineHeight = 26.sp) }
                    } else {
                        PatrolLabel("기기 검사 ${assessment.score}점", colors.error, colors.onError)
                        assessment.findings.groupBy { it.category }.toSortedMap().forEach { (category, findings) ->
                            HorizontalDivider(color = colors.onErrorContainer.copy(alpha = 0.18f))
                            Text(category.warningLabel, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.semantics { heading() })
                            findings.forEach {
                                PatrolDetail(it.label, it.reason)
                            }
                        }
                    }
                    linkHost?.let { PatrolDetail("의심 링크 주소", it) }
                }
            }
        }

        if (messagePreview != null) {
            PatrolCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PatrolSectionTitle("확인할 메시지")
                    sender?.let { PatrolDetail("보낸 사람·대화", it) }
                    receivedAt?.let { PatrolDetail("메시지 알림 시각", it) }
                    if (messagePreview.isNotBlank()) {
                        Text("“$messagePreview”", fontSize = 19.sp, lineHeight = 28.sp)
                    }
                }
            }
        }

        PatrolCard(containerColor = colors.primaryContainer) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PatrolSectionTitle("지금 할 일")
                Text(action, fontSize = 19.sp, lineHeight = 28.sp)
            }
        }
        PatrolButton("확인했어요", onClick = onDismiss)
    }
}
