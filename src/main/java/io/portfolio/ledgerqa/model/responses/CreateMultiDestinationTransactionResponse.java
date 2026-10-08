package io.portfolio.ledgerqa.model.responses;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateMultiDestinationTransactionResponse(

        @JsonProperty("precise_amount")
        long preciseAmount,

        long amount,

        @JsonProperty("amount_string")
        String amountString,

        int precision,

        @JsonProperty("overdraft_limit")
        long overdraftLimit,

        @JsonProperty("transaction_id")
        String transactionId,

        @JsonProperty("parent_transaction")
        String parentTransaction,

        String source,

        String reference,

        String currency,

        String description,

        String status,

        String hash,

        @JsonProperty("allow_overdraft")
        boolean allowOverdraft,

        boolean inflight,

        @JsonProperty("skip_queue")
        boolean skipQueue,

        boolean atomic,

        List<Destination> destinations,

        @JsonProperty("created_at")
        String createdAt,

        @JsonProperty("effective_date")
        String effectiveDate,

        @JsonProperty("scheduled_for")
        String scheduledFor,

        @JsonProperty("inflight_expiry_date")
        String inflightExpiryDate,

        @JsonProperty("inflight_commit_date")
        String inflightCommitDate,

        @JsonProperty("meta_data")
        MetaData metaData

) {

    public record Destination(

            String identifier,

            String distribution,

            @JsonProperty("precise_distribution")
            String preciseDistribution,

            @JsonProperty("transaction_id")
            String transactionId

    ) {
    }

    public record MetaData(

            @JsonProperty("QUEUED_PARENT_TRANSACTION")
            String queuedParentTransaction,

            boolean atomic

    ) {
    }
}