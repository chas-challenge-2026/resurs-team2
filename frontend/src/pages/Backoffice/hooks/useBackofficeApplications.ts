import { useEffect, useState } from "react";

import { applicationApi } from "@/api/applicationApi";
import type { Application } from "@/types/application";

export const useBackofficeApplications = () => {
  const [reviewApps, setReviewApps] = useState<Application[]>([]);
  const [decidedApps, setDecidedApps] = useState<Application[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const loadApplications = async () => {
      try {
        setLoading(true);
        setError(null);

        const [reviewResponse, approvedResponse, rejectedResponse] =
          await Promise.all([
            applicationApi.getAll("UNDER_REVIEW"),
            applicationApi.getAll("APPROVED"),
            applicationApi.getAll("REJECTED"),
          ]);

        const review = reviewResponse.content;

        const decided = [
          ...approvedResponse.content,
          ...rejectedResponse.content,
        ]
          .sort(
            (a, b) =>
              new Date(b.updatedAt).getTime() -
              new Date(a.updatedAt).getTime(),
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

  return {
    reviewApps,
    decidedApps,
    loading,
    error,
  };
};