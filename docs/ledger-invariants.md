# Ledger Invariants

| ID | Invariant |
|---|---|
| INV-001 | `balance = credit_balance - debit_balance` |
| INV-002 | Failed operations must not mutate settled balances |
| INV-003 | Duplicate requests must not create duplicate financial effects |
| INV-004 | Reserved funds cannot be settled more than once |
| INV-005 | Void releases an inflight reservation without settlement |
| INV-006 | Commit moves inflight effects to settled state exactly once |
| INV-007 | Monetary values must be represented exactly |
| INV-008 | Concurrent writes must not produce lost updates |
| INV-009 | Reconstructed ledger state must reconcile with persisted balances |
| INV-010 | Infrastructure failure must not leave partial financial mutation |
| INV-011 | Transaction lifecycle transitions must obey the allowed state machine |
