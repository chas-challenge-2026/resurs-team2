import { useEffect, useState } from "react";

import type { Application } from "@/types/application";
import { applicationApi } from "@/api/applicationApi";

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

  return { reviewApps, decidedApps, loading, error };
};
