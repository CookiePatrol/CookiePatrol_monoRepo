package com.cookiepatrol.monitor

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification
        if (sbn.packageName == packageName || !NotificationFilter.shouldInspect(sbn.packageName, notification.category)) return
        val e = notification.extras
        val text = listOfNotNull(
            e.getCharSequence(Notification.EXTRA_TITLE),
            e.getCharSequence(Notification.EXTRA_TEXT),
            e.getCharSequence(Notification.EXTRA_BIG_TEXT),
            e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n"),
        ).joinToString("\n")
        Detector.inspect(this, text)
    }

    private val installReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
            val pkg = intent.data?.schemeSpecificPart ?: return
            when {
                pkg in REMOTE_CONTROL_APPS -> Warning.show(
                    context,
                    "원격제어 앱이 설치됐어요. 다른 사람이 휴대폰을 마음대로 조작할 수 있어요.",
                    "모르는 사람이 설치하라고 했다면 지금 바로 지우세요.",
                )
                runCatching { packageManager.getInstallSourceInfo(pkg).installingPackageName }
                    .getOrNull() != "com.android.vending" -> Warning.show(
                    context,
                    "플레이스토어가 아닌 곳에서 앱이 설치됐어요.",
                    "직접 설치한 게 아니라면 지금 바로 지우세요.",
                )
            }
        }
    }

    private val warnedDownloads = HashSet<String>()

    private val downloadObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            if (uri?.lastPathSegment?.toLongOrNull() == null) return // 개별 파일 변경만 본다
            val name = runCatching {
                contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null }
            }.getOrNull() ?: return
            if (name.endsWith(".apk", ignoreCase = true) && warnedDownloads.add(uri.toString())) {
                Warning.show(
                    this@NotificationMonitor,
                    "앱 설치 파일(APK)이 다운로드됐어요.",
                    "직접 받은 게 아니라면 열지 말고 지우세요.",
                )
            }
        }
    }

    override fun onListenerConnected() {
        if (!installReceiverRegistered) {
            val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(installReceiver, filter, android.content.Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(installReceiver, filter)
            }
            installReceiverRegistered = true
        }
        if (!downloadObserverRegistered) {
            contentResolver.registerContentObserver(MediaStore.Downloads.EXTERNAL_CONTENT_URI, true, downloadObserver)
            downloadObserverRegistered = true
        }
    }

    override fun onListenerDisconnected() {
        unregisterMonitors()
    }

    override fun onDestroy() {
        unregisterMonitors()
        super.onDestroy()
    }

    private fun unregisterMonitors() {
        if (installReceiverRegistered) {
            runCatching { unregisterReceiver(installReceiver) }
            installReceiverRegistered = false
        }
        if (downloadObserverRegistered) {
            runCatching { contentResolver.unregisterContentObserver(downloadObserver) }
            downloadObserverRegistered = false
        }
    }
}
