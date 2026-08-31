package io.portfolio.ledgerqa.db.repository;

import io.portfolio.ledgerqa.db.DatabaseClient;
import io.portfolio.ledgerqa.db.model.TransactionRecord;

import java.util.Optional;

public final class TransactionRepository {

    private static final String FIND_BY_ID = """
            SELECT
                transaction_id,
                reference,
                source,
                destination,
                amount,
                precise_amount,
                currency,
                status
            FROM blnk.transactions
            WHERE transaction_id = ?
            """;

    private static final String FIND_BY_REFERENCE = """
            SELECT
                transaction_id,
                reference,
                source,
                destination,
                amount,
                precise_amount,
                currency,
                status
            FROM blnk.transactions
            WHERE reference = ?
            ORDER BY created_at DESC
            LIMIT 1
            """;

    private final DatabaseClient database;

    public TransactionRepository(
            DatabaseClient database) {
        this.database = database;
    }

    public Optional<TransactionRecord> findById(
            String transactionId) {

        return database.queryOne(
                FIND_BY_ID,
                this::mapTransaction,
                transactionId);
    }

    public Optional<TransactionRecord> findByReference(
            String reference) {

        return database.queryOne(
                FIND_BY_REFERENCE,
                this::mapTransaction,
                reference);
    }

    private TransactionRecord mapTransaction(
            java.sql.ResultSet resultSet) throws java.sql.SQLException {

        return new TransactionRecord(
                resultSet.getString("transaction_id"),
                resultSet.getString("reference"),
                resultSet.getString("source"),
                resultSet.getString("destination"),
                resultSet.getBigDecimal("amount"),
                resultSet.getBigDecimal("precise_amount"),
                resultSet.getString("currency"),
                resultSet.getString("status"));
    }
}