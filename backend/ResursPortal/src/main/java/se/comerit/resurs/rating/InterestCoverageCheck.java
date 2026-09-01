package se.comerit.resurs.rating;

import org.springframework.stereotype.Service;

@Service
public class InterestCoverageCheck implements ScoringCheck {
    private final ScoringConfig config;

    public InterestCoverageCheck(ScoringConfig config) {
        this.config = config;
    }

    @Override
    public String ruleName() {
        return "Räntetäckningsgrad";
    }

    @Override
    public CheckResult evaluate(ApplicationData data) {
        final double interestExpenses = data.interestExpenses();
        if (interestExpenses <= 0) {
            return skip("Räntetäckningsgrad ej tillämplig (inga räntekostnader). ");
        }

        final double coverage = data.operatingIncome() / interestExpenses;
        final double rejectThreshold = config.interestCoverage().reject();
        final double flagThreshold = config.interestCoverage().flag();

        if (coverage < rejectThreshold)
            return reject("AVSLAG: Räntetäckningsgrad under + " + rejectThreshold + " (" + fmt(coverage)
                    + "). Rörelseresultat täcker ej räntekostnader. ",
                    coverage, rejectThreshold, -35);
        if (coverage < flagThreshold)
            return flag("VARNING: Räntetäckningsgrad låg (" + fmt(coverage)
                    + " < +, rekommenderas minst " + flagThreshold + "). ",
                    coverage, flagThreshold, -15);
        return ok("Räntetäckningsgrad OK (" + fmt(coverage) + "). ", coverage, flagThreshold, 8);
    }

    private static String fmt(double v) {
        return String.format("%.2f", v);
    }
}