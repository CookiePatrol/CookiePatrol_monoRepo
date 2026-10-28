package com.cookiepatrol.monitor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.cookiepatrol.detect.Detector
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal fun combineScreenText(
    accessibleText: String,
    recognizedText: String,
    maxChars: Int = 12_000,
): String {
    if (maxChars <= 0) return ""
    if (recognizedText.isBlank()) return accessibleText.take(maxChars)

    val accessiblePart = accessibleText.take(maxChars / 2)
    val remaining = maxChars - accessiblePart.length - if (accessiblePart.isEmpty()) 0 else 1
    val recognizedPart = recognizedText.take(remaining.coerceAtLeast(0))
    return buildString(accessiblePart.length + recognizedPart.length + 1) {
        append(accessiblePart)
        if (accessiblePart.isNotEmpty() && recognizedPart.isNotEmpty()) append('\n')
        append(recognizedPart)
    }
}

/** Demo-only screen reading: accessibility text plus on-device OCR of the visible screen. */
class ScreenMonitor : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ocrExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "cookiepatrol-screen-ocr").apply { isDaemon = true }
    }
    private val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    private val scanInProgress = AtomicBoolean(false)
    private val scanAgain = AtomicBoolean(false)
    private val destroyed = AtomicBoolean(false)
    private val recognizerClosed = AtomicBoolean(false)
    private var lastScreenshotAt = 0L

    private val scanRunnable = Runnable { scanActiveWindow() }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName == packageName) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED
        ) return

        mainHandler.removeCallbacks(scanRunnable)
        mainHandler.postDelayed(scanRunnable, SCAN_DELAY_MS)
    }

    private fun scanActiveWindow() {
        if (!scanInProgress.compareAndSet(false, true)) {
            scanAgain.set(true)
            return
        }

        val now = SystemClock.elapsedRealtime()
        val delay = MIN_SCREENSHOT_INTERVAL_MS - (now - lastScreenshotAt)
        if (delay > 0) {
            scanInProgress.set(false)
            mainHandler.removeCallbacks(scanRunnable)
            mainHandler.postDelayed(scanRunnable, delay)
            return
        }

        val root = runCatching { rootInActiveWindow }.getOrNull()
        if (root == null) {
            finishScan()
            return
        }
        if (root.packageName == packageName) {
            recycleNode(root)
            finishScan()
            return
        }

        val windowId = root.windowId
        lastScreenshotAt = now
        val accessibleText = StringBuilder()
        try {
            collect(root, accessibleText, HashSet(), intArrayOf(0))
        } catch (_: RuntimeException) {
            finishScan()
            return
        }
        val textSnapshot = accessibleText.toString()

        val callback = object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                if (destroyed.get()) {
                    screenshot.hardwareBuffer.close()
                    finishScan()
                    return
                }
                recognizeScreenshot(screenshot, textSnapshot)
            }

            override fun onFailure(errorCode: Int) {
                try {
                    inspectText(textSnapshot)
                } finally {
                    finishScan()
                }
            }
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                takeScreenshotOfWindow(windowId, ocrExecutor, callback)
            } else {
                takeScreenshot(Display.DEFAULT_DISPLAY, ocrExecutor, callback)
            }
        } catch (_: RuntimeException) {
            try {
                inspectText(textSnapshot)
            } finally {
                finishScan()
            }
        }
    }

    private fun recognizeScreenshot(screenshot: ScreenshotResult, accessibleText: String) {
        val buffer = screenshot.hardwareBuffer
        val bitmap = try {
            Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                ?.copy(Bitmap.Config.ARGB_8888, false)
        } catch (_: RuntimeException) {
            null
        } finally {
            buffer.close()
        }

        if (bitmap == null) {
            try {
                inspectText(accessibleText)
            } finally {
                finishScan()
            }
            return
        }

        val task = runCatching { recognizer.process(InputImage.fromBitmap(bitmap, 0)) }.getOrNull()
        if (task == null) {
            try {
                inspectText(accessibleText)
            } finally {
                bitmap.recycle()
                finishScan()
            }
            return
        }

        task.addOnCompleteListener(ocrExecutor) { result ->
            try {
                val recognizedText = if (result.isSuccessful) result.result?.text.orEmpty() else ""
                inspectText(accessibleText, recognizedText)
            } finally {
                bitmap.recycle()
                finishScan()
            }
        }
    }

    private fun inspectText(accessibleText: String, recognizedText: String = "") {
        if (destroyed.get()) return
        val combined = combineScreenText(accessibleText, recognizedText, MAX_SCREEN_TEXT_CHARS)
        if (combined.isNotBlank()) Detector.inspect(this, combined)
    }

    private fun finishScan() {
        scanInProgress.set(false)
        if (destroyed.get()) {
            closeRecognizer()
            ocrExecutor.shutdown()
            return
        }
        if (scanAgain.getAndSet(false)) {
            mainHandler.removeCallbacks(scanRunnable)
            mainHandler.postDelayed(scanRunnable, SCAN_DELAY_MS)
        }
    }

    private fun collect(
        node: AccessibilityNodeInfo?,
        out: StringBuilder,
        seen: MutableSet<String>,
        visited: IntArray,
    ) {
        if (node == null) return
        try {
            if (out.length >= MAX_SCREEN_TEXT_CHARS || visited[0] >= MAX_NODES_PER_SCAN) return
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
        } finally {
            recycleNode(node)
        }
    }

    @Suppress("DEPRECATION")
    private fun recycleNode(node: AccessibilityNodeInfo) {
        if (android.os.Build.VERSION.SDK_INT < 33) node.recycle()
    }

    private fun closeRecognizer() {
        if (recognizerClosed.compareAndSet(false, true)) recognizer.close()
    }

    override fun onInterrupt() {
        mainHandler.removeCallbacks(scanRunnable)
        scanAgain.set(false)
    }

    override fun onDestroy() {
        destroyed.set(true)
        mainHandler.removeCallbacks(scanRunnable)
        scanAgain.set(false)
        if (!scanInProgress.get()) {
            closeRecognizer()
            ocrExecutor.shutdown()
        }
        super.onDestroy()
    }

    private companion object {
        const val SCAN_DELAY_MS = 350L
        const val MIN_SCREENSHOT_INTERVAL_MS = 1_000L
        const val MAX_SCREEN_TEXT_CHARS = 12_000
        const val MAX_NODES_PER_SCAN = 500
    }
}
