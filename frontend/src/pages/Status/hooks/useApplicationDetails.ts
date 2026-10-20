import { useEffect, useState } from "react";

import { applicationApi } from "@/api/applicationApi";
import type { Application } from "@/types/application";
import type {
  FinancialData,
} from "@/types/applicationDetails";
import type { ApplicationDocument } from "@/types/document";




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

          console.log("DETAILS:", details);
          console.log("FINANCIAL DATA RAW:", details.financialData);

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
    workerName,
    financialData,
    loading,
    error,
  };
};