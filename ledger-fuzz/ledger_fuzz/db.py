from __future__ import annotations

from decimal import Decimal

import psycopg
from psycopg.rows import dict_row

from .domain import AccountState


_GET_BALANCE_SQL = (
    "SELECT balance_id, balance, credit_balance, debit_balance, version "
    "FROM blnk.balances "
    "WHERE balance_id = %s"
)

_PRECISE_AMOUNT_SQL = (
    "SELECT precise_amount::text AS precise_amount "
    "FROM blnk.transactions "
    "WHERE reference = %s "
    "LIMIT 1"
)


class LedgerDatabase:
    def __init__(self, database_url: str) -> None:
        self._conn = psycopg.connect(database_url, row_factory=dict_row)

    def close(self) -> None:
        self._conn.close()

    def get_balance(self, balance_id: str) -> AccountState:
        with self._conn.cursor() as cur:
            cur.execute(_GET_BALANCE_SQL, (balance_id,))
            row = cur.fetchone()

        if row is None:
            raise AssertionError(f"Balance not found: {balance_id}")

        return AccountState(
            account_id=row["balance_id"],
            balance=Decimal(str(row["balance"])),
            credit_balance=Decimal(str(row["credit_balance"])),
            debit_balance=Decimal(str(row["debit_balance"])),
            version=int(row["version"]),
        )

    def precise_amount_for_reference(self, reference: str) -> str | None:
        with self._conn.cursor() as cur:
            cur.execute(_PRECISE_AMOUNT_SQL, (reference,))
            row = cur.fetchone()

        return None if row is None else row["precise_amount"]
