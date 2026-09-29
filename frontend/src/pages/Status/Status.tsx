import { useParams } from "react-router-dom";

import { Panel } from "@/components/Panel/Panel";

import { ApplicationDetailsPanel } from "./components/ApplicationDetailsPanel";
import { DocumentsPanel } from "./components/DocumentsPanel";
import { StatusHeader } from "./components/StatusHeader";
import { useApplicationDetails } from "./hooks/useApplicationDetails";
import { formatWorker } from "./utils/statusFormatters";
import { ScoringResultPanel } from "./components/ScoringResultPanel";

import "./Status.css";

export const Status = () => {
  const { id } = useParams<{ id: string }>();

  const {
    application,
    workerName,
    documents,
    financialData,
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
      <StatusHeader application={application} />

      <div className="status-layout">
        <aside className="status-side-column">
          <ScoringResultPanel
            scoringResult={application.scoringResult}
            financialData={financialData}
          />

          <Panel title="Handläggare">
            <p className="status-worker">
              {formatWorker(application.status, workerName)}
            </p>
          </Panel>
        </aside>

        <section className="status-main-column">
          <ApplicationDetailsPanel application={application} />

          <DocumentsPanel
            applicationId={application.id}
            documents={documents}
          />
        </section>
      </div>
    </main>
  );
};