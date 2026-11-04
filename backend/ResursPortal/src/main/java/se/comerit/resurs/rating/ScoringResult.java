package se.comerit.resurs.rating;

import java.util.List;

public record ScoringResult(
        Decision decision,
        List<CheckResult> checks,
        String summary) {
}
