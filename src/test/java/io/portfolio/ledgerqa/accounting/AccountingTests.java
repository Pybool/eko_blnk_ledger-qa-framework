package io.portfolio.ledgerqa.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import io.portfolio.ledgerqa.functional.FunctionalTestBase;

import java.math.BigDecimal;
import java.util.List;

import io.portfolio.ledgerqa.assertions.LedgerInvariantAssertions;
import io.portfolio.ledgerqa.db.model.BalanceRecord;
import io.portfolio.ledgerqa.model.responses.CreateBalanceResponse;
import io.portfolio.ledgerqa.model.responses.CreateLedgerResponse;
import io.portfolio.ledgerqa.testsupport.TestData;
import io.qameta.allure.Allure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.qameta.allure.Epic;
import io.qameta.allure.Story;

public class AccountingTests extends FunctionalTestBase {
    @Test
    @Tag("accounting")
    @Tag("ACC-01")
    @Epic("Accounting Invariants")
    @Story("ACC-01 - Derived balance integrity")
    @DisplayName("should maintain derived balance integrity across all affected balances")
    void shouldMaintainDerivedBalanceIntegrityAcrossAllAffectedBalances() {

        long fundingAmount = 8_000;
        long transferAmount = 2_000;
        String currency = "NGN";
        int precision = 100;
        long overdraftLimit = 0;

        // Given
        CreateLedgerResponse ledger = createLedger(TestData.unique("fun-04-ledger"));

        CreateBalanceResponse world = createBalance(ledger.ledgerId(), currency);

        CreateBalanceResponse source1 = createBalance(ledger.ledgerId(), currency);
        CreateBalanceResponse source2 = createBalance(ledger.ledgerId(), currency);
        CreateBalanceResponse destination = createBalance(ledger.ledgerId(), currency);

        fundBalanceFromWorld(world.balanceId(),
                source1.balanceId(), fundingAmount, currency, overdraftLimit, precision);

        fundBalanceFromWorld(world.balanceId(),
                source2.balanceId(), fundingAmount, currency, overdraftLimit, precision);

        // Database snapshots before transfer
        BalanceRecord source1Before = fetchPersistedBalance(source1.balanceId());

        BalanceRecord source2Before = fetchPersistedBalance(source2.balanceId());

        BalanceRecord destinationBefore = fetchPersistedBalance(destination.balanceId());

        Allure.step("Verify source and destination balances before transfer", () -> {

            assertThat(source1Before.creditBalance())
                    .isEqualByComparingTo(BigDecimal.valueOf(fundingAmount));

            assertThat(source2Before.debitBalance())
                    .isEqualByComparingTo(BigDecimal.ZERO);

            assertThat(destinationBefore.creditBalance())
                    .isEqualByComparingTo(BigDecimal.ZERO);

            assertThat(destinationBefore.debitBalance())
                    .isEqualByComparingTo(BigDecimal.ZERO);

            assertThat(destinationBefore.balance())
                    .isEqualByComparingTo(BigDecimal.ZERO);
        });

        makeTransfer(
                source1.balanceId(),
                destination.balanceId(),
                transferAmount, currency, precision,
                false,
                overdraftLimit,
                true, false);

        makeTransfer(
                source2.balanceId(),
                destination.balanceId(),
                transferAmount, currency, precision,
                false,
                overdraftLimit,
                true, false);

        // Then fetch databasse persisted data Via SQl queriies
        BalanceRecord worldBalance = fetchPersistedBalance(world.balanceId());
        BalanceRecord source1After = fetchPersistedBalance(source1.balanceId());
        BalanceRecord source2After = fetchPersistedBalance(source2.balanceId());
        BalanceRecord destinationAfter = fetchPersistedBalance(destination.balanceId());

        LedgerInvariantAssertions.assertMultiDerivedBalanceInvariant(
                List.of(worldBalance, source1After, source2After, destinationAfter));

    }

}
