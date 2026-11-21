package com.cookiepatrol.monitor

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ComponentName
import android.app.NotificationManager
import android.os.Environment
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import android.os.Build
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.cookiepatrol.detect.Detector
import com.cookiepatrol.warn.Warning
import com.cookiepatrol.warn.WarningMessage
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

private val REMOTE_CONTROL_APPS = setOf(
    "com.teamviewer.quicksupport.market",
    "com.anydesk.anydeskandroid",
    "com.rsupport.mobizen.cn",
    "com.rsupport.remotemeeting.application",
)

/**
 * 문자·카톡 알림 감시. 시스템이 계속 살려두는 서비스라서
 * 앱 설치·다운로드 감시도 여기에 붙인다(오레오부터 manifest로는 설치 브로드캐스트를 못 받음).
 */
class NotificationMonitor : NotificationListenerService() {

    private var installReceiverRegistered = false
    private var downloadObserverRegistered = false
    @Volatile private var listening = false
    private val downloads = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(32),
        { task -> Thread(task, "cookiepatrol-downloads").apply { isDaemon = true } })

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification
        if (sbn.packageName == packageName || !NotificationFilter.shouldInspect(sbn.packageName, notification.category)) return
        // Apps may change extras formats; malformed bundles cannot stop the listener.
        val content = runCatching {
            val e = notification.extras
            NotificationContent(
                title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                text = e.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
                bigText = e.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty(),
                lines = e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.map { it.toString() }.orEmpty(),
                messages = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
                    ?.messages?.map {
                        MessagePart(it.text?.toString().orEmpty(), it.timestamp, it.person?.name?.toString())
                    }.orEmpty(),
                groupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            )
        }.getOrNull() ?: return
        val parts = NotificationText.parts(content)
        if (parts.isEmpty()) return
        val conversation = notification.shortcutId?.takeIf { it.isNotBlank() }?.let {
            "${sbn.user.hashCode()}\u0000${sbn.packageName}\u0000$it"
        } ?: sbn.key
        messages.update(sbn.key, conversation, parts).forEach { (key, part) ->
            val sender = part.sender ?: content.title.takeUnless { it == part.text }
            val subject = WarningMessage.from(
                sender,
                part.text,
                part.timestamp.takeIf { it > 0 } ?: sbn.postTime,
                demoRunId = DemoReceipt.runId(sbn.packageName, sender),
            )
            Detector.inspect(this, part.text, key, subject)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        messages.remove(sbn.key)
    }

    private val installReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
            val pkg = intent.data?.schemeSpecificPart ?: return
            val key = "install\u0000$pkg\u0000${SystemClock.elapsedRealtime()}"
            when {
                pkg in REMOTE_CONTROL_APPS -> Warning.show(
                    context,
                    "원격제어 앱이 설치됐어요. 다른 사람이 휴대폰을 마음대로 조작할 수 있어요.",
                    "모르는 사람이 설치하라고 했다면 지금 바로 지우세요.",
                    eventKey = key,
                )
                runCatching { packageManager.getInstallSourceInfo(pkg).installingPackageName }
                    .getOrNull() != "com.android.vending" -> Warning.show(
                    context,
                    "플레이스토어가 아닌 곳에서 앱이 설치됐어요.",
                    "직접 설치한 게 아니라면 지금 바로 지우세요.",
                    eventKey = key,
                )
            }
        }
    }

    private val warnedDownloads = EventDeduplicator(ttlMillis = 24 * 60 * 60_000L)

    private val downloadObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            if (uri?.lastPathSegment?.toLongOrNull() == null) return // 개별 파일 변경만 본다
            if (!Environment.isExternalStorageManager() || !listening) return
            try {
                downloads.execute {
                    if (!listening) return@execute
                    val name = runCatching {
                        contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                            ?.use { if (it.moveToFirst()) it.getString(0) else null }
                    }.getOrNull() ?: return@execute
                    val key = fingerprint(uri.toString())
                    if (listening && name.endsWith(".apk", ignoreCase = true) && warnedDownloads.admit(key)) {
                        if (!Warning.show(this@NotificationMonitor, "앱 설치 파일(APK)이 다운로드됐어요.",
                            "직접 받은 게 아니라면 열지 말고 지우세요.", eventKey = key)) warnedDownloads.forget(key)
                    }
                }
            } catch (_: RejectedExecutionException) {
                // Bounded background work; no file names or content are logged.
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        listening = true
        MonitorStatus.connected = true
        if (!installReceiverRegistered) {
            val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") }
            installReceiverRegistered = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(installReceiver, filter, android.content.Context.RECEIVER_EXPORTED)
                } else {
                    @Suppress("DEPRECATION")
                    registerReceiver(installReceiver, filter)
                }
                true
            }.getOrDefault(false)
        }
        if (!downloadObserverRegistered) {
            downloadObserverRegistered = runCatching {
                contentResolver.registerContentObserver(MediaStore.Downloads.EXTERNAL_CONTENT_URI, true, downloadObserver)
                true
            }.getOrDefault(false)
        }
        MonitorStatus.installMonitoringReady = installReceiverRegistered
        MonitorStatus.downloadMonitoringReady = downloadObserverRegistered
    }

    override fun onListenerDisconnected() {
        listening = false
        MonitorStatus.connected = false
        unregisterMonitors()
        // Rebinding is permitted while disconnected. A revoked permission is never restored here.
        if (getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(ComponentName(this, javaClass))) {
            runCatching { requestRebind(ComponentName(this, javaClass)) }
        }
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        listening = false
        downloads.shutdownNow()
        MonitorStatus.connected = false
        unregisterMonitors()
        super.onDestroy()
    }

    private fun unregisterMonitors() {
        MonitorStatus.installMonitoringReady = false
        MonitorStatus.downloadMonitoringReady = false
        if (installReceiverRegistered) {
            runCatching { unregisterReceiver(installReceiver) }
            installReceiverRegistered = false
        }
        if (downloadObserverRegistered) {
            runCatching { contentResolver.unregisterContentObserver(downloadObserver) }
            downloadObserverRegistered = false
        }
    }

    private companion object {
        val messages = NotificationMessageTracker()
    }
}

object MonitorStatus {
    var connected: Boolean by mutableStateOf(false)
        internal set
    var installMonitoringReady: Boolean by mutableStateOf(false)
        internal set
    var downloadMonitoringReady: Boolean by mutableStateOf(false)
        internal set
}
