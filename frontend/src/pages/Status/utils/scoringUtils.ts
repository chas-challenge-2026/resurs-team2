import type { FinancialData } from "@/types/applicationDetails";

export interface ScoreItem {
  key: string;
  label: string;
  value: string;
  numericValue: number;
  flagged: boolean;
  requirementText: string;
}

const labels: Record<string, string> = {
  soliditet: "Soliditet",
  likviditetsgrad: "Likviditetsgrad",
  skuldsättningsgrad: "Skuldsättningsgrad",
  rörelsemarginal: "Rörelsemarginal",
  kassaflödeskvot: "Kassaflödeskvot",
  räntetäckningsgrad: "Räntetäckningsgrad",
  branschfaktor: "Branschfaktor",
};

const requirements: Record<
  string,
  {
    goodThreshold: number;
    higherIsBetter: boolean;
    description: string;
    formula: string;
  }
> = {
  soliditet: {
    goodThreshold: 0.3,
    higherIsBetter: true,
    description:
      "Visar hur stor del av företagets kapital som finansieras med eget kapital.",
    formula: "Eget kapital / Totalt kapital",
  },
  likviditetsgrad: {
    goodThreshold: 2.0,
    higherIsBetter: true,
    description: "Visar företagets förmåga att täcka sina kortfristiga skulder.",
    formula: "Omsättningstillgångar / Kortfristiga skulder",
  },
  skuldsättningsgrad: {
    goodThreshold: 2.0,
    higherIsBetter: false,
    description: "Visar förhållandet mellan företagets skulder och eget kapital.",
    formula: "Totala skulder / Eget kapital",
  },
  rörelsemarginal: {
    goodThreshold: 0.1,
    higherIsBetter: true,
    description: "Visar rörelseresultatets andel av nettoomsättningen.",
    formula: "Rörelseresultat / Nettoomsättning",
  },
  kassaflödeskvot: {
    goodThreshold: 0.05,
    higherIsBetter: true,
    description: "Visar operativt kassaflöde i förhållande till totala skulder.",
    formula: "Operativt kassaflöde / Totala skulder",
  },
  räntetäckningsgrad: {
    goodThreshold: 2.5,
    higherIsBetter: true,
    description: "Visar förmågan att betala räntor på skulder.",
    formula: "Rörelseresultat / Räntekostnader",
  },
};

const STATUSES = ["OK", "FLAG", "FLAGGED", "SKIP"];

const formatNumber = (value: number) =>
  new Intl.NumberFormat("sv-SE", {
    maximumFractionDigits: 2,
  }).format(value);

const safeDiv = (a: number, b: number): number | null =>
  b === 0 ? null : a / b;

const computeMetric = (key: string, fd: FinancialData): number | null => {
  switch (key) {
    case "soliditet":
      return safeDiv(fd.equity, fd.totalCapital);
    case "likviditetsgrad":
      return safeDiv(fd.currentAssets, fd.currentLiabilities);
    case "skuldsättningsgrad":
      return safeDiv(fd.totalLiabilities, fd.equity);
    case "rörelsemarginal":
      return safeDiv(fd.operatingIncome, fd.netRevenue);
    case "kassaflödeskvot":
      return safeDiv(fd.operatingCashFlow, fd.totalLiabilities);
    case "räntetäckningsgrad":
      return safeDiv(fd.operatingIncome, fd.interestExpenses);
    default:
      return null;
  }
};


export const parseScoringResult = (
  scoringResult: string,
  financialData: FinancialData | null
): Omit<ScoreItem, "requirementText">[] => {
  const [, values = scoringResult] = scoringResult.split(":");

  return values
    .split(",")
    .map((item) => item.trim())
    .flatMap((trimmed) => {
      const match = trimmed.match(
        /^(.+?)=(.+?)(?:\s+\((OK|FLAG|FLAGGED|SKIP)\))?$/i
      );
      if (!match) return [];

      const [, key, raw, parenStatus] = match;
      const status = (parenStatus ?? raw).trim().toUpperCase();
      if (!STATUSES.includes(status)) return [];

      const normalizedKey = key.toLowerCase().trim();
      const computed = financialData
        ? computeMetric(normalizedKey, financialData)
        : null;
      const parsed = parseFloat(raw.replace(",", "."));
      const numericValue = computed ?? (isNaN(parsed) ? 0 : parsed);

      return [
        {
          key: normalizedKey,
          label: labels[normalizedKey] ?? key.trim(),
          value: formatNumber(numericValue),
          numericValue,
          flagged: status === "FLAG" || status === "FLAGGED",
        },
      ];
    });
};

export const getScoringItemsWithRequirements = (
  scoringResult: string,
  financialData: FinancialData | null
): ScoreItem[] =>
  parseScoringResult(scoringResult, financialData)
    .filter((item) => item.flagged) 
    .map((item) => {
      const req = requirements[item.key];
      let requirementText: string;

      if (req) {
        const limit = formatNumber(req.goodThreshold);
        const direction = req.higherIsBetter ? "minst" : "högst";
        requirementText = `Ditt ${item.label.toLowerCase()}värde är ${item.value}, vi behöver ${direction} ${limit} för att godkänna.`;
      } else {
        requirementText = `Värdet ${item.value} kräver granskning.`;
      }

      return { ...item, requirementText };
    });

export const getMetricTooltip = (
  key: string,
  financialData: FinancialData
) => {
  const build = (
    reqKey: string,
    numerator: number,
    denominator: number
  ) => {
    const ratio = safeDiv(numerator, denominator);
    return {
      description: requirements[reqKey].description,
      formula: requirements[reqKey].formula,
      calculation: `${formatNumber(numerator)} / ${formatNumber(
        denominator
      )} = ${ratio === null ? "ej beräkningsbar" : formatNumber(ratio)}`,
    };
  };

  switch (key) {
    case "soliditet":
      return build("soliditet", financialData.equity, financialData.totalCapital);
    case "likviditetsgrad":
      return build(
        "likviditetsgrad",
        financialData.currentAssets,
        financialData.currentLiabilities
      );
    case "skuldsättningsgrad":
      return build(
        "skuldsättningsgrad",
        financialData.totalLiabilities,
        financialData.equity
      );
    case "rörelsemarginal":
      return build(
        "rörelsemarginal",
        financialData.operatingIncome,
        financialData.netRevenue
      );
    case "kassaflödeskvot":
      return build(
        "kassaflödeskvot",
        financialData.operatingCashFlow,
        financialData.totalLiabilities
      );
    case "räntetäckningsgrad":
      return build(
        "räntetäckningsgrad",
        financialData.operatingIncome,
        financialData.interestExpenses
      );
    default:
      return null;
  }
};