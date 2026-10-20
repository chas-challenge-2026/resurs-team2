import { useState } from "react";
import type { Application } from "../../../types/application";
import type { Decision } from "../../../api/applicationApi";
import { Panel } from "../../../components/Panel/Panel";
import { parseRejectionReason } from "@/utils/rejectionReasonFormatter";

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
    let actionText: string;
    switch (decision) {
      case "APPROVED":
        actionText = "Godkänn";
        break;
      case "REJECTED":
        actionText = "Avslå";
        break;
      case "DOCUMENTS_NEEDED":
        actionText = "Kräver dokument";
        break;
    }

    const confirmed = window.confirm(
      `${actionText} ansökan #${application.id}?`,
    );

    if (!confirmed) {
      return;
    }

    await onDecision(decision, comment);

    setComment("");
  };

  const reasons = parseRejectionReason(application.decisionReason ?? "");

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

          <div className="decision-reason">
            <strong>Motivering:</strong>

            {reasons.length > 0 ? (
              <ul className="decision-reason-list">
                {reasons.map((reason, index) => (
                  <li
                    key={`${reason.type}-${index}`}
                    className={`decision-reason-item decision-reason-item-${reason.type.toLowerCase()}`}
                  >
                    <span className="decision-reason-label">
                      {reason.type === "REJECT" ? "Avslag:" : "Varning:"}
                    </span>

                    <span className="decision-reason-text">{reason.text}</span>
                  </li>
                ))}
              </ul>
            ) : (
              <span>-</span>
            )}
          </div>
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
              // TODO: Change to a warning button once we have a warning button style
              className="btn btn-warning"
              disabled={decisionLoading}
              onClick={() => handleDecision("DOCUMENTS_NEEDED")}
            >
              {decisionLoading ? "Sparar..." : "Kräver dokument"}
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
