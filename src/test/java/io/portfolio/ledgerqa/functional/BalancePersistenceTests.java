package io.portfolio.ledgerqa.functional;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.portfolio.ledgerqa.api.ApiClientFactory;
import io.portfolio.ledgerqa.base.BaseTest;
import io.portfolio.ledgerqa.db.RepositoryFactory;
import io.portfolio.ledgerqa.db.model.BalanceRecord;
import io.portfolio.ledgerqa.model.requests.CreateBalanceRequest;
import io.portfolio.ledgerqa.model.requests.CreateLedgerRequest;
import io.portfolio.ledgerqa.model.responses.CreateBalanceResponse;
import io.portfolio.ledgerqa.model.responses.CreateLedgerResponse;
import io.portfolio.ledgerqa.testsupport.TestData;
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;

@Epic("Balance Quality Platform")
@Feature("Balances")
@Tag("functional")
class BalancePersistenceTests extends BaseTest {

    @Test
    @Story("Balance Persistence (Data Integrity)")
    @DisplayName("should persist created balance correctly")
    @Description("""
            Creates an NGN balance under a fresh ledger and verifies the API
            response and the persisted blnk.balances row agree on ledger and currency.
            """)
    @Severity(SeverityLevel.CRITICAL)
    void shouldPersistCreatedBalanceCorrectly() {
        CreatedLedgerAndBalance created = createLedgerAndBalance();
        CreateLedgerResponse ledger = created.ledger();
        CreateBalanceResponse balance = created.balance();
        Response response = created.response();

        Allure.step("Verify API response", () -> {
            assertThat(response.statusCode()).isEqualTo(201);
            assertThat(balance.balanceId()).startsWith("bln_");
            assertThat(balance.ledgerId()).isEqualTo(ledger.ledgerId());
            assertThat(balance.currency()).isEqualTo("NGN");
        });

        Allure.step("Verify balance was persisted correctly", () -> {
            BalanceRecord persisted = fetchPersistedBalance(balance.balanceId());
            assertThat(persisted.balanceId()).isEqualTo(balance.balanceId());
            assertThat(persisted.ledgerId()).isEqualTo(ledger.ledgerId());
            assertThat(persisted.currency()).isEqualTo("NGN");
        });
    }

    @Test
    @Story("Balance Persistence (Data Integrity)")
    @DisplayName("should initialise every balance component to zero for a new balance")
    @Description("""
            INV-001 must hold from creation: balance == credit_balance - debit_balance.
            A brand-new balance has all settled and inflight components at zero and version 0.
            """)
    @Severity(SeverityLevel.CRITICAL)
    void shouldInitialiseEveryBalanceComponentToZero() {
        CreateBalanceResponse balance = createLedgerAndBalance().balance();

        Allure.step("Verify API response components are zero", () -> {
            assertThat(balance.balance()).isZero();
            assertThat(balance.creditBalance()).isZero();
            assertThat(balance.debitBalance()).isZero();
            assertThat(balance.inflightBalance()).isZero();
            assertThat(balance.inflightCreditBalance()).isZero();
            assertThat(balance.inflightDebitBalance()).isZero();
            assertThat(balance.version()).isZero();
            assertThat(balance.createdAt()).isNotBlank();
        });

        Allure.step("Verify persisted components are zero and satisfy INV-001", () -> {
            BalanceRecord persisted = fetchPersistedBalance(balance.balanceId());
            assertThat(persisted.balance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(persisted.creditBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(persisted.debitBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(persisted.inflightBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(persisted.inflightCreditBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(persisted.inflightDebitBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(persisted.version()).isEqualTo(1);
            assertDerivedBalanceInvariant(persisted);
        });
    }

    @Test
    @Story("Balance API Consistency")
    @DisplayName("should fetch a balance with its correct details")
    @Severity(SeverityLevel.NORMAL)
    void shouldFetchBalanceWithCorrectDetails() {
        CreatedLedgerAndBalance created = createLedgerAndBalance();
        CreateLedgerResponse ledger = created.ledger();
        CreateBalanceResponse createdBalance = created.balance();

        Response fetchResponse = Allure.step(
                "Fetch balance via GET /balances/{id}",
                () -> ApiClientFactory.balanceClient().getById(createdBalance.balanceId()));

        attachJson("Fetch Balance Response", fetchResponse);

        CreateBalanceResponse fetched = fetchResponse.as(CreateBalanceResponse.class);

        Allure.step("Verify fetched balance matches created balance", () -> {
            assertThat(fetchResponse.statusCode()).isEqualTo(200);
            assertThat(fetched.balanceId()).isEqualTo(createdBalance.balanceId());
            assertThat(fetched.ledgerId()).isEqualTo(ledger.ledgerId());
            assertThat(fetched.currency()).isEqualTo("NGN");
            assertThat(fetched.balance()).isZero();
            assertThat(fetched.creditBalance()).isZero();
            assertThat(fetched.debitBalance()).isZero();
            assertThat(fetched.version()).isEqualTo(1);
        });

        Allure.step("Verify fetched balance matches persisted row", () -> {
            BalanceRecord persisted = fetchPersistedBalance(createdBalance.balanceId());
            assertThat(fetched.balanceId()).isEqualTo(persisted.balanceId());
            assertThat(fetched.ledgerId()).isEqualTo(persisted.ledgerId());
            assertThat(fetched.currency()).isEqualTo(persisted.currency());
            assertThat(BigDecimal.valueOf(fetched.balance())).isEqualByComparingTo(persisted.balance());
            assertThat(fetched.version()).isEqualTo(persisted.version());
        });
    }

    @Test
    @Story("Balance Persistence (Data Integrity)")
    @DisplayName("should allow multiple balances under the same ledger and currency")
    @Severity(SeverityLevel.NORMAL)
    void shouldAllowMultipleBalancesUnderSameLedgerAndCurrency() {
        CreateLedgerResponse ledger = createLedger(TestData.unique("balance-persistence"));

        CreateBalanceResponse first = createBalance(ledger.ledgerId(), "NGN");
        CreateBalanceResponse second = createBalance(ledger.ledgerId(), "NGN");

        Allure.step("Verify both balances are distinct and share the ledger", () -> {
            assertThat(first.balanceId()).isNotEqualTo(second.balanceId());
            assertThat(first.ledgerId()).isEqualTo(ledger.ledgerId());
            assertThat(second.ledgerId()).isEqualTo(ledger.ledgerId());
        });

        Allure.step("Verify both balances are persisted", () -> {
            assertThat(fetchPersistedBalance(first.balanceId()).ledgerId()).isEqualTo(ledger.ledgerId());
            assertThat(fetchPersistedBalance(second.balanceId()).ledgerId()).isEqualTo(ledger.ledgerId());
        });
    }

    @Test
    @Story("Balance API Validation")
    @DisplayName("should not create a balance when the ledger does not exist")
    @Severity(SeverityLevel.CRITICAL)
    void shouldRejectBalanceCreationWhenLedgerDoesNotExist() {
        String missingLedgerId = TestData.unique("ldg_missing");

        Response response = Allure.step(
                "Attempt to create a balance under a non-existent ledger",
                () -> ApiClientFactory.balanceClient()
                        .create(new CreateBalanceRequest(missingLedgerId, "NGN")));

        validateBalanceNotCreated(response);
    }

    @Test
    @Story("Balance API Validation")
    @DisplayName("should not create a balance when 'ledger_id' is missing")
    @Severity(SeverityLevel.NORMAL)
    void shouldRejectBalanceCreationWhenLedgerIdIsMissing() {
        Response response = Allure.step(
                "Attempt to create a balance without ledger_id",
                () -> ApiClientFactory.balanceClient().createRaw("""
                        { "currency": "NGN" }
                        """));

        validateBalanceNotCreated(response);
    }

    @Test
    @Story("Balance API Validation")
    @DisplayName("should not create a balance when 'currency' is missing")
    @Severity(SeverityLevel.NORMAL)
    void shouldRejectBalanceCreationWhenCurrencyIsMissing() {
        CreateLedgerResponse ledger = createLedger(TestData.unique("balance-persistence"));

        Response response = Allure.step(
                "Attempt to create a balance without currency",
                () -> ApiClientFactory.balanceClient().createRaw("""
                        { "ledger_id": "%s" }
                        """.formatted(ledger.ledgerId())));

        validateBalanceNotCreated(response);
    }

    @Test
    @Story("Balance API Validation")
    @DisplayName("should not create a balance with an unknown currency code")
    @Severity(SeverityLevel.MINOR)
    void shouldRejectBalanceCreationWhenCurrencyIsUnknownCode() {
        CreateLedgerResponse ledger = createLedger(TestData.unique("balance-persistence"));

        Response response = Allure.step(
                "Attempt to create a balance with a non-ISO currency code",
                () -> ApiClientFactory.balanceClient()
                        .create(new CreateBalanceRequest(ledger.ledgerId(), "ZZZ")));

        validateBalanceNotCreated(response);
    }

    @Test
    @Story("Balance API Validation")
    @DisplayName("should not create a balance when the payload is empty")
    @Severity(SeverityLevel.NORMAL)
    void shouldRejectBalanceCreationWhenPayloadIsEmpty() {
        Response response = Allure.step(
                "Attempt to create a balance with an empty payload",
                () -> ApiClientFactory.balanceClient().createRaw("{}"));

        validateBalanceNotCreated(response);
    }

    @Test
    @Story("Balance API Validation")
    @DisplayName("should not create a balance when the payload is malformed JSON")
    @Severity(SeverityLevel.NORMAL)
    void shouldRejectBalanceCreationWhenPayloadIsMalformedJson() {
        Response response = Allure.step(
                "Attempt to create a balance with malformed JSON",
                () -> ApiClientFactory.balanceClient().createRaw("""
                        { "ledger_id": "ldg_x", "currency":
                        """));

        validateBalanceNotCreated(response);
    }

    private CreateLedgerResponse createLedger(String projectOwner) {
        CreateLedgerRequest request = CreateLedgerRequest.of(TestData.unique("ledger"), projectOwner);
        Response response = ApiClientFactory.ledgerClient().create(request);
        attachJson("Create Ledger Response", response);
        assertThat(response.statusCode()).isEqualTo(201);
        return response.as(CreateLedgerResponse.class);
    }

    private Response postBalance(String ledgerId, String currency) {
        Response response = Allure.step(
                "Create %s balance".formatted(currency),
                () -> ApiClientFactory.balanceClient()
                        .create(new CreateBalanceRequest(ledgerId, currency)));
        attachJson("Create Balance Response", response);
        return response;
    }

    private CreateBalanceResponse createBalance(String ledgerId, String currency) {
        Response response = postBalance(ledgerId, currency);
        assertThat(response.statusCode()).isEqualTo(201);
        return response.as(CreateBalanceResponse.class);
    }

    private record CreatedLedgerAndBalance(
            CreateLedgerResponse ledger,
            CreateBalanceResponse balance,
            Response response) {
    }

    private CreatedLedgerAndBalance createLedgerAndBalance() {
        CreateLedgerResponse ledger = createLedger(TestData.unique("balance-persistence"));
        Response response = postBalance(ledger.ledgerId(), "NGN");
        assertThat(response.statusCode()).isEqualTo(201);
        return new CreatedLedgerAndBalance(ledger, response.as(CreateBalanceResponse.class), response);
    }

    private BalanceRecord fetchPersistedBalance(String balanceId) {
        return RepositoryFactory.balanceRepository()
                .findById(balanceId)
                .orElseThrow(() -> new AssertionError(
                        "Balance was not persisted in database: " + balanceId));
    }

    private void assertDerivedBalanceInvariant(BalanceRecord balance) {
        assertThat(balance.balance())
                .as("INV-001 derived balance for %s", balance.balanceId())
                .isEqualByComparingTo(balance.creditBalance().subtract(balance.debitBalance()));
    }

    private void validateBalanceNotCreated(Response response) {
        attachJson("API Rejection Response HTTP " + response.statusCode(), response);
        Allure.step("Verify balance creation was rejected", () -> {
            assertThat(response.statusCode()).isBetween(400, 499);
            assertThat(response.getBody().asString()).isNotBlank();
        });
    }

    private void attachJson(String name, Response response) {
        String body = response.getBody().asPrettyString();
        System.out.printf(
                "%n===== %s =====%n%s%n%s%n=====================%n",
                name, response.getStatusLine(), body);
        Allure.addAttachment(name, "application/json", body);
    }
}
