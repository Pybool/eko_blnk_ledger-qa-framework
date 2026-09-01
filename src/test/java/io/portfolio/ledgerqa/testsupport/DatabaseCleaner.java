package io.portfolio.ledgerqa.testsupport;

import io.portfolio.ledgerqa.db.DatabaseClient;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class DatabaseCleaner {

    private static final String SCHEMA = "blnk";

    private final DatabaseClient database;

    public DatabaseCleaner(DatabaseClient database) {
        this.database = database;
    }

    public void clean() {

        try (Connection connection = database.connect()) {

            List<String> tables = getTables(connection);

            if (tables.isEmpty()) {
                return;
            }

            String tableList = tables.stream()
                    .map(table -> "\"" + SCHEMA + "\".\"" + table + "\"")
                    .reduce((left, right) -> left + ", " + right)
                    .orElseThrow();

            String truncateSql = "TRUNCATE TABLE " +
                    tableList +
                    " RESTART IDENTITY CASCADE";

            try (Statement statement = connection.createStatement()) {
                statement.execute(truncateSql);
            }

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to clean test database",
                    exception);
        }
    }

    private List<String> getTables(Connection connection)
            throws Exception {

        String sql = """
                SELECT tablename
                FROM pg_tables
                WHERE schemaname = 'blnk'
                """;

        List<String> tables = new ArrayList<>();

        try (
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                tables.add(
                        resultSet.getString("tablename"));
            }
        }

        return tables;
    }
}