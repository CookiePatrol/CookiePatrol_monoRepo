package com.cookiepatrol.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cookiepatrol.R
import com.cookiepatrol.detect.RemoteCheckScope

data class ProtectionSetting(
    val title: String,
    val description: String?,
    val symbol: PatrolSymbol,
    val enabled: Boolean,
    val guide: PermissionGuide,
    val open: () -> Unit,
)

@Composable
internal fun HomeScreen(
    settings: List<ProtectionSetting>,
    screenEnabled: Boolean,
    monitorConnected: Boolean,
    extraMonitorsConnected: Boolean,
    remoteScope: RemoteCheckScope,
    changeRemoteConsent: (Boolean) -> Unit,
    openManualCheck: () -> Unit,
    openScoreGuide: () -> Unit,
    openFieldTrial: (() -> Unit)? = null,
    openScreenSettings: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var explaining by remember { mutableStateOf<ProtectionSetting?>(null) }
    var showConsent by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }

    PatrolPage {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(painterResource(R.drawable.ic_cookie_patrol), contentDescription = null,
                modifier = Modifier.size(44.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Cookie Patrol", Modifier.semantics { heading() },
                    fontSize = 25.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
                Text(if (monitorConnected) "문자 알림 감시 중" else "문자 감시 연결 필요",
                    fontSize = 17.sp, lineHeight = 24.sp, color = colors.onSurfaceVariant)
            }
            TextButton(onClick = openScoreGuide, modifier = Modifier.align(Alignment.Top)) {
                Text("검사 기준표", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        PatrolCard(containerColor = colors.primaryContainer) {
            HomeRow("링크 직접 검사", PatrolSymbol.LINK, featured = true, onClick = openManualCheck)
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PatrolSectionTitle("보호 설정", Modifier.padding(horizontal = 4.dp))
            PatrolCard {
                settings.forEachIndexed { index, setting ->
                    if (index > 0) HomeDivider()
                    HomeRow(setting.title, setting.symbol, setting.enabled, setting.description) {
                        explaining = setting
                    }
                }
            }
            if (monitorConnected && !extraMonitorsConnected) {
                Text("설치·다운로드 감시가 연결되지 않았어요. 알림 접근을 껐다 켜 주세요.", Modifier.padding(horizontal = 4.dp),
                    fontSize = 16.sp, lineHeight = 23.sp, color = colors.error)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PatrolSectionTitle("추가 기능", Modifier.padding(horizontal = 4.dp))
            PatrolCard {
                HomeRow(
                    "자동 링크 추가 검사", PatrolSymbol.LINK, remoteScope != RemoteCheckScope.OFF,
                    "의심 링크만 · 전체 검사 동의 필요".takeIf { remoteScope == RemoteCheckScope.SUSPICIOUS },
                ) { showConsent = true }
                HomeDivider()
                val screen = ProtectionSetting("화면 속 링크", "선택 · 데모", PatrolSymbol.SCREEN,
                    screenEnabled, PermissionGuides.screen, openScreenSettings)
                HomeRow(screen.title, screen.symbol, screen.enabled, screen.description) { explaining = screen }
                HomeDivider()
                HomeRow("개인정보 안내", PatrolSymbol.INFO) { showPrivacy = true }
            }
        }

        if (openFieldTrial != null) {
            TextButton(onClick = openFieldTrial) { Text("실험 기록 (디버그)", fontSize = 16.sp) }
        }
    }

    explaining?.let { setting ->
        AlertDialog(
            onDismissRequest = { explaining = null },
            title = { PatrolSectionTitle(setting.title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    PatrolDetail("필요한 이유", setting.guide.purpose)
                    PatrolDetail("켜는 방법", setting.guide.steps)
                    PatrolDetail("끄는 방법", setting.guide.revoke)
                    PatrolDetail("알아둘 점", setting.guide.limitation)
                }
            },
            confirmButton = {
                TextButton(onClick = { explaining = null; setting.open() }) {
                    Text("설정으로 이동", fontSize = 17.sp)
                }
            },
            dismissButton = { TextButton(onClick = { explaining = null }) { Text("닫기", fontSize = 17.sp) } },
        )
    }

    if (showConsent) {
        val allMessageLinks = remoteScope == RemoteCheckScope.ALL_MESSAGE_LINKS
        AlertDialog(
            onDismissRequest = { showConsent = false },
            title = { PatrolSectionTitle("자동 링크 추가 검사") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    PatrolDetail("자동 검사 범위", "문자·카톡 알림에 나온 링크는 점수와 관계없이 검사해요. 화면 감시에서는 의심 링크만 추가 검사해요.")
                    PatrolDetail("전송 범위", "${LinkDisclosure.TRANSFER} 전체 문자·화면 문구는 기기에서 검사해요.")
                    PatrolDetail("개인정보", "주소에 개인정보나 로그인 토큰이 포함될 수 있어요. ${LinkDisclosure.STORAGE}")
                    PatrolDetail("동의 관리", "여기서 언제든 끌 수 있어요. 이전에 보낸 정보는 회수할 수 없어요. 기기 내 경고는 계속 동작해요.")
                }
            },
            confirmButton = {
                TextButton(onClick = { changeRemoteConsent(!allMessageLinks); showConsent = false }) {
                    Text(if (allMessageLinks) "동의 철회" else "동의하고 켜기", fontSize = 17.sp)
                }
            },
            dismissButton = {
                if (remoteScope == RemoteCheckScope.SUSPICIOUS) {
                    TextButton(onClick = { changeRemoteConsent(false); showConsent = false }) {
                        Text("동의 철회", fontSize = 17.sp)
                    }
                } else {
                    TextButton(onClick = { showConsent = false }) { Text("닫기", fontSize = 17.sp) }
                }
            },
        )
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { PatrolSectionTitle("개인정보 안내") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    PatrolDetail("문자·화면", "문구는 기기 안에서 검사해요. 화면 감시를 켰을 때 잠깐 읽는 이미지는 처리 후 버려요.")
                    PatrolDetail("링크", "자동 검사에 동의하거나 직접 검사할 때 전송해요. ${LinkDisclosure.TRANSFER}")
                    PatrolDetail("서버 기록", LinkDisclosure.STORAGE)
                }
            },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("닫기", fontSize = 17.sp) } },
        )
    }
}

@Composable
private fun HomeDivider() {
    HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun HomeRow(
    title: String,
    symbol: PatrolSymbol,
    enabled: Boolean? = null,
    description: String? = null,
    featured: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val tint = if (featured) colors.onPrimaryContainer else colors.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (featured) 19.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PatrolIcon(symbol, tint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = if (featured) 23.sp else 19.sp,
                lineHeight = if (featured) 32.sp else 26.sp, fontWeight = FontWeight.SemiBold)
            description?.let { Text(it, fontSize = 15.sp, lineHeight = 21.sp, color = colors.onSurfaceVariant) }
        }
        enabled?.let {
            PatrolLabel(if (it) "켜짐" else "꺼짐",
                if (it) colors.primaryContainer else colors.surfaceVariant,
                if (it) colors.onPrimaryContainer else colors.onSurfaceVariant)
        }
        Text("›", Modifier.clearAndSetSemantics {}, fontSize = 26.sp, color = tint)
    }
}
