import React from "react";
import { Panel } from "../../../components/Panel/Panel";
import { getScoringBadgeClass } from "../utils/backofficeFormatters";

interface ScoringPanelProps {
  scoringResult?: string | null;
}

export const ScoringPanel: React.FC<ScoringPanelProps> = ({
  scoringResult,
}) => {
  return (
    <Panel title="Scoringresultat">
      <span className={`label ${getScoringBadgeClass(scoringResult)}`}>
        {scoringResult || "-"}
      </span>
    </Panel>
  );
};
