import type { AuditLog } from "@/types/auditLog";

import { Panel } from "@/components/Panel/Panel";

import "./AuditLogPanel.css";

interface AuditLogPanelProps {
  auditLogs: AuditLog[];
}

const formatDateTime = (value: string) => {
  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("sv-SE", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
};

const formatAction = (action: unknown) => {
  switch (action) {
    case "APPLICATION_CREATED":
      return "Ansökan skapades";

    case "SCORING_RUN":
      return "Automatisk scoring genomfördes";

    case "MANUAL_DECISION":
      return "Manuellt beslut registrerades";

    case "APPLICATION_APPROVED":
      return "Ansökan godkändes";

    case "APPLICATION_REJECTED":
      return "Ansökan avslogs";

    default:
      if (typeof action !== "string") {
        return "Händelse registrerad";
      }

      return action
        .toLowerCase()
        .replaceAll("_", " ")
        .replace(/^./, (char) => char.toUpperCase());
  }
};

const formatKey = (key: string) => {
  const labels: Record<string, string> = {
    orgNumber: "Organisationsnummer",
    companyName: "Företag",
    workerName: "Handläggare",
    requestedAmount: "Belopp",
    scoringResult: "Scoringresultat",
    result: "Resultat",
    flags: "Flaggor",
    decision: "Beslut",
    decisionReason: "Motivering",
    reason: "Motivering",
    status: "Status",
    previousStatus: "Tidigare status",
    newStatus: "Ny status",
  };

  return labels[key] ?? key;
};

const formatValue = (key: string, value: unknown) => {
  if (typeof value === "boolean") {
    return value ? "Ja" : "Nej";
  }

  if (
    key === "requestedAmount" &&
    typeof value === "number"
  ) {
    return new Intl.NumberFormat("sv-SE", {
      style: "currency",
      currency: "SEK",
      maximumFractionDigits: 0,
    }).format(value);
  }

  if (Array.isArray(value)) {
    return value.join(", ");
  }

  if (typeof value === "object" && value !== null) {
    return JSON.stringify(value);
  }

  return String(value);
};

const getEntryDetails = (
  entry: Record<string, unknown>,
) =>
  Object.entries(entry).filter(
    ([key, value]) =>
      key !== "action" &&
      value !== null &&
      value !== undefined &&
      value !== "",
  );

export const AuditLogPanel = ({
  auditLogs,
}: AuditLogPanelProps) => {
  return (
    <Panel title="Händelselogg">
      {auditLogs.length === 0 ? (
        <p className="text-muted">
          Ingen händelselogg finns ännu.
        </p>
      ) : (
        <div className="audit-timeline">
          {auditLogs.map((log) => {
            const details = getEntryDetails(
              log.entry,
            );

            return (
              <article
                key={log.sequenceNumber}
                className="audit-event"
              >
                <div
                  className="audit-event-marker"
                  aria-hidden="true"
                />

                <div className="audit-event-content">
                  <div className="audit-event-header">
                    <strong className="audit-event-title">
                      {formatAction(
                        log.entry.action,
                      )}
                    </strong>

                    <time
                      className="audit-event-time"
                      dateTime={log.timestamp}
                      title={log.timestamp}
                    >
                      {formatDateTime(
                        log.timestamp,
                      )}
                    </time>
                  </div>

                  {details.length > 0 && (
                    <dl className="audit-event-details">
                      {details.map(
                        ([key, value]) => (
                          <div
                            key={key}
                            className="audit-event-detail"
                          >
                            <dt>
                              {formatKey(key)}
                            </dt>

                            <dd>
                              {formatValue(
                                key,
                                value,
                              )}
                            </dd>
                          </div>
                        ),
                      )}
                    </dl>
                  )}
                </div>
              </article>
            );
          })}
        </div>
      )}
    </Panel>
  );
};