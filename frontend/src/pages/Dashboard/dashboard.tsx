import React from "react";
import { useNavigate } from "react-router-dom";
import type { Application } from "../../types/application";
import styles from "./dashboard.module.css";

export function Dashboard() {
  const navigate = useNavigate();

  const companyName = "Företag AB";

  const applications: Application[] = [
    {
      id: 0,
      companyName: "Företag AB",
      orgNumber: "",
      requestedAmount: 0,
      purpose: "",
      createdAt: "-",
      status: "PENDING_DOCS",
      decision: null,
      decisionReason: null,
      scoringResult: null,
    },
  ];

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
        <section className={styles.jumbotron}>
          <h1>Välkommen, {companyName}!</h1>

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

        {applications.length > 0 && (
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

                    <td>{application.requestedAmount}</td>

                    <td>
                      {application.status && (
                        <span className={getStatusClass(application.status)}>
                          {application.status}
                        </span>
                      )}
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
