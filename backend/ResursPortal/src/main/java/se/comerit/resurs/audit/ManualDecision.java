package se.comerit.resurs.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ManualDecision(@JsonProperty("decision") String decision,
                             @JsonProperty("worker") String worker,
                             @JsonInclude(JsonInclude.Include.NON_NULL)
                             @JsonProperty("comment") String comment) implements AuditEntry {
}
