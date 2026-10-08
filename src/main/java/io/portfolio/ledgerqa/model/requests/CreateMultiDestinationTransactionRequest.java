package io.portfolio.ledgerqa.model.requests;

import java.util.List;
import io.portfolio.ledgerqa.domain.TransactionDestination;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateMultiDestinationTransactionRequest(

        @JsonProperty("precise_amount")
        long preciseAmount,

        int precision,

        String reference,

        String currency,

        List<TransactionDestination> destinations,

        String source,

        String description,

        boolean atomic,

        @JsonProperty("skip_queue")
        boolean skipQueue

) {
}