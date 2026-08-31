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
  operatingResult: numberField("Rörelseresultat"),
  netSales: numberField("Nettoomsättning"),
});

export type FinancialMetricsData = z.infer<typeof financialMetricsSchema>;