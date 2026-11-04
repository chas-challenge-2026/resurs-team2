package se.comerit.resurs.audit;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DocumentUploaded(
        @JsonProperty("documentID") String documentID) implements AuditEntry {

}
