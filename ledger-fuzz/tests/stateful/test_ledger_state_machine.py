from __future__ import annotations

from decimal import Decimal

from hypothesis.stateful import RuleBasedStateMachine, invariant, rule

from ledger_fuzz.model import ReferenceLedger


class ReferenceLedgerStateMachine(RuleBasedStateMachine):
    """
    First stage: validate the independent model itself.

    Next stage: execute each generated command against BOTH:
    1. ReferenceLedger
    2. Real ledger API

    Then compare the persisted system state against the model after every rule.
    """

    def __init__(self) -> None:
        super().__init__()
        self.model = ReferenceLedger()
        self.account = "alice"

    @rule()
    def credit(self) -> None:
        self.model.credit(self.account, Decimal("10"))

    @rule()
    def debit(self) -> None:
        self.model.debit(self.account, Decimal("1"))

    @invariant()
    def model_balance_is_decimal(self) -> None:
        assert isinstance(self.model.settled(self.account), Decimal)


TestReferenceLedgerStateMachine = ReferenceLedgerStateMachine.TestCase
