package com.cookiepatrol.monitor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.cookiepatrol.detect.Detector
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private val EXCLUDED_SCREEN_PACKAGES = setOf("com.android.systemui", "com.cookiepatrol.demosender")

internal fun screenScanPackage(source: CharSequence?, appPackage: String): String? =
    source?.toString()?.takeUnless { it.isBlank() || it == appPackage || it in EXCLUDED_SCREEN_PACKAGES }

internal data class ScreenScanWindow(val id: Int, val packageName: String)

internal fun screenScanWindow(id: Int, source: CharSequence?, appPackage: String): ScreenScanWindow? =
    screenScanPackage(source, appPackage)?.let { ScreenScanWindow(id, it) }

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
    private var lastScanAt = 0L
    private val repeatedText = EventDeduplicator()

    private val scanRunnable = Runnable { scanActiveWindow() }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (screenScanPackage(event.packageName, packageName) == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED
        ) return

        // Coalesce bursts without postponing forever on continuously changing screens.
        if (!mainHandler.hasCallbacks(scanRunnable)) mainHandler.postDelayed(scanRunnable, SCAN_DELAY_MS)
    }

    private fun scanActiveWindow() {
        if (!scanInProgress.compareAndSet(false, true)) {
            scanAgain.set(true)
            return
        }

        val now = SystemClock.elapsedRealtime()
        val delay = MIN_SCAN_INTERVAL_MS - (now - lastScanAt)
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
        val window = screenScanWindow(root.windowId, root.packageName, packageName)
        if (window == null) {
            recycleNode(root)
            finishScan()
            return
        }

        lastScanAt = now
        val accessibleText = StringBuilder()
        try {
            collect(root, accessibleText, HashSet(), intArrayOf(0))
        } catch (_: RuntimeException) {
            finishScan()
            return
        }
        val textSnapshot = accessibleText.toString()

        // Earlier Android releases can only capture a full display, including notification overlays.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            completeScan(window, textSnapshot)
            return
        }

        val callback = object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                if (destroyed.get()) {
                    screenshot.hardwareBuffer.close()
                    completeScan(window, textSnapshot)
                    return
                }
                recognizeScreenshot(window, screenshot, textSnapshot)
            }

            override fun onFailure(errorCode: Int) {
                completeScan(window, textSnapshot)
            }
        }

        try {
            takeScreenshotOfWindow(window.id, ocrExecutor, callback)
        } catch (_: RuntimeException) {
            completeScan(window, textSnapshot)
        }
    }

    private fun recognizeScreenshot(window: ScreenScanWindow, screenshot: ScreenshotResult, accessibleText: String) {
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
            completeScan(window, accessibleText)
            return
        }

        val task = runCatching { recognizer.process(InputImage.fromBitmap(bitmap, 0)) }.getOrNull()
        if (task == null) {
            bitmap.recycle()
            completeScan(window, accessibleText)
            return
        }

        task.addOnCompleteListener(ocrExecutor) { result ->
            val recognizedText = runCatching {
                if (result.isSuccessful) result.result?.text.orEmpty() else ""
            }.getOrDefault("")
            bitmap.recycle()
            completeScan(window, accessibleText, recognizedText)
        }
    }

    private fun completeScan(window: ScreenScanWindow, accessibleText: String, recognizedText: String = "") {
        mainHandler.post {
            try {
                if (!destroyed.get() && window == activeScanWindow()) {
                    inspectText(accessibleText, recognizedText)
                }
            } finally {
                finishScan()
            }
        }
    }

    private fun activeScanWindow(): ScreenScanWindow? {
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return null
        return try {
            screenScanWindow(root.windowId, root.packageName, packageName)
        } finally {
            recycleNode(root)
        }
    }

    private fun inspectText(accessibleText: String, recognizedText: String) {
        val combined = combineScreenText(accessibleText, recognizedText, MAX_SCREEN_TEXT_CHARS)
        if (combined.isNotBlank() && repeatedText.admit(fingerprint(combined))) Detector.inspect(this, combined)
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
            if (node.isPassword) return
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
        const val MIN_SCAN_INTERVAL_MS = 2_000L
        const val MAX_SCREEN_TEXT_CHARS = 12_000
        const val MAX_NODES_PER_SCAN = 500
    }
}
