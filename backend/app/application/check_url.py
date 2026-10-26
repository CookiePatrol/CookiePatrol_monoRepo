from app.domain.url_check import ThreatChecker, ThreatCheckUnavailable, VerdictCache


class CheckUrl:
    def __init__(self, checker: ThreatChecker, cache: VerdictCache, primary: ThreatChecker | None = None):
        self.checker = checker
        self.cache = cache
        self.primary = primary

    def __call__(self, url: str) -> bool:
        # Always consult Google before the AI cache so a newly listed threat wins.
        if self.primary is not None:
            try:
                if self.primary.is_dangerous(url):
                    return True
            except ThreatCheckUnavailable:
                # An unavailable database is not a safe result; continue to AI.
                pass

        cached = self.cache.get(url)
        if cached is not None:
            return cached

        dangerous = self.checker.is_dangerous(url)
        self.cache.set(url, dangerous)
        return dangerous
