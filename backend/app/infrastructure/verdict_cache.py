"""Thread-safe SQLite cache with a 24-hour verdict lifetime."""

import hashlib
import os
import sqlite3
import threading
import time
from pathlib import Path


class SqliteVerdictCache:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        db_path = os.environ.get("CACHE_DB_PATH", "./data/cookiepatrol-cache.sqlite3")
        if db_path != ":memory:":
            Path(db_path).parent.mkdir(parents=True, exist_ok=True)
        self._connection = sqlite3.connect(db_path, check_same_thread=False)
        with self._lock, self._connection:
            columns = {
                row[1]
                for row in self._connection.execute("PRAGMA table_info(verdicts)").fetchall()
            }
            if "url" in columns:
                # Remove legacy rows that stored full URLs in plaintext.
                self._connection.execute("PRAGMA secure_delete = ON")
                self._connection.execute("DROP TABLE verdicts")
            self._connection.execute(
                "CREATE TABLE IF NOT EXISTS verdicts ("
                "cache_key TEXT PRIMARY KEY, dangerous INTEGER NOT NULL, checked_at REAL NOT NULL)"
            )

    @staticmethod
    def _cache_key(url: str) -> str:
        return hashlib.sha256(url.encode("utf-8")).hexdigest()

    def get(self, url: str) -> bool | None:
        with self._lock, self._connection:
            row = self._connection.execute(
                "SELECT dangerous, checked_at FROM verdicts WHERE cache_key = ?",
                (self._cache_key(url),),
            ).fetchone()
            if row is None:
                return None
            if time.time() - row[1] >= 24 * 60 * 60:
                self._connection.execute(
                    "DELETE FROM verdicts WHERE cache_key = ?", (self._cache_key(url),)
                )
                return None
            return bool(row[0])

    def set(self, url: str, dangerous: bool) -> None:
        with self._lock, self._connection:
            self._connection.execute(
                "INSERT INTO verdicts (cache_key, dangerous, checked_at) VALUES (?, ?, ?) "
                "ON CONFLICT(cache_key) DO UPDATE SET "
                "dangerous = excluded.dangerous, checked_at = excluded.checked_at",
                (self._cache_key(url), int(dangerous), time.time()),
            )

    def close(self) -> None:
        with self._lock:
            self._connection.close()
