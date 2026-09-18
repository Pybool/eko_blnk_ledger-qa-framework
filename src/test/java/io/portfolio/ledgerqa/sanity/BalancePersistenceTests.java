package io.portfolio.ledgerqa.sanity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.portfolio.ledgerqa.api.ApiClientFactory;
import io.portfolio.ledgerqa.db.model.BalanceRecord;
import io.portfolio.ledgerqa.functional.FunctionalTestBase;
import io.portfolio.ledgerqa.model.responses.CreateBalanceResponse;
import io.portfolio.ledgerqa.model.responses.CreateLedgerResponse;
import io.portfolio.ledgerqa.assertions.LedgerInvariantAssertions;


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
@Tag("sanity")
class BalancePersistenceTests extends FunctionalTestBase {

    @Test
    @Story("Balance Persistence (Data Integrity)")
    @DisplayName("should persist created balance correctly")
    @Description("""
            Creates an NGN balance under a fresh ledger and verifies the API
            response and the persisted blnk.balances row agree on ledger and currency.
            """)
    @Severity(SeverityLevel.CRITICAL)
    void shouldPersistCreatedBalanceCorrectly() {
        // When
        CreatedLedgerAndBalance created = createLedgerAndBalance();
        CreateLedgerResponse ledger = created.ledger();
        CreateBalanceResponse balance = created.balance();
        Response response = created.response();

        // Then

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
        // When
        CreateBalanceResponse balance = createLedgerAndBalance().balance();

        // Then
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
            LedgerInvariantAssertions.assertDerivedBalanceInvariant(persisted);
        });
    }

    @Test
    @Story("Balance API Consistency")
    @DisplayName("should fetch a balance with its correct details")
    @Severity(SeverityLevel.NORMAL)
    void shouldFetchBalanceWithCorrectDetails() {
        // Given
        CreatedLedgerAndBalance created = createLedgerAndBalance();
        CreateLedgerResponse ledger = created.ledger();
        CreateBalanceResponse createdBalance = created.balance();

        // When
        Response fetchResponse = Allure.step(
                "Fetch balance via GET /balances/{id}",
                () -> ApiClientFactory.balanceClient().getById(createdBalance.balanceId()));

        attachJson("Fetch Balance Response", fetchResponse);

        CreateBalanceResponse fetched = fetchResponse.as(CreateBalanceResponse.class);

        // Then
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
        // Given
        CreateLedgerResponse ledger = createLedger(TestData.unique("balance-persistence"));

        // When
        CreateBalanceResponse first = createBalance(ledger.ledgerId(), "NGN");
        CreateBalanceResponse second = createBalance(ledger.ledgerId(), "NGN");

        // Then
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

}
