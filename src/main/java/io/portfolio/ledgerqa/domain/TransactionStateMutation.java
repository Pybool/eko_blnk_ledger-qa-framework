package io.portfolio.ledgerqa.domain;

public enum TransactionStateMutation {
    COMMIT("commit"),
    VOID("void");

    private final String value;

    TransactionStateMutation(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}