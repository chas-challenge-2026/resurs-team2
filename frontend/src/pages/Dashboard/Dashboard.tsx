import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import type { ApplicationSummary } from "../../types/applicationSummary";
import { applicationApi } from "../../api/applicationApi";
import { useAuth } from "../../components/hooks/useAuth";

import styles from "./dashboard.module.css";

export function Dashboard() {
  const navigate = useNavigate();
  const { user } = useAuth();

  const [applications, setApplications] = useState<ApplicationSummary[]>([]);

  const [loading, setLoading] = useState<boolean>(true);

  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const loadApplications = async () => {
      try {
        setLoading(true);
        setError(null);

        const data = await applicationApi.getAll();

        setApplications(data);
      } catch (err: unknown) {
        if (err instanceof Error) {
          setError(err.message);
        } else {
          setError("Kunde inte hämta ansökningarna.");
        }
      } finally {
        setLoading(false);
      }
    };

    loadApplications();
  }, []);

  const getStatusClass = (status: ApplicationSummary["status"]) => {
    switch (status) {
      case "APPROVED":
        return styles.labelSuccess;

      case "REJECTED":
        return styles.labelDanger;

      case "UNDER_REVIEW":
        return styles.labelWarning;

      default:
        return styles.labelDefault;
    }
  };

  const formatStatus = (status: ApplicationSummary["status"]) => {
    switch (status) {
      case "PENDING_DOCS":
        return "Väntar på dokument";

      case "UNDER_REVIEW":
        return "Under granskning";

      case "APPROVED":
        return "Godkänd";

      case "REJECTED":
        return "Avslagen";

      default:
        return status;
    }
  };

  const formatCurrency = (amount: number) => {
    return new Intl.NumberFormat("sv-SE").format(amount) + " kr";
  };

  return (
    <main className={styles.page}>
      <div className={styles.container}>
        <section className={styles.jumbotron}>
          <h1>Välkommen, {user?.name ?? "Företag"}!</h1>

          <p>Ansök om företagskredit hos Resurs Bank via denna portal.</p>

          <div className={styles.actions}>
            <button
              type="button"
              className={styles.primaryButton}
              onClick={() => navigate("/credit-application")}
            >
              <span className={styles.icon}>+</span> Ny kreditansökan
            </button>

            <button
              type="button"
              className={styles.defaultButton}
              onClick={() => navigate("/applications")}
            >
              <span className={styles.listIcon}>☷</span> Mina ansökningar
            </button>
          </div>
        </section>

        {loading && <p>Laddar ansökningar...</p>}

        {error && <div role="alert">{error}</div>}

        {!loading && !error && applications.length === 0 && (
          <p>Du har inga kreditansökningar ännu.</p>
        )}

        {!loading && !error && applications.length > 0 && (
          <section className={styles.applicationsSection}>
            <h3>Senaste ansökningar</h3>

            <table className={styles.applicationTable}>
              <thead>
                <tr>
                  <th>#</th>
                  <th>Belopp</th>
                  <th>Status</th>
                  <th>Datum</th>
                  <th></th>
                </tr>
              </thead>

              <tbody>
                {applications.map((application) => (
                  <tr key={application.id}>
                    <td>{application.id}</td>

                    <td>{formatCurrency(application.requestedAmount)}</td>

                    <td>
                      <span className={getStatusClass(application.status)}>
                        {formatStatus(application.status)}
                      </span>
                    </td>

                    <td>{application.createdAt}</td>

                    <td>
                      <button
                        type="button"
                        className={styles.viewButton}
                        onClick={() => navigate(`/status/${application.id}`)}
                      >
                        Visa
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
        )}
      </div>
    </main>
  );
}
