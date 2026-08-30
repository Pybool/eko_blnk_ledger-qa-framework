package io.portfolio.ledgerqa.api;

public final class LedgerClientFactory {
    private LedgerClientFactory() {}

    public static LedgerClient create() {
        return new LedgerClient(new TransactionClient(), new BalanceClient());
    }
}
