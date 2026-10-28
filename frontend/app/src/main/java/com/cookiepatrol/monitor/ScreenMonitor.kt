package com.cookiepatrol.monitor

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.cookiepatrol.detect.Detector

/**
 * 데모 전용: 접근성 서비스로 다른 앱 화면의 글자·링크·입력란을 읽는다.
 * 플레이스토어 심사에서 막히므로 출시 버전에서는 NotificationMonitor만 남길 것.
 */
class ScreenMonitor : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scanRunnable = Runnable {
        val out = StringBuilder()
        val root = runCatching { rootInActiveWindow }.getOrNull()
        if (root?.packageName != packageName) {
            collect(root, out, HashSet(), intArrayOf(0))
            Detector.inspect(this, out.toString())
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName == packageName) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED
        ) return
        // 화면이 연속 갱신될 때 마지막 화면만 한 번 읽어 중복 경고와 반복 순회를 줄인다.
        mainHandler.removeCallbacks(scanRunnable)
        mainHandler.postDelayed(scanRunnable, SCAN_DELAY_MS)
    }

    private fun collect(
        node: AccessibilityNodeInfo?,
        out: StringBuilder,
        seen: MutableSet<String>,
        visited: IntArray,
    ) {
        if (node == null || out.length >= MAX_SCREEN_TEXT_CHARS || visited[0] >= MAX_NODES_PER_SCAN) return
        visited[0]++
        listOfNotNull(node.text, node.hintText, node.contentDescription)
            .map { it.toString() }
            .filter { it.isNotBlank() && seen.add(it) }
            .forEach { value ->
                if (out.length < MAX_SCREEN_TEXT_CHARS) {
                    out.append(value.take(MAX_SCREEN_TEXT_CHARS - out.length)).append('\n')
                }
            }
        for (i in 0 until node.childCount) {
            if (out.length >= MAX_SCREEN_TEXT_CHARS || visited[0] >= MAX_NODES_PER_SCAN) break
            collect(node.getChild(i), out, seen, visited)
        }
    }

    override fun onInterrupt() {
        mainHandler.removeCallbacks(scanRunnable)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(scanRunnable)
        super.onDestroy()
    }

    private companion object {
        const val SCAN_DELAY_MS = 350L
        const val MAX_SCREEN_TEXT_CHARS = 12_000
        const val MAX_NODES_PER_SCAN = 500
    }
}
