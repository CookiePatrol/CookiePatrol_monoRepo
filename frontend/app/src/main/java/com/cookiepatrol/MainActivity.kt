package com.cookiepatrol

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cookiepatrol.monitor.NotificationMonitor
import com.cookiepatrol.monitor.ScreenMonitor
import com.cookiepatrol.ui.CookiePatrolTheme
import com.cookiepatrol.ui.HomeScreen
import com.cookiepatrol.ui.PatrolSymbol
import com.cookiepatrol.ui.ProtectionSetting

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
    }

    // 설정에서 돌아올 때마다 실제 권한 상태를 다시 읽는다.
    override fun onResume() {
        super.onResume()
        val nm = getSystemService(NotificationManager::class.java)
        val settings = listOf(
            ProtectionSetting("경고 알림", "위험 신호를 바로 알려드려요", PatrolSymbol.BELL,
                nm.areNotificationsEnabled()) {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            },
            ProtectionSetting("문자·카톡 알림 감시", "알림의 글자와 링크를 살펴봐요", PatrolSymbol.MESSAGE,
                nm.isNotificationListenerAccessGranted(ComponentName(this, NotificationMonitor::class.java))) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
            ProtectionSetting("다운로드 감시", "앱 설치 파일을 받으면 알려드려요", PatrolSymbol.DOWNLOAD,
                Environment.isExternalStorageManager()) {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")))
            },
        )
        val screenComponent = ComponentName(this, ScreenMonitor::class.java)
        val screenEnabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.split(':')?.any { ComponentName.unflattenFromString(it) == screenComponent } == true
        setContent {
            CookiePatrolTheme {
                HomeScreen(settings, screenEnabled) {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
        }
    }
}
