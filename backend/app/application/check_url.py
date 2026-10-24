from app.domain.url_check import ThreatChecker, VerdictCache


class CheckUrl:
    def __init__(self, checker: ThreatChecker, cache: VerdictCache):
        self.checker = checker
        self.cache = cache

    def __call__(self, url: str) -> bool:
        cached = self.cache.get(url)
        if cached is not None:
            return cached

        dangerous = self.checker.is_dangerous(url)
        self.cache.set(url, dangerous)
        return dangerous
