import React, {
  useEffect,
  useState,
} from "react";

import {
  useNavigate,
  useParams,
} from "react-router-dom";

import "./Backofficedetail.css";
import "../../styles/components.css";

import type { Application } from "../../types/application";
import type { ApplicationDocument } from "../../types/document";

import {
  applicationApi,
  type Decision,
} from "../../api/applicationApi";

export const Backofficedetail: React.FC = () => {
  const { id } =
    useParams<{ id: string }>();

  const navigate = useNavigate();

  const [application, setApplication] =
    useState<Application | null>(null);

  const [documents, setDocuments] =
    useState<ApplicationDocument[]>([]);

  const [auditLogRaw, setAuditLogRaw] =
    useState<string>("[]");

  const [workerName, setWorkerName] =
    useState<string>("");

  const [comment, setComment] =
    useState<string>("");

  const [loading, setLoading] =
    useState<boolean>(true);

  const [decisionLoading, setDecisionLoading] =
    useState<boolean>(false);

  const [error, setError] =
    useState<string | null>(null);

  const loadApplication = async (
    applicationId: string,
  ) => {
    const details =
      await applicationApi.getById(
        applicationId,
      );

    setApplication(
      details.application,
    );

    setDocuments(
      details.documents,
    );

    setAuditLogRaw(
      details.auditLogRaw,
    );

    setWorkerName(
      details.workerName,
    );
  };

  useEffect(() => {
    const load = async () => {
      if (!id) {
        setError(
          "Ansöknings-ID saknas.",
        );

        setLoading(false);
        return;
      }

      try {
        setLoading(true);
        setError(null);

        await loadApplication(id);
      } catch (err: unknown) {
        if (err instanceof Error) {
          setError(err.message);
        } else {
          setError(
            "Kunde inte hämta ansökan.",
          );
        }
      } finally {
        setLoading(false);
      }
    };

    load();
  }, [id]);

  const formatCurrency = (
    amount: number,
  ) => {
    return (
      new Intl.NumberFormat(
        "sv-SE",
      ).format(amount) + " kr"
    );
  };

  const formatDateTime = (
    value: string,
  ) => {
    if (!value) {
      return "-";
    }

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
      return value;
    }

    return new Intl.DateTimeFormat(
      "sv-SE",
      {
        dateStyle: "short",
        timeStyle: "short",
      },
    ).format(date);
  };

  const formatStatus = (
    status: Application["status"],
  ) => {
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

  const getStatusBadgeClass = (
    status: Application["status"],
  ) => {
    switch (status) {
      case "APPROVED":
        return "label-success";

      case "REJECTED":
        return "label-danger";

      case "UNDER_REVIEW":
        return "label-warning";

      default:
        return "label-default";
    }
  };

  const getScoringBadgeClass = (
    score?: string | null,
  ) => {
    if (!score) {
      return "label-default";
    }

    const uppercaseScore =
      score.toUpperCase();

    if (
      uppercaseScore.includes("GREEN")
    ) {
      return "label-success";
    }

    if (
      uppercaseScore.includes("YELLOW")
    ) {
      return "label-warning";
    }

    if (
      uppercaseScore.includes("RED")
    ) {
      return "label-danger";
    }

    return "label-default";
  };

  const handleDecision = async (
    decision: Decision,
  ) => {
    if (!application || !id) {
      return;
    }

    const actionText =
      decision === "APPROVED"
        ? "Godkänn"
        : "Avslå";

    const confirmed =
      window.confirm(
        `${actionText} ansökan #${application.id}?`,
      );

    if (!confirmed) {
      return;
    }

    try {
      setDecisionLoading(true);
      setError(null);

      await applicationApi.decide(
        application.id,
        {
          decision,
          comment: comment.trim(),
        },
      );

      await loadApplication(id);

      setComment("");
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError(
          "Kunde inte registrera beslutet.",
        );
      }
    } finally {
      setDecisionLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="backoffice-page">
        <p>
          Laddar ansökan...
        </p>
      </div>
    );
  }

  if (
    error &&
    !application
  ) {
    return (
      <div className="backoffice-page">
        <p className="text-muted">
          {error}
        </p>

        <button
          type="button"
          className="btn btn-default"
          onClick={() =>
            navigate("/backoffice")
          }
        >
          Tillbaka till handläggarkön
        </button>
      </div>
    );
  }

  if (!application) {
    return (
      <div className="backoffice-page">
        <p>
          Ansökan hittades inte.
        </p>
      </div>
    );
  }

  const isDecided =
    application.status ===
      "APPROVED" ||
    application.status ===
      "REJECTED";

  return (
    <div className="backoffice-page">
      <h2>
        Ansökan #{application.id} –
        Detaljvy
      </h2>

      {error && (
        <div className="alert alert-danger">
          {error}
        </div>
      )}

      <div className="backoffice-layout">
        <div className="col-left">
          <div className="panel">
            <div className="panel-heading">
              Företagsuppgifter
            </div>

            <div className="panel-body">
              <p>
                <strong>
                  Företagsnamn:
                </strong>{" "}
                {application.companyName}
              </p>

              <p>
                <strong>
                  Org.nummer:
                </strong>{" "}
                {application.orgNumber}
              </p>

              <p>
                <strong>
                  Handläggare:
                </strong>{" "}
                {workerName ||
                  "Ej tilldelad"}
              </p>
            </div>
          </div>

          <div className="panel">
            <div className="panel-heading">
              Kreditdetaljer
            </div>

            <div className="panel-body">
              <p>
                <strong>
                  Belopp:
                </strong>{" "}
                {formatCurrency(
                  application.requestedAmount,
                )}
              </p>

              <p>
                <strong>
                  Syfte:
                </strong>{" "}
                {application.purpose}
              </p>

              <p>
                <strong>
                  Status:
                </strong>{" "}
                <span
                  className={`label ${getStatusBadgeClass(
                    application.status,
                  )}`}
                >
                  {formatStatus(
                    application.status,
                  )}
                </span>
              </p>

              <p>
                <strong>
                  Inlämnad:
                </strong>{" "}
                {formatDateTime(
                  application.createdAt,
                )}
              </p>

              <p>
                <strong>
                  Senast uppdaterad:
                </strong>{" "}
                {formatDateTime(
                  application.updatedAt,
                )}
              </p>
            </div>
          </div>

          <div className="panel">
            <div className="panel-heading">
              Scoringresultat
            </div>

            <div className="panel-body">
              <span
                className={`label ${getScoringBadgeClass(
                  application.scoringResult,
                )}`}
              >
                {application.scoringResult ||
                  "-"}
              </span>
            </div>
          </div>
        </div>

        <div className="col-right">
          <div className="panel panel-warning">
            <div className="panel-heading">
              {isDecided
                ? "Beslut"
                : "Fatta beslut"}
            </div>

            <div className="panel-body">
              {isDecided ? (
                <>
                  <p>
                    <strong>
                      Beslut:
                    </strong>{" "}
                    {application.decision ||
                      "-"}
                  </p>

                  <p>
                    <strong>
                      Motivering:
                    </strong>{" "}
                    {application.decisionReason ||
                      "-"}
                  </p>
                </>
              ) : (
                <>
                  <div className="form-group">
                    <label htmlFor="decision-comment">
                      Kommentar
                    </label>

                    <textarea
                      id="decision-comment"
                      className="form-control"
                      rows={4}
                      maxLength={500}
                      placeholder="Motivering till beslutet"
                      value={comment}
                      onChange={(e) =>
                        setComment(
                          e.target.value,
                        )
                      }
                      disabled={
                        decisionLoading
                      }
                    />

                    <small className="text-muted">
                      {comment.length}/500
                    </small>
                  </div>

                  <div className="button-group">
                    <button
                      type="button"
                      className="btn btn-success"
                      disabled={
                        decisionLoading
                      }
                      onClick={() =>
                        handleDecision(
                          "APPROVED",
                        )
                      }
                    >
                      {decisionLoading
                        ? "Sparar..."
                        : "Godkänn"}
                    </button>

                    <button
                      type="button"
                      className="btn btn-danger"
                      disabled={
                        decisionLoading
                      }
                      onClick={() =>
                        handleDecision(
                          "REJECTED",
                        )
                      }
                    >
                      {decisionLoading
                        ? "Sparar..."
                        : "Avslå"}
                    </button>
                  </div>
                </>
              )}
            </div>
          </div>

          <div className="panel">
            <div className="panel-heading">
              Uppladdade dokument
            </div>

            <div className="panel-body">
              {documents.length === 0 ? (
                <div className="text-muted">
                  Inga dokument.
                </div>
              ) : (
                <ul className="document-list">
                  {documents.map(
                    (doc) => (
                      <li key={doc.id}>
                        📄 {doc.filename}{" "}
                        <small className="label label-default">
                          {doc.docType}
                        </small>
                      </li>
                    ),
                  )}
                </ul>
              )}
            </div>
          </div>

          <div className="panel">
            <div className="panel-heading">
              Händelselogg
            </div>

            <div className="panel-body">
              <pre className="audit-pre">
                {auditLogRaw}
              </pre>
            </div>
          </div>
        </div>
      </div>

      <div className="actions">
        <button
          type="button"
          className="btn btn-default"
          onClick={() =>
            navigate("/backoffice")
          }
        >
          Tillbaka
        </button>
      </div>
    </div>
  );
};