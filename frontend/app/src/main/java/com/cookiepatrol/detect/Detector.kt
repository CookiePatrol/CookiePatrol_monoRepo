package com.cookiepatrol.detect

import android.content.Context
import com.cookiepatrol.monitor.DemoReceipt
import com.cookiepatrol.monitor.fingerprint
import com.cookiepatrol.warn.Warning
import com.cookiepatrol.warn.WarningAssessment
import com.cookiepatrol.warn.WarningFinding
import com.cookiepatrol.warn.WarningMessage
import com.cookiepatrol.warn.WarningPriority
import java.net.URI
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

object Detector {
    private const val MAX_LINKS_PER_EVENT = 10
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

    internal fun inspect(
        context: Context,
        rawText: String,
        eventKey: String = fingerprint(rawText),
        message: WarningMessage? = null,
    ) {
        val app = context.applicationContext
        val assessment = RiskAnalyzer.analyze(rawText.take(12_000))
        DemoReceipt.local(message?.demoRunId, assessment.score)
        val urls = assessment.urls.take(MAX_LINKS_PER_EVENT)
        val hits = assessment.hits.sortedByDescending { it.score }
        val findings = hits.map {
            WarningFinding(assessment.categoryFor(it), it.title, it.reason, it.score)
        }
        val transferReason = hits.firstOrNull { it.id == BANK_TRANSFER_SIGNAL_ID }?.reason
        val callbackReason = hits.firstOrNull { it.id == PHONE_CALLBACK_SIGNAL_ID }?.reason
        val advice = when {
            transferReason != null && callbackReason != null ->
                "문자에 적힌 계좌로 송금하거나 번호로 전화하지 마세요."
            transferReason != null -> "문자에 적힌 계좌로 송금하지 마세요."
            callbackReason != null -> "문자에 적힌 번호로 전화하지 마세요."
            else -> null
        }?.let { caution ->
            val verify = "공식 앱이나 홈페이지, 또는 평소 연락하던 번호로 요청을 직접 확인하세요."
            val linkCaution = if (urls.isEmpty()) "" else "문자의 링크를 열지 마세요. "
            "$linkCaution$caution $verify"
        }
        val remembered = urls.mapNotNull { url ->
            coordinator?.cachedDangerous(url)?.let { url to it }
        }.toMap()

        fun warnAboutLink(url: String, result: CheckResult) {
            val title = when (result.source) {
                "safe_browsing" -> "Google Safe Browsing"
                "ai", "ai_cache" -> "AI 링크 검사"
                else -> "서버 링크 검사"
            }
            val finding = WarningFinding(SignalCategory.LINK, title, result.warningReason)
            Warning.show(
                app, result.warningReason, action = advice, eventKey = eventKey, priority = result.warningPriority,
                message = message, linkHost = URI.create(url).rawAuthority,
                assessment = WarningAssessment(assessment.score, listOf(finding) + findings),
            )
        }

        val strongest = remembered.entries.maxByOrNull { it.value.warningPriority }
        if (strongest != null) {
            warnAboutLink(strongest.key, strongest.value)
        } else if (assessment.shouldWarn) {
            val reason = transferReason ?: hits.first().reason
            val linkHost = urls.singleOrNull()?.takeIf { findings.any { it.category == SignalCategory.LINK } }
                ?.let { URI.create(it).rawAuthority }
            Warning.show(
                app, reason, action = advice, eventKey = eventKey, message = message,
                linkHost = linkHost, assessment = WarningAssessment(assessment.score, findings),
            )
        }

        if (!assessment.shouldLookup(PrivacySettings.scope(app), fromMessage = message != null)) return
        val toCheck = urls.filterNot { it in remembered }
        val messageChecks = if (message != null && strongest == null && !assessment.shouldWarn) {
            MessageLinkChecks(toCheck.size, hasUncheckedLinks = assessment.urls.size > urls.size)
        } else null
        toCheck.forEach { url ->
            coordinator(app).lookup(url) { result ->
                if (!PrivacySettings.remoteEnabled(app)) return@lookup
                if (result.status == CheckStatus.DANGEROUS) warnAboutLink(url, result)
                if (message != null) {
                    messageChecks?.record(result.status)?.let { status ->
                        Warning.showLinkResult(app, eventKey, message, completed = status == CheckStatus.NOT_DETECTED)
                    }
                }
            }
        }
    }

    private val CheckResult.warningPriority: WarningPriority
        get() = when (source) {
            "safe_browsing" -> WarningPriority.GOOGLE
            "legacy" -> WarningPriority.SERVER
            else -> WarningPriority.AI
        }
}

/** Wait for all the checks before reporting a quiet result for the message. */
internal class MessageLinkChecks(private var remaining: Int, hasUncheckedLinks: Boolean = false) {
    private var status = if (hasUncheckedLinks) CheckStatus.UNKNOWN else CheckStatus.NOT_DETECTED

    @Synchronized fun record(result: CheckStatus): CheckStatus? {
        if (result == CheckStatus.DANGEROUS || (result == CheckStatus.UNKNOWN && status != CheckStatus.DANGEROUS)) {
            status = result
        }
        remaining--
        return status.takeIf { remaining == 0 && it != CheckStatus.DANGEROUS }
    }
}
