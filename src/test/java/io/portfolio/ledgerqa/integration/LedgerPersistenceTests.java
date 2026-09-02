package io.portfolio.ledgerqa.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.portfolio.ledgerqa.api.ApiClientFactory;
import io.portfolio.ledgerqa.api.LedgerClient;
import io.portfolio.ledgerqa.base.BaseTest;
import io.portfolio.ledgerqa.db.RepositoryFactory;
import io.portfolio.ledgerqa.db.model.LedgerRecord;
import io.portfolio.ledgerqa.db.repository.LedgerRepository;
import io.portfolio.ledgerqa.helpers.Helpers;
import io.portfolio.ledgerqa.model.requests.CreateLedgerRequest;
import io.portfolio.ledgerqa.model.responses.CreateLedgerResponse;
import io.portfolio.ledgerqa.testsupport.TestData;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;

@Epic("Ledger Quality Platform")
@Feature("Ledgers")
@Tag("integration")
class LedgerPersistenceTests extends BaseTest {

    @Test
    @Story("Ledger Persistence")
    @DisplayName("should persist created ledger correctly")
    @Severity(SeverityLevel.CRITICAL)
    void shouldPersistCreatedLedgerCorrectly() {
        String ledgerName = TestData.unique("qa-ledger");
        String projectOwner = TestData.unique("Blnk Tech");

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

        Allure.addAttachment(
                "Create Ledger API Response",
                "application/json",
                ledgerResponse.getBody().asPrettyString());

        Allure.step("Verify API returns valid 201 response", () -> {

            assertThat(ledgerResponse.statusCode()).isEqualTo(201);
            assertThat(ledgerResponse.statusCode()).isEqualTo(201);
            assertThat(createdLedger.ledgerId()).startsWith("ldg_");
            assertThat(createdLedger.name()).isEqualTo(ledgerName);
            assertThat(createdLedger.metadata().projectOwner()).isEqualTo(projectOwner);
        });

        LedgerRepository ledgerRepository = RepositoryFactory.ledgerRepository();
        LedgerRecord persistedLedger = ledgerRepository
                .findById(createdLedger.ledgerId())
                .orElseThrow(() -> new AssertionError(
                        "Ledger was not persisted in database: " + createdLedger.ledgerId()));

        Allure.addAttachment(
                "Database Query Response",
                "application/json",
                Helpers.toPrettyJson(persistedLedger));

        Allure.step("Data Integrity Test to verify ledger data returned via API matches ledger data in database",
                () -> {

                    assertThat(persistedLedger.ledgerId())
                            .isEqualTo(createdLedger.ledgerId());

                    assertThat(persistedLedger.name()).isEqualTo(createdLedger.name());
                    assertThat(persistedLedger.metadata()).isNotNull();

                    assertThat(persistedLedger.metadata().projectOwner())
                            .isEqualTo(createdLedger.metadata().projectOwner());
                });

    }
}
