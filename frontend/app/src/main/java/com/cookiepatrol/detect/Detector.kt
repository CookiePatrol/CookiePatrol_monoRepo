package com.cookiepatrol.detect

import android.content.Context
import com.cookiepatrol.monitor.EventDeduplicator
import com.cookiepatrol.monitor.fingerprint
import com.cookiepatrol.warn.Warning
import com.cookiepatrol.warn.WarningPriority
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

object Detector {
    private val warnings = EventDeduplicator()
    private val executor = ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(64),
        { task -> Thread(task, "cookiepatrol-url-check").apply { isDaemon = true } })
    // Only the application context is retained by pending work.
    @Volatile private var coordinator: ReputationCoordinator? = null

    @Synchronized private fun coordinator(context: Context): ReputationCoordinator {
        val app = context.applicationContext
        return coordinator ?: ReputationCoordinator(executor, { url ->
            if (PrivacySettings.remoteEnabled(app)) CheckApi.checkDetails(url) else CheckResult(CheckStatus.UNKNOWN)
        }).also { coordinator = it }
    }

    internal fun isLookupCandidate(score: Int): Boolean = score >= RiskAnalyzer.LOOKUP_THRESHOLD

    fun inspect(context: Context, rawText: String, eventKey: String = fingerprint(rawText)) {
        val app = context.applicationContext
        val assessment = RiskAnalyzer.analyze(rawText.take(12_000))
        if (assessment.shouldWarn) showWarningOnce(app, eventKey, requireNotNull(assessment.reason))
        val remembered = assessment.urls.take(10).mapNotNull { url ->
            coordinator?.cachedDangerous(url)?.let { url to it }
        }.toMap()
        remembered.values.forEach { result ->
            showWarningOnce(app, eventKey, result.warningReason,
                if (result.source == "safe_browsing") WarningPriority.GOOGLE else WarningPriority.AI)
        }
        if (!assessment.shouldLookup || !PrivacySettings.remoteEnabled(app)) return
        assessment.urls.take(10).filterNot { it in remembered }.forEach { url ->
            coordinator(app).lookup(url) { result ->
                when (result.status) {
                    CheckStatus.DANGEROUS -> showWarningOnce(app, eventKey, result.warningReason,
                        if (result.source == "safe_browsing") WarningPriority.GOOGLE else WarningPriority.AI)
                    CheckStatus.UNKNOWN -> if (!assessment.shouldWarn) showWarningOnce(app, eventKey,
                        "의심 링크의 추가 검사를 마치지 못했어요. 안전하다고 확인된 것은 아니에요.", WarningPriority.UNAVAILABLE)
                    CheckStatus.NOT_DETECTED -> Unit
                }
            }
        }
    }

    private fun showWarningOnce(context: Context, eventKey: String, reason: String, priority: WarningPriority = WarningPriority.LOCAL) {
        val key = fingerprint("$eventKey\u0000$reason")
        if (warnings.admit(key) && !Warning.show(context, reason, eventKey = eventKey, priority = priority)) warnings.forget(key)
    }
}
