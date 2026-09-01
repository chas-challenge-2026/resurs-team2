package se.comerit.resurs.api.v1.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import se.comerit.resurs.rating.ApplicationData;
import se.comerit.resurs.rating.CheckResult;
import se.comerit.resurs.rating.CheckStatus;
import se.comerit.resurs.rating.DecisionEngine;
import se.comerit.resurs.rating.Score;
import se.comerit.resurs.rating.ScoringCheck;
import se.comerit.resurs.rating.ScoringResult;

/**
 * ScoringService – orchestrates every scoring check and synthesizes the final
 * decision via the {@link DecisionEngine}.
 *
 * <p>Every check is a Spring bean implementing {@link ScoringCheck} and is
 * auto-injected as a {@code List<ScoringCheck>}. Each check is a pure function
 * of the input {@link ApplicationData} and returns its {@link CheckResult}s.
 * All results feed the decision engine, while the legacy {@link Score}
 * projection keeps the controller interface stable.
 */
@Service
public class ScoringService {
    public static final int BASE_CREDIT_POINTS = 100;

    private final List<ScoringCheck> scoringChecks;
    private final DecisionEngine decisionEngine;

    public ScoringService(List<ScoringCheck> scoringChecks, DecisionEngine decisionEngine) {
        this.scoringChecks = scoringChecks;
        this.decisionEngine = decisionEngine;
    }

    public Score score(ApplicationData input) {
        List<CheckResult> checks = new ArrayList<>();
        for (ScoringCheck check : scoringChecks) {
            checks.addAll(check.evaluate(input));
        }

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