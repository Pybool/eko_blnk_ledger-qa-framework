package io.portfolio.ledgerqa.db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DatabaseClient {

    private final String url;
    private final String username;
    private final String password;

    public DatabaseClient(
            String url,
            String username,
            String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    public boolean isHealthy(int timeoutSeconds) {
        try (Connection connection = connect()) {
            return connection.isValid(timeoutSeconds);
        } catch (SQLException exception) {
            return false;
        }
    }

    public <T> Optional<T> queryOne(
            String sql,
            RowMapper<T> mapper,
            Object... parameters) {

        try (
            Connection connection = connect();
            PreparedStatement statement = connection.prepareStatement(sql)) {

            setParameters(statement, parameters);

            try (ResultSet resultSet = statement.executeQuery()) {

                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(
                        mapper.map(resultSet));
            }

        } catch (SQLException exception) {

            throw new DatabaseException(
                    "Database query failed",
                    exception);
        }
    }

    public <T> List<T> queryMany(
            String sql,
            RowMapper<T> mapper,
            Object... parameters) {

        try (
            Connection connection = connect();
            PreparedStatement statement = connection.prepareStatement(sql)) {

            setParameters(statement, parameters);

            try (ResultSet resultSet = statement.executeQuery()) {

                List<T> results = new ArrayList<>();

                while (resultSet.next()) {
                    results.add(
                            mapper.map(resultSet));
                }

                return results;
            }

        } catch (SQLException exception) {

            throw new DatabaseException(
                    "Database query failed",
                    exception);
        }
    }

    public int execute(
            String sql,
            Object... parameters) {

        try (
                Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            setParameters(statement, parameters);

            return statement.executeUpdate();

        } catch (SQLException exception) {

            throw new DatabaseException(
                    "Database update failed",
                    exception);
        }
    }

    private void setParameters(
            PreparedStatement statement,
            Object... parameters) throws SQLException {

        for (int index = 0; index < parameters.length; index++) {

            statement.setObject(
                    index + 1,
                    parameters[index]);
        }
    }
}