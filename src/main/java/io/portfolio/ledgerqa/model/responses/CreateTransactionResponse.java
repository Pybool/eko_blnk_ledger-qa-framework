package io.portfolio.ledgerqa.model.responses;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateTransactionResponse(

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

        String destination,

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

        @JsonProperty("created_at")
        String createdAt,

        @JsonProperty("effective_date")
        String effectiveDate,

        @JsonProperty("scheduled_for")
        String scheduledFor,

        @JsonProperty("inflight_expiry_date")
        String inflightExpiryDate,

        @JsonProperty("inflight_commit_date")
        String inflightCommitDate

) {}