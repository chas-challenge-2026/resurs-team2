package se.comerit.resurs.rating;

import java.util.List;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

@Service
@Order(40)
public class ProfitMarginCheck implements ScoringCheck {
    private final ScoringConfig config;

    public ProfitMarginCheck(ScoringConfig config) {
        this.config = config;
    }

    @Override
    public String ruleName() {
        return "Rörelsemarginal";
    }

    @Override
    public List<CheckResult> evaluate(ApplicationData data) {
        final double margin = data.netRevenue() != 0 ? data.operatingIncome() / data.netRevenue() : 0.0;
        final double warningThreshold = config.profitMargin().flag();
        final double goodThreshold = config.profitMargin().good();

        if (margin < warningThreshold)
            return flag("VARNING: Rörelseresultatmarginal låg (" + pct(margin)
                    + "%, rekommenderas över " + warningThreshold + "). ",
                    margin, warningThreshold, -10);
        if (margin > goodThreshold)
            return ok("Rörelseresultatmarginal god (" + pct(margin)
                    + "%, rekommenderas " + goodThreshold + "). ",
                    margin, goodThreshold, 8);
        return ok("Rörelseresultatmarginal godkänd (" + pct(margin) + "%). ", margin, warningThreshold, 0);
    }

    /** Formats a ratio as a percentage with two decimals, e.g. 0.15 -> "15.00". */
    private static String pct(double v) {
        return String.format("%.2f", v * 100);
    }
}