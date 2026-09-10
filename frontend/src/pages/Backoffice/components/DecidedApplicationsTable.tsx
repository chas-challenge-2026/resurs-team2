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
    <div className="panel">
      <div className="panel-heading">
        <strong>Senaste beslut (max 20)</strong>
      </div>

      {applications.length === 0 ? (
        <div className="panel-body">
          <p className="text-muted">Inga avgjorda ansökningar ännu.</p>
        </div>
      ) : (
        <table className="table table-striped">
          <thead>
            <tr>
              <th>#</th>
              <th>Företag</th>
              <th>Belopp</th>
              <th>Status</th>
              <th>Beslut</th>
              <th>Motivering</th>
              <th>Åtgärd</th>
            </tr>
          </thead>

          <tbody>
            {applications.map((app) => (
              <tr key={app.id}>
                <td>{app.id}</td>
                <td>{app.companyName}</td>
                <td>{formatCurrency(app.requestedAmount)}</td>

                <td>
                  <span
                    className={`label ${
                      app.status === "APPROVED"
                        ? "label-success"
                        : "label-danger"
                    }`}
                  >
                    {formatStatus(app.status)}
                  </span>
                </td>

                <td>{app.decision || "-"}</td>
                <td>{app.decisionReason || "-"}</td>

                <td>
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
      )}
    </div>
  );
};