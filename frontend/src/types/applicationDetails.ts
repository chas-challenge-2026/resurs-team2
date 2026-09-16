import type { Application } from "./application";
import type { ApplicationDocument } from "./document";
import type { FinancialMetricsData } from "@/schemas/credit-application-schemas/FinancialMetrics.schema";

export interface ApplicationDetails {
  application: Application;
  financialMetrics: FinancialMetricsData;
  workerName: string;
  documents: ApplicationDocument[];
}