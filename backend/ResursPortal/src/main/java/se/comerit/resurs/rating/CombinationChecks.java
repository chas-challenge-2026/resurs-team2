package se.comerit.resurs.rating;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

/**
 * CombinationChecks – compound-risk rules that combine several financial
 * ratios (e.g. low solvency AND large credit). Each rule is a small named
 * helper; only fired rules produce a {@link CheckResult}. The formulas mirror
 * the original engine, including its quirks (the {@code netRevenue + 1} guard
 * in the debt-burden rule).
 */
@Service
@Order(100)
public class CombinationChecks implements ScoringCheck {
    private final ScoringConfig config;

    public CombinationChecks(ScoringConfig config) {
        this.config = config;
    }

    @Override
    public String ruleName() {
        return "Kombinationsrisker";
    }

    @Override
    public List<CheckResult> evaluate(ApplicationData data) {
        List<CheckResult> results = new ArrayList<>();
        addIfPresent(results, largeCreditWithLowSolvency(data));
        addIfPresent(results, doubleRiskIndicator(data));
        addIfPresent(results, liquidityWithNegativeResult(data));
        addIfPresent(results, creditVsRevenue(data));
        addIfPresent(results, equityVsCredit(data));
        addIfPresent(results, debtBurdenVsRevenue(data));
        addIfPresent(results, cashFlowWithDebt(data));
        return results;
    }

    private Optional<CheckResult> largeCreditWithLowSolvency(ApplicationData data) {
        double solvency = solvency(data);
        double threshold = config.extra().lowSolvencyThreshold();
        if (solvency < threshold
                && data.requestAmount().doubleValue() > config.extra().largeCreditWithLowSolvency()) {
            return Optional.of(new CheckResult(
                    "Kombination: Stor kredit + låg soliditet",
                    solvency, threshold, CheckStatus.FLAG, -12,
                    "VARNING: Stor kreditbelopp med soliditet under " + threshold
                            + " – extra granskning rekommenderas. "));
        }
        return Optional.empty();
    }

    private Optional<CheckResult> doubleRiskIndicator(ApplicationData data) {
        double solvency = solvency(data);
        double debtRatio = debtRatio(data);
        if (solvency < config.solidity().reject() && debtRatio > config.debtRatio().flag()) {
            return Optional.of(new CheckResult(
                    "Kombination: Dubbel riskindikator",
                    solvency, config.solidity().reject(), CheckStatus.FLAG, -18,
                    "VARNING: Dubbel riskindikator — låg soliditet ("
                            + String.format("%.2f", solvency) + ") kombinerat med hög skuldsättning ("
                            + String.format("%.2f", debtRatio) + "). "));
        }
        return Optional.empty();
    }

    private Optional<CheckResult> liquidityWithNegativeResult(ApplicationData data) {
        double liquidity = liquidity(data);
        if (liquidity < config.liquidity().reject() && data.operatingIncome() < 0) {
            return Optional.of(new CheckResult(
                    "Kombination: Likviditet + resultat",
                    liquidity, config.liquidity().reject(), CheckStatus.REJECT, -40,
                    "AVSLAG: Kombinationsrisk — likviditetsgrad under "
                            + config.liquidity().reject() + " samt negativt rörelseresultat. "));
        }
        return Optional.empty();
    }

    private Optional<CheckResult> creditVsRevenue(ApplicationData data) {
        double request = data.requestAmount().doubleValue();
        double revenueGuard = data.netRevenue() * config.combination().creditVsRevenueMinimum();
        if (request > revenueGuard) {
            return Optional.of(new CheckResult(
                    "Kombination: Kredit vs omsättning",
                    request, revenueGuard, CheckStatus.FLAG, -8,
                    "VARNING: Kreditbelopp överstiger årsoms. ("
                            + String.format("%.0f", request) + " kr > "
                            + String.format("%.0f", data.netRevenue()) + " kr). "));
        }
        return Optional.empty();
    }

    private Optional<CheckResult> equityVsCredit(ApplicationData data) {
        double request = data.requestAmount().doubleValue();
        if (request <= 0) {
            return Optional.empty();
        }
        double ratio = data.equity() / request;
        if (ratio < config.combination().equityVsCreditRatio()) {
            return Optional.of(new CheckResult(
                    "Kombination: Eget kapital vs kredit",
                    ratio, config.combination().equityVsCreditRatio(), CheckStatus.FLAG, -10,
                    "VARNING: Eget kapital täcker mindre än "
                            + String.format("%.0f%%",
                                    config.combination().equityVsCreditRatio() * 100)
                            + " av kreditbeloppet. "));
        }
        return Optional.empty();
    }

    private Optional<CheckResult> debtBurdenVsRevenue(ApplicationData data) {
        // netRevenue + 1 mirrors the original "debt burden" formula (avoids division by zero).
        double debtBurden = (data.totalLiabilities() + data.currentLiabilities())
                / (data.netRevenue() + 1);
        if (debtBurden > config.combination().debtBurdenVsRevenueThreshold()) {
            return Optional.of(new CheckResult(
                    "Kombination: Skuldbörda vs omsättning",
                    debtBurden, config.combination().debtBurdenVsRevenueThreshold(),
                    CheckStatus.FLAG, -7,
                    "VARNING: Skuldbörda hög relativt omsättning (kombinationscheck). "));
        }
        return Optional.empty();
    }

    private Optional<CheckResult> cashFlowWithDebt(ApplicationData data) {
        double cashFlowRatio = data.totalLiabilities() != 0
                ? data.operatingCashFlow() / data.totalLiabilities() : 0.0;
        if (cashFlowRatio < config.cashFlow().flag() && debtRatio(data) > config.debtRatio().flag()) {
            return Optional.of(new CheckResult(
                    "Kombination: Kassaflöde + skuldsättning",
                    cashFlowRatio, config.cashFlow().flag(), CheckStatus.FLAG, -12,
                    "VARNING: Kombinationsrisk kassaflöde + skuldsättning. "));
        }
        return Optional.empty();
    }

    private void addIfPresent(List<CheckResult> results, Optional<CheckResult> result) {
        result.ifPresent(results::add);
    }

    private double solvency(ApplicationData data) {
        return data.totalCapital() != 0 ? data.equity() / data.totalCapital() : 0.0;
    }

    private double debtRatio(ApplicationData data) {
        return data.equity() != 0 ? data.totalLiabilities() / data.equity() : 0.0;
    }

    private double liquidity(ApplicationData data) {
        return data.currentLiabilities() != 0
                ? data.currentAssets() / data.currentLiabilities() : 0.0;
    }
}