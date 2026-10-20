package se.comerit.resurs.audit;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApplicationCreated(@JsonProperty("orgNumber") String orgNumber) implements AuditEntry {
}
