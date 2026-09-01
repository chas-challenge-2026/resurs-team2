package se.comerit.resurs.api.v1.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import se.comerit.resurs.rating.ApplicationData;
import se.comerit.resurs.rating.BrandFactorCheck;
import se.comerit.resurs.rating.CashFlowCheck;
import se.comerit.resurs.rating.CheckResult;
import se.comerit.resurs.rating.CheckStatus;
import se.comerit.resurs.rating.CombinationChecks;
import se.comerit.resurs.rating.DebtRatioCheck;
import se.comerit.resurs.rating.DecisionEngine;
import se.comerit.resurs.rating.IndustryBenchmarkCheck;
import se.comerit.resurs.rating.InterestCoverageCheck;
import se.comerit.resurs.rating.LiquidityCheck;
import se.comerit.resurs.rating.ProfitMarginCheck;
import se.comerit.resurs.rating.Score;
import se.comerit.resurs.rating.ScoringResult;
import se.comerit.resurs.rating.SignalChecks;
import se.comerit.resurs.rating.SolidityCheck;

/**
 * ScoringService – orchestrates every scoring check and synthesizes the final
 * decision via the {@link DecisionEngine}.
 *
 * <p>Each check is a pure function of the input {@link ApplicationData} and
 * returns a {@link CheckResult}. All results feed the decision engine, while
 * the legacy {@link Score} projection keeps the controller interface stable.
 */
@Service
public class ScoringService {
    public static final int BASE_CREDIT_POINTS = 100;

    private final SolidityCheck solidityCheck;
    private final LiquidityCheck liquidityCheck;
    private final DebtRatioCheck debtRatioCheck;
    private final ProfitMarginCheck profitMarginCheck;
    private final CashFlowCheck cashFlowCheck;
    private final InterestCoverageCheck interestCoverageCheck;
    private final BrandFactorCheck brandFactorCheck;
    private final IndustryBenchmarkCheck brandBenchmarkCheck;
    private final SignalChecks signalChecks;
    private final CombinationChecks combinationChecks;
    private final DecisionEngine decisionEngine;

    public ScoringService(
            SolidityCheck solidityCheck,
            LiquidityCheck liquidityCheck,
            DebtRatioCheck debtRatioCheck,
            ProfitMarginCheck profitMarginCheck,
            CashFlowCheck cashFlowCheck,
            InterestCoverageCheck interestCoverageCheck,
            BrandFactorCheck brandFactorCheck,
            IndustryBenchmarkCheck brandBenchmarkCheck,
            SignalChecks signalChecks,
            CombinationChecks combinationChecks,
            DecisionEngine decisionEngine) {
        this.solidityCheck = solidityCheck;
        this.liquidityCheck = liquidityCheck;
        this.debtRatioCheck = debtRatioCheck;
        this.profitMarginCheck = profitMarginCheck;
        this.cashFlowCheck = cashFlowCheck;
        this.interestCoverageCheck = interestCoverageCheck;
        this.brandFactorCheck = brandFactorCheck;
        this.brandBenchmarkCheck = brandBenchmarkCheck;
        this.signalChecks = signalChecks;
        this.combinationChecks = combinationChecks;
        this.decisionEngine = decisionEngine;
    }

    public Score score(ApplicationData input) {
        List<CheckResult> checks = new ArrayList<>();
        checks.add(solidityCheck.evaluate(input));
        checks.add(liquidityCheck.evaluate(input));
        checks.add(debtRatioCheck.evaluate(input));
        checks.add(profitMarginCheck.evaluate(input));
        checks.add(cashFlowCheck.evaluate(input));
        checks.add(interestCoverageCheck.evaluate(input));
        checks.add(brandFactorCheck.evaluate(input));
        checks.addAll(brandBenchmarkCheck.evaluate(input));
        checks.addAll(signalChecks.evaluate(input));
        checks.addAll(combinationChecks.evaluate(input));

        ScoringResult result = decisionEngine.decide(checks);

        int flagCount = (int) checks.stream()
                .filter(check -> check.status() == CheckStatus.FLAG)
                .count();
        @SuppressWarnings("null")
        int creditPoints = BASE_CREDIT_POINTS + checks.stream()
                .mapToInt(CheckResult::scoreDelta)
                .sum();
        String scoringLog = checks.stream()
                .map(check -> check.ruleName() + "=" + check.status())
                .collect(Collectors.joining(", "))
                + ", kreditPoäng=" + creditPoints + " (ANVÄNDS EJ I BESLUT)";

        return new Score(
                result.decision().decisionValue(),
                flagCount,
                scoringLog,
                result.decision().statusValue(),
                result.summary());
    }
}