package io.portfolio.ledgerqa.testsupport;

import io.portfolio.ledgerqa.db.DatabaseClientFactory;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class DatabaseCleanupExtension
        implements BeforeEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {

        DatabaseCleaner cleaner =
                new DatabaseCleaner(
                        DatabaseClientFactory.create()
                );

        cleaner.clean();
    }
}