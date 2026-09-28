import { Panel } from "@/components/Panel/Panel";

interface ScoringResultPanelProps {
  scoringResult: string | null;
}

interface ScoreItem {
  label: string;
  value: string;
  flagged: boolean;
}

const labels: Record<string, string> = {
  soliditet: "Soliditet",
  likviditetsgrad: "Likviditetsgrad",
  skuldsättningsgrad: "Skuldsättningsgrad",
};

const parseScoringResult = (scoringResult: string): ScoreItem[] => {
  const [, values = scoringResult] = scoringResult.split(":");

  return values.split(",").map((item) => {
    const match = item.trim().match(/^(.+?)=(.+?) \((OK|FLAGGED)\)$/);

    if (!match) {
      return {
        label: item.trim(),
        value: "",
        flagged: false,
      };
    }

    const [, key, value, status] = match;

    return {
      label: labels[key] ?? key,
      value: value.replace(".", ","),
      flagged: status === "FLAGGED",
    };
  });
};

export const ScoringResultPanel = ({
  scoringResult,
}: ScoringResultPanelProps) => {
  if (!scoringResult) {
    return (
      <Panel title="Scoringresultat">
        <p className="text-muted">Ingen scoring tillgänglig.</p>
      </Panel>
    );
  }

  const scores = parseScoringResult(scoringResult);
  const flagged = scores.some((score) => score.flagged);

  return (
    <Panel title="Scoringresultat">
      <div className="status-scoring">
        <div className="status-scoring-summary">
          <span>Bedömning</span>
          <strong className={flagged ? "is-flagged" : "is-approved"}>
            {flagged ? "Kräver granskning" : "Godkänd"}
          </strong>
        </div>

        <dl className="status-scoring-list">
          {scores.map((score) => (
            <div className="status-scoring-row" key={score.label}>
              <dt>{score.label}</dt>

              <dd>
                <span>{score.value}</span>
                <span
                  className={`status-scoring-status ${
                    score.flagged ? "is-flagged" : "is-approved"
                  }`}
                >
                  {score.flagged ? "Granska" : "OK"}
                </span>
              </dd>
            </div>
          ))}
        </dl>
      </div>
    </Panel>
  );
};