import type {
  ApplicationStatus,
} from "./application";

export interface ApplicationSummary {
  id: number;
  requestedAmount: number;
  status: ApplicationStatus;
  createdAt: string;
  companyName: string;
}