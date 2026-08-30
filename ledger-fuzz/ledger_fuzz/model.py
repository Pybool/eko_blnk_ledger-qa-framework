from __future__ import annotations

from collections import defaultdict
from decimal import Decimal


class ReferenceLedger:
    """Independent oracle used to model expected financial effects."""

    def __init__(self) -> None:
        self._settled: dict[str, Decimal] = defaultdict(lambda: Decimal("0"))
        self._reserved: dict[str, Decimal] = defaultdict(lambda: Decimal("0"))

    def credit(self, account: str, amount: Decimal) -> None:
        if amount < 0:
            raise ValueError("amount must be non-negative")
        self._settled[account] += amount

    def debit(self, account: str, amount: Decimal) -> None:
        if amount < 0:
            raise ValueError("amount must be non-negative")
        self._settled[account] -= amount

    def reserve(self, account: str, amount: Decimal) -> None:
        if amount < 0:
            raise ValueError("amount must be non-negative")
        self._reserved[account] += amount

    def release(self, account: str, amount: Decimal) -> None:
        self._reserved[account] -= amount

    def commit_debit(self, account: str, amount: Decimal) -> None:
        self.release(account, amount)
        self.debit(account, amount)

    def settled(self, account: str) -> Decimal:
        return self._settled[account]

    def reserved(self, account: str) -> Decimal:
        return self._reserved[account]

    def available(self, account: str) -> Decimal:
        return self._settled[account] - self._reserved[account]
