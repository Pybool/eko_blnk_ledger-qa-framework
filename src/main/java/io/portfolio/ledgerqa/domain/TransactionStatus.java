package io.portfolio.ledgerqa.domain;

public enum TransactionStatus {
    QUEUED,
    APPLIED,
    INFLIGHT,
    VOID,
    REJECTED
}
