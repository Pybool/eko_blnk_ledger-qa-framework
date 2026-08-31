package io.portfolio.ledgerqa.db;

public final class DatabaseException
        extends RuntimeException {

    public DatabaseException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}