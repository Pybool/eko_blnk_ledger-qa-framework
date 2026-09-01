package io.portfolio.ledgerqa.model.requests;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateLedgerRequest(
        String name,

        @JsonProperty("meta_data") Metadata metadata) {

    public static CreateLedgerRequest of(
            String name,
            String projectOwner) {
        return new CreateLedgerRequest(name, new Metadata(projectOwner));
    }

    public record Metadata(
            @JsonProperty("project_owner") String projectOwner) {
    }
}