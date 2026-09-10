import type { AuditLog } from "@/types/auditLog";
import { Panel } from "@/components/Panel/Panel";

interface AuditLogPanelProps {
  auditLogs: AuditLog[];
}

export const AuditLogPanel = ({
  auditLogs,
}: AuditLogPanelProps) => {
  return (
    <Panel title="Händelselogg">
      {auditLogs.length === 0 ? (
        <p className="text-muted">Ingen händelselogg finns ännu.</p>
      ) : (
        <pre className="audit-pre">
          {JSON.stringify(auditLogs, null, 2)}
        </pre>
      )}
    </Panel>
  );
};