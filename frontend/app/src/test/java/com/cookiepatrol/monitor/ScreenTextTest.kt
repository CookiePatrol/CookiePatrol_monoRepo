package com.cookiepatrol.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.cookiepatrol.detect.SIGNALS
import org.junit.Test

class ScreenTextTest {
    @Test
    fun scansForegroundAppContentOnly() {
        assertNull(screenScanWindow(14, "com.cookiepatrol", "com.cookiepatrol"))
        assertNull(screenScanWindow(14, "com.android.systemui", "com.cookiepatrol"))
        assertNull(screenScanWindow(14, "com.cookiepatrol.demosender", "com.cookiepatrol"))
        assertNull(screenScanWindow(14, null, "com.cookiepatrol"))
        assertEquals(ScreenScanWindow(14, "com.android.chrome"), screenScanWindow(14, "com.android.chrome", "com.cookiepatrol"))
    }

    @Test
    fun rejectsScanResultsFromAnotherWindowOrPackage() {
        val captured = screenScanWindow(14, "com.android.chrome", "com.cookiepatrol")
        assertEquals(captured, screenScanWindow(14, "com.android.chrome", "com.cookiepatrol"))
        assertNotEquals(captured, screenScanWindow(15, "com.android.chrome", "com.cookiepatrol"))
        assertNotEquals(captured, screenScanWindow(14, "com.kakao.talk", "com.cookiepatrol"))
        assertNotEquals(captured, screenScanWindow(14, "com.android.systemui", "com.cookiepatrol"))
    }

    @Test
    fun keepsOcrWhenAccessibilityTextFillsTheBudget() {
        val accessible = "a".repeat(12_000)
        val ocr = "b".repeat(5_970) + "[축하] 경품 당첨! bit.ly/x"

        val combined = combineScreenText(accessible, ocr)

        assertTrue(combined.length <= 12_000)
        assertTrue(combined.startsWith("a".repeat(6_000)))
        assertTrue(combined.contains("경품 당첨! bit.ly/x"))
        assertTrue(SIGNALS.filter { it.matches(combined.lowercase()) }.sumOf { it.score } >= 3)
    }

    @Test
    fun usesFullBudgetForTheOnlyAvailableSource() {
        assertEquals("ocr".repeat(4), combineScreenText("", "ocr".repeat(4), 12))
        assertEquals("text", combineScreenText("text", "", 12))
    }
}
