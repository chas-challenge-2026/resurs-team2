import { MetricTooltip } from "@/components/metricTooltips/MetricTooltips";
import { Panel } from "@/components/Panel/Panel";
import type { FinancialData } from "@/types/applicationDetails";

interface ScoringResultPanelProps {
  scoringResult: string | null;
  financialData: FinancialData | null;
}

interface ScoreItem {
  key: string;
  label: string;
  value: string;
  flagged: boolean;
}

const labels: Record<string, string> = {
  soliditet: "Soliditet",
  likviditetsgrad: "Likviditetsgrad",
  skuldsättningsgrad: "Skuldsättningsgrad",
};

const formatNumber = (value: number) =>
  new Intl.NumberFormat("sv-SE", {
    maximumFractionDigits: 2,
  }).format(value);

const parseScoringResult = (
  scoringResult: string,
): ScoreItem[] => {
  const [, values = scoringResult] =
    scoringResult.split(":");

  return values.split(",").map((item) => {
    const match = item
      .trim()
      .match(/^(.+?)=(.+?) \((OK|FLAGGED)\)$/);

    if (!match) {
      return {
        key: item.trim(),
        label: item.trim(),
        value: "",
        flagged: false,
      };
    }

    const [, key, value, status] = match;

    return {
      key,
      label: labels[key] ?? key,
      value: value.replace(".", ","),
      flagged: status === "FLAGGED",
    };
  });
};

const getMetricTooltip = (
  key: string,
  financialData: FinancialData,
) => {
  switch (key) {
    case "soliditet":
      return {
        description:
          "Visar hur stor del av företagets kapital som finansieras med eget kapital.",
        formula: "Eget kapital / Totalt kapital",
        calculation: `${formatNumber(
          financialData.equity,
        )} / ${formatNumber(
          financialData.totalCapital,
        )} = ${formatNumber(
          financialData.equity /
            financialData.totalCapital,
        )}`,
      };

    case "likviditetsgrad":
      return {
        description:
          "Visar företagets förmåga att täcka sina kortfristiga skulder.",
        formula:
          "Omsättningstillgångar / Kortfristiga skulder",
        calculation: `${formatNumber(
          financialData.currentAssets,
        )} / ${formatNumber(
          financialData.currentLiabilities,
        )} = ${formatNumber(
          financialData.currentAssets /
            financialData.currentLiabilities,
        )}`,
      };

    case "skuldsättningsgrad":
      return {
        description:
          "Visar förhållandet mellan företagets skulder och eget kapital.",
        formula: "Totala skulder / Eget kapital",
        calculation: `${formatNumber(
          financialData.totalLiabilities,
        )} / ${formatNumber(
          financialData.equity,
        )} = ${formatNumber(
          financialData.totalLiabilities /
            financialData.equity,
        )}`,
      };

    default:
      return null;
  }
};

export const ScoringResultPanel = ({
  scoringResult,
  financialData,
}: ScoringResultPanelProps) => {
  if (!scoringResult) {
    return (
      <Panel
        title="Scoringresultat"
        className="status-scoring-panel"
      >
        <p className="text-muted">
          Ingen scoring tillgänglig.
        </p>
      </Panel>
    );
  }

  const scores =
    parseScoringResult(scoringResult);

  const flagged = scores.some(
    (score) => score.flagged,
  );

  return (
    <Panel
      title="Scoringresultat"
      className="status-scoring-panel"
    >
      <div className="status-scoring">
        <div className="status-scoring-summary">
          <span>Bedömning</span>

          <strong
            className={
              flagged
                ? "is-flagged"
                : "is-approved"
            }
          >
            {flagged
              ? "Kräver granskning"
              : "Godkänd"}
          </strong>
        </div>

        <dl className="status-scoring-list">
          {scores.map((score) => {
            const tooltip = financialData
              ? getMetricTooltip(
                  score.key,
                  financialData,
                )
              : null;

            return (
              <div
                className="status-scoring-row"
                key={score.key}
              >
                <dt>
                  {tooltip ? (
                    <MetricTooltip
                      label={score.label}
                      description={
                        tooltip.description
                      }
                      formula={tooltip.formula}
                      calculation={
                        tooltip.calculation
                      }
                    />
                  ) : (
                    score.label
                  )}
                </dt>

                <dd>
                  <span>{score.value}</span>

                  <span
                    className={`status-scoring-status ${
                      score.flagged
                        ? "is-flagged"
                        : "is-approved"
                    }`}
                  >
                    {score.flagged
                      ? "Granska"
                      : "OK"}
                  </span>
                </dd>
              </div>
            );
          })}
        </dl>
      </div>
    </Panel>
  );
};