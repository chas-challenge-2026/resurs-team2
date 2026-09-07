package se.comerit.resurs.rating;

/**
 * CheckResult
 */
public record CheckResult(
        String ruleName,
        double metric,
        double threshold,
        CheckStatus status,
        int scoreDelta,
        String message) {
}
