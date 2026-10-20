import { Panel } from "@/components/Panel/Panel";

interface ScoringPanelProps {
  scoringResult?: string | null;
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

const FLAGGED = new Set(["FLAG", "FLAGGED"]);

const getFlaggedLabels = (ScoringResult: string): string[] =>
  ScoringResult
  .split(",")
  .map((item) => item.split("="))
  .filter(([, status =""]) => FLAGGED.has(status.trim().toUpperCase()))
  .map(([key]) => labels[key.trim().toLowerCase()] ?? key.trim());

export const ScoringPanel = ({ scoringResult }: ScoringPanelProps) => {
  if (!scoringResult) {
    return (
      <Panel title="Scoringresultat">
        <p className="text-muted">Ingen scoring tillgänglig.</p>
      </Panel>
    );
  }

  const flagged = getFlaggedLabels(scoringResult);

  if (flagged.length === 0) {
    return (
      <Panel title="Scoringresultat">
        <p className="text-muted">Inga varningsflaggor. Alla nyckeltal är godkända.</p>
      </Panel>
    );
  }

  return (
    <Panel title={`Behöver granskning`}>
      <dl className="status-scoring-list">
        {flagged.map((label) => (
          <div className="status-scoring-row" key={label}>
            <dt>{label}</dt>
            <dd>
              <span className="status-scoring-status is-flagged">Granska</span>
            </dd>
          </div>
        ))}
      </dl>

      
    </Panel>
  );
};
