import type { ApplicationStatus } from "./application";

export interface DashboardRequest {
  id: number;
  requestedAmount: number;
  status: ApplicationStatus;
  createdAt: string;
  companyName: string;
}
