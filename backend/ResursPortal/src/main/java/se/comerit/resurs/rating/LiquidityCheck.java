package se.comerit.resurs.rating;

import java.util.List;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

@Service
@Order(20)
public class LiquidityCheck implements ScoringCheck {
    private final ScoringConfig config;

    public LiquidityCheck(ScoringConfig config) {
        this.config = config;
    }

    @Override
    public String ruleName() {
        return "Likviditetsgrad";
    }

    @Override
    public List<CheckResult> evaluate(ApplicationData data) {
        final double liquidity = data.currentLiabilities() != 0
                ? data.currentAssets() / data.currentLiabilities() : 0.0;
        final double rejectThreshold = config.liquidity().reject();
        final double flagThreshold = config.liquidity().flag();
        final double marginalThreshold = config.liquidity().marginal();

        if (liquidity < rejectThreshold)
            return flag("VARNING: Likviditetsgrad under " + rejectThreshold + " (" + fmt(liquidity)
                    + "). Kortfristiga skulder överstiger omsättningstillgångar. ",
                    liquidity, rejectThreshold, -15);
        if (liquidity < marginalThreshold)
            return flag("VARNING: Likviditetsgrad nära minimigräns (" + fmt(liquidity)
                    + " < " + marginalThreshold + "). ",
                    liquidity, marginalThreshold, -8);
        if (liquidity >= flagThreshold)
            return ok("Likviditetsgrad god (" + fmt(liquidity) + "). ", liquidity, flagThreshold, 10);
        return ok("Likviditetsgrad godkänd (" + fmt(liquidity) + "). ", liquidity, rejectThreshold, 0);
    }

    private static String fmt(double v) {
        return String.format("%.2f", v);
    }
}