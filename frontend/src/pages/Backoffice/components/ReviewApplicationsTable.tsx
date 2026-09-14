import { useNavigate } from "react-router-dom";

import type { Application } from "@/types/application";

import {
  formatCurrency,
  formatDateTime,
} from "../utils/backofficeFormatters";

interface ReviewApplicationsTableProps {
  applications: Application[];
}

export const ReviewApplicationsTable = ({
  applications,
}: ReviewApplicationsTableProps) => {
  const navigate = useNavigate();

  return (
    <section className="backoffice-card backoffice-card-primary">
      <header className="backoffice-card-header">
        <div>
          <h2>Ansökningar för granskning</h2>
          <p>
            Ansökningar som behöver hanteras manuellt.
          </p>
        </div>

        <span className="backoffice-card-count">
          {applications.length}
        </span>
      </header>

      {applications.length === 0 ? (
        <div className="backoffice-empty">
          <strong>Inga ansökningar väntar</strong>
          <p>Det finns inga kreditansökningar att granska just nu.</p>
        </div>
      ) : (
        <div className="backoffice-table-wrapper">
          <table className="backoffice-table">
            <thead>
              <tr>
                <th scope="col">ID</th>
                <th scope="col">Företag</th>
                <th scope="col">Org.nr</th>
                <th scope="col">Belopp</th>
                <th scope="col">Syfte</th>
                <th scope="col">Scoring</th>
                <th scope="col">Inlämnad</th>
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

                  <td>{app.orgNumber}</td>

                  <td className="backoffice-amount">
                    {formatCurrency(app.requestedAmount)}
                  </td>

                  <td className="backoffice-purpose">
                    {app.purpose || "-"}
                  </td>

                  <td>
                    <span className="backoffice-score">
                      {app.scoringResult || "-"}
                    </span>
                  </td>

                  <td>{formatDateTime(app.createdAt)}</td>

                  <td className="backoffice-table-action">
                    <button
                      type="button"
                      className="btn btn-primary"
                      onClick={() => navigate(`/backoffice/${app.id}`)}
                    >
                      Granska
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