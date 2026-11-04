import type { FinancialMetricsFormData } from "../../../schemas/credit-application-schemas/FinancialMetrics.schema";

type FinancialMetricField = {
  name: keyof FinancialMetricsFormData;
  label: string;
  helpText?: string;
};

export const financialMetricFields: FinancialMetricField[] = [
  {
    name: "equity",
    label: "Eget kapital (SEK)",
    helpText: "Summa eget kapital",
  },
  {
    name: "totalCapital",
    label: "Totalt kapital (SEK)",
    helpText: "Balansomslutning",
  },
  {
    name: "currentAssets",
    label: "Omsättningstillgångar (SEK)",
  },
  {
    name: "currentLiabilities",
    label: "Kortfristiga skulder (SEK)",
  },
  {
    name: "totalLiabilities",
    label: "Totala skulder (SEK)",
  },
  {
    name: "operatingIncome",
    label: "Rörelseresultat (SEK)",
    helpText: "EBIT",
  },
  {
    name: "netRevenue",
    label: "Nettoomsättning (SEK)",
  },
];