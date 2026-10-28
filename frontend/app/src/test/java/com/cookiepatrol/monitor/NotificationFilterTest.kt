package com.cookiepatrol.monitor

import android.app.Notification
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationFilterTest {
    @Test
    fun inspectsMessageCategoryAndKnownMessagingApps() {
        assertTrue(NotificationFilter.shouldInspect("com.unknown.chat", Notification.CATEGORY_MESSAGE))
        assertTrue(NotificationFilter.shouldInspect("com.kakao.talk", null))
        assertTrue(NotificationFilter.shouldInspect("com.google.android.apps.messaging", null))
    }

    @Test
    fun skipsUnrelatedNotifications() {
        assertFalse(NotificationFilter.shouldInspect("com.android.settings", Notification.CATEGORY_SYSTEM))
        assertFalse(NotificationFilter.shouldInspect("com.example.shop", Notification.CATEGORY_PROMO))
    }
}
