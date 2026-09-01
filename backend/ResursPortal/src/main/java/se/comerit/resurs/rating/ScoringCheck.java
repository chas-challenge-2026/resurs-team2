package se.comerit.resurs.rating;

public interface ScoringCheck {
    String ruleName();
    CheckResult evaluate(ApplicationData data);

    default CheckResult reject(String message, double value, double threshold, int delta) {
        return new CheckResult(ruleName(), value, threshold, CheckStatus.REJECT, delta, message);
    }

    default CheckResult flag(String message, double value, double threshold, int delta) {
        return new CheckResult(ruleName(), value, threshold, CheckStatus.FLAG, delta, message);
    }

    default CheckResult ok(String message, double value, double threshold, int delta) {
        return new CheckResult(ruleName(), value, threshold, CheckStatus.OK, delta, message);
    }

    default CheckResult skip(String message) {
        return new CheckResult(ruleName(), 0.0, 0.0, CheckStatus.SKIP, 0, message);
    }
}
