from __future__ import annotations

import json
import uuid

from hypothesis import given, settings

from ledger_fuzz.generators import precise_amounts


# Configure these through fixtures or environment-backed account creation
# once the API fixture layer is implemented.
SOURCE = "REPLACE_WITH_TEST_SOURCE"
DESTINATION = "REPLACE_WITH_TEST_DESTINATION"


@settings(max_examples=50)
@given(precise_amount=precise_amounts)
def test_precise_amount_round_trips_exactly(
    precise_amount: int,
    ledger_client,
    ledger_db,
):
    reference = f"hypothesis-boundary-{uuid.uuid4()}"
    expected = str(precise_amount)

    # json.dumps preserves the integer exactly, avoiding any intermediate
    # floating-point conversion.
    payload = {
        "reference": reference,
        "source": SOURCE,
        "destination": DESTINATION,
        "precise_amount": precise_amount,
        "currency": "NGN",
        "precision": 100,
        "skip_queue": True,
        "allow_overdraft": True,
    }
    body = json.dumps(payload)

    response = ledger_client.create_transaction_raw(body)

    assert response.status_code < 400, response.text
    assert ledger_db.precise_amount_for_reference(reference) == expected
