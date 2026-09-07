import React, { useEffect, useState } from "react";

import { useNavigate, useParams } from "react-router-dom";

import "./Status.css";

import type { Application } from "../../types/application";
import type { ApplicationDocument } from "../../types/document";

import { applicationApi } from "../../api/applicationApi";

export const Status: React.FC = () => {
  const { id } = useParams<{ id: string }>();

  const navigate = useNavigate();

  const [currentApplication, setCurrentApplication] =
    useState<Application | null>(null);

  const [documents, setDocuments] = useState<ApplicationDocument[]>([]);

  const [auditLogRaw, setAuditLogRaw] = useState<string>("[]");

  const [workerName, setWorkerName] = useState<string>("");

  const [loading, setLoading] = useState<boolean>(true);

  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const loadApplication = async () => {
      if (!id) {
        setError("Ansöknings-ID saknas.");

        setLoading(false);

        return;
      }

      try {
        setLoading(true);

        setError(null);

        const details = await applicationApi.getById(id);

        setCurrentApplication(details.application);

        setDocuments(details.documents);

        setAuditLogRaw(details.auditLogRaw);

        setWorkerName(details.workerName);
      } catch (err: unknown) {
        if (err instanceof Error) {
          setError(err.message);
        } else {
          setError("Kunde inte hämta ansökan.");
        }
      } finally {
        setLoading(false);
      }
    };

    loadApplication();
  }, [id]);

  const formatCurrency = (amount?: number) => {
    if (amount === undefined || amount === null) {
      return "0 kr";
    }

    return new Intl.NumberFormat("sv-SE").format(amount) + " kr";
  };

  const formatDateTime = (value?: string) => {
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
      <div className="status-page">
        <p>Laddar ansökan...</p>
      </div>
    );
  }

  if (error || !currentApplication) {
    return (
      <div className="status-page">
        <p className="text-muted">{error ?? "Ingen ansökan hittades."}</p>

        <button
          type="button"
          className="btn btn-default"
          onClick={() => navigate("/application")}
        >
          Tillbaka till ansökningar
        </button>
      </div>
    );
  }

  return (
    <div className="status-page">
      <h2>Ansökan #{currentApplication.id}</h2>

      <p className="status-header">
        Status:{" "}
        <span
          className={`badge badge-${currentApplication.status}`}
          role="status"
        >
          {formatStatus(currentApplication.status)}
        </span>
      </p>

      {currentApplication.decision && (
        <div
          className={`alert ${
            currentApplication.decision === "APPROVED"
              ? "alert-success"
              : "alert-danger"
          }`}
        >
          <h4>
            {currentApplication.decision === "APPROVED"
              ? "Ansökan godkänd"
              : "Ansökan avslagen"}
          </h4>

          <p>{currentApplication.decisionReason}</p>
        </div>
      )}

      <div className="status-layout">
        <div className="left-column">
          {currentApplication.scoringResult && (
            <div className="panel">
              <div className="panel-heading">Scoringresultat</div>

              <div className="panel-body">
                {currentApplication.scoringResult}
              </div>
            </div>
          )}

          <div className="panel">
            <div className="panel-heading">Handläggare</div>

            <div className="panel-body">{workerName || "Ej tilldelad"}</div>
          </div>
        </div>

        <div className="right-column">
          <div className="panel">
            <div className="panel-heading">Ansökningsdetaljer</div>

            <div className="panel-body">
              <p>
                <strong>Företag:</strong>

                <br />

                {currentApplication.companyName}
              </p>

              <p>
                <strong>Org.nummer:</strong>

                <br />

                {currentApplication.orgNumber}
              </p>

              <p>
                <strong>Kreditbelopp:</strong>

                <br />

                {formatCurrency(currentApplication.requestedAmount)}
              </p>

              <p>
                <strong>Syfte:</strong>

                <br />

                {currentApplication.purpose}
              </p>

              <p>
                <strong>Inlämnad:</strong>

                <br />

                {formatDateTime(currentApplication.createdAt)}
              </p>

              <p>
                <strong>Senast uppdaterad:</strong>

                <br />

                {formatDateTime(currentApplication.updatedAt)}
              </p>
            </div>
          </div>

          <div className="panel">
            <div className="panel-heading">
              Dokument
              <button
                type="button"
                className="btn btn-sm btn-primary pull-right"
                onClick={() => navigate(`/documents/${currentApplication.id}`)}
              >
                Ladda upp
              </button>
            </div>

            <div className="panel-body">
              {documents.length === 0 ? (
                <p className="text-muted">Inga dokument uppladdade ännu.</p>
              ) : (
                <ul className="document-list">
                  {documents.map((doc) => (
                    <li key={doc.id}>
                      📄 {doc.filename} ({doc.docType})
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>

          <div className="panel">
            <div className="panel-heading">Händelselogg</div>

            <div className="panel-body">
              <pre>{auditLogRaw}</pre>
            </div>
          </div>
        </div>
      </div>

      <div className="actions">
        <button
          type="button"
          className="btn btn-default"
          onClick={() => navigate("/application")}
        >
          Tillbaka
        </button>

        <button
          type="button"
          className="btn btn-primary"
          onClick={() => navigate("/apply")}
        >
          Redigera ansökan
        </button>
      </div>
    </div>
  );
};
