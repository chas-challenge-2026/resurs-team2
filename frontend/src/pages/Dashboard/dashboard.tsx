import React from "react";
import { useNavigate } from "react-router-dom";
import styles from "./dashboard.module.css";
import type { Application } from "../../types/application";
import { mockApplications } from "../../mockdata/applications";

interface DashboardProps {
  applications?: Application[];
  companyName?: string;
}

export const Dashboard: React.FC<DashboardProps> = ({
  applications = mockApplications,
  companyName = "Nordic Tech Solutions AB",
}) => {
  const navigate = useNavigate();

  const getStatusClass = (status: string) => {
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

  const formatCurrency = (amount?: number) => {
    if (!amount) return "0 kr";
    return new Intl.NumberFormat("sv-SE").format(amount) + " kr";
  };

  return (
    <main className={styles.page}>
      <div className={styles.container}>
        <section className={styles.jumbotron}>
          <h1>Välkommen, {companyName}!</h1>
          <p>
            Ansök om företagskredit hos Resurs Bank via denna portal.
          </p>

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
                    <td>#{application.id}</td>
                    <td>{formatCurrency(application.requestedAmount)}</td>
                    <td>
                      {application.status && (
                        <span className={getStatusClass(application.status)}>
                          {application.status}
                        </span>
                      )}
                    </td>
                    <td>{application.createdAt || "-"}</td>
                    <td>
                      <button
                        type="button"
                        className={styles.viewButton}
                        onClick={() => navigate(`/status`)}
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
};

export default Dashboard;