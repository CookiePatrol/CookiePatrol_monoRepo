package com.cookiepatrol.warn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WarningMessageTest {
    @Test fun previewRemovesControlCharactersAndNormalizesWhitespace() {
        val message = WarningMessage.from("  발신자\u202e ", " 첫째 줄\r\n\t둘째\u0000 줄 ", 123L)
        assertEquals("발신자", message.sender)
        assertEquals("첫째 줄 둘째 줄", message.preview)
        assertEquals(123L, message.timeMillis)
    }

    @Test fun previewIsBoundedWithoutBreakingSurrogatePairs() {
        val prefix = "가".repeat(159)
        val message = WarningMessage.from(" ", prefix + "😀더 긴 본문", 0)
        assertNull(message.sender)
        assertEquals(prefix + "😀…", message.preview)
    }
}
