import React, { useEffect, useState } from "react";

import { useNavigate } from "react-router-dom";

import "./Backoffice.css";
import "../../styles/components.css";

import type { Application } from "../../types/application";

import { applicationApi } from "../../api/applicationApi";
import { useAuth } from "../../components/hooks/useAuth";

export const Backoffice: React.FC = () => {
  const navigate = useNavigate();

  const { user } = useAuth();

  const [reviewApps, setReviewApps] = useState<Application[]>([]);

  const [decidedApps, setDecidedApps] = useState<Application[]>([]);

  const [loading, setLoading] = useState<boolean>(true);

  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const loadApplications = async () => {
      try {
        setLoading(true);
        setError(null);

        const applications = await applicationApi.getAll();

        const review = applications.filter(
          (app) => app.status === "UNDER_REVIEW",
        );

        const decided = applications
          .filter(
            (app) => app.status === "APPROVED" || app.status === "REJECTED",
          )
          .sort(
            (a, b) =>
              new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime(),
          )
          .slice(0, 20);

        setReviewApps(review);
        setDecidedApps(decided);
      } catch (err: unknown) {
        if (err instanceof Error) {
          setError(err.message);
        } else {
          setError("Kunde inte hämta handläggarkön.");
        }
      } finally {
        setLoading(false);
      }
    };

    loadApplications();
  }, []);

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

  const formatStatus = (status: Application["status"]) => {
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

  if (loading) {
    return (
      <div className="backoffice-page">
        <h2>Handläggarkö</h2>

        <p className="text-muted">Laddar ansökningar...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="backoffice-page">
        <h2>Handläggarkö</h2>

        <div className="panel">
          <div className="panel-body">
            <p>{error}</p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="backoffice-page">
      <header className="backoffice-header">
        <h2 className="backoffice-title">
          Handläggarkö <span className="badge-count">{reviewApps.length}</span>
        </h2>

        <p className="text-muted">
          Inloggad handläggare: {user?.name || "Handläggare"}
        </p>
      </header>

      <div className="panel panel-warning">
        <div className="panel-heading">
          <strong>Ansökningar för granskning</strong>
        </div>

        {reviewApps.length === 0 ? (
          <div className="panel-body">
            <p className="text-muted">Inga ansökningar väntar på granskning.</p>
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
              {reviewApps.map((app) => (
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

      <div className="panel">
        <div className="panel-heading">
          <strong>Senaste beslut (max 20)</strong>
        </div>

        {decidedApps.length === 0 ? (
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
              {decidedApps.map((app) => (
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
    </div>
  );
};
