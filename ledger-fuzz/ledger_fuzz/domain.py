from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal


@dataclass(frozen=True)
class Money:
    amount: Decimal
    currency: str = "NGN"


@dataclass(frozen=True)
class AccountState:
    account_id: str
    balance: Decimal
    credit_balance: Decimal
    debit_balance: Decimal
    version: int
