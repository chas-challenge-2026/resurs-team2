import { Panel } from "@/components/Panel/Panel";

interface ScoringPanelProps {
  scoringResult?: string | null;
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

export const ScoringPanel = ({
  scoringResult,
}: ScoringPanelProps) => {
  if (!scoringResult) {
    return (
      <Panel title="Scoringresultat">
        <p className="text-muted">
          Ingen scoring tillgänglig.
        </p>
      </Panel>
    );
  }

  const scores =
    parseScoringResult(scoringResult);

  return (
    <Panel title="Scoringresultat">
      <dl className="status-scoring-list">
        {scores.map((score) => (
          <div
            className="status-scoring-row"
            key={score.key}
          >
            <dt>{score.label}</dt>

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
        ))}
      </dl>
    </Panel>
  );
};