import { useAuth } from "@/components/hooks/useAuth";

import "./Backoffice.css";

import { DecidedApplicationsTable } from "./components/DecidedApplicationsTable";
import { ReviewApplicationsTable } from "./components/ReviewApplicationsTable";
import { useBackofficeApplications } from "./hooks/useBackofficeApplications";

export const Backoffice = () => {
  const { user } = useAuth();

  const {
    reviewApps,
    decidedApps,
    loading,
    error,
  } = useBackofficeApplications();

  if (loading) {
    return (
      <main className="backoffice-page">
        <div className="backoffice-state">
          <h1>Handläggarkö</h1>
          <p>Laddar ansökningar...</p>
        </div>
      </main>
    );
  }

  if (error) {
    return (
      <main className="backoffice-page">
        <div className="backoffice-state">
          <h1>Handläggarkö</h1>

          <div className="backoffice-error">
            <strong>Kunde inte hämta ansökningar</strong>
            <p>{error}</p>
          </div>
        </div>
      </main>
    );
  }

  return (
    <main className="backoffice-page">
      <header className="backoffice-header">
        <div>
          <p className="backoffice-eyebrow">Backoffice</p>

          <div className="backoffice-title-row">
            <h1 className="backoffice-title">Handläggarkö</h1>

            <span
              className="backoffice-count"
              aria-label={`${reviewApps.length} ansökningar väntar på granskning`}
            >
              {reviewApps.length}
            </span>
          </div>

          <p className="backoffice-description">
            Granska inkomna kreditansökningar och följ tidigare beslut.
          </p>
        </div>

        <div className="backoffice-user">
          <span className="backoffice-user-label">Inloggad som</span>
          <strong>{user?.name || "Handläggare"}</strong>
        </div>
      </header>

      <div className="backoffice-content">
        <ReviewApplicationsTable applications={reviewApps} />

        <DecidedApplicationsTable applications={decidedApps} />
      </div>
    </main>
  );
};