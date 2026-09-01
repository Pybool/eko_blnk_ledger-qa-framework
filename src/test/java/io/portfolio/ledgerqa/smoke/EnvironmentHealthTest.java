package io.portfolio.ledgerqa.smoke;

import io.portfolio.ledgerqa.base.BaseTest;
import org.junit.jupiter.api.Test;

class EnvironmentHealthTest extends BaseTest {

    @Test
    void environmentShouldBeHealthy() {
        // If API or DB is unhealthy,
        // EnvironmentHealthExtension fails before this runs.
    }
}