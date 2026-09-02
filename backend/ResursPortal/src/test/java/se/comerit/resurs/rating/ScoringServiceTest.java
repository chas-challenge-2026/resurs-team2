package se.comerit.resurs.rating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static se.comerit.resurs.rating.ScoringTestData.Field.EQUITY;
import static se.comerit.resurs.rating.ScoringTestData.Field.TOTAL_CAPITAL;
import static se.comerit.resurs.rating.ScoringTestData.Field.CURRENT_ASSETS;
import static se.comerit.resurs.rating.ScoringTestData.Field.CURRENT_LIABILITIES;
import static se.comerit.resurs.rating.ScoringTestData.Field.TOTAL_LIABILITIES;
import static se.comerit.resurs.rating.ScoringTestData.Field.OPERATING_INCOME;
import static se.comerit.resurs.rating.ScoringTestData.Field.NET_REVENUE;
import static se.comerit.resurs.rating.ScoringTestData.Field.REQUEST;
import static se.comerit.resurs.rating.ScoringTestData.Field.OPERATING_CASHFLOW;
import static se.comerit.resurs.rating.ScoringTestData.Field.INVESTING_CASHFLOW;
import static se.comerit.resurs.rating.ScoringTestData.Field.INTEREST_EXPENSES;
import static se.comerit.resurs.rating.ScoringTestData.Field.INDUSTRY;
import static se.comerit.resurs.rating.ScoringTestData.healthy;
import static se.comerit.resurs.rating.ScoringTestData.mutate;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import se.comerit.resurs.api.v1.service.ScoringService;

/**
 * Behavioural lock-in tests for the ORIGINAL scoring engine (currently still
 * implemented inline in {@link ScoringService}).
 *
 * <p>These tests document the exact observable behaviour of the current engine,
 * so a refactor into the check-based architecture (SolidityCheck,
 * LiquidityCheck, etc.) can be verified to be behaviour-preserving. If a
 * refactored engine changes any of these outcomes, the corresponding test
 * fails.
 *
 * <p>Note: the original engine has highly interdependent checks — e.g. a single
 * mutated field can trip several rules at once (negative operating income also
 * makes interest-coverage collapse). Where possible inputs were chosen to
 * isolate a single rule; where that is structurally impossible the test name
 * documents the compounded outcome it locks in.
 */
class ScoringServiceTest {

    private ScoringService service;

    @BeforeEach
    void setUp() {
        ScoringConfig config = ScoringTestData.config();
        service = new ScoringService(
                List.of(
                        new SolidityCheck(config),
                        new LiquidityCheck(config),
                        new DebtRatioCheck(config),
                        new ProfitMarginCheck(config),
                        new CashFlowCheck(config),
                        new InterestCoverageCheck(config),
                        new BrandFactorCheck(config),
                        new IndustryBenchmarkCheck(config),
                        new SignalChecks(config),
                        new CombinationChecks(config)),
                new DecisionEngine(config));
    }

    private Score run(ApplicationData data) {
        return ScoringService.toScore(service.score(data));
    }

    // ================================================================
    // BASELINE
    // ================================================================
    @Nested
    @DisplayName("Baseline healthy application")
    class Baseline {
        @Test
        @DisplayName("A healthy application is APPROVED with zero flags")
        void healthyIsApproved() {
            Score s = run(healthy());
            assertEquals("APPROVED", s.decision());
            assertEquals("APPROVED", s.status());
            assertEquals(0, s.flagCount());
        }

        @Test
        @DisplayName("Decision reason states the application was approved")
        void healthyApprovalReason() {
            assertTrue(run(healthy()).decisionReason().contains("ANSÖKAN GODKÄND"));
        }
    }

    // ================================================================
    // SOLVENCY (equity / total capital) — reject < 0.20, flag < 0.25
    // ================================================================
    @Nested
    @DisplayName("Solvency checks")
    class Solvency {

        @Test
        @DisplayName("Solvency below reject threshold -> REJECTED")
        void rejectWhenBelowRejectThreshold() {
            // solvency = 100k/1M = 0.10 ; debt = 200k/100k = 2.0 (not > 2.0)
            Score s = run(mutate(healthy(), EQUITY, 100_000, TOTAL_LIABILITIES, 200_000));
            assertEquals("REJECTED", s.decision());
            assertEquals("REJECTED", s.status());
        }

        @Test
        @DisplayName("Solvency between reject and flag thresholds -> REVIEW")
        void flagWhenBetweenThresholds() {
            // solvency = 220k/1M = 0.22
            Score s = run(mutate(healthy(), EQUITY, 220_000, TOTAL_LIABILITIES, 240_000));
            assertEquals("REVIEW", s.decision());
            assertEquals("UNDER_REVIEW", s.status());
            assertTrue(s.flagCount() > 0);
        }

        @Test
        @DisplayName("Solvency exactly at reject threshold (0.20) is NOT hard-rejected")
        void atRejectBoundaryIsFlagNotReject() {
            // solvency = 200k/1M = 0.20 (0.20 < 0.20 is false)
            Score s = run(mutate(healthy(), EQUITY, 200_000, TOTAL_LIABILITIES, 300_000));
            assertNotEquals("REJECTED", s.decision());
        }

        @Test
        @DisplayName("Division by zero total capital is handled (no crash)")
        void zeroTotalCapitalIsHandled() {
            Score s = run(mutate(healthy(), TOTAL_CAPITAL, 0));
            // soliditet = 0.0 -> REJECT, but no exception thrown
            assertEquals("REJECTED", s.decision());
        }
    }

    // ================================================================
    // LIQUIDITY (current assets / current liabilities) — reject < 1.0,
    // good >= 2.0, marginal 1.0..1.2
    // ================================================================
    @Nested
    @DisplayName("Liquidity checks")
    class Liquidity {

        @Test
        @DisplayName("Liquidity below 1.0 is flagged")
        void lowLiquidityIsFlagged() {
            // liquidity = 150k/200k = 0.75
            Score s = run(mutate(healthy(), CURRENT_ASSETS, 150_000, CURRENT_LIABILITIES, 200_000));
            assertEquals("REVIEW", s.decision());
            assertEquals(1, s.flagCount());
        }

        @Test
        @DisplayName("Liquidity in the marginal band (1.0..1.2) is flagged")
        void marginalLiquidityIsFlagged() {
            // liquidity = 110k/100k = 1.1
            Score s = run(mutate(healthy(), CURRENT_ASSETS, 110_000));
            assertEquals("REVIEW", s.decision());
            assertEquals(1, s.flagCount());
        }

        @Test
        @DisplayName("Good liquidity (>= 2.0) is not flagged for liquidity")
        void goodLiquidityNotFlagged() {
            assertEquals(0, run(healthy()).flagCount());
        }
    }

    // ================================================================
    // DEBT RATIO (total liabilities / equity) — reject > 3.0, flag > 2.0
    // ================================================================
    @Nested
    @DisplayName("Debt ratio checks")
    class DebtRatio {

        @Test
        @DisplayName("Debt ratio above reject threshold -> REJECTED")
        void highDebtIsRejected() {
            // equity 100k, totalLiab 400k -> debt 4.0
            Score s = run(mutate(healthy(), EQUITY, 100_000, TOTAL_LIABILITIES, 400_000));
            assertEquals("REJECTED", s.decision());
        }

        @Test
        @DisplayName("Debt ratio exactly at flag threshold (2.0) is NOT flagged")
        void debtAtFlagBoundaryNotFlagged() {
            // equity 250k, totalLiab 500k -> debt 2.0 (not > 2.0).
            // Industry FASTIGHET keeps solvency-benchmark checks silent
            // (avg solvency 0.18 * 0.75 = 0.135 < 0.25).
            Score s = run(mutate(healthy(),
                    EQUITY, 250_000,
                    TOTAL_LIABILITIES, 500_000,
                    INDUSTRY, "FASTIGHET"));
            assertEquals("APPROVED", s.decision());
            assertEquals(0, s.flagCount());
        }
    }

    // ================================================================
    // PROFIT MARGIN (operating income / net revenue) — flag < 0.02
    // Note: in the original engine a low margin collapses interest coverage
    // into a hard reject, so these are hardened rejections in practice.
    // ================================================================
    @Nested
    @DisplayName("Profit margin checks")
    class ProfitMargin {

        @Test
        @DisplayName("Negative operating income triggers a REJECT via interest coverage")
        void negativeIncomeRejects() {
            // income -50k => coverage -50k/20k < 1.5 -> hard reject
            Score s = run(mutate(healthy(), OPERATING_INCOME, -50_000));
            assertEquals("REJECTED", s.decision());
        }

        @Test
        @DisplayName("Very low positive margin triggers a REJECT via interest coverage")
        void lowMarginRejects() {
            // margin 10k/1M = 0.01 ; coverage 10k/20k = 0.5 < 1.5
            Score s = run(mutate(healthy(), OPERATING_INCOME, 10_000));
            assertEquals("REJECTED", s.decision());
        }

        @Test
        @DisplayName("Healthy margin is not flagged")
        void healthyMarginNotFlagged() {
            assertEquals(0, run(healthy()).flagCount());
        }
    }

    // ================================================================
    // CASH FLOW (operating cash flow / total liabilities) — reject < 0,
    // flag < 0.05
    // ================================================================
    @Nested
    @DisplayName("Cash flow checks")
    class CashFlow {

        @Test
        @DisplayName("Negative operating cash flow -> REJECTED")
        void negativeCashFlowRejected() {
            Score s = run(mutate(healthy(), OPERATING_CASHFLOW, -10_000));
            assertEquals("REJECTED", s.decision());
        }

        @Test
        @DisplayName("Low positive cash flow ratio is flagged")
        void lowCashFlowFlagged() {
            // capc flow 20k/500k = 0.04 (< 0.05)
            Score s = run(mutate(healthy(), OPERATING_CASHFLOW, 20_000));
            assertEquals("REVIEW", s.decision());
            assertEquals(1, s.flagCount());
        }

        @Test
        @DisplayName("Highly negative investing cash flow is flagged")
        void negativeInvestingCashFlowFlagged() {
            // investing -400k < -1M * 0.3 = -300k
            Score s = run(mutate(healthy(), INVESTING_CASHFLOW, -400_000));
            assertEquals("REVIEW", s.decision());
        }
    }

    // ================================================================
    // INTEREST COVERAGE (operating income / interest expenses) — reject <
    // 1.5, flag < 2.5; N/A when no interest expenses
    // ================================================================
    @Nested
    @DisplayName("Interest coverage checks")
    class InterestCoverage {

        @Test
        @DisplayName("No interest expenses -> coverage not assessed, application approved")
        void noInterestIsNotFlaggedForCoverage() {
            assertEquals("APPROVED", run(mutate(healthy(), INTEREST_EXPENSES, 0)).decision());
        }

        @Test
        @DisplayName("Coverage below reject threshold -> REJECTED")
        void lowCoverageRejected() {
            // coverage = 150k/200k = 0.75
            Score s = run(mutate(healthy(), INTEREST_EXPENSES, 200_000));
            assertEquals("REJECTED", s.decision());
        }

        @Test
        @DisplayName("Coverage between flag and reject -> REVIEW")
        void flagCoverage() {
            // coverage = 150k/75k = 2.0 (< 2.5)
            Score s = run(mutate(healthy(), INTEREST_EXPENSES, 75_000));
            assertEquals("REVIEW", s.decision());
            assertEquals(1, s.flagCount());
        }
    }

    // ================================================================
    // EXTRA / SIGNAL CHECKS
    // ================================================================
    @Nested
    @DisplayName("Extra signal checks")
    class ExtraSignals {

        @Test
        @DisplayName("Negative equity -> REJECTED")
        void negativeEquityRejected() {
            Score s = run(mutate(healthy(), EQUITY, -50_000));
            assertEquals("REJECTED", s.decision());
        }

        @Test
        @DisplayName("Low net revenue (< 500k) is flagged")
        void lowRevenueFlagged() {
            // revenue 300k < 500k ; margin 40k/300k = 0.13 (> 0.02, no margin flag);
            // interest 10k keeps coverage at 4.0 (> 2.5) so the only flag is revenue.
            Score s = run(mutate(healthy(),
                    NET_REVENUE, 300_000,
                    OPERATING_INCOME, 40_000,
                    INTEREST_EXPENSES, 10_000));
            assertEquals("REVIEW", s.decision());
            assertEquals(1, s.flagCount());
        }

        @Test
        @DisplayName("Large credit (> 5M) is flagged")
        void largeCreditFlagged() {
            Score s = run(mutate(healthy(), REQUEST, new BigDecimal("6000000")));
            assertEquals("REVIEW", s.decision());
            assertTrue(s.flagCount() >= 1);
        }

        @Test
        @DisplayName("Low solvency combined with a large credit is flagged")
        void lowSolvencyWithLargeCreditFlagged() {
            // solvency 0.20 (< 0.30) AND request 1.5M (> 1M)
            Score s = run(mutate(healthy(), EQUITY, 200_000, REQUEST, new BigDecimal("1500000")));
            assertEquals("REVIEW", s.decision());
            assertTrue(s.flagCount() >= 1);
        }

        @Test
        @DisplayName("Total liabilities above twice net revenue is flagged")
        void highDebtVsRevenueFlagged() {
            // equity 3M keeps debt ratio low (2.1M/3M = 0.7); totalLiab 2.1M > 2M.
            Score s = run(mutate(healthy(),
                    TOTAL_LIABILITIES, 2_100_000,
                    EQUITY, 3_000_000,
                    TOTAL_CAPITAL, 6_000_000));
            assertEquals("REVIEW", s.decision());
            assertTrue(s.flagCount() >= 1);
        }
    }

    // ================================================================
    // COMBINATION RULES
    // ================================================================
    @Nested
    @DisplayName("Combination rules")
    class Combination {

        @Test
        @DisplayName("Requested amount above annual revenue is flagged")
        void creditAboveRevenueFlagged() {
            // request 1.5M > netRevenue 1M ; < 5M so no large-credit flag
            Score s = run(mutate(healthy(), REQUEST, new BigDecimal("1500000")));
            assertEquals("REVIEW", s.decision());
            assertEquals(1, s.flagCount());
        }

        @Test
        @DisplayName("Equity below 30% of requested amount is flagged")
        void lowEquityVsCreditFlagged() {
            // equity 260k/request 900k = 0.29 < 0.3 ; solvency 0.26 (> 0.25,
            // no solvency flag) ; request 900k < revenue 1M (no combo-3 flag).
            Score s = run(mutate(healthy(),
                    EQUITY, 260_000,
                    REQUEST, new BigDecimal("900000"),
                    INDUSTRY, "FASTIGHET"));
            assertEquals("REVIEW", s.decision());
            assertEquals(1, s.flagCount());
        }

        @Test
        @DisplayName("Debt burden above twice revenue is flagged")
        void debtBurdenVsRevenueFlagged() {
            // (totalLiab 2.2M + currentLiab 100k)/(revenue 1M + 1) = 2.3 > 2.0 ;
            // equity 3M keeps the plain debt ratio low (0.73).
            Score s = run(mutate(healthy(),
                    TOTAL_LIABILITIES, 2_200_000,
                    EQUITY, 3_000_000,
                    TOTAL_CAPITAL, 6_000_000));
            assertEquals("REVIEW", s.decision());
            assertTrue(s.flagCount() >= 1);
        }
    }

    // ================================================================
    // TRACEABILITY
    // ================================================================
    @Nested
    @DisplayName("Traceability of reasons and logs")
    class Traceability {

        @Test
        @DisplayName("Decision reason is non-empty and scoring log tracks credit points")
        void reasonAndLogArePopulated() {
            Score s = run(healthy());
            assertFalse(s.decisionReason().isBlank());
            assertTrue(s.scoringLog().contains("kreditPoäng"));
        }

        @Test
        @DisplayName("Reasons differ between approved and rejected applications")
        void reasonsDiffer() {
            String approved = run(healthy()).decisionReason();
            String rejected = run(mutate(healthy(), OPERATING_CASHFLOW, -10_000)).decisionReason();
            assertNotEquals(approved, rejected);
        }

        @Test
        @DisplayName("Unknown industry is tolerated (default factor 1.0)")
        void unknownIndustryDoesNotFail() {
            Score s = run(mutate(healthy(), INDUSTRY, ""));
            assertTrue(s.decisionReason().contains("ANSÖKAN GODKÄND"));
        }
    }
}