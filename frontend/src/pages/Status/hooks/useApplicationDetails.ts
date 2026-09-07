import { useEffect, useState } from "react";
import { applicationApi } from "../../../api/applicationApi";
import type { Application } from "../../../types/application";
import type { ApplicationDocument } from "../../../types/document";

export const useApplicationDetails = (
  id: string | undefined,
) => {
  const [application, setApplication] =
    useState<Application | null>(null);

  const [documents, setDocuments] =
    useState<ApplicationDocument[]>([]);

  const [auditLogRaw, setAuditLogRaw] =
    useState<string>("[]");

  const [workerName, setWorkerName] =
    useState<string>("");

  const [loading, setLoading] =
    useState<boolean>(true);

  const [error, setError] =
    useState<string | null>(null);

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

        const details =
          await applicationApi.getById(id);

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

    loadApplication();
  }, [id]);

  return {
    application,
    documents,
    auditLogRaw,
    workerName,
    loading,
    error,
  };
};