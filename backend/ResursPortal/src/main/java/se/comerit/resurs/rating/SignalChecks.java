package se.comerit.resurs.rating;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

/**
 * SignalChecks – standalone single-metric signals (negative equity, large
 * credit, low revenue, negative result, liabilities vs revenue, investing cash
 * flow). Each fired signal produces its own {@link CheckResult}; nothing is
 * returned when no signal fires.
 */
@Service
public class SignalChecks {
    private final ScoringConfig config;

    public SignalChecks(ScoringConfig config) {
        this.config = config;
    }

    public List<CheckResult> evaluate(ApplicationData data) {
        List<CheckResult> results = new ArrayList<>();
        ScoringConfig.ExtraThresholds extra = config.extra();

        if (data.equity() < extra.negativeEquityThreshold()) {
            results.add(new CheckResult("Signal: Negativt eget kapital",
                    data.equity(), extra.negativeEquityThreshold(),
                    CheckStatus.REJECT, -50, "AVSLAG: Negativt eget kapital. "));
        }

        if (data.requestAmount().doubleValue() > extra.largeCreditThreshold()) {
            results.add(new CheckResult("Signal: Stor kredit",
                    data.requestAmount().doubleValue(), extra.largeCreditThreshold(),
                    CheckStatus.FLAG, -10,
                    "VARNING: Kreditbelopp överstiger "
                            + String.format("%.0f", extra.largeCreditThreshold())
                            + " kr — kräver manuell granskning. "));
        }

        if (data.netRevenue() < extra.lowRevenueThreshold()) {
            results.add(new CheckResult("Signal: Låg nettoomsättning",
                    data.netRevenue(), extra.lowRevenueThreshold(),
                    CheckStatus.FLAG, -7,
                    "VARNING: Låg nettoomsättning (under "
                            + String.format("%,.0f", extra.lowRevenueThreshold()).replace(',', ' ')
                            + " kr). "));
        }

        if (data.operatingIncome() < extra.negativeIncomeThreshold()) {
            results.add(new CheckResult("Signal: Negativt rörelseresultat",
                    data.operatingIncome(), extra.negativeIncomeThreshold(),
                    CheckStatus.FLAG, -12, "VARNING: Negativt rörelseresultat. "));
        }

        double debtVsRevenue = data.netRevenue() * extra.highDebtToRevenueMultiplier();
        if (data.totalLiabilities() > debtVsRevenue) {
            results.add(new CheckResult("Signal: Skulder vs nettoomsättning",
                    data.totalLiabilities(), debtVsRevenue,
                    CheckStatus.FLAG, -10,
                    "VARNING: Totala skulder överstiger dubbla nettoomsättningen. "));
        }

        double investingBoundary = -data.netRevenue() * extra.negativeInvestingRatio();
        if (data.investingCashFlow() < investingBoundary) {
            results.add(new CheckResult("Signal: Investeringskassaflöde",
                    data.investingCashFlow(), investingBoundary,
                    CheckStatus.FLAG, -4,
                    "VARNING: Högt negativt investeringskassaflöde ("
                            + String.format("%.0f", data.investingCashFlow()) + " kr). "));
        }

        return results;
    }
}