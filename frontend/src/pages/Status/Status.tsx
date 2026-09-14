import { useParams } from "react-router-dom";

import { ApplicationDetailsPanel } from "./components/ApplicationDetailsPanel";
import { DocumentsPanel } from "./components/DocumentsPanel";
import { ScoringPanel } from "./components/ScoringPanel";
import { WorkerPanel } from "./components/WorkerPanel";
import { useApplicationDetails } from "./hooks/useApplicationDetails";

import {
  formatStatus,
  getStatusBadgeClass,
} from "./utils/statusFormatters";

import "./Status.css";

export const Status = () => {
  const { id } = useParams<{ id: string }>();

  const {
    application,
    workerName,
    documents,
    loading,
    error,
  } = useApplicationDetails(id);

  if (loading) {
    return (
      <main className="status-page">
        <p className="text-muted">Laddar ansökan...</p>
      </main>
    );
  }

  if (error || !application) {
    return (
      <main className="status-page">
        <div className="status-error">
          <strong>Kunde inte hämta ansökan</strong>
          <p>{error || "Ansökan hittades inte."}</p>
        </div>
      </main>
    );
  }

  return (
    <main className="status-page">
      <header className="status-header">
        <div>
          <div className="status-title-row">
            <h1>Ansökan #{application.id}</h1>

            <span
              className={`label ${getStatusBadgeClass(application.status)}`}
            >
              {formatStatus(application.status)}
            </span>
          </div>

          <p className="status-subtitle">
            {application.companyName}
          </p>
        </div>
      </header>

      <div className="status-layout">
        <aside className="status-side-column">
          <ScoringPanel
            scoringResult={application.scoringResult}
          />

          <WorkerPanel
            workerName={workerName}
            status={application.status}
          />
        </aside>

        <section className="status-main-column">
          <ApplicationDetailsPanel
            application={application}
          />

          <DocumentsPanel
            applicationId={application.id}
            documents={documents}
          />
        </section>
      </div>
    </main>
  );
};