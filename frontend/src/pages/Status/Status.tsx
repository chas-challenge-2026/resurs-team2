import React from "react";
import "./Status.css";
import type { Application } from "../../types/application";
import type { ApplicationStep } from "../../types/timeline";
import type { ApplicationDocument } from "../../types/document";

interface StatusProps {
  application?: Application;
  steps?: ApplicationStep[];
  documents?: ApplicationDocument[];
  auditLogRaw?: string;
}

export const Status: React.FC<StatusProps> = ({
  application,
  steps = [],
  documents = [],
  auditLogRaw = "[]",
}) => {
  if (!application) {
    return (
      <div className="status-page">
        <p className="text-muted">Ingen ansökan hittades.</p>
      </div>
    );
  }

  const formatCurrency = (amount?: number) => {
    if (!amount) return "0 kr";
    return new Intl.NumberFormat("sv-SE").format(amount) + " kr";
  };

  return (
    <div className="status-page">
      <h2>Ansökan #{application.id || "000"}</h2>

      <p className="status-header">
        Status:{" "}
        <span className={`badge badge-${application.status}`} role="status">
          {application.status}
        </span>
      </p>

      {application.decision && (
        <div
          className={`alert ${
            application.decision === "APPROVED"
              ? "alert-success"
              : "alert-danger"
          }`}
        >
          <h4>
            {application.decision === "APPROVED"
              ? "Ansökan godkänd"
              : "Ansökan avslagen"}
          </h4>
          <p>{application.decisionReason}</p>
        </div>
      )}

      <div className="status-layout">
        <div className="left-column">
          {application.scoringResult && (
            <div className="panel">
              <div className="panel-heading">Scoringresultat</div>
              <div className="panel-body">{application.scoringResult}</div>
            </div>
          )}

          <h3>Statusflöde</h3>
          <div className="timeline">
            {steps.map((step, index) => (
              <div key={index} className="timeline-item">
                <div className={`dot dot-${step.status}`} />
                <div className="content">
                  <strong>{step.name}</strong>{" "}
                  {step.status === "DONE" && (
                    <span className="label label-success">Klart</span>
                  )}
                  {step.status === "CURRENT" && (
                    <span className="label label-warning">Pågår</span>
                  )}
                  {step.status === "PENDING" && (
                    <span className="label label-default">Väntar</span>
                  )}
                  <br />
                  <small>{step.description}</small>
                  {step.eta && step.eta !== "—" && (
                    <>
                      <br />
                      <small className="text-muted">
                        Beräknad handläggningstid: <strong>{step.eta}</strong>
                      </small>
                    </>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="right-column">
          <div className="panel">
            <div className="panel-heading">Ansökningsdetaljer</div>
            <div className="panel-body">
              <p>
                <strong>Företag:</strong>
                <br />
                {application.companyName || "-"}
              </p>
              <p>
                <strong>Org.nummer:</strong>
                <br />
                {application.orgNumber || "-"}
              </p>
              <p>
                <strong>Kreditbelopp:</strong>
                <br />
                {formatCurrency(application.requestedAmount)}
              </p>
              <p>
                <strong>Syfte:</strong>
                <br />
                {application.purpose || "-"}
              </p>
              <p>
                <strong>Inlämnad:</strong>
                <br />
                {application.createdAt || "-"}
              </p>
            </div>
          </div>

          <div className="panel">
            <div className="panel-heading">
              Dokument
              <button className="btn btn-sm btn-primary pull-right">
                Ladda upp
              </button>
            </div>
            <div className="panel-body">
              {documents.length === 0 ? (
                <p className="text-muted">Inga dokument uppladdade ännu.</p>
              ) : (
                <ul className="document-list">
                  {documents.map((doc) => (
                    <li key={doc.id}>
                      📄 {doc.filename} ({doc.docType})
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>

          <div className="panel">
            <div className="panel-heading">Händelselogg</div>
            <div className="panel-body">
              <pre>{auditLogRaw}</pre>
            </div>
          </div>
        </div>
      </div>

      <div className="actions">
        <button className="btn btn-default">Tillbaka</button>
        <button className="btn btn-primary">Redigera ansökan</button>
      </div>
    </div>
  );
};