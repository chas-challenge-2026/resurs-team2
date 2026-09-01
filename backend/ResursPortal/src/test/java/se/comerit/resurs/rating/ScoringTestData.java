package se.comerit.resurs.rating;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Fixture helpers for scoring tests.
 *
 * <p>The config values mirror {@code application.properties} so tests track the
 * production defaults. Any threshold change in properties should be reflected
 * here, otherwise tests document the drift.
 */
public final class ScoringTestData {

    private ScoringTestData() {
    }

    /** A config fully populated with the values defined in application.properties. */
    public static ScoringConfig config() {
        return new ScoringConfig(
                metric(0.20, 0.25, 0.30),                 // solidity
                metric(1.0, 2.0, 2.0),                    // liquidity
                metric(3.0, 2.0, 2.0),                    // debt ratio
                metric(0.02, 0.02, 0.10),                 // profit margin
                metric(0.0, 0.05, 0.05),                  // cash flow
                metric(1.5, 2.5, 2.5),                    // interest coverage
                new ScoringConfig.ExtraThresholds(
                        5_000_000,                        // large-credit-threshold
                        1_000_000,                        // large-credit-with-low-solvency
                        0.30,                             // low-solvency-threshold
                        2.0,                              // high-debt-to-revenue-multiplier
                        500_000,                          // low-revenue-threshold
                        0.0,                              // negative-equity-threshold
                        0.0,                              // negative-income-threshold
                        0.3),                             // negative-investing-ratio
                new ScoringConfig.CombinationThresholds(
                        1.0,                              // credit-vs-revenue-minimum
                        0.3,                              // equity-vs-credit-ratio
                        2.0),                             // debt-burden-vs-revenue-threshold
                new ScoringConfig.IndustryAverages(
                        defaultSolidityAverages(),
                        defaultDebtRatioAverages(),
                        defaultMarginAverages(),
                        0.75,                             // solidity-comparison-ratio
                        0.5),                             // margin-comparison-ratio
                defaultIndustryFactors());
    }

    private static ScoringConfig.MetricThreshold metric(double reject, double flag, double good) {
        return new ScoringConfig.MetricThreshold(reject, flag, good);
    }

    /** An ApplicationData with every metric in a healthy/approved state. */
    public static ApplicationData healthy() {
        // solvency 0.50, liquidity 3.0, debt 1.0, margin 0.15,
        // cash flow 0.20, positive equity, large revenue, small clean credit.
        return new ApplicationData(
                500_000,        // equity
                1_000_000,      // totalCapital
                300_000,        // currentAssets
                100_000,        // currentLiabilities
                500_000,        // totalLiabilities
                150_000,        // operatingIncome
                1_000_000,      // netRevenue
                new BigDecimal("100000"),  // requestAmount
                100_000,        // operatingCashFlow
                -10_000,        // investingCashFlow
                20_000,         // interestExpenses
                "IT");
    }

    /**
     * Copy of {@code base} with the given overrides applied. Values ±1 wrap one
     * value; the flag {@code -1} means "leave unchanged" for that slot. This keeps
     * call sites compact while tests document what they changed.
     *
     * <p>Usage: {@code mutate(healthy(), EQUITY, 100_000, NET_REVENUE, 300_000)}
     */
    public static ApplicationData mutate(ApplicationData base, Object... overrides) {
        double equity = base.equity();
        double totalCapital = base.totalCapital();
        double currentAssets = base.currentAssets();
        double currentLiabilities = base.currentLiabilities();
        double totalLiabilities = base.totalLiabilities();
        double operatingIncome = base.operatingIncome();
        double netRevenue = base.netRevenue();
        BigDecimal request = base.requestAmount();
        double operatingCashFlow = base.operatingCashFlow();
        double investingCashFlow = base.investingCashFlow();
        double interestExpenses = base.interestExpenses();
        String industry = base.industry();

        for (int i = 0; i < overrides.length; i += 2) {
            Field field = (Field) overrides[i];
            Object value = overrides[i + 1];
            switch (field) {
                case EQUITY -> equity = toDouble(value);
                case TOTAL_CAPITAL -> totalCapital = toDouble(value);
                case CURRENT_ASSETS -> currentAssets = toDouble(value);
                case CURRENT_LIABILITIES -> currentLiabilities = toDouble(value);
                case TOTAL_LIABILITIES -> totalLiabilities = toDouble(value);
                case OPERATING_INCOME -> operatingIncome = toDouble(value);
                case NET_REVENUE -> netRevenue = toDouble(value);
                case REQUEST -> request = (BigDecimal) value;
                case OPERATING_CASHFLOW -> operatingCashFlow = toDouble(value);
                case INVESTING_CASHFLOW -> investingCashFlow = toDouble(value);
                case INTEREST_EXPENSES -> interestExpenses = toDouble(value);
                case INDUSTRY -> industry = (String) value;
                default -> throw new IllegalStateException("Unexpected field " + field);
            }
        }
        return new ApplicationData(equity, totalCapital, currentAssets, currentLiabilities,
                totalLiabilities, operatingIncome, netRevenue, request, operatingCashFlow,
                investingCashFlow, interestExpenses, industry);
    }

    private static double toDouble(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        throw new IllegalArgumentException("Expected a number but got " + value.getClass().getName());
    }

    public enum Field {
        EQUITY, TOTAL_CAPITAL, CURRENT_ASSETS, CURRENT_LIABILITIES, TOTAL_LIABILITIES,
        OPERATING_INCOME, NET_REVENUE, REQUEST, OPERATING_CASHFLOW, INVESTING_CASHFLOW,
        INTEREST_EXPENSES, INDUSTRY
    }

    // ------------------------------------------------------------------
    // Industry tables (mirror application.properties)
    // ------------------------------------------------------------------
    public static Map<String, Double> defaultIndustryFactors() {
        return Map.ofEntries(
                Map.entry("BYGG", 0.85),
                Map.entry("HANDEL", 1.1),
                Map.entry("IT", 1.2),
                Map.entry("FASTIGHET", 0.9),
                Map.entry("TILLVERKNING", 0.95),
                Map.entry("TRANSPORT", 0.88),
                Map.entry("RESTAURANG", 0.80),
                Map.entry("FINANS", 1.15),
                Map.entry("VÅRD", 1.05),
                Map.entry("UTBILDNING", 1.0));
    }

    public static Map<String, Double> defaultSolidityAverages() {
        return Map.ofEntries(
                Map.entry("BYGG", 0.22), Map.entry("HANDEL", 0.28), Map.entry("IT", 0.45),
                Map.entry("FASTIGHET", 0.18), Map.entry("TILLVERKNING", 0.30),
                Map.entry("TRANSPORT", 0.20), Map.entry("RESTAURANG", 0.15),
                Map.entry("FINANS", 0.35), Map.entry("VÅRD", 0.38), Map.entry("UTBILDNING", 0.32));
    }

    public static Map<String, Double> defaultDebtRatioAverages() {
        return Map.ofEntries(
                Map.entry("BYGG", 2.8), Map.entry("HANDEL", 1.9), Map.entry("IT", 0.8),
                Map.entry("FASTIGHET", 3.5), Map.entry("TILLVERKNING", 1.5),
                Map.entry("TRANSPORT", 2.2), Map.entry("RESTAURANG", 2.5),
                Map.entry("FINANS", 1.2), Map.entry("VÅRD", 0.9), Map.entry("UTBILDNING", 1.1));
    }

    public static Map<String, Double> defaultMarginAverages() {
        return Map.ofEntries(
                Map.entry("BYGG", 0.04), Map.entry("HANDEL", 0.03), Map.entry("IT", 0.15),
                Map.entry("FASTIGHET", 0.12), Map.entry("TILLVERKNING", 0.06),
                Map.entry("TRANSPORT", 0.03), Map.entry("RESTAURANG", 0.05),
                Map.entry("FINANS", 0.18), Map.entry("VÅRD", 0.07), Map.entry("UTBILDNING", 0.08));
    }
}
