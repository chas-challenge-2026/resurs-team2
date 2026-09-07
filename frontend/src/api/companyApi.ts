import { apiFetch } from "./apiFetch";

export interface Company {
  companyName: string;
  orgNumber: string;
}

export const companyApi = {
    async getCurrentCompany(): Promise<Company> {
        const response = await apiFetch("/api/v1/companies/me");

        if(!response.ok) {
            throw new Error("Kunde inte hämta företagsinformationen.");
        }

        return response.json();
    },
};