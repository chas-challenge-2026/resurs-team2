export type ApplicationStatus =
  | "PENDING_DOCS"
  | "UNDER_REVIEW"
  | "APPROVED"
  | "REJECTED";

export interface Application {
  id: number;
  companyName: string;
  orgNumber: string;
  requestedAmount: number;
  purpose: string;
  createdAt: string;
  status: ApplicationStatus;
  decision: "APPROVED" | "REJECTED" | null;
  decisionReason: string | null;
  scoringResult: string | null;
}