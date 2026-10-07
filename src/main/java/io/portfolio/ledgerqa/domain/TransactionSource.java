package io.portfolio.ledgerqa.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionSource(

        String identifier,

        String distribution,

        @JsonProperty("precise_distribution") String preciseDistribution

) {

    public static TransactionSource percentage(
            String balanceId,
            String percentage) {

        return new TransactionSource(
                balanceId,
                percentage,
                null);
    }

    public static TransactionSource fixed(
            String balanceId,
            long preciseAmount) {

        return new TransactionSource(
                balanceId,
                null,
                String.valueOf(preciseAmount));
    }

    public static TransactionSource left(String balanceId) {

        return new TransactionSource(
                balanceId,
                "left",
                null);
    }
}