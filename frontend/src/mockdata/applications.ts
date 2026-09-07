import type { Application } from "../types/application";

export const mockApplications: Application[] = [
  {
    id: 101,
    companyName: "Nordic Tech Solutions AB",
    orgNumber: "556123-4567",
    requestedAmount: 500000,
    authorizedSignatory: "Anna Lindberg",
    purpose: "Investering i nya maskiner",
    createdAt: "2026-08-28 10:15",
    status: "UNDER_REVIEW",
    decision: null,
    decisionReason: null,
    scoringResult: "LOW_RISK (Score: 840)",
  },
  {
    id: 102,
    companyName: "Svenska Bygg & Fastighet AB",
    orgNumber: "556987-6543",
    requestedAmount: 1200000,
    authorizedSignatory: "Johan Berg",
    purpose: "Brygglån för fastighetsprojekt",
    createdAt: "2026-08-30 14:22",
    status: "UNDER_REVIEW",
    decision: null,
    decisionReason: null,
    scoringResult: "MEDIUM_RISK (Score: 650)",
  },
];