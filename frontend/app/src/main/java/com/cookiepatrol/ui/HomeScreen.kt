package com.cookiepatrol.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ProtectionSetting(
    val title: String,
    val description: String,
    val symbol: PatrolSymbol,
    val enabled: Boolean,
    val open: () -> Unit,
)

@Composable
fun HomeScreen(settings: List<ProtectionSetting>, screenEnabled: Boolean, openScreenSettings: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val next = settings.firstOrNull { !it.enabled }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("COOKIE PATROL", color = colors.onSurfaceVariant, fontSize = 20.sp,
                letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
            Text("안심을 더하는\n작은 습관", fontSize = 38.sp, lineHeight = 46.sp,
                letterSpacing = (-1).sp, fontWeight = FontWeight.Bold)
        }
        PatrolCard {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PatrolIcon(PatrolSymbol.SHIELD, if (next == null) colors.secondary else colors.primary)
                    Text("기본 설정 ${settings.count { it.enabled }} / ${settings.size}", fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant)
                }
                Text(if (next == null) "설정이 완료됐어요" else "보호 설정을\n완성해요", fontSize = 32.sp,
                    lineHeight = 40.sp, letterSpacing = (-0.8).sp, fontWeight = FontWeight.Bold)
                Text(
                    if (next == null) "평소처럼 휴대폰을 사용하세요. 위험 신호를 발견하면 알려드려요."
                    else "한 번 설정하면, 의심스러운 문자와 링크를 대신 살펴봐요.",
                    fontSize = 22.sp, lineHeight = 32.sp, color = colors.onSurfaceVariant,
                )
                if (next != null) PatrolButton("${next.title} 켜기", next.open)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("보호 설정")
            PatrolCard {
                settings.forEachIndexed { index, setting ->
                    SettingRow(setting)
                    if (index < settings.lastIndex) HorizontalDivider(
                        Modifier.padding(start = 80.dp), color = colors.outlineVariant,
                    )
                }
            }
            Text("설정 후 돌아오면 상태가 자동으로 반영돼요.", Modifier.padding(horizontal = 4.dp),
                fontSize = 20.sp, lineHeight = 28.sp, color = colors.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("추가 보호")
            PatrolCard {
                SettingRow(ProtectionSetting("화면 속 링크", "선택 · 데모용 화면 감시", PatrolSymbol.SCREEN,
                    screenEnabled, openScreenSettings))
                Text("다른 앱 화면에 보이는 글자와 링크를 살펴봐요.", Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    fontSize = 22.sp, lineHeight = 32.sp, color = colors.onSurfaceVariant)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("개인정보 안내")
            PatrolCard {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PatrolIcon(PatrolSymbol.INFO, colors.primary)
                    Text("문구는 기기 안에서,\n링크는 추가 검사해요", fontSize = 26.sp,
                        lineHeight = 36.sp, fontWeight = FontWeight.SemiBold)
                    Text("알림·화면 문구 전체는 기기 안에서 검사해요. 의심 링크의 전체 주소는 데모 서버와 Google Safe Browsing으로 전송되고, 추가 분석이 필요하면 OpenAI로도 전송됩니다.",
                        fontSize = 22.sp, lineHeight = 32.sp, color = colors.onSurfaceVariant)
                    HorizontalDivider(color = colors.outlineVariant)
                    Text("주소에 개인정보나 로그인 토큰이 포함될 수 있어요. 서버는 주소 원문 대신 변환값을 최대 24시간 저장하지만, 완전한 익명 정보는 아닙니다.",
                        fontSize = 22.sp, lineHeight = 32.sp, color = colors.onSurfaceVariant)
                }
            }
        }
        Text("경고는 주의를 돕는 안내예요.\n모든 사기를 찾아낼 수는 없어요.",
            Modifier.padding(horizontal = 4.dp, vertical = 8.dp), fontSize = 20.sp,
            lineHeight = 30.sp, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, Modifier.padding(horizontal = 4.dp), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SettingRow(setting: ProtectionSetting) {
    val colors = MaterialTheme.colorScheme
    val interaction = if (setting.enabled) Modifier else Modifier.clickable(role = Role.Button, onClick = setting.open)
    Row(
        interaction.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PatrolIcon(setting.symbol, if (setting.enabled) colors.secondary else colors.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(setting.title, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
            Text(setting.description, fontSize = 20.sp, lineHeight = 28.sp, color = colors.onSurfaceVariant)
            Text(if (setting.enabled) "✓ 켜짐" else "눌러서 켜기", fontSize = 20.sp, lineHeight = 28.sp,
                color = if (setting.enabled) colors.secondary else colors.primary, fontWeight = FontWeight.SemiBold)
        }
        if (!setting.enabled) Text("›", fontSize = 30.sp, color = colors.onSurfaceVariant)
    }
}
