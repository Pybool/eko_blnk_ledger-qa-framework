# Architecture

## Quality platform

```text
                     +---------------------+
                     |    Ledger System    |
                     +----------+-----------+
                                |
            +-------------------+-------------------+
            |                   |                   |
            v                   v                   v
   Java SDET Framework    Python Model/Fuzzer      k6
      deterministic          generative /        performance
      verification          adversarial           testing
            |                   |                   |
            +-------------------+-------------------+
                                v
                     Accounting Invariants
                      + Reconciliation
```

The Java framework is the primary regression system.
Python/Hypothesis is a specialized adversarial testing subsystem.
