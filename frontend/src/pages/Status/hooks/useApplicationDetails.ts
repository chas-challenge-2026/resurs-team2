import { useCallback, useEffect, useState } from "react";

import { applicationApi } from "@/api/applicationApi";
import type { Application } from "@/types/application";
import type { FinancialData } from "@/types/applicationDetails";
import type { ApplicationDocument } from "@/types/document";

const SCORING_POLL_INTERVAL = 2000;

export const useApplicationDetails = (
  id: string | undefined,
) => {
  const [application, setApplication] =
    useState<Application | null>(null);

  const [documents, setDocuments] =
    useState<ApplicationDocument[]>([]);

  const [workerName, setWorkerName] =
    useState<string | null>(null);

  const [financialData, setFinancialData] =
    useState<FinancialData | null>(null);

  const [loading, setLoading] =
    useState<boolean>(true);

  const [error, setError] =
    useState<string | null>(null);

  const applyApplicationDetails = useCallback(
    (details: Awaited<ReturnType<typeof applicationApi.getById>>) => {
      setApplication(details.application);
      setDocuments(details.documents);
      setWorkerName(details.workerName);

      if (details.financialData) {
        const parsedFinancialData =
          JSON.parse(details.financialData) as FinancialData;

        setFinancialData(parsedFinancialData);
      } else {
        setFinancialData(null);
      }
    },
    [],
  );

  const loadApplication = useCallback(async () => {
    if (!id) {
      setError("Ansöknings-ID saknas.");
      setLoading(false);
      return;
    }

    try {
      const details =
        await applicationApi.getById(id);

      applyApplicationDetails(details);

      return details.application.status;
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError("Kunde inte hämta ansökan.");
      }

      return null;
    }
  }, [id, applyApplicationDetails]);

  useEffect(() => {
    const loadInitialApplication = async () => {
      setLoading(true);
      setError(null);

      await loadApplication();

      setLoading(false);
    };

    loadInitialApplication();
  }, [loadApplication]);

  useEffect(() => {
    if (
      !id ||
      !application ||
      application.status !== "SCORING_IN_PROGRESS"
    ) {
      return;
    }

    const intervalId = window.setInterval(async () => {
      const status = await loadApplication();

      if (status && status !== "SCORING_IN_PROGRESS") {
        window.clearInterval(intervalId);
      }
    }, SCORING_POLL_INTERVAL);

    return () => {
      window.clearInterval(intervalId);
    };
  }, [id, application?.status, loadApplication]);

  return {
    application,
    documents,
    workerName,
    financialData,
    loading,
    error,
  };
};