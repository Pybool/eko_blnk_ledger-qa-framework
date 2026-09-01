package io.portfolio.ledgerqa.model.requests;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateTransactionRequest(

        String reference,

        String source,

        String destination,

        @JsonProperty("precise_amount") long preciseAmount,

        String currency,

        int precision,

        @JsonProperty("skip_queue") boolean skipQueue,

        boolean inflight) {
}