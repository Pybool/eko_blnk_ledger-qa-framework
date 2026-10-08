package io.portfolio.ledgerqa.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionDestination(

        String identifier,

        String distribution,

        @JsonProperty("precise_distribution") String preciseDistribution

) {

    public static TransactionDestination percentage(
            String balanceId,
            String percentage) {

        return new TransactionDestination(
                balanceId,
                percentage,
                null);
    }

    public static TransactionDestination fixed(
            String balanceId,
            long preciseAmount) {

        return new TransactionDestination(
                balanceId,
                null,
                String.valueOf(preciseAmount));
    }

    public static TransactionDestination left(String balanceId) {

        return new TransactionDestination(
                balanceId,
                "left",
                null);
    }
}