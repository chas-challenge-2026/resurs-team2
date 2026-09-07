import React from "react";
import type { Application } from "../../../types/application";
import { Panel } from "../../../components/Panel/Panel";
import {
  formatCurrency,
  formatDateTime,
  formatStatus,
  getStatusBadgeClass,
} from "../utils/backofficeFormatters";

interface CreditPanelProps {
  application: Application;
}

export const CreditPanel: React.FC<CreditPanelProps> = ({ application }) => {
  return (
    <Panel title="Kreditdetaljer">
      <p>
        <strong>Belopp:</strong> {formatCurrency(application.requestedAmount)}
      </p>

      <p>
        <strong>Syfte:</strong> {application.purpose}
      </p>

      <p>
        <strong>Status:</strong>{" "}
        <span className={`label ${getStatusBadgeClass(application.status)}`}>
          {formatStatus(application.status)}
        </span>
      </p>

      <p>
        <strong>Inlämnad:</strong> {formatDateTime(application.createdAt)}
      </p>

      <p>
        <strong>Senast uppdaterad:</strong>{" "}
        {formatDateTime(application.updatedAt)}
      </p>
    </Panel>
  );
};
