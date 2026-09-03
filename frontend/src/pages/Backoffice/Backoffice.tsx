import React, { useState } from "react";
import "./Backoffice.css";
import "../../styles/components.css";
import type { Application } from "../../types/application";

interface BackofficeProps {
  workerName?: string;
  initialReviewApplications?: Application[];
  initialDecidedApplications?: Application[];
}

export const Backoffice: React.FC<BackofficeProps> = ({
  workerName = "Handläggare",
  initialReviewApplications = [],
  initialDecidedApplications = [],
}) => {
  const [reviewApps, setReviewApps] = useState<Application[]>(
    initialReviewApplications
  );
  const [decidedApps, setDecidedApps] = useState<Application[]>(
    initialDecidedApplications
  );
  const [comments, setComments] = useState<{ [key: string | number]: string }>({});

  const handleCommentChange = (id: string | number, value: string) => {
    setComments((prev) => ({ ...prev, [id]: value }));
  };

  const handleDecision = (
    id: string | number,
    decision: "APPROVED" | "REJECTED"
  ) => {
    const comment =
      comments[id] ||
      (decision === "REJECTED" ? "Manuellt avslagen av handläggare." : "");
    const actionText = decision === "APPROVED" ? "Godkänn" : "Avslå";

    if (window.confirm(`${actionText} ansökan #${id}?`)) {
      console.log(`Beslut för #${id}: ${decision}, Kommentar: ${comment}`);

      const appToMove = reviewApps.find((app) => app.id === id);
      if (appToMove) {
        const updatedApp: Application = {
          ...appToMove,
          status: decision,
          decision: decision,
          decisionReason:
            comment || (decision === "APPROVED" ? "Godkänd" : "Avslagen"),
        };

        setReviewApps((prev) => prev.filter((app) => app.id !== id));
        setDecidedApps((prev) => [updatedApp, ...prev.slice(0, 19)]);
      }
    }
  };

  const formatCurrency = (amount: number) => {
    return new Intl.NumberFormat("sv-SE").format(amount) + " kr";
  };

  return (
    <div className="backoffice-page">
      <header className="backoffice-header">
        <h2 className="backoffice-title">
          Handläggarkö
          <span className="badge-count">{reviewApps.length}</span>
        </h2>
        <p className="text-muted">Inloggad handläggare: {workerName}</p>
      </header>

      <div className="panel panel-warning">
        <div className="panel-heading">
          <strong>Ansökningar för granskning (UNDER_REVIEW)</strong>
        </div>
        {reviewApps.length === 0 ? (
          <div className="panel-body">
            <p className="text-muted">Inga ansökningar väntar på granskning.</p>
          </div>
        ) : (
          <table className="table table-bordered table-review">
            <thead>
              <tr>
                <th>#</th>
                <th>Företag</th>
                <th>Org.nr</th>
                <th>Belopp</th>
                <th>Syfte</th>
                <th>Scoring</th>
                <th>Inlämnad</th>
                <th>Beslut</th>
              </tr>
            </thead>
            <tbody>
              {reviewApps.map((app) => (
                <tr key={app.id}>
                  <td>{app.id}</td>
                  <td>{app.companyName}</td>
                  <td>{app.orgNumber}</td>
                  <td>{formatCurrency(app.requestedAmount)}</td>
                  <td>{app.purpose}</td>
                  <td>
                    <small>{app.scoringResult || "-"}</small>
                  </td>
                  <td>{app.createdAt}</td>
                  <td>
                    <div className="inline-form">
                      <input
                        type="text"
                        placeholder="Kommentar (valfri)"
                        className="input-comment"
                        value={comments[app.id] || ""}
                        onChange={(e) =>
                          handleCommentChange(app.id, e.target.value)
                        }
                      />
                      <button
                        type="button"
                        className="btn-sm btn-success-sm"
                        onClick={() => handleDecision(app.id, "APPROVED")}
                      >
                        Godkänn
                      </button>
                      <button
                        type="button"
                        className="btn-sm btn-danger-sm"
                        onClick={() => handleDecision(app.id, "REJECTED")}
                      >
                        Avslå
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="panel">
        <div className="panel-heading">
          <strong>Senaste beslut (max 20)</strong>
        </div>
        {decidedApps.length === 0 ? (
          <div className="panel-body">
            <p className="text-muted">Inga avgjorda ansökningar ännu.</p>
          </div>
        ) : (
          <table className="table table-striped">
            <thead>
              <tr>
                <th>#</th>
                <th>Företag</th>
                <th>Belopp</th>
                <th>Status</th>
                <th>Beslut</th>
                <th>Motivering</th>
              </tr>
            </thead>
            <tbody>
              {decidedApps.map((app) => (
                <tr key={app.id}>
                  <td>{app.id}</td>
                  <td>{app.companyName}</td>
                  <td>{formatCurrency(app.requestedAmount)}</td>
                  <td>
                    <span
                      className={`label ${
                        app.status === "APPROVED"
                          ? "label-success"
                          : "label-danger"
                      }`}
                    >
                      {app.status}
                    </span>
                  </td>
                  <td>{app.decision || "-"}</td>
                  <td>{app.decisionReason || "-"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
};