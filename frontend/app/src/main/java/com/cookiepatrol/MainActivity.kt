package com.cookiepatrol

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cookiepatrol.monitor.NotificationMonitor
import com.cookiepatrol.monitor.ScreenMonitor
import com.cookiepatrol.ui.CookiePatrolTheme

private data class PermissionStatus(val title: String, val enabled: Boolean, val settings: Intent)

class MainActivity : ComponentActivity() {
    // 설정에서 돌아올 때 실제 권한 상태를 새로 읽는다.
    override fun onResume() {
        super.onResume()
        val nm = getSystemService(NotificationManager::class.java)
        val permissions = listOf(
            PermissionStatus("경고 알림", nm.areNotificationsEnabled(),
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)),
            PermissionStatus("문자·카톡 알림 감시",
                nm.isNotificationListenerAccessGranted(ComponentName(this, NotificationMonitor::class.java)),
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)),
            PermissionStatus("다운로드 감시", Environment.isExternalStorageManager(),
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))),
        )
        val screenComponent = ComponentName(this, ScreenMonitor::class.java)
        val screenEnabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.split(':')?.any { ComponentName.unflattenFromString(it) == screenComponent } == true
        setContent {
            CookiePatrolTheme {
                val next = permissions.firstOrNull { !it.enabled }
                Column(
                    Modifier.fillMaxSize().safeDrawingPadding()
                        .verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Text("Cookie Patrol", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                if (next == null) "기본 설정이\n완료됐어요" else "사기 예방,\n여기서 시작해요",
                                fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (next == null) "의심되는 내용을 발견하면 경고로 알려드려요. 평소처럼 휴대폰을 사용하세요."
                                else "아래 버튼을 누르면 필요한 설정으로 바로 이동해요.",
                                fontSize = 22.sp, lineHeight = 32.sp,
                            )
                            Text("기본 설정 ${permissions.count { it.enabled }} / ${permissions.size}",
                                fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            if (next != null) {
                                Button(
                                    onClick = { startActivity(next.settings) },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
                                ) {
                                    Text("${next.title} 켜기", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("설정을 마치고 돌아오면 켜진 상태를 확인해요.", fontSize = 22.sp, lineHeight = 30.sp)
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("한눈에 보는 설정", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        permissions.forEach { permission ->
                            if (permission.enabled) {
                                Text("✓ ${permission.title} 켜짐",
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.primary, fontSize = 22.sp, lineHeight = 30.sp)
                            } else {
                                OutlinedButton(
                                    onClick = { startActivity(permission.settings) },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                                ) { Text("${permission.title} · 꺼짐", fontSize = 22.sp) }
                            }
                        }
                    }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("화면 속 링크도 살펴보기", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text("선택 · 데모용 기능이에요. 다른 앱 화면의 글자와 링크를 읽어요.",
                                fontSize = 22.sp, lineHeight = 30.sp)
                            if (screenEnabled) {
                                Text("✓ 화면 감시 켜짐", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                            } else {
                                OutlinedButton(
                                    onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                                ) { Text("화면 감시 켜기", fontSize = 22.sp) }
                            }
                        }
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("링크 검사와 개인정보", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "알림·화면 문구 전체는 기기 안에서 검사해요. 의심 링크의 전체 주소는 데모 서버와 Google Safe Browsing으로 전송되고, 추가 분석이 필요하면 OpenAI로도 전송됩니다.",
                                fontSize = 20.sp, lineHeight = 29.sp,
                            )
                            Text(
                                "링크 주소에 개인 정보나 로그인 토큰이 들어 있을 수 있어요. 서버는 주소 원문 대신 변환값을 최대 24시간 저장하지만, 이 값도 완전한 익명 정보는 아닙니다.",
                                fontSize = 20.sp, lineHeight = 29.sp,
                            )
                        }
                    }
                    Text("경고는 주의를 돕는 안내예요. 모든 사기를 찾아낼 수는 없어요.", fontSize = 22.sp, lineHeight = 30.sp)
                }
            }
        }
    }
}
