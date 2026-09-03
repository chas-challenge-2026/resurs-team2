import { z } from "zod";

const numberField = (fieldName: string) =>
  z.number({
    error: `${fieldName} måste anges.`,
  });

export const financialMetricsSchema = z.object({
  equity: numberField("Eget kapital"),
  totalCapital: numberField("Totalt kapital"),
  currentAssets: numberField("Omsättningstillgångar"),
  currentLiabilities: numberField("Kortfristiga skulder"),
  totalLiabilities: numberField("Totala skulder"),
  operatingIncome: numberField("Rörelseresultat"),
  netRevenue: numberField("Nettoomsättning"),
});

export type FinancialMetricsData = z.infer<typeof financialMetricsSchema>;