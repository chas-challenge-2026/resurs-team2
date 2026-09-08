import type { Application, ApplicationStatus } from "../types/application";
import type { ApplicationDetails } from "../types/applicationDetails";
import type { ApplicationRequest } from "../types/applicationRequest";
import { apiFetch } from "./apiFetch";
export type Decision = "APPROVED" | "REJECTED";

export interface DecisionRequest {
  decision: Decision;
  comment: string;
}

export const applicationApi = {
  async getAll(status?: ApplicationStatus): Promise<Application[]> {
    const query = status ? `?status=${encodeURIComponent(status)}` : "";

    const response = await apiFetch(`/api/v1/applications${query}`);

    if (!response.ok) {
      throw new Error("Kunde inte hämta ansökningarna.");
    }

    return response.json();
  },

  async getById(id: number | string): Promise<ApplicationDetails> {
    const response = await apiFetch(`/api/v1/applications/${id}`);

    if (response.status === 404) {
      throw new Error("Ansökan hittades inte.");
    }

    if (response.status === 403) {
      throw new Error("Du har inte behörighet att visa ansökan.");
    }

    if (!response.ok) {
      throw new Error("Kunde inte hämta ansökan.");
    }

    return response.json();
  },

  async decide(
    id: number | string,
    request: DecisionRequest,
  ): Promise<Application> {
    const response = await apiFetch(`/api/v1/applications/${id}/decision`, {
      method: "POST",

      headers: {
        "Content-Type": "application/json",
      },

      body: JSON.stringify(request),
    });

    if (response.status === 403) {
      throw new Error("Du har inte behörighet att fatta beslut.");
    }

    if (response.status === 404) {
      throw new Error("Ansökan hittades inte.");
    }

    if (response.status === 400) {
      throw new Error(
        "Beslutet kunde inte registreras. Kontrollera kommentaren och försök igen.",
      );
    }

    if (!response.ok) {
      throw new Error("Kunde inte registrera beslutet.");
    }

    return response.json();
  },

  async create(request: ApplicationRequest): Promise<number> {
    const response = await apiFetch("/api/v1/applications", {
      method: "POST",

      headers: {
        "Content-Type": "application/json",
      },

      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error("Kunde inte skapa ansökan.");
    }

    return response.json();
  },
};
