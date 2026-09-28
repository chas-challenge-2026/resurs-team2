import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import type { Application as ApplicationType } from "../../types/application";
import { applicationApi } from "../../api/applicationApi";

import styles from "./Applications.module.css";

export function Application() {
  const navigate = useNavigate();

  const [applications, setApplications] = useState<ApplicationType[]>([]);

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

  const getStatusClass = (status: ApplicationType["status"]) => {
    switch (status) {
      case "APPROVED":
        return styles.labelSuccess;

      case "REJECTED":
        return styles.labelDanger;

      case "UNDER_REVIEW":
        return styles.labelWarning;

      case "PENDING_DOCS":
      default:
        return styles.labelDefault;
    }
  };

  const formatStatus = (status: ApplicationType["status"]) => {
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

  const formatDateTime = (value: string) => {
    if (!value) {
      return "-";
    }

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
      return value;
    }

    return new Intl.DateTimeFormat("sv-SE", {
      dateStyle: "short",
      timeStyle: "short",
    }).format(date);
  };

  if (loading) {
    return (
      <main className={styles.page}>
        <div className={styles.container}>
          <div className={styles.pageHeader}>
            <h1>Mina kreditansökningar</h1>
          </div>

          <p>Laddar ansökningar...</p>
        </div>
      </main>
    );
  }

  if (error) {
    return (
      <main className={styles.page}>
        <div className={styles.container}>
          <div className={styles.pageHeader}>
            <h1>Mina kreditansökningar</h1>
          </div>

          <div className={styles.infoAlert}>
            <p>{error}</p>
          </div>
        </div>
      </main>
    );
  }

  return (
    <main className={styles.page}>
      <div className={styles.container}>
        <div className={styles.pageHeader}>
          <h1>Mina kreditansökningar</h1>
        </div>

        {applications.length === 0 ? (
          <div className={styles.infoAlert}>
            <p>
              Du har inga ansökningar ännu.{" "}
              <button
                type="button"
                className={styles.createLink}
                onClick={() => navigate("/apply")}
              >
                Skapa din första ansökan
              </button>
              .
            </p>
          </div>
        ) : (
          <>
            <button
              type="button"
              className={styles.primaryButton}
              onClick={() => navigate("/apply")}
            >
              <span className={styles.plusIcon}>+</span> Ny ansökan
            </button>

            <table className={styles.applicationTable}>
              <thead>
                <tr>
                  <th>#</th>
                  <th>Kreditbelopp</th>
                  <th>Syfte</th>
                  <th>Status</th>
                  <th>Inlämnad</th>
                  <th>Åtgärder</th>
                </tr>
              </thead>

              <tbody>
                {applications.map((application) => (
                  <tr key={application.id}>
                    <td>{application.id}</td>

                    <td>{formatCurrency(application.requestedAmount)}</td>

                    <td>{application.purpose}</td>

                    <td>
                      <span className={getStatusClass(application.status)}>
                        {formatStatus(application.status)}
                      </span>
                    </td>

                    <td>{formatDateTime(application.createdAt)}</td>

                    <td className={styles.actionCell}>
                      <button
                        type="button"
                        className={styles.viewButton}
                        onClick={() => navigate(`/status/${application.id}`)}
                      >
                        Visa
                      </button>

                      <button
                        type="button"
                        className={styles.documentButton}
                        onClick={() => navigate(`/documents/${application.id}`)}
                      >
                        Dokument
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}
      </div>
    </main>
  );
}
