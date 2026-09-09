package se.comerit.resurs.audit;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ScoringRun(@JsonProperty("result") String result,
                         @JsonProperty("flags") String flags) implements AuditEntry {
}
