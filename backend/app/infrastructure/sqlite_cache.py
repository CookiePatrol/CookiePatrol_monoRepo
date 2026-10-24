import os
import sqlite3
from datetime import datetime, timezone
from pathlib import Path

from app.domain.verdict import Verdict, VerdictStatus


class SQLiteVerdictCache:
    def __init__(self, db_path: str | None = None):
        self.db_path = db_path or os.getenv("CACHE_DB_PATH", "./data/cookiepatrol-cache.sqlite3")
        if self.db_path != ":memory:":
            Path(self.db_path).parent.mkdir(parents=True, exist_ok=True)
        self.connection = sqlite3.connect(self.db_path, check_same_thread=False)
        self.connection.execute(
            """CREATE TABLE IF NOT EXISTS verdict_cache (
                cache_key TEXT PRIMARY KEY,
                status TEXT NOT NULL,
                threat_types TEXT NOT NULL,
                source TEXT,
                expires_at TEXT NOT NULL
            )"""
        )
        self.connection.commit()

    def get(self, key: str, now: datetime) -> Verdict | None:
        row = self.connection.execute(
            "SELECT status, threat_types, source, expires_at FROM verdict_cache WHERE cache_key = ?",
            (key,),
        ).fetchone()
        if row is None:
            return None
        try:
            status = VerdictStatus(row[0])
        except ValueError:
            # Discard entries written by older verdict schemas (for example, "safe").
            self.connection.execute("DELETE FROM verdict_cache WHERE cache_key = ?", (key,))
            self.connection.commit()
            return None
        if datetime.fromisoformat(row[3]) <= now:
            self.connection.execute("DELETE FROM verdict_cache WHERE cache_key = ?", (key,))
            self.connection.commit()
            return None
        threats = tuple(filter(None, row[1].split(",")))
        return Verdict(status, threats, row[2], cached=True)

    def put(self, key: str, verdict: Verdict, expires_at: datetime) -> None:
        self.connection.execute(
            """INSERT OR REPLACE INTO verdict_cache
               (cache_key, status, threat_types, source, expires_at) VALUES (?, ?, ?, ?, ?)""",
            (key, verdict.status.value, ",".join(verdict.threat_types), verdict.source, expires_at.isoformat()),
        )
        self.connection.commit()

    def close(self) -> None:
        self.connection.close()
