import { useNavigate, useParams } from "react-router-dom";

import { Panel } from "@/components/Panel/Panel";

import "./Status.css";

import { ApplicationDetailsPanel } from "./components/ApplicationDetailsPanel";
import { DocumentsPanel } from "./components/DocumentsPanel";
import { StatusHeader } from "./components/StatusHeader";
import { useApplicationDetails } from "./hooks/useApplicationDetails";

export const Status = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const {
    application,
    documents,
    workerName,
    loading,
    error,
  } = useApplicationDetails(id);

  if (loading) {
    return (
      <div className="status-page">
        <p>Laddar ansökan...</p>
      </div>
    );
  }

  if (error || !application) {
    return (
      <div className="status-page">
        <p className="text-muted">
          {error ?? "Ingen ansökan hittades."}
        </p>

        <button
          type="button"
          className="btn btn-default"
          onClick={() => navigate("/application")}
        >
          Tillbaka till ansökningar
        </button>
      </div>
    );
  }

  return (
    <div className="status-page">
      <StatusHeader application={application} />

      <div className="status-layout">
        <div className="left-column">
          {application.scoringResult && (
            <Panel title="Scoringresultat">
              {application.scoringResult}
            </Panel>
          )}

          <Panel title="Handläggare">
            {workerName || "Ej tilldelad"}
          </Panel>
        </div>

        <div className="right-column">
          <ApplicationDetailsPanel application={application} />

          <DocumentsPanel
            applicationId={application.id}
            documents={documents}
          />
        </div>
      </div>

      <div className="actions">
        <button
          type="button"
          className="btn btn-default"
          onClick={() => navigate("/application")}
        >
          Tillbaka
        </button>

        <button
          type="button"
          className="btn btn-primary"
          onClick={() => navigate("/apply")}
        >
          Redigera ansökan
        </button>
      </div>
    </div>
  );
};