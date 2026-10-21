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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cookiepatrol.monitor.NotificationMonitor

/** 첫 화면: 감시에 필요한 권한을 큰 버튼으로 하나씩 켠다. */
class MainActivity : ComponentActivity() {

    // 설정에서 돌아올 때마다 켜짐/꺼짐 상태를 다시 그린다
    override fun onResume() {
        super.onResume()
        setContent { MaterialTheme { Home() } }
    }

    @Composable
    private fun Home() {
        val nm = getSystemService(NotificationManager::class.java)
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Cookie Patrol", fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text("아래 항목을 모두 켜면 사기 문자와 링크를 대신 살펴봐 드려요.", fontSize = 22.sp)
            Permission("경고 알림 보여주기", nm.areNotificationsEnabled()) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
            Permission(
                "문자·카톡 알림 살펴보기",
                nm.isNotificationListenerAccessGranted(ComponentName(this@MainActivity, NotificationMonitor::class.java)),
            ) { Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
            Permission("다운로드 파일 살펴보기", Environment.isExternalStorageManager()) {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
            }
            Permission(
                "화면 링크 살펴보기 (데모)",
                Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                    ?.contains(packageName) == true,
            ) { Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
        }
    }

    @Composable
    private fun Permission(label: String, on: Boolean, settings: () -> Intent) {
        Button(
            onClick = { startActivity(settings()) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (on) Color(0xFF2E7D32) else Color(0xFF1565C0)),
        ) {
            Text(if (on) "✓ $label" else "$label — 눌러서 켜기", fontSize = 22.sp)
        }
    }
}
