import type { ApplicationStep } from "../types/timeline";

export const mockTimelineSteps: ApplicationStep[] = [
  { name: "Ansökan mottagen", status: "DONE", description: "Vi har tagit emot er ansökan.", eta: "—" },
  { name: "Komplettering & Dokument", status: "DONE", description: "Alla nödvändiga dokument har skickats in.", eta: "—" },
  { name: "Kreditprövning & Granskning", status: "CURRENT", description: "Handläggare granskar företagets ekonomi.", eta: "1-2 dagar" },
  { name: "Beslut & Utbetalning", status: "PENDING", description: "Slutgiltigt besked lämnas.", eta: "3 dagar" },
];