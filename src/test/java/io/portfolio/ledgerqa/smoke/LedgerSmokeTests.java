package io.portfolio.ledgerqa.smoke;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;

import io.portfolio.ledgerqa.api.ApiClientFactory;
import io.portfolio.ledgerqa.api.LedgerClient;
import io.portfolio.ledgerqa.base.BaseTest;
import io.portfolio.ledgerqa.model.requests.CreateLedgerRequest;
import io.portfolio.ledgerqa.model.responses.CreateLedgerResponse;
import io.portfolio.ledgerqa.testsupport.TestData;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.restassured.response.Response;
import io.qameta.allure.Allure;

@Epic("Ledger Quality Platform")
@Feature("Create a ledger")
@Tag("smoke")
class LedgerSmokeTests extends BaseTest {

    @Test
    @DisplayName("should create a ledger successfully")
    @Description("""
            Verifies that a ledger can be created successfully
            with the supplied name and metadata.
            """)
    @Severity(SeverityLevel.CRITICAL)
    void createLedger() {

        String ledgerName = TestData.unique("qa-ledger");
        String projectOwner = "Blnk Tech";

        LedgerClient ledgerClient = ApiClientFactory.ledgerClient();

        CreateLedgerRequest request = CreateLedgerRequest.of(
                ledgerName,
                projectOwner);

        Response ledgerResponse = Allure.step(
                "Create ledger via POST /ledgers",
                () -> ledgerClient.create(request));

        CreateLedgerResponse createdLedger = Allure.step(
                "Deserialize created ledger response",
                () -> ledgerResponse.as(CreateLedgerResponse.class));

        Allure.step("Verify created ledger response contract", () -> {

            assertThat(ledgerResponse.statusCode())
                    .isEqualTo(201);

            assertThat(createdLedger.ledgerId())
                    .startsWith("ldg_");

            assertThat(createdLedger.name())
                    .isEqualTo(ledgerName);

            assertThat(createdLedger.createdAt())
                    .isNotBlank();

            assertThat(createdLedger.metadata())
                    .isNotNull();

            assertThat(createdLedger.metadata().projectOwner())
                    .isEqualTo(projectOwner);
        });
    }
}