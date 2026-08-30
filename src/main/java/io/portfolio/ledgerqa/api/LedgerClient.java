package io.portfolio.ledgerqa.api;

public final class LedgerClient {
    private final TransactionClient transactions;
    private final BalanceClient balances;

    public LedgerClient(TransactionClient transactions, BalanceClient balances) {
        this.transactions = transactions;
        this.balances = balances;
    }

    public TransactionClient transactions() {
        return transactions;
    }

    public BalanceClient balances() {
        return balances;
    }
}
