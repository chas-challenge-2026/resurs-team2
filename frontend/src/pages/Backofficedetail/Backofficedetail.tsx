import React, { useState } from "react";
import "./Backofficedetail.css";
import "../../styles/components.css";
import type { Application } from "../../types/application";
import type { ApplicationDocument } from "../../types/document";

interface BackofficeProps {
  application: Application;
  documents: ApplicationDocument[];
  auditLogRaw?: string;
}

export const Backofficedetail: React.FC<BackofficeProps> = ({
  application,
  documents,
  auditLogRaw = "[]",
}) => {
  const [comment, setComment] = useState("");

  const handleDecision = (decision: "APPROVED" | "REJECTED") => {
    const actionText = decision === "APPROVED" ? "Godkänn" : "Avslå";
    if (window.confirm(`${actionText} ansökan?`)) {
      console.log(`Beslut: ${decision}, Kommentar: ${comment}`);
    }
  };

  const getScoringBadgeClass = (score?: string) => {
    if (!score) return "label-default";
    const uppercaseScore = score.toUpperCase();
    if (uppercaseScore.includes("GREEN")) return "label-success";
    if (uppercaseScore.includes("YELLOW")) return "label-warning";
    if (uppercaseScore.includes("RED")) return "label-danger";
    return "label-default";
  };

  return (
    <div className="backoffice-page">
      <h2>Ansökan #{application.id || "0"} – Detaljvy</h2>

      <div className="backoffice-layout">
        {/* VÄNSTERKOLUMN */}
        <div className="col-left">
          <div className="panel">
            <div className="panel-heading">Företagsuppgifter</div>
            <div className="panel-body">
              <p><strong>Företagsnamn:</strong> {application.companyName || "-"}</p>
              <p><strong>Org.nummer:</strong> {application.orgNumber || "-"}</p>
              <p><strong>Firmatecknare:</strong> {application.authorizedSignatory || "-"}</p>
            </div>
          </div>

          <div className="panel">
            <div className="panel-heading">Kreditdetaljer</div>
            <div className="panel-body">
              <p><strong>Belopp:</strong> {application.requestedAmount ? `${application.requestedAmount} kr` : "0 kr"}</p>
              <p><strong>Syfte:</strong> {application.purpose || "-"}</p>
              <p>
                <strong>Status:</strong>{" "}
                <span className="label label-warning">
                  {application.status || "STATUS"}
                </span>
              </p>
            </div>
          </div>

          <div className="panel">
            <div className="panel-heading">Scoringresultat</div>
            <div className="panel-body">
              <span className={`label ${getScoringBadgeClass(application.scoringResult)}`}>
                {application.scoringResult || "-"}
              </span>
              <hr />
              <p>{application.decisionReason || "Anledning saknas"}</p>
            </div>
          </div>
        </div>

        {/* HÖGERKOLUMN */}
        <div className="col-right">
          <div className="panel panel-warning">
            <div className="panel-heading">Fatta beslut</div>
            <div className="panel-body">
              <div className="form-group">
                <label htmlFor="decision-comment">Kommentar</label>
                <textarea
                  id="decision-comment"
                  className="form-control"
                  rows={3}
                  placeholder="Motivering till beslutet"
                  value={comment}
                  onChange={(e) => setComment(e.target.value)}
                />
              </div>
              <div className="button-group">
                <button
                  type="button"
                  className="btn btn-success"
                  onClick={() => handleDecision("APPROVED")}
                >
                  Godkänn
                </button>
                <button
                  type="button"
                  className="btn btn-danger"
                  onClick={() => handleDecision("REJECTED")}
                >
                  Avslå
                </button>
              </div>
            </div>
          </div>

          <div className="panel">
            <div className="panel-heading">Uppladdade dokument</div>
            <div className="panel-body">
              {!documents || documents.length === 0 ? (
                <div className="text-muted">Inga dokument.</div>
              ) : (
                <ul className="document-list">
                  {documents.map((doc) => (
                    <li key={doc.id}>
                      📄 <a href={`/document/${doc.id}`}>{doc.filename}</a>{" "}
                      <small className="label label-default">{doc.docType}</small>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>

          <div className="panel">
            <div className="panel-heading">Händelselogg (rådata)</div>
            <div className="panel-body">
              <pre className="audit-pre">{auditLogRaw}</pre>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};