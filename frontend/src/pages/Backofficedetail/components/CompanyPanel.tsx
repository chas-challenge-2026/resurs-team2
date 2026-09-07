import React from "react";
import type { Application } from "../../../types/application";
import { Panel } from "../../../components/Panel/Panel";

interface CompanyPanelProps {
  application: Application;
  workerName: string;
}

export const CompanyPanel: React.FC<CompanyPanelProps> = ({
  application,
  workerName,
}) => {
  return (
    <Panel title="Företagsuppgifter">
      <p>
        <strong>Företagsnamn:</strong> {application.companyName}
      </p>

      <p>
        <strong>Org.nummer:</strong> {application.orgNumber}
      </p>

      <p>
        <strong>Handläggare:</strong> {workerName || "Ej tilldelad"}
      </p>
    </Panel>
  );
};
