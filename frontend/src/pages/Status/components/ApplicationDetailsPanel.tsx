import type { Application } from "@/types/application";

import { Panel } from "@/components/Panel/Panel";

interface ApplicationDetailsPanelProps {
  application: Application;
}

const formatCurrency = (value: number) =>
  new Intl.NumberFormat("sv-SE", {
    style: "currency",
    currency: "SEK",
    maximumFractionDigits: 0,
  }).format(value);

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

export const ApplicationDetailsPanel = ({
  application,
}: ApplicationDetailsPanelProps) => {
  return (
    <Panel title="Ansökningsdetaljer">
      <dl className="status-detail-list">
        <div className="status-detail-row">
          <dt>Företag</dt>
          <dd>{application.companyName}</dd>
        </div>

        <div className="status-detail-row">
          <dt>Org.nummer</dt>
          <dd>{application.orgNumber}</dd>
        </div>

        <div className="status-detail-row">
          <dt>Kreditbelopp</dt>
          <dd>{formatCurrency(application.requestedAmount)}</dd>
        </div>

        <div className="status-detail-row">
          <dt>Syfte</dt>
          <dd>{application.purpose || "-"}</dd>
        </div>

        <div className="status-detail-row">
          <dt>Inlämnad</dt>
          <dd>{formatDateTime(application.createdAt)}</dd>
        </div>

        <div className="status-detail-row">
          <dt>Senast uppdaterad</dt>
          <dd>{formatDateTime(application.updatedAt)}</dd>
        </div>
      </dl>
    </Panel>
  );
};