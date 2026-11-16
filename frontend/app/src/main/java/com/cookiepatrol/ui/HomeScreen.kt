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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    val guide: PermissionGuide,
    val open: () -> Unit,
)

@Composable
fun HomeScreen(settings: List<ProtectionSetting>, screenEnabled: Boolean,
               monitorConnected: Boolean, extraMonitorsConnected: Boolean, remoteEnabled: Boolean, changeRemoteConsent: (Boolean) -> Unit,
               openManualCheck: () -> Unit, openFieldTrial: (() -> Unit)? = null, openScreenSettings: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val next = settings.firstOrNull { !it.enabled }
    var explaining by remember { mutableStateOf<ProtectionSetting?>(null) }
    var showConsent by remember { mutableStateOf(false) }
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
                if (next != null) PatrolButton("${next.title} 켜기", { explaining = next })
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("보호 설정")
            PatrolCard {
                settings.forEachIndexed { index, setting ->
                    SettingRow(setting) { explaining = setting }
                    if (index < settings.lastIndex) HorizontalDivider(
                        Modifier.padding(start = 80.dp), color = colors.outlineVariant,
                    )
                }
            }
            Text("설정 후 돌아오면 상태가 자동으로 반영돼요.", Modifier.padding(horizontal = 4.dp),
                fontSize = 20.sp, lineHeight = 28.sp, color = colors.onSurfaceVariant)
            Text(if (monitorConnected) "알림 감시 서비스가 연결됐어요." else "알림 감시 서비스 연결을 확인하지 못했어요. 권한이 켜져 있어도 기종별 절전 설정으로 멈출 수 있어요.",
                fontSize = 22.sp, lineHeight = 32.sp)
            if (monitorConnected && !extraMonitorsConnected) Text(
                "앱 설치·다운로드 감시를 연결하지 못했어요. 문자·카톡 알림 감시 설정을 껐다 켜고 다시 확인해주세요.",
                fontSize = 22.sp, lineHeight = 32.sp, color = colors.error,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("추가 보호")
            PatrolCard {
                val setting = ProtectionSetting("화면 속 링크", "선택 · 데모용 화면 감시", PatrolSymbol.SCREEN,
                    screenEnabled, PermissionGuides.screen, openScreenSettings)
                SettingRow(setting) { explaining = setting }
                Text("다른 앱 화면에 보이는 글자와 링크를 살펴봐요.", Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    fontSize = 22.sp, lineHeight = 32.sp, color = colors.onSurfaceVariant)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("어떻게 판단하나요?")
            Text("기기 안에서 12개 위험 신호의 점수를 더해요. 3점부터 경고하고, 링크가 있으면 2점부터 Google 위험 목록을 조회해요. 점수는 위험 확률이 아닌 시험용 기준이에요.",
                fontSize = 22.sp, lineHeight = 32.sp)
            Text("목록에 없거나 조회하지 못하면 AI가 주소의 도메인·경로 조합을 추가 분석해요. AI는 웹페이지를 방문하지 않아요. 경고에 Google 목록인지 AI 분석인지 구분해 알려드려요. 경고가 없어도 안전하다는 뜻은 아니에요.",
                fontSize = 22.sp, lineHeight = 32.sp)
            Text(if (remoteEnabled) "자동 링크 추가 검사: 동의함" else "자동 링크 추가 검사: 꺼짐 · 현재는 기기 안에서 검사해요.", fontSize = 22.sp)
            PatrolButton(if (remoteEnabled) "자동 링크 검사 동의 철회" else "자동 링크 추가 검사 안내", {
                if (remoteEnabled) changeRemoteConsent(false) else showConsent = true
            })
            PatrolButton("링크 직접 검사", openManualCheck)
            if (openFieldTrial != null) PatrolButton("실험 기록 (디버그)", openFieldTrial)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("개인정보 안내")
            PatrolCard {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PatrolIcon(PatrolSymbol.INFO, colors.primary)
                    Text("문구는 기기 안에서,\n링크는 추가 검사해요", fontSize = 26.sp,
                        lineHeight = 36.sp, fontWeight = FontWeight.SemiBold)
                    Text("알림·화면 문구 전체는 기기 안에서 검사해요. 자동 외부 검사에 동의하거나 링크 직접 검사 버튼을 누른 경우, 의심 링크의 전체 주소는 서버와 Google Safe Browsing으로 전송되고 필요하면 OpenAI로도 전송돼요.",
                        fontSize = 22.sp, lineHeight = 32.sp, color = colors.onSurfaceVariant)
                    Text("화면 OCR은 켜져 있을 때만 화면을 잠깐 캡처해 기기 안에서 글자를 읽어요. 캡처 이미지는 저장하거나 서버로 보내지 않습니다.",
                        fontSize = 22.sp, lineHeight = 32.sp, color = colors.onSurfaceVariant)
                    HorizontalDivider(color = colors.outlineVariant)
                    Text("주소에 개인정보나 로그인 토큰이 포함될 수 있어요. 서버는 주소 원문 대신 해시·판정·시각을 저장해요. 판정은 24시간 동안 사용하며 만료 기록은 다음 조회·저장 때 지워요. 완전한 익명 정보는 아닙니다.",
                        fontSize = 22.sp, lineHeight = 32.sp, color = colors.onSurfaceVariant)
                }
            }
        }
        Text("경고는 주의를 돕는 안내예요.\n모든 사기를 찾아낼 수는 없어요.",
            Modifier.padding(horizontal = 4.dp, vertical = 8.dp), fontSize = 20.sp,
            lineHeight = 30.sp, color = colors.onSurfaceVariant)
    }
    explaining?.let { setting ->
        AlertDialog(
            onDismissRequest = { explaining = null },
            title = { Text(setting.title, fontSize = 26.sp) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf("필요한 이유" to setting.guide.purpose, "켜는 방법" to setting.guide.steps,
                        "끄는 방법" to setting.guide.revoke, "알아두세요" to setting.guide.limitation).forEach { (title, text) ->
                        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        Text(text, fontSize = 22.sp, lineHeight = 32.sp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { explaining = null; setting.open() }) { Text("설정으로 이동", fontSize = 22.sp) } },
            dismissButton = { TextButton(onClick = { explaining = null }) { Text("닫기", fontSize = 22.sp) } },
        )
    }
    if (showConsent) AlertDialog(
        onDismissRequest = { showConsent = false },
        title = { Text("의심 링크를 추가 검사할까요?", fontSize = 26.sp) },
        text = { Text("문자와 화면의 전체 문구는 보내지 않아요. 의심 링크의 전체 주소는 서버·Google으로 보내고, 필요하면 OpenAI로 보내요. 주소 경로와 쿼리에 개인정보·로그인 토큰이 들어 있을 수 있어요. 서버는 주소 원문 대신 해시·판정·시각을 저장해요. 판정은 24시간 동안 사용하고 만료 기록은 다음 조회·저장 때 지워요. 해시는 익명화가 아니에요. 동의는 이 화면에서 언제든 철회할 수 있고, 철회 전에 전송된 정보는 되돌릴 수 없어요. 동의하지 않아도 기기 내 경고는 동작해요.", modifier = Modifier.verticalScroll(rememberScrollState()), fontSize = 22.sp) },
        confirmButton = { TextButton(onClick = { changeRemoteConsent(true); showConsent = false }) { Text("동의하고 켜기", fontSize = 22.sp) } },
        dismissButton = { TextButton(onClick = { showConsent = false }) { Text("기기 안에서만 검사", fontSize = 22.sp) } },
    )
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, Modifier.padding(horizontal = 4.dp), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SettingRow(setting: ProtectionSetting, explain: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interaction = Modifier.clickable(role = Role.Button, onClick = explain)
    Row(
        interaction.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PatrolIcon(setting.symbol, if (setting.enabled) colors.secondary else colors.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(setting.title, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
            Text(setting.description, fontSize = 20.sp, lineHeight = 28.sp, color = colors.onSurfaceVariant)
            Text(if (setting.enabled) "✓ 켜짐 · 안내 / 끄기" else "눌러서 안내 보기", fontSize = 20.sp, lineHeight = 28.sp,
                color = if (setting.enabled) colors.secondary else colors.primary, fontWeight = FontWeight.SemiBold)
        }
        if (!setting.enabled) Text("›", fontSize = 30.sp, color = colors.onSurfaceVariant)
    }
}
