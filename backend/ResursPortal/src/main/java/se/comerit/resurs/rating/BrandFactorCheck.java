package se.comerit.resurs.rating;

import java.util.List;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

/**
 * BrandFactorCheck – applies the industry-specific correction factor to the
 * solvency rejection boundary (solvency below {@code solidity.reject * factor}
 * is flagged). Mirrors the industry-factor check of the original engine.
 */
@Service
@Order(70)
public class BrandFactorCheck implements ScoringCheck {
    private final ScoringConfig config;

    public BrandFactorCheck(ScoringConfig config) {
        this.config = config;
    }

    @Override
    public String ruleName() {
        return "Branschfaktor";
    }

    @Override
    public List<CheckResult> evaluate(ApplicationData data) {
        double factor = config.industryFactors().getOrDefault(data.industry(), 1.0);
        double boundary = config.solidity().reject() * factor;
        double solvency = data.totalCapital() != 0 ? data.equity() / data.totalCapital() : 0.0;

        if (solvency < boundary) {
            return flag("VARNING: Soliditet understiger branschjusterad gräns ("
                    + String.format("%.2f", boundary) + " för bransch " + data.industry() + "). ",
                    solvency, boundary, -8);
        }
        return ok("", solvency, boundary, 0);
    }
}