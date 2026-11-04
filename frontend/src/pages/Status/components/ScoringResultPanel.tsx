import { MetricTooltip } from "@/components/metricTooltips/MetricTooltips";
import { Panel } from "@/components/Panel/Panel";
import type { FinancialData } from "@/types/applicationDetails";
import {
  getScoringItemsWithRequirements,
  getMetricTooltip,
} from "../utils/scoringUtils";

interface ScoringResultPanelProps {
  scoringResult: string | null;
  financialData: FinancialData | string | null;
}

const toFinancialData = (
  value: FinancialData | string | null
): FinancialData | null => {
  if (!value) return null;
  if (typeof value === "string") {
    try {
      return JSON.parse(value) as FinancialData;
    } catch {
      return null;
    }
  }
  return value;
};

export const ScoringResultPanel = ({
  scoringResult,
  financialData,
}: ScoringResultPanelProps) => {
  if (!scoringResult) {
    return (
      <Panel title="Scoringresultat" className="status-scoring-panel">
        <p className="text-muted">Ingen scoring tillgänglig.</p>
      </Panel>
    );
  }

  const parsedFinancialData = toFinancialData(financialData);
  const scores = getScoringItemsWithRequirements(
    scoringResult,
    parsedFinancialData
  );
  const flagged = scores.length > 0;

  return (
    <Panel title="Scoringresultat" className="status-scoring-panel">
      <div className="status-scoring">
        <div className="status-scoring-summary">
          <span>Bedömning</span>
          <strong className={flagged ? "is-flagged" : "is-approved"}>
            {flagged ? "Kräver granskning" : "Godkänd"}
          </strong>
        </div>

        {scores.length === 0 ? (
          <p className="text-muted" style={{ marginTop: "1rem" }}>
            Alla nyckeltal uppfyller kraven utan anmärkning.
          </p>
        ) : (
          <dl className="status-scoring-list">
            {scores.map((score) => {
              const tooltip = parsedFinancialData
                ? getMetricTooltip(score.key, parsedFinancialData)
                : null;

              return (
                <div className="status-scoring-row" key={score.key}>
                  <dt>
                    {tooltip ? (
                      <MetricTooltip
                        label={score.label}
                        description={tooltip.description}
                        formula={tooltip.formula}
                        calculation={tooltip.calculation}
                      />
                    ) : (
                      score.label
                    )}
                  </dt>

                  <dd>
                    <div className="status-scoring-value-wrapper">
                      {score.requirementText && (
                        <span
                          className="status-requirement-text text-muted"
                          style={{
                            fontSize: "0.85rem",
                            display: "block",
                            marginBottom: "0.25rem",
                          }}
                        >
                          {score.requirementText}
                        </span>
                      )}
                    </div>
                  </dd>
                </div>
              );
            })}
          </dl>
        )}

        {flagged && (
          <div
            className="status-review-message"
            style={{
              marginTop: "1.25rem",
              padding: "0.75rem",
              backgroundColor: "rgba(255, 193, 7, 0.1)",
              borderRadius: "6px",
              borderLeft: "3px solid #ffc107",
            }}
          >
            <p style={{ fontSize: "0.9rem", margin: 0, color: "inherit" }}>
              Din ansökan har vidarebefordrats till en handläggare för en
              manuell granskning.
            </p>
          </div>
        )}
      </div>
    </Panel>
  );
};
