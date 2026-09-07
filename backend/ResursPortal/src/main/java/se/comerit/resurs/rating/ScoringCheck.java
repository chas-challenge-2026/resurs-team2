package se.comerit.resurs.rating;

import java.util.List;

/**
 * ScoringCheck – strategy interface for every scoring rule.
 *
 * <p>All implementations are Spring beans injected as a {@code List<ScoringCheck>}.
 * Each {@code evaluate} returns the {@link CheckResult}s the rule produced: a
 * simple rule yields exactly one, a composite rule may yield several or none.
 */
public interface ScoringCheck {
    String ruleName();
    List<CheckResult> evaluate(ApplicationData data);

    default List<CheckResult> reject(String message, double value, double threshold, int delta) {
        return List.of(new CheckResult(ruleName(), value, threshold, CheckStatus.REJECT, delta, message));
    }

    default List<CheckResult> flag(String message, double value, double threshold, int delta) {
        return List.of(new CheckResult(ruleName(), value, threshold, CheckStatus.FLAG, delta, message));
    }

    default List<CheckResult> ok(String message, double value, double threshold, int delta) {
        return List.of(new CheckResult(ruleName(), value, threshold, CheckStatus.OK, delta, message));
    }

    default List<CheckResult> skip(String message) {
        return List.of(new CheckResult(ruleName(), 0.0, 0.0, CheckStatus.SKIP, 0, message));
    }
}