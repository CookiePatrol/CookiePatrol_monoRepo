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
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return
        if (root.packageName == packageName) return

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

        lastScreenshotAt = now
        val accessibleText = StringBuilder()
        collect(root, accessibleText, HashSet(), intArrayOf(0))
        val textSnapshot = accessibleText.toString()

        val callback = object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                recognizeScreenshot(screenshot, textSnapshot)
            }

            override fun onFailure(errorCode: Int) {
                inspectText(textSnapshot)
                finishScan()
            }
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                takeScreenshotOfWindow(root.windowId, ocrExecutor, callback)
            } else {
                takeScreenshot(Display.DEFAULT_DISPLAY, ocrExecutor, callback)
            }
        } catch (_: RuntimeException) {
            inspectText(textSnapshot)
            finishScan()
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
            inspectText(accessibleText)
            finishScan()
            return
        }

        val task = runCatching { recognizer.process(InputImage.fromBitmap(bitmap, 0)) }.getOrNull()
        if (task == null) {
            bitmap.recycle()
            inspectText(accessibleText)
            finishScan()
            return
        }

        task.addOnCompleteListener(ocrExecutor) { result ->
            val recognizedText = if (result.isSuccessful) result.result?.text.orEmpty() else ""
            inspectText(accessibleText, recognizedText)
            bitmap.recycle()
            finishScan()
        }
    }

    private fun inspectText(accessibleText: String, recognizedText: String = "") {
        val combined = buildString {
            append(accessibleText)
            if (recognizedText.isNotBlank() && length < MAX_SCREEN_TEXT_CHARS) {
                append('\n').append(recognizedText.take(MAX_SCREEN_TEXT_CHARS - length))
            }
        }
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
