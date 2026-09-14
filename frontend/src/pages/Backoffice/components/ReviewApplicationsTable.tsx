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
    <div className="panel panel-warning">
      <div className="panel-heading">
        <strong>Ansökningar för granskning</strong>
      </div>

      {applications.length === 0 ? (
        <div className="panel-body">
          <p className="text-muted">
            Inga ansökningar väntar på granskning.
          </p>
        </div>
      ) : (
        <table className="table table-bordered table-review">
          <thead>
            <tr>
              <th>#</th>
              <th>Företag</th>
              <th>Org.nr</th>
              <th>Belopp</th>
              <th>Syfte</th>
              <th>Scoring</th>
              <th>Inlämnad</th>
              <th>Åtgärd</th>
            </tr>
          </thead>

          <tbody>
            {applications.map((app) => (
              <tr key={app.id}>
                <td>{app.id}</td>
                <td>{app.companyName}</td>
                <td>{app.orgNumber}</td>
                <td>{formatCurrency(app.requestedAmount)}</td>
                <td>{app.purpose}</td>

                <td>
                  <small>{app.scoringResult || "-"}</small>
                </td>

                <td>{formatDateTime(app.createdAt)}</td>

                <td>
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
      )}
    </div>
  );
};