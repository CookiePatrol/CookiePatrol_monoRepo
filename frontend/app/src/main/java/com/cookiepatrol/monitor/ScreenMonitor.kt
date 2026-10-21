package com.cookiepatrol.monitor

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.cookiepatrol.detect.Detector

/**
 * 데모 전용: 접근성 서비스로 다른 앱 화면의 글자·링크·입력란을 읽는다.
 * 플레이스토어 심사에서 막히므로 출시 버전에서는 NotificationMonitor만 남길 것.
 */
class ScreenMonitor : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName == packageName) return
        val out = StringBuilder()
        collect(rootInActiveWindow, out)
        Detector.inspect(this, out.toString())
    }

    private fun collect(node: AccessibilityNodeInfo?, out: StringBuilder) {
        node ?: return
        node.text?.let { out.appendLine(it) }
        node.hintText?.let { out.appendLine(it) } // 입력란 안내문, 예: "주민등록번호 13자리"
        node.contentDescription?.let { out.appendLine(it) }
        for (i in 0 until node.childCount) collect(node.getChild(i), out)
    }

    override fun onInterrupt() {}
}
