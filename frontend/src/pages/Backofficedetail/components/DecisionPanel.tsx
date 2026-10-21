import { useEffect, useRef, useState } from "react";
import type { Application } from "../../../types/application";
import type { Decision } from "../../../api/applicationApi";
import { Panel } from "../../../components/Panel/Panel";

interface DecisionPanelProps {
  application: Application;
  decisionLoading: boolean;
  onDecision: (decision: Decision, comment: string) => Promise<void>;
}

const decisionConfig: Record<
  Decision,
  {
    actionText: string;
    question: string;
    buttonClass: string;
    commentRequired: boolean;
  }
> = {
  APPROVED: {
    actionText: "Godkänn",
    question: "Vill du godkänna ansökan?",
    buttonClass: "btn btn-success",
    commentRequired: false,
  },
  DOCUMENTS_NEEDED: {
    actionText: "Kräver dokument",
    question: "Vill du begära kompletterande dokument?",
    buttonClass: "btn btn-warning",
    commentRequired: true,
  },
  REJECTED: {
    actionText: "Avslå",
    question: "Vill du avslå ansökan?",
    buttonClass: "btn btn-danger",
    commentRequired: true,
  },
};

export const DecisionPanel: React.FC<DecisionPanelProps> = ({
  application,
  decisionLoading,
  onDecision,
}) => {
  const [comment, setComment] = useState<string>("");
  // Vilket beslut som väntar på bekräftelse i popupen (null = popupen är stängd)
  const [pendingDecision, setPendingDecision] = useState<Decision | null>(null);
  const [error, setError] = useState<string | null>(null);

  const dialogRef = useRef<HTMLDialogElement>(null);

  const isDecided =
    application.status === "APPROVED" || application.status === "REJECTED";

  // Öppna/stäng den inbyggda <dialog> när pendingDecision ändras.
  // showModal() ger mörk bakgrund, fokusfälla och Esc-stängning utan extra kod.
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;

    if (pendingDecision && !dialog.open) {
      dialog.showModal();
    } else if (!pendingDecision && dialog.open) {
      dialog.close();
    }
  }, [pendingDecision]);

  const openConfirm = (decision: Decision) => {
    setError(null);
    setPendingDecision(decision);
  };

  const closeConfirm = () => {
    if (decisionLoading) return; // stäng inte mitt i ett pågående anrop
    setPendingDecision(null);
    setError(null);
  };

  const handleConfirm = async () => {
    if (!pendingDecision) return;

    const config = decisionConfig[pendingDecision];

    if (config.commentRequired && !comment.trim()) {
      setError("Skriv en motivering innan du fattar beslutet.");
      return;
    }

    try {
      await onDecision(pendingDecision, comment);
      setComment("");
      setPendingDecision(null);
      setError(null);
    } catch {
      setError("Kunde inte spara beslutet. Försök igen.");
    }
  };

  const pending = pendingDecision ? decisionConfig[pendingDecision] : null;

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
              onClick={() => openConfirm("APPROVED")}
            >
              Godkänn
            </button>

            <button
              type="button"
              // TODO: Change to a warning button once we have a warning button style
              className="btn btn-warning"
              disabled={decisionLoading}
              onClick={() => openConfirm("DOCUMENTS_NEEDED")}
            >
              Kräver dokument
            </button>

            <button
              type="button"
              className="btn btn-danger"
              disabled={decisionLoading}
              onClick={() => openConfirm("REJECTED")}
            >
              Avslå
            </button>
          </div>

          <dialog
            ref={dialogRef}
            className="decision-dialog"
            onClose={closeConfirm}
            onCancel={(event) => {
              // Esc ska inte stänga under pågående sparning
              if (decisionLoading) event.preventDefault();
            }}
            aria-labelledby="decision-dialog-title"
          >
            {pending && (
              <>
                <h3 id="decision-dialog-title" className="decision-dialog-title">
                  {pending.actionText} ansökan #{application.id}
                </h3>

                <p>{pending.question}</p>

                {comment.trim() ? (
                  <p>
                    <strong>Motivering:</strong>
                    <br />
                    <span className="text-muted decision-dialog-comment">
                      {comment}
                    </span>
                  </p>
                ) : (
                  <p className="text-muted">
                    {pending.commentRequired
                      ? "Motivering saknas. Den krävs för det här beslutet."
                      : "Ingen motivering angiven."}
                  </p>
                )}

                {error && (
                  <p role="alert" className="decision-dialog-error">
                    {error}
                  </p>
                )}

                <div className="button-group decision-dialog-actions">
                  <button
                    type="button"
                    className="btn btn-default"
                    onClick={closeConfirm}
                    disabled={decisionLoading}
                  >
                    Avbryt
                  </button>

                  <button
                    type="button"
                    className={pending.buttonClass}
                    onClick={handleConfirm}
                    disabled={decisionLoading}
                  >
                    {decisionLoading ? "Sparar..." : pending.actionText}
                  </button>
                </div>
              </>
            )}
          </dialog>
        </>
      )}
    </Panel>
  );
};
