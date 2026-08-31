# Financial Ledger Quality Engineering Platform

Enterprise-grade SDET project for validating financial-ledger
correctness through deterministic integration tests, accounting invariants,
concurrency testing, fault injection, property-based/model-based testing,
reconciliation, and performance testing.

## Test ecosystems

- **Java 21 / JUnit 5 / REST Assured** — primary enterprise automation framework
- **Testcontainers / Toxiproxy / WireMock** — isolated infrastructure and fault injection
- **jqwik** — Java-native property testing
- **Python / pytest / Hypothesis** — adversarial model-based ledger fuzzing
- **k6** — load, contention, spike, stress, and soak testing
- **PostgreSQL** — persistence and independent reconciliation verification

## Core quality concerns

- monetary precision
- accounting invariants
- transaction lifecycle correctness
- idempotency
- optimistic locking
- concurrency and lost-update prevention
- inflight authorization / commit / void behavior
- reconciliation
- infrastructure failure recovery
- performance under contention

## Quick start

```bash
./mvnw verify
```

Python adversarial suite:

```bash
cd ledger-fuzz
python -m venv .venv
# Linux/macOS
source .venv/bin/activate
# Windows PowerShell
# .venv\Scripts\Activate.ps1

pip install -e ".[dev]"
pytest
```
