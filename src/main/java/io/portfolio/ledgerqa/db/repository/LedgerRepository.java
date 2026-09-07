package io.portfolio.ledgerqa.db.repository;

import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.portfolio.ledgerqa.db.DatabaseClient;
import io.portfolio.ledgerqa.db.model.LedgerRecord;

public final class LedgerRepository {

    private static final String FIND_BY_ID = """
            SELECT
                name,
                ledger_id,
                meta_data,
                created_at
            FROM blnk.ledgers
            WHERE ledger_id = ?
            """;

    private static final String FIND_BY_PROJECT_OWNER = """
            SELECT
                name,
                ledger_id,
                meta_data,
                created_at
            FROM blnk.ledgers
            WHERE meta_data ->> 'project_owner' = ?
            """;

    private final DatabaseClient database;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LedgerRepository(DatabaseClient database) {
        this.database = database;
    }

    private LedgerRecord.Metadata parseMetadata(String json) {
        try {
            return objectMapper.readValue(
                    json,
                    LedgerRecord.Metadata.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to deserialize ledger metadata: " + json,
                    e);
        }
    }

    public Optional<LedgerRecord> findById(String ledgerId) {
        return database.queryOne(
                FIND_BY_ID,
                resultSet -> new LedgerRecord(
                        resultSet.getString("name"),
                        resultSet.getString("ledger_id"),
                        parseMetadata(resultSet.getString("meta_data")),
                        resultSet.getTimestamp("created_at").toInstant()),
                ledgerId);
    }

    public Optional<LedgerRecord> findByProjectOwner(String projectOwner) {
        return database.queryOne(
                FIND_BY_PROJECT_OWNER,
                resultSet -> new LedgerRecord(
                        resultSet.getString("name"),
                        resultSet.getString("ledger_id"),
                        parseMetadata(resultSet.getString("meta_data")),
                        resultSet.getTimestamp("created_at").toInstant()),
                projectOwner);
    }
}