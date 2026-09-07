import React from "react";
import type { Application } from "../../../types/application";
import { formatStatus } from "../utils/statusFormatters";

interface StatusHeaderProps {
  application: Application;
}

export const StatusHeader: React.FC<
  StatusHeaderProps
> = ({ application }) => {
  return (
    <>
      <h2>
        Ansökan #{application.id}
      </h2>

      <p className="status-header">
        Status:{" "}
        <span
          className={`badge badge-${application.status}`}
          role="status"
        >
          {formatStatus(application.status)}
        </span>
      </p>

      {application.decision && (
        <div
          className={`alert ${
            application.decision ===
            "APPROVED"
              ? "alert-success"
              : "alert-danger"
          }`}
        >
          <h4>
            {application.decision ===
            "APPROVED"
              ? "Ansökan godkänd"
              : "Ansökan avslagen"}
          </h4>

          <p>
            {application.decisionReason ||
              "Ingen motivering angiven."}
          </p>
        </div>
      )}
    </>
  );
};