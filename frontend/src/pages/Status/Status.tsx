import React, { useState } from "react";
import "./Status.css";
import type { Application } from "../../types/application";
import type { ApplicationStep } from "../../types/timeline";
import type { ApplicationDocument } from "../../types/document";

interface StatusProps {
  application: Application;
  steps: ApplicationStep[];
  documents: ApplicationDocument[];
  auditLogRaw?: string;
}

export const Status: React.FC<StatusProps> = ({
  application,
  steps,
  documents,
  auditLogRaw = "[]",
}) => {
  return (
    <div className="status-page">
      <p className="status-header">
        status:{" "}
        <span className={`badge badge-${application.status}`} role="status">
          {application.status}
        </span>
      </p>
      {application.decision && (
        <div className={`alert ${application.decision === "APPROVED" ? "alert-success" : "alert-danger"}`}>
          <h4>
            {application.decision === "APPROVED"
              ? "Ansökan godkänd"
              : "Ansökan avslagen"}
          </h4>
          <p>{application.decisionReason}</p>
        </div>
      )}
      {application.scoringResult && (
      <div className="panel">
            <div className="panel-heading">Scoringresultat</div>
            <div className="panel-body">
                <code>{application.scoringResult}</code>
            </div>
      </div>
      )}
      <div className="panel">
        <div className="panel-heading">Ansökningsdetaljer</div>
        <div className="panel-body">
            
        </div>
      </div>
    </div>
  );
};
