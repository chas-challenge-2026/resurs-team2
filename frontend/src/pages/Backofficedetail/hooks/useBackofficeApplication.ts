import { useEffect, useState } from "react";
import { applicationApi, type Decision } from "../../../api/applicationApi";
import type { Application } from "../../../types/application";
import type { ApplicationDocument } from "../../../types/document";

export const useBackofficeApplication = (id: string | undefined) => {
  const [application, setApplication] = useState<Application | null>(null);

  const [documents, setDocuments] = useState<ApplicationDocument[]>([]);

  const [auditLogRaw, setAuditLogRaw] = useState<string>("[]");

  const [workerName, setWorkerName] = useState<string>("");

  const [loading, setLoading] = useState<boolean>(true);

  const [decisionLoading, setDecisionLoading] = useState<boolean>(false);

  const [error, setError] = useState<string | null>(null);

  const loadApplication = async (applicationId: string) => {
    const details = await applicationApi.getById(applicationId);

    setApplication(details.application);

    setDocuments(details.documents);

    setAuditLogRaw(details.auditLogRaw);

    setWorkerName(details.workerName);
  };

  useEffect(() => {
    const load = async () => {
      if (!id) {
        setError("Ansöknings-ID saknas.");

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
          setError("Kunde inte hämta ansökan.");
        }
      } finally {
        setLoading(false);
      }
    };

    load();
  }, [id]);

  const handleDecision = async (decision: Decision, comment: string) => {
    if (!application || !id) {
      return;
    }

    try {
      setDecisionLoading(true);
      setError(null);

      await applicationApi.decide(application.id, {
        decision,
        comment: comment.trim(),
      });

      await loadApplication(id);
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError("Kunde inte registrera beslutet.");
      }
    } finally {
      setDecisionLoading(false);
    }
  };

  return {
    application,
    documents,
    auditLogRaw,
    workerName,
    loading,
    decisionLoading,
    error,
    handleDecision,
  };
};
