package se.comerit.resurs.rating;

import java.util.List;

import org.springframework.stereotype.Service;

/**
 * DecisionEngine – synthesizes the final decision from the aggregated check
 * results and returns the complete {@link ScoringResult}.
 *
 * <p>
 * Behaviour mirrors the original engine (at least one flag = review, a number
 * at or above the manual-review threshold = manual review, any hard reject =
 * rejected, otherwise approved). The reason summary is the Swedish prefix
 * followed by each check's message, matching the text produced by the original
 * engine.
 */
@Service
public class DecisionEngine {

    public DecisionEngine(ScoringConfig config) {
        this.reviewFlagThreshold = config.reviewFlagThreshold();
    }

    /**
     * Flag count from which a manual review is forced, bound from
     * {@code resurs.scoring.review-flag-threshold}.
     *
     * <p>
     * Any flagged application (<code>flagCount &gt; 0</code>) below this
     * threshold is still reviewed, so changing the value can never approve an
     * application outright.
     */
    private final int reviewFlagThreshold;

    public ScoringResult decide(List<CheckResult> checks) {
        int flagCount = (int) checks.stream()
                .filter(result -> result.status() == CheckStatus.FLAG)
                .count();
        boolean hardReject = checks.stream()
                .anyMatch(result -> result.status() == CheckStatus.REJECT);

        Decision decision;
        String prefix;
        if (hardReject) {
            decision = Decision.REJECTED;
            prefix = "=== ANSÖKAN AVSLAGEN === ";
        } else if (flagCount >= reviewFlagThreshold) {
            decision = Decision.UNDER_REVIEW;
            prefix = "=== MANUELL GRANSKNING === Antal varningsflaggor: " + flagCount + ". ";
        } else if (flagCount > 0) {
            decision = Decision.UNDER_REVIEW;
            prefix = "=== GRANSKNING REKOMMENDERAS === " + flags(flagCount);
        } else {
            decision = Decision.APPROVED;
            prefix = "=== ANSÖKAN GODKÄND === Alla nyckeltal uppfyller krav. ";
        }

        return new ScoringResult(decision, checks, summary(prefix, checks));
    }

    private String flags(int flagCount) {
        return flagCount == 1 ? "1 varningsflagga. "
                : flagCount + " varningsflaggor. ";
    }

    private String summary(String prefix, List<CheckResult> checks) {
        StringBuilder sb = new StringBuilder(prefix);
        for (CheckResult result : checks) {
            sb.append(result.message());
        }
        return sb.toString();
    }
}