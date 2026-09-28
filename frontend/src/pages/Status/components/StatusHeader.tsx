import type { Application } from "@/types/application";

import { formatStatus } from "../utils/statusFormatters";

interface StatusHeaderProps {
  application: Application;
}

const getStatusClass = (status: Application["status"]) => {
  switch (status) {
    case "APPROVED":
      return "label-success";

    case "REJECTED":
      return "label-danger";

    case "UNDER_REVIEW":
    case "PENDING_DOCS":
      return "label-warning";

    default:
      return "label-default";
  }
};

export const StatusHeader = ({
  application,
}: StatusHeaderProps) => {
  return (
    <header className="status-page-header">
      <div className="status-title-row">
        <h1>Ansökan #{application.id}</h1>

        <span
          className={`label ${getStatusClass(application.status)}`}
          role="status"
        >
          {formatStatus(application.status)}
        </span>
      </div>

      <p className="status-subtitle">
        {application.companyName}
      </p>

      {application.decision && (
        <div
          className={`status-decision ${
            application.decision === "APPROVED"
              ? "status-decision-success"
              : "status-decision-danger"
          }`}
        >
          <strong>
            {application.decision === "APPROVED"
              ? "Ansökan godkänd"
              : "Ansökan avslagen"}
          </strong>

          <p>
            {application.decisionReason ||
              "Ingen motivering angiven."}
          </p>
        </div>
      )}
    </header>
  );
};