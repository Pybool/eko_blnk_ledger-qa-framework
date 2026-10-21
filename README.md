# Financial Ledger Quality Engineering Platform

**An independent, enterprise-oriented quality engineering framework for the [Blnk](https://github.com/blnkfinance/blnk) open-source financial ledger.**

[Repository](https://github.com/Pybool/eko_blnk_ledger-qa-framework)

This project tests **financial correctness**, not just HTTP success codes. It combines REST API scenarios with independent PostgreSQL assertions to uncover inaccurate monetary effects, invalid lifecycle transitions, duplicate settlement, lost updates, and persistence inconsistencies.

> **Status:** Active development. Core functional, accounting, and lifecycle test cases have been implemented or developed incrementally. Not all cases are confirmed passing. In particular, automatic inflight expiry (LIF-10) remains under investigation. Concurrency, fault injection, model-based testing, and performance testing are planned. This is an independent project, not an official Blnk test suite.

## What this project validates

- Do source debits and destination credits reflect the **exact** intended amount?
- Are rejected operations free of settled financial side effects?
- Does an inflight transfer reserve funds without prematurely settling them?
- Do commit and void operations produce the correct child transactions and release reservations?
- Are duplicate and illegal lifecycle mutations rejected without extra settlement?
- Is value conserved across a closed set of balances, including the funding/world account?
- Is precision retained over long chains of odd-valued transfers?
- Can asynchronous behavior, retries, concurrency, and infrastructure faults compromise financial integrity?

## Technology stack

| Area | Technology | Purpose |
| --- | --- | --- |
| Primary test framework | Java 21, Maven, JUnit 5, AssertJ | Integration tests and exact invariant assertions |
| API testing | REST Assured, Jackson | Blnk API requests, responses, and DTO mapping |
| Persistence oracle | PostgreSQL | Independent transaction and balance inspection |
| Reporting | Allure | Structured steps and request/response evidence |
| Asynchronous verification | Awaitility | Bounded polling for persisted transaction states |
| Infrastructure | Docker, Testcontainers | Reproducible test dependencies and environments |
| Fault simulation | Toxiproxy, WireMock | Planned network and downstream failure testing |
| Property testing | jqwik | Planned generated transaction sequences |
| Adversarial testing | Python, pytest, Hypothesis | Planned stateful/model-based fuzzing |
| Performance | k6 | Planned load, contention, stress, and soak tests |
| CI | GitHub Actions | Planned/expanding regression automation |

**System under test:** Blnk API and worker services, PostgreSQL, and Redis. The framework independently observes persisted accounting state.

## Quality model: 11 ledger invariants

| ID | Invariant | Verification principle |
| --- | --- | --- |
| **INV-001** | Derived balance | `balance = credit_balance - debit_balance` |
| **INV-002** | No mutation on failed operations | Rejected operations must not change settled balances |
| **INV-003** | Idempotency | Duplicate logical requests must not duplicate financial effects |
| **INV-004** | Reservation safety | Reserved funds cannot be settled more than once |
| **INV-005** | Void without settlement | Void releases reservation without moving settled money |
| **INV-006** | Exactly-once commit | Commit releases applicable holds and settles the intended amount once |
| **INV-007** | Exact monetary representation | No floating-point drift, truncation, or unexplained rounding |
| **INV-008** | No lost updates | Concurrent operations preserve all valid effects |
| **INV-009** | Reconciliation | Reconstructed transaction history matches persisted balances |
| **INV-010** | Failure atomicity | Infrastructure faults must not leave partial financial mutations |
| **INV-011** | Valid lifecycle transitions | Invalid transaction state transitions are rejected |

### Accounting conventions

**Precise units:** `precise_amount = 7_500` with precision `100` represents `75.00` currency units. Tests use `long` and `BigDecimal`, not binary floating-point values.

**Settled versus inflight:** `credit_balance`, `debit_balance`, and `balance` represent settled accounting state; `inflight_debit_balance` and `inflight_credit_balance` represent reservations.

**Parent-child transactions:** Blnk's commit/void mutations may create child records with `APPLIED` or `VOID` status. Tests verify child IDs, `parent_transaction`, status, and precise amount. Queued processing can introduce `meta_data.QUEUED_PARENT_TRANSACTION`; tests must distinguish queued records from their eventual settlement records.

**Closed-system conservation:** Include the funding/world account when summing all balances involved in a test. A negative world balance can be legitimate.

**Exactly once:** A successful commit and correct balances are not sufficient on their own. Retry and duplicate-commit tests also verify that subsequent mutations cannot add financial effects.

## Test suites and coverage

The tables describe scenarios worked on in this project. **Implemented does not necessarily mean passing in every Blnk version or environment.**

### Functional API tests — `FUN-*`

| Scenario | Behavior under test |
| --- | --- |
| FUN-01 | Fund from world and verify both persisted sides |
| FUN-02 | Same-ledger transfer and source/destination accounting |
| FUN-03 | Cross-ledger transfer using the same currency |
| FUN-04 | Reject zero-amount transfer without mutation |
| FUN-05 | Retrieve transaction and compare API data with persistence |
| FUN-06 | Reject insufficient funds without overdraft |
| FUN-07 | Allow insufficient-balance transfer with explicit overdraft |

Additional functional cases covering multi-party transfers, atomicity, and rounding remain potential extensions.

### Accounting invariant tests — `ACC-*`

| Scenario | Core assertions | Invariants |
| --- | --- | --- |
| ACC-01 | Every affected balance satisfies the derived balance equation | INV-001 |
| ACC-02 | Source debit and destination credit deltas equal the transfer amount | INV-001 |
| ACC-03 | Inflight hold changes reservation fields without settlement | INV-004 |
| ACC-04 | Full commit releases holds, settles amount, and links child transaction | INV-001, INV-006 |
| ACC-05 | Void releases holds without changing settled balances | INV-001, INV-005 |
| ACC-06 | Closed-set settled balance sum remains constant after multiple transfers | Conservation |
| ACC-07 | Chained odd-valued transfers preserve exact monetary results | INV-007 |

ACC-06 includes the world/funding account in its conservation calculation. ACC-07 tracks expected balances independently using exact arithmetic.

### Transaction lifecycle tests — `LIF-*`

| Scenario | Behavior under test | Coverage / current note |
| --- | --- | --- |
| **LIF-01** | Create inflight transfer; reserve source funds without settlement | Developed |
| **LIF-02** | Full commit creates `APPLIED` settlement child and releases reservation | Developed; INV-006 |
| **LIF-03** | Partial commit settles only the requested amount | Developed; remainder semantics require confirmation |
| **LIF-04** | Commit then commit again; second request rejected, no second settlement | Developed; INV-004; observed error `TXN_ALREADY_COMMITTED` |
| **LIF-05** | Void creates `VOID` child, releases reservation, leaves settled balances unchanged | Developed; INV-005 |
| **LIF-06** | Void then commit; commit rejected and voided state preserved | Developed; INV-005, INV-011 |
| **LIF-07** | Commit then void; void rejected and settlement preserved | Developed; INV-011 |
| **LIF-08** | Commit/void an already `APPLIED`, non-inflight transfer; reject both | Developed; direct transfer requires correct `skipQueue`/`inflight` argument order |
| **LIF-09** | Commit/void unknown transaction ID; expect HTTP 404 | Test added; contract assertion |
| **LIF-10** | Expire inflight transaction, release reservation, reject subsequent commit | **Under investigation:** transaction remained `INFLIGHT` after two-minute poll |
| **LIF-11** | Partial commit followed by remainder; total settlement cannot exceed original hold | Test developed; multi-partial-commit support not yet confirmed |
| **LIF-12** | Invalid mutation statuses (`COMMIT`, `foo`, empty) rejected without state change | Test added; confirm exact API validation contract |

#### Lifecycle implementation notes

- `mutateTransactionStatus(transactionId, mutation, skipQueue, preciseAmount)` returns the raw REST Assured `Response` for **both successful and rejected** operations. It does not assert 2xx internally; tests assert the expected status themselves.
- `TransactionStateMutation` centralizes supported `commit` and `void` strings.
- `CreateTransactionRequest` supports an **optional** `inflight_expiry_date`. An overloaded 11-argument constructor preserves existing call sites; `@JsonInclude(NON_NULL)` omits the field when absent.
- `makeTransferWithInflightExpiry(...)` is intended for expiry-specific tests; regular transfers continue to use `makeTransfer(...)`.
- In the existing `makeTransfer(...)` helper, the final parameters are **`skipQueue`, then `inflight`**. To create a direct, non-inflight transfer, pass `true, false`.
- Response DTOs should tolerate optional Blnk metadata, including `QUEUED_PARENT_TRANSACTION`, while preserving fields needed for lineage assertions.
- Use `awaitTransactionStatus(...)` or Awaitility for eventual state changes. A queued response is not proof of an already-applied settlement.
- LIF-10's expiry timeout is **not** evidence that automatic expiry is supported or that the transaction expired. Investigate persisted expiry timestamps, service/worker configuration, and actual post-expiry commit behavior before asserting `EXPIRED`.
- LIF-11 assumes Blnk permits committing the remainder after an initial partial commit. Confirm that the remaining reservation persists and the API accepts another commit before treating the scenario as passing.

### Planned advanced suites

- **Idempotency and state-machine exploration:** retries, replayed requests, commit/void races, and competing operations (INV-003, INV-004, INV-011).
- **Concurrency:** simultaneous transfers, insufficient-funds contention, lost-update detection (INV-008).
- **Reconciliation:** reconstruct account state from persisted transaction records (INV-009).
- **Fault injection:** database, Redis, network, queue, and worker failures (INV-010).
- **Property/model-based testing:** jqwik and Hypothesis sequences with invariant checks.
- **Performance:** k6 load, contention, spike, stress, and soak testing.

## Framework design

```text
ledger-quality-platform/
├── src/test/java/io/portfolio/ledgerqa/
│   ├── api/              REST Assured clients and endpoint abstractions
│   ├── assertions/       LedgerInvariantAssertions
│   ├── db/               Database access and persisted models
│   ├── domain/           Transaction state mutation enums
│   ├── functional/       FunctionalTestBase and functional tests
│   ├── model/            Request/response DTOs
│   └── ...               Accounting, lifecycle, and supporting tests
├── ledger-fuzz/          Planned/expanding Python adversarial suite
├── pom.xml
└── README.md
```

*Illustrative layout; consult the repository for the exact current tree.*

The framework separates **actions** (API requests), **observations** (HTTP and PostgreSQL), **assertions** (exact deltas, snapshots, lineage, invariants), and **reporting** (Allure evidence).

Reusable helpers include `createLedger`, `createBalance`, `fundBalanceFromWorld`, `makeTransfer`, `makeTransferWithInflightExpiry`, `mutateTransactionStatus`, `fetchPersistedBalance`, `fetchPersistedTransaction`, and `awaitTransactionStatus`.

## Running the project

### Prerequisites

- JDK 21 and Maven or the Maven wrapper
- Docker Engine / Docker Desktop
- Running Blnk API/worker services, PostgreSQL, and Redis
- Test configuration pointing at an **isolated, disposable environment**

> **Safety:** Tests create ledger records, deliberately trigger overdrafts and rejection paths, and should never target production financial data.

### Java integration tests

```bash
./mvnw verify
```

Windows PowerShell:

```powershell
.\mvnw.cmd verify
```

Or use an installed Maven executable:

```powershell
mvn verify
mvn -Dtest=AccountingTests test
mvn -Dtest=LifecycleTests test
mvn -Dtest=LifecycleTests#shouldRejectCommitAfterVoid test
```

JUnit tags include `accounting`, `lifecycle`, and individual IDs such as `ACC-04`, `LIF-04`, and `LIF-10`.

Supply the API base URL, database connection details, and any credentials through the repository's test configuration. No fixed hostnames or secrets are assumed here.

### Python adversarial suite (planned/when present)

```bash
cd ledger-fuzz
python -m venv .venv
# Linux/macOS: source .venv/bin/activate
# Windows PowerShell: .venv\Scripts\Activate.ps1
pip install -e ".[dev]"
pytest
```

### Reports

Allure captures named steps and API/transaction evidence. Consult `pom.xml` and the repository's installed Allure CLI/plugin configuration for report generation.

## Engineering principles

- **Independent oracle:** API success does not prove accounting correctness; verify PostgreSQL state.
- **Deterministic isolation:** Unique references, dedicated ledgers, and nonshared mutable balances.
- **Exact arithmetic:** `long` precise amounts and `BigDecimal` comparisons, never `double` for money.
- **Eventual consistency:** Poll async state transitions with bounded timeouts rather than fixed sleeps.
- **No false positives:** Verify holds were created before checking their release; compare settled snapshots after rejection.
- **Contract fidelity:** Separate documented Blnk behavior, observed results, and assumptions—especially for partial commits, queued transactions, and expiry.
- **Reproducibility:** Record response bodies, transaction IDs, balance IDs, and before/after database snapshots.
- **Stateful correctness:** Validate sequences and invariants, not only isolated endpoint responses.

## Roadmap

- [x] Establish Java API and PostgreSQL verification framework
- [x] Define 11 financial ledger invariants
- [x] Develop core functional transfer and rejection scenarios
- [x] Develop accounting scenarios ACC-01–ACC-07
- [x] Develop lifecycle scenarios LIF-01–LIF-09 and LIF-12
- [x] Add optional inflight expiry field without breaking existing request constructors
- [x] Add Awaitility-based expiry test scenario
- [ ] Diagnose and stabilize LIF-10 automatic expiry behavior
- [ ] Confirm and stabilize LIF-11 remainder-commit behavior
- [ ] Verify all lifecycle scenarios against the running Blnk version and CI
- [ ] Expand concurrency, idempotency, and competing-operation tests
- [ ] Implement independent transaction-log reconciliation
- [ ] Add fault injection and recovery verification
- [ ] Build jqwik and Python Hypothesis model-based suites
- [ ] Add k6 performance and contention baselines
- [ ] Enforce regression gates and publish reproducible Allure results in CI

> Status reflects the development work discussed to date, **not** a fresh repository audit or claim that every test passes.

## Project purpose

This is my portfolio exercise in **financial systems quality engineering**: testing whether financial state remains correct under normal operations, edge cases, retries, concurrency, and failure—not merely whether endpoints return successful responses.

**Repository:** [Pybool/eko_blnk_ledger-qa-framework](https://github.com/Pybool/eko_blnk_ledger-qa-framework)
