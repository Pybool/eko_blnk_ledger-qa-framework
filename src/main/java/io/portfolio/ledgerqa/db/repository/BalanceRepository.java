package io.portfolio.ledgerqa.db.repository;

import io.portfolio.ledgerqa.db.DatabaseClient;
import io.portfolio.ledgerqa.db.model.BalanceRecord;

import java.util.Optional;

public final class BalanceRepository {

    private static final String FIND_BY_ID = """
            SELECT
                balance_id,
                ledger_id,
                balance,
                credit_balance,
                debit_balance,
                inflight_balance,
                inflight_credit_balance,
                inflight_debit_balance,
                version
            FROM blnk.balances
            WHERE balance_id = ?
            """;

    private final DatabaseClient database;

    public BalanceRepository(DatabaseClient database) {
        this.database = database;
    }

    public Optional<BalanceRecord> findById(String balanceId) {

        return database.queryOne(
                FIND_BY_ID,
                resultSet -> new BalanceRecord(
                        resultSet.getString("balance_id"),
                        resultSet.getString("ledger_id"),
                        resultSet.getBigDecimal("balance"),
                        resultSet.getBigDecimal("credit_balance"),
                        resultSet.getBigDecimal("debit_balance"),
                        resultSet.getBigDecimal("inflight_balance"),
                        resultSet.getBigDecimal("inflight_credit_balance"),
                        resultSet.getBigDecimal("inflight_debit_balance"),
                        resultSet.getLong("version")),
                balanceId);
    }
}