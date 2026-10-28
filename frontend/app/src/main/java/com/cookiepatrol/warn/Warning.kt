package com.cookiepatrol.warn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cookiepatrol.ui.CookiePatrolTheme
import com.cookiepatrol.ui.WarningScreen

private const val CONCLUSION = "사기(피싱)일 수 있어요"
private const val DEFAULT_ACTION = "누르지 말고, 돈·개인정보·인증번호를 보내지 마세요. 걱정되면 112에 전화하세요."

/** 경고문 = 결론 1줄 + 이유 1줄 + 할 일 1줄. 알림으로 띄우고, 누르면 큰 글씨 화면. */
object Warning {
    private const val CHANNEL = "warning"
    private const val NOTIFICATION_ID = 1001

    fun show(context: Context, reason: String, action: String = DEFAULT_ACTION) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "위험 경고", NotificationManager.IMPORTANCE_HIGH))
        val intent = Intent(context, WarningActivity::class.java)
            .putExtra("reason", reason)
            .putExtra("action", action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            context, NOTIFICATION_ID, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(CONCLUSION)
            .setContentText(reason)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }
}

/** 강제로 막지 않는다: 확인 버튼은 화면만 닫는다. */
class WarningActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val reason = intent.getStringExtra("reason")?.takeIf { it.isNotBlank() }
            ?: "의심스러운 내용이 발견됐어요."
        val action = intent.getStringExtra("action")?.takeIf { it.isNotBlank() } ?: DEFAULT_ACTION
        setContent {
            CookiePatrolTheme {
                WarningScreen(reason = reason, action = action, onDismiss = ::finish)
            }
        }
    }
}
