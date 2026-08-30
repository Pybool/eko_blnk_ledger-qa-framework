from __future__ import annotations

from .domain import AccountState


def assert_derived_balance(state: AccountState) -> None:
    assert state.balance == state.credit_balance - state.debit_balance, (
        f"balance invariant violated for {state.account_id}: "
        f"stored={state.balance}, "
        f"derived={state.credit_balance - state.debit_balance}"
    )
