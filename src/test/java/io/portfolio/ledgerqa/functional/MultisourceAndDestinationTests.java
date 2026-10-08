package io.portfolio.ledgerqa.functional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.concurrent.atomic.AtomicReference;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.portfolio.ledgerqa.db.model.BalanceRecord;
import io.portfolio.ledgerqa.db.model.TransactionRecord;
import io.portfolio.ledgerqa.domain.TransactionDestination;
import io.portfolio.ledgerqa.domain.TransactionSource;

import io.portfolio.ledgerqa.model.requests.TransactionAttempt;
import io.portfolio.ledgerqa.model.responses.CreateBalanceResponse;
import io.portfolio.ledgerqa.model.responses.CreateLedgerResponse;
import io.portfolio.ledgerqa.model.responses.CreateMultiDestinationTransactionResponse;
import io.portfolio.ledgerqa.model.responses.CreateMultiSourceTransactionResponse;
import io.portfolio.ledgerqa.assertions.LedgerInvariantAssertions;

import io.portfolio.ledgerqa.testsupport.TestData;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

@Epic("Ledger Quality Platform")
@Feature("Balances")
@Tag("functional")
class MultisourceAndDestinationTests extends FunctionalTestBase {

        @Test
        @Tag("FUN-10A")
        @Story("FUN-10 - Multi-source fixed distribution")
        @DisplayName("should debit each source by its fixed distribution and credit the destination with the total")
        void shouldSplitTransactionAcrossSourcesUsingFixedDistribution() {

                long fundingAmount = 10_000;
                long totalTransferAmount = 10_000;
                long source1Distribution = 3_000;
                long source2Distribution = 7_000;

                String currency = "NGN";
                int precision = 100;

                // Given
                CreateLedgerResponse ledger = createLedger(TestData.unique("fun-10-ledger"));

                CreateBalanceResponse worldBalance = createBalance(ledger.ledgerId(), currency);

                CreateBalanceResponse source1Balance = createBalance(ledger.ledgerId(), currency);

                CreateBalanceResponse source2Balance = createBalance(ledger.ledgerId(), currency);

                CreateBalanceResponse destinationBalance = createBalance(ledger.ledgerId(), currency);

                fundBalanceFromWorld(
                                worldBalance.balanceId(),
                                source1Balance.balanceId(),
                                fundingAmount,
                                currency,
                                0,
                                precision);

                fundBalanceFromWorld(
                                worldBalance.balanceId(),
                                source2Balance.balanceId(),
                                fundingAmount,
                                currency,
                                0,
                                precision);

                // Database snapshots before transfer
                BalanceRecord source1BeforeTransfer = fetchPersistedBalance(source1Balance.balanceId());

                BalanceRecord source2BeforeTransfer = fetchPersistedBalance(source2Balance.balanceId());

                BalanceRecord destinationBeforeTransfer = fetchPersistedBalance(destinationBalance.balanceId());

                Allure.step("Verify multi-source transfer preconditions", () -> {

                        assertThat(source1BeforeTransfer.balance())
                                        .isEqualByComparingTo(BigDecimal.valueOf(fundingAmount));

                        assertThat(source2BeforeTransfer.balance())
                                        .isEqualByComparingTo(BigDecimal.valueOf(fundingAmount));

                        assertThat(destinationBeforeTransfer.balance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);
                });

                List<TransactionSource> sources = List.of(
                                TransactionSource.fixed(
                                                source2Balance.balanceId(),
                                                source2Distribution),

                                TransactionSource.fixed(
                                                source1Balance.balanceId(),
                                                source1Distribution));

                // When
                CreateMultiSourceTransactionResponse response = makeMultiSourceTransfer(
                                sources,
                                destinationBalance.balanceId(),
                                totalTransferAmount,
                                currency,
                                precision,
                                true, true);

                // Database snapshots after transfer
                BalanceRecord source1AfterTransfer = fetchPersistedBalance(source1Balance.balanceId());

                BalanceRecord source2AfterTransfer = fetchPersistedBalance(source2Balance.balanceId());

                BalanceRecord destinationAfterTransfer = fetchPersistedBalance(destinationBalance.balanceId());

                // Then
                Allure.step("Verify multi-source transaction was applied", () -> {
                        assertThat(response.status())
                                        .isEqualTo("APPLIED");

                        assertThat(response.preciseAmount())
                                        .isEqualTo(totalTransferAmount);
                });

                Allure.step("Verify each source was debited by its exact fixed distribution", () -> {

                        assertThat(source1AfterTransfer.debitBalance())
                                        .isEqualByComparingTo(
                                                        source1BeforeTransfer.debitBalance()
                                                                        .add(BigDecimal.valueOf(source1Distribution)));

                        assertThat(source1AfterTransfer.balance())
                                        .isEqualByComparingTo(
                                                        source1BeforeTransfer.balance()
                                                                        .subtract(BigDecimal
                                                                                        .valueOf(source1Distribution)));

                        assertThat(source2AfterTransfer.debitBalance())
                                        .isEqualByComparingTo(
                                                        source2BeforeTransfer.debitBalance()
                                                                        .add(BigDecimal.valueOf(source2Distribution)));

                        assertThat(source2AfterTransfer.balance())
                                        .isEqualByComparingTo(
                                                        source2BeforeTransfer.balance()
                                                                        .subtract(BigDecimal
                                                                                        .valueOf(source2Distribution)));
                });

                Allure.step("Verify destination received the total source distribution", () -> {

                        assertThat(destinationAfterTransfer.creditBalance())
                                        .isEqualByComparingTo(
                                                        destinationBeforeTransfer.creditBalance()
                                                                        .add(BigDecimal.valueOf(totalTransferAmount)));

                        assertThat(destinationAfterTransfer.balance())
                                        .isEqualByComparingTo(
                                                        destinationBeforeTransfer.balance()
                                                                        .add(BigDecimal.valueOf(totalTransferAmount)));
                });

                Allure.step("Verify total source debits equal destination credit", () -> {

                        BigDecimal source1DebitDelta = source1AfterTransfer.debitBalance()
                                        .subtract(source1BeforeTransfer.debitBalance());

                        BigDecimal source2DebitDelta = source2AfterTransfer.debitBalance()
                                        .subtract(source2BeforeTransfer.debitBalance());

                        BigDecimal totalSourceDebits = source1DebitDelta.add(source2DebitDelta);

                        BigDecimal destinationCreditDelta = destinationAfterTransfer.creditBalance()
                                        .subtract(destinationBeforeTransfer.creditBalance());

                        assertThat(totalSourceDebits)
                                        .isEqualByComparingTo(destinationCreditDelta);

                        assertThat(totalSourceDebits)
                                        .isEqualByComparingTo(BigDecimal.valueOf(totalTransferAmount));
                });

                Allure.step("Verify INV-001 holds for all affected balances", () -> {
                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(source1AfterTransfer);
                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(source2AfterTransfer);
                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(destinationAfterTransfer);
                });
        }

        @Test
        @Tag("FUN-10B")
        @Story("FUN-10 - Multi-source fixed distribution -rollback")
        @DisplayName("should rollback each source and destination when transfer from a source fails for any reason")
        void shouldRollBackSplitTransactionAcrossSourcesOnFailureUsingFixedDistribution() {
                long source1FundingAmount = 10_000;
                long source2FundingAmount = 6_000;

                long totalTransferAmount = 10_000;
                long source1Distribution = 2_500;
                long source2Distribution = 7_000;

                String currency = "NGN";
                int precision = 100;

                // Given
                CreateLedgerResponse ledger = createLedger(TestData.unique("fun-10-ledger"));

                CreateBalanceResponse worldBalance = createBalance(ledger.ledgerId(), currency);

                CreateBalanceResponse source1Balance = createBalance(ledger.ledgerId(), currency);

                CreateBalanceResponse source2Balance = createBalance(ledger.ledgerId(), currency);

                CreateBalanceResponse destinationBalance = createBalance(ledger.ledgerId(), currency);

                fundBalanceFromWorld(
                                worldBalance.balanceId(),
                                source1Balance.balanceId(),
                                source1FundingAmount,
                                currency,
                                0,
                                precision);

                fundBalanceFromWorld(
                                worldBalance.balanceId(),
                                source2Balance.balanceId(),
                                source2FundingAmount,
                                currency,
                                0,
                                precision);

                // Database snapshots before transfer
                BalanceRecord source1BeforeTransfer = fetchPersistedBalance(source1Balance.balanceId());

                BalanceRecord source2BeforeTransfer = fetchPersistedBalance(source2Balance.balanceId());

                BalanceRecord destinationBeforeTransfer = fetchPersistedBalance(destinationBalance.balanceId());

                Allure.step("Verify multi-source transfer preconditions", () -> {

                        assertThat(source1BeforeTransfer.balance())
                                        .isEqualByComparingTo(BigDecimal.valueOf(source1FundingAmount));

                        assertThat(source2BeforeTransfer.balance())
                                        .isEqualByComparingTo(BigDecimal.valueOf(source2FundingAmount));

                        assertThat(destinationBeforeTransfer.balance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);
                });

                List<TransactionSource> sources = buildSources(
                                List.of(TransactionSource
                                                .fixed(source2Balance.balanceId(),
                                                                source2Distribution),

                                                TransactionSource
                                                                .fixed(source1Balance.balanceId(),
                                                                                source1Distribution)),
                                false);

                // When
                TransactionAttempt transactionAttempt = makeMultiSourceTransferToFail(
                                sources,
                                destinationBalance.balanceId(),
                                totalTransferAmount,
                                currency,
                                precision,
                                true, // atomic
                                true); // expect failure

                attachJson("Atomic Multi-Source Failure Response", transactionAttempt.response());

                // Characterize the actual failure response
                System.out.printf(
                                "%n===== FUN-10B ATOMIC SPLIT FAILURE RESPONSE =====%n" +
                                                "HTTP Status: %d%n" +
                                                "%s%n" +
                                                "=================================================%n",
                                transactionAttempt.response().statusCode(),
                                transactionAttempt.response().getBody().asPrettyString());

                // Database snapshots after failed transfer
                BalanceRecord source1AfterTransfer = fetchPersistedBalance(source1Balance.balanceId());

                BalanceRecord source2AfterTransfer = fetchPersistedBalance(source2Balance.balanceId());

                BalanceRecord destinationAfterTransfer = fetchPersistedBalance(destinationBalance.balanceId());
                // Then
                Allure.step("Verify atomic multi-source transaction was rejected", () -> {

                        assertThat(transactionAttempt.response().statusCode())
                                        .isBetween(400, 499);
                });

                Allure.step("Verify Source 1 was rolled back to its original state", () -> {

                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        source1BeforeTransfer,
                                        source1AfterTransfer);
                });

                Allure.step("Verify failing Source 2 remained unchanged", () -> {

                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        source2BeforeTransfer,
                                        source2AfterTransfer);
                });

                Allure.step("Verify destination received no funds", () -> {

                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        destinationBeforeTransfer,
                                        destinationAfterTransfer);
                });

                Allure.step("Verify INV-001 holds for all balances after rollback", () -> {

                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(
                                        source1AfterTransfer);

                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(
                                        source2AfterTransfer);

                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(
                                        destinationAfterTransfer);
                });
        }

        @Test
        @Tag("FUN-10C")
        @Story("FUN-10 - Multi-source fixed distribution -rollback")
        @DisplayName("should rollback each source and destination when transfer from a source fails for any reason")
        void shouldCreateRefundTransactionForFailingSourceOnFailureUsingFixedDistribution() {
                long source1FundingAmount = 10_000;
                long source2FundingAmount = 6_000;

                long totalTransferAmount = 10_000;
                long source1Distribution = 2_500;
                long source2Distribution = 7_000;

                String currency = "NGN";
                int precision = 100;

                // Given
                CreateLedgerResponse ledger = createLedger(TestData.unique("fun-10-ledger"));

                CreateBalanceResponse worldBalance = createBalance(ledger.ledgerId(), currency);

                CreateBalanceResponse source1Balance = createBalance(ledger.ledgerId(), currency);

                CreateBalanceResponse source2Balance = createBalance(ledger.ledgerId(), currency);

                CreateBalanceResponse destinationBalance = createBalance(ledger.ledgerId(), currency);

                fundBalanceFromWorld(
                                worldBalance.balanceId(),
                                source1Balance.balanceId(),
                                source1FundingAmount,
                                currency,
                                0,
                                precision);

                fundBalanceFromWorld(
                                worldBalance.balanceId(),
                                source2Balance.balanceId(),
                                source2FundingAmount,
                                currency,
                                0,
                                precision);

                // Database snapshots before transfer
                BalanceRecord source1BeforeTransfer = fetchPersistedBalance(source1Balance.balanceId());

                BalanceRecord source2BeforeTransfer = fetchPersistedBalance(source2Balance.balanceId());

                BalanceRecord destinationBeforeTransfer = fetchPersistedBalance(destinationBalance.balanceId());

                Allure.step("Verify multi-source transfer preconditions", () -> {

                        assertThat(source1BeforeTransfer.balance())
                                        .isEqualByComparingTo(BigDecimal.valueOf(source1FundingAmount));

                        assertThat(source2BeforeTransfer.balance())
                                        .isEqualByComparingTo(BigDecimal.valueOf(source2FundingAmount));

                        assertThat(destinationBeforeTransfer.balance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);
                });

                List<TransactionSource> sources = buildSources(
                                List.of(TransactionSource.fixed(source2Balance.balanceId(), source2Distribution),
                                                TransactionSource.fixed(source1Balance.balanceId(),
                                                                source1Distribution)),
                                true);

                // When
                TransactionAttempt transactionAttempt = makeMultiSourceTransferToFail(
                                sources,
                                destinationBalance.balanceId(),
                                totalTransferAmount,
                                currency,
                                precision,
                                true, // atomic
                                true); // expect failure

                String reference = transactionAttempt.reference();

                // Characterize the actual failure response
                System.out.printf(
                                "%n===== FUN-10C ATOMIC SPLIT FAILURE RESPONSE =====%n" +
                                                "HTTP Status: %d%n" +
                                                "%s%n" +
                                                "=================================================%n",
                                transactionAttempt.response().statusCode(),
                                transactionAttempt.response().getBody().asPrettyString());

                TransactionRecord originalTransaction = fetchPersistedTransactionRefund(reference, "-1");
                TransactionRecord queuedRefundTransaction = fetchPersistedTransactionRefund(
                                originalTransaction.transactionId(), "_refund");

                attachJson("Atomic Multi-Source Failure Response", transactionAttempt.response());

                // Then
                Allure.step("Verify atomic multi-source transaction was rejected", () -> {

                        assertThat(transactionAttempt.response().statusCode())
                                        .isBetween(400, 499);
                });

                Allure.step("Verify compensating refund was created and queued", () -> {

                        assertThat(queuedRefundTransaction.source())
                                        .as("Refund should originate from the original destination")
                                        .isEqualTo(destinationBalance.balanceId());

                        assertThat(queuedRefundTransaction.destination())
                                        .as("Refund should return funds to Source 1")
                                        .isEqualTo(source1Balance.balanceId());

                        assertThat(queuedRefundTransaction.preciseAmount())
                                        .as("Refund should compensate the exact applied amount")
                                        .isEqualByComparingTo(BigDecimal.valueOf(source1Distribution));

                        assertThat(queuedRefundTransaction.status())
                                        .isEqualTo("QUEUED");
                });

                AtomicReference<TransactionRecord> refund = new AtomicReference<>();

                Allure.step("Wait for compensating refund to be applied", () -> {

                        await().atMost(Duration.ofSeconds(30))
                                        .pollInterval(Duration.ofMillis(500))
                                        .untilAsserted(() -> {

                                                TransactionRecord transaction = fetchPersistedTransactionRefund(
                                                                originalTransaction.transactionId(),
                                                                "_refund_q");

                                                assertThat(transaction.status())
                                                                .isEqualTo("APPLIED");

                                                refund.set(transaction);
                                        });
                });

                TransactionRecord resolvedRefundTransaction = refund.get();
                assertThat(resolvedRefundTransaction.status()).isEqualTo("APPLIED");

        }

        @Test
        @Tag("FUN-11A")
        @Story("FUN-11 - Percentage split rounding residue (Multiple sources to one destination)")
        @DisplayName("should conserve total amount when percentage splits produce rounding residue for Multiple sources to one destination")
        void shouldConserveTotalAmountWhenPercentageSplitsProduceRoundingResidueFromMultipleSourcesToOneDestination() {
                long transferAmount = 10_001;
                long fundingAmount = 20_000;

                String currency = "NGN";
                int precision = 100;

                // Given
                CreateLedgerResponse ledger = createLedger(TestData.unique("fun-11-ledger"));

                CreateBalanceResponse worldBalance = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse source1 = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse source2 = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse source3 = createBalance(ledger.ledgerId(), currency);

                CreateBalanceResponse destinationBalance = createBalance(ledger.ledgerId(), currency);

                fundBalanceFromWorld(
                                worldBalance.balanceId(),
                                source1.balanceId(),
                                fundingAmount,
                                currency,
                                0,
                                precision);

                fundBalanceFromWorld(
                                worldBalance.balanceId(),
                                source2.balanceId(),
                                fundingAmount,
                                currency,
                                0,
                                precision);

                fundBalanceFromWorld(
                                worldBalance.balanceId(),
                                source3.balanceId(),
                                fundingAmount,
                                currency,
                                0,
                                precision);

                // Database snapshots before transfer
                BalanceRecord source1BeforeTransfer = fetchPersistedBalance(source1.balanceId());

                BalanceRecord source2BeforeTransfer = fetchPersistedBalance(source2.balanceId());

                BalanceRecord source3BeforeTransfer = fetchPersistedBalance(source3.balanceId());

                BalanceRecord destinationBeforeTransfer = fetchPersistedBalance(destinationBalance.balanceId());

                Allure.step("Verify multi-source transfer preconditions", () -> {

                        assertThat(source1BeforeTransfer.balance())
                                        .isEqualByComparingTo(BigDecimal.valueOf(fundingAmount));

                        assertThat(source2BeforeTransfer.balance())
                                        .isEqualByComparingTo(BigDecimal.valueOf(fundingAmount));

                        assertThat(source3BeforeTransfer.balance())
                                        .isEqualByComparingTo(BigDecimal.valueOf(fundingAmount));

                        assertThat(destinationBeforeTransfer.balance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);
                });

                List<TransactionSource> sources = buildSources(
                                List.of(TransactionSource.percentage(source1.balanceId(), "33%"),
                                                TransactionSource.percentage(source2.balanceId(), "33%"),
                                                TransactionSource.percentage(source3.balanceId(), "34%")),
                                false);

                // When
                CreateMultiSourceTransactionResponse transactionResponse = makeMultiSourceTransfer(
                                sources,
                                destinationBalance.balanceId(),
                                transferAmount,
                                currency,
                                precision,
                                true, // atomic
                                true); // expect failure

                String parentTransactionId = transactionResponse.transactionId();

                List<TransactionRecord> transactionsFromDb = fetchPersistedTransactionsByParent(parentTransactionId);

                System.out.println("\n===== CHILD TRANSACTIONS FROM DB =====");

                transactionsFromDb.forEach(dbtransaction -> System.out.printf(
                                "ID: %s | Source: %s | Destination: %s | Amount: %s | Status: %s%n",
                                dbtransaction.transactionId(),
                                dbtransaction.source(),
                                dbtransaction.destination(),
                                dbtransaction.preciseAmount(),
                                dbtransaction.status()));

                System.out.println("======================================");

                // Database snapshots after transfer
                BalanceRecord source1AfterTransfer = fetchPersistedBalance(source1.balanceId());

                BalanceRecord source2AfterTransfer = fetchPersistedBalance(source2.balanceId());

                BalanceRecord source3AfterTransfer = fetchPersistedBalance(source3.balanceId());

                BalanceRecord destinationAfterTransfer = fetchPersistedBalance(destinationBalance.balanceId());

                BigDecimal source1Debit = source1AfterTransfer.debitBalance()
                                .subtract(source1BeforeTransfer.debitBalance());

                BigDecimal source2Debit = source2AfterTransfer.debitBalance()
                                .subtract(source2BeforeTransfer.debitBalance());

                BigDecimal source3Debit = source3AfterTransfer.debitBalance()
                                .subtract(source3BeforeTransfer.debitBalance());

                BigDecimal destinationCredit = destinationAfterTransfer.creditBalance()
                                .subtract(destinationBeforeTransfer.creditBalance());

                BigDecimal totalSourceDebits = source1Debit.add(source2Debit).add(source3Debit);

                assertThat(totalSourceDebits).isEqualByComparingTo(destinationCredit);

                assertThat(destinationCredit).isEqualByComparingTo(BigDecimal.valueOf(10_001));

        }

        @Test
        @Tag("FUN-11B")
        @Story("FUN-11 - Percentage split rounding residue (One source to multiple destination)")
        @DisplayName("should conserve total amount when percentage splits produce rounding residue for one source to multiple destination")
        void shouldConserveTotalAmountWhenPercentageSplitsProduceRoundingResidueForOneSourceToMultipleDestinations()
                        throws JsonProcessingException {
                long transferAmount = 10_001;
                long fundingAmount = 20_000;

                String currency = "NGN";
                int precision = 100;

                // Given
                CreateLedgerResponse ledger = createLedger(TestData.unique("fun-11-ledger"));

                CreateBalanceResponse worldBalance = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse source = createBalance(ledger.ledgerId(), currency);

                CreateBalanceResponse destination1Balance = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse destination2Balance = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse destination3Balance = createBalance(ledger.ledgerId(), currency);

                fundBalanceFromWorld(
                                worldBalance.balanceId(),
                                source.balanceId(),
                                fundingAmount,
                                currency,
                                0,
                                precision);

                // Database snapshots before transfer
                BalanceRecord sourceBeforeTransfer = fetchPersistedBalance(source.balanceId());

                BalanceRecord destination1BeforeTransfer = fetchPersistedBalance(destination1Balance.balanceId());
                BalanceRecord destination2BeforeTransfer = fetchPersistedBalance(destination2Balance.balanceId());
                BalanceRecord destination3BeforeTransfer = fetchPersistedBalance(destination3Balance.balanceId());

                Allure.step("Verify multi-destination transfer preconditions", () -> {

                        assertThat(sourceBeforeTransfer.balance())
                                        .isEqualByComparingTo(BigDecimal.valueOf(fundingAmount));
                        assertThat(destination1BeforeTransfer.balance()).isEqualByComparingTo(BigDecimal.ZERO);
                        assertThat(destination2BeforeTransfer.balance()).isEqualByComparingTo(BigDecimal.ZERO);
                        assertThat(destination3BeforeTransfer.balance()).isEqualByComparingTo(BigDecimal.ZERO);

                });

                List<TransactionDestination> destinations = buildDestinations(
                                List.of(TransactionDestination.percentage(destination1Balance.balanceId(), "33%"),
                                                TransactionDestination.percentage(destination2Balance.balanceId(),
                                                                "33%"),
                                                TransactionDestination.percentage(destination3Balance.balanceId(),
                                                                "34%")),
                                false);

                CreateMultiDestinationTransactionResponse transactionResponse = makeMultiDestinationTransfer(
                                destinations,
                                source.balanceId(),
                                transferAmount,
                                currency,
                                precision,
                                true, // atomic
                                true);

                ObjectMapper mapper = new ObjectMapper();

                System.out.println(
                                mapper.writerWithDefaultPrettyPrinter()
                                                .writeValueAsString(transactionResponse));

                // Fetch balances after transfer
                BalanceRecord sourceAfterTransfer = fetchPersistedBalance(source.balanceId());

                BalanceRecord destination1AfterTransfer = fetchPersistedBalance(destination1Balance.balanceId());

                BalanceRecord destination2AfterTransfer = fetchPersistedBalance(destination2Balance.balanceId());

                BalanceRecord destination3AfterTransfer = fetchPersistedBalance(destination3Balance.balanceId());

                // Calculate actual financial movements
                BigDecimal sourceDebit = sourceAfterTransfer.debitBalance()
                                .subtract(sourceBeforeTransfer.debitBalance());

                BigDecimal destination1Credit = destination1AfterTransfer.creditBalance()
                                .subtract(destination1BeforeTransfer.creditBalance());

                BigDecimal destination2Credit = destination2AfterTransfer.creditBalance()
                                .subtract(destination2BeforeTransfer.creditBalance());

                BigDecimal destination3Credit = destination3AfterTransfer.creditBalance()
                                .subtract(destination3BeforeTransfer.creditBalance());

                BigDecimal totalDestinationCredits = destination1Credit
                                .add(destination2Credit)
                                .add(destination3Credit);

                Allure.step("Verify percentage split rounding preserves total funds", () -> {

                        assertThat(sourceDebit)
                                        .as("Source must be debited the exact transfer amount")
                                        .isEqualByComparingTo(BigDecimal.valueOf(transferAmount));

                        assertThat(totalDestinationCredits)
                                        .as("Total destination credits must equal source debit")
                                        .isEqualByComparingTo(sourceDebit);

                        assertThat(totalDestinationCredits)
                                        .as("Rounding must not create or lose funds")
                                        .isEqualByComparingTo(BigDecimal.valueOf(transferAmount));

                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(sourceAfterTransfer);
                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(destination1AfterTransfer);
                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(destination2AfterTransfer);
                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(destination3AfterTransfer);
                });

                System.out.printf("""
                                ===== FUN-11B ROUNDING RESULTS =====
                                Source debit:        %s
                                Destination 1 (33%%): %s
                                Destination 2 (33%%): %s
                                Destination 3 (34%%): %s
                                Total credits:       %s
                                Expected total:      %d
                                ====================================
                                %n""",
                                sourceDebit,
                                destination1Credit,
                                destination2Credit,
                                destination3Credit,
                                totalDestinationCredits,
                                transferAmount);
        }

        private List<TransactionSource> buildSources(
                        List<TransactionSource> sources,
                        boolean reverseOrder) {

                List<TransactionSource> orderedSources = new ArrayList<>(sources);

                if (reverseOrder) {
                        Collections.reverse(orderedSources);
                }

                return orderedSources;
        }

        private List<TransactionDestination> buildDestinations(
                        List<TransactionDestination> destinations,
                        boolean reverseOrder) {

                List<TransactionDestination> orderedDestinations = new ArrayList<>(destinations);

                if (reverseOrder) {
                        Collections.reverse(orderedDestinations);
                }

                return orderedDestinations;
        }

}