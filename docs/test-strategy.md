# Test Strategy

## Layers

1. Smoke
2. Contract
3. Functional
4. Accounting invariants
5. Lifecycle
6. Precision
7. Idempotency
8. Concurrency
9. Reconciliation
10. Resilience
11. Property/model-based testing
12. Performance

## Retry policy

Product failures are not hidden by automatic retries.
Environmental retries must be explicit, justified, and observable.
Flaky tests are quarantined, investigated, and assigned an owner.
