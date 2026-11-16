package com.cookiepatrol.detect

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

class ReputationCoordinatorTest {
    @Test fun googleEvidenceExpiresWhenProviderSaysSoAndMissingTtlIsNotInvented() {
        var time = 0L
        var calls = 0
        val coordinator = ReputationCoordinator(Executor { it.run() }, {
            calls++; CheckResult(CheckStatus.DANGEROUS, "safe_browsing", googleCacheTtlSeconds = 1.0)
        }, now = { time })
        coordinator.lookup("https://example.com") {}
        time = 999
        coordinator.lookup("https://example.com") {}
        assertEquals(1, calls)
        time = 1000
        coordinator.lookup("https://example.com") {}
        assertEquals(2, calls)
        val zero = ReputationCoordinator(Executor { it.run() }, {
            calls++; CheckResult(CheckStatus.DANGEROUS, "safe_browsing")
        })
        repeat(2) { zero.lookup("https://zero.example") {} }
        assertEquals(4, calls)
        assertEquals(CheckStatus.UNKNOWN, detailedResult(true, "safe_browsing", emptyList(), Double.NaN).status)
        assertEquals(300_000L, detailedResult(true, "safe_browsing", emptyList(), 600.0).cacheLifetimeMillis)
    }

    @Test fun sharesRequestAndReplaysDangerForNewNotification() {
        val jobs = mutableListOf<Runnable>()
        var calls = 0
        val results = mutableListOf<CheckResult>()
        var time = 0L
        val coordinator = ReputationCoordinator(Executor { jobs.add(it) }, {
            calls++; CheckResult(CheckStatus.DANGEROUS, "ai_cache")
        }, now = { time }, ttl = 10)
        repeat(2) { coordinator.lookup("https://example.com") { results.add(it) } }
        assertEquals(1, jobs.size)
        jobs.removeAt(0).run()
        assertEquals(2, results.size)
        coordinator.lookup("https://example.com") { results.add(it) }
        assertEquals(3, results.size)
        assertEquals(1, calls)
        time = 10
        coordinator.lookup("https://example.com") { results.add(it) }
        assertEquals(1, jobs.size)
    }
    @Test fun transientFailuresAndQueueRejectionAllowRetry() {
        var calls = 0
        val coordinator = ReputationCoordinator(Executor { it.run() }, { calls++; CheckResult(CheckStatus.UNKNOWN) })
        repeat(2) { coordinator.lookup("https://example.com") { assertEquals(CheckStatus.UNKNOWN, it.status) } }
        assertEquals(2, calls)
        val rejected = ReputationCoordinator(Executor { throw RejectedExecutionException() }, { error("must not run") })
        repeat(2) { rejected.lookup("https://example.com") { assertEquals(CheckStatus.UNKNOWN, it.status) } }
    }
}
