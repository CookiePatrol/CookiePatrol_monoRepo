package com.cookiepatrol.warn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import androidx.core.net.toUri
import com.cookiepatrol.monitor.fingerprint
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.NotificationCompat
import com.cookiepatrol.ui.CookiePatrolTheme
import com.cookiepatrol.ui.WarningScreen
import java.util.Date

private const val CONCLUSION = "사기(피싱)일 수 있어요"
private const val DEFAULT_ACTION = "누르지 말고, 돈·개인정보·인증번호를 보내지 마세요. 걱정되면 112에 전화하세요."
private const val MESSAGE_ACTION = "링크를 열거나 답장하지 마세요. 송금하거나 인증번호를 보내기 전에 공식 앱이나 평소 쓰던 번호로 확인하세요."

/** Show the warning as a notification and open its details on tap. */
object Warning {
    private const val CHANNEL = "warning"
    private const val NOTIFICATION_ID = 1001
    private val visibleTags = LinkedHashSet<String>()
    private val history = WarningHistory()
    private val messageHistory = WarningHistory(ttlMillis = 24 * 60 * 60_000L, maxEntries = 2_048)

    fun canNotify(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java)
        return nm.areNotificationsEnabled() && nm.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    internal fun show(
        context: Context,
        reason: String,
        action: String = DEFAULT_ACTION,
        eventKey: String = fingerprint(reason),
        priority: WarningPriority = WarningPriority.LOCAL,
        message: WarningMessage? = null,
        linkHost: String? = null,
        additionalReason: String? = null,
    ): Boolean = runCatching {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!canNotify(context)) return@runCatching false
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "위험 경고", NotificationManager.IMPORTANCE_HIGH))
        if (nm.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return@runCatching false
        val tag = fingerprint(eventKey)
        val warningHistory = if (message == null) history else messageHistory
        warningHistory.publish(tag, priority) { updating ->
            val title = if (message == null) CONCLUSION else "사기 의심 메시지"
            val sender = message?.sender
            val receivedAt = message?.timeMillis?.takeIf { it > 0 }?.let {
                val date = Date(it)
                "${DateFormat.getDateFormat(context).format(date)} ${DateFormat.getTimeFormat(context).format(date)}"
            }
            val intent = Intent(context, WarningActivity::class.java)
                .setData("cookiepatrol://warning/$tag".toUri())
                .putExtra("reason", reason)
                .putExtra("action", if (message == null) action else MESSAGE_ACTION)
                .putExtra("sender", sender)
                .putExtra("received_at", receivedAt)
                .putExtra("message_preview", message?.preview)
                .putExtra("link_host", linkHost)
                .putExtra("additional_reason", additionalReason)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pending = PendingIntent.getActivity(
                context, NOTIFICATION_ID, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            // Keep the detail intent up to date even if the first post is still reaching System UI.
            // Never post an upgrade to a notification that has already been cleared.
            if (updating && nm.activeNotifications.none { it.tag == tag && it.id == NOTIFICATION_ID }) {
                return@publish
            }
            val expandedText = listOfNotNull(
                sender?.let { "보낸 사람·대화  $it" },
                receivedAt?.let { "메시지 알림 시각  $it" },
                reason,
                linkHost?.let { "의심 링크 주소  $it" },
            ).joinToString("\n")
            val publicNotification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(title)
                .setContentText("잠금을 해제해 경고 내용을 확인하세요.")
                .build()
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(sender?.let { "$title · $it" } ?: title)
                .setContentText(linkHost?.let { "$reason ($it)" } ?: reason)
                .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicNotification)
                .setOnlyAlertOnce(true)
                .setSilent(updating)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            synchronized(visibleTags) {
                nm.notify(tag, NOTIFICATION_ID, notification)
                visibleTags.add(tag)
                while (visibleTags.size > 10) {
                    val oldest = visibleTags.first(); visibleTags.remove(oldest); nm.cancel(oldest, NOTIFICATION_ID)
                }
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
        val preview = intent.getStringExtra("message_preview")
        setContent {
            CookiePatrolTheme {
                WarningScreen(
                    reason = reason,
                    action = action,
                    onDismiss = ::finish,
                    messagePreview = preview,
                    sender = intent.getStringExtra("sender"),
                    receivedAt = intent.getStringExtra("received_at"),
                    linkHost = intent.getStringExtra("link_host"),
                    additionalReason = intent.getStringExtra("additional_reason"),
                )
            }
        }
    }
}
