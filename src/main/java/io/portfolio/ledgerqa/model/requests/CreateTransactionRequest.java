package io.portfolio.ledgerqa.model.requests;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateTransactionRequest(
        String description,

        String reference,

        String source,

        String destination,

        @JsonProperty("precise_amount") long preciseAmount,

        String currency,

        int precision,

        @JsonProperty("allow_overdraft") boolean allowOverdraft,

        @JsonProperty("overdraft_limit") long overdraftLimit,

        @JsonProperty("skip_queue") boolean skipQueue,

        boolean inflight,

        @JsonProperty("inflight_expiry_date") String inflightExpiryDate) {

    public CreateTransactionRequest(
            String description,
            String reference,
            String source,
            String destination,
            long preciseAmount,
            String currency,
            int precision,
            boolean allowOverdraft,
            long overdraftLimit,
            boolean skipQueue,
            boolean inflight) {

        this(
                description,
                reference,
                source,
                destination,
                preciseAmount,
                currency,
                precision,
                allowOverdraft,
                overdraftLimit,
                skipQueue,
                inflight,
                null
        );
    }
}