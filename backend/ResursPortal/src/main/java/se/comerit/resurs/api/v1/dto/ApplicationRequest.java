package se.comerit.resurs.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import se.comerit.resurs.api.v1.dto.validation.MaxRequestedAmount;
import se.comerit.resurs.api.v1.dto.validation.MinRequestedAmount;

import java.math.BigDecimal;

@Schema(description = "Credit application request payload")
public record ApplicationRequest(
    @Schema(description = "Owner's equity in SEK", example = "5000000")
    @NotNull(message = "Equity is required")
    Double equity,

    @Schema(description = "Total capital (equity + liabilities) in SEK", example = "12000000")
    @NotNull(message = "Total capital is required")
    Double totalCapital,

    @Schema(description = "Current assets in SEK", example = "3000000")
    @NotNull(message = "Current assets is required")
    Double currentAssets,

    @Schema(description = "Current liabilities in SEK", example = "1500000")
    @NotNull(message = "Current liabilities is required")
    Double currentLiabilities,

    @Schema(description = "Total liabilities in SEK", example = "7000000")
    @NotNull(message = "Total liabilities is required")
    Double totalLiabilities,

    @Schema(description = "Operating income in SEK", example = "2500000")
    @NotNull(message = "Operating income is required")
    Double operatingIncome,

    @Schema(description = "Net revenue in SEK", example = "10000000")
    @NotNull(message = "Net revenue is required")
    Double netRevenue,

    @Schema(description = "Requested loan amount in SEK (min 50 000, max 10 000 000)",
            example = "7500000",
            minimum = "50000",
            maximum = "10000000")
    @NotNull(message = "Requested amount is required")
    @MinRequestedAmount
    @MaxRequestedAmount
    BigDecimal requestedAmount,

    @Schema(description = "Intended use of the loan funds", example = "Working capital for expansion")
    @NotBlank(message = "Purpose is required")
    String purpose,

    @Schema(description = "Cash flow from operating activities in SEK", example = "1200000")
    Double operatingCashFlow,

    @Schema(description = "Cash flow from investing activities in SEK", example = "-800000")
    Double investingCashFlow,

    @Schema(description = "Annual interest expenses in SEK", example = "150000")
    Double interestExpenses,

    @Schema(description = "Industry classification of the company", example = "IT Services")
    String industry
) {

}
