import type { FinancialMetricsData } from "@/schemas/credit-application-schemas/FinancialMetrics.schema";
import type { CreditAmountData } from "@/schemas/credit-application-schemas/CreditAmount.schema";

export const demoData: {
    financialMetrics: FinancialMetricsData;
    creditAmount: CreditAmountData;
} = {
    financialMetrics: {
        equity: 5000000,
        totalCapital: 10000000,
        currentAssets: 6000000,
        currentLiabilities: 2000000,
        totalLiabilities: 5000000,
        operatingIncome: 1500000,
        netRevenue: 8000000,
    },
    creditAmount: {
        requestedAmount: 500000,
        purpose: "Renovering av fastighet",
    },
};