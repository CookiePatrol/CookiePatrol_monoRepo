package com.cookiepatrol.warn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import com.cookiepatrol.monitor.fingerprint
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
    private val visibleTags = LinkedHashSet<String>()
    private val history = WarningHistory()

    fun canNotify(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java)
        return nm.areNotificationsEnabled() && nm.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun show(context: Context, reason: String, action: String = DEFAULT_ACTION,
             eventKey: String = fingerprint(reason), priority: WarningPriority = WarningPriority.LOCAL): Boolean = runCatching {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!canNotify(context)) return@runCatching false
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "위험 경고", NotificationManager.IMPORTANCE_HIGH))
        if (nm.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return@runCatching false
        val tag = fingerprint(eventKey)
        history.publish(tag, priority) {
            val intent = Intent(context, WarningActivity::class.java)
                .setData("cookiepatrol://warning/$tag".toUri())
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
            synchronized(visibleTags) {
                visibleTags.add(tag)
                while (visibleTags.size > 10) {
                    val oldest = visibleTags.first(); visibleTags.remove(oldest); nm.cancel(oldest, NOTIFICATION_ID)
                }
                nm.notify(tag, NOTIFICATION_ID, notification)
            }
        }
    }.getOrDefault(false)
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
