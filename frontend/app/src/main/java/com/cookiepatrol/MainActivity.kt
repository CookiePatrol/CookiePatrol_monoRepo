package com.cookiepatrol

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.net.toUri
import com.cookiepatrol.monitor.NotificationMonitor
import com.cookiepatrol.monitor.ScreenMonitor
import com.cookiepatrol.ui.CookiePatrolTheme
import com.cookiepatrol.ui.HomeScreen
import com.cookiepatrol.ui.PatrolSymbol
import com.cookiepatrol.ui.ProtectionSetting
import com.cookiepatrol.ui.PermissionGuides
import com.cookiepatrol.detect.PrivacySettings
import com.cookiepatrol.monitor.MonitorStatus
import com.cookiepatrol.warn.Warning
import android.widget.Toast
import androidx.compose.runtime.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The starting window owns the logo; restore the app theme before drawing home.
        setTheme(R.style.Theme_CookiePatrol)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
    }

    // 설정에서 돌아올 때마다 실제 권한 상태를 다시 읽는다.
    override fun onResume() {
        super.onResume()
        val nm = getSystemService(NotificationManager::class.java)
        val listenerEnabled = nm.isNotificationListenerAccessGranted(ComponentName(this, NotificationMonitor::class.java))
        val settings = listOf(
            ProtectionSetting("위험 경고 팝업", null, PatrolSymbol.BELL,
                Warning.canShowOnScreen(this), PermissionGuides.warnings) {
                startActivity(Warning.settingsIntent(this))
            },
            ProtectionSetting("문자·카톡 알림 감시", null, PatrolSymbol.MESSAGE,
                listenerEnabled, PermissionGuides.messages) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
            ProtectionSetting("다운로드 감시", if (listenerEnabled) null else "문자·카톡 감시가 필요해요", PatrolSymbol.DOWNLOAD,
                Environment.isExternalStorageManager() && listenerEnabled, PermissionGuides.downloads) {
                if (!listenerEnabled) startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                else startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    "package:$packageName".toUri()))
            },
        )
        val screenComponent = ComponentName(this, ScreenMonitor::class.java)
        val screenEnabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.split(':')?.any { ComponentName.unflattenFromString(it) == screenComponent } == true
        setContent {
            CookiePatrolTheme {
                var remoteScope by remember { mutableStateOf(PrivacySettings.scope(this)) }
                HomeScreen(settings, screenEnabled,
                    monitorConnected = MonitorStatus.connected,
                    extraMonitorsConnected = MonitorStatus.installMonitoringReady && MonitorStatus.downloadMonitoringReady,
                    remoteScope = remoteScope,
                    changeRemoteConsent = { enabled ->
                        PrivacySettings.setRemoteEnabled(this, enabled)
                        remoteScope = PrivacySettings.scope(this)
                    },
                    openManualCheck = { startActivity(Intent(this, ManualCheckActivity::class.java)) },
                    openScoreGuide = { startActivity(Intent(this, ScoreGuideActivity::class.java)) },
                    openFieldTrial = if (BuildConfig.DEBUG) ({
                    startActivity(Intent().setClassName(this, "$packageName.evaluation.FieldTrialActivity"))
                }) else null) {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
        }
    }

    override fun startActivity(intent: Intent) {
        runCatching { super.startActivity(intent) }.onFailure {
            Toast.makeText(this, "이 기기에서는 해당 설정을 바로 열 수 없어요. 휴대폰 설정에서 Cookie Patrol을 찾아주세요.", Toast.LENGTH_LONG).show()
        }
    }
}
