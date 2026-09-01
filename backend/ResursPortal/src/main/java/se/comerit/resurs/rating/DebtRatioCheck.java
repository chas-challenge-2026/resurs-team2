package se.comerit.resurs.rating;

import org.springframework.stereotype.Service;

@Service
public class DebtRatioCheck implements ScoringCheck {
    private final ScoringConfig config;

    public DebtRatioCheck(ScoringConfig config) {
        this.config = config;
    }

    @Override
    public String ruleName() {
        return "Skuldsättningsgrad";
    }

    @Override
    public CheckResult evaluate(ApplicationData data) {
        final double debtRatio = data.equity() != 0 ? data.totalLiabilities() / data.equity() : 0.0;
        final double rejectThreshold = config.debtRatio().reject();
        final double flagThreshold = config.debtRatio().flag();

        if (debtRatio > rejectThreshold)
            return reject("AVSLAG: Skuldsättningsgrad för hög (" + fmt(debtRatio)
                    + " > " + rejectThreshold + "). ",
                    debtRatio, rejectThreshold, -35);
        if (debtRatio > flagThreshold)
            return flag("VARNING: Skuldsättningsgrad hög (" + fmt(debtRatio)
                    + ", rekommenderas under " + flagThreshold + "). ",
                    debtRatio, flagThreshold, -15);
        return ok("Skuldsättningsgrad OK (" + fmt(debtRatio) + "). ", debtRatio, flagThreshold, 5);
    }

    private static String fmt(double v) {
        return String.format("%.2f", v);
    }
}