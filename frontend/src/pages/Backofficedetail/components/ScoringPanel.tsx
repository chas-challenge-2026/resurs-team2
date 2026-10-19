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


const getFlaggedLabels = (scoringResult: string): string[] => {
  const [, values = scoringResult] = scoringResult.split(":");

  return values
    .split(",")
    .map((item) => item.trim())
    .flatMap((item) => {
      const match = item.match(
        /^(.+?)=(.+?)(?:\s+\((OK|FLAG|FLAGGED|SKIP)\))?$/i,
      );
      if (!match) return [];

      const [, key, raw, parenStatus] = match;
      const status = (parenStatus ?? raw).trim().toUpperCase();

      if (status !== "FLAG" && status !== "FLAGGED") return [];

      const normalizedKey = key.toLowerCase().trim();
      return [labels[normalizedKey] ?? key.trim()];
    });
};

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
  