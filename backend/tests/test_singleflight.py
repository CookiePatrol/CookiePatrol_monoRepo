from concurrent.futures import ThreadPoolExecutor
from dataclasses import replace
import threading

from app.application.check_url import CheckUrl


def test_concurrent_identical_urls_share_one_provider_call():
    class Checker:
        def __init__(self):
            self.calls = 0
        def is_dangerous(self, url):
            self.calls += 1
            entered.set()
            assert release.wait(2)
            return True
    class Cache:
        def __init__(self):
            self.value = None
        def get(self, url):
            return self.value
        def set(self, url, result):
            self.value = replace(result, source="ai_cache")
    entered, release = threading.Event(), threading.Event()
    checker = Checker()
    service = CheckUrl(checker, Cache())
    with ThreadPoolExecutor(max_workers=8) as pool:
        results = [pool.submit(service, "https://example.com/?token=private") for _ in range(8)]
        assert entered.wait(2)
        release.set()
        assert all(future.result() is True for future in results)
    assert checker.calls == 1
    assert service._pending == {}
