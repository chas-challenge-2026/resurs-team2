import { useState, useRef } from "react";
import type { Application } from "../../../types/application";
import type { FinancialMetricsData } from "@/schemas/credit-application-schemas/FinancialMetrics.schema";
import { Panel } from "../../../components/Panel/Panel";
import { FinancialMetricsPanel } from "./financialMetricsPanel";
import {
  formatCurrency,
  formatDateTime,
  formatStatus,
  getStatusBadgeClass,
} from "../utils/backofficeFormatters";
import styles from "./CreditPanel.module.css";

interface CreditPanelProps {
  application: Application;
  financialMetrics: FinancialMetricsData;
}

export const CreditPanel: React.FC<CreditPanelProps> = ({ application, financialMetrics }) => {
  const [showFinancialMetrics, setShowFinancialMetrics] = useState(false);
  const financialMetricsRef = useRef<HTMLDivElement>(null);

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

        <button
          type="button"
          className={`btn btn-default ${styles.financialMetricsButton} ${
            showFinancialMetrics ? styles.financialMetricsButtonActive : ""
          }`}
          onClick={() => {
            if (!showFinancialMetrics) {
              setShowFinancialMetrics(true);

              setTimeout(() => {
                financialMetricsRef.current?.scrollIntoView({
                  behavior: "smooth",
                  block: "start",
                });
              }, 0);
            } else {
              setShowFinancialMetrics(false);

              window.scrollTo({
                top: 0,
                behavior: "smooth",
              });
            }
          }}
        >
          {showFinancialMetrics
            ? "Dölj finansiella uppgifter"
            : "Visa finansiella uppgifter"}
        </button>

        {showFinancialMetrics && (
          <div
            ref={financialMetricsRef}
            className={styles.financialMetricsContainer}
          >
            <FinancialMetricsPanel financialMetrics={financialMetrics} />
          </div>
        )}
    </Panel>
  );
};
