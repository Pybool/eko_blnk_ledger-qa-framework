package io.portfolio.ledgerqa.testsupport;

import io.portfolio.ledgerqa.db.DatabaseClientFactory;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class TestSuiteLifecycleExtension
        implements BeforeAllCallback {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(
            TestSuiteLifecycleExtension.class);

    @Override
    public void beforeAll(ExtensionContext context) {

        context.getRoot()
                .getStore(NAMESPACE)
                .getOrComputeIfAbsent(
                        "suite-lifecycle",
                        key -> new SuiteLifecycle());
    }

    private static final class SuiteLifecycle implements ExtensionContext.Store.CloseableResource {

        private final DatabaseCleaner databaseCleaner;

        private SuiteLifecycle() {

            this.databaseCleaner = new DatabaseCleaner(DatabaseClientFactory.create());

            databaseCleaner.clean();

            System.out.println("QA test suite started. Run ID: " + TestData.runId());
        }

        @Override
        public void close() {
            // databaseCleaner.clean();
            System.out.println("QA test suite finished. Database cleaned.");
        }
    }
}