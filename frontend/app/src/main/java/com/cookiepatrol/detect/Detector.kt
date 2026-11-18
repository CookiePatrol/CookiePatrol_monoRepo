package com.cookiepatrol.detect

import android.content.Context
import com.cookiepatrol.monitor.fingerprint
import com.cookiepatrol.warn.Warning
import com.cookiepatrol.warn.WarningMessage
import com.cookiepatrol.warn.WarningPriority
import java.net.URI
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

object Detector {
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

    internal fun inspect(
        context: Context,
        rawText: String,
        eventKey: String = fingerprint(rawText),
        message: WarningMessage? = null,
    ) {
        val app = context.applicationContext
        val assessment = RiskAnalyzer.analyze(rawText.take(12_000))
        val urls = assessment.urls.take(10)
        val localReasons = assessment.hits.sortedByDescending { it.score }.take(2).map { it.reason }
        val remembered = urls.mapNotNull { url ->
            coordinator?.cachedDangerous(url)?.let { url to it }
        }.toMap()

        fun warnAboutLink(url: String, result: CheckResult) {
            Warning.show(
                app, result.warningReason, eventKey = eventKey, priority = result.warningPriority,
                message = message, linkHost = URI.create(url).rawAuthority, additionalReason = localReasons.firstOrNull(),
            )
        }

        val strongest = remembered.entries.maxByOrNull { it.value.warningPriority }
        if (strongest != null) {
            warnAboutLink(strongest.key, strongest.value)
        } else if (assessment.shouldWarn) {
            Warning.show(
                app, localReasons.first(), eventKey = eventKey, message = message,
                additionalReason = localReasons.getOrNull(1),
            )
        }

        if (!assessment.shouldLookup || !PrivacySettings.remoteEnabled(app)) return
        urls.filterNot { it in remembered }.forEach { url ->
            coordinator(app).lookup(url) { result ->
                if (result.status == CheckStatus.DANGEROUS) warnAboutLink(url, result)
            }
        }
    }

    private val CheckResult.warningPriority: WarningPriority
        get() = if (source == "safe_browsing") WarningPriority.GOOGLE else WarningPriority.AI
}
