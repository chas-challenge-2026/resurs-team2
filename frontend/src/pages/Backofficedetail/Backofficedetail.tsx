import { useNavigate, useParams } from "react-router-dom";

import "@/styles/components.css";

import "./Backofficedetail.css";

import { AuditLogPanel } from "./components/AuditLogPanel";
import { CompanyPanel } from "./components/CompanyPanel";
import { CreditPanel } from "./components/CreditPanel";
import { DecisionPanel } from "./components/DecisionPanel";
import { DocumentsPanel } from "./components/DocumentsPanel";
import { ScoringPanel } from "./components/ScoringPanel";
import { useBackofficeApplication } from "./hooks/useBackofficeApplication";
import {
  formatStatus,
  getStatusBadgeClass,
} from "./utils/backofficeFormatters";

export const Backofficedetail = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const {
    application,
    documents,
    auditLogs,
    workerName,
    loading,
    decisionLoading,
    error,
    handleDecision,
  } = useBackofficeApplication(id);

  if (loading) {
    return (
      <main className="backoffice-detail-page">
        <p className="text-muted">Laddar ansökan...</p>
      </main>
    );
  }

  if (error && !application) {
    return (
      <main className="backoffice-detail-page">
        <div className="backoffice-detail-error">
          <strong>Kunde inte hämta ansökan</strong>
          <p>{error}</p>
        </div>

        <button
          type="button"
          className="btn btn-default"
          onClick={() => navigate("/backoffice")}
        >
          Tillbaka till handläggarkön
        </button>
      </main>
    );
  }

  if (!application) {
    return (
      <main className="backoffice-detail-page">
        <p className="text-muted">Ansökan hittades inte.</p>
      </main>
    );
  }

  return (
    <main className="backoffice-detail-page">
      <header className="backoffice-detail-header">
        <div>
          <button
            type="button"
            className="backoffice-back-link"
            onClick={() => navigate("/backoffice")}
          >
            ← Handläggarkö
          </button>

          <div className="backoffice-detail-title-row">
            <h1>Ansökan #{application.id}</h1>

            <span
              className={`label ${getStatusBadgeClass(application.status)}`}
            >
              {formatStatus(application.status)}
            </span>
          </div>

          <p className="backoffice-detail-subtitle">
            {application.companyName} · {application.orgNumber}
          </p>
        </div>
      </header>

      {error && (
        <div className="alert alert-danger">
          {error}
        </div>
      )}

      <div className="backoffice-detail-layout">
        <div className="backoffice-detail-column">
          <CompanyPanel
            application={application}
            workerName={workerName}
          />

          <CreditPanel application={application} />

          <ScoringPanel scoringResult={application.scoringResult} />
        </div>

        <div className="backoffice-detail-column">
          <DecisionPanel
            application={application}
            decisionLoading={decisionLoading}
            onDecision={handleDecision}
          />

          <DocumentsPanel documents={documents} />

          <AuditLogPanel auditLogs={auditLogs} />
        </div>
      </div>
    </main>
  );
};