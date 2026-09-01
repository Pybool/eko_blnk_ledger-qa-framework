package io.portfolio.ledgerqa.model.requests;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateTransactionRequest(
        String description,
        String reference,

        String source,

        String destination,

        @JsonProperty("precise_amount") long preciseAmount,

        String currency,

        int precision,

        @JsonProperty("allow_overdraft") boolean allowOverdraft,

        @JsonProperty("skip_queue") boolean skipQueue,

        boolean inflight) {
}