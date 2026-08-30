from ledger_fuzz.invariants import assert_derived_balance


def test_balance_invariant_example(ledger_db):
    # Replace this with a fixture-created balance rather than a permanent shared ID.
    balance_id = "REPLACE_WITH_FIXTURE_CREATED_BALANCE"
    state = ledger_db.get_balance(balance_id)
    assert_derived_balance(state)
