"""Thread-safe SQLite cache with a 24-hour verdict lifetime."""

import hashlib
import json
import os
import sqlite3
import threading
import time
from pathlib import Path

from app.domain.url_check import INDICATOR_REASONS, UrlAssessment

MAX_CACHE_ENTRIES = 100_000
CACHE_TTL_SECONDS = 24 * 60 * 60


def _known_indicators(value: object) -> tuple[str, ...]:
    if not isinstance(value, (list, tuple)):
        return ()
    return tuple(dict.fromkeys(code for code in value if isinstance(code, str) and code in INDICATOR_REASONS))


class SqliteVerdictCache:
    def __init__(self, namespace: str = "") -> None:
        self._namespace = namespace
        self._lock = threading.Lock()
        db_path = os.environ.get("CACHE_DB_PATH", "./data/cookiepatrol-cache.sqlite3")
        if db_path != ":memory:":
            Path(db_path).parent.mkdir(parents=True, exist_ok=True)
        self._connection = sqlite3.connect(db_path, check_same_thread=False)
        with self._lock, self._connection:
            # Serialize schema changes when multiple processes open the same cache.
            self._connection.execute("BEGIN IMMEDIATE")
            columns = {
                row[1]
                for row in self._connection.execute("PRAGMA table_info(verdicts)").fetchall()
            }
            if "url" in columns:
                # Remove legacy rows that stored full URLs in plaintext.
                self._connection.execute("PRAGMA secure_delete = ON")
                self._connection.execute("DROP TABLE verdicts")
                columns = set()
            self._connection.execute(
                "CREATE TABLE IF NOT EXISTS verdicts ("
                "cache_key TEXT PRIMARY KEY, dangerous INTEGER NOT NULL, checked_at REAL NOT NULL, "
                "indicators TEXT NOT NULL DEFAULT '[]')"
            )
            if columns and "indicators" not in columns:
                self._connection.execute("ALTER TABLE verdicts ADD COLUMN indicators TEXT NOT NULL DEFAULT '[]'")
            self._connection.execute(
                "CREATE INDEX IF NOT EXISTS idx_verdicts_checked_at ON verdicts(checked_at)"
            )

    def _cache_key(self, url: str) -> str:
        value = f"{self._namespace}\0{url}" if self._namespace else url
        return hashlib.sha256(value.encode("utf-8")).hexdigest()

    def get(self, url: str) -> UrlAssessment | None:
        key = self._cache_key(url)
        with self._lock, self._connection:
            row = self._connection.execute(
                "SELECT dangerous, checked_at, indicators FROM verdicts WHERE cache_key = ?",
                (key,),
            ).fetchone()
            if row is None:
                return None
            cutoff = time.time() - CACHE_TTL_SECONDS
            if row[1] <= cutoff:
                self._connection.execute(
                    "DELETE FROM verdicts WHERE cache_key = ? AND checked_at <= ?", (key, cutoff)
                )
                return None
            dangerous = bool(row[0])
            try:
                indicators = _known_indicators(json.loads(row[2])) if dangerous else ()
            except (ValueError, TypeError):
                indicators = ()
            return UrlAssessment(dangerous, "ai_cache", indicators)

    def set(self, url: str, result: UrlAssessment) -> None:
        indicators = _known_indicators(result.indicators) if result.dangerous else ()
        with self._lock, self._connection:
            now = time.time()
            self._connection.execute(
                "DELETE FROM verdicts WHERE checked_at <= ?", (now - CACHE_TTL_SECONDS,)
            )
            self._connection.execute(
                "INSERT INTO verdicts (cache_key, dangerous, checked_at, indicators) VALUES (?, ?, ?, ?) "
                "ON CONFLICT(cache_key) DO UPDATE SET "
                "dangerous = excluded.dangerous, checked_at = excluded.checked_at, indicators = excluded.indicators",
                (self._cache_key(url), int(result.dangerous), now, json.dumps(indicators)),
            )
            excess = self._connection.execute(
                "SELECT COUNT(*) - ? FROM verdicts", (MAX_CACHE_ENTRIES,)
            ).fetchone()[0]
            if excess > 0:
                self._connection.execute(
                    "DELETE FROM verdicts WHERE cache_key IN ("
                    "SELECT cache_key FROM verdicts ORDER BY checked_at ASC LIMIT ?)",
                    (excess,),
                )

    def close(self) -> None:
        with self._lock:
            self._connection.close()
