import { useNavigate } from "react-router-dom";
import type { Application } from "../../types/application";
import { mockApplications } from "../../mockdata/applications";
import styles from "./Applications.module.css";

export function Applications() {
  const navigate = useNavigate();

  const applications: Application[] = mockApplications;

  const getStatusClass = (status: Application["status"]) => {
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

  return (
    <main className={styles.page}>
      <div className={styles.container}>
        <div className={styles.pageHeader}>
          <h1>Mina kreditansökningar</h1>
        </div>

        {applications.length === 0 && (
          <div className={styles.infoAlert}>
            <p>
              Du har inga ansökningar ännu.{" "}
              <button
                type="button"
                className={styles.createLink}
                onClick={() => navigate("/credit-application")}
              >
                Skapa din första ansökan
              </button>
              .
            </p>
          </div>
        )}

        <button
          type="button"
          className={styles.primaryButton}
          onClick={() => navigate("/credit-application")}
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
            {applications.length === 0 ? (
              <tr>
                <td>0</td>

                <td>0</td>

                <td>Syfte</td>

                <td></td>

                <td>datum</td>

                <td className={styles.actionCell}>
                  <button type="button" className={styles.viewButton}>
                    👁 Visa
                  </button>

                  <button type="button" className={styles.documentButton}>
                    ↑ Dokument
                  </button>
                </td>
              </tr>
            ) : (
              applications.map((application) => (
                <tr key={application.id}>
                  <td>{application.id}</td>

                  <td>{application.requestedAmount} kr</td>

                  <td>{application.purpose}</td>

                  <td>
                    <span className={getStatusClass(application.status)}>
                      {application.status}
                    </span>
                  </td>

                  <td>{application.createdAt}</td>

                  <td className={styles.actionCell}>
                    <button
                      type="button"
                      className={styles.viewButton}
                      onClick={() => navigate(`/status/${application.id}`)}
                    >
                      👁 Visa
                    </button>

                    <button
                      type="button"
                      className={styles.documentButton}
                      onClick={() => navigate(`/documents/${application.id}`)}
                    >
                      ↑ Dokument
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </main>
  );
}
