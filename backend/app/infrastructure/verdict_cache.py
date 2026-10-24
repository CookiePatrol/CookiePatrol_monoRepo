"""Thread-safe SQLite cache with a 24-hour verdict lifetime."""

import os
import sqlite3
import threading
import time


class SqliteVerdictCache:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._connection = sqlite3.connect(
            os.environ.get("CACHE_DB_PATH", "cache.db"), check_same_thread=False
        )
        with self._lock, self._connection:
            self._connection.execute(
                "CREATE TABLE IF NOT EXISTS verdicts ("
                "url TEXT PRIMARY KEY, dangerous INTEGER NOT NULL, checked_at REAL NOT NULL)"
            )

    def get(self, url: str) -> bool | None:
        with self._lock:
            row = self._connection.execute(
                "SELECT dangerous, checked_at FROM verdicts WHERE url = ?", (url,)
            ).fetchone()
            if row is None or time.time() - row[1] >= 24 * 60 * 60:
                return None
            return bool(row[0])

    def set(self, url: str, dangerous: bool) -> None:
        with self._lock, self._connection:
            self._connection.execute(
                "INSERT INTO verdicts (url, dangerous, checked_at) VALUES (?, ?, ?) "
                "ON CONFLICT(url) DO UPDATE SET "
                "dangerous = excluded.dangerous, checked_at = excluded.checked_at",
                (url, int(dangerous), time.time()),
            )

    def close(self) -> None:
        with self._lock:
            self._connection.close()
