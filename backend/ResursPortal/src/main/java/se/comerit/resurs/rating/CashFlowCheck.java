package se.comerit.resurs.rating;

import java.util.List;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

@Service
@Order(50)
public class CashFlowCheck implements ScoringCheck {
    private final ScoringConfig config;

    public CashFlowCheck(ScoringConfig config) {
        this.config = config;
    }

    @Override
    public String ruleName() {
        return "Kassaflödeskvot";
    }

    @Override
    public List<CheckResult> evaluate(ApplicationData data) {
        final double ratio = data.totalLiabilities() != 0
                ? data.operatingCashFlow() / data.totalLiabilities() : 0.0;
        final double rejectThreshold = config.cashFlow().reject();
        final double flagThreshold = config.cashFlow().flag();

        if (ratio < rejectThreshold)
            return reject("AVSLAG: Negativt operativt kassaflöde (kassaflödeskvot=" + fmt(ratio) + "). ",
                    ratio, rejectThreshold, -30);
        if (ratio < flagThreshold)
            return flag("VARNING: Kassaflödeskvot låg (" + fmt(ratio) + " < + " + flagThreshold + " ). ",
                    ratio, flagThreshold, -12);
        return ok("Kassaflödeskvot OK (" + fmt(ratio) + "). ", ratio, flagThreshold, 5);
    }

    private static String fmt(double v) {
        return String.format("%.3f", v);
    }
}