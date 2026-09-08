import React from "react";
import { Panel } from "../../../components/Panel/Panel";

interface AuditLogPanelProps {
  auditLogRaw: string;
}

export const AuditLogPanel: React.FC<AuditLogPanelProps> = ({
  auditLogRaw,
}) => {
  return (
    <Panel title="Händelselogg">
      <pre className="audit-pre">{auditLogRaw}</pre>
    </Panel>
  );
};
