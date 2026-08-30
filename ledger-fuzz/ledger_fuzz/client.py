from __future__ import annotations

import httpx


class LedgerClient:
    def __init__(self, base_url: str) -> None:
        self._client = httpx.Client(base_url=base_url, timeout=10.0)

    def close(self) -> None:
        self._client.close()

    def create_transaction_raw(self, raw_json: str) -> httpx.Response:
        return self._client.post(
            "/transactions",
            content=raw_json,
            headers={"content-type": "application/json"},
        )

    def create_transaction(self, payload: dict) -> httpx.Response:
        return self._client.post("/transactions", json=payload)
