package se.comerit.resurs.api.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import se.comerit.resurs.api.v1.dto.validation.MaxRequestedAmount;
import se.comerit.resurs.api.v1.dto.validation.MinRequestedAmount;

import java.math.BigDecimal;

public record ApplicationRequest(
    @NotNull(message = "Equity is required")
    Double equity,

    @NotNull(message = "Total capital is required")
    Double totalCapital,

    @NotNull(message = "Current assets is required")
    Double currentAssets,

    @NotNull(message = "Current liabilities is required")
    Double currentLiabilities,

    @NotNull(message = "Total liabilities is required")
    Double totalLiabilities,

    @NotNull(message = "Operating income is required")
    Double operatingIncome,

    @NotNull(message = "Net revenue is required")
    Double netRevenue,

    @NotNull(message = "Requested amount is required")
    @MinRequestedAmount
    @MaxRequestedAmount
    BigDecimal requestedAmount,

    @NotBlank(message = "Purpose is required")
    String purpose,

    Double operatingCashFlow,

    Double investingCashFlow,

    Double interestExpenses,

    String industry
) {

}
