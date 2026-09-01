package io.portfolio.ledgerqa.model.responses;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateBalanceResponse(
        long balance,

        long version,

        @JsonProperty("inflight_balance") long inflightBalance,

        @JsonProperty("credit_balance") long creditBalance,

        @JsonProperty("inflight_credit_balance") long inflightCreditBalance,

        @JsonProperty("debit_balance") long debitBalance,

        @JsonProperty("inflight_debit_balance") long inflightDebitBalance,

        @JsonProperty("ledger_id") String ledgerId,

        @JsonProperty("identity_id") String identityId,

        @JsonProperty("balance_id") String balanceId,

        String currency,

        @JsonProperty("created_at") String createdAt,

        @JsonProperty("inflight_expires_at") String inflightExpiresAt,

        @JsonProperty("meta_data") Object metadata,

        @JsonProperty("track_fund_lineage") boolean trackFundLineage,

        @JsonProperty("allocation_strategy") String allocationStrategy) {
}