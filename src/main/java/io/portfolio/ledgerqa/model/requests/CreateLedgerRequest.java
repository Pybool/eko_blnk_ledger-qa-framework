package io.portfolio.ledgerqa.model.requests;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateLedgerRequest(
        String name,

        @JsonProperty("meta_data") Metadata metadata) {

    public static CreateLedgerRequest of(
            String name,
            String projectOwner) {
        Metadata metadata = projectOwner == null
                ? null
                : new Metadata(projectOwner);

        return new CreateLedgerRequest(
                name,
                metadata);
    }

    public record Metadata(
            @JsonProperty("project_owner") String projectOwner) {
    }
}