package se.comerit.resurs.rating;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

/**
 * IndustryBenchmarkCheck – compares the applicant's solvency and profit margin
 * against the industry averages when the industry is known. Each comparison
 * produces its own {@link CheckResult}; nothing is returned when the industry
 * is unknown or within range.
 */
@Service
@Order(80)
public class IndustryBenchmarkCheck implements ScoringCheck {
    private final ScoringConfig config;

    public IndustryBenchmarkCheck(ScoringConfig config) {
        this.config = config;
    }

    @Override
    public String ruleName() {
        return "Branschjämförelse";
    }

    @Override
    public List<CheckResult> evaluate(ApplicationData data) {
        List<CheckResult> results = new ArrayList<>();
        String industry = data.industry();

        double solidityAverage = config.industryAverages().solidity().getOrDefault(industry, -1.0);
        if (solidityAverage >= 0) {
            double threshold = solidityAverage * config.industryAverages().solidityComparisonRatio();
            double solvency = data.totalCapital() != 0 ? data.equity() / data.totalCapital() : 0.0;
            if (solvency < threshold) {
                results.add(new CheckResult("Branschsnitt soliditet", solvency, threshold,
                        CheckStatus.FLAG, -6,
                        "VARNING: Soliditet betydligt under branschsnitt för " + industry
                                + " (snitt=" + String.format("%.2f", solidityAverage) + "). "));
            }
        }

        double marginAverage = config.industryAverages().margin().getOrDefault(industry, -1.0);
        if (marginAverage >= 0) {
            double threshold = marginAverage * config.industryAverages().marginComparisonRatio();
            double margin = data.netRevenue() != 0 ? data.operatingIncome() / data.netRevenue() : 0.0;
            if (margin < threshold) {
                results.add(new CheckResult("Branschsnitt marginal", margin, threshold,
                        CheckStatus.FLAG, -5,
                        "VARNING: Rörelsemarginal under "
                                + String.format("%.0f%%",
                                        config.industryAverages().marginComparisonRatio() * 100)
                                + " av branschsnitt för " + industry + ". "));
            }
        }

        return results;
    }
}