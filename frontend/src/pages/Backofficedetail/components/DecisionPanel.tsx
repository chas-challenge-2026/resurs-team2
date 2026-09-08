import React, { useState } from "react";
import type { Application } from "../../../types/application";
import type { Decision } from "../../../api/applicationApi";
import { Panel } from "../../../components/Panel/Panel";

interface DecisionPanelProps {
  application: Application;
  decisionLoading: boolean;
  onDecision: (decision: Decision, comment: string) => Promise<void>;
}

export const DecisionPanel: React.FC<DecisionPanelProps> = ({
  application,
  decisionLoading,
  onDecision,
}) => {
  const [comment, setComment] = useState<string>("");

  const isDecided =
    application.status === "APPROVED" || application.status === "REJECTED";

  const handleDecision = async (decision: Decision) => {
    const actionText = decision === "APPROVED" ? "Godkänn" : "Avslå";

    const confirmed = window.confirm(
      `${actionText} ansökan #${application.id}?`,
    );

    if (!confirmed) {
      return;
    }

    await onDecision(decision, comment);

    setComment("");
  };

  return (
    <Panel
      title={isDecided ? "Beslut" : "Fatta beslut"}
      className="panel-warning"
    >
      {isDecided ? (
        <>
          <p>
            <strong>Beslut:</strong> {application.decision || "-"}
          </p>

          <p>
            <strong>Motivering:</strong> {application.decisionReason || "-"}
          </p>
        </>
      ) : (
        <>
          <div className="form-group">
            <label htmlFor="decision-comment">Kommentar</label>

            <textarea
              id="decision-comment"
              className="form-control"
              rows={4}
              maxLength={500}
              placeholder="Motivering till beslutet"
              value={comment}
              onChange={(event) => setComment(event.target.value)}
              disabled={decisionLoading}
            />

            <small className="text-muted">{comment.length}/500</small>
          </div>

          <div className="button-group">
            <button
              type="button"
              className="btn btn-success"
              disabled={decisionLoading}
              onClick={() => handleDecision("APPROVED")}
            >
              {decisionLoading ? "Sparar..." : "Godkänn"}
            </button>

            <button
              type="button"
              className="btn btn-danger"
              disabled={decisionLoading}
              onClick={() => handleDecision("REJECTED")}
            >
              {decisionLoading ? "Sparar..." : "Avslå"}
            </button>
          </div>
        </>
      )}
    </Panel>
  );
};
