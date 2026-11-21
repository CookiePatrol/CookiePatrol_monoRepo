package com.cookiepatrol.detect

import com.cookiepatrol.monitor.fingerprint
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/** One in-flight request per URL; each new notification receives the resulting warning. */
internal class ReputationCoordinator(
    private val executor: Executor,
    private val check: (String) -> CheckResult,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val ttl: Long = 5 * 60_000,
) {
    private val pending = HashMap<String, MutableList<(CheckResult) -> Unit>>()
    private val completed = LinkedHashMap<String, Pair<Long, CheckResult>>()

    /** Reuse recent dangerous verdicts without making a request. */
    @Synchronized fun cachedDangerous(url: String): CheckResult? {
        completed.entries.removeAll { now() >= it.value.first }
        return completed[fingerprint(url)]?.second?.takeIf { it.status == CheckStatus.DANGEROUS }
    }

    fun lookup(url: String, callback: (CheckResult) -> Unit) {
        val key = fingerprint(url)
        var immediate: CheckResult? = null
        var start = false
        synchronized(this) {
            completed.entries.removeAll { now() >= it.value.first }
            val cached = completed[key]
            val waiting = pending[key]
            when {
                cached != null -> immediate = cached.second
                waiting != null && waiting.size < 64 -> waiting.add(callback)
                waiting != null -> immediate = CheckResult(CheckStatus.UNKNOWN)
                pending.size >= 66 -> immediate = CheckResult(CheckStatus.UNKNOWN)
                else -> { pending[key] = mutableListOf(callback); start = true }
            }
        }
        immediate?.let(callback)
        if (!start) return
        try {
            executor.execute {
                finish(key, runCatching { check(url) }.getOrDefault(CheckResult(CheckStatus.UNKNOWN)))
            }
        } catch (_: RejectedExecutionException) {
            finish(key, CheckResult(CheckStatus.UNKNOWN))
        }
    }

    private fun finish(key: String, result: CheckResult) {
        val callbacks = synchronized(this) {
            val cacheMillis = minOf(ttl, result.cacheLifetimeMillis)
            if (result.status != CheckStatus.UNKNOWN && cacheMillis > 0) {
                completed[key] = (now() + cacheMillis) to result
                while (completed.size > 500) completed.remove(completed.keys.first())
            }
            pending.remove(key).orEmpty().toList()
        }
        callbacks.forEach { runCatching { it(result) } }
    }
}
