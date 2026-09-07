package se.comerit.resurs.rating;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ScoringConfig – all tunable thresholds and industry data for the scoring
 * engine, bound from application.properties under the {@code resurs.scoring}
 * prefix.
 *
 * <p>Each sub-record maps to a group of related thresholds so the engine stays
 * configurable at runtime without code changes.
 */
@ConfigurationProperties(prefix = "resurs.scoring")
public record ScoringConfig(
                MetricThreshold solidity,
                MetricThreshold liquidity,
                MetricThreshold debtRatio,
                MetricThreshold profitMargin,
                MetricThreshold cashFlow,
                MetricThreshold interestCoverage,
                ExtraThresholds extra,
                CombinationThresholds combination,
                int reviewFlagThreshold, // review-flag-threshold
                IndustryAverages industryAverages,
                Map<String, Double> industryFactors) {

        /**
         * Thresholds shared by the individual ratio checks.
         *
         * @param reject   hard-rejection boundary
         * @param flag     warning/flag boundary
         * @param good     strong/good boundary (may be unused by some checks)
         * @param marginal upper bound of a "near the limit" band that is flagged
         *                 (unused by checks without such a band)
         */
        public record MetricThreshold(
                        double reject,
                        double flag,
                        double good,
                        double marginal) {
        }

        /**
         * Thresholds for standalone signal checks (non-ratio).
         *
         * @param largeCreditThreshold       amount above which the credit is flagged (kr)
         * @param largeCreditWithLowSolvency credit amount that combined with low
         *                                   solvency triggers a flag (kr)
         * @param lowSolvencyThreshold       solvency below which, combined with a
         *                                   large credit, triggers a flag
         * @param highDebtToRevenueMultiplier liabilities multiplier over revenue that
         *                                   is flagged
         * @param lowRevenueThreshold        revenue below which is flagged (kr)
         * @param negativeEquityThreshold    equity below which is a hard rejection
         * @param negativeIncomeThreshold    result below which is flagged
         * @param negativeInvestingRatio     investing cash-flow magnitude relative to
         *                                   revenue that is flagged
         */
        public record ExtraThresholds(
                        double largeCreditThreshold,
                        double largeCreditWithLowSolvency,
                        double lowSolvencyThreshold,
                        double highDebtToRevenueMultiplier,
                        double lowRevenueThreshold,
                        double negativeEquityThreshold,
                        double negativeIncomeThreshold,
                        double negativeInvestingRatio) {
        }

        /**
         * Thresholds for the combined-risk rules.
         *
         * @param creditVsRevenueMinimum        minimum ratio of credit to revenue
         *                                      before flagging
         * @param equityVsCreditRatio           minimum equity-to-credit ratio before
         *                                      flagging
         * @param debtBurdenVsRevenueThreshold  debt-burden-to-revenue threshold that
         *                                      triggers a flag
         */
        public record CombinationThresholds(
                        double creditVsRevenueMinimum,
                        double equityVsCreditRatio,
                        double debtBurdenVsRevenueThreshold) {
        }

        /**
         * Industry benchmark averages compared against the applicant's ratios.
         *
         * @param solidity                 average solvency per industry
         * @param debtRatio                average debt ratio per industry
         * @param margin                   average profit margin per industry
         * @param solidityComparisonRatio  threshold fraction of the industry solvency
         *                                 average below which a flag is raised
         * @param marginComparisonRatio    threshold fraction of the industry margin
         *                                 average below which a flag is raised
         */
        public record IndustryAverages(
                        Map<String, Double> solidity,
                        Map<String, Double> debtRatio,
                        Map<String, Double> margin,
                        double solidityComparisonRatio,
                        double marginComparisonRatio) {
        }
}
