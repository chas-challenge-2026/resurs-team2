import { Panel } from "@/components/Panel/Panel";
import type { Application } from "@/types/application";

import {
  formatCurrency,
  formatDateTime,
} from "../utils/statusFormatters";

interface ApplicationDetailsPanelProps {
  application: Application;
}

export const ApplicationDetailsPanel = ({
  application,
}: ApplicationDetailsPanelProps) => {
  return (
    <Panel title="Ansökningsdetaljer">
      <p>
        <strong>Företag:</strong>
        <br />
        {application.companyName}
      </p>

      <p>
        <strong>Org.nummer:</strong>
        <br />
        {application.orgNumber}
      </p>

      <p>
        <strong>Kreditbelopp:</strong>
        <br />
        {formatCurrency(application.requestedAmount)}
      </p>

      <p>
        <strong>Syfte:</strong>
        <br />
        {application.purpose}
      </p>

      <p>
        <strong>Inlämnad:</strong>
        <br />
        {formatDateTime(application.createdAt)}
      </p>

      <p>
        <strong>Senast uppdaterad:</strong>
        <br />
        {formatDateTime(application.updatedAt)}
      </p>
    </Panel>
  );
};