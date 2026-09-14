import { useNavigate } from "react-router-dom";

import type { Application } from "@/types/application";

import {
  formatCurrency,
  formatStatus,
} from "../utils/backofficeFormatters";

interface DecidedApplicationsTableProps {
  applications: Application[];
}

export const DecidedApplicationsTable = ({
  applications,
}: DecidedApplicationsTableProps) => {
  const navigate = useNavigate();

  return (
    <section className="backoffice-card">
      <header className="backoffice-card-header">
        <div>
          <h2>Senaste beslut</h2>
          <p>De 20 senast avgjorda kreditansökningarna.</p>
        </div>
      </header>

      {applications.length === 0 ? (
        <div className="backoffice-empty">
          <strong>Inga beslut ännu</strong>
          <p>Avgjorda ansökningar kommer att visas här.</p>
        </div>
      ) : (
        <div className="backoffice-table-wrapper">
          <table className="backoffice-table">
            <thead>
              <tr>
                <th scope="col">ID</th>
                <th scope="col">Företag</th>
                <th scope="col">Belopp</th>
                <th scope="col">Status</th>
                <th scope="col">Beslut</th>
                <th scope="col">Motivering</th>
                <th scope="col">
                  <span className="sr-only">Åtgärd</span>
                </th>
              </tr>
            </thead>

            <tbody>
              {applications.map((app) => (
                <tr key={app.id}>
                  <td className="backoffice-id">#{app.id}</td>

                  <td>
                    <strong className="backoffice-company">
                      {app.companyName}
                    </strong>
                  </td>

                  <td className="backoffice-amount">
                    {formatCurrency(app.requestedAmount)}
                  </td>

                  <td>
                    <span
                      className={`backoffice-status ${
                        app.status === "APPROVED"
                          ? "backoffice-status-success"
                          : "backoffice-status-danger"
                      }`}
                    >
                      {formatStatus(app.status)}
                    </span>
                  </td>

                  <td>{app.decision || "-"}</td>

                  <td className="backoffice-reason">
                    {app.decisionReason || "-"}
                  </td>

                  <td className="backoffice-table-action">
                    <button
                      type="button"
                      className="btn btn-default"
                      onClick={() => navigate(`/backoffice/${app.id}`)}
                    >
                      Visa
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
};