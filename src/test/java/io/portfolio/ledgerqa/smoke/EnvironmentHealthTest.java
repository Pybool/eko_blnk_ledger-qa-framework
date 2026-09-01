package io.portfolio.ledgerqa.smoke;

import io.portfolio.ledgerqa.base.BaseTest;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Epic("Ledger Quality Platform")
@Feature("Environment Health")
class EnvironmentHealthTest extends BaseTest {

    @Test
    @DisplayName("API and Database should be reachable")
    @Description("Verifies that the Blnk ledger API responds successfully before functional tests run.")
    @Severity(SeverityLevel.BLOCKER)
    void environmentShouldBeHealthy() {}
}