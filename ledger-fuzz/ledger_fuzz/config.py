from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Config:
    ledger_base_url: str
    database_url: str

    @classmethod
    def from_env(cls) -> "Config":
        return cls(
            ledger_base_url=os.getenv("LEDGER_BASE_URL", "http://localhost:5001"),
            database_url=os.getenv(
                "DATABASE_URL",
                "postgresql://postgres:postgres@localhost:5432/blnk",
            ),
        )
