# Failure Model

Fault scenarios include:
- latency
- timeout
- connection reset
- database outage
- dependency outage
- ambiguous client response after commit
- retry after partial network failure

Each scenario must verify both API behavior and final ledger consistency.
