package com.cookiepatrol.monitor

import android.util.Log
import com.cookiepatrol.BuildConfig

/** Lets the PC correlate a debug demo with the app's result without logging message content. */
internal object DemoReceipt {
    private const val LOG_TAG = "CookiePatrolDemo"
    private const val SENDER_PACKAGE = "com.cookiepatrol.demosender"
    private val senderLabel = Regex("쿠키패트롤 시연 · ([0-9A-F]{8})")

    fun runId(sourcePackage: String, sender: String?): String? {
        if (!BuildConfig.DEBUG || sourcePackage != SENDER_PACKAGE || sender == null) return null
        return senderLabel.matchEntire(sender)?.groupValues?.get(1)
    }

    fun local(runId: String?, score: Int) {
        if (BuildConfig.DEBUG && runId != null) Log.i(LOG_TAG, "$runId LOCAL $score")
    }

    fun warning(runId: String?, stage: Int, notificationTag: String) {
        if (BuildConfig.DEBUG && runId != null) Log.i(LOG_TAG, "$runId WARNING $stage $notificationTag")
    }

    fun done(runId: String?) {
        if (BuildConfig.DEBUG && runId != null) Log.i(LOG_TAG, "$runId DONE")
    }
}
