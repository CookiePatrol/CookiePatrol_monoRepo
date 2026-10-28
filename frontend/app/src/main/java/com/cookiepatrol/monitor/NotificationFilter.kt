package com.cookiepatrol.monitor

import android.app.Notification

/** Avoid inspecting unrelated notifications while retaining common SMS and Kakao clients. */
internal object NotificationFilter {
    private val knownMessagingPackages = setOf(
        "com.android.mms",
        "com.google.android.apps.messaging",
        "com.kakao.talk",
        "com.samsung.android.messaging",
    )

    fun shouldInspect(packageName: String, category: String?): Boolean =
        category == Notification.CATEGORY_MESSAGE || packageName in knownMessagingPackages
}
