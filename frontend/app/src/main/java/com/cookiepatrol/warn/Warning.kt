package com.cookiepatrol.warn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import androidx.core.net.toUri
import com.cookiepatrol.monitor.DemoReceipt
import com.cookiepatrol.monitor.fingerprint
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.app.NotificationCompat
import com.cookiepatrol.ui.CookiePatrolTheme
import com.cookiepatrol.ui.WarningScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Date

private const val CONCLUSION = "사기(피싱)일 수 있어요"
private const val DEFAULT_ACTION = "누르지 말고, 돈·개인정보·인증번호를 보내지 마세요. 걱정되면 112에 전화하세요."
private const val MESSAGE_ACTION = "링크를 열거나 답장하지 마세요. 송금하거나 인증번호를 보내기 전에 공식 앱이나 평소 쓰던 번호로 확인하세요."

internal enum class LinkRiskLevel(val stage: Int) {
    NOT_DETECTED(1), SUSPICIOUS(2), BLOCKLIST_MATCH(3);

    val label: String get() = "${stage}단계"
}

internal data class WarningPresentation(
    val reason: String,
    val action: String,
    val linkHost: String?,
    val stage: String?,
    val assessment: WarningAssessment?,
)

/** Post urgent warnings and quiet message-link results to separate channels. */
object Warning {
    private const val CHANNEL = "warning"
    private const val LINK_RESULT_CHANNEL = "message_link_result"
    private const val NOTIFICATION_ID = 1001
    private const val LINK_RESULT_ID = 1002
    private const val VISIBLE_LIMIT = 10
    private val visibleWarnings = LinkedHashSet<String>()
    private val visibleResults = LinkedHashSet<String>()
    private val history = WarningHistory()
    private val messageHistory = WarningHistory(ttlMillis = 24 * 60 * 60_000L, maxEntries = 2_048)
    private val recentMessages = MutableStateFlow<Map<String, WarningPresentation>>(emptyMap())
    internal val messagePresentations = recentMessages.asStateFlow()

    fun canNotify(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    internal fun canShowOnScreen(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        val importance = manager.getNotificationChannel(CHANNEL)?.importance ?: NotificationManager.IMPORTANCE_HIGH
        return manager.areNotificationsEnabled() && importance >= NotificationManager.IMPORTANCE_HIGH
    }

    internal fun settingsIntent(context: Context): Intent {
        val manager = context.getSystemService(NotificationManager::class.java)
        val settings = if (manager.areNotificationsEnabled()) {
            createWarningChannel(manager)
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL)
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        }
        return settings.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }

    private fun createWarningChannel(manager: NotificationManager) {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "위험 경고", NotificationManager.IMPORTANCE_HIGH))
    }

    internal fun show(
        context: Context,
        reason: String,
        action: String? = null,
        eventKey: String = fingerprint(reason),
        priority: WarningPriority = WarningPriority.LOCAL,
        message: WarningMessage? = null,
        linkHost: String? = null,
        assessment: WarningAssessment? = null,
    ): Boolean = runCatching {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return@runCatching false
        createWarningChannel(nm)
        if (nm.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return@runCatching false
        val tag = fingerprint(eventKey)
        val warningHistory = if (message == null) history else messageHistory
        val level = if (message == null) null else when (priority) {
            WarningPriority.UNAVAILABLE -> null
            WarningPriority.LOCAL, WarningPriority.SERVER, WarningPriority.AI -> LinkRiskLevel.SUSPICIOUS
            WarningPriority.GOOGLE -> LinkRiskLevel.BLOCKLIST_MATCH
        }
        warningHistory.publish(tag, priority) { updating ->
            val title = level?.let { "${it.label} · 사기 의심 메시지" } ?: CONCLUSION
            val responseAdvice = action ?: if (message == null) DEFAULT_ACTION else MESSAGE_ACTION
            val sender = message?.sender
            val receivedAt = message?.timeMillis?.takeIf { it > 0 }?.let {
                val date = Date(it)
                "${DateFormat.getDateFormat(context).format(date)} ${DateFormat.getTimeFormat(context).format(date)}"
            }
            val intent = Intent(context, WarningActivity::class.java)
                .setData("cookiepatrol://warning/$tag".toUri())
                .putExtra("reason", reason)
                .putExtra("action", responseAdvice)
                .putExtra("sender", sender)
                .putExtra("received_at", receivedAt)
                .putExtra("message_preview", message?.preview)
                .putExtra("link_host", linkHost)
                .putExtra("risk_stage", level?.label)
                .putExtra("assessment", assessment?.toBundle())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pending = PendingIntent.getActivity(
                context, NOTIFICATION_ID, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            if (message != null) {
                val presentation = WarningPresentation(reason, responseAdvice, linkHost, level?.label, assessment)
                recentMessages.update { current ->
                    (current - tag + (tag to presentation)).toList().takeLast(VISIBLE_LIMIT).toMap()
                }
            }
            // A reader may already have opened or dismissed this warning.
            // Never post an upgrade to a notification that has already been cleared.
            if (updating && nm.activeNotifications.none { it.tag == tag && it.id == NOTIFICATION_ID }) {
                // The existing detail still receives the updated stage under the same tag.
                if (level != null) DemoReceipt.warning(message?.demoRunId, level.stage, tag)
                return@publish
            }
            val evidence = assessment?.findings?.sortedBy { it.category }.orEmpty()
            val categories = evidence.map { it.category.warningLabel }.distinct()
            val score = assessment?.let { "기기 검사 ${it.score}점" }
            val headline = if (categories.isEmpty()) reason else (categories + listOfNotNull(score)).joinToString(" · ")
            val expandedLines = listOfNotNull(
                sender?.let { "보낸 사람·대화  $it" },
                receivedAt?.let { "메시지 알림 시각  $it" },
                score,
                linkHost?.let { "의심 링크 주소  $it" },
            ) + evidence.map { "${it.category.warningLabel} · ${it.label}\n${it.reason}" }.ifEmpty { listOf(reason) }
            val publicNotification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(title)
                .setContentText("잠금을 해제해 경고 내용을 확인하세요.")
                .build()
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(sender?.let { "$title · $it" } ?: title)
                .setContentText(headline)
                .setStyle(NotificationCompat.BigTextStyle().bigText(expandedLines.joinToString("\n")))
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicNotification)
                .setOnlyAlertOnce(true)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .apply {
                    if (level != null) addAction(android.R.drawable.ic_menu_info_details, "경고 이유 보기", pending)
                }
                .build()
            post(nm, tag, NOTIFICATION_ID, notification, visibleWarnings)
            if (level != null) DemoReceipt.warning(message?.demoRunId, level.stage, tag)
        }
    }.getOrDefault(false)

    internal fun showLinkResult(context: Context, eventKey: String, message: WarningMessage, completed: Boolean) {
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (!nm.areNotificationsEnabled()) return@runCatching
            nm.createNotificationChannel(NotificationChannel(LINK_RESULT_CHANNEL, "문자 링크 검사 결과", NotificationManager.IMPORTANCE_LOW))
            if (nm.getNotificationChannel(LINK_RESULT_CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) {
                return@runCatching
            }
            val title = if (completed) "${LinkRiskLevel.NOT_DETECTED.label} · 링크 위험 신호 미감지" else "링크 검사 확인 불가"
            val detail = if (completed) "메시지의 링크에서 위험 신호를 찾지 못했어요." else "메시지에 있는 링크 검사를 모두 완료하지 못했어요."
            val text = listOfNotNull(message.sender, detail).joinToString(" · ")
            val publicNotification = NotificationCompat.Builder(context, LINK_RESULT_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("문자 링크 검사 결과")
                .build()
            val notification = NotificationCompat.Builder(context, LINK_RESULT_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicNotification)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .build()
            post(nm, fingerprint(eventKey), LINK_RESULT_ID, notification, visibleResults)
        }
    }

    private fun post(
        manager: NotificationManager,
        tag: String,
        id: Int,
        notification: Notification,
        visibleTags: LinkedHashSet<String>,
    ) = synchronized(visibleTags) {
        manager.notify(tag, id, notification)
        visibleTags.add(tag)
        while (visibleTags.size > VISIBLE_LIMIT) {
            val oldest = visibleTags.first()
            visibleTags.remove(oldest)
            manager.cancel(oldest, id)
        }
    }
}

/** 강제로 막지 않는다: 확인 버튼은 화면만 닫는다. */
class WarningActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val tag = intent.data?.lastPathSegment
        val initial = WarningPresentation(
            intent.getStringExtra("reason")?.takeIf { it.isNotBlank() } ?: "의심스러운 내용이 발견됐어요.",
            intent.getStringExtra("action")?.takeIf { it.isNotBlank() } ?: DEFAULT_ACTION,
            intent.getStringExtra("link_host"),
            intent.getStringExtra("risk_stage"),
            WarningAssessment.fromBundle(intent.getBundleExtra("assessment")),
        )
        setContent {
            CookiePatrolTheme {
                val messages by Warning.messagePresentations.collectAsState()
                val presentation = messages[tag] ?: initial
                WarningScreen(
                    reason = presentation.reason,
                    action = presentation.action,
                    onDismiss = ::finish,
                    messagePreview = intent.getStringExtra("message_preview"),
                    sender = intent.getStringExtra("sender"),
                    receivedAt = intent.getStringExtra("received_at"),
                    linkHost = presentation.linkHost,
                    additionalReason = intent.getStringExtra("additional_reason"),
                    riskStage = presentation.stage,
                    assessment = presentation.assessment,
                )
            }
        }
    }
}
