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
      <div className="backoffice-page">
        <p>Laddar ansökan...</p>
      </div>
    );
  }

  if (error && !application) {
    return (
      <div className="backoffice-page">
        <p className="text-muted">{error}</p>

        <button
          type="button"
          className="btn btn-default"
          onClick={() => navigate("/backoffice")}
        >
          Tillbaka till handläggarkön
        </button>
      </div>
    );
  }

  if (!application) {
    return (
      <div className="backoffice-page">
        <p>Ansökan hittades inte.</p>
      </div>
    );
  }

  return (
    <div className="backoffice-page">
      <h2>Ansökan #{application.id} – Detaljvy</h2>

      {error && <div className="alert alert-danger">{error}</div>}

      <div className="backoffice-layout">
        <div className="col-left">
          <CompanyPanel
            application={application}
            workerName={workerName}
          />

          <CreditPanel application={application} />

          <ScoringPanel scoringResult={application.scoringResult} />
        </div>

        <div className="col-right">
          <DecisionPanel
            application={application}
            decisionLoading={decisionLoading}
            onDecision={handleDecision}
          />

          <DocumentsPanel documents={documents} />

          <AuditLogPanel auditLogs={auditLogs} />
        </div>
      </div>

      <div className="actions">
        <button
          type="button"
          className="btn btn-default"
          onClick={() => navigate("/backoffice")}
        >
          Tillbaka
        </button>
      </div>
    </div>
  );
};