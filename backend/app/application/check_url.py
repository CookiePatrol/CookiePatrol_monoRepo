from app.domain.url_check import DetailedThreatChecker, ThreatChecker, ThreatCheckUnavailable, UrlAssessment, VerdictCache
import hashlib
import threading
from concurrent.futures import Future, TimeoutError
from collections.abc import Callable


class CheckUrl:
    def __init__(self, checker: ThreatChecker, cache: VerdictCache, primary: ThreatChecker | None = None,
                 detailed_checker: DetailedThreatChecker | None = None,
                 primary_cache_ttl: Callable[[str], float] | None = None):
        self.checker = checker
        self.cache = cache
        self.primary = primary
        self.detailed_checker = detailed_checker
        self.primary_cache_ttl = primary_cache_ttl
        self._pending: dict[str, Future[UrlAssessment]] = {}
        self._lock = threading.Lock()

    def __call__(self, url: str) -> bool:
        return self.assess(url).dangerous

    def assess(self, url: str) -> UrlAssessment:
        key = hashlib.sha256(url.encode("utf-8")).hexdigest()
        with self._lock:
            pending = self._pending.get(key)
            owner = pending is None
            if owner:
                if len(self._pending) >= 64:
                    raise ThreatCheckUnavailable("Request queue full", reason="overloaded")
                pending = Future()
                self._pending[key] = pending
        if not owner:
            try:
                return pending.result(timeout=22)
            except TimeoutError:
                raise ThreatCheckUnavailable("Shared check timed out", reason="timeout") from None
        try:
            result = self._assess_once(url)
            pending.set_result(result)
            return result
        except Exception as exc:
            pending.set_exception(exc)
            raise
        finally:
            with self._lock:
                self._pending.pop(key, None)

    def _assess_once(self, url: str) -> UrlAssessment:
        # Always consult Google before the AI cache so a newly listed threat wins.
        if self.primary is not None:
            try:
                if self.primary.is_dangerous(url):
                    ttl = self.primary_cache_ttl(url) if self.primary_cache_ttl is not None else 0.0
                    return UrlAssessment(True, "safe_browsing", google_cache_ttl_seconds=ttl)
            except ThreatCheckUnavailable:
                # An unavailable database is not a safe result; continue to AI.
                pass

        cached = self.cache.get(url)
        if cached is not None:
            return cached

        result = (self.detailed_checker.assess(url) if self.detailed_checker is not None
                  else UrlAssessment(self.checker.is_dangerous(url), "ai"))
        self.cache.set(url, result)
        return result
