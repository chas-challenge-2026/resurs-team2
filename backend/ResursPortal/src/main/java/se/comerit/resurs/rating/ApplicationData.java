package se.comerit.resurs.rating;

import java.math.BigDecimal;

/**
 * ApplicationData
 */
public record ApplicationData(
        double equity,
        double totalCapital,
        double currentAssets,
        double currentLiabilities,
        double totalLiabilities,
        double operatingIncome,
        double netRevenue,
        BigDecimal requestAmount,
        double operatingCashFlow,
        double investingCashFlow,
        double interestExpenses,
        String industry
    ) {
}
