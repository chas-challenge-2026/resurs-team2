import {
  type CompanyCredentials,
  type CaseWorkerCredentials,
  type Role,
} from "../context/AuthContext";

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  role: Role;
  name: string;
}

export interface CurrentCompanyResponse {
  name: string;
  orgNumber: string;
}

export const authApi = {
  async loginCompany(credentials: CompanyCredentials): Promise<AuthTokens> {
    const response = await fetch("/api/v1/auth/login/company", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(credentials),
    });

    if (!response.ok) {
      throw new Error(
        "Inloggning misslyckades. Kontrollera organisationsnumret.",
      );
    }

    return response.json();
  },

  async loginCaseWorker(
    credentials: CaseWorkerCredentials,
  ): Promise<AuthTokens> {
    const response = await fetch("/api/v1/auth/login/caseWorker", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(credentials),
    });

    if (!response.ok) {
      throw new Error("Felaktig e-postadress eller lösenord.");
    }

    return response.json();
  },

  async refresh(refreshToken: string): Promise<AuthTokens> {
    const response = await fetch("/api/v1/auth/refresh", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        refreshToken,
      }),
    });

    if (!response.ok) {
      throw new Error("Sessionen har gått ut.");
    }

    return response.json();
  },

  async getCurrentCompany(
    accessToken: string,
  ): Promise<CurrentCompanyResponse> {
    const response = await fetch("/api/v1/companies/me", {
      method: "GET",
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    });

    if (!response.ok) {
      throw new Error("Kunde inte hämta företagsinformationen.");
    }

    return response.json();
  },

  async logout(accessToken: string): Promise<void> {
    const response = await fetch("/api/v1/auth/logout", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    });

    if (!response.ok && response.status !== 401) {
      throw new Error("Utloggning misslyckades.");
    }
  },
};
