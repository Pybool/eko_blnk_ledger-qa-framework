# Financial Ledger Quality Engineering Platform

**An independent, enterprise-oriented quality engineering framework for the [Blnk](https://github.com/blnkfinance/blnk) open-source financial ledger.**

This project tests financial correctness rather than relying exclusively on HTTP success codes. It combines API-driven scenarios with independent PostgreSQL assertions to detect monetary inaccuracies, invalid transaction transitions, duplicate financial effects, lost updates, and inconsistent persistence.

> **Project status:** Active development. Functional and accounting test coverage is being built incrementally. Concurrency, failure injection, fuzzing, and performance capabilities described below are roadmap items unless explicitly marked otherwise. This repository is an independent testing project, not an official Blnk test suite.

## What this project validates

A financial API can return `200 OK` while its underlying accounting is wrong. This framework treats the persisted ledger as a separate source of evidence and tests questions such as:

- Did both sides of a transfer settle the **exact** intended amount?
- Was a rejected operation truly free of financial side effects?
- Did an inflight transaction reserve funds without prematurely settling them?
- Did a commit or void create the correct child transaction and clear reservations?
- Are funds conserved across a **closed set** of balances, including the funding/world account?
- Does monetary precision survive long sequences of odd-valued transactions?
- Will retries, concurrent writes, and infrastructure failures preserve financial integrity?

## Technology stack

| Area | Technology | Purpose |
| --- | --- | --- |
| Primary test framework | Java 21, Maven, JUnit 5, AssertJ | Deterministic integration and invariant assertions |
| API testing | REST Assured | Transaction, balance, and ledger API verification |
| Persistence oracle | PostgreSQL | Independent state inspection and reconciliation |
| Test reporting | Allure | Scenario steps, assertions, and response attachments |
| Async verification | Awaitility | Poll for eventual settlement instead of fixed sleeps |
| Infrastructure isolation | Docker, Testcontainers | Reproducible dependencies and integration environments |
| Fault simulation | Toxiproxy, WireMock | Network faults, timeouts, and downstream failures (planned/expanding) |
| Property testing | jqwik | Generated transaction sequences and invariants (planned/expanding) |
| Adversarial testing | Python, pytest, Hypothesis | Stateful/model-based fuzzing (planned) |
| Performance | k6 | Load, contention, spike, stress, and soak tests (planned) |
| CI | GitHub Actions | Automated regression and quality gates (planned/expanding) |

**System under test:** Blnk ledger API and worker services, with PostgreSQL and Redis. The application under test is separate from the independent test assertions.

## Quality model: 11 ledger invariants

An *invariant* is a property that must remain true for every applicable transaction sequence, not merely for one happy-path example.

| ID | Invariant | Verification principle |
| --- | --- | --- |
| **INV-001** | Derived balance | `balance = credit_balance - debit_balance` for each persisted balance |
| **INV-002** | No mutation on failed operations | Rejected/invalid operations must not change settled financial state; compare database snapshots |
| **INV-003** | Idempotency | Repeating the same logically idempotent request must not produce duplicate financial effects |
| **INV-004** | Reservation safety | Reserved funds must not be settled twice, including under competing operations |
| **INV-005** | Void without settlement | Voiding an inflight transfer releases the reservation and leaves settled balances unchanged |
| **INV-006** | Exactly-once commit effect | A commit clears applicable inflight reservations and moves the intended amount into settled balances exactly once |
| **INV-007** | Exact monetary representation | Amounts and balances must retain exact precision; no floating-point drift, truncation, or unexplained rounding |
| **INV-008** | No lost updates | Concurrent writes must preserve all valid financial effects without overwritten balances |
| **INV-009** | Reconciliation | Reconstructing account state from applicable transaction records must match persisted balances |
| **INV-010** | Failure atomicity | Infrastructure faults must not leave an unrecoverable or partially applied financial mutation |
| **INV-011** | Valid lifecycle transitions | Transaction state changes must follow the supported state machine and reject invalid transitions |

### Accounting interpretation and test boundaries

**Precise units.** Tests use integer `precise_amount` values, such as `7_500` at precision `100` representing `75.00` currency units. Expected values use `long` and `BigDecimal`, never `double` or `float` for financial comparisons. API decimal representations, where exposed, should be compared independently against `precise_amount / precision`.

**Settled vs. inflight.** `credit_balance`, `debit_balance`, and derived `balance` represent settled accounting state. Inflight fields track reservations. A hold must not be confused with a settled transfer.

**Parent vs. child transactions.** Blnk's commit/void operation creates a child transaction (`APPLIED` or `VOID`). Tests identify the child from the mutation response and validate its relationship to the original inflight transaction. Queued flows may require searching by `meta_data.QUEUED_PARENT_TRANSACTION` instead of direct parent linkage.

**Closed-system conservation.** The sum of balances is conserved only when *all* source and destination accounts for the tested transfers are included. For funding scenarios, this includes the world/funding balance, which may legitimately become negative.

**Exactly once.** Verifying that one commit settles the expected amount is necessary but not sufficient. Full INV-006 coverage also requires retry/duplicate-commit and competing-operation scenarios.

## Test suites and coverage

The labels below describe the designed coverage. They are **not** a claim that every scenario is already merged, passing, or running in CI.

### Functional API tests — `FUN-*`

| Scenario | Behavior under test |
| --- | --- |
| FUN-01 | Fund a balance from a world/funding account; verify both persisted sides |
| FUN-02 | Transfer within a ledger and validate source/debit and destination/credit |
| FUN-03 | Transfer across ledgers using the same currency |
| FUN-04 | Reject a zero-amount transfer without settled mutation |
| FUN-05 | Retrieve a transaction and compare API status/amount with persistence |
| FUN-06 | Reject insufficient funds when overdraft is disabled |
| FUN-07 | Allow an otherwise insufficient transfer when overdraft is explicitly enabled |
| Additional `FUN-*` | Multi-source/multi-destination transfers, atomicity/compensation, rounding residue, and related edge cases under development |

### Accounting invariant tests — `ACC-*`

| Scenario | Core assertions | Invariants |
| --- | --- | --- |
| ACC-01 | Every affected balance satisfies the derived balance equation | INV-001 |
| ACC-02 | Source debit and destination credit deltas equal the same transfer amount | INV-001 |
| ACC-03 | Inflight reservation changes hold fields without settling the transfer | INV-004 |
| ACC-04 | Full commit clears holds and settles exactly the intended amount; validate child transaction | INV-001, INV-006 |
| ACC-05 | Void clears reservations while settled source/destination snapshots remain unchanged | INV-001, INV-005 |
| ACC-06 | Sum of settled balances across a closed ledger set remains constant after N transfers | Conservation |
| ACC-07 | Odd-valued chained transfers retain exact expected monetary values and representations | INV-007 |

**ACC-06 example:** Fund participants A, B, and C from a world balance; perform A→B, B→C, C→D, A→D, and D→B transfers. Snapshot all five accounts before and after the batch. Compare the exact `BigDecimal` sums, then run INV-001 for every account.

**ACC-07 example:** Fund A, then perform a long, deterministic sequence of odd precise-unit transfers between A and B. Track expected balances independently using exact arithmetic; compare persisted values after the chain. Where Blnk exposes a separate decimal API field, validate it independently against the expected scaled amount.

### Transaction lifecycle tests — `LIF-*`

| Scenario | Behavior under test |
| --- | --- |
| LIF-01 | Creating an inflight transaction reserves source funds without premature settlement |
| LIF-02 | Full commit transitions reserved funds into settled accounting and produces the correct child record |
| LIF-03 | Partial commit settles only the requested amount; verify the remaining hold against the Blnk version's documented semantics |
| Planned lifecycle extensions | Void, invalid transitions, repeated commits, queued processing, scheduled commits, and expiry |

**Partial-commit caution:** Do not assume whether the uncommitted remainder is released or remains reserved without checking the exact Blnk version and request contract. Tests should assert documented behavior, not inferred behavior.

### Planned advanced suites

- **Idempotency and state machines:** replay requests, retry commits, invalid transitions, commit/void races (INV-003, INV-004, INV-006, INV-011).
- **Concurrency:** simultaneous transfers, insufficient-funds contention, balance-update races, and lost-update detection (INV-008).
- **Reconciliation:** independently aggregate applicable persisted transactions and compare reconstructed balances with stored state (INV-009).
- **Fault injection:** database/Redis/network interruption, delayed responses, queue retries, and recovery consistency (INV-010).
- **Property/model-based tests:** generate valid and invalid operation sequences and check invariants after every step.
- **Performance:** k6 load, contention, spike, stress, and soak profiles with correctness checks before and after runs.

## Framework design

```text
ledger-quality-platform/
├── src/test/java/io/portfolio/ledgerqa/
│   ├── api/                 # REST Assured clients and endpoint abstractions
│   ├── assertions/          # LedgerInvariantAssertions
│   ├── db/                  # DB connection, queries, persisted models
│   ├── domain/              # Transaction enums and domain concepts
│   ├── functional/          # FunctionalTestBase and functional scenarios
│   ├── model/               # API request/response DTOs
│   └── ...                  # Accounting, lifecycle and supporting tests
├── ledger-fuzz/             # Planned/expanding Python adversarial suite
├── pom.xml
└── README.md
```

*Illustrative architecture; consult the checked-out repository for the exact current file tree.*

The test design separates:

1. **Actions:** Create ledgers and balances, fund accounts, submit transfers, commit or void inflight transactions.
2. **Observations:** Capture HTTP responses and independent persisted PostgreSQL records.
3. **Assertions:** Compare snapshots, transaction lineage, monetary deltas, and invariants.
4. **Reporting:** Attach evidence to Allure and fail with precise invariant-specific diagnostics.

Reusable helpers include `createLedger`, `createBalance`, `fundBalanceFromWorld`, `makeTransfer`, `mutateTransactionStatus`, `fetchPersistedBalance`, `fetchPersistedTransaction`, and `awaitTransactionStatus`. The invariant assertion layer centralizes accounting checks rather than duplicating them in each test.

## Running the project

### Prerequisites

- JDK 21 and Maven (or the repository's Maven wrapper)
- Docker Engine / Docker Desktop
- Running Blnk API and worker services with PostgreSQL and Redis
- Test configuration pointing at an **isolated, disposable** environment

> **Safety:** These tests create ledgers, balances, and transactions and may deliberately trigger overdrafts and failure cases. Never point the suite at production financial data.

### Java integration tests

```bash
./mvnw verify
```

On Windows PowerShell:

```powershell
.\mvnw.cmd verify
```

Or, if Maven is installed globally:

```powershell
mvn verify
```

Run a focused class (adjust class name to match the repository):

```powershell
mvn -Dtest=AccountingTests test
mvn -Dtest=LifecycleTests test
```

JUnit tags used by the suite include `accounting`, `lifecycle`, and individual case IDs such as `ACC-04`, `ACC-05`, `ACC-06`, `ACC-07`, and `LIF-03`.

**Configuration:** Supply the API base URL, database connection details, and any required Blnk credentials using the project's test configuration/environment settings. Consult the repository configuration before running; no fixed hostnames or credentials are assumed here.

### Python adversarial suite (planned/when present)

```bash
cd ledger-fuzz
python -m venv .venv
# Linux/macOS:
source .venv/bin/activate
# Windows PowerShell:
# .venv\Scripts\Activate.ps1
pip install -e ".[dev]"
pytest
```

### Reports

Allure is used for structured test steps and API/transaction evidence. Report generation depends on the Allure Maven plugin/CLI configuration present in the repository; check `pom.xml` for the supported command.

## Engineering principles

- **Independent oracle:** Never treat the API response as proof of accounting correctness; inspect PostgreSQL.
- **Determinism:** Use isolated test data and unique references; avoid sharing mutable balances across tests.
- **Exact arithmetic:** Compare precise units and `BigDecimal` values; never use binary floating-point for money.
- **Eventual consistency:** Poll asynchronous child transactions and state changes using bounded waits.
- **No false positives:** Check intermediate reservations before asserting that they were released.
- **No invented contracts:** Differentiate observed behavior from documented Blnk behavior, especially for partial commits and queued operations.
- **Reproducible failures:** Record request/response bodies, transaction IDs, balance IDs, and before/after database snapshots.
- **Stateful correctness:** Verify invariants across transaction sequences, not only isolated endpoint calls.

## Roadmap

- [x] Establish Java API and PostgreSQL verification framework
- [x] Define 11 financial ledger invariants
- [x] Develop core functional transfer and rejection scenarios
- [x] Develop inflight commit/void accounting scenarios and reusable assertions
- [ ] Complete and stabilize ACC-06/ACC-07 and lifecycle partial-commit coverage
- [ ] Expand idempotency, state machine, and concurrency tests
- [ ] Implement independent transaction-log reconciliation
- [ ] Add fault injection and recovery verification
- [ ] Build jqwik and Python Hypothesis model-based suites
- [ ] Add k6 performance and contention baselines
- [ ] Enforce regression gates and publish reproducible Allure results in CI

> Checklist reflects project development discussions, not a fresh repository audit. Verify completed/merged/passing status against the actual code and CI results before publishing.

## Project purpose

This is a portfolio-grade SDET exercise in **financial systems quality engineering**: testing whether financial state is correct under normal operations, edge cases, retries, concurrency, and failure—not simply whether endpoints return successful responses.

**Repository:** [Pybool/eko_blnk_ledger-qa-framework](https://github.com/Pybool/eko_blnk_ledger-qa-framework)
