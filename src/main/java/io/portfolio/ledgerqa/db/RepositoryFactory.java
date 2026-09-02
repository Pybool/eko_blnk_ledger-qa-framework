package io.portfolio.ledgerqa.db;

import io.portfolio.ledgerqa.db.repository.BalanceRepository;
import io.portfolio.ledgerqa.db.repository.LedgerRepository;
import io.portfolio.ledgerqa.db.repository.TransactionRepository;

public final class RepositoryFactory {

    private RepositoryFactory() {}

    public static LedgerRepository ledgerRepository() {
        return new LedgerRepository(DatabaseClientFactory.create());
    }

    public static BalanceRepository balanceRepository() {
        return new BalanceRepository(DatabaseClientFactory.create());
    }

    public static TransactionRepository transactionRepository() {
        return new TransactionRepository(DatabaseClientFactory.create());
    }
}