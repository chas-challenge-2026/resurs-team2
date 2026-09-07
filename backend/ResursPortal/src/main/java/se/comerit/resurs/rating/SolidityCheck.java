package se.comerit.resurs.rating;

import java.util.List;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

@Service
@Order(10)
public class SolidityCheck implements ScoringCheck {
    private final ScoringConfig config;

    public SolidityCheck(ScoringConfig config) {
        this.config = config;
    }

    @Override
    public String ruleName() {
        return "Soliditet";
    }

    @Override
    public List<CheckResult> evaluate(ApplicationData data) {
        final double solvency = data.totalCapital() != 0 ? data.equity() / data.totalCapital() : 0.0;
        final double rejectThreshold = config.solidity().reject();
        final double flagThreshold = config.solidity().flag();

        if (solvency < rejectThreshold)
            return reject("Soliditet för låg", solvency, rejectThreshold, -40);
        if (solvency < flagThreshold)
            return flag("Soliditet låg", solvency, flagThreshold, -20);
        return ok("Soliditet OK", solvency, flagThreshold, 5);
    }
}
