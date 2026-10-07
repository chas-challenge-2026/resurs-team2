export type ApplicationStatus =
  | "PENDING_DOCS"
  | "SCORING_IN_PROGRESS"
  | "UNDER_REVIEW"
  | "APPROVED"
  | "REJECTED";

export interface Application {
  id: number;
  companyName: string;
  orgNumber: string;
  requestedAmount: number;
  purpose: string;
  status: ApplicationStatus;
  decision: "APPROVED" | "REJECTED" | null;
  decisionReason: string | null;
  scoringResult: string | null;
  createdAt: string;
  updatedAt: string;
}