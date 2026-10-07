package io.portfolio.ledgerqa.model.requests;

import java.util.List;
import io.portfolio.ledgerqa.domain.TransactionSource;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateMultiSourceTransactionRequest(

        @JsonProperty("precise_amount")
        long preciseAmount,

        int precision,

        String reference,

        String currency,

        List<TransactionSource> sources,

        String destination,

        String description,

        boolean atomic,

        @JsonProperty("skip_queue")
        boolean skipQueue

) {
}