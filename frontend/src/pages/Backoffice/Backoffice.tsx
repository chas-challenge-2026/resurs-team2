import "./Backoffice.css";
import "@/styles/components.css";

import { useBackofficeApplications } from "./hooks/useBackofficeApplications";
import { ReviewApplicationsTable } from "./components/ReviewApplicationsTable";
import { DecidedApplicationsTable } from "./components/DecidedApplicationsTable";

import { useAuth } from "@/components/hooks/useAuth";

export const Backoffice = () => {

  const { user } = useAuth();
  const { reviewApps, decidedApps, loading, error } = useBackofficeApplications();


  if (loading) {
    return (
      <div className="backoffice-page">
        <h2>Handläggarkö</h2>

        <p className="text-muted">Laddar ansökningar...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="backoffice-page">
        <h2>Handläggarkö</h2>

        <div className="panel">
          <div className="panel-body">
            <p>{error}</p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="backoffice-page">
      <header className="backoffice-header">
        <h2 className="backoffice-title">
          Handläggarkö <span className="badge-count">{reviewApps.length}</span>
        </h2>

        <p className="text-muted">
          Inloggad handläggare: {user?.name || "Handläggare"}
        </p>
      </header>

      <ReviewApplicationsTable applications={reviewApps} />

      <DecidedApplicationsTable applications={decidedApps} />
    </div>
  );
};
