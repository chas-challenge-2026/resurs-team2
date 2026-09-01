package se.comerit.resurs.rating;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

/**
 * CombinationChecks – compound-risk rules that combine several financial
 * ratios (e.g. low solvency AND large credit). Each fired rule produces its own
 * {@link CheckResult}; nothing is returned when no rule fires. The formulas
 * mirror the original engine, including its quirks (the {@code netRevenue + 1}
 * guard in the debt-burden rule).
 */
@Service
public class CombinationChecks {
    private final ScoringConfig config;

    public CombinationChecks(ScoringConfig config) {
        this.config = config;
    }

    public List<CheckResult> evaluate(ApplicationData data) {
        List<CheckResult> results = new ArrayList<>();
        double solvency = data.totalCapital() != 0 ? data.equity() / data.totalCapital() : 0.0;
        double debtRatio = data.equity() != 0 ? data.totalLiabilities() / data.equity() : 0.0;
        double liquidity = data.currentLiabilities() != 0
                ? data.currentAssets() / data.currentLiabilities() : 0.0;
        double cashFlowRatio = data.totalLiabilities() != 0
                ? data.operatingCashFlow() / data.totalLiabilities() : 0.0;
        double request = data.requestAmount().doubleValue();

        if (solvency < config.extra().lowSolvencyThreshold()
                && request > config.extra().largeCreditWithLowSolvency()) {
            results.add(new CheckResult("Kombination: Stor kredit + låg soliditet",
                    solvency, config.extra().lowSolvencyThreshold(),
                    CheckStatus.FLAG, -12,
                    "VARNING: Stor kreditbelopp med soliditet under "
                            + config.extra().lowSolvencyThreshold()
                            + " – extra granskning rekommenderas. "));
        }

        if (solvency < config.solidity().reject() && debtRatio > config.debtRatio().flag()) {
            results.add(new CheckResult("Kombination: Dubbel riskindikator",
                    solvency, config.solidity().reject(),
                    CheckStatus.FLAG, -18,
                    "VARNING: Dubbel riskindikator — låg soliditet ("
                            + String.format("%.2f", solvency) + ") kombinerat med hög skuldsättning ("
                            + String.format("%.2f", debtRatio) + "). "));
        }

        if (liquidity < config.liquidity().reject() && data.operatingIncome() < 0) {
            results.add(new CheckResult("Kombination: Likviditet + resultat",
                    liquidity, config.liquidity().reject(),
                    CheckStatus.REJECT, -40,
                    "AVSLAG: Kombinationsrisk — likviditetsgrad under "
                            + config.liquidity().reject() + " samt negativt rörelseresultat. "));
        }

        double revenueGuard = data.netRevenue() * config.combination().creditVsRevenueMinimum();
        if (request > revenueGuard) {
            results.add(new CheckResult("Kombination: Kredit vs omsättning",
                    request, revenueGuard,
                    CheckStatus.FLAG, -8,
                    "VARNING: Kreditbelopp överstiger årsoms. ("
                            + String.format("%.0f", request) + " kr > "
                            + String.format("%.0f", data.netRevenue()) + " kr). "));
        }

        if (request > 0
                && data.equity() / request < config.combination().equityVsCreditRatio()) {
            results.add(new CheckResult("Kombination: Eget kapital vs kredit",
                    data.equity() / request, config.combination().equityVsCreditRatio(),
                    CheckStatus.FLAG, -10,
                    "VARNING: Eget kapital täcker mindre än "
                            + String.format("%.0f%%",
                                    config.combination().equityVsCreditRatio() * 100)
                            + " av kreditbeloppet. "));
        }

        double debtBurden = (data.totalLiabilities() + data.currentLiabilities()) / (data.netRevenue() + 1);
        if (debtBurden > config.combination().debtBurdenVsRevenueThreshold()) {
            results.add(new CheckResult("Kombination: Skuldbörda vs omsättning",
                    debtBurden, config.combination().debtBurdenVsRevenueThreshold(),
                    CheckStatus.FLAG, -7,
                    "VARNING: Skuldbörda hög relativt omsättning (kombinationscheck). "));
        }

        if (cashFlowRatio < config.cashFlow().flag() && debtRatio > config.debtRatio().flag()) {
            results.add(new CheckResult("Kombination: Kassaflöde + skuldsättning",
                    cashFlowRatio, config.cashFlow().flag(),
                    CheckStatus.FLAG, -12,
                    "VARNING: Kombinationsrisk kassaflöde + skuldsättning. "));
        }

        return results;
    }
}